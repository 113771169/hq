/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc.
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

package org.hyperic.hq.events.server.session;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.events.MaintenanceEvent;
import org.hyperic.hq.events.shared.MaintenanceEventManagerInterface;
import org.hyperic.hq.events.shared.MaintenanceEventManagerLocal;
import org.hyperic.hq.events.shared.MaintenanceEventManagerUtil;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;

/**
 * The MaintenanceEventManager provides APIs to manage maintenance events.
 *
 * @ejb:bean name="MaintenanceEventManager"
 *      jndi-name="ejb/events/MaintenanceEventManager"
 *      local-jndi-name="LocalMaintenanceEventManager"
 *      view-type="local"
 *      type="Stateless"
 * @ejb:interface local-extends="MaintenanceEventManagerInterface, javax.ejb.EJBLocalObject"
 * @ejb:transaction type="REQUIRED"
 *
 */
public class MaintenanceEventManagerEJBImpl 
    extends SessionBase
    implements MaintenanceEventManagerInterface, SessionBean
{
	/**
     * Get the maintenance event for the group
     * 
     * @ejb:interface-method 
     */
    public MaintenanceEvent getMaintenanceEvent(AuthzSubject subject,
    											Integer groupId)
        throws PermissionException, SchedulerException 
    {
        throw new UnsupportedOperationException();
    }
        
    /**
     * Unschedule a maintenance event
     * 
     * @ejb:interface-method 
     */
    public void unschedule(AuthzSubject subject, MaintenanceEvent event)
    	throws PermissionException, SchedulerException
    {
        throw new UnsupportedOperationException();    	
    }

    /**
     * Schedule or reschedule a maintenance event
     * 
     * @ejb:interface-method 
     */    
    public MaintenanceEvent schedule(AuthzSubject subject, MaintenanceEvent event) 
    	throws PermissionException, SchedulerException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Disable or enable monitors (alerts, measurements) for the group
     * and its resources during the maintenance event.
     * 
     * @ejb:interface-method 
     */        
    public void manageMonitors(AuthzSubject admin, MaintenanceEvent event,
                             boolean activate) 
		throws PermissionException
	{
        throw new UnsupportedOperationException();    	
	}

    /**
     * Perform group permission check
     * 
     * @ejb:interface-method 
     */
    public boolean canSchedule(AuthzSubject subject, MaintenanceEvent event) 
    {
        throw new UnsupportedOperationException();    	
    }
    
    /**
     * Create a MaintenanceEvent object from a JobDetail
     * 
     * @ejb:interface-method 
     */    
    public MaintenanceEvent buildMaintenanceEvent(JobDetail jobDetail)
    { 
        throw new UnsupportedOperationException();
    }
    
    /**
     * Get local home object
     */
    public static MaintenanceEventManagerLocal getOne() {
        try {
            return MaintenanceEventManagerUtil.getLocalHome().create();
        } catch(Exception e) { 
            throw new SystemException(e);
        }
    }
    
    /**
     * @ejb:create-method
     */
    public void ejbCreate() {}
    public void ejbRemove() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void setSessionContext(SessionContext ctx) {}
}
