/*                                                                 
 * NOTE: This copyright does *not* cover user programs that use HQ 
 * program services by normal system calls through the application 
 * program interfaces provided as part of the Hyperic Plug-in Development 
 * Kit or the Hyperic Client Development Kit - this is merely considered 
 * normal use of the program, and does *not* fall under the heading of 
 * "derived work". 
 *  
 * Copyright (C) [2004-2007], Hyperic, Inc. 
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

package org.hyperic.hibernate.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.shared.MeasTabManagerUtil;
import org.hyperic.util.StringUtil;
import org.hyperic.util.jdbc.DBUtil;
import org.hyperic.util.timer.StopWatch;

/**
 * HQ's version of MySQL5InnoDBDialect to create pseudo sequences.
 * 
 * This class must be public for Hibernate to access it.
 */
public class MySQL5InnoDBDialect
    extends org.hibernate.dialect.MySQL5InnoDBDialect
    implements HQDialect
{
    private static final String logCtx = MySQL5InnoDBDialect.class.getName();
    private final Log _log = LogFactory.getLog(logCtx);
    private static final String TAB_MEAS   = MeasurementConstants.TAB_MEAS;
    private static final String TAB_DATA   = MeasurementConstants.TAB_DATA;
    private static final int IND_MIN       = MeasurementConstants.IND_MIN;
    private static final int IND_AVG       = MeasurementConstants.IND_AVG;
    private static final int IND_MAX       = MeasurementConstants.IND_MAX;
    private static final int IND_LAST_TIME = MeasurementConstants.IND_LAST_TIME;

    public MySQL5InnoDBDialect() {
        super();
        registerColumnType(Types.VARBINARY, 255, "blob");
    }

    public boolean supportsIdentityColumns() {
        return false;
    }
    
    public boolean supportsInsertSelectIdentity() {
        return false;
    }
    
    public String getOptimizeStmt(String table, int cost) {
        return "ANALYZE TABLE "+table.toUpperCase();
    }

    public boolean supportsDuplicateInsertStmt() {
        return true;
    }

    public boolean supportsMultiInsertStmt() {
        return true;
    }

    public boolean viewExists(Statement stmt, String viewName)
        throws SQLException
    {
        ResultSet rs = null;
        try
        {
            String sql = "SELECT table_name from information_schema.views"+
                         " WHERE table_name = '"+viewName+"'"+
                         " AND table_schema = database()";
            rs = stmt.executeQuery(sql);
            if (rs.next())
                return true;
            return false;
        } finally {
            DBUtil.closeResultSet(logCtx, rs);
        }
    }

    public boolean tableExists(Statement stmt, String tableName)
        throws SQLException
    {
        ResultSet rs = null;
        try
        {
            String sql = "SELECT table_name from information_schema.tables"+
                         " WHERE table_name = '"+tableName+"'"+
                         " AND table_schema = database()";
            rs = stmt.executeQuery(sql);
            if (rs.next())
                return true;
            return false;
        } finally {
            DBUtil.closeResultSet(logCtx, rs);
        }
    }

    public String getLimitString(int num) {
        return "LIMIT "+num;
    }

    private Map getMeasIds(Connection conn, Map lastMap, Integer[] iids)
        throws SQLException
    {
        StringBuffer iidsConj = new StringBuffer(
                DBUtil.composeConjunctions("instance_id", iids.length));
        DBUtil.replacePlaceHolders(iidsConj, iids);
        Map rtn = new HashMap();
        PreparedStatement pstmt = null;
        ResultSet rs            = null;
        try
        {
            String sql = "SELECT id FROM " + TAB_MEAS +
                         " WHERE template_id = ? AND " + iidsConj;
            pstmt = conn.prepareStatement(sql);
            for (Iterator it = lastMap.entrySet().iterator(); it.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) it.next();
                Integer tid = (Integer) entry.getKey();
                Long lastTime = (Long) entry.getValue();

                // Reset the index
                int ind = 1;
                pstmt.setInt(ind++, tid.intValue());

                rs = pstmt.executeQuery();
                List list = new ArrayList(); 
                while (rs.next()) {
                    list.add(new Integer(rs.getInt(1)));
                }
                LongListObj longListObj;
                if (null == (longListObj = (LongListObj)rtn.get(tid))) {
                    rtn.put(tid, new LongListObj(lastTime, list));
                } else {
                    List timelist = longListObj.getList();
                    timelist.addAll(list);
                }
            }
        }
        finally {
            DBUtil.closeResultSet(logCtx, rs);
            DBUtil.closeStatement(logCtx, pstmt);
        }
        return rtn;
    }

    public Map getLastData(Connection conn, String minMax,
                           Map resMap, Map lastMap, Integer[] iids,
                           long begin, long end, String table)
        throws SQLException {

        ResultSet rs    = null;
        Statement stmt  = null;
        StopWatch timer = new StopWatch();

        try {
            Map timeMeasIDMap = getMeasIds(conn, lastMap, iids);
            stmt = conn.createStatement();

            for (Iterator it = timeMeasIDMap.entrySet().iterator();
                it.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) it.next();
                Integer tid = (Integer) entry.getKey();
                LongListObj obj = (LongListObj) entry.getValue();
                Long lastTime = obj.getLong();
                Integer[] measIds =
                    (Integer[])obj.getList().toArray(new Integer[0]);
                if (table.endsWith(TAB_DATA))
                {
                    table = MeasTabManagerUtil.getUnionStatement(measIds,
                                                    lastTime.longValue());
                }

                String sql = "SELECT value FROM " + table +
                             " WHERE timestamp = " + lastTime;
                if (_log.isTraceEnabled()) {
                    _log.trace("getAggregateData() for measids=" + measIds +
                               " lastTime=" + lastTime + ": " + sql);
                }
                rs = stmt.executeQuery(sql);

                if (rs.next())
                {
                    // Get the double[] value from results
                    double[] data = (double[]) resMap.get(tid);
                    // Now set the the last reported value
                    data[IND_LAST_TIME] = rs.getDouble(1);
                }

            }
            if (_log.isTraceEnabled()) {
                _log.trace("getAggregateData(): Statement query elapsed " +
                          "time: " + timer.reset());
            }
        }
        finally {
            // Close ResultSet
            DBUtil.closeResultSet(logCtx, rs);
            DBUtil.closeStatement(logCtx, stmt);
        }
        return resMap;
    }

    private class LongListObj
    {
        private Long longVal;
        private List listVal;
        LongListObj(Long longVal, List listVal) {
            this.longVal = longVal;
            this.listVal = listVal;
        }
        List getList() {
            return listVal;
        }
        Long getLong() {
            return longVal;
        }
    }

    private List getMeasIds(Connection conn, Integer[] tids, Integer[] iids)
        throws SQLException
    {
        List rtn = new ArrayList();
        StringBuffer iidsConj = new StringBuffer(
                DBUtil.composeConjunctions("instance_id", iids.length));
        DBUtil.replacePlaceHolders(iidsConj, iids);
        StringBuffer tidsConj = new StringBuffer(
                DBUtil.composeConjunctions("template_id", tids.length));
        DBUtil.replacePlaceHolders(tidsConj, tids);
        final String sql = "SELECT distinct id FROM " + TAB_MEAS +
                           " WHERE " + iidsConj + " AND " + tidsConj;
        Statement stmt = null;
        ResultSet rs   = null;
        try
        {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                rtn.add(new Integer(rs.getInt(1)));
            }
        }
        finally {
            DBUtil.closeResultSet(logCtx, rs);
            DBUtil.closeStatement(logCtx, stmt);
        }
        return rtn;
    }

    public Map getAggData(Connection conn, String minMax, Map resMap,
                          Integer[] tids, Integer[] iids,
                          long begin, long end, String table)
        throws SQLException
    {
        HashMap lastMap = new HashMap();

        ResultSet rs            = null;
        PreparedStatement astmt = null;
        StopWatch timer         = new StopWatch();

        StringBuffer iidsConj = new StringBuffer(
                DBUtil.composeConjunctions("instance_id", iids.length));
        DBUtil.replacePlaceHolders(iidsConj, iids);

        StringBuffer tidsConj = new StringBuffer(
                DBUtil.composeConjunctions("template_id", tids.length));
        DBUtil.replacePlaceHolders(tidsConj, tids);

        if (table.endsWith(TAB_DATA)) {
            List measIds = getMeasIds(conn, tids, iids);
            table = MeasTabManagerUtil.getUnionStatement(
                begin, end, (Integer[])measIds.toArray(new Integer[0]));
            //if there are 0 measurement ids there is no need to go forward
            if (measIds.size() == 0) {
                return lastMap;
            }
        }

        final String aggregateSQL =
            "SELECT template_id," + minMax + "MAX(timestamp) " +
            " FROM " + table + "," + TAB_MEAS +
            " WHERE timestamp BETWEEN ? AND ? AND measurement_id = id AND " +
                    iidsConj + " AND " + tidsConj + " GROUP BY template_id";

        try {
            // Prepare aggregate SQL
            astmt = conn.prepareStatement(aggregateSQL);
            // First set the time range
            int ind = 1;
            astmt.setLong(ind++, begin);
            astmt.setLong(ind++, end);

            if (_log.isTraceEnabled())
                _log.trace("getAggregateData() for begin=" + begin +
                          " end = " + end + ": " + aggregateSQL);
            // First get the min, max, average
            rs = astmt.executeQuery();
            while (rs.next()) {
                Integer tid = new Integer(rs.getInt("template_id"));
                double[] data = new double[IND_LAST_TIME + 1];
                // data[0] = min, data[1] = avg, data[2] = max,
                // data[3] = last, data[4] = count of measurement ID's
                data[IND_MIN] = rs.getDouble(2);
                data[IND_AVG] = rs.getDouble(3);
                data[IND_MAX] = rs.getDouble(4);
                // Put it into the result map
                resMap.put(tid, data);
                // Get the time
                Long lastTime = new Long(rs.getLong(5));
                // Put it into the last map
                lastMap.put(tid, lastTime);
            }
            if (_log.isTraceEnabled())
                _log.trace("getAggregateData(): Statement query elapsed: " +
                          timer.reset());
        } finally {
            DBUtil.closeResultSet(logCtx, rs);
            DBUtil.closeStatement(logCtx, astmt);
        }
        return lastMap;
    }

    public String getAddForeignKeyConstraintString(String constraintName,
                                                   String[] foreignKey,
                                                   String referencedTable,
                                                   String[] primaryKey,
                                                   boolean referencesPrimaryKey)
    {
        String cols = StringUtil.implode(Arrays.asList(foreignKey), ", ");
        return new StringBuffer(64)
            .append(" add constraint ")
            .append(constraintName)
            .append(" foreign key (")
            .append(cols)
            .append(") references ")
            .append(referencedTable)
            .append(" (")
            .append( StringUtil.implode(Arrays.asList(primaryKey), ", ") )
            .append(')')
            .toString();
    }

    public boolean usesSequenceGenerator() {
        return false;
    }

    public String getRegExSQL(String column, String regex, boolean ignoreCase,
                              boolean invertMatch) {
        if (ignoreCase) {
            return new StringBuilder()
                .append("lower(").append(column).append(")")
                .append((invertMatch) ? " NOT " : " ")
                .append("REGEXP ")
                .append("lower(").append(regex).append(")")
                .toString();
        } else {
            return new StringBuilder()
                .append(column)
                .append((invertMatch) ? " NOT " : " ")
                .append("REGEXP ")
                .append(regex)
                .toString();
        }
    }

    public boolean useEamNumbers() {
        return false;
    }

    public int getMaxExpressions() {
        return -1;
    }

    public boolean supportsPLSQL() {
        return false;
    }

    public boolean useMetricUnion() {
        return false;
    }
}
