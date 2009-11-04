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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.type.IntegerType;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.dao.HibernateDAO;
import org.hyperic.hq.measurement.MeasurementConstants;

public class AvailabilityDataDAO extends HibernateDAO {
    
    private static final String logCtx = AvailabilityDataDAO.class.getName();
    private final Log _log = LogFactory.getLog(logCtx);
    
    private static final long MAX_TIMESTAMP =
        AvailabilityDataRLE.getLastTimestamp();
    private static final double AVAIL_DOWN = MeasurementConstants.AVAIL_DOWN;
    private static final String ALIAS_CLAUSE = " upper(t.alias) = '" +
    				MeasurementConstants.CAT_AVAILABILITY.toUpperCase() + "' ";
    // TOTAL_TIME and TOTAL_UPTIME are used to anchor the start and end values to
    // the appropriate time range.  They avoid the situation where a query
    // may result in Long.MAX_VALUE as the endtime and a startime which is <
    // the user specified value
    private static final String TOTAL_TIME =
        "least(rle.endtime,:endtime) " +
        "- greatest(rle.availabilityDataId.startime,:startime)";
    private static final String TOTAL_UPTIME =
        "(" + TOTAL_TIME + ") * rle.availVal";
    
    public AvailabilityDataDAO(DAOFactory f) {
        super(AvailabilityDataDAO.class, f);
    }

    List findLastAvail(List mids, long after) {
        // sort so that the cache has the best opportunity use the query 
        // multiple times
        mids = new ArrayList(mids);
        Collections.sort(mids);
        List rtn = new ArrayList(mids.size());
        if (mids.isEmpty()) {
            return rtn;
        }
        String hql = new StringBuilder()
            .append("from AvailabilityDataRLE")
            .append(" WHERE endtime > :endtime")
            .append(" AND availabilityDataId.measurement in (:ids)")
            .append(" ORDER BY endtime desc").toString();
        Query query = getSession().createQuery(hql).setLong("endtime", after);
        for (int i=0; i<mids.size(); i+=BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, mids.size());
            query.setParameterList(
                "ids", mids.subList(i, end), new IntegerType());
            rtn.addAll(query.list());
        }
        return rtn;
    }

    List findLastAvail(List mids) {
        // sort so that the cache has the best opportunity use the query 
        // multiple times
        mids = new ArrayList(mids);
        Collections.sort(mids);
        List rtn = new ArrayList(mids.size());
        if (mids.isEmpty()) {
            return rtn;
        }
        String hql = new StringBuilder()
			         .append("from AvailabilityDataRLE")
                     .append(" WHERE endtime = :endtime")
                     .append(" AND availabilityDataId.measurement in (:ids)")
                     .toString();
        // need to do this because of hibernate bug
        // http://opensource.atlassian.com/projects/hibernate/browse/HHH-1985
        Query query = getSession()
            .createQuery(hql)
            .setLong("endtime", MAX_TIMESTAMP);
        for (int i=0; i<mids.size(); i+=BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, mids.size());
            query.setParameterList(
                "ids", mids.subList(i, end), new IntegerType());
            rtn.addAll(query.list());
        }
        return rtn;
    }

    AvailabilityDataRLE findAvail(DataPoint state) {
        String sql = new StringBuilder()
                     .append("FROM AvailabilityDataRLE")
                     .append(" WHERE availabilityDataId.measurement = :meas")
                     .append(" AND availabilityDataId.startime = :startime")
                     .toString();
        List list =
            getSession().createQuery(sql)
            .setLong("startime", state.getTimestamp())
            .setInteger("meas", state.getMetricId().intValue())
        	.list();
        if (list.isEmpty()) {
            return null;
        }
        return (AvailabilityDataRLE)list.get(0);
    }
    
    List findAllAvailsAfter(DataPoint state) {
        String sql = new StringBuilder()
                     .append("FROM AvailabilityDataRLE")
                     .append(" WHERE availabilityDataId.measurement = :meas")
                     .append(" AND availabilityDataId.startime > :startime")
                     .append(" ORDER BY startime asc").toString();
        return getSession().createQuery(sql)
            .setLong("startime", state.getTimestamp())
            .setInteger("meas", state.getMetricId().intValue())
        	.list();
    }

    AvailabilityDataRLE findAvailAfter(DataPoint state) {
        String sql = new StringBuilder()
                     .append("FROM AvailabilityDataRLE")
                     .append(" WHERE availabilityDataId.measurement = :meas")
                     .append(" AND availabilityDataId.startime > :startime")
                     .append(" ORDER BY startime asc").toString();
        List list =
            getSession().createQuery(sql)
            .setLong("startime", state.getTimestamp())
            .setInteger("meas", state.getMetricId().intValue())
        	.setMaxResults(1).list();
        if (list.isEmpty()) {
            return null;
        }
        return (AvailabilityDataRLE)list.get(0);
    }

    void updateVal(AvailabilityDataRLE avail, double newVal) {
        avail.setAvailVal(newVal);
        save(avail);
    }

    AvailabilityDataRLE findAvailBefore(DataPoint state) {
        String sql = new StringBuilder()
                     .append("FROM AvailabilityDataRLE")
                     .append(" WHERE availabilityDataId.measurement = :meas")
                     .append(" AND availabilityDataId.startime < :startime")
                     .append(" ORDER BY startime desc").toString();
        List list =
            getSession().createQuery(sql)
            .setLong("startime", state.getTimestamp())
            .setInteger("meas", state.getMetricId().intValue())
        	.setMaxResults(1).list();
        if (list.isEmpty()) {
            return null;
        }
        return (AvailabilityDataRLE)list.get(0);
    }

    /**
     * @return List of AvailabilityDataRLE objs
     */
    List getHistoricalAvails(Measurement m, long start,
                             long end, boolean descending) {
        String sql = new StringBuilder()
            .append("FROM AvailabilityDataRLE rle ")
            .append("WHERE rle.availabilityDataId.measurement = :m AND")
            .append(" (rle.availabilityDataId.startime > :startime")
            .append("   OR rle.endtime > :startime)")
            .append(" AND (rle.availabilityDataId.startime < :endtime")
            .append("   OR rle.endtime < :endtime)")
            .append(" ORDER BY rle.availabilityDataId.measurement,")
            .append(" rle.availabilityDataId.startime")
            .append(((descending) ? " DESC" : " ASC")).toString();
        return getSession()
            .createQuery(sql)
            .setLong("startime", start)
            .setLong("endtime", end)
            .setParameter("m", m)
            .list();
    }

    /**
     * @return List of AvailabilityDataRLE objs
     */
    List getHistoricalAvails(Integer[] mids, long start,
                             long end, boolean descending) {
        String sql = new StringBuilder()
                    .append("FROM AvailabilityDataRLE rle")
                    .append(" WHERE rle.availabilityDataId.measurement in (:mids)")
                    .append(" AND rle.endtime > :startime")
                    .append(" AND rle.availabilityDataId.startime < :endtime")
                    .append(" ORDER BY rle.availabilityDataId.measurement,")
                    .append(" rle.availabilityDataId.startime")
                    .append(((descending) ? " DESC" : " ASC")).toString();
        return getSession()
            .createQuery(sql)
            .setLong("startime", start)
            .setLong("endtime", end)
            .setParameterList("mids", mids, new IntegerType())
            .list();
    }
    
    /**
     * @return {@link Map} of {@link Integer} to ({@link TreeSet} of
     *  {@link AvailabilityDataRLE}).
     * <p>The {@link Map} key of {@link Integer} == {@link Measurement}.getId().
     * <p>The {@link TreeSet}'s comparator sorts by
     *  {@link AvailabilityDataRLE}.getStartime().
     */
    Map getHistoricalAvailMap(Integer[] mids, final long after,
                              final boolean descending) {
        if (mids.length <= 0) {
            return Collections.EMPTY_MAP;
        }
        final Comparator comparator = new Comparator() {
            public int compare(Object arg0, Object arg1) {
                AvailabilityDataRLE lhs = (AvailabilityDataRLE)arg0;
                AvailabilityDataRLE rhs = (AvailabilityDataRLE)arg1;
                Long lhsStart = new Long(lhs.getStartime());
                Long rhsStart = new Long(rhs.getStartime());
                if (descending) {
                    return rhsStart.compareTo(lhsStart);
                }
                return lhsStart.compareTo(rhsStart);
            }
        };
        StringBuilder sql = new StringBuilder()
            .append("FROM AvailabilityDataRLE rle")
            .append(" WHERE rle.availabilityDataId.measurement in (:mids)");
        if (after > 0) {
            sql.append(" AND rle.endtime >= :endtime");
        }
        Query query = getSession()
            .createQuery(sql.toString())
            .setParameterList("mids", mids, new IntegerType());
        if (after > 0) {
            query.setLong("endtime", after);
        }
        List list = query.list();
        Map rtn = new HashMap(list.size());
        TreeSet tmp;
        for (Iterator it=list.iterator(); it.hasNext(); ) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE)it.next();
            Integer mId = rle.getMeasurement().getId();
            if (null == (tmp = (TreeSet)rtn.get(mId))) {
                tmp = new TreeSet(comparator);
                rtn.put(rle.getMeasurement().getId(), tmp);
            }
            tmp.add(rle);
        }
        for (int i=0; i<mids.length; i++) {
            if (!rtn.containsKey(mids[i])) {
                rtn.put(mids[i], new TreeSet(comparator));
            }
        }
        return rtn;
    }
    
    /**
     * @return List of AvailabilityDataRLE objs
     */
    List getHistoricalAvails(Resource res, long start, long end) {
        String sql = new StringBuilder()
                    .append("SELECT rle")
                    .append(" FROM AvailabilityDataRLE rle")
                    .append(" JOIN rle.availabilityDataId.measurement m")
                    .append(" WHERE m.resource = :resource")
                    .append(" AND rle.endtime > :startime")
                    .append(" AND rle.availabilityDataId.startime < :endtime")
                    .append(" ORDER BY rle.availabilityDataId.startime").toString();
        return getSession()
            .createQuery(sql)
            .setParameter("resource", res)
            .setLong("startime", start)
            .setLong("endtime", end)
            .list();
    }

    /**
     * @return List of Object[].  [0] = Measurement Obj
     *  [1] = min(availVal), [2] = max(availVal), [3] = avg(availVal)
     *  [4] = mid count, [5] = total uptime, [6] = = total time
     */
    List findAggregateAvailability(Integer[] mids, long start, long end) {
        if (mids.length == 0) {
            // Nothing to do
            return new ArrayList(0);
        }
        String sql = new StringBuilder()
                    .append("SELECT m, min(rle.availVal),")
                    .append(" max(rle.availVal),")
                    .append(" avg(rle.availVal),")
                    .append(" (:endtime - :startime) / m.interval, ")
                    .append(" sum(").append(TOTAL_UPTIME).append("), ")
                    .append(" sum(").append(TOTAL_TIME).append(") ")
                    .append(" FROM Measurement m")
                    .append(" JOIN m.availabilityData rle")
                    .append(" WHERE m in (:mids)")
                    .append(" AND (rle.availabilityDataId.startime > :startime")
                    .append("   OR rle.endtime > :startime)")
                    .append(" AND (rle.availabilityDataId.startime < :endtime")
                    .append("   OR rle.endtime < :endtime)")
                    // must group by all columns in query for postgres to work
                    // there is an open bug on this for hibernate to
                    // automatically expand group by's
                    // http://opensource.atlassian.com/projects/hibernate/browse/HHH-2407
                    .append(" GROUP BY m.id, m._version_, m.instanceId,")
                    .append(" m.template, m.mtime,m.enabled,")
                    .append(" m.interval, m.dsn,m.resource,")
                    .append(" rle.endtime")
                    .append(" ORDER BY rle.endtime").toString();
        return getSession()
            .createQuery(sql)
            .setLong("startime", start)
            .setLong("endtime", end)
            .setParameterList("mids", mids, new IntegerType())
            .list();
    }

    /**
     * @return List of Object[].  [0] = measurement template id,
     *  [1] = min(availVal), [2] = max(availVal), [3] = avg(availVal)
     *  [4] = mid count, [5] = total uptime, [6] = = total time
     */
    List findAggregateAvailability(Integer[] tids, Integer[] iids,
                                   long start, long end) {
        if (tids.length == 0) {
            // Nothing to do
            return new ArrayList(0);
        }
        
        String sql = new StringBuilder()
                    .append("SELECT m.template.id, min(rle.availVal),")
                    .append(" max(rle.availVal),")
                    .append(" avg(rle.availVal),")
                    .append(" count(distinct m.id), ")
                    .append(" sum(").append(TOTAL_UPTIME).append("), ")
                    .append(" sum(").append(TOTAL_TIME).append(") ")
                    .append(" FROM Measurement m")
                    .append(" JOIN m.availabilityData rle")
                    .append(" WHERE m.template in (:tids)")
                    .append(" AND m.instanceId in (:iids)")
                    .append(" AND (rle.availabilityDataId.startime > :startime")
                    .append("   OR rle.endtime > :startime)")
                    .append(" AND (rle.availabilityDataId.startime < :endtime")
                    .append("   OR rle.endtime < :endtime)")
                    .append(" GROUP BY m.template.id, rle.endtime")
                    .append(" ORDER BY rle.endtime").toString();
        return getSession()
            .createQuery(sql)
            .setLong("startime", start)
            .setLong("endtime", end)
            .setParameterList("tids", tids, new IntegerType())
            .setParameterList("iids", iids, new IntegerType())
            .list();
    }

    void remove(AvailabilityDataRLE avail) {
        super.remove(avail);
    }

    AvailabilityDataRLE create(Measurement meas, long startime,
                               long endtime, double availVal) {
        AvailabilityDataRLE availObj =
            new AvailabilityDataRLE(meas, startime, endtime, availVal);
        getSession().save(availObj);
        return availObj;
    }

    /**
     * @return List of down Measurements
     */
    List getDownMeasurements(List includes) {
        StringBuilder sql = new StringBuilder()
                     .append("SELECT rle FROM AvailabilityDataRLE rle")
                     .append(" JOIN rle.availabilityDataId.measurement m")
                     .append(" JOIN m.template t")
				     .append(" WHERE rle.endtime = ").append(MAX_TIMESTAMP)
                     .append(" AND m.resource is not null ")
                     .append(" AND rle.availVal = ").append(AVAIL_DOWN)
                     .append(" AND ").append(ALIAS_CLAUSE);
        final boolean hasIncludes =
            (includes != null && includes.size() > 0) ? true : false;
        if (hasIncludes) {
            sql.append(" rle.availabilityDataId.measurement in (:mids)");
        }
        Query query = getSession().createQuery(sql.toString());
        if (!hasIncludes) {
            return query.list();
        }
        List rtn = new ArrayList(includes.size());
        for (int i=0; i<includes.size(); i+=BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, includes.size());
            query.setParameterList(
                "mids", includes.subList(i, end), new IntegerType());
            rtn.addAll(query.list());
        }
        return rtn;
    }

    AvailabilityDataRLE create(Measurement meas, long startime, double availVal)
    {
        AvailabilityDataRLE availObj = new AvailabilityDataRLE(meas, startime,
                                                               availVal);
        if (_log.isDebugEnabled()) {
            _log.debug("creating Avail: "+availObj);
        }
        save(availObj);
        return availObj;
    }
}
