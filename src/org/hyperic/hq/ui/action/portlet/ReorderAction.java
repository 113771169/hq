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

package org.hyperic.hq.ui.action.portlet;

import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.BaseAction;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.SessionUtils;

public class ReorderAction extends BaseAction {

    public static final String delim = Constants.DASHBOARD_DELIMITER;

    private static final Log log
        = LogFactory.getLog(ReorderAction.class.getName());

    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception {
        HttpSession session = request.getSession();
        WebUser user = SessionUtils.getWebUser(session);
        ServletContext ctx = getServlet().getServletContext();
        AuthzBoss boss = ContextUtils.getAuthzBoss(ctx);
        String narrowPortlets =
            request.getParameter("narrowList_true[]");
        String widePortlets =
            request.getParameter("narrowList_false[]");

        String columnKey = null;
        StringBuffer ordPortlets = new StringBuffer();
        StringTokenizer st = null;
        if (narrowPortlets != null) {
            columnKey = Constants.USER_PORTLETS_FIRST;
            st = new StringTokenizer(narrowPortlets, "&=");
        }
        else if (widePortlets != null) {
            columnKey = Constants.USER_PORTLETS_SECOND;
            st = new StringTokenizer(widePortlets, "&=");
        }
        else {
            return mapping.findForward(Constants.AJAX_URL);
        }

        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.startsWith("narrowList_")
                    || token.startsWith(".dashContent.addContent"))
                continue;
            
            ordPortlets.append(delim);
            ordPortlets.append(token);
        }
        
        // tokenize and reshuffle
        if (columnKey != null &&
            !user.getPreference(columnKey).equals(ordPortlets.toString())) {
            user.setPreference(columnKey, ordPortlets.toString());
            boss.setUserPrefs(user.getSessionId(), user.getId(), 
                              user.getPreferences());
            session.removeAttribute(Constants.USERS_SES_PORTAL);
        }
        return mapping.findForward(Constants.AJAX_URL);
    }

}
