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

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.hyperic.lather.LatherContext;
import org.hyperic.lather.LatherRemoteException;
import org.hyperic.lather.LatherValue;

import org.hyperic.util.pager.PageList;
import org.hyperic.util.security.SecurityUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Lather Boss.
 * 
 * @ejb:bean name="LatherBoss"
 *      jndi-name="ejb/bizapp/LatherBoss"
 *      local-jndi-name="LocalLatherBoss"
 *      view-type="both"
 *      type="Stateless"
 */
public class LatherBossEJBImpl
    extends BizappSessionEJB
    implements SessionBean
{
    private final Log log = 
        LogFactory.getLog(LatherBossEJBImpl.class.getName());

    private SessionContext  sessionCtx;
    private LatherDispatcher dispatcher;

    /**
     * The main dispatch command which is called via the JBoss-lather 
     * servlet.  It is the responsibility of this routine to take the
     * method/args, and route it to the correct method.
     *
     * @param ctx     Information about the remote caller
     * @param method  Name of the method to invoke
     * @param arg     LatherValue argument object to pass to the method
     *
     * @return an instantiated subclass of the LatherValue class, 
     *         representing the result of the invoked method
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public LatherValue dispatch(LatherContext ctx, String method, 
                                LatherValue arg)
        throws LatherRemoteException
    {
        try {
            return dispatcher.dispatch(ctx, method, arg);
        } catch(RuntimeException exc){
            throw new LatherRemoteException("Runtime exception: " + 
                                            exc.getMessage());
        }
    }

    public void ejbCreate() {
        this.dispatcher = new LatherDispatcher();
    }

    public void ejbRemove() {
        if (this.dispatcher != null) {
            this.dispatcher.destroy();
        }
    }

    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void setSessionContext(SessionContext ctx) {
        this.sessionCtx = ctx;
    }
}
