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

package org.hyperic.hq.agent.handler.measurement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.agent.server.AgentStartException;
import org.hyperic.hq.agent.server.monitor.AgentMonitorException;
import org.hyperic.hq.agent.server.monitor.AgentMonitorSimple;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.product.*;
import org.hyperic.util.TimeUtil;
import org.hyperic.util.collection.IntHashMap;
import org.hyperic.util.schedule.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * The schedule thread which maintains the schedule, and dispatches on them.
 * After data is retrieved, it is sent to the SenderThread which handles
 * depositing the results on disk, and sending them to the bizapp.
 */

public class ScheduleThread 
    extends AgentMonitorSimple
    implements Runnable 
{
    // Agent properties configuration
    static final String PROP_POOLSIZE =
            "scheduleThread.poolsize."; // e.g. scheduleThread.poolsize.system=10
    static final String PROP_FETCH_LOG_TIMEOUT =
            "scheduleThread.fetchLogTimeout";
    static final String PROP_CANCEL_TIMEOUT =
            "scheduleThread.cancelTimeout";
    static final String PROP_QUEUE_SIZE =
            "scheduleThread.queuesize.";

    // How often we check schedules when we think they are empty.
    private static final int POLL_PERIOD = 1000;
    private static final int UNREACHABLE_EXPIRE = (60 * 1000) * 5;

    private static final long FETCH_TIME  = 2000; // 2 seconds.
    private static final long CANCEL_TIME = 5000; // 5 seconds.
    private static final int  EXECUTOR_QUEUE_SIZE = 10000;

    private long logFetchTimeout = FETCH_TIME;
    private long cancelTimeout = CANCEL_TIME;

    private static final Log log = LogFactory.getLog(ScheduleThread.class.getName());

    // AppdefID -> Schedule
    private final Map<String,ResourceSchedule> schedules = new HashMap<String,ResourceSchedule>();
    // Should I shut down?
    private volatile boolean shouldDie = false;
    // Interrupt object
    private final Object interrupter = new Object();
    // Hash of DSNs to their errors
    private final HashMap<String,String> errors = new HashMap<String,String>();
    private final Properties agentConfig; // agent.properties

    // Map of Executors, one per plugin
    private final HashMap<String,ThreadPoolExecutor> executors = new HashMap<String,ThreadPoolExecutor>();
    // Map of asynchronous MetricTasks pending confirmation
    private final HashMap<Future,MetricTask> metricCollections = new HashMap<Future,MetricTask>();
    // The executor confirming metric collections, cancelling tasks that exceed
    // our timeouts.
    private ScheduledExecutorService metricVerificationService;
    private ScheduledFuture metricVerificationTask;
    private ScheduledFuture metricLoggingTask;
    
    private MeasurementValueGetter manager;
    private Sender sender;  // Guy handling the results

    // Statistics
    private final Object statsLock = new Object();
    private long statNumMetricsFetched = 0;
    private long statNumMetricsFailed = 0;
    private long statTotFetchTime = 0;
    private long statNumMetricsScheduled = 0;
    private long statMaxFetchTime = Long.MIN_VALUE;
    private long statMinFetchTime = Long.MAX_VALUE;

    ScheduleThread(Sender sender, MeasurementValueGetter manager,
                   Properties config)
        throws AgentStartException
    {
        agentConfig = config;
        this.manager = manager;
        this.sender = sender;

        String sLogFetchTimeout = agentConfig.getProperty(PROP_FETCH_LOG_TIMEOUT);
        if(sLogFetchTimeout != null){
            try {
                logFetchTimeout = Integer.parseInt(sLogFetchTimeout);
                log.info("Log fetch timeout set to " + logFetchTimeout);
            } catch(NumberFormatException exc){
                log.error("Invalid setting for " + PROP_FETCH_LOG_TIMEOUT + " value=" +
                           sLogFetchTimeout + ", using defaults.");
            }
        }

        String sCancelTimeout = agentConfig.getProperty(PROP_CANCEL_TIMEOUT);
        if(sCancelTimeout != null){
            try {
                cancelTimeout = Integer.parseInt(sCancelTimeout);
                log.info("Cancel timeout set to " + cancelTimeout);
            } catch(NumberFormatException exc){
                log.error("Invalid setting for " + PROP_CANCEL_TIMEOUT + " value=" +
                           sCancelTimeout + ", using defaults.");
            }
        }

        metricVerificationService = Executors.newSingleThreadScheduledExecutor();
        metricVerificationTask =
                metricVerificationService.scheduleAtFixedRate(new MetricVerificationTask(),
                                                               POLL_PERIOD, POLL_PERIOD,
                                                               TimeUnit.MILLISECONDS);
        metricLoggingTask =
                metricVerificationService.scheduleAtFixedRate(new MetricLoggingTask(),
                                                               1, 600, TimeUnit.SECONDS);
    }

    /**
     * Task for printing Executor statistics
     */
    private class MetricLoggingTask implements Runnable {
        public void run() {
            for (String plugin : executors.keySet()) {
                ThreadPoolExecutor executor = executors.get(plugin);
                if (log.isDebugEnabled()) {
                    log.debug("Plugin=" + plugin + ", " +
                              "CompletedTaskCount=" + executor.getCompletedTaskCount() + ", " +
                              "ActiveCount=" + executor.getActiveCount() + ", " +
                              "TaskCount=" + executor.getTaskCount() + ", " +
                              "PoolSize=" + executor.getPoolSize());
                }
            }
        }
    }

    /**
     * The MetricVerificationTask iterates over the list of FutureTasks that
     * have been submitted for execution.  Each task is checked for completion
     * and then removed.  For tasks that do not complete within the timeout
     * are cancelled, which will attempt to free up the executor running the
     * task.
     * NOTE: This will only work if the hung task is in an interrupt-able state
     *       i.e. sleep() or wait()
     */
    private class MetricVerificationTask implements Runnable  {
        public void run() {
            boolean isDebugEnabled = log.isDebugEnabled();
            synchronized (metricCollections) {
                if (isDebugEnabled && metricCollections.size() > 0) {
                    log.debug(metricCollections.size() + " metrics to validate.");
                }
                for (Iterator<Future> i = metricCollections.keySet().iterator();
                     i.hasNext();) {
                    Future t = i.next();
                    MetricTask mt = metricCollections.get(t);
                    if (t.isDone())
                    {
                        if (isDebugEnabled) {
                            log.debug("Metric task '" + mt +
                                   "' complete, duration: " +
                                   mt.getExecutionDuration());
                        }
                        i.remove();
                    } else {
                        // Not complete, check for timeout
                        if (mt.getExecutionDuration() > cancelTimeout) {
                            boolean res = t.cancel(true);
                            log.error("Metric '" + mt +
                                       "' took too long to run (" + mt.getExecutionDuration() +
                                       "ms), cancelled (result=" + res + ")");

                            // If the metric is Availability, send a down data point in
                            // case the metric cancellation fails.
                            ParsedTemplate pt = getParsedTemplate(mt.meas);
                            if (pt.metric.isAvail()) {
                                MetricValue data = new MetricValue(MeasurementConstants.AVAIL_DOWN);
                                sender.processData(mt.meas.getDsnID(), data,
                                                   mt.meas.getDerivedID());
                            }
                            // Task will be removed on next iteration
                        }
                    }
                }
            }
        }
    }

    private static class ResourceSchedule {
        private Schedule       schedule = new Schedule();
        private AppdefEntityID id;
        private long           lastUnreachble = 0;
        private List<ScheduledMeasurement> retry = new ArrayList<ScheduledMeasurement>();
        private IntHashMap collected = new IntHashMap();
    }

    private ResourceSchedule getSchedule(ScheduledMeasurement meas) {
        String key = meas.getEntity().getAppdefKey();
        ResourceSchedule schedule;
        synchronized (schedules) {
            schedule = schedules.get(key);
            if (schedule == null) {
                schedule = new ResourceSchedule();
                schedule.id = meas.getEntity();
                schedules.put(key, schedule);
                log.debug("Created ResourceSchedule for: " + key);
            }
        }
        
        return schedule;
    }

    // TODO: I don't think this works properly, hence the slow agent shutdowns..
    private void interruptMe(){
        synchronized (interrupter) {
            interrupter.notify();
        }
    }

    /**
     * Shut down the schedule thread.
     */
    void die(){
        shouldDie = true;
        for (String s : executors.keySet()) {
            ThreadPoolExecutor executor = executors.get(s);
            List<Runnable> queuedMetrics = executor.shutdownNow();
            log.info("Shut down executor service for plugin '" + s + "'" +
                      " with " + queuedMetrics.size() + " queued collections");
        }

        metricLoggingTask.cancel(true);
        metricVerificationTask.cancel(true);
        List<Runnable> pending = metricVerificationService.shutdownNow();
        log.info("Shutdown metric verification task with " +
                  pending.size() + " tasks");
        
        interruptMe();
    }

    /**
     * Un-schedule a previously scheduled, repeatable measurement.
     *
     * @param ent Entity to un-schedule metrics for
     *
     * @throws org.hyperic.util.schedule.UnscheduledItemException indicating the passed ID was not found
     */
    void unscheduleMeasurements(AppdefEntityID ent)
        throws UnscheduledItemException
    {
        String key = ent.getAppdefKey();
        ScheduledItem[] items;

        ResourceSchedule rs;
        synchronized (schedules) {
            rs = schedules.remove(key);
        }

        if (rs == null) {
            throw new UnscheduledItemException("No measurement schedule for: " + key);
        }

        items = rs.schedule.getScheduledItems();
        log.debug("Un-scheduling " + items.length + " metrics for " + ent);

        synchronized (statsLock) {
            statNumMetricsScheduled -= items.length;
        }

        for (ScheduledItem item : items) {
            ScheduledMeasurement meas = (ScheduledMeasurement) item.getObj();
            //For plugin/Collector awareness
            ParsedTemplate tmpl = getParsedTemplate(meas);
            tmpl.metric.setInterval(-1);
        }
    }

    /**
     * Schedule a measurement to be taken at a given interval.  
     *
     * @param meas Measurement to schedule
     */

    void scheduleMeasurement(ScheduledMeasurement meas){
        ResourceSchedule rs = getSchedule(meas);
        try {
            if (log.isDebugEnabled()) {
                log.debug("scheduleMeasurement " + getParsedTemplate(meas).metric.toDebugString());
            }

            rs.schedule.scheduleItem(meas, meas.getInterval(), true, true);

            synchronized (statsLock) {
                statNumMetricsScheduled++;
            }
        } catch (ScheduleException e) {
            log.error("Unable to schedule metric '" +
                      getParsedTemplate(meas) + "', skipping. Cause is " +
                      e.getMessage(), e);
        }
    }

    private void logCache(String basicMsg, ParsedTemplate tmpl, String msg,
                          Exception exc, boolean printStack){
        String oldMsg;
        boolean isDebug = log.isDebugEnabled();

        synchronized(errors){
            oldMsg = errors.get(tmpl.metric.toString());
        }

        if(!isDebug && oldMsg != null && oldMsg.equals(msg)){
            return;
        }

        if(isDebug){
            log.error(basicMsg + " while processing Metric '" + tmpl + "'", exc);
        } else {
            log.error(basicMsg + ": " + msg);
            if (printStack) {
                log.error("Stack trace follows:", exc);
            }
        }

        synchronized(errors){
            errors.put(tmpl.metric.toString(), msg);
        }
    }

    /**
     * A method which does the main logging for the run() method.  It
     * ensures that we don't perform excessive logging when plugins
     * generate a lot of errors.
     *
     * @param basicMsg The basic log message
     * @param tmpl The template causing the errors
     * @param exc The Exception to be logged.
     */
    private void logCache(String basicMsg, ParsedTemplate tmpl, Exception exc){
        logCache(basicMsg, tmpl, exc.getMessage(), exc, false);
    }

    private void clearLogCache(ParsedTemplate tmpl){
        synchronized(errors){
            errors.remove(tmpl.metric.toString());
        }
    }

    static class ParsedTemplate {
        String plugin;
        Metric metric;
        
        public String toString() {
            return plugin + ":" + metric.toDebugString();
        }
    }

    static ParsedTemplate getParsedTemplate(ScheduledMeasurement meas) {
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
        throws PluginException, MetricNotFoundException,
               MetricUnreachableException
    {
        return manager.getValue(tmpl.plugin, tmpl.metric);
    }

    private class MetricTask implements Runnable {
        ResourceSchedule rs;
        ScheduledMeasurement meas;
        long executeStartTime = 0;
        long executeEndTime = 0;

        MetricTask(ResourceSchedule rs, ScheduledMeasurement meas) {
            this.rs = rs;
            this.meas = meas;
        }

        /**
         * @return The string representing this metric.
         */
        public String toString() {
            return getParsedTemplate(meas).metric.toDebugString();
        }

        /**
         * @return 0 if task is still queued for execution, otherwise the
         * amount of time in milliseconds the task has been running.
         */
        public long getExecutionDuration() {
            if (executeStartTime == 0) {
                // Still queued for execution
                return executeStartTime;
            } else if (executeEndTime == 0) {
                // Currently executing
                return System.currentTimeMillis() - executeStartTime;
            } else {
                // Completed
                return executeEndTime - executeStartTime;
            }
        }

        public void run() {
            boolean isDebug = log.isDebugEnabled();
            AppdefEntityID aid = meas.getEntity();
            String category = meas.getCategory();
            ParsedTemplate dsn = toParsedTemplate(meas);
            MetricValue data = null;
            executeStartTime = System.currentTimeMillis();
            boolean success = false;

            if (rs.lastUnreachble != 0) {
                if (!category.equals(MeasurementConstants.CAT_AVAILABILITY)) {
                    // Prevent stacktrace bombs if a resource is
                    // down, but don't skip processing availability metrics.
                    statNumMetricsFailed++;
                    return;
                }
            }

            // XXX -- We should do something with the exceptions here.
            //        Maybe send some kind of error back to the
            //        bizapp?
            try {
                int mid = meas.getDsnID();
                if (rs.collected.get(mid) == Boolean.TRUE) {
                    if (isDebug) {
                        log.debug("Skipping duplicate mid=" + mid + ", aid=" + rs.id);
                    }
                }
                data = getValue(dsn);
                if (data == null) {
                    // Don't allow plugins to return null from getValue(),
                    // convert these to MetricValue.NONE
                    log.warn("Plugin returned null value for metric: " + dsn);
                    data = MetricValue.NONE;
                }
                rs.collected.put(mid, Boolean.TRUE);
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
                rs.lastUnreachble = executeStartTime;
                log.warn("Disabling metrics for: " + rs.id);
            } catch(Exception exc){
                // Unexpected exception
                logCache("Error getting measurement value",
                         dsn, exc.toString(), exc, true);
            }

            // Stats stuff
            Long timeDiff = System.currentTimeMillis() - executeStartTime;

            synchronized (statsLock) {
                statTotFetchTime += timeDiff;
                if(timeDiff > statMaxFetchTime) {
                    statMaxFetchTime = timeDiff;
                }

                if(timeDiff < statMinFetchTime) {
                    statMinFetchTime = timeDiff;
                }
            }

            if (timeDiff > logFetchTimeout) {
                log.warn("Collection of metric: '" + dsn + "' took: " + timeDiff + "ms");
            }

            if (success) {
                if (isDebug) {
                    String debugDsn = getParsedTemplate(meas).metric.toDebugString();
                    String msg =
                        "[" + aid + ":" + category +
                        "] Metric='" + debugDsn + "' -> " + data;

                    log.debug(msg + " timestamp=" + data.getTimestamp());
                }
                if (data.isNone()) {
                    //wouldn't be inserted into the database anyhow
                    //but might as well skip sending this to the server.
                    return;
                }
                else if (data.isFuture()) {
                    //for example, first time collecting an exec: metric
                    //adding to this list will cause the metric to be
                    //collected next time the schedule has items to consume
                    //rather than waiting for the metric's own interval
                    //which could take much longer to hit
                    //(e.g. Windows Updates on an 8 hour interval)
                    rs.retry.add(meas);
                    return;
                }
                sender.processData(meas.getDsnID(), data,
                                    meas.getDerivedID());
                synchronized (statsLock) {
                    statNumMetricsFetched++;
                }
            } else {
                synchronized (statsLock) {
                    statNumMetricsFailed++;
                }
            }
        }
    }

    private int getQueueSize(String plugin) {
        String prop = PROP_QUEUE_SIZE + plugin;
        String sQueueSize = agentConfig.getProperty(prop);
        if(sQueueSize != null){
            try {
                return Integer.parseInt(sQueueSize);
            } catch(NumberFormatException exc){
                log.error("Invalid setting for " + prop + " value=" +
                           sQueueSize + " using defaults.");
            }
        }
        return EXECUTOR_QUEUE_SIZE;
    }

    private int getPoolSize(String plugin) {
        String prop = PROP_POOLSIZE + plugin;
        String sPoolSize = agentConfig.getProperty(prop);
        if(sPoolSize != null){
            try {
                return Integer.parseInt(sPoolSize);
            } catch(NumberFormatException exc){
                log.error("Invalid setting for " + prop + " value=" +
                           sPoolSize + " using defaults.");
            }
        }
        return 1;
    }

    private void collect(ResourceSchedule rs, List items)
    {
        for (int i=0; i<items.size() && (!shouldDie); i++) {
            ScheduledMeasurement meas =
                (ScheduledMeasurement)items.get(i);
            ParsedTemplate tmpl = toParsedTemplate(meas);

            ThreadPoolExecutor executor;
            String plugin;
            synchronized (executors) {
                try {
                    GenericPlugin p = manager.getPlugin(tmpl.plugin).getProductPlugin();
                    plugin = p.getName();
                } catch (PluginNotFoundException e) {
                    // Proxied plugin?
                    plugin = tmpl.plugin;
                }

                executor = executors.get(plugin);
                if (executor == null) {
                    int poolSize = getPoolSize(plugin);
                    int queueSize = getQueueSize(plugin);
                    log.info("Creating executor for plugin '" + plugin +
                              "' with a poolsize=" + poolSize + " queuesize=" + queueSize);
                    executor = new ThreadPoolExecutor(poolSize, poolSize,
                                                 60, TimeUnit.SECONDS,
                                                 new LinkedBlockingQueue<Runnable>(queueSize),
                                                 new ThreadPoolExecutor.AbortPolicy());
                    executors.put(plugin, executor);
                }
            }

            MetricTask metricTask = new MetricTask(rs, meas);
            try {
                Future<?> task = executor.submit(metricTask);
                synchronized (metricCollections) {
                    metricCollections.put(task,metricTask);
                }
            } catch (RejectedExecutionException e) {
                log.warn("Executor[" + plugin + "] rejected metric task " + metricTask);
                statNumMetricsFailed++;
            }
        }
    }

    private long collect(ResourceSchedule rs) {
        long timeOfNext;
        long now = System.currentTimeMillis();
        Schedule schedule = rs.schedule;
        try {
            timeOfNext = schedule.getTimeOfNext();
        } catch (EmptyScheduleException e) {
            return POLL_PERIOD + now;
        }

        if (rs.lastUnreachble != 0) {
            if ((now - rs.lastUnreachble) > UNREACHABLE_EXPIRE) {
                rs.lastUnreachble = 0;
                log.info("Re-enabling metrics for: " + rs.id);
            }
        }

        rs.collected.clear();

        if (rs.retry.size() != 0) {
            if (log.isDebugEnabled()) {
                log.debug("Retrying " + rs.retry.size() + " items (MetricValue.FUTUREs)");
            }
            collect(rs, rs.retry);
            rs.retry.clear();
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
        collect(rs, items);
        return timeOfNext;
    }

    private long collect() {
        long timeOfNext = 0;
        
        Map<String,ResourceSchedule> schedules = null;
        synchronized (this.schedules) {
            if (this.schedules.size() == 0) {
                //nothing scheduled
                timeOfNext = POLL_PERIOD + System.currentTimeMillis();
            } else {
                schedules = new HashMap<String,ResourceSchedule>(this.schedules);
            }
        }

        if (schedules != null) {
            for (Iterator<ResourceSchedule> it = schedules.values().iterator();
            it.hasNext() && (!shouldDie);) {

                ResourceSchedule rs = it.next();
                try {
                    long next = collect(rs);
                    if (timeOfNext == 0) {
                        timeOfNext = next;
                    } else {
                        timeOfNext = Math.min(next, timeOfNext);
                    }
                } catch (Throwable e) {
                    log.error(e.getMessage(), e);
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
        boolean isDebug = log.isDebugEnabled();
        while (!shouldDie) {
            long timeOfNext = collect();
            long now = System.currentTimeMillis();
            if (timeOfNext > now) {
                long wait = timeOfNext - now;
                if (isDebug) {
                    log.debug("Waiting " + wait + " ms until " +
                               TimeUtil.toString(now+wait));
                }
                try {
                    synchronized (interrupter) {
                        interrupter.wait(wait);
                    }
                } catch (InterruptedException e) {
                    log.debug("Schedule thread kicked");
                }
            }
        }
        log.info("Schedule thread shut down");
    }

    // MONITOR METHODS

    /**
     * @return Get the number of metrics in the schedule
     */
    public double getNumMetricsScheduled() throws AgentMonitorException {
        synchronized (statsLock) {
            return statNumMetricsScheduled;
        }
    }

    /**
     * @return The number of metrics which were attempted to be fetched (failed or successful)
     */
    public double getNumMetricsFetched() throws AgentMonitorException {
        synchronized (statsLock) {
            return statNumMetricsFetched;
        }
    }

    /**
     * @return Get the number of metrics which resulted in an error when collected
     */
    public double getNumMetricsFailed() throws AgentMonitorException {
        synchronized (statsLock) {
            return statNumMetricsFailed;
        }
    }

    /**
     * @return The total time spent fetching metrics
     */
    public double getTotFetchTime() throws AgentMonitorException {
        synchronized (statsLock) {
            return statTotFetchTime;
        }
    }

    /**
     * @return The maximum time spent fetching a metric
     */
    public double getMaxFetchTime() throws AgentMonitorException {
        synchronized (statsLock) {
            if(statMaxFetchTime == Long.MIN_VALUE) {
                return MetricValue.VALUE_NONE;
            }
            return statMaxFetchTime;
        }
    }

    /**
     * @return The minimum time spent fetching a metric
     */
    public double getMinFetchTime() throws AgentMonitorException {
        synchronized (statsLock) {
            if(statMinFetchTime == Long.MAX_VALUE) {
                return MetricValue.VALUE_NONE;
            }
            return statMinFetchTime;
        }
    }
}
