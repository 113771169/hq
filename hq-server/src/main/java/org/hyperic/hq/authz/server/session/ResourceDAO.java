/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2008], Hyperic, Inc.
 * This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.authz.server.session;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.FlushMode;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hyperic.hibernate.PageInfo;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.EdgePermCheck;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.dao.HibernateDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceDAO
    extends HibernateDAO<Resource> {
    private Log _log = LogFactory.getLog(ResourceDAO.class);

    private PermissionManager permissionManager;

    @Autowired
    public ResourceDAO(SessionFactory f, PermissionManager permissionManager) {
        super(Resource.class, f);
        this.permissionManager = permissionManager;
    }

    Resource create(ResourceType type, Resource prototype, String name, AuthzSubject creator,
                    Integer instanceId, boolean system) {
        Resource resource = createPrivate(type, prototype, name, creator, instanceId, system);

        /* add it to the root resource group */
        // TODO resolve circular dependencies to remove Bootstrap
        ResourceGroupDAO resourceGroupDAO = Bootstrap.getBean(ResourceGroupDAO.class);
        ResourceGroup authzGroup = resourceGroupDAO.findRootGroup();
        resourceGroupDAO.addMembers(authzGroup, Collections.singleton(resource));

        return resource;
    }

    Resource createPrivate(ResourceType type, Resource prototype, String name,
                           AuthzSubject creator, Integer instanceId, boolean system) {
        if (type == null) {
            throw new IllegalArgumentException("ResourceType not set");
        }
        Resource resource = new Resource(type, prototype, name, creator, instanceId, system);

        save(resource);

        // Need to flush so that later permission checking can succeed
        getSession().flush();

        return resource;
    }

    public Resource findRootResource() {
        return findById(AuthzConstants.rootResourceId);
    }

    public void remove(Resource entity) {
        // need this to ensure that the optimistic locking doesn't fail
        entity.markDirty();
        super.remove(entity);
    }

    public boolean isOwner(Resource entity, Integer possibleOwner) {
        boolean is = false;

        if (possibleOwner == null) {
            _log.error("possible Owner is NULL. " + "This is probably not what you want.");
            /* XXX throw exception instead */
        } else {
            /* overlord owns every thing */
            if (is = possibleOwner.equals(AuthzConstants.overlordId) == false) {
                if (_log.isDebugEnabled() && possibleOwner != null) {
                    _log.debug("User is " + possibleOwner + " owner is " +
                               entity.getOwner().getId());
                }
                is = (possibleOwner.equals(entity.getOwner().getId()));
            }
        }
        return is;
    }

    public Resource findByInstanceId(ResourceType type, Integer id) {
        return findByInstanceId(type.getId(), id);
    }

    @SuppressWarnings("unchecked")
    List<Resource> findResourcesOfType(int typeId, PageInfo pInfo) {
        String sql = "from Resource r where resourceType.id = :typeId ";
        ResourceSortField sort = (ResourceSortField) pInfo.getSort();

        sql += " order by " + sort.getSortString("r") + (pInfo.isAscending() ? "" : " DESC");

        return pInfo.pageResults(getSession().createQuery(sql).setInteger("typeId", typeId)).list();
    }

    public Resource findByInstanceId(Integer typeId, Integer id) {
        // Resource table is often updated. Manage the instance id and type
        // to resource mapping manually to avoid the query cache getting
        // invalidated on updates to the resource table.
        Cache ridCache = CacheManager.getInstance().getCache("ResourceByInstanceId");
        String key = typeId.toString() + id;

        Element e = ridCache.get(key);
        if (e != null) {
            Integer rid = (Integer) e.getObjectValue();
            return get(rid);
        } else {
            String sql = "from Resource where instanceId = ? and " + "resourceType.id = ?";
            Resource r = (Resource) getSession().createQuery(sql).setInteger(0, id.intValue())
                .setInteger(1, typeId.intValue()).uniqueResult();

            if (r != null) {
                ridCache.put(new Element(key, r.getId()));
            }

            return r;
        }
    }

    /**
     * Find a Resource by type Id and instance Id, allowing for the query to
     * return a stale copy of the resource (for efficiency reasons).
     * 
     * @param typeId The type Id.
     * @param id The instance Id.
     * @param allowStale <code>true</code> to allow stale copies of an alert
     *        definition in the query results; <code>false</code> to never allow
     *        stale copies, potentially always forcing a sync with the database.
     * @return The Resource.
     */
    public Resource findByInstanceId(Integer typeId, Integer id, boolean allowStale) {
        FlushMode oldFlushMode = this.getSession().getFlushMode();

        try {
            if (allowStale) {
                this.getSession().setFlushMode(FlushMode.MANUAL);
            }

            return findByInstanceId(typeId, id);
        } finally {
            this.getSession().setFlushMode(oldFlushMode);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Resource> findByResource(AuthzSubject subject, Resource r) {
        final String[] VIEW_APPDEFS = new String[] { AuthzConstants.platformOpViewPlatform,
                                                    AuthzConstants.serverOpViewServer,
                                                    AuthzConstants.serviceOpViewService, };

        EdgePermCheck wherePermCheck = permissionManager.makePermCheckHql("rez", true);
        String hql = "select rez from Resource rez " + wherePermCheck;

        Query q = createQuery(hql);
        return wherePermCheck.addQueryParameters(q, subject, r, 0, Arrays.asList(VIEW_APPDEFS))
            .list();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findByOwner(AuthzSubject owner) {
        String sql = "from Resource where owner.id = ?";
        return getSession().createQuery(sql).setInteger(0, owner.getId().intValue()).list();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findByOwnerAndType(AuthzSubject owner, ResourceType type) {
        String sql = "from Resource where owner.id = ? and resourceType.id = ?";
        return getSession().createQuery(sql).setInteger(0, owner.getId().intValue()).setInteger(1,
            type.getId().intValue()).list();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findViewableSvcRes_orderName(Integer user, Boolean fSystem) {
        ResourceType serviceType = Bootstrap.getBean(ResourceTypeDAO.class).findByName(
            AuthzConstants.serviceResType);
        Operation op = Bootstrap.getBean(OperationDAO.class).findByTypeAndName(serviceType,
            AuthzConstants.serviceOpViewService);
        final String sql = new StringBuilder(1024).append("SELECT {res.*} ").append(
            "FROM EAM_SUBJECT subject ").append(
            "JOIN EAM_SUBJECT_ROLE_MAP subjrolemap on subject.ID = subjrolemap.SUBJECT_ID ")
            .append("JOIN EAM_ROLE role on role.ID = subjrolemap.ROLE_ID ").append(
                "JOIN EAM_ROLE_RESOURCE_GROUP_MAP rolegrpmap on rolegrpmap.ROLE_ID = role.ID ")
            .append("JOIN EAM_RESOURCE res on res.RESOURCE_TYPE_ID = :resourceTypeId ").append(
                "AND res.FSYSTEM = :system ").append(
                "JOIN EAM_RES_GRP_RES_MAP grpmap on grpmap.RESOURCE_ID = res.ID ").append(
                "JOIN EAM_RESOURCE_GROUP resgrp on resgrp.ID = grpmap.RESOURCE_GROUP_ID ").append(
                "AND rolegrpmap.RESOURCE_GROUP_ID = resgrp.ID ").append(
                "JOIN EAM_ROLE_OPERATION_MAP opmap on role.id = opmap.ROLE_ID ").append(
                "AND opmap.OPERATION_ID = :opId ").append("WHERE subject.ID = :subjectId ").append(
                "UNION ALL ").append("SELECT {res.*} ").append("FROM EAM_RESOURCE res ").append(
                "WHERE res.SUBJECT_ID = :subjectId AND res.RESOURCE_TYPE_ID = :resourceTypeId ")
            .append("AND res.FSYSTEM = :system ").toString();

        List<Resource> resources = getSession().createSQLQuery(sql)
            .addEntity("res", Resource.class).setBoolean("system", fSystem.booleanValue())
            .setInteger("opId", op.getId().intValue()).setInteger("subjectId", user.intValue())
            .setInteger("resourceTypeId", serviceType.getId().intValue()).list();

        // use TreeSet to eliminate dups and sort by Resource
        return new TreeSet<Resource>(resources);

    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findSvcRes_orderName(Boolean fSystem) {
        String sql = "select r from Resource r join r.resourceType rt "
                     + "where r.system = :system and " + "(rt.name = :resSvcType or "
                     + "exists (select rg from ResourceGroup rg " + "join rg.resource r2 "
                     + "where r = r2 and rg.groupType = 15)) " + "order by r.sortName ";

        return getSession().createQuery(sql).setBoolean("system", fSystem.booleanValue())
            .setString("resSvcType", AuthzConstants.serviceResType).list();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findInGroupAuthz_orderName(Integer userId, Integer groupId,
                                                           Boolean fSystem) {
        String sql = "select distinct r from Resource r " + " join r.resourceGroups rgg"
                     + " join r.resourceGroups rg " + " join rg.roles role "
                     + " join role.subjects subj " + " join role.operations op " + "where "
                     + " r.system = :system and " + " rgg.id = :groupId and "
                     + " (subj.id = :subjectId or " + "  r.owner.id = :subjectId or "
                     + "  subj.authDsn = 'covalentAuthzInternalDsn') and "
                     + " op.resourceType.id = r.resourceType.id and " + " ("
                     + "  op.name = 'viewPlatform' or " + "  op.name = 'viewServer' or "
                     + "  op.name = 'viewService' or " + "  op.name = 'viewApplication' or "
                     + "  op.name = 'viewResourceGroup' )" + " order by r.sortName ";
        return getSession().createQuery(sql).setBoolean("system", fSystem.booleanValue())
            .setInteger("groupId", groupId.intValue()).setInteger("subjectId", userId.intValue())
            .list();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findInGroup_orderName(Integer groupId, Boolean fSystem) {
        String sql = "select distinct r from Resource r " + " join r.resourceGroups rgg"
                     + " join r.resourceGroups rg " + " join rg.roles role "
                     + " join role.subjects subj " + " join role.operations op " + "where "
                     + " r.system = :system and " + " rgg.id = :groupId and "
                     + " (subj.id=1 or r.owner.id=1 or "
                     + "  subj.authDsn = 'covalentAuthzInternalDsn') and "
                     + " op.resourceType.id = r.resourceType.id and "
                     + " (op.name = 'viewPlatform' or " + "  op.name = 'viewServer' or "
                     + "  op.name = 'viewService' or " + "  op.name = 'viewApplication' or "
                     + "  op.name='viewResourceGroup' )" + " order by r.sortName ";

        return getSession().createQuery(sql).setBoolean("system", fSystem.booleanValue())
            .setInteger("groupId", groupId.intValue()).list();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> findScopeByOperationBatch(AuthzSubject subjLoc, Resource[] resLocArr,
                                                Operation[] opLocArr) {
        StringBuffer sb = new StringBuffer();

        sb.append("SELECT DISTINCT r ").append("FROM Resource r ").append(
            " join r.resourceGroups g ").append(" join g.roles e ").append(" join e.operations o ")
            .append(" join e.subjects s ").append(" WHERE s.id = ? ").append(" AND ( ");

        for (int x = 0; x < resLocArr.length; x++) {
            if (x > 0)
                sb.append(" OR ");
            sb.append(" (o.id=").append(opLocArr[x].getId()).append(" AND r.id=").append(
                resLocArr[x].getId()).append(") ");
        }
        sb.append(")");
        return getSession().createQuery(sb.toString()).setInteger(0, subjLoc.getId().intValue())
            .list();
    }

    /**
     * Returns an ordered list of instance IDs for a given operation.
     */
    @SuppressWarnings("unchecked")
    public List<Integer> findAllResourcesInstancesForOperation(int opId) {
        final String sql = "SELECT r.instanceId FROM Resource r, Operation o "
                           + "WHERE     o.resourceType = r.resourceType" + "      AND o.id = :opId";

        return getSession().createQuery(sql).setInteger("opId", opId).list();
    }

    int reassignResources(int oldOwner, int newOwner) {
        return getSession().createQuery(
            "UPDATE Resource " + "SET owner.id = :newOwner " + "WHERE owner.id = :oldOwner")
            .setInteger("oldOwner", oldOwner).setInteger("newOwner", newOwner).executeUpdate();
    }

    boolean resourcesExistOfType(String typeName) {
        String sql = "select r from Resource r " + "join r.prototype p "
                     + "where p.name = :protoName";

        return getSession().createQuery(sql).setParameter("protoName", typeName).setMaxResults(1)
            .list().isEmpty() == false;
    }

    @SuppressWarnings("unchecked")
    List<Resource> findResourcesOfPrototype(Resource proto, PageInfo pInfo) {
        String sql = "select r from Resource r where r.prototype = :proto";

        return pInfo.pageResults(getSession().createQuery(sql).setParameter("proto", proto)).list();
    }

    Resource findResourcePrototypeByName(String name) {
        String sql = "select r from Resource r " + "where r.name = :name "
                     + " AND r.resourceType.id in (:platProto, :svrProto, :svcProto)";

        return (Resource) getSession().createQuery(sql).setParameter("name", name).setParameter(
            "platProto", AuthzConstants.authzPlatformProto).setParameter("svrProto",
            AuthzConstants.authzServerProto).setParameter("svcProto",
            AuthzConstants.authzServiceProto).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    List<Resource> findAllAppdefPrototypes() {
        String sql = "select r from Resource r "
                     + "where r.resourceType.id in (:platProto, :svrProto, :svcProto)";

        return getSession().createQuery(sql)
            .setParameter("platProto", AuthzConstants.authzPlatformProto)
            .setParameter("svrProto", AuthzConstants.authzServerProto)
            .setParameter("svcProto", AuthzConstants.authzServiceProto)
            .list();
    }

    @SuppressWarnings("unchecked")
    List<Resource> findAppdefPrototypes() {
        String sql = "select distinct r.prototype from Resource r "
                     + "where r.resourceType.id in (:platProto, :svrProto, :svcProto) ";

        return getSession().createQuery(sql)
            .setParameter("platProto", AuthzConstants.authzPlatform)
            .setParameter("svrProto", AuthzConstants.authzServer)
            .setParameter("svcProto", AuthzConstants.authzService)
            .list();
    }

    public int getPlatformCountMinusVsphereVmPlatforms() {
        String sql = "select count(*) from Resource r " + "where r.resourceType.id = :platProto "
                     + "and r.prototype.name != :vspherevm";
        return ((Number) getSession().createQuery(sql).setInteger("platProto",
            AuthzConstants.authzPlatform.intValue()).setString("vspherevm",
            AuthzConstants.platformPrototypeVmwareVsphereVm).uniqueResult()).intValue();
    }

    @SuppressWarnings("unchecked")
    public Collection<Resource> getUnconfiguredResources() {
        String hql = "from Resource r " +
                     "where resourceType.id in (:platformType, :serverType, :serviceType) " +
                     "and r not in (select resource from Measurement) ";
        Collection<Resource> rtn =
            getSession().createQuery(hql)
                        .setParameter("platformType", AuthzConstants.authzPlatform)
                        .setParameter("serverType", AuthzConstants.authzServer)
                        .setParameter("serviceType", AuthzConstants.authzService)
                        .list();
        final ServerManager sMan = Bootstrap.getBean(ServerManager.class);
        for (final Iterator<Resource> it=rtn.iterator(); it.hasNext(); ) {
            final Resource r = it.next();
            if (r == null || r.isInAsyncDeleteState()) {
                it.remove();
                continue;
            }
            if (r.getResourceType().getId().equals(AuthzConstants.authzServer)) {
                try {
                    final Server server = sMan.findServerById(r.getInstanceId());
                    if (server.getServerType().isVirtual()) {
                        it.remove();
                    }
                } catch (ServerNotFoundException e) {
                    _log.debug(e,e);
                    continue;
                }
            }
        }
        return rtn;
    }

}
