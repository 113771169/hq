/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2009], Hyperic, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hibernate.PageInfo;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.authz.server.session.ResourceGroup.ResourceGroupCreateInfo;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.GroupCreationException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.dao.HibernateDAO;
import org.hyperic.util.pager.PageList;

public class ResourceGroupDAO extends HibernateDAO
{
    private static final Log _log
        = LogFactory.getLog(ResourceGroupDAO.class.getName());
    private static final Integer rootResourceGroupId =
        AuthzConstants.rootResourceGroupId;
    
    public ResourceGroupDAO(DAOFactory f) {
        super(ResourceGroup.class, f);
    }

    private void assertNameConstraints(String name) 
        throws GroupCreationException
    {
        if (name == null || name.length() == 0 || name.length() > 100)
            throw new GroupCreationException("Group name must be between " +
                                             "1 and 100 characters in length");
    }

    private void assertDescriptionConstraints(String desc) 
        throws GroupCreationException
    {
        if (desc != null && desc.length() > 100)
            throw new GroupCreationException("Group description must be " + 
                                     "between 1 and 100 characters in length");
    }

    private void assertLocationConstraints(String loc) 
        throws GroupCreationException
    {
        if (loc != null && loc.length() > 100)
            throw new GroupCreationException("Group location must be " + 
                                     "between 1 and 100 characters in length");
    }
    
    ResourceGroup create(AuthzSubject creator, ResourceGroupCreateInfo cInfo,
                         Collection resources, Collection roles)
        throws GroupCreationException
    {
        assertNameConstraints(cInfo.getName());
        assertDescriptionConstraints(cInfo.getDescription());
        assertLocationConstraints(cInfo.getLocation());
        
        switch(cInfo.getGroupType()) {
        case AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_APP:
        case AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_GRP:
        case AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_PSS:
            if (cInfo.getResourcePrototype() != null) {
                throw new GroupCreationException("Cannot specify a prototype "+
                                                 "for mixed groups");
            }
            break;
        case AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_PS:
        case AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC:
            if (cInfo.getResourcePrototype() == null) {
                throw new GroupCreationException("Compatable groups must " +
                                                 "specify a prototype");
            }
            break;
        }
        
        ResourceGroup resGrp = new ResourceGroup(cInfo, creator);

        ResourceType resType = new ResourceTypeDAO(DAOFactory.getDAOFactory())
            .findById(AuthzConstants.authzGroup);
        
        assert resType != null;
        final ResourceDAO rDao = new ResourceDAO(DAOFactory.getDAOFactory());
        final Resource proto = rDao.findById(AuthzConstants.rootResourceId);
        Resource r = cInfo.isPrivateGroup() ?
            rDao.createPrivate(resType, proto, cInfo.getName(), creator,  
                               resGrp.getId(), cInfo.isSystem()) :
            rDao.create(resType, proto, cInfo.getName(), creator,  
                        resGrp.getId(), cInfo.isSystem());

        resGrp.setResource(r);
        save(resGrp);
        
        /* The following oddity is needed because the above rDao.create()
         * flushes the session.  If we don't refresh the object, then 
         * changing the instanceId here doens't seem to do anything.  This
         * is definitely a hacky workaround for a Hibernate issue. */
        r = rDao.findById(r.getId());
        getSession().refresh(r);
        r.setInstanceId(resGrp.getId());
        save(r);
        flushSession();

        setMembers(resGrp, new HashSet(resources));
        resGrp.setRoles(new HashSet(roles));
        
        return resGrp;
    }
    
    ResourceGroup findResourceGroup(Resource resource) {
        final String hql = "from ResourceGroup where resource = :resource";
        return (ResourceGroup)createQuery(hql)
                .setParameter("resource", resource)
                .uniqueResult();
    }
    
    void removeAllMembers(ResourceGroup group) {
        // Don't want to mark the Root Resource Group dirty to avoid optimistic
        // locking issues.  Since the root group is associated with all
        // resources, transactions which involve creating/deleting resources
        // are not self-contained and therefore any changes to this object
        // would make these types of transactions potentially fail.
        if (!group.getId().equals(rootResourceGroupId)) {
            group.markDirty();
        }
        createQuery("delete from GroupMember g " + 
                    "where g.group = :group")
            .setParameter("group", group)
            .executeUpdate();
    }

    boolean isMember(ResourceGroup group, Resource resource) {
        GroupMember gm = (GroupMember) 
            createQuery("from GroupMember g where g.group = :group " + 
                        " and g.resource = :resource")
                .setParameter("group", group)
                .setParameter("resource", resource)
                .uniqueResult();
        
        return gm != null; 
    }

    void removeMembers(ResourceGroup group, Collection members) {
        // Don't want to mark the Root Resource Group dirty to avoid optimistic
        // locking issues.  Since the root group is associated with all
        // resources, transactions which involve creating/deleting resources
        // are not self-contained and therefore any changes to this object
        // would make these types of transactions potentially fail.
        if (!group.getId().equals(rootResourceGroupId)) {
            group.markDirty();
        }
        
        List memberIds = new ArrayList(members.size());
        
        for (Iterator i=members.iterator(); i.hasNext(); ) {
            Resource r = (Resource)i.next();
            memberIds.add(r.getId());
        }
        int numDeleted = 
            createQuery("delete from GroupMember where group = :group " +
                        "and resource.id in (:members)")
            .setParameter("group", group)
            .setParameterList("members", memberIds)
            .executeUpdate();
        
        if (numDeleted != members.size()) {
            _log.warn("Expected to delete " + members.size() + " members " + 
                      "but only deleted " + numDeleted + " (group=" + 
                      group.getId());
        }
    }
    
    void addMember(ResourceGroup group, Resource resource) {
        addMembers(group, Collections.singleton(resource));
    }
    
    void addMembers(ResourceGroup group, Collection resources) {
        Session sess = getSession();
        
        // Don't want to mark the Root Resource Group dirty to avoid optimistic
        // locking issues.  Since the root group is associated with all
        // resources, transactions which involve creating/deleting resources
        // are not self-contained and therefore any changes to this object
        // would make these types of transactions potentially fail.
        if (!group.getId().equals(rootResourceGroupId)) {
            group.markDirty();
        }
        for (Iterator i=resources.iterator(); i.hasNext(); ) {
            Resource r = (Resource)i.next();
            GroupMember m = new GroupMember(group, r);
            
            sess.save(m);
        }
    }
    
    void setMembers(ResourceGroup group, Collection resources) {
        removeAllMembers(group);
        addMembers(group, resources);
    }
    
    /**
     * Get groups that a resource belongs to via the persistence mechanism
     * (i.e. mapping table)
     * 
     * @return {@link ResourceGroup}s
     */
    Collection getGroups(Resource r) {
        return createQuery("select g.group from GroupMember g " + 
                           "where g.resource = :resource")
            .setParameter("resource", r)
            .list();
    }
    
    /**
     * Get resources belonging to a group via the persistence mechanism.
     * 
     * @return {@link Resource}s
     */
    Collection getMembers(ResourceGroup g) {
        return createQuery("select g.resource from GroupMember g " +
                           "where g.group = :group " +
                           "and g.resource.resourceType is not null " +
                           "order by g.resource.name")
            .setParameter("group", g)
            .list();
    }
    
    /**
     * Get counts of resources mapped by type name.
     * 
     * @return {@link Resource}s
     */
    Map getMemberTypes(ResourceGroup g) {
        List counts =
            createQuery("select p.name, count(r) from GroupMember g " +
                        "join g.resource r " +
                        "join r.prototype p " +
                         "where g.group = :group group by p.name")
            .setParameter("group", g)
            .list();
        Map types = new HashMap();
        for (Iterator it = counts.iterator(); it.hasNext(); ) {
            Object[] objs = (Object[]) it.next();
            types.put(objs[0], objs[1]);
        }
        return types;
    }
    
    public ResourceGroup findById(Integer id) {
        return (ResourceGroup) super.findById(id);
    }

    public ResourceGroup get(Integer id) {
        return (ResourceGroup) super.get(id);
    }

    public void save(ResourceGroup entity) {
        super.save(entity);
    }

    public void remove(ResourceGroup entity) {
        // remove all roles
        entity.getRoles().clear();
        removeAllMembers(entity);

        Resource res = entity.getResource();
        
        // remove this resourceGroup itself
        super.remove(entity);
        flushSession();

        ResourceDAO dao = new ResourceDAO(DAOFactory.getDAOFactory());
        dao.remove(res);
    }
    
    public ResourceGroup findRootGroup() {
        ResourceGroup res = findByName(AuthzConstants.rootResourceGroupName);
        
        if (res == null) {
            throw new SystemException("Root group should exist");
        }
        return res;
    }
        
    public ResourceGroup findByName(String name) {            
        String sql =
            "from ResourceGroup g where lower(g.resource.name) = lower(?)";
        return (ResourceGroup)getSession().createQuery(sql)
            .setString(0, name)
            .setCacheable(true)
            .setCacheRegion("ResourceGroup.findByName")
            .uniqueResult();
    }
    
    public Collection findByRoleIdAndSystem_orderName(Integer roleId,
                                                      boolean system,
                                                      boolean asc) {            
        String sql = "select g from ResourceGroup g join fetch g.roles r " +
                     "where r.id = ? and g.system = ? " +
                     "order by g.resource.sortName " + (asc ? "asc" : "desc");
        return getSession().createQuery(sql)
            .setInteger(0, roleId.intValue())
            .setBoolean(1, system)
            .list();
    }

    public Collection findWithNoRoles_orderName(boolean asc) {            
        String sql = "from ResourceGroup g " +
                     "where g.roles.size = 0 and g.system = false " +
                     "order by g.resource.sortName " + (asc ? "asc" : "desc");
        return getSession().createQuery(sql).list();
    }

    public Collection findByNotRoleId_orderName(Integer roleId, boolean asc)
    {
        return getSession()
            .createQuery("from ResourceGroup g " +
                         "where ? not in (select id from g.roles) and " +
                         "g.system = false order by g.resource.sortName " +
                         (asc ? "asc" : "desc"))
            .setInteger(0, roleId.intValue())
            .list();
    }

    public Collection findCompatible(Resource proto) {
        String sql =
            "from ResourceGroup g " +
            "where g.resourcePrototype = ? and " +
            "(g.groupType = ? or g.groupType = ?)";

        return getSession().createQuery(sql)
            .setParameter(0, proto)
            .setInteger(1, AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_PS)
            .setInteger(2, AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC)
            .list();
    }

    /**
     * Return the maximum collection interval for the given template within
     * the group.
     *
     * @param g The group in question.
     * @param templateId The measurement template to query.
     * @return templateId The maximum collection time in milliseconds.
     */
    public Long getMaxCollectionInterval(ResourceGroup g, Integer templateId) {
        String sql =
            "select max(m.interval) from Measurement m, GroupMember g " +
            "join g.group rg " +
            "join g.resource r " +
            "where m.instanceId = r.instanceId and "+
            "rg = ? and m.template.id = ?";

        return (Long)getSession().createQuery(sql)
            .setParameter(0, g)
            .setInteger(1, templateId.intValue())
            .setCacheable(true)
            .setCacheRegion("ResourceGroup.getMaxCollectionInterval")
            .uniqueResult();
    }

    /**
     * Return a List of Measurements that are collecting for the given
     * template ID and group.
     *
     * @param g The group in question.
     * @param templateId The measurement template to query.
     * @return templateId A list of Measurement objects with the given template
     * id in the group that are set to be collected.
     */
    public List getMetricsCollecting(ResourceGroup g, Integer templateId) {
        String sql =
            "select m from Measurement m, GroupMember g " +
            "join g.group rg " +
            "join g.resource r " +
            "where m.instanceId = r.instanceId and "+
            "rg = ? and m.template.id = ? and m.enabled = true";

        return getSession().createQuery(sql)
            .setParameter(0, g)
            .setInteger(1, templateId.intValue())
            .setCacheable(true)
            .setCacheRegion("ResourceGroup.getMetricsCollecting")
            .list();
    }
    
    PageList findGroupsClusionary(AuthzSubject subject, Resource member,
                                  Resource prototype, Collection excludeGroups,
                                  PageInfo pInfo, boolean inclusive)
    {
        ResourceGroupSortField sort = (ResourceGroupSortField)pInfo.getSort();
        String hql = "from ResourceGroup g where g.system = false and ";
        
        if (prototype != null) {
            hql += " (g.resourcePrototype = :proto ";
            
            // Mixed groups, too
            
            Integer protoType = prototype.getResourceType().getId();
            if (protoType.equals(AuthzConstants.authzPlatformProto) ||
                protoType.equals(AuthzConstants.authzServerProto)   ||
                protoType.equals(AuthzConstants.authzService)) {
                hql += " or g.groupType = " +
                       AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_PSS;
            } else if (protoType.equals(AuthzConstants.authzApplicationProto)) {
                hql += " or g.groupType = " +
                       AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_APP;
            }
                
            hql += ") and ";
        }
        
        List excludes = new ArrayList(excludeGroups.size());
        for (Iterator i=excludeGroups.iterator(); i.hasNext(); ) {
            ResourceGroup g = (ResourceGroup)i.next();
            
            excludes.add(g.getId());
        }
        if (!excludes.isEmpty())
            hql += " g.id not in (:excludes) and ";
        
        String inclusionStr = "";
        if (!inclusive) 
            inclusionStr = " not ";
        
        PermissionManager pm = PermissionManagerFactory.getInstance();
        hql += inclusionStr + " exists ( " + 
               " select m.id from GroupMember m " + 
               " where m.resource = :resource and m.group = g " + 
               ") ";
        
        String pmql = pm.getOperableGroupsHQL(subject, "g",
               inclusive ? AuthzConstants.groupOpViewResourceGroup : 
                           AuthzConstants.groupOpModifyResourceGroup);
        
        if (pmql.length() > 0)
            hql += pmql;
        
        String countHql  = "select count(g.id) " + hql;
        String actualHql = "select g " + hql + " order by " + 
            sort.getSortString("g");
      
        Query q = getSession().createQuery(countHql)
            .setParameter("resource", member);
        
        if (!excludes.isEmpty())
            q.setParameterList("excludes", excludes);
        
        if (prototype != null)
            q.setParameter("proto", prototype);
        
        if (pmql.length() > 0)
            q.setInteger("subjId", subject.getId().intValue());
            
        int total = ((Number)(q.uniqueResult())).intValue();
        q = getSession().createQuery(actualHql)
            .setParameter("resource", member);
        
        if (prototype != null)
            q.setParameter("proto", prototype);

        if (!excludes.isEmpty())
            q.setParameterList("excludes", excludes);
        
        if (pmql.length() > 0)
            q.setInteger("subjId", subject.getId().intValue());

        List vals = pInfo.pageResults(q).list();
        return new PageList(vals, total);
    }
    
    public Collection findByGroupType_orderName(boolean isAscending,
                                                int groupType) {
        String sql = "from ResourceGroup g where g.groupType = :type" +
                     " ORDER BY g.resource.name " +
                     ((isAscending) ? "asc" : "desc");
        return getSession()
            .createQuery(sql)
            .setInteger("type", groupType)
            .list();
    }

    public Collection findDeletedGroups() {
        String hql = "from ResourceGroup where resource.resourceType = null";
        return createQuery(hql).list();
    }
}
