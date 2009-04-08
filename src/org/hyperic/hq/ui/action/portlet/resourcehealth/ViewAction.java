/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006, 2007], Hyperic, Inc.
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

package org.hyperic.hq.ui.action.portlet.resourcehealth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.bizapp.shared.EventsBoss;
import org.hyperic.hq.bizapp.shared.MeasurementBoss;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceDisplaySummary;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.UnitsConvert;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.BaseAction;
import org.hyperic.hq.ui.server.session.DashboardConfig;
import org.hyperic.hq.ui.util.CheckPermissionsUtil;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.DashboardUtils;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.hq.ui.util.SessionUtils;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.units.FormattedNumber;
import org.json.JSONObject;

/**
 * This action class is used by the Favorite Resources portlet.  It's main
 * use is to generate the JSON objects required for display into the UI.
 */
public class ViewAction extends BaseAction {

    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
       throws Exception
    {
        ServletContext ctx = getServlet().getServletContext();
        MeasurementBoss boss = ContextUtils.getMeasurementBoss(ctx);
        EventsBoss eBoss = ContextUtils.getEventsBoss(ctx);
        AuthzBoss aBoss = ContextUtils.getAuthzBoss(ctx);
        HttpSession session = request.getSession();
        WebUser user = SessionUtils.getWebUser(session);
        DashboardConfig dashConfig = DashboardUtils.findDashboard(
        		(Integer)session.getAttribute(Constants.SELECTED_DASHBOARD_ID),
        		user, aBoss);
        ConfigResponse dashPrefs = dashConfig.getConfig();
        
        String key = Constants.USERPREF_KEY_FAVORITE_RESOURCES;

        // First determine what entityIds can be viewed by this user
        // This code probably should be in the boss somewhere but
        // for now doing it here...
        List<AppdefEntityID> entityIds = CheckPermissionsUtil.filterEntityIdsByViewPermission(
        											RequestUtils.getSessionId(request).intValue(), 
        											DashboardUtils.preferencesAsEntityIds(key, dashPrefs), 
        		                                    ContextUtils.getAppdefBoss(ctx));
        
        AppdefEntityID[] arrayIds = new AppdefEntityID[entityIds.size()];
        arrayIds = (AppdefEntityID[]) entityIds.toArray(arrayIds);

        List<ResourceDisplaySummary> list;
        int sessionID = user.getSessionId().intValue();
        try{
            list = boss.findResourcesCurrentHealth(sessionID, arrayIds);
        } catch(Exception e) {
            DashboardUtils.verifyResources(key, ctx, dashPrefs, user);
            list = boss.findResourcesCurrentHealth(sessionID, arrayIds);
        }

        // Get alert counts for each resource
        int alerts[] = eBoss.getAlertCount(sessionID, arrayIds);

        // Due to the complexity of the UIBeans, we need to construct the
        // JSON objects by hand.
        JSONObject favorites = new JSONObject();

        List<JSONObject> resources = new ArrayList<JSONObject>();
        int count = 0;
        
        for (ResourceDisplaySummary bean : list) {
            JSONObject res = new JSONObject();

            res.put("resourceName", bean.getResourceName());
            res.put("resourceTypeName", bean.getResourceTypeName());
            res.put("resourceTypeId", bean.getResourceTypeId());
            res.put("resourceId", bean.getResourceId());
            res.put("performance", getFormattedValue(bean.getPerformance(),
                                                     bean.getPerformanceUnits()));
            res.put("throughput",  getFormattedValue(bean.getThroughput(),
                                                     bean.getThroughputUnits()));
            res.put("availability", getAvailString(bean.getAvailability()));
            res.put("monitorable", bean.getMonitorable());
            res.put("alerts", alerts[count]);

            resources.add(res);
            count++;
        }
        
        favorites.put("favorites", resources);

        response.getWriter().write(favorites.toString());

        return null;
    }
    
    private String getFormattedValue(Double value, String units) {
        if (value != null) {
            FormattedNumber fn = UnitsConvert.convert(value.doubleValue(),
                                                      units);
            return fn.toString();
        }
        return null;
    }

    /**
     * Get the availability string for the given metric value.  The returned
     * string should match the availabilty icon filenames.
     * @param availability The availability metric value.
     * @return The mapped string for the given availablity metric.  If the
     * given metric is not valid, unknown is returned.
     */
    private String getAvailString(Double availability) {
        if (availability != null) {
            double avail = availability.doubleValue();

            if (avail == MeasurementConstants.AVAIL_UP) {
                return "green";
            } else if (avail == MeasurementConstants.AVAIL_DOWN) {
                return "red";
            } else if (avail == MeasurementConstants.AVAIL_PAUSED) {
                return "orange";
            } else if (avail > MeasurementConstants.AVAIL_DOWN && 
                       avail < MeasurementConstants.AVAIL_UP) {
                return "yellow";
            }
        }
        return "unknown";
    }
}
