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

package org.hyperic.hq.plugin.websphere;

import java.util.Iterator;
import java.util.Set;

import javax.management.ObjectName;
import javax.management.j2ee.statistics.JDBCStats;
import javax.management.j2ee.statistics.Stats;

import org.hyperic.hq.product.PluginException;

import com.ibm.websphere.management.AdminClient;

public class ConnectionPoolCollector extends WebsphereCollector {

    private static final String[][] ATTRS = {
        //basic (default) PMI level
        { "CreateCount", "numCreates" },
        { "CloseCount", "numDestroys" },
        { "PoolSize", "poolSize" },
        { "FreePoolSize" }, //XXX
        { "WaitingThreadCount", "concurrentWaiters" },
        //non-default PMI level
        { "AllocateCount", "numAllocates" },
        { "ReturnCount", "numReturns" },
        { "PrepStmtCacheDiscardCount", "prepStmtCacheDiscards" },
        { "FaultCount", "faults" }
    };

    protected ObjectName resolve(AdminClient server, ObjectName name)
            throws PluginException {

        Set beans;
        try {
            beans = server.queryNames(name, null);
        } catch (Exception e) {
            String msg =
                "resolve(" + name + "): " + e.getMessage();
            throw new PluginException(msg, e);
        }

        if (beans.size() == 0) {
            String msg =
                name + " query returned " +
                beans.size() + " results";
            throw new PluginException(msg);
        } else if (beans.size() == 1) {
        	return (ObjectName) beans.iterator().next();
        } else {
        	for (Iterator it=beans.iterator(); it.hasNext();) {
        		//XXX seen in samples, two beans where all attributes are equal
        		//with the exception of mbeanIdentifier
        		ObjectName fullName = (ObjectName) it.next();
        		String id = fullName.getKeyProperty("mbeanIdentifier");
        		if (id != null) {
        			if (id.indexOf(getServerName()) != -1) {
        				return fullName;
        			}
        		}
        	}
        }

        String msg =
        	name + " query returned " +
        	beans.size() + " results";
        throw new PluginException(msg);
    }

    protected void init(AdminClient mServer) throws PluginException {
        super.init(mServer);

        this.name =
            newObjectNamePattern("type=JDBCProvider," +
                                 "j2eeType=JDBCResource," +
                                 "name=" + getModuleName() + "," +
                                 getProcessAttributes());

        this.name = resolve(mServer, this.name);
    }

    public void collect() {
        AdminClient mServer = getMBeanServer();
        if (mServer == null) {
            return;
        }
        JDBCStats stats = (JDBCStats)getStats(mServer, this.name);
        if (stats == null) {
            return;
        }
        setAvailability(true);
        Stats[] pools = stats.getConnectionPools();
        for (int i=0; i<pools.length; i++) {
            collectStatCount(pools[i], ATTRS);
        }
    }
}
