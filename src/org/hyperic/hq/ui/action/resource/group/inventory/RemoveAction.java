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

/*
 * Created on Feb 14, 2003
 *
 */
package org.hyperic.hq.ui.action.resource.group.inventory;

import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefGroupNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.action.BaseAction;
import org.hyperic.hq.ui.exception.ParameterNotFoundException;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.RequestUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class RemoveAction extends BaseAction {

    Log log = LogFactory.getLog(RemoveAction.class.getName());

    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception
    {
        RemoveGroupResourcesForm nwForm = (RemoveGroupResourcesForm) form;
        HashMap forwardParams = new HashMap(2);
        forwardParams.put(Constants.RESOURCE_PARAM, nwForm.getRid());
        forwardParams.put(Constants.RESOURCE_TYPE_ID_PARAM, nwForm.getType());
        
        try {
            
            String[] resources = nwForm.getResources();
            
            if (resources == null || resources.length == 0) {
                return returnSuccess(request, mapping,forwardParams);
            }
            
            Integer groupId = RequestUtils.getResourceId(request);
            Integer sessionId =  RequestUtils.getSessionId(request);
            
            //get the spiderSubjectValue of the user to be deleated.
            ServletContext ctx = getServlet().getServletContext();            
            AuthzBoss authzBoss = ContextUtils.getAuthzBoss(ctx);            
            
            log.trace("removing resource");                                                      
            AppdefBoss boss = ContextUtils.getAppdefBoss(ctx);
            
            AppdefGroupValue group = boss.findGroup(sessionId.intValue(), 
                            groupId);
            
            for (int i = 0; i < resources.length; i++) {
                AppdefEntityID entity = new AppdefEntityID(resources[i]);
                group.removeAppdefEntity(entity);                
            }

            boss.saveGroup(sessionId.intValue(), group);
            
            return returnSuccess(request, mapping,forwardParams);
                
        } catch (ParameterNotFoundException e2) {
            RequestUtils
                .setError(request,
                          Constants.ERR_RESOURCE_ID_FOUND);
            return returnFailure(request, mapping, forwardParams);
        } catch (AppdefGroupNotFoundException e) {
            RequestUtils
                .setError(request,
                          "resource.common.inventory.error.ResourceNotFound");
                     
            return returnFailure(request, mapping, forwardParams);
        } 
    }
}
