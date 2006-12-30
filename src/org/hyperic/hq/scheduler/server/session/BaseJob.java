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

package org.hyperic.hq.scheduler.server.session;

import java.util.Iterator;
import java.util.List;

import javax.ejb.CreateException;
import javax.naming.NamingException;

import org.hyperic.hq.auth.shared.SubjectNotFoundException;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerLocal;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerUtil;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.util.StringUtil;
import org.quartz.Job;
import org.quartz.JobExecutionException;

public abstract class BaseJob implements Job {

    // Configuration parametres
    public static final String PROP_ID             = "id";
    public static final String PROP_TYPE           = "type";
    public static final String PROP_SUBJECT        = "subject";
    public static final String PROP_SCHEDULED      = "scheduled";
    public static final String PROP_SCHEDULESTRING = "schedulestring";
    public static final String PROP_ORDER          = "order";
    public static final String PROP_DESCRIPTION    = "description";


    protected AuthzSubjectManagerLocal manager = null;
    
    protected AuthzSubjectManagerLocal getSubjectManager()
        throws CreateException, NamingException
    {
        if (manager == null)
            manager = AuthzSubjectManagerUtil.getLocalHome().create();
        return manager;
    }
    
    /**
     * @return the subject using the subject Id
     */
    protected AuthzSubjectValue getSubject(Integer subjectId)
        throws JobExecutionException
    {
        try {
            AuthzSubjectValue overlord = getSubjectManager().findOverlord();
            return getSubjectManager().findSubjectById(overlord, subjectId); 
            
        } catch (PermissionException e) { // we should never get permission exception
            throw new JobExecutionException(e, false);
        } catch (NamingException e) {
            throw new JobExecutionException(e, false);
        } catch (SubjectNotFoundException e) {
            throw new JobExecutionException(e, false);
        } catch (CreateException e) {
            throw new JobExecutionException(e, false);
        }
    }

    /**
     * get the job order for the group
     */
    protected int[] getOrder(String orderStr) {
        if (orderStr == null || orderStr.equals(""))
            return new int[0];
            
        List list = StringUtil.explode(orderStr, ",");
        int[] order = new int[list.size()];
        Iterator it = list.iterator();
        for (int i = 0; i < list.size(); i++)
        {
            order[i] = new Integer((String)it.next()).intValue();
        }
        return order;
    }


}
