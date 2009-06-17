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

package org.hyperic.hq.ui.action.portlet.metricviewer;

import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityTypeID;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.bizapp.shared.MeasurementBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.server.session.DashboardConfig;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.DashboardUtils;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.hq.ui.util.SessionUtils;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;

public class PrepareAction extends TilesAction {

    public ActionForward execute(ComponentContext context,
                                 ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception
    {
        ServletContext ctx = getServlet().getServletContext();
        AppdefBoss appdefBoss = ContextUtils.getAppdefBoss(ctx);
        MeasurementBoss measBoss = ContextUtils.getMeasurementBoss(ctx);

        HttpSession session = request.getSession();
        WebUser user = RequestUtils.getWebUser(session);
        int sessionId = user.getSessionId().intValue();
        AuthzBoss aBoss = ContextUtils.getAuthzBoss(ctx);
        DashboardConfig dashConfig = DashboardUtils.findDashboard(
        		(Integer)session.getAttribute(Constants.SELECTED_DASHBOARD_ID),
        		user, aBoss);
        ConfigResponse dashPrefs = dashConfig.getConfig();
        PropertiesForm pForm = (PropertiesForm) form;
        PageList resources = new PageList();

        // this quarantees that the session dosen't contain any resources it
        // shouldnt
        SessionUtils.removeList(session, Constants.PENDING_RESOURCES_SES_ATTR);

        String token = pForm.getToken();

        // For multi-portlet configuration
        String numKey = PropertiesForm.NUM_TO_SHOW;
        String resKey = PropertiesForm.RESOURCES;
        String resTypeKey = PropertiesForm.RES_TYPE;
        String metricKey = PropertiesForm.METRIC;
        String descendingKey = PropertiesForm.DECSENDING;
        String titleKey = PropertiesForm.TITLE;

        if (token != null) {
            numKey += token;
            resKey += token;
            resTypeKey += token;
            metricKey += token;
            descendingKey += token;
            titleKey += token;
        }

        // We set defaults here rather than in DefaultUserPreferences.properites
        Integer numberToShow = new Integer(dashPrefs.getValue(numKey, "10"));
        String resourceType = dashPrefs.getValue(resTypeKey, "");
        String metric = dashPrefs.getValue(metricKey, "");
        String descending = dashPrefs.getValue(descendingKey, "true");
        
        pForm.setTitle(dashPrefs.getValue(titleKey, ""));

        pForm.setNumberToShow(numberToShow);
        pForm.setMetric(metric);
        pForm.setDescending(descending);
        
        List resourceList = DashboardUtils.preferencesAsEntityIds(resKey, dashPrefs);        
        AppdefEntityID[] aeids = (AppdefEntityID[])
            resourceList.toArray(new AppdefEntityID[resourceList.size()]);

        PageControl pc = RequestUtils.getPageControl(request);
        resources = appdefBoss.findByIds(sessionId, aeids, pc);
        request.setAttribute("descending", descending);
        request.setAttribute("metricViewerList", resources);

        PageList viewablePlatformTypes =
            appdefBoss.findViewablePlatformTypes(sessionId,
                                                 PageControl.PAGE_ALL);
        request.setAttribute("platformTypes", viewablePlatformTypes);
        PageList viewableServerTypes =
            appdefBoss.findViewableServerTypes(sessionId,
                                               PageControl.PAGE_ALL);
        request.setAttribute("serverTypes", viewableServerTypes);
        PageList viewableServiceTypes =
            appdefBoss.findViewableServiceTypes(sessionId,
                                                PageControl.PAGE_ALL);
        request.setAttribute("serviceTypes", viewableServiceTypes);

        AppdefResourceTypeValue typeVal = null;
        if (resourceType == null || resourceType.length() == 0) {
            if (viewablePlatformTypes.size() > 0) {
                // Take the first platform type
                typeVal =
                    (AppdefResourceTypeValue) viewablePlatformTypes.get(0);
            }
            else if (viewableServerTypes.size() > 0) {
                // Take the first server type
                typeVal =
                    (AppdefResourceTypeValue) viewableServerTypes.get(0);
            }
            else if (viewableServiceTypes.size() > 0) {
                // Take the first service type
                typeVal =
                    (AppdefResourceTypeValue) viewableServiceTypes.get(0);
            }
        }
        else {
            AppdefEntityTypeID typeId = new AppdefEntityTypeID(resourceType);
            typeVal = appdefBoss.findResourceTypeById(sessionId, typeId);
        }
        
        if (typeVal != null) {
            pForm.setResourceType(typeVal.getAppdefTypeKey());
            List metrics = measBoss.findMeasurementTemplates(sessionId,
                                                             typeVal.getName(),
                                                             PageControl.PAGE_ALL);
            request.setAttribute("metrics", metrics);
        }

        return null;
    }
}
