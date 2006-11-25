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

package org.hyperic.hq.ui.action.resource.group.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;
import org.apache.struts.util.LabelValueBean;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.ControlBoss;
import org.hyperic.hq.control.shared.ControlScheduleValue;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.beans.OptionItem;
import org.hyperic.hq.ui.util.BizappUtils;
import org.hyperic.hq.ui.util.ContextUtils;
import org.hyperic.hq.ui.util.RequestUtils;

/**
 * An <code>Action</code> subclass that prepares a control action associated
 * with a group for editing.
 */
public class EditFormPrepareAction extends TilesAction {

    // ---------------------------------------------------- Public Methods

    /**
     * Find the control action and
     * populate the GroupControlForm.
     */
    public ActionForward execute(ComponentContext context,
                                 ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception {
        
        Log log = LogFactory.getLog(EditFormPrepareAction.class.getName());            
        log.trace("preparing edit group control action" );                    
        
        int sessionId = RequestUtils.getSessionId(request).intValue();
        GroupControlForm gForm = (GroupControlForm)form;        
        ServletContext ctx = getServlet().getServletContext();
        ControlBoss cBoss =
            ContextUtils.getControlBoss(getServlet().getServletContext());

        AppdefEntityID appdefId = RequestUtils.getEntityId(request);

        List actions = cBoss.getActions(sessionId, appdefId);
        actions = OptionItem.createOptionsList(actions);
        gForm.setControlActions(actions);
        gForm.setNumControlActions(new Integer(actions.size()));

        Integer trigger 
            = RequestUtils.getIntParameter(request, Constants.CONTROL_BATCH_ID_PARAM);

        ControlScheduleValue job = cBoss.getControlJob(sessionId, trigger);
        // populate control actions
        gForm.populateFromSchedule(job.getScheduleValue(), request.getLocale());
        gForm.setControlAction(job.getAction());
        gForm.setDescription(job.getScheduleValue().getDescription());

        // get the resource ids associated with this group,
        // create an options list, and associate it with the form
        AppdefBoss aBoss = ContextUtils.getAppdefBoss(ctx);
        AppdefGroupValue group = aBoss.findGroup(sessionId, appdefId);
        List groupMembers 
            = BizappUtils.buildGroupResources(aBoss, sessionId, group);
        Iterator i = groupMembers.iterator();
        ArrayList groupOptions = new ArrayList();
        HashMap mapOfGroupMembers = new HashMap();
        while (i.hasNext()) {
            AppdefResourceValue arv = (AppdefResourceValue)i.next();
            LabelValueBean lvb 
                = new LabelValueBean(arv.getName(), arv.getId().toString());
            groupOptions.add(lvb);

            // create a set for ordering, later.
            mapOfGroupMembers.put(arv.getId().toString(), arv.getName());
        }           
        gForm.setResourceOrderingOptions(groupOptions);

        // if this is an ordered control action
        String resourceOrdering = job.getJobOrderData();
        if (resourceOrdering != null 
                && !"".equals(resourceOrdering.trim())) {
            gForm.setInParallel(GroupControlForm.IN_ORDER);

            groupOptions = new ArrayList();
            // comes back in the form of a string list
            // of group members for ordering 10001,10002,10004. barf.
            StringTokenizer tok = new StringTokenizer(resourceOrdering, ",");
            String gmemberId;
            while (tok.hasMoreTokens()) {
                gmemberId = tok.nextToken();
                if (!mapOfGroupMembers.containsKey(gmemberId)) {
                    // weird, in ordering, but not in group
                    log.warn("Group control ordering contains id"
                        + " of non group member.");
                } else {
                    LabelValueBean lvb 
                        = new LabelValueBean((String)mapOfGroupMembers.get(gmemberId), gmemberId);
                    groupOptions.add(lvb);
                    mapOfGroupMembers.remove(gmemberId);
                }
            }

            // there are members of the group, that were not contained 
            // in the ordering for some reason
            if (mapOfGroupMembers.size() != 0) {
                Set memberIds = mapOfGroupMembers.keySet();
                Iterator idIterator = memberIds.iterator();

                while (idIterator.hasNext()) {
                    gmemberId = (String)idIterator.next();
                    LabelValueBean lvb 
                        = new LabelValueBean((String)mapOfGroupMembers.get(gmemberId), gmemberId);
                    groupOptions.add(lvb);
                }
            }

            gForm.setResourceOrderingOptions(groupOptions);
        } /* if ( resourceOrdering != null) */

        return null;

    } 
}
