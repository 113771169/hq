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

package org.hyperic.hq.ha.server.mbean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.product.server.MBeanUtil;
import org.hyperic.hq.events.ext.RegisteredTriggers;

/**
 * The HAService starts all internal HQ processes.
 *
 * @jmx:mbean name="hyperic.jmx:type=Service,name=HAService"
 */
public class HAService
    implements HAServiceMBean
{
    private static Log _log = LogFactory.getLog(HAService.class);
    private static boolean isFirstPass = true;

    /**
     * @jmx:managed-operation
     */
    public void startSingleton() {
        MBeanServer server = MBeanUtil.getMBeanServer();

        //Reset in-memory triggers
        if (!isFirstPass) {
            // RegisteredTriggers already does a startup initialization
            // don't want to reset that.
            RegisteredTriggers.reset();
        } else {
            isFirstPass = false;
        }
        
        _log.info("Starting HA Services");

        startAvailCheckService(server);
        startAgentAIScanService(server);
    }

    /**
     * @jmx:managed-operation
     */
    public void stopSingleton(String gracefulShutdown) {
        // XXX: shut down services
    }

    private void startAvailCheckService(MBeanServer server) {
        try {
            invoke(server, "hyperic.jmx:service=Scheduler,name=AvailabilityCheck",
                    "startSchedule");

        } catch (Exception e) {
            _log.info("Unable to start service: " + e);
        }
    }

    private void startAgentAIScanService(MBeanServer server) {
        try {
            invoke(server, "hyperic.jmx:service=Scheduler,name=AgentAIScan",
                    "startSchedule");
        } catch (Exception e) {
            _log.info("Unable to start service: " + e);
        }
    }

    private void invoke(MBeanServer server, String mbean, String method)
            throws Exception {
        ObjectName o = new ObjectName(mbean);
        _log.info("Invoking " + o.getCanonicalName() + "." + method);
        server.invoke(o, method, new Object[] {}, new String[] {});
    }
}
