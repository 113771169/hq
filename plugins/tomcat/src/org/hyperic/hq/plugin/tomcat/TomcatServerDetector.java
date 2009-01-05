/*
 * NOTE: This copyright does *not* cover user programs that use HQ program
 * services by normal system calls through the application program interfaces
 * provided as part of the Hyperic Plug-in Development Kit or the Hyperic Client
 * Development Kit - this is merely considered normal use of the program, and
 * does *not* fall under the heading of "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc. This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify it under the terms
 * version 2 of the GNU General Public License as published by the Free Software
 * Foundation. This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.hyperic.hq.plugin.tomcat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServerResource;
import org.hyperic.hq.product.Win32ControlPlugin;
import org.hyperic.hq.product.jmx.MxServerDetector;
import org.hyperic.hq.product.jmx.MxUtil;
import org.hyperic.util.config.ConfigResponse;

import org.hyperic.sigar.win32.RegistryKey;
import org.hyperic.sigar.win32.Win32Exception;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TomcatServerDetector extends MxServerDetector {

    private static final String TOMCAT_PARAMS_KEY = "\\Parameters\\Java";
    private static final String TOMCAT_SERVICE_KEY = "SOFTWARE\\Apache Software Foundation\\Procrun 2.0";
   
    private static final String PTQL_QUERY_WIN32 = "Pid.Service.eq=%service_name%";

    private static final String CATALINA_BASE_PROP = "-Dcatalina.base=";
    
    private static final String TOMCAT_DEFAULT_URL = "service:jmx:rmi:///jndi/rmi://localhost:6969/jmxrmi";
    
    // use hard-coded ptql instead of SigarMeasurementPlugin.PTQL_CONFIG for backward
    // compatibility with pre-4.0 tomcat plugin
    private static final String PTQL_CONFIG_OPTION = "ptql";

    private Log log = LogFactory.getLog(TomcatServerDetector.class);

    private ServerResource getServerResource(String win32Service, List options)
            throws PluginException {

        if (!isWin32ServiceRunning(win32Service)) {
            log.debug(win32Service + " is not running, skipping.");
            return null;
        }
        String path;
        String[] args = (String[])options.toArray(new String[0]);
        String catalinaBase = getCatalinaBase(args);
        if (catalinaBase == null) {
            // no catalina base found
            log.error("No Catalina Base found for service " +
                      win32Service + ". Skipping..");
            return null;
        }
        else {
            File catalinaBaseDir = new File(catalinaBase);
            if (catalinaBaseDir.exists()) {
                log.debug("Successfully detected Catalina Base for service: " +
                          catalinaBase + " options=" + options);
                path = catalinaBaseDir.getAbsolutePath();
            }
            else {
                log.error("Resolved catalina base " + catalinaBase +
                          " is not a valid directory. Skipping Tomcat service " +
                          win32Service);
                return null;
            }
        }

        ServerResource server = createServerResource(path);
        // Set PTQL query
        ConfigResponse config = new ConfigResponse();
        config.setValue(MxUtil.PROP_JMX_URL, TOMCAT_DEFAULT_URL);
        for (int i=0; i<args.length; i++) {
            if (configureMxURL(config, args[i])) {
                break;
            }
        }
        config.setValue(Win32ControlPlugin.PROP_SERVICENAME, win32Service);
        config.setValue(PTQL_CONFIG_OPTION, PTQL_QUERY_WIN32);
        server.setName(server.getName() + " " + win32Service);
        server.setProductConfig(config);
        server.setMeasurementConfig();
        server.setControlConfig();
        return server;
    }

    private String[] getServicesFromRegistry() {
        RegistryKey key = null;
        String[] services = null;
        try {
            key = RegistryKey.LocalMachine.openSubKey(TOMCAT_SERVICE_KEY);
            services = key.getSubKeyNames();
        }
        catch (Win32Exception e) {
            // no tomcat services installed
        }
        finally {
            if (key != null) {
                key.close();
            }
        }
        return services;
    }

    /**
     * Helper method to discover Tomcat server paths using the Windows registry
     */
    private Map getServerRegistryMap() {
        Map serverMap = new HashMap();

        String[] services = getServicesFromRegistry();
        // return empty map if no windows services are found
        if (services == null) {
            return serverMap;
        }

        for (int i = 0; i < services.length; i++) {
            log.debug("Detected Tomcat service " + services[i]);
            List options = new ArrayList();
            RegistryKey key = null;
            try {
                key = RegistryKey.LocalMachine.openSubKey(TOMCAT_SERVICE_KEY
                        + "\\" + services[i] + TOMCAT_PARAMS_KEY);
                key.getMultiStringValue("Options", options);
            }
            catch (Win32Exception e) {
                log.error("Failed to find Java parameters for Tomcat service "
                        + services[i]);
                // skip current service
                continue;
            }
            finally {
                if (key != null) {
                    key.close();
                }
            }
            serverMap.put(services[i], options);
        }
        return serverMap;
    }

    /**
     * Auto scan
     */
    public List getServerResources(ConfigResponse platformConfig)
            throws PluginException {
        List servers = super.getServerResources(platformConfig);

        // if we are on windows, take a look at the registry for autodiscovery
        if (isWin32()) {
            Map registryMap = getServerRegistryMap();
            // convert registry options to server value types
            for (Iterator it = registryMap.keySet().iterator(); it.hasNext();) {
                String serviceName = (String) it.next();
                List options = (List) registryMap.get(serviceName);
                ServerResource server = getServerResource(serviceName, options);
                if (server != null) {
                    servers.add(server);
                }
            }
        }
        return servers;
    }

    private String getCatalinaBase(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(CATALINA_BASE_PROP)) {
                return args[i].substring(CATALINA_BASE_PROP.length());
            }
        }
        return null;
    }
}
