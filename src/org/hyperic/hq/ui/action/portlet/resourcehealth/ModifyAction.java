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

package org.hyperic.hq.ui.action.portlet.resourcehealth;

import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.BaseAction;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.DashboardUtils;
import org.hyperic.util.StringUtil;

import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * An <code>Action</code> that loads the <code>Portal</code>
 * identified by the <code>PORTAL_PARAM<    /code> request parameter (or
 * the default portal, if the parameter is not specified) into the
 * <code>PORTAL_KEY</code> request attribute.
 */
public class ModifyAction extends BaseAction {
    
    /**
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     *
     * @exception Exception if the application business logic throws
     *  an exception
     */
    public ActionForward execute(ActionMapping mapping,
                            ActionForm form,
                            HttpServletRequest request,
                            HttpServletResponse response)
        throws Exception {

        ServletContext ctx = getServlet().getServletContext();
        AuthzBoss boss = ContextUtils.getAuthzBoss(ctx);
        PropertiesForm pForm = (PropertiesForm) form;
        HttpSession session = request.getSession();
        WebUser user = (WebUser)
            session.getAttribute(Constants.WEBUSER_SES_ATTR);

        String forwardStr = "success";

        if(pForm.isRemoveClicked()){
            DashboardUtils.removeResources(pForm.getIds(), 
                                           Constants.USERPREF_KEY_FAVORITE_RESOURCES,
                                           user);
            forwardStr = "review";
        }

        ActionForward forward = checkSubmit(request, mapping, form);

        if (forward != null) {
            return forward;
        }

        if(!pForm.isDisplayOnDash())
            DashboardUtils.removePortlet( user, pForm.getPortletName());

        String availability = Boolean.toString( pForm.isAvailability() );
        String throughput =  Boolean.toString( pForm.isThroughput() );
        String performance = Boolean.toString( pForm.isPerformance() );
        String utilization = Boolean.toString( pForm.isUtilization() );

        user.setPreference(".dashContent.resourcehealth.availability",
                           availability);
        user.setPreference(".dashContent.resourcehealth.throughput",
                           throughput);
        user.setPreference(".dashContent.resourcehealth.performance",
                           performance);
        user.setPreference(".dashContent.resourcehealth.utilization",
                           utilization);

        // Set the order of resources
        String order = StringUtil.replace(pForm.getOrder(), "%3A", ":");
        StringTokenizer orderTK = new StringTokenizer(order, "=&");
        ArrayList resources = new ArrayList();
        while (orderTK.hasMoreTokens()) {
            orderTK.nextToken();                    // left-hand
            resources.add(orderTK.nextToken());     // appdef key
        }
        user.setPreference(Constants.USERPREF_KEY_FAVORITE_RESOURCES,
                           StringUtil.listToString(resources, Constants
                                                   .DASHBOARD_DELIMITER));
        
        LogFactory.getLog("user.preferences").trace("Invoking setUserPrefs"+
            " in resourcehealth/ModifyAction " +
            " for " + user.getId() + " at "+System.currentTimeMillis() +
            " user.prefs = " + user.getPreferences());
        boss.setUserPrefs(user.getSessionId(), user.getId(),
                          user.getPreferences() );

        session.removeAttribute(Constants.USERS_SES_PORTAL);
        return mapping.findForward(forwardStr);
        
    }
}
