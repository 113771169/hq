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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.util.Messenger;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.events.ext.RegisteredTriggers;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.hq.measurement.TimingVoodoo;
import org.hyperic.hq.measurement.ext.DownMetricValue;
import org.hyperic.hq.measurement.ext.MeasurementEvent;
import org.hyperic.hq.measurement.server.session.Measurement;
import org.hyperic.hq.measurement.shared.HighLowMetricValue;
import org.hyperic.hq.measurement.shared.MeasurementManagerLocal;
import org.hyperic.hq.measurement.shared.AvailabilityManagerLocal;
import org.hyperic.hq.measurement.shared.AvailabilityManagerUtil;
import org.hyperic.hq.product.MetricValue;
import org.hyperic.hq.zevents.ZeventManager;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;

/** The AvailabityManagerEJBImpl class is a stateless session bean that can be
 *  used to retrieve Availability Data RLE points
 *  
 * @ejb:bean name="AvailabilityManager"
 *      jndi-name="ejb/measurement/AvailabilityManager"
 *      local-jndi-name="LocalAvailabilityManager"
 *      view-type="local"
 *      type="Stateless"
 *      
 * @ejb:transaction type="Required"
 */
public class AvailabilityManagerEJBImpl
    extends SessionEJB implements SessionBean {

    private final Log _log = LogFactory.getLog(AvailabilityManagerEJBImpl.class);
    private static final double AVAIL_NULL = MeasurementConstants.AVAIL_NULL;
    private static final double AVAIL_DOWN = MeasurementConstants.AVAIL_DOWN;
    private static final double AVAIL_UNKNOWN =
        MeasurementConstants.AVAIL_UNKNOWN;
    private static final int IND_MIN       = MeasurementConstants.IND_MIN;
    private static final int IND_AVG       = MeasurementConstants.IND_AVG;
    private static final int IND_MAX       = MeasurementConstants.IND_MAX;
    private static final int IND_CFG_COUNT = MeasurementConstants.IND_CFG_COUNT;
    private static final int IND_LAST_TIME = MeasurementConstants.IND_LAST_TIME;
    private static final int IND_UP_TIME   = IND_LAST_TIME + 1;
    private static final int IND_TOTAL_TIME = IND_UP_TIME + 1;
    private static final long MAX_AVAIL_TIMESTAMP =
        AvailabilityDataRLE.getLastTimestamp();
    private static final String ALL_EVENTS_INTERESTING_PROP = 
        "org.hq.triggers.all.events.interesting";
    private static final int DEFAULT_INTERVAL = 60;
    
    /**
     * @ejb:interface-method
     */
    public Measurement getAvailMeasurement(Resource resource) {
        return getMeasurementDAO().findAvailMeasurement(resource);
    }
    
    /**
     * @ejb:interface-method
     */
    public List getPlatformResources() {
        return getMeasurementDAO().findAvailMeasurementsByInstances(
            AppdefEntityConstants.APPDEF_TYPE_PLATFORM, null);
    }

    /**
     * @return Down time in ms for the Resource availability
     * 
     * @ejb:interface-method
     */
    public long getDowntime(Resource resource, long begin, long end)
        throws MeasurementNotFoundException
    {
        Measurement meas = getMeasurementDAO().findAvailMeasurement(resource);
        if (meas == null) {
            throw new MeasurementNotFoundException("Availability measurement " +
                                                   "not found for resource " +
                                                   resource.getId());
        }
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List availInfo = dao.getHistoricalAvails(meas, begin, end, false);
        long rtn = 0l;
        for (Iterator i=availInfo.iterator(); i.hasNext(); ) {
            AvailabilityDataRLE avail = (AvailabilityDataRLE)i.next();
            if (avail.getAvailVal() != AVAIL_DOWN) {
                continue;
            }
            long endtime = avail.getEndtime();
            if (endtime == MAX_AVAIL_TIMESTAMP) {
                endtime = System.currentTimeMillis();
            }
            rtn += (endtime-avail.getStartime());
        }
        return rtn;
    }

    /**
     * @return List of all measurement ids for availability, ordered
     * 
     * @ejb:interface-method
     */
    public List getAllAvailIds() {
        return getMeasurementDAO().findAllAvailIds();
    }

    /**
     * @ejb:interface-method
     */
    public List getAvailMeasurementChildren(Resource resource) {
        List list = new ArrayList();
        list.add(resource.getId());
        return getMeasurementDAO().findRelatedAvailMeasurements(list);
    }
    
    /**
     * TODO: Can this method be combined with the one that takes an array?
     * 
     * @ejb:interface-method
     */
    public PageList getHistoricalAvailData(Measurement m, long begin, long end,
                                           PageControl pc,
                                           boolean prependUnknowns) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List availInfo = dao.getHistoricalAvails(m, begin,
                                                 end, pc.isDescending());
        return getPageList(availInfo, begin, end, (end-begin)/DEFAULT_INTERVAL,
            prependUnknowns);
    }

    /**
     * Fetches historical availability encapsulating the specified time range
     * for each measurement id in mids;
     * @param mids measurement ids 
     * @param begin time range start
     * @param end time range end
     * @param interval interval of each time range window
     * @param pc page control
     * @param prependUnknowns determines whether to prepend AVAIL_UNKNOWN if the
     * corresponding time window is not accounted for in the database.  Since
     * availability is contiguous this will not occur unless the time range
     * precedes the first availability point.
     * @see org.hyperic.hq.measurement.MeasurementConstants#AVAIL_UNKNOWN
     * @ejb:interface-method
     */
    public PageList getHistoricalAvailData(Integer[] mids, long begin, long end,
                                           long interval, PageControl pc,
                                           boolean prependUnknowns) {
        if (mids.length == 0) {
            return new PageList();
        }
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List availInfo = dao.getHistoricalAvails(mids, begin,
            end, pc.isDescending());
        return getPageList(availInfo, begin, end, interval, prependUnknowns);
    }
    
    // XXX scottmf, not used right now.  Will be used in some fashion to calc
    // the correct availability uptime percent
    private double getUpTime(List availInfo, long begin, long end) {
        long totalUptime = 0;
        long totalTime = 0;
        for (Iterator it=availInfo.iterator(); it.hasNext(); ) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE)it.next();
            long endtime = rle.getEndtime();
            long startime = rle.getStartime();
            long total = Math.min(endtime, end) - Math.max(startime, begin);
            totalUptime += total*rle.getAvailVal();
            totalTime += total;
        }
        return (double)totalUptime/(double)totalTime*100;
    }
    
    /**
     * Get the list of Raw RLE objects for a resource
     * @return List<AvailabilityDataRLE>
     * @ejb:interface-method
     */
    public List getHistoricalAvailData(Resource res, long begin, long end) {
        return getAvailabilityDataDAO().getHistoricalAvails(res, begin, end);
    }

    private Collection getDefaultHistoricalAvail(long timestamp) {
        HighLowMetricValue[] rtn = new HighLowMetricValue[DEFAULT_INTERVAL];
        Arrays.fill(rtn, new HighLowMetricValue(AVAIL_UNKNOWN, timestamp));
        return Arrays.asList(rtn);
    }

    private PageList getPageList(List availInfo, long begin, long end,
                                 long interval, boolean prependUnknowns) {
        PageList rtn = new PageList();
        begin += interval;
        for (Iterator it=availInfo.iterator(); it.hasNext(); ) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE)it.next();
            long availStartime = rle.getStartime();
            long availEndtime = rle.getEndtime();
            if (availEndtime < begin) {
                continue;
            }
            LinkedList queue = new LinkedList();
            queue.add(rle);
            int i=0;
            for (long curr=begin; curr<=end; curr+=interval) {
                long next = curr + interval;
                next = (next > end) ? end : next;
                long endtime =
                    ((AvailabilityDataRLE)queue.getFirst()).getEndtime();
                while (next > endtime) {
                    // it should not be the case that there are no more
                    // avails in the array, but we need to handle it
                    if (it.hasNext()) {
                        AvailabilityDataRLE tmp = (AvailabilityDataRLE)it.next();
                        queue.addFirst(tmp);
                        endtime = tmp.getEndtime();
                    } else {
                        endtime = availEndtime;
                        int measId = rle.getMeasurement().getId().intValue();
                        String msg = "Measurement, " + measId +
                            ", for interval " + begin + " - " + end + 
                            " did not return a value for range " +
                            curr + " - " + (curr + interval);
                        _log.warn(msg);
                    }
                }
                endtime = availEndtime;
                while (curr > endtime) {
                    queue.removeLast();
                    // this should not happen unless the above !it.hasNext()
                    // else condition is true
                    if (queue.size() == 0) {
                        rle = new AvailabilityDataRLE(rle.getMeasurement(),
                            rle.getEndtime(), next, AVAIL_UNKNOWN);
                        queue.addLast(rle);
                    }
                    rle = (AvailabilityDataRLE)queue.getLast();
                    availStartime = rle.getStartime();
                    availEndtime = rle.getEndtime();
                    endtime = availEndtime;
                }
                HighLowMetricValue val;
                if (curr >= availStartime) {
                    val = getMetricValue(queue, curr);
                } else if (prependUnknowns) {
                    val = new HighLowMetricValue(AVAIL_UNKNOWN, curr);
                    val.incrementCount();
                } else {
                    i++;
                    continue;
                }
                if (rtn.size() <= i) {
                    rtn.add(val);
                } else {
                    updateMetricValue(val, (HighLowMetricValue)rtn.get(i));
                }
                i++;
            }
        }
        if (rtn.size() == 0) {
            rtn.addAll(getDefaultHistoricalAvail(end));
        }
        return rtn;
    }

    private HighLowMetricValue updateMetricValue(HighLowMetricValue newVal,
                                                 HighLowMetricValue oldVal) {
        if (newVal.getHighValue() == AVAIL_UNKNOWN ||
                newVal.getHighValue() > oldVal.getHighValue()) {
            oldVal.setHighValue(newVal.getHighValue());
        }
        if (newVal.getLowValue() == AVAIL_UNKNOWN ||
                newVal.getLowValue() < oldVal.getLowValue()) {
            oldVal.setLowValue(newVal.getLowValue());
        }
        int count = oldVal.getCount();
        if (oldVal.getValue() == AVAIL_UNKNOWN) {
            double value = newVal.getValue();
            oldVal.setValue(value);
            oldVal.setCount(1);
        } else if (newVal.getValue() == AVAIL_UNKNOWN) {
            return oldVal;
        } else {
            double value =
                ((newVal.getValue()+(oldVal.getValue()*count)))/(count+1);
            oldVal.setValue(value);
            oldVal.incrementCount();
        }
        return oldVal;
    }

    private HighLowMetricValue getMetricValue(List avails, long timestamp) {
        if (avails.size() == 1) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE)avails.get(0);
            return new HighLowMetricValue(rle.getAvailVal(), timestamp);
        }
        double value = 0;
        for (Iterator i=avails.iterator(); i.hasNext(); ) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE)i.next();
            double availVal = rle.getAvailVal();
            value += availVal;
        }
        value = value/avails.size();
	    HighLowMetricValue val = new HighLowMetricValue(value, timestamp);
            val.incrementCount();
	    return val;
	}

    /**
     * @return Map<Measurement, double[]> Array is comprised of 5 elements
     * [IND_MIN]
     * [IND_AVG]
     * [IND_MAX]
     * [IND_CFG_COUNT]
     * [IND_LAST_TIME]
     * @ejb:interface-method
     */
    public Map getAggregateData(Integer[] mids, long begin, long end)
    {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List avails = dao.findAggregateAvailability(mids, begin, end);
        return getAggData(avails, begin, end, false);
    }

    /**
     * @return Map<Integer, double[]> Array is comprised of 5 elements
     * [IND_MIN]
     * [IND_AVG]
     * [IND_MAX]
     * [IND_CFG_COUNT]
     * [IND_LAST_TIME]
     * @ejb:interface-method
     */
    public Map getAggregateData(Integer[] tids, Integer[] iids,
                                long begin, long end)
    {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List avails = dao.findAggregateAvailability(tids, iids, begin, end);
        return getAggData(avails, begin, end, true);
    }

    private Map getAggData(List avails, long begin, long end, boolean useTidKey)
    {
        Map rtn = new HashMap();
        if (avails.size() == 0) {
            // Nothing to do, return an empty Map.
            return rtn;
        }
        for (Iterator it=avails.iterator(); it.hasNext(); ) {
            Object[] objs = (Object[]) it.next();

            double[] data;
            Integer key = null;
            if (useTidKey) {
                key = (Integer)objs[0];
            } else {
                key = ((Measurement)objs[0]).getId();
            }
            if (null == (data = (double[])rtn.get(key))) {
                data = new double[IND_TOTAL_TIME + 1];
                data[IND_MIN] = Double.MAX_VALUE;
                data[IND_MAX] = Double.MIN_VALUE;
                rtn.put(key, data);
            }

            data[IND_MIN] = Math.min(data[IND_MIN],
                                     ((Double)objs[1]).doubleValue());
            data[IND_MAX] += Math.max(data[IND_MAX],
                                      ((Double)objs[3]).doubleValue());
            
            // Expect data to be sorted by end time, so that the last value
            // returned is the final count and the last value
            data[IND_CFG_COUNT] = ((Integer)objs[4]).doubleValue();
            data[IND_LAST_TIME] = ((Double)objs[2]).doubleValue();
            
            data[IND_UP_TIME]    += ((Double)objs[5]).doubleValue();
            data[IND_TOTAL_TIME] += ((Double)objs[6]).doubleValue();
        }
        
        // Now calculate the average value
        for (Iterator it = rtn.values().iterator(); it.hasNext(); ) {
            double[] data = (double[]) it.next();
            data[IND_AVG] += data[IND_UP_TIME] / data[IND_TOTAL_TIME];

        }
        return rtn;
    }

    /**
     * @ejb:interface-method
     */
    public MetricValue getLastAvail(Measurement m) {

        Map map = getLastAvail(new Integer[] { m.getId() }, MAX_AVAIL_TIMESTAMP - 1);
        MetricValue mv = (MetricValue)map.get(m.getId());
        if (mv == null) {
            return new MetricValue(AVAIL_UNKNOWN, System.currentTimeMillis());
        } else {
            return mv;
        }
    }

    /**
     * @return Map<Integer, MetricValue> Integer is the measurementId
     * @ejb:interface-method
     */
    public Map getLastAvail(Integer[] mids) {
        return getLastAvail(mids, -1);
    }

    /**
     * @return Map<Integer, MetricValue> Integer is the measurementId
     * @ejb:interface-method
     */
    public Map getLastAvail(Integer[] mids, long after) {
        Map rtn = new HashMap();
        if (mids.length == 0) {
            return rtn;
        }
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List list;
        List midList = new ArrayList(Arrays.asList(mids));
        if (after != -1) {
            list = dao.findLastAvail(midList, after);
        } else {
            list = dao.findLastAvail(midList);
        }
        long now = TimingVoodoo.roundDownTime(System.currentTimeMillis(), 60000);
        for (Iterator i=list.iterator(); i.hasNext(); ) {
            AvailabilityDataRLE avail = (AvailabilityDataRLE)i.next();
            Integer mid = avail.getMeasurement().getId();
            long endtime;
            if (avail.getEndtime() == MAX_AVAIL_TIMESTAMP) {
                endtime = now;
            } else {
                endtime = avail.getEndtime();
            }
            MetricValue tmp;
            if (null == (tmp = (MetricValue)rtn.get(mid)) ||
                    endtime > tmp.getTimestamp()) {
                MetricValue mVal = new MetricValue(avail.getAvailVal(), endtime);
                rtn.put(avail.getMeasurement().getId(), mVal);
                midList.remove(avail.getMeasurement().getId());
            }
        }
        // fill in missing measurements
        if (midList.size() > 0) {
            for (Iterator i=midList.iterator(); i.hasNext(); ) {
                Integer mid = (Integer)i.next();
                MetricValue mVal = new MetricValue(AVAIL_UNKNOWN, now);
                rtn.put(mid, mVal);
            }
        }
        return rtn;
    }

    /**
     * @param includes List<Integer> of mids.  If includes is null then all
     * unavail entities will be returned.
     * @ejb:interface-method
     */
    public List getUnavailEntities(List includes) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List rtn = new ArrayList();
        List down = dao.getDownMeasurements();
        for (Iterator i=down.iterator(); i.hasNext(); ) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE) i.next();
            Measurement meas = rle.getMeasurement();
            long timestamp = rle.getStartime();
            Integer mid = meas.getId();
            if (includes != null && !includes.contains(mid)) {
                continue;
            }
            MetricValue val = new MetricValue(AVAIL_DOWN, timestamp);
            rtn.add(new DownMetricValue(meas.getEntityId(), mid, val));
        }
        return rtn;
    }

    /**
     * Add a single Availablility Data point.
     * @mid The Measurement id
     * @mval The MetricValue to store.
     * @ejb:transaction type="RequiresNew"
     * @ejb:interface-method
     */
    public void addData(Integer mid, MetricValue mval) {
        List l = new ArrayList(1);
        l.add(new DataPoint(mid, mval));
        addData(l);
    }

    /**
     * Process Availability data.
     *
     * @param availPoints List of DataPoints
     * 
     * @ejb:transaction type="RequiresNew"
     * @ejb:interface-method
     */
    public void addData(List availPoints)
    {
        if (availPoints == null || availPoints.size() == 0) {
            return;
        }
        List updateList = new ArrayList(availPoints.size());
        List outOfOrderAvail = new ArrayList(availPoints.size());
        AvailabilityCache cache = AvailabilityCache.getInstance();
        synchronized (cache) {
            updateCache(availPoints, updateList, outOfOrderAvail);
            updateStates(updateList);
        }
        updateOutOfOrderState(outOfOrderAvail);
        sendDataToEventHandlers(availPoints);
    }

    private void updateDup(DataPoint state, AvailabilityDataRLE dup)
        throws BadAvailStateException
    {
        if (dup.getAvailVal() == state.getValue()) {
            // nothing to do
        } else  if (dup.getAvailVal() != AVAIL_DOWN) {
            String msg = "New DataPoint and current DB value for " +
             "MeasurementId " + state.getMetricId() + " / timestamp " +
             state.getTimestamp() + " have conflicting states, no update";
            throw new BadAvailStateException(msg);
        } else {
            Measurement meas = dup.getMeasurement();
            long newStartime = dup.getStartime()+meas.getInterval();
            insertPointOnBoundry(dup, newStartime, state);
        }
    }

    /**
     * sets avail's startime to newStartime and prepends a new avail obj
     * from avail.getStartime() to newStartime with a value of state.getValue()
     * Used specifically for a point which collides with a RLE on its startime
     * @return true if the avail with MAX_AVAIL_TIMESTAMP was updated
     */
    private void insertPointOnBoundry(AvailabilityDataRLE avail,
                                      long newStartime,
                                      DataPoint pt)
        throws BadAvailStateException
    {
        if (newStartime <= avail.getStartime()) {
            return;
        }
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        Measurement meas = avail.getMeasurement();
        if (avail.getEndtime() == MAX_AVAIL_TIMESTAMP) {
            AvailabilityCache cache = AvailabilityCache.getInstance();
            DataPoint tmp = cache.get(pt.getMetricId());
            if (tmp == null || pt.getTimestamp() >= tmp.getTimestamp()) {
                updateAvailVal(avail, pt.getValue());
            } else {
                prependState(pt, avail);
            }
        } else if (newStartime < avail.getEndtime()) {
            prependState(pt, avail);
        } else if (newStartime > avail.getEndtime()) {
            dao.remove(avail);
        } else if (newStartime == avail.getEndtime()) {
            AvailabilityDataRLE after = dao.findAvailAfter(pt);
            if (after.getAvailVal() == pt.getValue()) {
                // resolve by removing the before obj, if it exists,
                // and sliding back the start time of after obj
                AvailabilityDataRLE before = dao.findAvailBefore(pt);
                if (before == null) {
                    dao.updateStartime(after, avail.getStartime());
                }
                // XXX this is wrong
                else if (before.getAvailVal() == after.getAvailVal()) {
                    dao.remove(avail);
                    dao.remove(before);
                    dao.updateStartime(after, before.getStartime());
                }
            } else {
                // newStartime == avail.getEndtime() &&
                // newStartime == after.getStartime() &&
                // newStartime <  after.getEndtime()  &&
                // pt.getValue() != after.getAvailVal()
                // therefore, need to push back startTime and set the value
                long interval = meas.getInterval();
                if ( (after.getStartime()+interval) < after.getEndtime() ) {
                    prependState(pt, after);
                } else {
                    DataPoint afterPt = new DataPoint(meas.getId().intValue(),
                        after.getAvailVal(), after.getStartime());
                    AvailabilityDataRLE afterAfter = dao.findAvailAfter(afterPt);
                    if (afterAfter.getAvailVal() == pt.getValue()) {
                        dao.remove(after);
                        updateStartime(afterAfter, pt.getTimestamp());
                    } else {
                        dao.updateVal(after, pt.getValue());
                    }
                }
            }
        }
    }

    private void merge(DataPoint state)
            throws BadAvailStateException {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        AvailabilityDataRLE dup = dao.findAvail(state);
        if (dup != null) {
            updateDup(state, dup);
            return;
        }
        AvailabilityDataRLE before = dao.findAvailBefore(state);
        AvailabilityDataRLE after = dao.findAvailAfter(state);
        if (before == null && after == null) {
            // this shouldn't happen here
            Measurement meas = getMeasurement(state.getMetricId().intValue());
            dao.create(meas, state.getTimestamp(),
                       MAX_AVAIL_TIMESTAMP,
                       state.getValue());
        } else if (before == null) {
            if (after.getAvailVal() != state.getValue()) {
                prependState(state, after);
            } else {
                dao.updateStartime(after, state.getTimestamp());
            }
        } else if (after == null) {
            // this shouldn't happen here
            updateState(state, null);
        } else {
            insertAvail(before, after, state);
        }
    }

    private void insertAvail(AvailabilityDataRLE before,
        AvailabilityDataRLE after, DataPoint state) {

        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        if (state.getValue() != after.getAvailVal() &&
                state.getValue() != before.getAvailVal()) {
            Measurement meas = getMeasurement(state.getMetricId().intValue());
            long pivotTime = state.getTimestamp() + meas.getInterval();
            dao.create(meas, state.getTimestamp(), pivotTime, state.getValue());
            dao.updateEndtime(before, state.getTimestamp());
            dao.updateStartime(after, pivotTime);
        } else if (state.getValue() == after.getAvailVal() &&
                   state.getValue() != before.getAvailVal()) {
            dao.updateEndtime(before, state.getTimestamp());
            dao.updateStartime(after, state.getTimestamp());
        } else if (state.getValue() != after.getAvailVal() &&
                   state.getValue() == before.getAvailVal()) {
            // this is fine
        } else if (state.getValue() == after.getAvailVal() &&
                   state.getValue() == before.getAvailVal()) {
            // this should never happen or else there is something wrong
            // in the code
            String msg = "AvailabilityData [" + before + "] and [" + after +
                "] have the same values.  This should not be the case.  " +
                "Cleaning up";
            _log.warn(msg);
            dao.updateEndtime(before, after.getEndtime());
            dao.remove(after);
        }
    }

    private boolean prependState(DataPoint state, AvailabilityDataRLE avail) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        AvailabilityDataRLE before = dao.findAvailBefore(state);
        Measurement meas = avail.getMeasurement();
        if (before != null && before.getAvailVal() == state.getValue()) {
            long newStart =  state.getTimestamp() + meas.getInterval();
            dao.updateEndtime(before, newStart);
            updateStartime(avail, newStart);
        } else {
            long newStart =  state.getTimestamp() + meas.getInterval();
            long endtime = newStart;
            updateStartime(avail, newStart);
            dao.create(avail.getMeasurement(), state.getTimestamp(),
                       endtime, state.getValue());
        }
        return true;
    }

    private void updateAvailVal(AvailabilityDataRLE avail, double val) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        Measurement meas = avail.getMeasurement();
        DataPoint state = new DataPoint(meas.getId().intValue(), val,
                                        avail.getStartime());
        AvailabilityDataRLE before = dao.findAvailBefore(state);
        if (before == null || before.getAvailVal() != val) {
            dao.updateVal(avail, val);
        } else {
            dao.remove(before);
            dao.updateStartime(avail, before.getStartime());
            dao.updateVal(avail, val);
        }
    }
    
    /*
     * !!!need to move all calls to this
     */
    private void updateStartime(AvailabilityDataRLE avail, long start) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        // this should not be the case here, but want to make sure and
        // avoid HibernateUniqueKeyExceptions :(
        AvailabilityDataRLE tmp;
        DataPoint tmpState = new DataPoint(
            avail.getMeasurement().getId().intValue(),
            avail.getAvailVal(), start);
        if (null != (tmp = dao.findAvail(tmpState))) {
            dao.remove(tmp);
        }
        dao.updateStartime(avail, start);
    }

    private AvailabilityDataRLE getLastAvail(DataPoint state, Map availMap) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        AvailabilityDataRLE avail = null;
        if (availMap != null ) {
            // remove the avail just in case it is updated
            // don't want stale data
            avail = (AvailabilityDataRLE)availMap.remove(state.getMetricId());
        }
        if (avail == null) {
            List mids = new ArrayList();
            mids.add(state.getMetricId());
            List avails = dao.findLastAvail(mids);
	        if (avails.size() > 0) {
	            avail = (AvailabilityDataRLE)avails.get(0);
	        }
        }
        return avail;
    }

    private boolean updateState(DataPoint state, Map availMap)
        throws BadAvailStateException {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        AvailabilityDataRLE avail = getLastAvail(state, availMap);
	    boolean debug = _log.isDebugEnabled();
	    if (avail == null) {
	        Measurement meas = getMeasurement(state.getMetricId().intValue());
	        dao.create(meas,
	            state.getTimestamp(), state.getValue());
	        return true;
	    } else if (state.getTimestamp() < avail.getStartime()) {
	        merge(state);
	        return false;
	    } else if (state.getTimestamp() == avail.getStartime() &&
                   state.getValue() != avail.getAvailVal()) {
	        updateDup(state, avail);
	        return true;
	    } else if (state.getValue() == avail.getAvailVal()) {
	        if (debug) {
	            _log.debug("no update state == avail " + state + " == " + avail);
	        }
	        return true;
	    }
	    if (debug) {
	        _log.debug("updating endtime on avail -> " + avail +
	                   ", updating to state -> " + state);
	    }
	    dao.updateEndtime(avail, state.getTimestamp());
	    dao.create(avail.getMeasurement(), state.getTimestamp(),
	        state.getValue());
	    return true;
    }
    
    private Map getLastAvails(List states) {
        AvailabilityDataDAO dao = getAvailabilityDataDAO();
        List mids = new ArrayList(states.size());
        for (Iterator i=states.iterator(); i.hasNext(); ) {
            DataPoint state = (DataPoint)i.next();
            mids.add(state.getMetricId());
        }
        List avails = dao.findLastAvail(mids);
        Map rtn = new HashMap(avails.size());
        for (Iterator i=avails.iterator(); i.hasNext(); ) {
            AvailabilityDataRLE avail = (AvailabilityDataRLE)i.next();
            rtn.put(avail.getMeasurement().getId(), avail);
        }
        return rtn;
    }

    private void updateStates(List states) {
        AvailabilityCache cache = AvailabilityCache.getInstance();
        if (states.size() == 0) {
            return;
        }
        // as a performance optimization, fetch all the last avails
        // at once, rather than one at a time in updateState()
        Map avMap = getLastAvails(states);
        for (Iterator i=states.iterator(); i.hasNext(); ) {
            DataPoint state = (DataPoint)i.next();
            try {
                // need to check again since there could be multiple
                // states with the same id in the list
                DataPoint currState = cache.get(state.getMetricId());
                if (currState != null &&
                    currState.getValue() == state.getValue()) {
                    continue;
                }
                boolean updateCache = updateState(state, avMap);
                if (_log.isDebugEnabled()) {
                    _log.debug("state " + state + 
                               " was updated, cache updated: " + updateCache);
                }
                if (updateCache) {
                    cache.put(state.getMetricId(), state);
                }
            } catch (BadAvailStateException e) {
                _log.warn(e.getMessage());
            }
        }
    }

    private void updateOutOfOrderState(List outOfOrderAvail) {
        if (outOfOrderAvail.size() == 0) {
            return;
        }
        for (Iterator i=outOfOrderAvail.iterator(); i.hasNext(); ) {
            try {
            	DataPoint state = (DataPoint)i.next();
            	// do not update the cache here, the timestamp is out of order
                merge(state);
            } catch (BadAvailStateException e) {
                _log.warn(e.getMessage());
            }
        }
    }

    private void updateCache(List availPoints, List updateList,
                             List outOfOrderAvail)
    {
        if (availPoints.size() == 0) {
            return;
        }
        AvailabilityCache cache = AvailabilityCache.getInstance();
        for (Iterator i=availPoints.iterator(); i.hasNext(); ) {
            DataPoint pt = (DataPoint)i.next();
			int id = pt.getMetricId().intValue();
            MetricValue mval = pt.getMetricValue();
            double val = mval.getValue();
            long timestamp = mval.getTimestamp();
            DataPoint newState = new DataPoint(id, val, timestamp);
            DataPoint oldState = cache.get(new Integer(id));
            // we do not want to update the state if it changes
            // instead change it when the db is changed in order
            // to ensure the state of memory to db
            // ONLY update memory state here if there is no change
            if (oldState != null && timestamp < oldState.getTimestamp()) {
                outOfOrderAvail.add(newState);
            } else if (oldState == null || oldState.getValue() == AVAIL_NULL ||
                    oldState.getValue() != val) {
                updateList.add(newState);
                if (_log.isDebugEnabled()) {
                    String msg = "value of state " + newState + 
                                 " differs from" + " current value" +
                                 ((oldState != null) ? oldState.toString() :
                                     " old state does not exist");
                    _log.debug(msg);
                }
            } else {
                cache.put(new Integer(id), newState);
	        }
        }
    }
    
    private void sendDataToEventHandlers(List data) {
        ArrayList events  = new ArrayList();
        List zevents = new ArrayList();

        boolean allEventsInteresting = 
            Boolean.getBoolean(ALL_EVENTS_INTERESTING_PROP);

        for (Iterator i = data.iterator(); i.hasNext();) {
            DataPoint dp = (DataPoint) i.next();
            Integer metricId = dp.getMetricId();
            MetricValue val = dp.getMetricValue();
            MeasurementEvent event = new MeasurementEvent(metricId, val);

            if (RegisteredTriggers.isTriggerInterested(event) ||
                    allEventsInteresting) {
                events.add(event);
            }

            zevents.add(new MeasurementZevent(metricId.intValue(), val));
        }

        if (!events.isEmpty()) {
            Messenger sender = new Messenger();
            sender.publishMessage(EventConstants.EVENTS_TOPIC, events);
        }

        if (!zevents.isEmpty()) {
            try {
                // XXX:  Shouldn't this be a transactional queueing?
                ZeventManager.getInstance().enqueueEvents(zevents);
            } catch(InterruptedException e) {
                _log.warn("Interrupted while sending availability events.  " +
                          "Some data may be lost");
            }
        }
    }

    private Measurement getMeasurement(int id) {
        MeasurementManagerLocal derMan =
            MeasurementManagerEJBImpl.getOne();
        return derMan.getMeasurement(new Integer(id));
    }

    public static AvailabilityManagerLocal getOne() {
        try {
            return AvailabilityManagerUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public void ejbCreate() {}
    public void ejbPostCreate() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void ejbRemove() {}
    public void setSessionContext(SessionContext ctx) {}
}
