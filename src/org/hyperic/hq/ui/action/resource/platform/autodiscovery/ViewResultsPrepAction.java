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
 * Created on Mar 3, 2003
 *
 */
package org.hyperic.hq.ui.action.resource.platform.autodiscovery;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hyperic.hq.appdef.shared.AIIpValue;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIServerValue;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerTypeValue;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.action.WorkflowPrepareAction;
import org.hyperic.hq.ui.exception.ParameterNotFoundException;
import org.hyperic.hq.ui.util.BizappUtils;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.util.pager.PageList;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;

/**
 *
 */
public class ViewResultsPrepAction extends WorkflowPrepareAction {

    /**
     * Retrieve this data and store it in request attributes:
     *
     *
     */
    public ActionForward workflow(ComponentContext context,
                                 ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception {
        Log log = LogFactory.getLog(ViewAutoDiscoveryAction.class.getName());

        AutoDiscoveryResultsForm aForm = (AutoDiscoveryResultsForm) form;
        
        Integer resourceId = null;
        Integer resourceType = null;
        try {
            AppdefEntityID aeid = RequestUtils.getEntityId(request);
            resourceId = aeid.getId();
            resourceType = new Integer(aeid.getType());
        } catch (ParameterNotFoundException e) {
            // don't care if no resource type (coming from dashboard)
            if (log.isDebugEnabled())
                log.debug("resourceType param not found", e);
        }
        
        aForm.setRid(resourceId);
        aForm.setResourceType(resourceType);

        AIPlatformValue aiVal = 
            (AIPlatformValue) request.getAttribute(Constants.AIPLATFORM_ATTR);
        if (aiVal == null) {
            RequestUtils.setError(request,
                "resource.platform.inventory.autoinventory.error.NoAIPlatformFound");
            return null;
        }
        
        ServletContext ctx = getServlet().getServletContext();
        Integer sessionId = RequestUtils.getSessionId(request);
        AppdefBoss appdefBoss = ContextUtils.getAppdefBoss(ctx);
        
        PlatformValue pValue =
            (PlatformValue) RequestUtils.getResource(request);
        if (pValue != null) {
            List serverSigList = new ArrayList();
        
            AppdefResourceTypeValue[] supportedSTypeFilter = null;
            supportedSTypeFilter =
                    BizappUtils.buildSupportedAIServerTypes(ctx, request, pValue);
            
            AppdefResourceTypeValue[] serverTypeFilter = 
                        BizappUtils.buildfilteredAIServerTypes(supportedSTypeFilter, 
                                            aiVal.getAIServerValues());
                                            
            aForm.setServerTypeFilterList(serverTypeFilter);
            aForm.setRid(pValue.getId());
            aForm.setType(new Integer(pValue.getEntityId().getType()));
        }
        
        aForm.setAiRid(aiVal.getId());

        aForm.buildActionOptions(request);
        
                                         
        List newModifiedServers = new PageList();
        AIServerValue[] aiServerVals = aiVal.getAIServerValues();
        CollectionUtils.addAll(newModifiedServers,aiServerVals);
        
        List filteredNewServers = BizappUtils.filterAIResourcesByStatus(newModifiedServers, 
                                        aForm.getStdStatusFilter());
        List filteredServers2 = null;
        String name = "";

        if (aForm.getServerTypeFilter() != null &&  aForm.getServerTypeFilter().intValue() != -1)
        {                
            ServerTypeValue sTypeVal = appdefBoss.findServerTypeById(sessionId.intValue(), 
                                                                     aForm.getServerTypeFilter());
            name = sTypeVal.getName();                                                                 
        }
        
        filteredServers2 = BizappUtils.filterAIResourcesByServerType(filteredNewServers, 
                                        name);
       
        
        List newIps = new ArrayList();

        AIIpValue[] aiIpVals = aiVal.getAIIpValues();
        CollectionUtils.addAll(newIps, aiIpVals);

        List filteredIps = BizappUtils.filterAIResourcesByStatus(newIps, 
                                aForm.getIpsStatusFilter());

        List sortedFilteredIps = BizappUtils.sortAIResource(filteredIps);
        request.setAttribute(Constants.AI_IPS, sortedFilteredIps);
        request.setAttribute(Constants.AI_SERVERS, filteredServers2);
                                   
        return null;
    }

}
