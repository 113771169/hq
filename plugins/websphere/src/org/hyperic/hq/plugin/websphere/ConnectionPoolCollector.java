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

import javax.management.ObjectName;
import javax.management.j2ee.statistics.JDBCConnectionPoolStats;
import javax.management.j2ee.statistics.JDBCStats;

import org.hyperic.hq.product.PluginException;

import com.ibm.websphere.management.AdminClient;

public class ConnectionPoolCollector extends WebsphereCollector {

    private ObjectName name;

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

        double size=0, waiters=0, close=0, create=0;

        JDBCConnectionPoolStats[] poolStats = stats.getConnectionPools();
        for (int i=0; i<poolStats.length; i++) {
            JDBCConnectionPoolStats pool = poolStats[i];
            size += getStatCount(pool.getPoolSize());
            waiters += getStatCount(pool.getWaitingThreadCount());
            close += getStatCount(pool.getCloseCount());
            create += getStatCount(pool.getCreateCount());
        }

        setValue("poolSize", size);
        setValue("concurrentWaiters", waiters);
        setValue("numDestroys", close);
        setValue("numCreates", create);
    }
}
