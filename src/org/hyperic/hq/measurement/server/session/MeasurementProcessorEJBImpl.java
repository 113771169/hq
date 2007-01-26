/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.FinderException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.hyperic.hq.appdef.shared.AgentValue;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.MeasurementScheduleException;
import org.hyperic.hq.measurement.MeasurementUnscheduleException;
import org.hyperic.hq.measurement.TimingVoodoo;
import org.hyperic.hq.measurement.server.session.SRN;
import org.hyperic.hq.measurement.server.session.DerivedMeasurement;
import org.hyperic.hq.measurement.server.session.RawMeasurement;
import org.hyperic.hq.measurement.data.DataNotAvailableException;
import org.hyperic.hq.measurement.ext.MonitorFactory;
import org.hyperic.hq.measurement.ext.MonitorInterface;
import org.hyperic.hq.measurement.ext.ScheduleMetricInfo;
import org.hyperic.hq.measurement.ext.UnscheduleMetricInfo;
import org.hyperic.hq.measurement.ext.depgraph.CircularDependencyException;
import org.hyperic.hq.measurement.ext.depgraph.DerivedNode;
import org.hyperic.hq.measurement.ext.depgraph.Graph;
import org.hyperic.hq.measurement.ext.depgraph.RawNode;
import org.hyperic.hq.measurement.monitor.MonitorAgentException;
import org.hyperic.hq.measurement.monitor.MonitorCreateException;
import org.hyperic.hq.measurement.shared.DerivedMeasurementValue;
import org.hyperic.hq.measurement.shared.MeasurementProcessorLocal;
import org.hyperic.hq.measurement.shared.MeasurementProcessorUtil;
import org.hyperic.hq.measurement.shared.RawMeasurementValue;
import org.hyperic.hq.measurement.shared.SRNManagerLocal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.SchedulerException;
import org.hibernate.ObjectNotFoundException;

/** The MeasurementProcessor class runs as a daemon that receives
 *  DerivedMeasurements to be scheduled, and RawMeasurements to be
 *  calculated.
 *
 * @ejb:transaction type="REQUIRED"
 * @ejb:bean name="MeasurementProcessor"
 *      jndi-name="ejb/measurement/MeasurementProcessor"
 *      local-jndi-name="LocalMeasurementProcessor"
 *      view-type="local"
 *      type="Stateless"
 */
public class MeasurementProcessorEJBImpl 
    extends SessionEJB 
    implements SessionBean 
{
    private static final String logCtx = 
        MeasurementProcessorEJBImpl.class.getName();
    private final Log log = LogFactory.getLog(logCtx);
    private static Log timingLog =
        LogFactory.getLog(MeasurementConstants.MEA_TIMING_LOG);

    /**
     * Ping the agent to make sure it's up
     * @ejb:interface-method
     */
    public boolean ping(AgentValue aconn)
        throws PermissionException, MonitorCreateException {
        // Schedule RawMeasurements with a monitor
        MonitorInterface monitor =
            MonitorFactory.newInstance(aconn.getAgentType().getName());
        
        return monitor.ping(aconn);
    }

    /**
     * Schedule a DerivedMeasurement and all of its dependent
     * measurements to be collected and calculated.
     *
     * @ejb:interface-method
     *
     * @param entId The AppdefEntityID to schedule
     * @param graphs the graph for measurement
     */
    public void schedule(AppdefEntityID entId, Graph[] graphs,
                         Set agentSchedule, Set serverSchedule)
        throws PermissionException, MeasurementScheduleException,
               MonitorAgentException {
        long scheduleTime = System.currentTimeMillis();

        SRNManagerLocal srnManager = getSRNManager();
        try {
            Map toScheduleRaw = new HashMap();
            List toScheduleDerived = new ArrayList();
            
            long minInterval = Long.MAX_VALUE;
            
            for (int i = 0; i < graphs.length; i++) {
                // for each derived template node in the graph, schedule
                // its raw template measurements with the agent and then
                // schedule the derived measurement job with quartz
                Set dns = graphs[i].getDerivedNodes();
                for (Iterator it=dns.iterator(); it.hasNext();) {
                    DerivedNode dn = (DerivedNode)it.next();
                    DerivedMeasurementValue dmval =
                        getDMByTemplateAndInstance(new Integer(dn.getId()),
                                                   entId.getID());

                    // update the interval for each derived measurement to
                    // the interval computed by the graph if they are different
                    long interval;
                    try {
                        interval = dn.getInterval();
                    } catch (CircularDependencyException e) {
                        throw new MeasurementScheduleException(
                            "Circular dependency found for derived measurement",
                            new Integer(dn.getId()), e);
                    }

                    if (dmval.getInterval() != interval ||
                        !dmval.getEnabled()){
                        log.info("Updating interval for DerivedMeasurement " +
                                 "from " + dmval.getInterval() + " to " +
                                 interval);

                        DerivedMeasurement dm = getDerivedMeasurementDAO()
                            .findById(dmval.getId());

                        dm.setInterval(interval);
                        dm.setEnabled(interval != 0);
                        // Update the derived measurement value
                        dmval = dm.getDerivedMeasurementValue();
                    }
                    
                    // Do not continue if interval was 0
                    if (interval == 0)
                        continue;

                    // Get the minimum interval time
                    minInterval = Math.min(minInterval, interval);
                                    
                    // Insert the measurements to be scheduled into map
                    ArrayList rmVals = new ArrayList();
                    Set rns = dn.getRawOutgoing();
                    for (Iterator jt=rns.iterator(); jt.hasNext();) {
                        RawNode rn = (RawNode)jt.next();
                        RawMeasurementValue rmval =
                            getRMByTemplateAndInstance(new Integer(rn.getId()),
                                                       entId.getID());
                        // make sure we're supposed to schedule this one
                        if ( agentSchedule.contains( rmval.getId() ) ) {
                            rmVals.add(rmval);
                        }
                    }
                    if (rmVals.size() > 0) {
                        toScheduleRaw.put(
                            dmval, rmVals.toArray(new RawMeasurementValue[0]));
                    }

                    // Not pass-thru                
                    if (!dmval.getFormula().equals
                            (MeasurementConstants.TEMPL_IDENTITY)) {
                        toScheduleDerived.add(dmval);
                    }
                }
            }

            // Schedule the measurements
            scheduleRawMeasurements(entId, toScheduleRaw,
                srnManager.beginIncrementSrn(entId, minInterval));
            scheduleDerivedMeasurement(toScheduleDerived, serverSchedule);
        } catch (SchedulerException e) {
            throw new MeasurementScheduleException(
                "Cannot schedule measurement", e);
        } catch (FinderException e) {
            throw new MeasurementScheduleException(
                "Cannot expand measurement", e);
        } catch (PermissionException e) {
            throw new MonitorAgentException(e);
        } catch (MonitorCreateException e) {
            throw new MonitorAgentException(e);
        } finally {
            srnManager.endIncrementSrn(entId);
        }
        
        logTime("schedule",scheduleTime);
    }

    private void unschedule(AgentValue aconn, AppdefEntityID[] entIds)
        throws MeasurementUnscheduleException, MonitorAgentException {
        try {
            // Create SRNs from the measurements
            UnscheduleMetricInfo[] schedule =
                new UnscheduleMetricInfo[entIds.length];
            
            SRNManagerLocal srnManager = getSRNManager();
            for (int i = 0; i < schedule.length; i++) {
                schedule[i] = new UnscheduleMetricInfo(entIds[i]);
                try {
                    srnManager.removeSrn(entIds[i]);
                } catch (ObjectNotFoundException e) {
                    // Ok to ignore, this is the first time scheduling metrics
                    // for this resource.
                }
            }

            // Get the monitor for the agent type
            MonitorInterface monitor =
                MonitorFactory.newInstance(aconn.getAgentType().getName());

            monitor.unschedule(aconn, schedule);
        } catch (MonitorCreateException e) {
            throw new MeasurementUnscheduleException(
                "Could not create monitor", aconn.getId(), e);
        }
    }
    
    /** Unschedule metrics of multiple appdef entities
     * @ejb:interface-method
     * @param agentToken the entity whose agent will be contacted for the
     * unschedule
     * @param entIds the entity IDs whose metrics should be unscheduled
     * @throws MeasurementUnscheduleException if an error occurs
     */
    public void unschedule(String agentToken, AppdefEntityID[] entIds)
        throws MeasurementUnscheduleException {
        try {
            // Get the agent from agent token
            AgentValue aconn = getAgentConnection(agentToken);
            unschedule(aconn, entIds);
        } catch (MonitorAgentException e) {
        }

    }
    
    /** Unschedule metrics of multiple appdef entities
     * @ejb:interface-method
     * @param agentEnt the entity whose agent will be contacted for the unschedule
     * @param entIds the entity IDs whose metrics should be unscheduled
     * @throws MeasurementUnscheduleException if an error occurs
     */
    public void unschedule(AppdefEntityID agentEnt, AppdefEntityID[] entIds)
        throws MeasurementUnscheduleException {
        try {
            // Get the agent IP and Port from server ID
            AgentValue aconn = getAgentConnection(agentEnt);
            unschedule(aconn, entIds);
        } catch (MonitorAgentException e) {
        }
    }

    /** Unschedule a measurement
     * @ejb:interface-method
     * @param entId The Appdef ID to unschedule
     * @throws MeasurementUnscheduleException if an error occurs
     */
    public void unschedule(AppdefEntityID entId)
        throws MeasurementUnscheduleException, PermissionException {
        this.unschedule(entId, new AppdefEntityID[] { entId });
    }

    /**
     * This method will handle "backfilling" old derived measurement
     * values that were missed.
     *
     * @param oldDataPoints <code>{@link java.util.Map}</code> of
     * Integer (raw measurement id) to List of Long (timestamps)
     *
     * @ejb:interface-method
     */
    public void recalculateMeasurements(Map oldDataPoints)
        throws MeasurementScheduleException {
        final String err = "Couldn't calculate derived measurement ";
        
        // Convert the Map of
        // (rawId --> [retrievalTime0,...,retrievalTimeN])
        // into a Map of
        // (derivedMeasValue --> [scheduledTime0,...,scheduledTimeN])
        Map missedDerivedDataPoints = new HashMap();
        for (Iterator it = oldDataPoints.entrySet().iterator();
             it.hasNext();)
        {
            Map.Entry entry = (Map.Entry)it.next();
            Integer rawId = (Integer)entry.getKey();
            List retrievalTimes = (List)entry.getValue();


            Collection dmsForRaw =
                getDerivedMeasurementDAO().findByRawExcludeIdentity(rawId);
            for (Iterator jt = dmsForRaw.iterator(); jt.hasNext();) {
                DerivedMeasurement dmBean = (DerivedMeasurement)jt.next();
                List scheduledTimes = new ArrayList();
                for (Iterator kt = retrievalTimes.iterator();
                     kt.hasNext(); )
                {
                    long retrievalTime = ((Long)kt.next()).longValue();
                    scheduledTimes.add(
                        new Long(TimingVoodoo.roundDownTime(retrievalTime,
                            dmBean.getInterval())));
                    }
                    missedDerivedDataPoints.put(dmBean,
                                                scheduledTimes);
                }
        }

        // Iterate through the missed derived measurements and
        // calculate them.
        CalculateDerivedMeasurementJob calculator =
            new CalculateDerivedMeasurementJob();
        for (Iterator it=missedDerivedDataPoints.entrySet().iterator();
             it.hasNext();) {
            Map.Entry entry = (Map.Entry)it.next();
            DerivedMeasurement measurement = (DerivedMeasurement)entry.getKey();
            List scheduledTimes = (List)entry.getValue();
            if ( log.isDebugEnabled() ) {
                log.debug
                    ( "Back-filling " + scheduledTimes.size() +
                      " old data points for derived measurement " +
                      measurement.getId() );
            }
            for (Iterator jt=scheduledTimes.iterator(); jt.hasNext();) {
                long scheduledTime = ( (Long)jt.next() ).longValue();
                try {
                    calculator.calculate(measurement, scheduledTime);
                } catch (FinderException e) {
                    throw new MeasurementScheduleException(
                        err, measurement.getId(), e);
                } catch (DataNotAvailableException e) {
                    throw new MeasurementScheduleException(
                        err, measurement.getId(), e);
                }
            }
        }
    }

    private void scheduleRawMeasurements(AppdefEntityID entId,
                                         Map measurements, int srnVer)
        throws PermissionException, MonitorCreateException,
               MonitorAgentException 
    {
        // Get the agent IP and Port from server ID
        AgentValue aconn = getAgentConnection(entId);
        SRN srn = new SRN(entId, srnVer);
            
        // Schedule RawMeasurements with a monitor
        MonitorInterface monitor =
            MonitorFactory.newInstance(aconn.getAgentType().getName());
            
        long talk2AgentStart = System.currentTimeMillis();
        
        ArrayList schedule = new ArrayList();

        for(Iterator i=measurements.entrySet().iterator(); i.hasNext(); ){
            Map.Entry ent = (Map.Entry)i.next();
            DerivedMeasurementValue dMetric;
            RawMeasurementValue[] rMetrics;

            dMetric  = (DerivedMeasurementValue)ent.getKey();
            rMetrics = (RawMeasurementValue[])ent.getValue();

            schedule.add(new ScheduleMetricInfo(dMetric, rMetrics));
        }

        ScheduleMetricInfo[] aSched = (ScheduleMetricInfo[])
            schedule.toArray(new ScheduleMetricInfo[0]);

        // Then schedule
        monitor.schedule(aconn, srn, aSched);
        logTime("schedule.talk2Agent",talk2AgentStart);
    }

    private void scheduleDerivedMeasurement(List dmvals, Set serverSchedule)
        throws SchedulerException {
        for (Iterator it = dmvals.iterator(); it.hasNext(); ) {
            DerivedMeasurementValue dmval =
                (DerivedMeasurementValue) it.next();
                
            // Only schedule if not identity expression
            // and if it hasn't already been scheduled.
            if (serverSchedule.contains( dmval.getId() )) {
    
                // if no job yet exists for this derived measurement,
                // schedule one
                String jobName =
                    CalculateDerivedMeasurementJob.getJobName(dmval);
                String schedName
                    = CalculateDerivedMeasurementJob.getScheduleName(dmval);
                Object job = getScheduler().getJobDetail
                    (jobName, CalculateDerivedMeasurementJob.SCHEDULER_GROUP);
                Object schedule = getScheduler().getTrigger
                    (schedName, CalculateDerivedMeasurementJob.SCHEDULER_GROUP);
    
                // if either the job exists without the schedule, or
                // the schedule exists without the job, remove the
                // part that exists so that we can properly schedule
                // the job
                if (null == job) {
                    if (null != schedule) {
                        // schedule exists, job doesn't
                        if ( log.isDebugEnabled() ) {
                            log.debug("Schedule " + schedName +
                                      " exists without job; removing it.");
                        }
                        getScheduler().unscheduleJob(
                            schedName,
                            CalculateDerivedMeasurementJob.SCHEDULER_GROUP);
                        schedule = null;
                    }
                } else {
                    if (null == schedule) {
                        // job exists, schedule doesn't
                        if ( log.isDebugEnabled() ) {
                            log.debug("Job " + jobName +
                                      " exists without schedule; removing it.");
                        }
                        getScheduler().deleteJob(
                            jobName,
                            CalculateDerivedMeasurementJob.SCHEDULER_GROUP);
                        job = null;
                    }
                }

                // schedule the job if it is not already scheduled
                if (null == job && null == schedule) {
                    long scheduleStart = System.currentTimeMillis();
                    getScheduler().scheduleJob
                        ( CalculateDerivedMeasurementJob.getJob(dmval),
                          CalculateDerivedMeasurementJob.getSchedule(dmval) );
                    logTime("schedule.scheduleJob",scheduleStart);
                }
            } else {
                if ( log.isDebugEnabled() ) {
                    log.debug
                        ("DM " + dmval.getId() + " is already scheduled.");
                }
            }
        }
    }

    private DerivedMeasurementValue getDMByTemplateAndInstance(Integer tid,
                                                               int instanceId)
        throws FinderException {
        DerivedMeasurement dm = getDerivedMeasurementDAO().
            findByTemplateForInstance(tid, new Integer(instanceId));
        if (dm == null) {
            throw new FinderException();
        }
        return dm.getDerivedMeasurementValue();

    }

    private RawMeasurementValue getRMByTemplateAndInstance(Integer tid,
                                                           int instanceId)
        throws FinderException {
        RawMeasurement rm = getRawMeasurementDAO().
            findByTemplateForInstance(tid, new Integer(instanceId));
        return rm.getRawMeasurementValue();
    }

    private void logTime (String method, long start) {
        if (timingLog.isDebugEnabled()) {
            long end=System.currentTimeMillis();
            timingLog.debug("MP."+method+"() - "+end+"-"+start+"="+(end-start));
        }
    }

    public static MeasurementProcessorLocal getOne() {
        try {
            return MeasurementProcessorUtil.getLocalHome().create();
        } catch(Exception e) {
            throw new SystemException(e);
        }
    }
    
    /**
     * @ejb:create-method
     */
    public void ejbCreate() {}
    public void ejbPostCreate() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void ejbRemove() {}
    public void setSessionContext(SessionContext ctx) {}
}
