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

package org.hyperic.hq.plugin.mqseries;

import java.io.File;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.hyperic.hq.product.AutoServerDetector;
import org.hyperic.hq.product.FileServerDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.RegistryServerDetector;
import org.hyperic.hq.product.RuntimeDiscoverer;
import org.hyperic.hq.product.ServerDetector;
import org.hyperic.hq.product.ServerResource;


import org.hyperic.sigar.win32.RegistryKey;
import org.hyperic.util.config.ConfigResponse;

public class MQSeriesDetector
    extends ServerDetector
    implements FileServerDetector,
               RegistryServerDetector,
               AutoServerDetector {

    public static final String MQ_KEY =
        "SOFTWARE\\IBM\\MQSeries\\CurrentVersion";

    private static final String[] SCAN_KEYS = {
        MQ_KEY,
    };

    private static final List SCAN_KEYS_LIST =
        Arrays.asList(SCAN_KEYS);

    public MQSeriesDetector() {
        super();
    }

    private List getServerList(String path) {
        ServerResource server = createServerResource(path);

        server.setProductConfig();
        server.setMeasurementConfig();

        List serverList = new ArrayList();
        serverList.add(server);
        return serverList;
    }

    public List getServerResources(ConfigResponse platformConfig) throws PluginException {
        if (isWin32()) {
            return null; //registry scan will pick it up.
        }

        //always installed in /opt/mqm
        String path = MQSeriesProductPlugin.DEFAULT_UNIX_INST[0];
        if (new File(path).exists()) {
            return getServerList(path);
        }
        else {
            return null;
        }
    }

    public List getServerResources(ConfigResponse platformConfig, String path) throws PluginException {
        File filePath = new File(path);

        if (!filePath.exists()) {
            throw new PluginException("Error detecting MQSeries "
                                      + "server in: " + path);
        }

        //loose bin/runmqlsr
        path =
            new File(path).getParentFile().getParentFile().getAbsolutePath();

        return getServerList(path);
    }

    public List getServerResources(ConfigResponse platformConfig, String path, RegistryKey current) 
        throws PluginException {

        //XXX check CurrentVersion\\MQServer{Release,Version}
        return getServerList(path);
    }

    public List getRegistryScanKeys() {
        return SCAN_KEYS_LIST;
    }

    public RuntimeDiscoverer getRuntimeDiscoverer() {
        return new MQSeriesRuntimeDiscoverer();
    }
}
