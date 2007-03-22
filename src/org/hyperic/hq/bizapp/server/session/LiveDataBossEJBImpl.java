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

package org.hyperic.hq.bizapp.server.session;

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.livedata.shared.LiveDataException;
import org.hyperic.hq.livedata.shared.LiveDataManagerLocal;
import org.hyperic.hq.livedata.server.session.LiveDataManagerEJBImpl;
import org.hyperic.hq.product.PluginNotFoundException;

import javax.ejb.SessionBean;
import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.SessionContext;
import java.rmi.RemoteException;

/**
 * External API into the live data system.
 *
 * @ejb:bean name="LiveDataBoss"
 *      jndi-name="ejb/bizapp/LiveDataBoss"
 *      local-jndi-name="LocalLiveDataBoss"
 *      view-type="both"
 *      type="Stateless"
 * @ejb:transaction type="Required"
 */

public class LiveDataBossEJBImpl implements SessionBean {

    /** @ejb:create-method */
    public void ejbCreate() throws CreateException {}
    public void ejbActivate() throws EJBException, RemoteException {}
    public void ejbPassivate() throws EJBException, RemoteException {}
    public void ejbRemove() throws EJBException, RemoteException {}
    public void setSessionContext(SessionContext ctx) {}

    /**
     * Get live data for a given resource
     *
     * The data returned is the string representation of a JSONArray.  This
     * is mainly because JSONArray is not Serializable. To reconstruct the
     * JSONArray object use:
     *
     * new JSONArray(string);
     *
     * @ejb:interface-method
     */
    public String getLiveData(int sessionId, AppdefEntityID id,
                              String command)
        throws PermissionException, AgentConnectionException,
               AgentRemoteException, AgentNotFoundException,
               AppdefEntityNotFoundException, LiveDataException
    {
        LiveDataManagerLocal manager = LiveDataManagerEJBImpl.getOne();
        return manager.getData(id, command);
    }

    /**
     * Get the commands for a given resource.
     *
     * @ejb:interface-method 
     */
    public String[] getLiveDataCommands(int sessionId, AppdefEntityID id)
        throws PluginNotFoundException
    {
        LiveDataManagerLocal manager = LiveDataManagerEJBImpl.getOne();

        return manager.getCommands(id);
    }
}
