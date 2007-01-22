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

package org.hyperic.hq.ui.action.resource.platform.autodiscovery;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerTypeValue;
import org.hyperic.hq.autoinventory.ScanConfiguration;
import org.hyperic.hq.autoinventory.ScanMethod;
import org.hyperic.hq.autoinventory.ScanStateCore;
import org.hyperic.hq.autoinventory.ServerSignature;
import org.hyperic.hq.bizapp.shared.AIBoss;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.common.DuplicateObjectException;
import org.hyperic.hq.scheduler.ScheduleValue;
import org.hyperic.hq.scheduler.ScheduleWillNeverFireException;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.action.BaseAction;
import org.hyperic.hq.ui.exception.InvalidOptionValsFoundException;
import org.hyperic.hq.ui.util.BizappUtils;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.pager.PageControl;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Action class which saves an auto-discovery.  The autodiscovery
 * can be an new/edit auto-discovery.
 * 
 */
public class NewAutoDiscoveryAction extends BaseAction {
    
    public final static long TIMEOUT = 5000;
    public final static long INTERVAL = 500;
        
    /**
     * Create a new auto-discovery with the attributes specified in the given
     * <code>AutoDiscoveryForm</code>.
     */
    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception 
    { 
        ActionErrors errors = new ActionErrors();
        try {
            PlatformAutoDiscoveryForm newForm = (PlatformAutoDiscoveryForm) form;

            Integer platformId = newForm.getRid();
            Integer platformType = newForm.getType();
            Integer sid = newForm.getSid();
            
            HashMap forwardParams = new HashMap(3);
            forwardParams.put(Constants.RESOURCE_PARAM, platformId);
            forwardParams.put(Constants.RESOURCE_TYPE_ID_PARAM, platformType );

            // Bug #7109, save the ai schedule id for edit ai schedule. needed
            // when the user clicks on the reset button.
            if (sid != null && sid.intValue() > 0)            
                forwardParams.put(Constants.SCHEDULE_PARAM, sid );

            ActionForward forward =
                checkSubmit(request, mapping, form,
                            forwardParams, YES_RETURN_PATH);

            if (forward != null) {
                return forward;
            }         

            ServletContext ctx = getServlet().getServletContext();
            AppdefBoss appdefBoss = ContextUtils.getAppdefBoss(ctx);
            int sessionId = RequestUtils.getSessionIdInt(request);
            
            PlatformValue pValue =
                appdefBoss.findPlatformById(sessionId, platformId);
            buildAutoDiscoveryScan(request, newForm, pValue, errors); 

            RequestUtils.setConfirmation(request,
                "resource.platform.inventory.autoinventory.status.NewScan");

            return returnNew(request, mapping, forwardParams);
        } catch (AgentConnectionException e) {
            RequestUtils
                .setError(request,
                          "resource.platform.inventory.configProps.NoAgentConnection");
            return returnFailure(request, mapping);
        } catch (AgentNotFoundException e) {
            RequestUtils
                .setError(request,
                          "resource.platform.inventory.configProps.NoAgentConnection");
            return returnFailure(request, mapping);
        } catch (ScheduleWillNeverFireException e) {
            RequestUtils
                .setError(request,
                          "resource.platform.inventory.autoinventory.error.ScanAlreadyInPast");
            return returnFailure(request, mapping);
                
        } catch (InvalidOptionValsFoundException e) {
            RequestUtils.setErrors(request,errors);
            return returnFailure(request, mapping);
                
        } catch (DuplicateObjectException e1) {
            RequestUtils
                .setError(request,
                          Constants.ERR_DUP_RESOURCE_FOUND);
            return returnFailure(request, mapping);
        }
    }

    /**
     * Return an <code>ActionForward</code> if the form has been
     * cancelled or reset; otherwise return <code>null</code> so that
     * the subclass can continue to execute.
     */
    protected ActionForward checkSubmit(HttpServletRequest request,
                                        ActionMapping mapping, ActionForm form,
                                        Map params, boolean doReturnPath)
        throws Exception {
        PlatformAutoDiscoveryForm aiForm = (PlatformAutoDiscoveryForm) form;

        if (aiForm.isScheduleTypeChgSelected()) {
            return returnScheduleTypeChg(request, mapping, params, false);
        }
        
        return super.checkSubmit(request,mapping,form,params,doReturnPath);
    }

    /**
     * Return an <code>ActionForward</code> representing the
     * <em>cancel</em> form gesture.
     */
    private ActionForward returnScheduleTypeChg(HttpServletRequest request,
                                         ActionMapping mapping,
                                         Map params, boolean doReturnPath)
        throws Exception {
            return constructForward(request, mapping,
                                    Constants.SCHEDULE_TYPE_CHG_URL,
                                    params, doReturnPath);
    }

    private void buildAutoDiscoveryScan(HttpServletRequest request,
                                        PlatformAutoDiscoveryForm newForm,        
                                        PlatformValue pValue,
                                        ActionErrors errors)
        throws Exception
    {
        ServletContext ctx = getServlet().getServletContext();
        AppdefBoss boss = ContextUtils.getAppdefBoss(ctx);
        AIBoss aiboss = ContextUtils.getAIBoss(ctx);
        int sessionId = RequestUtils.getSessionIdInt(request);
        Integer scheduleId = newForm.getSid();
        
        // update the ScanConfiguration from the form obect
        List stValues =
            boss.findServerTypesByPlatformType(sessionId,
                                               pValue.getPlatformType().getId(),
                                               PageControl.PAGE_ALL);
        ServerTypeValue[] stArray = (ServerTypeValue[])
            stValues.toArray(new ServerTypeValue[0]);
        
        Map serverDetectors =
            aiboss.getServerSignatures(sessionId,
                                       newForm.getSelectedServerTypes(stArray));
        
        ServerSignature[] serverDetectorArray =
            new ServerSignature[serverDetectors.size()];
        serverDetectors.values().toArray(serverDetectorArray);
        
        String ptName = pValue.getPlatformType().getName();
        ScanMethod scanMethod
            = NewAutoDiscoveryPrepAction.getScanMethod(ptName);
        ScanConfiguration scanConfig = new ScanConfiguration();
        ConfigResponse oldCr
            = NewAutoDiscoveryPrepAction.getConfigResponse(ptName);
        ConfigResponse cr =
            BizappUtils.buildSaveConfigOptions(request, oldCr,
                                               scanMethod.getConfigSchema(),
                                               errors);
        
        // Only setup the FileScan if server types were actually selected
        if (serverDetectorArray.length > 0) {
            scanConfig.setScanMethodConfig(scanMethod, cr);
        }        
        scanConfig.setServerSignatures(serverDetectorArray);

        // probably need to add a new scan and remove the old one in
        // one transaction.                                   
        removeAISchedule(aiboss, sessionId, scheduleId);
        
        if (newForm.getIsNow()) {
            aiboss.startScan(sessionId,
                             pValue.getId().intValue(), 
                             scanConfig.getCore(),
                             null, null, /* No scanName or scanDesc for 
                                            immediate, one-time scans */
                             null);
                             
            waitForScanStart(sessionId, aiboss, pValue.getId().intValue());
        } else {
            ScheduleValue val = newForm.createSchedule();
            aiboss.startScan(sessionId, 
                             pValue.getId().intValue(), 
                             scanConfig.getCore(),
                             newForm.getName(),
                             newForm.getDescription(),
                             val);
        }
    }

    /**
     * removes a AIScheduleValue object if new
     */
    protected void removeAISchedule(AIBoss aiboss, 
                                    int sessionId, Integer sid) 
        throws Exception {}
    
    private void waitForScanStart(int sessionId, AIBoss boss, int platformId)
        throws Exception {
        Thread.sleep(2000);
    }
}
