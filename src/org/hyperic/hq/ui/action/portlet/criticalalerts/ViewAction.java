/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2009], Hyperic, Inc.
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

package org.hyperic.hq.ui.action.portlet.criticalalerts;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.bizapp.shared.EventsBoss;
import org.hyperic.hq.escalation.server.session.Escalatable;
import org.hyperic.hq.escalation.server.session.Escalation;
import org.hyperic.hq.events.AlertDefinitionInterface;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.BaseAction;
import org.hyperic.hq.ui.exception.ParameterNotFoundException;
import org.hyperic.hq.ui.server.session.DashboardConfig;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.DashboardUtils;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.util.config.ConfigResponse;
import org.json.JSONObject;

/**
 * This action class is used by the Critical Alerts portlet.  It's main
 * use is to generate the JSON objects required for display into the UI.
 */
public class ViewAction extends BaseAction {

    static final String RESOURCES_KEY = Constants.USERPREF_KEY_CRITICAL_ALERTS_RESOURCES;

    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception
    {
        ServletContext ctx = getServlet().getServletContext();
        AuthzBoss authzBoss = ContextUtils.getAuthzBoss(ctx);
        EventsBoss eventBoss = ContextUtils.getEventsBoss(ctx);
        HttpSession session = request.getSession();
        WebUser user = RequestUtils.getWebUser(session);
        DashboardConfig dashConfig = DashboardUtils.findDashboard(
        		Integer.valueOf(String.valueOf(session.getAttribute(Constants.SELECTED_DASHBOARD_ID))),
        		user, authzBoss);
        ConfigResponse dashPrefs = dashConfig.getConfig();
        
        
        String token;

        try {
            token = RequestUtils.getStringParameter(request, "token");
        } catch (ParameterNotFoundException e) {
            token = null;
        }

        // For multi-portlet configurations
        String resKey = ViewAction.RESOURCES_KEY;
        String countKey = PropertiesForm.ALERT_NUMBER;
        String priorityKey = PropertiesForm.PRIORITY;
        String timeKey = PropertiesForm.PAST;
        String selOrAllKey = PropertiesForm.SELECTED_OR_ALL;
        String titleKey = PropertiesForm.TITLE;
        
        if (token != null) {
            resKey += token;
            countKey += token;
            priorityKey += token;
            timeKey += token;
            selOrAllKey += token;
            titleKey += token;
        }

        List entityIds = DashboardUtils.preferencesAsEntityIds(resKey, dashPrefs);
        AppdefEntityID[] arrayIds =
            (AppdefEntityID[])entityIds.toArray(new AppdefEntityID[0]);

        int count = Integer.parseInt(dashPrefs.getValue(countKey));
        int priority = Integer.parseInt(dashPrefs.getValue(priorityKey).trim());
        long timeRange = Long.parseLong(dashPrefs.getValue(timeKey));
        boolean all = "all".equals(dashPrefs.getValue(selOrAllKey));

        int sessionID = user.getSessionId().intValue();

        if (all) {
            arrayIds = null;
        }

        List criticalAlerts = eventBoss.findRecentAlerts(sessionID, count, 
                                                         priority, timeRange, 
                                                         arrayIds); 

        JSONObject alerts = new JSONObject();
        List a = new ArrayList();

        MessageResources res = getResources(request);
        String formatString =
            res.getMessage(Constants.UNIT_FORMAT_PREFIX_KEY + "epoch-millis");

        AuthzSubject subject = authzBoss.getCurrentSubject(sessionID); 
        SimpleDateFormat df = new SimpleDateFormat(formatString);
        for (Iterator i = criticalAlerts.iterator(); i.hasNext(); ) {
            Escalatable alert = (Escalatable) i.next();
            AlertDefinitionInterface def;
            AppdefEntityValue aVal;
            AppdefEntityID eid;
            Escalation escalation;
            long maxPauseTime = 0;
            
            String date = 
                df.format(new Date(alert.getAlertInfo().getTimestamp()));
            def = alert.getDefinition().getDefinitionInfo();
            escalation = alert.getDefinition().getEscalation();
            if (escalation != null && escalation.isPauseAllowed()) {
                maxPauseTime = escalation.getMaxPauseTime();
            }
            eid = new AppdefEntityID(def.getResource());
            aVal = new AppdefEntityValue(eid, subject);
            
            JSONObject jAlert = new JSONObject();
            jAlert.put("alertId", alert.getId());
            jAlert.put("appdefKey", eid.getAppdefKey());
            jAlert.put("resourceName", aVal.getName());
            jAlert.put("alertDefName", def.getName()); 
            jAlert.put("cTime", date);
            jAlert.put("fixed", alert.getAlertInfo().isFixed()); 
            jAlert.put("acknowledgeable", alert.isAcknowledgeable());
            jAlert.put("alertType",
                       alert.getDefinition().getAlertType().getCode());
            jAlert.put("maxPauseTime", maxPauseTime);

            a.add(jAlert);
        }

        alerts.put("criticalAlerts", a);
        if (token != null) {
            alerts.put("token", token);
        } else {
            alerts.put("token", JSONObject.NULL);
        }

        alerts.put("title", dashPrefs.getValue(titleKey, ""));
        
        response.getWriter().write(alerts.toString());

        return null;
    }
}
