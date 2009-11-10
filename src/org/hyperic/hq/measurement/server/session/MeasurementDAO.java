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

package org.hyperic.hq.measurement.server.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.FlushMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.IntegerType;
import org.hyperic.hibernate.Util;
import org.hyperic.hibernate.dialect.HQDialect;
import org.hyperic.hq.appdef.server.session.AgentManagerImpl;
import org.hyperic.hq.appdef.shared.AgentManager;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.dao.HibernateDAO;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
@Repository

public class MeasurementDAO extends HibernateDAO<Measurement> {
    private static final String ALIAS_CLAUSE = " upper(t.alias) = '" +
    				MeasurementConstants.CAT_AVAILABILITY.toUpperCase() + "' ";

    @Autowired
    public MeasurementDAO(SessionFactory f) {
        super(Measurement.class, f);
    }

    public Measurement findById(Integer id) {
        return (Measurement)super.findById(id);
    }

    public Measurement get(Integer id) {
        return (Measurement)super.get(id);
    }

    void remove(Measurement entity) {
        super.remove(entity);
    }

    public void removeBaseline(Measurement m) {
        m.setBaseline(null);
    }

    /**
     * retrieves List<Object[]>
     * [0] = Measurement
     * [1] = MeasurementTemplate
     */
    List<Measurement> findAllEnabledMeasurementsAndTemplates() {
        Dialect dialect = Util.getDialect();
        String hql = new StringBuilder()
            .append("from Measurement m")
            .append(" join m.template t")
            .append(" where enabled = ")
            .append(dialect.toBooleanValueString(true))
            .toString();
        return getSession().createQuery(hql).list();
    }

    /**
     * Remove all measurements associated with a MeasurementTemplate
     * @param mt The MeasurementTemplate for the Measurements to be removed.
     */
    void remove(MeasurementTemplate mt) {
        String sql = "from Measurement where template.id=?";
        List<Measurement> measurements = getSession().createQuery(sql)
            .setInteger(0, mt.getId().intValue())
            .list();



        for (Iterator it = measurements.iterator(); it.hasNext();) {

            Measurement meas = (Measurement) it.next();
            remove(meas);
        }
    }

    Measurement create(Resource resource,
                       MeasurementTemplate mt,
                       String dsn,
                       long interval) {
        Measurement m = new Measurement(resource.getInstanceId(),
                                        mt, interval);

        m.setEnabled(interval != 0);
        m.setDsn(dsn);
        m.setResource(resource);
        save(m);
        return m;
    }

    /**
     * Look up a Measurement, allowing for the query to return a stale
     * copy (for efficiency reasons).
     *
     * @param tid The MeasurementTemplate id
     * @param iid The instance id
     * @param allowStale <code>true</code> to allow stale copies of an alert
     *                   definition in the query results; <code>false</code> to
     *                   never allow stale copies, potentially always forcing a
     *                   sync with the database.
     * @return The Measurement or <code>null</code>.
     */
    Measurement findByTemplateForInstance(Integer tid, Integer iid,
                                          boolean allowStale) {
        Session session = getSession();
        FlushMode oldFlushMode = session.getFlushMode();

        try {
            if (allowStale) {
                session.setFlushMode(FlushMode.MANUAL);
            }

            String sql =
                "select distinct m from Measurement m " +
                "join m.template t " +
                "where t.id=? and m.instanceId=?";

            return (Measurement) getSession().createQuery(sql)
                .setInteger(0, tid.intValue())
                .setInteger(1, iid.intValue())
                .setCacheable(true)
                .setCacheRegion("Measurement.findByTemplateForInstance")
                .uniqueResult();
        } finally {
            session.setFlushMode(oldFlushMode);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Measurement> findByTemplatesForInstance(Integer[] tids, Resource res) {
        if (tids.length == 0)   // Nothing to do
            return new ArrayList(0);

        String sql =
            "select m from Measurement m " +
            "join m.template t " +
            "where t.id in (:tids) and m.resource = :res";

        return getSession().createQuery(sql)
            .setParameterList("tids", tids)
            .setParameter("res", res)
            .setCacheable(true)     // Share the cache for now
            .setCacheRegion("Measurement.findByTemplateForInstance")
            .list();
    }

    List findIdsByTemplateForInstances(Integer tid, Integer[] iids) {
        if (iids.length == 0)
            return new ArrayList(0);

        String sql = "select id from Measurement " +
                     "where template.id = :tid and instanceId IN (:ids)";

        return getSession().createQuery(sql)
            .setInteger("tid", tid.intValue())
            .setParameterList("ids", iids)
            .setCacheable(true)
            .setCacheRegion("Measurement.findIdsByTemplateForInstances")
            .list();
    }

    @SuppressWarnings("unchecked")
    List<Measurement> findByTemplate(Integer id) {
        String sql = "select distinct m from Measurement m " +
                     "join m.template t " +
                     "where t.id=?";

        return getSession().createQuery(sql)
               .setInteger(0, id.intValue()).list();
    }

    /**
     * Find the AppdefEntityID objects for all the Measurements
     * associated with the MeasurementTemplate.
     *
     * @param id The measurement template id.
     * @return A list of AppdefEntityID objects.
     */
    @SuppressWarnings("unchecked")
    List<AppdefEntityID> findAppdefEntityIdsByTemplate(Integer id) {
        String sql = "select distinct mt.appdefType, m.instanceId from " +
        		     "Measurement m join m.template t " +
                     "join t.monitorableType mt where t.id=?";
        
        List<Object[]> results = getSession()
                   .createQuery(sql)
                   .setInteger(0, id.intValue())
                   .list();
        
        List<AppdefEntityID> appdefEntityIds = new ArrayList<AppdefEntityID>(results.size());
        
        for (Object[] result : results) {
            int appdefType = ((Integer)result[0]).intValue();
            int instanceId = ((Integer)result[1]).intValue();            
            appdefEntityIds.add(new AppdefEntityID(appdefType, instanceId));
        }
        
        return appdefEntityIds;
    }
    
    List findByResource(Resource resource)
    {
        return createCriteria()
            .add(Restrictions.eq("resource", resource))
            .setCacheable(true)
            .setCacheRegion("Measurement.findByResource")
            .list();
    }

    int deleteByIds(List ids) {
        int count = 0;
        // need to remove one at a time to avoid EhCache clearing the
        // measurement cache which would lead to thrashing
        for (Iterator it=ids.iterator(); it.hasNext(); ) {
            Integer id = (Integer)it.next();
            Measurement meas = findById(id);
            if (meas == null) {
                continue;
            }
            count++;
            remove(meas);
        }
        return count;
    }

    public List findEnabledByResource(Resource resource) {
        if (resource == null || resource.isInAsyncDeleteState()) {
            return Collections.EMPTY_LIST;
        }
        String sql =
            "select m from Measurement m " +
            "join m.template t " +
            "where m.enabled = true and " +
            "m.resource = ? " +
            "order by t.name";

        return getSession().createQuery(sql)
            .setParameter(0, resource)
            .setCacheable(true)
            .setCacheRegion("Measurement.findEnabledByResource").list();
    }

    List findDefaultsByResource(Resource resource) {
        return getSession()
            .createQuery("select m from Measurement m join m.template t " +
                         "where t.defaultOn = true and m.resource = ? " +
                         "order by m.id ")
            .setParameter(0, resource)
            .list();
    }

    List findByInstanceForCategory(int type, int id, String cat) {
        String sql =
            "select m from Measurement m " +
            "join m.template t " +
            "join t.monitorableType mt " +
            "join t.category c " +
            "where mt.appdefType = ? and " +
            "m.instanceId = ? and " +
            "c.name = ?";

        return getSession().createQuery(sql)
            .setInteger(0, type)
            .setInteger(1, id)
            .setString(2, cat).list();
    }

    List findByResourceForCategory(Resource resource, String cat) {
        String sql =
            "select m from Measurement m " +
            "join m.template t " +
            "join t.category c " +
            "where m.resource = ? and m.enabled = true and c.name = ? " +
            "order by t.name";

        return getSession().createQuery(sql)
            .setParameter(0, resource)
            .setString(1, cat).list();
    }

    Measurement findByAliasAndID(String alias, Resource resource) {

        String sql =
            "select distinct m from Measurement m " +
            "join m.template t " +
            "where t.alias = ? and m.resource = ?";

        return (Measurement)getSession().createQuery(sql)
            .setString(0, alias)
            .setParameter(1, resource)
            .uniqueResult();
    }

    List findDesignatedByResourceForCategory(Resource resource, String cat)
    {
        String sql =
            "select m from Measurement m " +
            "join m.template t " +
            "join t.category c " +
            "where m.resource = ? and " +
            "t.designate = true and " +
            "c.name = ? " +
            "order by t.name";

        return getSession().createQuery(sql)
            .setParameter(0, resource)
            .setParameter(1, cat)
            .setCacheable(true)
            .setCacheRegion("Measurement.findDesignatedByResourceForCategory")
            .list();
    }

    List findDesignatedByResource(Resource resource) {
        String sql =
            "select m from Measurement m " +
            "join m.template t " +
            "where m.resource = ? and " +
            "t.designate = true " +
            "order by t.name";

        return getSession().createQuery(sql)
            .setParameter(0, resource)
            .setCacheable(true)
            .setCacheRegion("Measurement.findDesignatedByResource")
            .list();
    }

    List findDesignatedByCategoryForGroup(ResourceGroup g, String cat) {
        String sql =
            "select m from Measurement m, GroupMember gm " +
            "join m.template t " +
            "join t.category c " +
            "where gm.group = :group and gm.resource = m.resource " +
            "and t.designate = true and c.name = :cat order by t.name";

        return getSession().createQuery(sql)
            .setParameter("group", g)
            .setParameter("cat", cat)
            .setCacheable(true)
            .setCacheRegion("Measurement.findDesignatedByCategoryForGroup")
            .list();
        }

    List findByCategory(String cat) {
        String sql =
            "select distinct m from Measurement m " +
            "join m.template t " +
            "join t.monitorableType mt " +
            "join t.category c " +
            "where m.enabled = true " +
            "and c.name = ?";

        return getSession().createQuery(sql)
            .setString(0, cat)
            .setCacheable(true)
            .setCacheRegion("Measurement.findByCategory")
            .list();
    }

    /**
     * @return List of all measurement ids for availability, ordered
     */
    @SuppressWarnings("unchecked")
    List<Integer> findAllAvailIds() {
        String sql = new StringBuilder()
            .append("select m.id from Measurement m ")
            .append("join m.template t ")
            .append("where ")
            .append(ALIAS_CLAUSE)
            .append("and m.resource is not null ")
            .append("order BY m.id").toString();
        return getSession()
            .createQuery(sql)
            .setCacheable(true)
            .setCacheRegion("Measurement.findAllAvailIds")
            .list();
    }

    /**
     * @return {@link Measurement}.  May return null.
     */
    Measurement findAvailMeasurement(Resource resource) {
        List list = Collections.singletonList(resource);
        list = findAvailMeasurements(list);
        if (list.size() == 0) {
            return null;
        }
        return (Measurement)list.get(0);
    }

    List findAvailMeasurements(Collection resources) {
        if (resources.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        List resList = new ArrayList(resources);
        // sort to give the query cache best chance of reuse
        Collections.sort(resList);
        List rtn = new ArrayList(resList.size());
        final String sql = new StringBuilder()
            .append("select m from Measurement m ")
            .append("join m.template t ")
            .append("where m.resource in (:resources) AND ")
            .append(ALIAS_CLAUSE).toString();
        final Query query = getSession().createQuery(sql)
            .setCacheable(true)
            .setCacheRegion("Measurement.findAvailMeasurements");
        
        // should be a unique result if only one resource is being examined
        if (resources.size() == 1) {
            query.setParameterList("resources", resList);            
            Object result = query.uniqueResult();
            if (result != null) {
                rtn.add(result);
            }
        } else {        
            for (int i=0; i<resList.size(); i+=BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, resList.size());
                query.setParameterList("resources", resList.subList(i, end));
                rtn.addAll(query.list());
            }
        }

        return rtn;
    }

    List findAvailMeasurements(ResourceGroup g) {
        String hql = "select m from GroupMember gm, " +
        		     "Measurement m join m.template t " +
        		     "where m.resource = gm.resource and gm.group = :group and "
        		     + ALIAS_CLAUSE;
        return createQuery(hql)
            .setParameter("group", g)
            .setCacheable(true)
            .setCacheRegion("Measurement.findAvailMeasurementsForGroup")
            .list();
    }

    List findMeasurements(Integer[] tids, Integer[] iids) {
        // sort to take advantage of query cache
        final List iidList = Arrays.asList(iids);
        final List tidList = Arrays.asList(tids);
        Collections.sort(tidList);
        Collections.sort(iidList);
        final String sql = new StringBuilder()
            .append("select m from Measurement m ")
            .append("join m.template t ")
            .append("where m.instanceId in (:iids) AND t.id in (:tids)")
            .toString();
        return getSession().createQuery(sql)
            .setParameterList("iids", iidList, new IntegerType())
            .setParameterList("tids", tidList, new IntegerType())
            .setCacheable(true)
            .setCacheRegion("Measurement.findMeasurements")
            .list();
    }

    List findAvailMeasurements(Integer[] tids, Integer[] iids) {
        String sql = new StringBuilder()
            .append("select m from Measurement m ")
            .append("join m.template t ")
            .append("where m.instanceId in (:iids) AND t.id in (:tids) AND ")
            .append(ALIAS_CLAUSE).toString();
        return getSession().createQuery(sql)
            .setParameterList("iids", iids)
            .setParameterList("tids", tids).list();
    }

    /**
     * @param {@link List} of {@link Integer} resource ids
     * @return {@link Object[]}
     * 0 = {@link Integer}
     * 1 = {@link List} of Availability {@link Measurement}s
     * Measurements which are children of the resource
     */
    @SuppressWarnings("unchecked")
    final List<Object[]> findRelatedAvailMeasurements(final List<Integer> resourceIds,
                                            final String resourceRelationType) {
       if (resourceIds.isEmpty()) {
           return Collections.EMPTY_LIST;
       }

       final String sql = new StringBuilder()
           .append("select e.from.id,m from Measurement m ")
           .append("join m.resource.toEdges e ")
           .append("join m.template t ")
           .append("join e.relation r ")
           .append("where m.resource is not null ")
           .append("and e.distance > 0 ")
           .append("and r.name = :relationType ")
           .append("and e.from in (:resourceIds) and ")
           .append(ALIAS_CLAUSE).toString();

       // create a new list so that the original list is not modified
       // and sort the resource ids so that the results are more cacheable
       final List sortedResourceIds = new ArrayList(resourceIds);
       Collections.sort(sortedResourceIds);

       final HQDialect dialect = Util.getHQDialect();
       final int max = (dialect.getMaxExpressions() <= 0) ?
           Integer.MAX_VALUE : dialect.getMaxExpressions();
       final List rtn = new ArrayList(sortedResourceIds.size());
       for (int i=0; i<sortedResourceIds.size(); i+=max) {
           final int end = Math.min(i+max, sortedResourceIds.size());
           final List list = sortedResourceIds.subList(i, end);
           rtn.addAll(getSession()
               .createQuery(sql)
               .setParameterList("resourceIds", list, new IntegerType())
               .setParameter("relationType", resourceRelationType)
               .setCacheable(true)
               .setCacheRegion("Measurement.findRelatedAvailMeasurements")
               .list());
       }
       return rtn;
    }

    /**
     * @param {@link List} of {@link Integer} resource ids
     * @return {@link Object[]}
     * 0 = {@link Integer}
     * 1 = {@link List} of Availability {@link Measurement}s
     * Availability measurements which are parents of the resourceId
     */
    @SuppressWarnings("unchecked")
    List<Object[]> findParentAvailMeasurements(List resourceIds,
                                     String resourceRelationType) {
        if (resourceIds.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        // Needs to be ordered by DISTANCE in descending order so that
        // it's immediate parent is the first record
       final String sql = new StringBuilder()
           .append("select e.from.id, m from Measurement m ")
           .append("join m.resource.toEdges e ")
           .append("join m.template t ")
           .append("join e.relation r ")
           .append("where m.resource is not null ")
           .append("and e.distance < 0 ")
           .append("and r.name = :relationType ")
           .append("and e.from in (:resourceIds) and ")
           .append(ALIAS_CLAUSE)
           .append("order by e.from.id, e.distance desc ").toString();

       // create a new list so that the original list is not modified
       // and sort the resource ids so that the results are more cacheable
       final List sortedResourceIds = new ArrayList(resourceIds);
       Collections.sort(sortedResourceIds);

       final List rtn = new ArrayList(sortedResourceIds.size());
       final HQDialect dialect = Util.getHQDialect();
       final int max = (dialect.getMaxExpressions() <= 0) ?
                           Integer.MAX_VALUE : dialect.getMaxExpressions();

       for (int i=0; i<sortedResourceIds.size(); i+=max) {
           final int end = Math.min(i+max, sortedResourceIds.size());
           final List list = sortedResourceIds.subList(i, end);
           rtn.addAll(getSession()
                       .createQuery(sql)
                       .setParameterList("resourceIds", list, new IntegerType())
                       .setParameter("relationType", resourceRelationType)
                       .setCacheable(true)
                       .setCacheRegion("Measurement.findParentAvailMeasurements")
                       .list());
       }
       return rtn;
    }

    @SuppressWarnings("unchecked")
    List<Measurement> findAvailMeasurementsByInstances(int type, Integer[] ids) {
        boolean checkIds = (ids != null && ids.length > 0);
        String sql = new StringBuilder()
            .append("select m from Measurement m ")
            .append("join m.template t ")
            .append("join t.monitorableType mt ")
            .append("where mt.appdefType = :type and ")
            .append("m.resource is not null and ")
            .append((checkIds ? "m.instanceId in (:ids) and " : ""))
            .append(ALIAS_CLAUSE).toString();

        Query q = getSession().createQuery(sql).setInteger("type", type);

        if (checkIds) {
            q.setParameterList("ids", ids);
        }

        q.setCacheable(true);
        q.setCacheRegion("Measurement.findAvailMeasurementsByInstances");
        return q.list();
    }

    List findMetricsCountMismatch(String plugin) {
        return getSession().createSQLQuery(
            "SELECT 1, S.ID FROM EAM_MONITORABLE_TYPE MT, EAM_PLATFORM_TYPE ST "
              + "INNER JOIN EAM_PLATFORM S ON PLATFORM_TYPE_ID = ST.ID " +
            "WHERE ST.PLUGIN = MT.PLUGIN AND MT.PLUGIN = :plugin AND " +
                  "MT.NAME = ST.NAME AND " +
                  "(SELECT COUNT(M.ID) FROM EAM_MEASUREMENT M," +
                                           "EAM_MEASUREMENT_TEMPL T " +
                   "WHERE M.TEMPLATE_ID = T.ID AND " +
                         "T.MONITORABLE_TYPE_ID = MT.ID AND " +
                         "INSTANCE_ID = S.ID) < " +
                  "(SELECT COUNT(T.ID) FROM EAM_MEASUREMENT_TEMPL T " +
                   "WHERE MONITORABLE_TYPE_ID = MT.ID) GROUP BY S.ID UNION " +
            "SELECT 2, S.ID FROM EAM_MONITORABLE_TYPE MT, EAM_SERVER_TYPE ST " +
                "INNER JOIN EAM_SERVER S ON SERVER_TYPE_ID = ST.ID " +
            "WHERE ST.PLUGIN = MT.PLUGIN AND MT.PLUGIN = :plugin AND " +
                  "MT.NAME = ST.NAME AND " +
                  "(SELECT COUNT(M.ID) FROM EAM_MEASUREMENT M," +
                                           "EAM_MEASUREMENT_TEMPL T " +
                   "WHERE M.TEMPLATE_ID = T.ID AND " +
                         "T.MONITORABLE_TYPE_ID = MT.ID AND " +
                         "INSTANCE_ID = S.ID) < " +
                  "(SELECT COUNT(T.ID) FROM EAM_MEASUREMENT_TEMPL T " +
                   "WHERE MONITORABLE_TYPE_ID = MT.ID) GROUP BY S.ID UNION " +
            "SELECT 3, S.ID FROM EAM_MONITORABLE_TYPE MT, EAM_SERVICE_TYPE ST "+
                "INNER JOIN EAM_SERVICE S ON SERVICE_TYPE_ID = ST.ID " +
            "WHERE ST.PLUGIN = MT.PLUGIN AND MT.PLUGIN = :plugin AND " +
                  "MT.NAME = ST.NAME AND " +
                  "(SELECT COUNT(M.ID) FROM EAM_MEASUREMENT M," +
                                           "EAM_MEASUREMENT_TEMPL T " +
                   "WHERE M.TEMPLATE_ID = T.ID AND " +
                         "T.MONITORABLE_TYPE_ID = MT.ID AND " +
                         "INSTANCE_ID = S.ID) < " +
                  "(SELECT COUNT(T.ID) FROM EAM_MEASUREMENT_TEMPL T " +
                   "WHERE MONITORABLE_TYPE_ID = MT.ID) GROUP BY S.ID")
            .setString("plugin", plugin)
            .list();
    }

    List findMetricCountSummaries() {
        String sql =
            "SELECT COUNT(m.template_id) AS total, " +
            "m.coll_interval/60000 AS coll_interval, " +
            "t.name AS name, mt.name AS type " +
            "FROM EAM_MEASUREMENT m, EAM_MEASUREMENT_TEMPL t, " +
            "EAM_MONITORABLE_TYPE mt " +
            "WHERE m.template_id = t.id " +
            " and t.monitorable_type_id=mt.id " +
            " and m.coll_interval > 0 " +
            " and m.enabled = :enabled " +
            "GROUP BY m.template_id, t.name, mt.name, m.coll_interval " +
            "ORDER BY total DESC";
        List vals = getSession().createSQLQuery(sql)
            .setBoolean("enabled", true)
            .list();

        List res = new ArrayList(vals.size());

        for (Iterator i=vals.iterator(); i.hasNext(); ) {
            Object[] v = (Object[])i.next();
            java.lang.Number total = (java.lang.Number)v[0];
            java.lang.Number interval = (java.lang.Number)v[1];
            String metricName = (String)v[2];
            String resourceName = (String)v[3];

            res.add(new CollectionSummary(total.intValue(), interval.intValue(),
                                          metricName, resourceName));
        }
        return res;
    }

    /**
     * @see MeasurementManagerEJBImpl#findAgentOffsetTuples()
     */
    List findAgentOffsetTuples() {
        String sql = "select a, p, s, meas from Agent a " +
            "join a.platforms p " +
            "join p.platformType pt " +
            "join p.serversBag s " +
            "join s.serverType st, " +
            "Measurement as meas " +
            "join meas.template as templ " +
            "join templ.monitorableType as mt " +
            "where " +
            "pt.plugin = 'system' " +
            "and templ.name = 'Server Offset' " +
            "and meas.instanceId = s.id " +
            "and st.name = 'HQ Agent' ";

        return getSession().createQuery(sql).list();
    }

    /**
     * @see MeasurementManagerEJBImpl#findNumMetricsPerAgent()
     */
    Map findNumMetricsPerAgent() {
        String platSQL =
            "select a.id, count(m) from Agent a " +
            "join a.platforms p, " +
            "Measurement as m " +
            "join m.template templ " +
            "join templ.monitorableType monType " +
            "where " +
            " monType.appdefType = '1' and m.instanceId = p.id " +
            "and m.enabled = true " +
            "group by a";
        String serverSQL =
            "select a.id, count(m) from Agent a " +
            "join a.platforms p " +
            "join p.serversBag s, " +
            "Measurement as m " +
            "join m.template templ " +
            "join templ.monitorableType monType " +
            "where " +
            " monType.appdefType = '2' and m.instanceId = s.id " +
            "and m.enabled = true " +
            "group by a";
        String serviceSQL =
            "select a.id, count(m) from Agent a " +
            "join a.platforms p " +
            "join p.serversBag s " +
            "join s.services v, " +
            "Measurement as m " +
            "join m.template templ " +
            "join templ.monitorableType monType " +
            "where " +
            " monType.appdefType = '3' and m.instanceId = v.id " +
            "and m.enabled = true " +
            "group by a";
        String[] queries = {platSQL, serverSQL, serviceSQL};
        Map idToCount = new HashMap();

        for (int i=0; i<queries.length; i++) {
            List tuples = getSession().createQuery(queries[i]).list();

            for (Iterator j=tuples.iterator(); j.hasNext(); ) {
                Object[] tuple = (Object[])j.next();
                Integer id = (Integer)tuple[0];
                java.lang.Number count = (java.lang.Number)tuple[1];
                Long curCount;

                curCount = (Long)idToCount.get(id);
                if (curCount == null) {
                    curCount = new Long(0);
                }
                curCount = new Long(curCount.longValue() + count.longValue());
                idToCount.put(id, curCount);
            }
        }

        Map res = new HashMap(idToCount.size());
        AgentManager agentMan = AgentManagerImpl.getOne();
        for (Iterator i=idToCount.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry ent = (Map.Entry)i.next();
            Integer id = (Integer)ent.getKey();
            Long count = (Long)ent.getValue();

            res.put(agentMan.findAgent(id), count);
        }
        return res;
    }

    int clearResource(Resource resource) {
        // need to do this one measurement at a time to avoid the whole EhCache
        // being cleared due to bulk updates
        List list = findByResource(resource);
        int count = 0;
        for (Iterator it=list.iterator(); it.hasNext(); ) {
            Measurement meas = (Measurement)it.next();
            if (meas == null) {
                continue;
            }
            meas.setResource(null);
            count++;
        }
        return count;
    }

    /**
     * Find a list of Measurement ID's that are no longer associated with a
     * resource.
     *
     * @return A List of Measurement ID's.
     */
    List findOrphanedMeasurements() {
        String sql = "SELECT id FROM Measurement WHERE resource IS NULL";
        return getSession().createQuery(sql).list();
    }
}
