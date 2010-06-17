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

package org.hyperic.hq.measurement.agent.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hyperic.hq.agent.server.AgentStartException;
import org.hyperic.hq.agent.server.monitor.AgentMonitorException;
import org.hyperic.hq.agent.server.monitor.AgentMonitorSimple;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.measurement.agent.ScheduledMeasurement;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.product.MeasurementPluginManager;
import org.hyperic.hq.product.Metric;
import org.hyperic.hq.product.MetricInvalidException;
import org.hyperic.hq.product.MetricNotFoundException;
import org.hyperic.hq.product.MetricUnreachableException;
import org.hyperic.hq.product.MetricValue;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.PluginNotFoundException;
import org.hyperic.util.TimeUtil;
import org.hyperic.util.collection.IntHashMap;
import org.hyperic.util.schedule.EmptyScheduleException;
import org.hyperic.util.schedule.Schedule;
import org.hyperic.util.schedule.ScheduleException;
import org.hyperic.util.schedule.ScheduledItem;
import org.hyperic.util.schedule.UnscheduledItemException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The schedule thread which maintains the schedule, and dispatches on them.
 * After data is retrieved, it is sent to the SenderThread which handles
 * depositing the results on disk, and sending them to the bizapp.
 */

public class ScheduleThread 
    extends AgentMonitorSimple
    implements Runnable 
{
    // How often we check schedules when we think they are empty.
    private static final int POLL_PERIOD = 1000;
    private static final int UNREACHABLE_EXPIRE = (60 * 1000) * 5;
    private static final long WARN_FETCH_TIME = 5 * 1000; // 5 seconds.
    private static final Log _log =
        LogFactory.getLog(ScheduleThread.class.getName());
    
    // We are separating the platform availability schedule to make sure
    // it is collected first to so that we don't risk any metrics hanging 
    // and making the agent seem unavailable
    private ResourceSchedule _platformAvailSchedule;
    
    private final    Object     _lock = new Object();

    private          Map       _schedules;   // AppdefID -> Schedule
    private final    List      _pendingUnschedules = Collections.synchronizedList(new ArrayList());
    private final    List      _pendingSchedules = Collections.synchronizedList(new ArrayList());
    private volatile boolean   _shouldDie;   // Should I shut down?
    private final    Object    _interrupter = new Object();// Interrupt object
    private final    HashMap   _errors = new HashMap();      // Hash of DSNs to their errors

    private MeasurementPluginManager _manager;
    private SenderThread             _sender;  // Guy handling the results

    private long _stat_numMetricsFetched = 0;
    private long _stat_numMetricsFailed  = 0;
    private long _stat_totFetchTime      = 0;
    private long _stat_numMetricsScheduled = 0;
    private long _stat_maxFetchTime      = Long.MIN_VALUE;
    private long _stat_minFetchTime      = Long.MAX_VALUE;

    private static class ResourceSchedule {
        private Schedule _schedule = new Schedule();
        private AppdefEntityID _id;
        private long _lastUnreachble = 0;
        private List _retry = new ArrayList();
        private IntHashMap _collected = new IntHashMap();
    }

    ScheduleThread(SenderThread sender, MeasurementPluginManager manager)
        throws AgentStartException 
    {
        _schedules    = Collections.synchronizedMap(new HashMap());
        _shouldDie    = false;
        _manager      = manager;
        _sender       = sender;               
    }

    private ResourceSchedule getSchedule(ScheduledMeasurement meas) {
        String key = meas.getEntity().getAppdefKey();
        ResourceSchedule schedule = (ResourceSchedule)_schedules.get(key);
        if (schedule == null) {
            schedule = new ResourceSchedule();
            schedule._id = meas.getEntity();
            _schedules.put(key, schedule);
            _log.debug("Created ResourceSchedule for: " + key);
        }
        return schedule;
    }

    private void interruptMe(){
        synchronized (_interrupter) {
            _interrupter.notify();
        }
    }

    /**
     * Unschedule a previously scheduled, repeatable measurement.  
     *
     * @param ent Entity to unschedule metrics for
     */
    void unscheduleMeasurements(AppdefEntityID ent) {
        _pendingUnschedules.add(ent);
    }

    private void unscheduleMeasurements_internal() {
        synchronized (_pendingUnschedules) {
            for (Iterator it = _pendingUnschedules.iterator(); it.hasNext();) {
                AppdefEntityID ent = (AppdefEntityID) it.next();
                String key = ent.getAppdefKey();
                ScheduledItem[] items = null;
                if (ent.isPlatform() && _platformAvailSchedule != null &&
                        ent.equals(_platformAvailSchedule._id)) {
                    items = _platformAvailSchedule._schedule.getScheduledItems();
                    _platformAvailSchedule = null;
                    _log.debug("Un-scheduling metrics for Platform Availability");
                } else {
                    ResourceSchedule rs = (ResourceSchedule) _schedules.remove(key);
                    if (rs == null) {
                        _log.error("No measurement schedule for: " + key);
                        continue;
                    }
                    items = rs._schedule.getScheduledItems();
                    _log.debug("Un-scheduling " + items.length + " metrics for " + ent);
                }
                synchronized (_lock) {
                    _stat_numMetricsScheduled -= items.length;
                }

                for (int i = 0; i < items.length; i++) {
                    ScheduledMeasurement meas = (ScheduledMeasurement) items[i].getObj();
                    //For plugin/Collector awareness
                    ParsedTemplate tmpl = getParsedTemplate(meas);
                    tmpl.metric.setInterval(-1);
                }
                it.remove();
            }
        }
    }

    /**
     * Schedule a measurement to be taken at a given interval.
     *
     * @param meas The ScheduledMeasurement to add to the collection schedule.
     */
    void scheduleMeasurement(ScheduledMeasurement meas) {
        _pendingSchedules.add(meas);
        interruptMe(); // Notify ScheduleThread we have new schedules to process.
    }

    void scheduleMeasurements_internal() {
        synchronized (_pendingSchedules) {
            for (Iterator it = _pendingSchedules.iterator(); it.hasNext();) {
                ScheduledMeasurement meas = (ScheduledMeasurement) it.next();
                ResourceSchedule rs = getSchedule(meas);
                try {
                    final String platformTemplate =
                            ("system.avail:Type=Platform:Availability").toLowerCase();
                    final String dsn = meas.getDSN().toLowerCase();
                    if (_log.isDebugEnabled()) {
                        _log.debug("scheduleMeasurement " + dsn);
                    }
                    if (dsn.endsWith(platformTemplate)) {
                        _log.debug("Scheduling Platform Availability");
                        _platformAvailSchedule = new ResourceSchedule();
                        _platformAvailSchedule._id = meas.getEntity();
                        _platformAvailSchedule._schedule.scheduleItem(
                                meas, meas.getInterval(), true, true);
                    } else {
                        rs._schedule.scheduleItem(meas, meas.getInterval(), true, true);
                    }
                    synchronized (_lock) {
                        _stat_numMetricsScheduled++;
                    }
                } catch (ScheduleException e) {
                    _log.error("Unable to schedule metric '" +
                            getParsedTemplate(meas) + "', skipping. Cause is " +
                            e.getMessage(), e);
                }
                it.remove();
            }
        }
    }

    /**
     * Shut down the schedule thread.  
     */

    void die(){
        _shouldDie = true;
        interruptMe();
    }

    private void logCache(String basicMsg, ParsedTemplate tmpl, String msg,
                          Exception exc, boolean printStack){
        String oldMsg;
        boolean isDebug = _log.isDebugEnabled();

        synchronized(_errors){
            oldMsg = (String)_errors.get(tmpl.metric.toString());
        }

        if(!isDebug && oldMsg != null && oldMsg.equals(msg)){
            return;
        }

        if(isDebug){
            _log.error(basicMsg + " while processing Metric '" + tmpl + "'", exc);
        } else {
            _log.error(basicMsg + ": " + msg);
            if (printStack) {
                _log.error("Stack trace follows:", exc);
            }
        }

        synchronized(_errors){
            _errors.put(tmpl.metric.toString(), msg);
        }
    }

    /**
     * A method which does the main logging for the run() method.  It
     * ensures that we don't perform excessive logging when plugins
     * generate a lot of errors. 
     */
    private void logCache(String basicMsg, ParsedTemplate tmpl, Exception exc){
        logCache(basicMsg, tmpl, exc.getMessage(), exc, false);
    }

    private void clearLogCache(ParsedTemplate tmpl){
        synchronized(_errors){
            _errors.remove(tmpl.metric.toString());
        }
    }

    private class ParsedTemplate {
        String plugin;
        Metric metric;
        
        public String toString() {
            return plugin + ":" + metric.toDebugString();
        }
    }

    private ParsedTemplate getParsedTemplate(ScheduledMeasurement meas) {
        ParsedTemplate tmpl = new ParsedTemplate();
        String template = meas.getDSN();
        //duplicating some code from MeasurementPluginManager
        //so we can do Metric.setId
        int ix = template.indexOf(":");
        tmpl.plugin = template.substring(0, ix);
        String metric = template.substring(ix+1, template.length());
        tmpl.metric = Metric.parse(metric);
        return tmpl;
    }

    private ParsedTemplate toParsedTemplate(ScheduledMeasurement meas) {
        AppdefEntityID aid = meas.getEntity();
        int id = aid.getID();
        int type = aid.getType();
        ParsedTemplate tmpl = getParsedTemplate(meas);
        tmpl.metric.setId(type, id);
        tmpl.metric.setCategory(meas.getCategory());
        tmpl.metric.setInterval(meas.getInterval());
        return tmpl;
    }

    private MetricValue getValue(ParsedTemplate tmpl)
        throws PluginException, PluginNotFoundException,
               MetricNotFoundException, MetricUnreachableException
    {
        return _manager.getValue(tmpl.plugin, tmpl.metric);
    }

    private long collect(ResourceSchedule rs) {
        long timeOfNext;
        long now = System.currentTimeMillis();
        Schedule schedule = rs._schedule;
        try {
            timeOfNext = schedule.getTimeOfNext();
        } catch (EmptyScheduleException e) {
            return POLL_PERIOD + now;
        }
 
        boolean isUnreachable = false;
        if (rs._lastUnreachble != 0) {
            if ((now - rs._lastUnreachble) > UNREACHABLE_EXPIRE) {
                rs._lastUnreachble = 0;
                _log.info("Re-enabling metrics for: " + rs._id);
            }
            else {
                isUnreachable = true;
            }
        }

        rs._collected.clear();

        if (rs._retry.size() != 0) {
            if (_log.isDebugEnabled()) {
                _log.debug("Retrying " + rs._retry.size() +
                           " items (MetricValue.FUTUREs)");
            }
            collect(rs, rs._retry, isUnreachable);
            rs._retry.clear();
        }

        if (now < timeOfNext) {
            return timeOfNext;
        }

        List items;
        try {
            items = schedule.consumeNextItems();
            timeOfNext = schedule.getTimeOfNext();
        } catch (EmptyScheduleException e) {
            return POLL_PERIOD + now;
        }
        collect(rs, items, isUnreachable);
        return timeOfNext;
    }

    private void collect(ResourceSchedule rs,
                         List items,
                         boolean isUnreachable) {
 
        boolean isDebug = _log.isDebugEnabled();

        for (int i=0; i<items.size() && (_shouldDie == false); i++) {
            ScheduledMeasurement meas =
                (ScheduledMeasurement)items.get(i);

            AppdefEntityID aid = meas.getEntity();
            String category = meas.getCategory();
            ParsedTemplate dsn = toParsedTemplate(meas);
            MetricValue data = null;
            long currTime, timeDiff;
            boolean success = false;

            if (isUnreachable) {
                if (!category.equals(MeasurementConstants.CAT_AVAILABILITY)) {
                    // Prevent stacktrace bombs if a resource is
                    // down, but don't skip processing availability metrics.
                    _stat_numMetricsFailed++;
                    continue;
                }
            }

            currTime = System.currentTimeMillis();
            // XXX -- We should do something with the exceptions here.
            //        Maybe send some kind of error back to the
            //        bizapp?
            try {
                int mid = meas.getDsnID();
                if (rs._collected.get(mid) == Boolean.TRUE) {
                    if (isDebug) {
                        _log.debug("Skipping duplicate mid=" + mid +
                                   ", aid=" + rs._id);
                    }
                    continue; //avoid dups
                }
                data = getValue(dsn);
                if (data == null) {
                    // Don't allow plugins to return null from getValue(),
                    // convert these to MetricValue.NONE
                    _log.warn("Plugin returned null value for metric: " + dsn);
                    data = MetricValue.NONE;
                }
                rs._collected.put(mid, Boolean.TRUE);
                success = true;
                clearLogCache(dsn);
            } catch(PluginNotFoundException exc){
                logCache("Plugin not found", dsn, exc);
            } catch(PluginException exc){
                logCache("Measurement plugin error", dsn, exc);
            } catch(MetricInvalidException exc){
                logCache("Invalid Metric requested", dsn, exc);
            } catch(MetricNotFoundException exc){
                logCache("Metric Value not found", dsn, exc);
            } catch(MetricUnreachableException exc){
                logCache("Metric unreachable", dsn, exc);
                rs._lastUnreachble = currTime;
                isUnreachable = true;
                _log.warn("Disabling metrics for: " + rs._id);
            } catch(Exception exc){
                // Unexpected exception
                logCache("Error getting measurement value",
                         dsn, exc.toString(), exc, true);
            }
            
            // Stats stuff
            timeDiff = System.currentTimeMillis() - currTime;
            _stat_totFetchTime += timeDiff;
            if(timeDiff > _stat_maxFetchTime)
                _stat_maxFetchTime = timeDiff;

            if(timeDiff < _stat_minFetchTime)
                _stat_minFetchTime = timeDiff;

            if (timeDiff > WARN_FETCH_TIME) {
                _log.warn("Collection of metric: '" + dsn + 
                          "' took: " + timeDiff + "ms");
            }

            if (success) {
                if (isDebug) {
                    String msg =
                        "[" + aid + ":" + category +
                        "] Metric='" + dsn + "' -> " + data;
                    _log.debug(msg + " timestamp=" + data.getTimestamp());
                }
                if (data.isNone()) {
                    //wouldn't be inserted into the database anyhow
                    //but might as well skip sending this to the server.
                    continue;
                }
                else if (data.isFuture()) {
                    //for example, first time collecting an exec: metric
                    //adding to this list will cause the metric to be
                    //collected next time the schedule has items to consume
                    //rather than waiting for the metric's own interval
                    //which could take much longer to hit
                    //(e.g. Windows Updates on an 8 hour interval)
                    rs._retry.add(meas);
                    continue;
                }
                _sender.processData(meas.getDsnID(), data, 
                                    meas.getDerivedID());
                _stat_numMetricsFetched++;
            } else {
                _stat_numMetricsFailed++;
            }
        }
    }

    private long collect() {
        long timeOfNext = 0;
        synchronized (_schedules) {
            // want to make sure and schedule the platform availability first
            // so that we don't risk any metrics hanging and making the agent
            // seem unavailable
            if (_platformAvailSchedule != null) {
                if (_log.isDebugEnabled()) {
                    _log.debug("Platform schedule is not null");
                }
                timeOfNext = collect(_platformAvailSchedule);
            }
            else {
                if (_log.isDebugEnabled()) {
                    _log.debug("Platform schedule is null");
                }
            }

            if (_schedules.size() == 0) {
                //nothing scheduled
                return POLL_PERIOD + System.currentTimeMillis();
            }
            
            for (Iterator it = _schedules.values().iterator();
                 it.hasNext() && (_shouldDie == false);) {
                
                ResourceSchedule rs = (ResourceSchedule)it.next();

                try {
                    long next = collect(rs);
                    if (timeOfNext == 0) {
                        timeOfNext = next;
                    }
                    else {
                        timeOfNext = Math.min(next, timeOfNext);
                    }
                } catch (Throwable e) {
                    _log.error(e.getMessage(), e);
                }
            }
        }
        return timeOfNext;
    }

    /**
     * The main loop of the ScheduleThread, which watches the schedule
     * waits the appropriate time, and executes scheduled operations.
     */
    public void run(){
        boolean isDebug = _log.isDebugEnabled();
        while (_shouldDie == false) {

            // First process any unschedules/schedules
            unscheduleMeasurements_internal();
            scheduleMeasurements_internal();

            long timeOfNext = collect();
            long now = System.currentTimeMillis();
            if (timeOfNext > now) {
                long wait = timeOfNext - now;
                if (isDebug) {
                    _log.debug("Waiting " + wait + " ms until " +
                               TimeUtil.toString(now+wait));
                }
                try {
                    synchronized (_interrupter) {
                        _interrupter.wait(wait);
                    }
                } catch (InterruptedException e) {
                    _log.debug("Schedule thread kicked");
                }
            }
        }
    }

    /**
     * MONITOR METHOD:  Get the number of metrics in the schedule
     */
    public double getNumMetricsScheduled() 
        throws AgentMonitorException 
    {
        synchronized (_lock) {
            return _stat_numMetricsScheduled;            
        }
    }

    /**
     * MONITOR METHOD:  Get the number of metrics which were attempted
     *                  to be fetched (failed or successful)
     */
    public double getNumMetricsFetched() 
        throws AgentMonitorException 
    {
        return _stat_numMetricsFetched;
    }

    /**
     * MONITOR METHOD:  Get the number of metrics which resulted in an
     *                  error when collected
     */
    public double getNumMetricsFailed() 
        throws AgentMonitorException 
    {
        return _stat_numMetricsFailed;
    }

    /**
     * MONITOR METHOD:  Get the total time spent fetching metrics
     */
    public double getTotFetchTime() 
        throws AgentMonitorException 
    {
        return _stat_totFetchTime;
    }

    /**
     * MONITOR METHOD:  Get the maximum time spent fetching a metric
     */
    public double getMaxFetchTime() 
        throws AgentMonitorException 
    {
        if(_stat_maxFetchTime == Long.MIN_VALUE)
            return MetricValue.VALUE_NONE;

        return _stat_maxFetchTime;
    }

    /**
     * MONITOR METHOD:  Get the minimum time spent fetching a metric
     */
    public double getMinFetchTime() 
        throws AgentMonitorException 
    {
        if(_stat_minFetchTime == Long.MAX_VALUE)
            return MetricValue.VALUE_NONE;

        return _stat_minFetchTime;
    }
}
