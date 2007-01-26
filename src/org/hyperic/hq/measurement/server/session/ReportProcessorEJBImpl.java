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
import java.util.List;
import java.util.Map;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.hq.measurement.MeasurementScheduleException;
import org.hyperic.hq.measurement.MeasurementUnscheduleException;
import org.hyperic.hq.measurement.TimingVoodoo;
import org.hyperic.hq.measurement.data.DSNList;
import org.hyperic.hq.measurement.data.MeasurementReport;
import org.hyperic.hq.measurement.data.SingleMeasurementReport;
import org.hyperic.hq.measurement.data.ValueList;
import org.hyperic.hq.measurement.ext.MonitorFactory;
import org.hyperic.hq.measurement.shared.DataManagerLocal;
import org.hyperic.hq.measurement.shared.DerivedMeasurementManagerLocal;
import org.hyperic.hq.measurement.shared.DerivedMeasurementValue;
import org.hyperic.hq.measurement.shared.MeasurementProcessorLocal;
import org.hyperic.hq.measurement.shared.SRNManagerLocal;
import org.hyperic.hq.product.MetricValue;
import org.hyperic.util.StringUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @ejb:bean name="ReportProcessor"
 *      jndi-name="ejb/measurement/ReportProcessor"
 *      local-jndi-name="LocalReportProcessor"
 *      view-type="local"
 *      type="Stateless"
 * 
 * @ejb:transaction type="Required"
 */
public class ReportProcessorEJBImpl 
    extends SessionEJB 
    implements SessionBean 
{
    private final Log log = LogFactory.getLog(ReportProcessorEJBImpl.class);

    private final DataManagerLocal _dataMan = 
        DataManagerEJBImpl.getOne();
    private final DerivedMeasurementManagerLocal _dmMan =
        DerivedMeasurementManagerEJBImpl.getOne();
    private final MeasurementProcessorLocal _measurementProc =
        MeasurementProcessorEJBImpl.getOne();
    private Integer _debugId;
    
    private void addData(DerivedMeasurement dm, int dsnId,
                         MetricValue[] dpts, long current,
                         Map oldDataPoints) 
    {
        long interval = dm.getInterval();
        boolean isPassThrough =
            dm.getFormula().equals(MeasurementConstants.TEMPL_IDENTITY);

        // Each datapoint corresponds to a set of measurement
        // values for that cycle.
        MetricValue[] passThroughs = new MetricValue[dpts.length];
            
        // Detect if we need to backfill
        long reservation =
            TimingVoodoo.roundDownTime(current, interval);
        
        // For each Datapoint/MetricValue associated
        // with the DSN...
        for (int i = 0; i < dpts.length; i++) {
            // Save data point to DB.
            long retrieval = dpts[i].getTimestamp();
            if (isPassThrough) {
                long adjust = TimingVoodoo.roundDownTime(retrieval, interval);
                
                // Debugging missing data points
                if (dm.getId().equals(_debugId)) {
                    log.info("metricDebug: ReportProcessor addData: " +
                             "metric ID " + _debugId +
                             " value=" + dpts[i].getValue() +
                             " at " + adjust);
                }
                
                // Create new Measurement data point with the adjusted time
                MetricValue modified =
                    new MetricValue(dpts[i].getValue(), adjust);
                passThroughs[i] = modified;
            } else {
                Integer rmid = new Integer(dsnId);
                
                // Add the raw measurement if it's not a pass-thru
                _dataMan.addData(rmid, dpts, true);
                
                // See if we need to add to backfill queue
                if (retrieval < reservation) {
                    // It's old data.  Let's add it to the list of
                    // data points that need to be back-filled.
                    List timestampsForRmid = (List)oldDataPoints.get(rmid);
                    if (null == timestampsForRmid) {
                        timestampsForRmid = new ArrayList();
                        oldDataPoints.put(rmid, timestampsForRmid);
                    }
                    timestampsForRmid.add(new Long(retrieval));
                }
            }
        }
        
        if (isPassThrough) {
            _dataMan.addData(dm.getId(), passThroughs, true);
        }

        // Let's check to see if there is old data, if so, we
        // can tell the scheduler to calculate missing values
        if (oldDataPoints.size() > 0) {
            try {
                _measurementProc.recalculateMeasurements(oldDataPoints);
            } catch (MeasurementScheduleException e) {
                log.error("Cannot recalculate measurement(s).", e);
            }
        }
    }

    /**
     * Method which takes data from the agent (or elsewhere) and throws
     * it into the DataManager, doing the right things with all the
     * derived measurements
     *
     * @ejb:interface-method
     */
    public void handleMeasurementReport(MeasurementReport report){
        // get current time so that we can use to check for old data
        long current = System.currentTimeMillis();

        // Keep track of which measurement ids to recalculate
        HashMap oldDataPoints = new HashMap();
        DSNList[] dsnLists = report.getClientIdList();
                
        for (int i = 0; i < dsnLists.length; i++) {
            Integer dmId = new Integer(dsnLists[i].getClientId());
            DerivedMeasurement dm = _dmMan.getMeasurement(dmId);;
            
            // Can't do much if we can't look up the derived measurement
            if (dm == null)
                continue;
            
            ValueList[] valLists = dsnLists[i].getDsns();
            for (int j = 0; j < valLists.length; j++) {
                int dsnId = valLists[j].getDsnId();
                MetricValue[] vals = valLists[j].getValues();
                addData(dm, dsnId, vals, current, oldDataPoints);
            }
        }
        
        // Check the SRNs to make sure the agent is up-to-date
        SRNManagerLocal srnManager = getSRNManager();
        Collection nonEntities = srnManager.reportAgentSRNs(report.getSRNList());
        
        if (report.getAgentToken() != null && nonEntities.size() > 0) {
            // Better tell the agent to stop reporting non-existent entities
            AppdefEntityID[] entIds = (AppdefEntityID[])
                nonEntities.toArray(new AppdefEntityID[nonEntities.size()]);
            try {
                _measurementProc.unschedule(report.getAgentToken(), entIds);
            } catch (MeasurementUnscheduleException e) {
                log.error("Cannot unschedule entities: " +
                          StringUtil.arrayToString(entIds));
            }
        }
    }

    /**
     * @ejb:interface-method
     */
    public void handleMeasurementReport(SingleMeasurementReport single){
        _dataMan.addData(single.getMeasurementId(), 
                         single.getMeasurementValue(), true);
    }

    public void ejbCreate(){
        try {
            _debugId = 
                new Integer(MonitorFactory.getProperty("agent.metricDebug"));
        } catch (Exception e) {
            _debugId = null;
        } 
    }
    
    public void ejbPostCreate(){}
    public void ejbActivate(){}
    public void ejbPassivate(){}
    public void ejbRemove(){}
    public void setSessionContext(SessionContext ctx){}
} 
