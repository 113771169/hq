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
package org.hyperic.hq.events.server.session;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.hibernate.FlushMode;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hibernate.PageInfo;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceDAO;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.EdgePermCheck;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.dao.HibernateDAO;
import org.hyperic.hq.dao.HibernateDAOFactory;
import org.hyperic.hq.escalation.server.session.Escalation;
import org.hyperic.hq.events.AlertSeverity;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.events.shared.ActionValue;
import org.hyperic.hq.events.shared.AlertConditionValue;
import org.hyperic.hq.events.shared.AlertDefinitionValue;
import org.hyperic.hq.events.shared.RegisteredTriggerValue;

public class AlertDefinitionDAO extends HibernateDAO {
    private static final String[] MANAGE_ALERTS_OPS = new String[] { 
        AuthzConstants.platformOpManageAlerts,
        AuthzConstants.serverOpManageAlerts,
        AuthzConstants.serviceOpManageAlerts 
    };

    public AlertDefinitionDAO(DAOFactory f) {
        super(AlertDefinition.class, f);
    }

    void remove(AlertDefinition def) {
        super.remove(def.getAlertDefinitionState());
        super.remove(def);
    }
    
    Session getNewSession() {
        return HibernateDAOFactory.getInstance()
                .getSessionFactory().openSession();
    }

    public List findAllByResource(Resource r) {
        return createCriteria().add(Restrictions.eq("resource", r)).list();
    }
    
    public List findAllDeletedResources() {
        return createCriteria()
            .add(Restrictions.isNull("resource"))
            .add(Restrictions.eq("deleted", Boolean.TRUE))
            .list();
    }
    
    /**
     * Find the alert def for a given appdef entity and is child of the parent
     * alert def passed in
     * @param ent      Entity to find alert defs for
     * @param parentId ID of the parent
     */
    public AlertDefinition findChildAlertDef(Resource res, Integer parentId) {
        String sql = "FROM AlertDefinition a WHERE " + 
            "a.resource = :res AND a.deleted = false AND a.parent.id = :parent";
        
        List defs = getSession().createQuery(sql)
            .setParameter("res", res)
            .setInteger("parent", parentId.intValue())
            .list();
        
        if (defs.size() == 0) {
            return null;
        }

        return (AlertDefinition) defs.get(0);
    }
    
    /**
     * Find the alert def for a given appdef entity that is the child of the 
     * parent alert def passed in, allowing for the query to return a stale copy 
     * of the alert definition (for efficiency reasons).
     * 
     * @param ent
     * @param parentId
     * @param allowStale <code>true</code> to allow stale copies of an alert 
     *                   definition in the query results; <code>false</code> to 
     *                   never allow stale copies, potentially always forcing a 
     *                   sync with the database.
     * @return The alert definition or <code>null</code>.
     */
    public AlertDefinition findChildAlertDef(Resource res, 
                                             Integer parentId, 
                                             boolean allowStale) {
        Session session = this.getSession();
        FlushMode oldFlushMode = session.getFlushMode();
        
        try {
            if (allowStale) {
                session.setFlushMode(FlushMode.MANUAL);                
            }
            
            return findChildAlertDef(res, parentId);
        } finally {
            session.setFlushMode(oldFlushMode);
        } 
    }

    public AlertDefinition findById(Integer id) {
        return (AlertDefinition) super.findById(id);
    }
    
    /**
     * Find an alert definition by Id, loading from the given session.
     * 
     * @param id The alert definition Id.
     * @param session The session to use for loading the alert definition.
     * @return The alert definition.               
     * @throws ObjectNotFoundException if no alert definition with the give Id exists.
     */
    public AlertDefinition findById(Integer id, Session session) {
        return (AlertDefinition)session.load(getPersistentClass(), id);            
    }
    
    /** 
     * Find an alert definition by Id, loading from the current session.
     * 
     * @param id The alert definition Id.
     * @return The alert definition or <code>null</code> if no alert definition 
     *         exists with the given Id.
     */
    public AlertDefinition get(Integer id) {
        return (AlertDefinition)super.get(id);
    }
    
    private List findByResource(Resource res, String sort, boolean asc) {
        String sql = "from AlertDefinition a where a.resource = :res and " +
            "a.deleted = false order by a." + sort + (asc ? " ASC" : " DESC");
        
        return getSession().createQuery(sql)
            .setParameter("res", res)
            .list();
    }

    public List findByResource(Resource res) {
        return findByResource(res, true);
    }

    public List findByResource(Resource res, boolean asc) {
        return findByResource(res, "name", asc);
    }

    public List findByResourceSortByCtime(Resource res, boolean asc) {
        return findByResource(res, "ctime", asc);
    }
    
    /**
     * Return all alert definitions for the given resource and its descendants
     * @param res the root resource
     * @return
     */
    public List findByRootResource(AuthzSubject subject, Resource r) {
        EdgePermCheck wherePermCheck = 
            getPermissionManager().makePermCheckHql("rez");
        String hql = "select ad from AlertDefinition ad join ad.resource rez " +
            wherePermCheck; 
        
        Query q = createQuery(hql);

        return wherePermCheck
            .addQueryParameters(q, subject, r, 0,
                                Arrays.asList(MANAGE_ALERTS_OPS)).list();
    }
    
    void save(AlertDefinition def) {
        super.save(def);

        // Make sure there's a valid alert definition state
        if (def.getAlertDefinitionState() == null) {
            AlertDefinitionState state = new AlertDefinitionState(def);
            def.setAlertDefinitionState(state);
            super.save(state);
        }
    }
    
    int deleteByAlertDefinition(AlertDefinition def) {
        String sql = "update AlertDefinition " +
        		     "set escalation = null, deleted = true, parent = null, " +
        		         "active = false where parent = :def";

        int ret = getSession().createQuery(sql).setParameter("def", def)
                              .executeUpdate();
        def.getChildrenBag().clear();
        
        return ret;
    }

    void setAlertDefinitionValue(AlertDefinition def, AlertDefinitionValue val)
    {
        AlertConditionDAO cDAO =
            new AlertConditionDAO(DAOFactory.getDAOFactory());
        ActionDAO actDAO = new ActionDAO(DAOFactory.getDAOFactory());
        TriggerDAO tDAO = new TriggerDAO(DAOFactory.getDAOFactory());
        
        setAlertDefinitionValueNoRels(def, val);
    
        for (Iterator i=val.getAddedTriggers().iterator(); i.hasNext(); ) {
            RegisteredTriggerValue tVal = (RegisteredTriggerValue) i.next();
            def.addTrigger(tDAO.findById(tVal.getId()));
        }
        
        for (Iterator i=val.getRemovedTriggers().iterator(); i.hasNext(); ) {
            RegisteredTriggerValue tVal = (RegisteredTriggerValue)i.next();
            def.removeTrigger(tDAO.findById(tVal.getId()));
        }
        
        for (Iterator i=val.getAddedConditions().iterator(); i.hasNext(); ) {
            AlertConditionValue cVal = (AlertConditionValue)i.next();
            def.addCondition(cDAO.findById(cVal.getId()));
        }
    
        for (Iterator i=val.getRemovedConditions().iterator(); i.hasNext(); ) {
            AlertConditionValue cVal = (AlertConditionValue)i.next();
            def.removeCondition(cDAO.findById(cVal.getId()));
        }
    
        for (Iterator i=val.getAddedActions().iterator(); i.hasNext(); ) {
            ActionValue aVal = (ActionValue)i.next();
            def.addAction(actDAO.findById(aVal.getId()));
        }
    
        for (Iterator i=val.getRemovedActions().iterator(); i.hasNext(); ) {
            ActionValue aVal = (ActionValue)i.next();
            def.removeAction(actDAO.findById(aVal.getId()));
        }
    }

    void setAlertDefinitionValueNoRels(AlertDefinition def,
                                       AlertDefinitionValue val) {
        AlertDefinitionDAO aDAO = DAOFactory.getDAOFactory().getAlertDefDAO();
        TriggerDAO tDAO = DAOFactory.getDAOFactory().getTriggerDAO();
        
        def.setName(val.getName());
        def.setCtime(val.getCtime());
        def.setMtime(val.getMtime());
        if (val.parentIdHasBeenSet() && val.getParentId() != null) {
            def.setParent(aDAO.findById(val.getParentId()));
        }
        def.setDescription(val.getDescription());
        
        def.setActiveStatus(val.getEnabled());
        
        def.setWillRecover(val.getWillRecover());
        def.setNotifyFiltered(val.getNotifyFiltered() );
        def.setControlFiltered(val.getControlFiltered() );
        def.setPriority(val.getPriority());
        
        // def.set the resource based on the entity ID
        ResourceDAO rDao = new ResourceDAO(DAOFactory.getDAOFactory());
        // Don't need to synch the Resource with the db since changes 
        // to the Resource aren't cascaded on saving the AlertDefinition.
        Integer authzTypeId;
        if (EventConstants.TYPE_ALERT_DEF_ID.equals(val.getParentId())) {
            switch(val.getAppdefType()) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                authzTypeId = AuthzConstants.authzPlatformProto;
                break;
            case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                authzTypeId = AuthzConstants.authzServerProto;
                break;
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                authzTypeId = AuthzConstants.authzServiceProto;
                break;
            default:
                throw new IllegalArgumentException("Type " + val.getAppdefType()
                                                   + " is not a valid type");
            }
        }
        else {
            AppdefEntityID aeid = new AppdefEntityID(val.getAppdefType(),
                                                     val.getAppdefId());
            authzTypeId = aeid.getAuthzTypeId();
        }
        def.setResource(rDao.findByInstanceId(authzTypeId,
                                              new Integer(val.getAppdefId()),
                                              true));

        def.setFrequencyType(val.getFrequencyType());
        def.setCount(new Long(val.getCount()));
        def.setRange(new Long(val.getRange()));
        def.setDeleted(val.getDeleted());
        if (val.actOnTriggerIdHasBeenSet()) {
            def.setActOnTrigger(tDAO.findById(new Integer(val.getActOnTriggerId())));
        }
    }
    
    List findDefinitions(AuthzSubject subj, AlertSeverity minSeverity, 
                         Boolean enabled, boolean excludeTypeBased, 
                         PageInfo pInfo)
    {
        String sql = PermissionManagerFactory.getInstance().getAlertDefsHQL();
        
        sql += " and d.deleted = false";
        if (enabled != null) {
            sql += " and d.enabled = " + 
                   (enabled.booleanValue() ? "true" : "false");
        }
        
        sql += " and (d.parent is null";
        if (excludeTypeBased) {
            sql += ") ";            
        } else {
            sql += " or not d.parent.id = 0) ";
        }

        sql += getOrderByClause(pInfo);
               
        Query q = getSession().createQuery(sql)
            .setInteger("priority", minSeverity.getCode());

        if (sql.indexOf("subj") > 0) {
            q.setInteger("subj", subj.getId().intValue())
             .setParameterList("ops", MANAGE_ALERTS_OPS);
        }
        
        return pInfo.pageResults(q).list();
    }

    private String getOrderByClause(PageInfo pInfo) {
        AlertDefSortField sort = (AlertDefSortField)pInfo.getSort();
        String res = " order by " + sort.getSortString("d", "r") + 
            (pInfo.isAscending() ? "" : " DESC");
        
        if (!sort.equals(AlertDefSortField.CTIME)) {
            res += ", " + AlertDefSortField.CTIME.getSortString("d", "r") + 
                   " DESC";
        }
        return res;
    }
    
    List findTypeBased(Boolean enabled, PageInfo pInfo) {
        String sql = "from AlertDefinition d " + 
            "where d.deleted = false and d.parent.id = 0 ";
        
        if (enabled != null) {
            sql += " and d.enabled = " + 
                (enabled.booleanValue() ? "true" : "false");
        }
        sql += getOrderByClause(pInfo);
                   
        Query q = getSession().createQuery(sql);
        
        return pInfo.pageResults(q).list();
    }
    
    List getUsing(Escalation e) {
        return createCriteria().add(Restrictions.eq("escalation", e)).list();
    }
    
    Object[] getEnabledAndTriggerId(Integer id) {
        return (Object[]) getSession()
            .createQuery("select enabled, actOnTrigger.id from AlertDefinition"+
            		     " where id = " + id)
            .uniqueResult();
    }
    
    int setChildrenActive(AlertDefinition def, boolean active) {
        return createQuery("update AlertDefinition set active = :active, " +
                           "enabled = :active, mtime = :mtime " +
                           "where parent = :def")
            .setBoolean("active", active)
            .setLong("mtime", System.currentTimeMillis())
            .setParameter("def", def)
            .executeUpdate();
    }

}
