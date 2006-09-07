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

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.product.ProductPluginManager;

import org.hyperic.util.config.ConfigResponse;

import org.hyperic.sigar.win32.RegistryKey;
import org.hyperic.sigar.win32.Win32Exception;

public class WebsphereProductPlugin extends ProductPlugin {
    
    public static final String NAME = "websphere";

    public static final String SERVER_NAME = "WebSphere";
    public static final String SERVER_DESC = "Application Server";

    public static final String ADMIN_NAME  = "Admin";

    public static final String THRPOOL_NAME  = "Thread Pool";
    public static final String CONNPOOL_NAME = "Connection Pool";
    public static final String APP_NAME      = "Application";
    public static final String WEBAPP_NAME   = "Webapp";
    public static final String EJB_NAME      = "EJB";

    public static final String VERSION_AE  = "AE";
    public static final String VERSION_WS5 = "WAS50";

    public static final String SERVER_NAME_AE =
        SERVER_NAME + " " + VERSION_AE;
    public static final String ADMIN_NAME_AE =
        SERVER_NAME_AE + " " + ADMIN_NAME;

    public static final String SERVER_ADMIN_NAME =
        SERVER_NAME + " " + ADMIN_NAME;

    public static final String VERSION_40  = "4.0";
    public static final String VERSION_50  = "5.0";

    public static final String PROP_INSTALL_ROOT   = "was.install.root";
    public static final String PROP_ADMIN_HOST     = "admin.host";
    public static final String PROP_ADMIN_PORT     = "admin.port";
    public static final String PROP_ADMIN_VERS     = "admin.vers";
    public static final String PROP_USERNAME       = "username";
    public static final String PROP_PASSWORD       = "password";

    public static final String PROP_SERVER_NODE    = "server.node";
    public static final String PROP_SERVER_NAME    = "server.name";
    public static final String PROP_SERVER_PORT    = "server.port";
    
    public static final String PROP_THRPOOL_NAME   = "thrpool";
    public static final String PROP_CONNPOOL_NAME  = "connpool";
    public static final String PROP_APP_NAME       = "app";
    public static final String PROP_WEBAPP_NAME    = "webapp";
    public static final String PROP_EJB_NAME       = "ejb";

    public static final String PROP_PMI_ENABLE     = "pmi.enable";

    public static final String PROP_WEBAPP_DISPLAY_NAME =
        PROP_WEBAPP_NAME + ".display";

    public static final String PROP_WEBAPP_CONTEXT =
        PROP_WEBAPP_NAME + ".context";

    public static final String PROP_EJB_JNDI_NAME =
        PROP_EJB_NAME + ".jndi";

    //XXX rid need to configure this in agent.properties
    public static final String PROP_INSTALLPATH =
        "websphere." + ProductPlugin.PROP_INSTALLPATH;

    private static final String[] REG_VERSIONS = {
        "5.1.0.0",
        "5.0.0.0",
        "6.0.0.0",
        "6.1.0.0"
        //no point in looking for 4.0, requires agent to run w/ ibm jdk
    };

    private static Log log = LogFactory.getLog("WebsphereProductPlugin");

    private static String REG_KEY;
    private static boolean autoRT = false;
    private static boolean hasSoapConfig = false;
    private static boolean isOSGi = false;

    //if we are running with the ibm jdk we can configure
    //websphere.installpath ourselves.
    private static String getInstallPathFromJDK() {
        String vendor = System.getProperty("java.vendor");

        if (!vendor.startsWith("IBM")) {
            return null;
        }

        String javaHome = System.getProperty("java.home");
        
        File dir = new File(javaHome);

        //exists in both 4.0 and 5.0
        final String jar = "lib" + File.separator + "websphere-validation.jar";

        while ((dir = dir.getParentFile()) != null) {
            if (new File(dir, jar).exists()) {
                return dir.getAbsolutePath();
            }
        }

        return null;
    }

    /**
     * guess WebSphere installpath using the registry.
     * returns most recent version found.
     */
    public static File getRegistryInstallPath() {
        List dirs = getRegistryInstallPaths();

        if (dirs.size() == 0) {
            return null;
        }

        return (File)dirs.get(0);
    }

    public static List getRegistryInstallPaths() {
        List dirs = new ArrayList();

        try {
            RegistryKey key =
                RegistryKey.LocalMachine.openSubKey(REG_KEY);

            for (int i=0; i<REG_VERSIONS.length; i++) {
                String name = REG_VERSIONS[i];

                try {
                    RegistryKey ver = key.openSubKey(name);
                    String val = ver.getStringValue("InstallLocation");
                    if (val != null) {
                        dirs.add(new File(val.trim()));
                    }
                } catch (Win32Exception e) { }
            }
        } catch (Win32Exception e) { }
          catch (UnsatisfiedLinkError e) { } //dll is not on server-side

        return dirs;
    }

    private static String findInstallDir() {
        String dir;

        if (isWin32()) {
            File path = getRegistryInstallPath();
            if (path == null) {
                return null;
            }

            dir = path.getAbsolutePath();
        }
        else {
            //we only check for WebSphere5 since 4.0 requires
            //the agent to be run with the WebSphere JDK.
            //in which case dir already set from getInstallPathFromJDK()
            dir = WebsphereDetector5.getRunningInstallPath();
            if (dir == null) {
                dir = getInstallPathFromJDK();
            }
            if (dir == null) {
                //default WebSphere installpath(s)
                dir = "/opt/IBM/WebSphere/AppServer";
                if (!new File(dir).isDirectory()) {
                    dir = "/opt/WebSphere/AppServer";
                }
            }
            else {
                log.debug(PROP_INSTALLPATH + " found in process table");
            }
        }

        if (dir != null) {
            return dir;
        }

        log.info(PROP_INSTALLPATH +
                 " not set, defaulting to: " + dir);
        
        return dir;
    }

    public static boolean autoRT() {
        return autoRT;
    }

    public static boolean hasSoapConfig() {
        return hasSoapConfig;
    }

    public static boolean isOSGi() {
        return isOSGi;
    }

    private void addClassPath(List path,
                              String dir,
                              String[] jars) {

        if (jars == null) {
            return;
        }

        for (int j=0; j<jars.length; j++) {
            if (!jars[j].endsWith(".jar")) {
                continue;
            }

            path.add(dir + jars[j]);
        }
    }

    //jar names minus version "_6.1.0.jar"
    private void addClassPathOSGi(List path,
                                  String dir,
                                  String[] jars)
    {
        final HashMap wantedJars = new HashMap();
        for (int i=0; i<jars.length; i++) {
            wantedJars.put(jars[i], Boolean.TRUE);
        }

        String[] files =
            new File(dir).list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    int ix = name.indexOf('_');
                    if (ix == -1) {
                        return false;
                    }
                    name = name.substring(0, ix);
                    return wantedJars.get(name) != null;
                }
            });

        addClassPath(path, dir, files);
    }

    private String[] getClassPathOSGi(String installDir) {
        ArrayList path = new ArrayList();

        final String[] plugins = {
            "com.ibm.ws.runtime",
            "com.ibm.ws.security.crypto",
            "com.ibm.ws.emf"
        };
        addClassPathOSGi(path, installDir + "/plugins/", plugins);

        final String[] runtimes = {
            "com.ibm.ws.webservices.thinclient",
        };
        addClassPathOSGi(path, installDir + "/runtimes/", runtimes);

        final String[] libs = {
            "j2ee.jar",
            "bootstrap.jar",
            "urlprotocols.jar",
            "mail-impl.jar"    
        };
        addClassPath(path, installDir + "/lib/", libs);

        final String[] etc = {
            "tmx4jTransform.jar"
        };
        addClassPath(path, installDir + "/etc/", etc);

        String[] cp = new String[path.size()];
        path.toArray(cp);
        return cp;
    }

    public String[] getClassPath(ProductPluginManager manager) {
        if (isWin32()) {
            String prop = "websphere.regkey";
            REG_KEY = getProperty(prop);
            if (REG_KEY == null) {
                throw new IllegalArgumentException(prop +
                                                   " property undefined");
            }
        }

        Properties managerProps = manager.getProperties();

        if ("true".equals(managerProps.getProperty("websphere.autort"))) {
            autoRT = true;
        }

        String installDir =
            managerProps.getProperty(PROP_INSTALLPATH);

        if (installDir == null) {
            installDir = findInstallDir();
        }

        if (installDir == null) {
            return new String[0];
        }

        //required for authentication
        String defaultCorbaConfig =
            installDir + File.separator +
            "properties" + File.separator + "sas.client.props";

        String corbaConfig = 
            managerProps.getProperty("websphere.CORBA.ConfigURL",
                                     defaultCorbaConfig);

        System.setProperty("com.ibm.CORBA.ConfigURL",
                           "file:" + corbaConfig);

        String defaultSoapProps = 
            File.separator +
            "properties" + File.separator + "soap.client.props";

        String defaultSoapConfig = installDir;
        File profileDir = new File(installDir, "profiles");
        File defaultProfile = new File(profileDir, "default");

        //argh. 6.1 doesn't have soap.client.properties by default
        if (profileDir.isDirectory()) {
            String[] profiles = profileDir.list();
            if (profiles != null) {
                for (int i=0; i<profiles.length; i++) {
                    defaultProfile = new File(profileDir, profiles[i]);
                    if (new File(defaultProfile, defaultSoapProps).exists()) {
                        break;
                    }
                }
            }
        }
        String profile = defaultProfile.getName();

        if (defaultProfile.exists()) {
            //WAS 6.0+ need to use soap.client.props from a profile
            //since $installpath/etc/soap.client.props template will
            //not work w/ global security enabled.
            defaultSoapConfig +=
                File.separator + "profiles" + File.separator;

            List servers = WebsphereDetector5.getServerProcessList();
            if (servers.size() != 0) {
                WebsphereDetector.Process process =
                    (WebsphereDetector.Process)servers.get(0);

                if (process.serverRoot != null) {
                    profile = new File(process.serverRoot).getName();
                    log.debug("Using profile: " + profile);
                }
            }

            defaultSoapConfig += profile;
        }

        defaultSoapConfig += defaultSoapProps;

        String soapConfig = 
            managerProps.getProperty("websphere.SOAP.ConfigURL",
                                     defaultSoapConfig);

        File soapConfigFile = new File(soapConfig);
        hasSoapConfig = soapConfigFile.exists();
        if (hasSoapConfig) {
            log.debug("Using soap properties: " + soapConfig);    
            System.setProperty("com.ibm.SOAP.ConfigURL",
                               "file:" + soapConfig);
        }
        else {
            log.debug("Unable to find soap.client.props");
        }

        isOSGi =
            new File(installDir, "/runtimes").isDirectory();

        //required for 6.1
        File sslConfigFile =
            new File(soapConfigFile.getParent(),
                     "ssl.client.props");
        if (sslConfigFile.exists()) {
            log.debug("Using ssl properties: " + soapConfig);
            System.setProperty("com.ibm.SSL.ConfigURL",
                               "file:" + sslConfigFile);
        }
        else if (isOSGi) {
            log.debug("Unable to find ssl.client.props");
        }

        //required for 6.x
        System.setProperty(PROP_INSTALL_ROOT, installDir);

        if (isOSGi) {
            return getClassPathOSGi(installDir);
        }

        return new String[] {
            //5.0 + 5.1
            installDir + "/java/jre/lib/core.jar",
            installDir + "/java/jre/lib/ext/log.jar",
            installDir + "/lib/bootstrap.jar",
            installDir + "/lib/wssec.jar",
            installDir + "/lib/pmiclient.jar",
            installDir + "/lib/wasjmx.jar",
            installDir + "/lib/soap.jar",
            installDir + "/lib/sas.jar",
            installDir + "/lib/pmi.jar",
            installDir + "/lib/wsexception.jar",
            installDir + "/lib/jmxc.jar",
            installDir + "/lib/jmxx.jar",
            installDir + "/lib/jmxext.jar",
            installDir + "/lib/jflt.jar",
            installDir + "/lib/nls.jar",
            installDir + "/lib/ffdc.jar",
            installDir + "/lib/idl.jar",

            //5.0
            installDir + "/java/jre/lib/ext/ibmjsse.jar",
            installDir + "/java/jre/lib/ext/ibmjssefips-ob.jar",

            //5.1
            installDir + "/java/jre/lib/security.jar",
            //5.1.1
            installDir + "/java/jre/lib/ibmjsseprovider.jar",

            //4.0
            installDir + "/lib/perf.jar",
            installDir + "/lib/websphere.jar",
            installDir + "/lib/ujc.jar",
            installDir + "/lib/ns.jar",
            installDir + "/lib/iwsorb.jar",

            //4.0 AE
            installDir + "/lib/ejbcontainer.jar",
            installDir + "/lib/jts.jar",
            installDir + "/lib/csicpi.jar",
            installDir + "/lib/repository.jar",

            //both 4.0 + 5.0
            installDir + "/lib/admin.jar",
            installDir + "/lib/xerces.jar",
            installDir + "/lib/j2ee.jar",
            installDir + "/lib/ras.jar",
            installDir + "/lib/utils.jar",
            installDir + "/java/jre/lib/ext/mail.jar",

            //6.0
            installDir + "/lib/management.jar",
            installDir + "/lib/mail-impl.jar",
            installDir + "/lib/emf.jar",
            installDir + "/lib/utils.jar",
            installDir + "/lib/runtime.jar",
            installDir + "/lib/classloader.jar",
            installDir + "/lib/security.jar",
            installDir + "/lib/wasproduct.jar",
            installDir + "/java/jre/lib/ibmcertpathprovider.jar",
            installDir + "/java/jre/lib/ext/ibmjceprovider.jar",
            installDir + "/java/jre/lib/ext/ibmjcefips.jar",
            installDir + "/etc/tmx4jTransform.jar",
        };
    }

    public static boolean enablePMI(ConfigResponse config) {
        String pmiConfig = config.getValue(PROP_PMI_ENABLE);
        if (pmiConfig == null) {
            return false;
        }
        return Boolean.valueOf(pmiConfig) == Boolean.TRUE;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("websphere.installpath=" + 
                           findInstallDir());
    }
}
