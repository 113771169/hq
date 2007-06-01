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

package org.hyperic.hq.ui.action.resource.common.monitor.visibility;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.bizapp.shared.MeasurementBoss;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceMetricDisplaySummary;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.UnitsConvert;
import org.hyperic.hq.measurement.shared.MeasurementTemplateValue;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.WorkflowPrepareAction;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.MonitorUtils;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.hq.ui.util.SessionUtils;
import org.hyperic.util.config.InvalidOptionException;
import org.hyperic.util.units.FormattedNumber;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;

public class CompareMetricsFormPrepareAction extends WorkflowPrepareAction {
    protected static Log log =
        LogFactory.getLog(CompareMetricsFormPrepareAction.class.getName());
        
    /* (non-Javadoc)
     * @see org.hyperic.hq.ui.action.WorkflowPrepareAction#workflow(org.apache.struts.tiles.ComponentContext, org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public ActionForward workflow(
                ComponentContext context,
                ActionMapping mapping,
                ActionForm form,
                HttpServletRequest request,
                HttpServletResponse response)
                throws Exception {
        CompareMetricsForm cform = (CompareMetricsForm) form;
        int sessionId = RequestUtils.getSessionId(request).intValue();
        WebUser user = SessionUtils.getWebUser(request.getSession());
        Map range = user.getMetricRangePreference();

        long begin = ((Long) range.get(MonitorUtils.BEGIN)).longValue();
        long end = ((Long) range.get(MonitorUtils.END)).longValue();

        // assemble the ids, making sure none are duplicated
        Integer[] raw = cform.getR();
        ArrayList cooked = new ArrayList();
        HashMap idx = new HashMap();
        for (int i=0; i<raw.length; i++) {
            Integer val = raw[i];
            if (idx.get(val) == null) {
                cooked.add(val);
                idx.put(val, val);
            }
        }
        Integer[] rids = (Integer[]) cooked.toArray(new Integer[0]);

        AppdefEntityID[] entIds = new AppdefEntityID[rids.length];        
        for (int i = 0; i < rids.length; i++) {
            entIds[i] = new AppdefEntityID(cform.getAppdefType().intValue(), rids[i].intValue());
            if (log.isTraceEnabled())
                log.trace("will compare metrics for " + entIds[i]);
        }
        // get the metrics
        try {
            long start = 0;
            long finish = 0;
            MeasurementBoss boss = ContextUtils.getMeasurementBoss(getServlet().getServletContext());
            if (log.isDebugEnabled())
                start = System.currentTimeMillis();
            Map metrics = boss.findResourceMetricSummary(sessionId, entIds, begin, end);
            if (log.isDebugEnabled()) {
                finish = System.currentTimeMillis();
                long elapsed = finish - start;
                log.debug("Elapsed time: " + elapsed + " ms");            
            }
            formatComparisonMetrics(metrics, request.getLocale());
            cform.setMetrics(mapCategorizedMetrics(metrics));
        }
        catch (Exception e) {
            log.debug("findResourceMetricSummary(...) failed: ", e);
            throw e;
        }

        MetricRange mr = new MetricRange(new Long(begin), new Long(end));
        prepareForm(request, cform, mr);

        return null;
    }

    protected void prepareForm(HttpServletRequest request,
                               MetricsControlForm form,
                               MetricRange range)
        throws InvalidOptionException {
        WebUser user = SessionUtils.getWebUser(request.getSession());

        // set metric range defaults
        Map pref = user.getMetricRangePreference(true);
        form.setReadOnly((Boolean) pref.get(MonitorUtils.RO));
        form.setRn((Integer) pref.get(MonitorUtils.LASTN));
        form.setRu((Integer) pref.get(MonitorUtils.UNIT));

        Long begin, end;
        
        if (range != null) {
            begin = range.getBegin();
            end = range.getEnd();
        }
        else {
            begin = (Long) pref.get(MonitorUtils.BEGIN);
            end = (Long) pref.get(MonitorUtils.END);
        }
        
        form.setRb(begin);
        form.setRe(end);
        
        form.populateStartDate(new Date(begin.longValue()),
                               request.getLocale());
        form.populateEndDate(new Date(end.longValue()), request.getLocale());
        
        Boolean readOnly = (Boolean) pref.get(MonitorUtils.RO);
        if (readOnly.booleanValue()) {
            form.setA(MetricDisplayRangeForm.ACTION_DATE_RANGE);
        }
        else {
            form.setA(MetricDisplayRangeForm.ACTION_LASTN);
        }
    }

    private static Map mapCategorizedMetrics(Map metrics) {
        Map returnMap = new LinkedHashMap();
        for (int i = 0; i < MeasurementConstants.VALID_CATEGORIES.length; i++) {
            Map categoryMetrics = getMetricsByCategory(metrics, MeasurementConstants.VALID_CATEGORIES[i]);
            if (categoryMetrics.size() > 0)
                returnMap.put(MeasurementConstants.VALID_CATEGORIES[i], categoryMetrics);            
        }
        return returnMap;
    }

    // returns a "sub map" with entries that match the category
    private static Map getMetricsByCategory(Map metrics, String category) {
        Map returnMap = new HashMap();
        for (Iterator iter = metrics.entrySet().iterator();    iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            MeasurementTemplateValue mt = (MeasurementTemplateValue) entry.getKey();  
            if (mt.getCategory().getName().equals(category)) {
                List metricList = (List)entry.getValue();
                returnMap.put(mt,metricList);
            }
        } 
        return sortMetricMap(returnMap);               
    }

    private static void formatComparisonMetrics(Map metrics, Locale userLocale) {
        for (Iterator categoryIter = metrics.entrySet().iterator(); categoryIter.hasNext();) {
            Map.Entry entry = (Map.Entry) categoryIter.next();
            MeasurementTemplateValue mt = (MeasurementTemplateValue) entry.getKey();  
            
            List metricList = (List)entry.getValue();
            //List metricList = (List)metrics.get(mt);
            if (metricList == null) {
                // apparently, there may be meaurement templates populated but none of
                // the included resources are config'd for it, so instead of being a zero
                // length list, it's null
                if (log.isTraceEnabled())
                    log.trace(mt.getAlias() + " had no resources configured for it in the included map of metrics");   
                continue;
            }
            for (Iterator iter = metricList.iterator(); iter.hasNext();) {
                ResourceMetricDisplaySummary mds = (ResourceMetricDisplaySummary) iter.next();
                // the formatting subsystem doesn't interpret
                // units set to empty strings as "no units" so
                // we'll explicity set it so
                if (mds.getUnits().length() < 1) {
                    mds.setUnits(MeasurementConstants.UNITS_NONE);
                }
                FormattedNumber[] fs = UnitsConvert.convertSame(mds.getMetricValueDoubles(), mds.getUnits(), userLocale);
                String[] keys = mds.getMetricKeys();
                for (int i = 0; i < keys.length; i++) {         
                    mds.getMetric(keys[i]).setValueFmt(fs[i]);
                }
            }
        }
    }

    /**
     * Alpha sort on the MeasurementTemplateValue names and stuff them into a 
     * TreeMap they're always ordered consistently
     */
    private static Map sortMetricMap(Map metrics) {
        Map returnMap = new TreeMap();
        for (Iterator mtvIter = metrics.entrySet().iterator(); mtvIter.hasNext();) {
            Map.Entry entry = (Map.Entry) mtvIter.next();
            MeasurementTemplateValue mt = (MeasurementTemplateValue) entry.getKey();
            ComparableMeasurementTemplateValue cmtv = new ComparableMeasurementTemplateValue(mt);
            List metricList = (List)entry.getValue();
            returnMap.put(cmtv, metricList);
        }
        return returnMap;
    }

}
