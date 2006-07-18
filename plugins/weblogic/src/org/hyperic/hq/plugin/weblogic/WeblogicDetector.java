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

package org.hyperic.hq.plugin.weblogic;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.InvalidOptionException;
import org.hyperic.util.config.InvalidOptionValueException;

import org.hyperic.hq.product.AutoServerDetector;
import org.hyperic.hq.product.ServerControlPlugin;
import org.hyperic.hq.product.FileServerDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.RuntimeDiscoverer;
import org.hyperic.hq.product.ServerDetector;
import org.hyperic.hq.product.ServerResource;

import org.hyperic.hq.plugin.weblogic.jmx.WeblogicRuntimeDiscoverer;

public class WeblogicDetector
    extends ServerDetector 
    implements FileServerDetector,
               AutoServerDetector {

    private static final String PTQL_QUERY =
        "State.Name.eq=java,Args.-1.eq=weblogic.Server";

    private static final String SCRIPT_EXT = 
        isWin32() ? ".cmd" : ".sh";

    private static final String ADMIN_START =
        "startWebLogic" + SCRIPT_EXT;

    private static final String NODE_START =
        "startManagedWebLogic" + SCRIPT_EXT;

    private static final String PROP_MX_SERVER =
        "-Dweblogic.management.server";

    private Log log = LogFactory.getLog("WeblogicDetector");

    public WeblogicDetector() {
        super();
        setName(WeblogicProductPlugin.SERVER_NAME);
    }

    public RuntimeDiscoverer getRuntimeDiscoverer() {
        return new WeblogicRuntimeDiscoverer(this);
    }

    private File getPossibleControlProgram(File dir) {
        String[] scripts = dir.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.startsWith("start") &&
                    name.endsWith(SCRIPT_EXT) &&
                    !name.equals(NODE_START) &&
                    (name.indexOf("PointBase") == -1))
                {
                    return true;
                }
                return false;
            }
         });

        if ((scripts == null) || (scripts.length == 0)) {
            return null;
        }
        else {
            return new File(dir, scripts[0]);
        }
    }
    
    public List getServerResources(ConfigResponse platformConfig, String path) throws PluginException {
        return getServerList(path);
    }

    public List getServerResources(ConfigResponse platformConfig) throws PluginException {
        List servers = new ArrayList();
        List paths = getServerProcessList();

        for (int i=0; i<paths.size(); i++) {
            File dir = new File((String)paths.get(i));

            search(dir, servers);
        }

        if (isWin32()) {
            String version = getTypeInfo().getVersion();

            File root =
                WeblogicFinder.getRegistryInstallPath(version);
            if (root == null) {
                return servers;
            }

            List dirs = WeblogicFinder.getSearchDirs(root);
            for (int i=0; i<dirs.size(); i++) {
                File dir = (File)dirs.get(i);

                search(dir, servers);

                if ((i == 0) && (servers.size() > 0)) {
                    //dont bother searching samples if we found user_projects
                    break;
                }
            }            
        }

        return servers;
    }

    public List getServerList(String path)
        throws PluginException {

        File srvDir = new File(path).getParentFile().getParentFile();
        String srvName = srvDir.getName();

        this.log.debug("Looking at: " + srvDir);

        File installDir;
        File parentDir = srvDir.getParentFile();
        File configXML = new File(parentDir, "config.xml");

        if (configXML.exists()) {
            installDir = srvDir.getParentFile();
        }
        else {
            installDir = srvDir;
            //6.1
            configXML = new File(srvDir, "config.xml");
            if (!configXML.exists()) {
                //9.1
                configXML =
                    new File(parentDir.getParentFile(), "config/config.xml");
            }
        }

        WeblogicConfig cfg = new WeblogicConfig();

        try {
            cfg.read(configXML);
        } catch (IOException e) {
            this.log.warn("Failed to read " +
                          configXML + ": " +
                          e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            this.log.warn("Failed to parse " +
                          configXML + ": " +
                          e.getMessage());
            return null;
        }

        WeblogicConfig.Server srvConfig = cfg.getServer(srvName);

        if (srvConfig == null) {
            this.log.debug(srvName + " not found in " + configXML);
            srvConfig = cfg.guessAdminServer();

            if (srvConfig == null) {
                this.log.debug("no servers found in " + configXML);
                return null;
            }

            srvName = srvConfig.name;
            this.log.debug("defaulted to server " + srvName);
        }

        if (getName().indexOf(srvConfig.getVersion()) < 0) {
            this.log.debug(srvName + " is not a " + getName());
            return null;
        }

        ConfigResponse productConfig =
            new ConfigResponse(srvConfig.getProperties());

        String[] dirs = {
            srvName, //8.1
            "logs",  //9.1
        };
        
        File log = null;
        
        for (int i=0; i<dirs.length; i++) {
            log =
                new File(installDir,
                         dirs[i] + File.separator + srvName + ".log");
            if (log.exists()) {
                break;
            }
        }

        productConfig.setValue(WeblogicLogFileTrackPlugin.PROP_FILES_SERVER,
                               log.toString());

        ConfigResponse controlConfig = new ConfigResponse();
        File script = //9.1
            getPossibleControlProgram(new File(installDir, "../.."));
        if ((script == null) || !script.exists()) {
            script = getPossibleControlProgram(installDir);
        }
        if (script == null) {
            script = new File(installDir, ADMIN_START);
        }

        try {
            controlConfig.setValue(ServerControlPlugin.PROP_PROGRAM,
                                   getCanonicalPath(script.getPath()));
        } catch (InvalidOptionException e) {
            this.log.error(e.getMessage(), e);
        } catch (InvalidOptionValueException e) {
            this.log.error(e.getMessage(), e);
        }

        boolean hasCreds = false;
        //for use w/ -jar hq-product.jar or agent.properties
        Properties props = getManager().getProperties();
        String[] credProps = {
            WeblogicMetric.PROP_ADMIN_USERNAME,
            WeblogicMetric.PROP_ADMIN_PASSWORD,
            WeblogicMetric.PROP_ADMIN_URL,
        };

        for (int i=0; i<credProps.length; i++) {
            String key = credProps[i];
            //try both ${domain}.admin.url and admin.url
            String val =
                props.getProperty(srvConfig.domain + "." + key,
                                  props.getProperty(key));
            if (val != null) {
                productConfig.setValue(key, val);
                hasCreds = true;
            }
            else {
                hasCreds = false;
            }
        }

        if (this.log.isDebugEnabled()) {
            this.log.debug(getName() + " config: " + productConfig);
        }

        String installpath = getCanonicalPath(installDir.getPath());
        List servers = new ArrayList();
        ServerResource server = createServerResource(installpath);
        
        String name = getPlatformName() + " " +
            getTypeInfo().getName() +
            " " + srvConfig.domain + " " + srvConfig.name;

        server.setName(name);

        server.setProductConfig(productConfig);
        server.setControlConfig(controlConfig);
        //force user to configure by not setting measurement config
        //since we dont discover username or password.
        if (hasCreds) {
            server.setMeasurementConfig();
        }

        servers.add(server);

        return servers;
    }

    private void search(File dir, List servers)
        throws PluginException {

        if (!dir.exists()) {
            return;
        }

        log.debug(getName() + " checking path=" + dir);

        List configs = new ArrayList();
        WeblogicFinder.search(dir, configs);

        for (int i=0; i<configs.size(); i++) {
            File war = (File)configs.get(i);
            List found = getServerResources(null, war.getAbsolutePath());
            if (found != null) {
                servers.addAll(found);
            }
        }
    }

    private static List getServerProcessList() {
        ArrayList servers = new ArrayList();

        long[] pids = getPids(PTQL_QUERY);

        for (int i=0; i<pids.length; i++) {
            //nothin in the ProcArgs to indicate installpath
            String cwd = getProcCwd(pids[i]);

            //9.1-specific since config.xml no longer tells us
            //this is an admin server, check the args for this prop,
            //which if found means this is a node server, skip it.
            String[] args = getProcArgs(pids[i]);
            for (int j=0; j<args.length; j++) {
                if (args[j].startsWith(PROP_MX_SERVER)) {
                    cwd = null;
                    break;
                }
            }

            if (cwd == null) {
                continue;
            }

            servers.add(cwd);
        }

        return servers;
    }

    public static String getRunningInstallPath() {
        final String policyProp = "-Djava.security.policy=";

        String installpath = null;
        long[] pids = getPids(PTQL_QUERY);

        for (int i=0; i<pids.length; i++) {
            String[] args = getProcArgs(pids[i]);

            for (int j=0; j<args.length; j++) {
                String arg = args[j];
                if (arg.startsWith(policyProp)) {
                    arg = arg.substring(policyProp.length(), arg.length());
                    //6.1 petstore is  -Djava.security.policy==...
                    if (arg.startsWith("=")) {
                        arg = arg.substring(1, arg.length());
                    }

                    //strip lib/weblogic.policy for 6.1
                    //strip server/lib/weblogic.policy for 7.0+
                    File dir = new File(arg).getParentFile().getParentFile();
                    if (dir.getName().equals("server")) {
                        dir = dir.getParentFile();
                    }

                    installpath = dir.getAbsolutePath();
                    break;
                }
            }
        }

        return installpath;
    }

    public static void main(String[] args) {
        System.out.println("weblogic.installpath=" +
                           getRunningInstallPath());

        List servers = getServerProcessList();
        for (int i=0; i<servers.size(); i++) {
            System.out.println(servers.get(i));
        }
    }
}
