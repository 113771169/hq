/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2009], Hyperic, Inc.
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

package org.hyperic.hq.product.server.mbean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.bizapp.server.session.SystemAudit;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.hqu.rendit.RenditServer;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.PluginInfo;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.product.ProductPluginManager;
import org.hyperic.hq.product.server.session.ProductStartupListener;
import org.hyperic.hq.product.shared.ProductManagerLocal;
import org.hyperic.hq.product.shared.ProductManagerUtil;
import org.hyperic.util.file.FileUtil;
import org.hyperic.util.stats.ConcurrentStatsCollector;
import org.jboss.deployment.DeploymentException;
import org.jboss.deployment.DeploymentInfo;
import org.jboss.deployment.SubDeployerSupport;
import org.jboss.system.server.ServerConfig;
import org.jboss.system.server.ServerConfigLocator;

/**
 * ProductPlugin deployer.
 * We accept $PLUGIN_DIR/*.{jar,xml}
 *
 * @jmx:mbean name="hyperic.jmx:type=Service,name=ProductPluginDeployer"
 *            extends="org.jboss.deployment.SubDeployerMBean"
 */

public class ProductPluginDeployer
    extends SubDeployerSupport
    implements NotificationBroadcaster,
               NotificationListener,
               ProductPluginDeployerMBean,
               Comparator
{
    private static final String READY_MGR_NAME =
        "hyperic.jmx:service=NotReadyManager";
    private static final String SERVER_NAME =
        "jboss.system:type=Server";
    private static final String URL_SCANNER_NAME =
        "hyperic.jmx:type=DeploymentScanner,flavor=URL";
    private static final String READY_ATTR = "Ready";
    private static final String PRODUCT = "HQ";
    private static final String PLUGIN_DIR = "hq-plugins";
    private static final String HQU = "hqu";

    private static final HQApp _app = HQApp.getInstance();

    private Log _log = LogFactory.getLog(ProductPluginDeployer.class);

    private ProductPluginManager _ppm;
    private List                 _plugins = new ArrayList();
    private boolean              _isStarted = false;
    private ObjectName           _readyMgrName;
    private ObjectName           _serverName;
    private String               _pluginDir = PLUGIN_DIR;
    private String               _hquDir;

    private NotificationBroadcasterSupport _broadcaster =
        new NotificationBroadcasterSupport();

    private long _notifSequence = 0;

    private static final String PLUGIN_REGISTERED =
        NOTIF_TYPE("registered");

    private static final String PLUGIN_DEPLOYED =
        NOTIF_TYPE("deployed");

    private static final String PLUGIN_UNDEPLOYED =
        NOTIF_TYPE("undeployed");

    private static final String DEPLOYER_READY =
        NOTIF_TYPE("deployer.ready");

    private static final String DEPLOYER_CLEARED =
        NOTIF_TYPE("deployer.cleared");

    private static final String DEPLOYER_SUSPENDED =
        NOTIF_TYPE("deployer.suspended");

    private static final String[] NOTIF_TYPES = new String[] {
        DEPLOYER_READY,
        DEPLOYER_SUSPENDED,
        DEPLOYER_CLEARED,
        PLUGIN_REGISTERED,
        PLUGIN_DEPLOYED,
        PLUGIN_UNDEPLOYED,
    };

    private static String NOTIF_TYPE(String type) {
        return PRODUCT + ".plugin." + type;
    }

    public ProductPluginDeployer() {
        super();

        //XXX un-hardcode these paths.
        String ear =
            System.getProperty("jboss.server.home.dir") +
            "/deploy/hq.ear";

        //native libraries are deployed into another directory
        //which is not next to sigar.jar, so we drop this hint
        //to find it.
        System.setProperty("org.hyperic.sigar.path",
                           ear + "/sigar_bin/lib");
        
        _hquDir = ear + "/hq.war/" + HQU;

        // Initialize database
        DatabaseInitializer.init();
        
        File propFile = ProductPluginManager.PLUGIN_PROPERTIES_FILE;
        _ppm = new ProductPluginManager(propFile);
        _ppm.setRegisterTypes(true);

        if (propFile.canRead()) {
            _log.info("Loaded custom properties from: " + propFile);
        }

        try {
            _readyMgrName = new ObjectName(READY_MGR_NAME);
            _serverName   = new ObjectName(SERVER_NAME);
        } catch (MalformedObjectNameException e) {
            //notgonnahappen
            _log.error(e);
        }
    }

    /**
     * This is called when the full server startup has occurred, and you 
     * get the "Started in 30s:935ms" message.
     * 
     * We load all startup classes, then initialize the plugins.  Currently
     * this is necesssary, since startup classes need to initialize the 
     * application (creating callbacks, etc.), and plugins can't hit the
     * app until that's been done.  Unfortunately, it also means that any
     * startup listeners that depend on plugins loaded through the deployer
     * won't work.  So far that doesn't seem to be a problem, but if it 
     * ends up being one, we can split the plugin loading into more stages so
     * that everyone has access to everyone.
     * 
     * @jmx:managed-operation
     */
    public void handleNotification(Notification n, Object o) {
        loadStartupClasses();

        pluginNotify("deployer", DEPLOYER_READY);

        Collections.sort(_plugins, this);

        ProductManagerLocal pm = getProductManager();

        for (Iterator i = _plugins.iterator(); i.hasNext();) {
            String pluginName = (String)i.next();

            try {
                deployPlugin(pluginName, pm);
            } catch(DeploymentException e) {
                _log.error("Unable to deploy plugin [" + pluginName + "]", e);
            }
        }

        ProductStartupListener
            .getPluginsDeployedCaller().pluginsDeployed(_plugins);

        _plugins.clear();
        startConcurrentStatsCollector();

        //generally means we are done deploying plugins at startup.
        //but we are not "done" since a plugin can be dropped into
        //hq-plugins at anytime.
        pluginNotify("deployer", DEPLOYER_CLEARED);
        
        if (Boolean.getBoolean("hq.unittest.run")) {
            doInContainerUnitTestFrameworkSetup();            
        }
        
        setReady(true);
        
        if (n != null && n.getType().equals("org.jboss.system.server.started")) {
            SystemAudit.createUpAudit(((Number)n.getUserData()).longValue());
        }
    }
    
    private void startConcurrentStatsCollector() {
        String prop = System.getProperty("hq.unittest.run");
        System.out.println(prop);
        if (prop != null && prop.equals("true")) {
            return;
        }
        try {
            ConcurrentStatsCollector c = ConcurrentStatsCollector.getInstance();
            c.register(
                ConcurrentStatsCollector.RUNTIME_PLATFORM_AND_SERVER_MERGER);
            c.register(
                ConcurrentStatsCollector.AVAIL_MANAGER_METRICS_INSERTED);
            c.register(
                ConcurrentStatsCollector.DATA_MANAGER_INSERT_TIME);
            c.register(
                ConcurrentStatsCollector.JMS_TOPIC_PUBLISH_TIME);
            c.register(
                ConcurrentStatsCollector.JMS_QUEUE_PUBLISH_TIME);
            c.register(
                ConcurrentStatsCollector.METRIC_DATA_COMPRESS_TIME);
            c.register(
                ConcurrentStatsCollector.DB_ANALYZE_TIME);
            c.register(
                ConcurrentStatsCollector.PURGE_EVENT_LOGS_TIME);
            c.register(
                ConcurrentStatsCollector.PURGE_MEASUREMENTS_TIME);
            c.register(
                ConcurrentStatsCollector.MEASUREMENT_SCHEDULE_TIME);
            c.startCollector();
        } catch (Exception e) {
            _log.error("Could not start Concurrent Stats Collector", e);
        }
    }
    
    /**
     * Do the setup necessary to run the in-container unit test framework.
     */
    private void doInContainerUnitTestFrameworkSetup() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        String[] jars = {
            "hq.jar", "hq-test.jar", "hqee.jar", "hqee-test.jar"
        };

        for (int i = 0; i < jars.length; i++) {
            URL jar = classLoader.getResource(jars[i]);

            if (jar != null) {
                try {
                    _log.info("Preloading instance pools for Util classes in " +
                              jar);
                    preloadInstancePoolsForEJBs(jar, classLoader);
                } catch (Exception e) {
                    throw new RuntimeException("failed to preload cached local " +
                                               "homes for " + jars[i], e);
                }
            }
        }

        registerEJBDeployerClassLoader(classLoader);        
    }
    
    /**
     * Register the EJB deployer classloader, making it accessible to the 
     * in-container unit tests for looking up local references to EJBs.
     * 
     * @param deployerClassLoader The EJB deployer classloader.
     */
    private void registerEJBDeployerClassLoader(ClassLoader deployerClassLoader) {            
        // Have to use reflection - else we will get a ClassCastException
        ClassLoader sysClassLoader = ClassLoader.getSystemClassLoader();

        _log.info("Registering EJB deployer classloader for unit tests");

        try {                
            Method method = sysClassLoader.getClass()
                            .getMethod("registerEJBClassLoader", 
                                    new Class[]{ClassLoader.class});

            method.invoke(sysClassLoader, new Object[]{deployerClassLoader});                
        } catch (Exception e) {
            throw new RuntimeException("failed to register EJB classloader", e);
        }
    }
    
    /**
     * Find all EJBImpl classes in the jar file and invoke the getOne() static 
     * factory method on them, preloading the instance pool for that EJB. This 
     * is necessary for the in-container unit tests when looking up local 
     * references to EJBs.
     * 
     * @param jarFile The jar file.
     * @param deployerClassLoader The EJB deployer classloader.
     * @throws Exception
     */
    private void preloadInstancePoolsForEJBs(URL jarFile, ClassLoader deployerClassLoader) 
        throws Exception {
        
        ZipFile file = new ZipFile(new File(new URI(jarFile.toString())));
        Enumeration enumeration = file.entries();
                        
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = (ZipEntry)enumeration.nextElement();
            String name = entry.getName();
            
            if (name.endsWith("EJBImpl.class")) {
                String className = name.substring(0, name.length()-6)
                                        .replace('/', '.');
                
                _log.debug("Found class: "+className);
                                
                try {
                    Class clazz = deployerClassLoader.loadClass(className);
                    Method m = clazz.getMethod("getOne", new Class[0]);
                    _log.info("Preloading instance pool for: "+className);
                    m.invoke(clazz, new Object[0]);
                } catch (ClassNotFoundException e) {
                    // This is probably a Mock EJBImpl
                    _log.warn("Cannot preload instance pool for (probably a mock EJB Impl): "+
                               className);
                } catch (NoSuchMethodException e) {
                    // The getOne() method probably doesn't exist
                    _log.warn("No getOne() static factory method found for: "+className);
                } catch (Throwable t) {
                    _log.error("Caught Throwable preloading instance pool for: "+className, t);
                }
            }
        }        
    }

    protected boolean isDeployable(String name, URL url) {
        boolean isDeployable = super.isDeployable(name, url);
        if (isDeployable && name.endsWith(SubDeployerSupport.nativeSuffix)) {
            //e.g. JBoss will attempt to deploy a .so regardless of linux/solaris/etc
            _log.info("Skipping deployment: " + name);
            return false;
        }
        return isDeployable;
    }

    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback) 
    {
        _broadcaster.addNotificationListener(listener, filter, handback);
    }

    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException 
    {
        _broadcaster.removeNotificationListener(listener);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(NOTIF_TYPES,
                                      Notification.class.getName(),
                                      "Product Plugin Notifications"),
        };
    }

    /**
     * @jmx:managed-attribute
     */
    public ProductPluginManager getProductPluginManager() {
        return _ppm;
    }

    /**
     * @jmx:managed-attribute
     */
    public void setPluginDir(String name) {
        _pluginDir = name;
    }

    /**
     * @jmx:managed-attribute
     */
    public String getPluginDir() {
        return _pluginDir;
    }

    private Set getPluginNames(String type)
        throws PluginException 
    {
        return _ppm.getPluginManager(type).getPlugins().keySet();
    }
    
    /**
     * @jmx:managed-attribute
     * List registered plugin names of given type.
     * Intended for use via /jmx-console
     */
    public ArrayList getRegisteredPluginNames(String type)
        throws PluginException 
    {
        return new ArrayList(getPluginNames(type));
    }

    /**
     * @jmx:managed-attribute
     * List registered product plugin names.
     * Intended for use via /jmx-console
     */
    public ArrayList getRegisteredPluginNames()
        throws PluginException 
    {
        return new ArrayList(_ppm.getPlugins().keySet());
    }

    /**
     * @jmx:managed-attribute
     */
    public int getProductPluginCount()
        throws PluginException 
    {
        return _ppm.getPlugins().keySet().size();
    }

    /**
     * @jmx:managed-attribute
     */
    public int getMeasurementPluginCount()
        throws PluginException 
    {
        return getPluginNames(ProductPlugin.TYPE_MEASUREMENT).size();
    }

    /**
     * @jmx:managed-attribute
     */
    public int getControlPluginCount()
        throws PluginException 
    {
        return getPluginNames(ProductPlugin.TYPE_CONTROL).size();
    }

    /**
     * @jmx:managed-attribute
     */
    public int getAutoInventoryPluginCount()
        throws PluginException 
    {
        return getPluginNames(ProductPlugin.TYPE_AUTOINVENTORY).size();
    }

    /**
     * @jmx:managed-attribute
     */
    public int getLogTrackPluginCount()
        throws PluginException 
    {
        return getPluginNames(ProductPlugin.TYPE_LOG_TRACK).size();
    }

    /**
     * @jmx:managed-attribute
     */
    public int getConfigTrackPluginCount()
        throws PluginException 
    {
        return getPluginNames(ProductPlugin.TYPE_CONFIG_TRACK).size();
    }

    /**
     * @jmx:managed-operation 
     */
    public void setProperty(String name, String value) {
        String oldValue = _ppm.getProperty(name, "null");
        _ppm.setProperty(name, value);
        _log.info("setProperty(" + name + ", " + value + ")");
        attributeChangeNotify("setProperty", name, oldValue, value);
    }

    /**
     * @jmx:managed-operation 
     */
    public String getProperty(String name) {
       return _ppm.getProperty(name); 
    }

    /**
     * @jmx:managed-operation
     */
    public PluginInfo getPluginInfo(String name)
        throws PluginException 
    {
        PluginInfo info = _ppm.getPluginInfo(name);

        if (info == null) {
            throw new PluginException("No PluginInfo found for: " + name);
        }

        return info;
    }

    public boolean accepts(DeploymentInfo di) {
        String urlFile = di.url.getFile();

        if (!(urlFile.endsWith("jar") || (urlFile.endsWith("xml")))) {
            return false;
        }

        String urlPath = new File(urlFile).getParent();

        if (urlPath.endsWith(_pluginDir)) {
            _log.debug("accepting plugin=" + urlFile);
            return true;
        }

        return false;
    }

    public int compare(Object o1, Object o2) {
        String s1 = (String)o1;
        String s2 = (String)o2;

        int order1 = _ppm.getPluginInfo(s1).deploymentOrder;
        int order2 = _ppm.getPluginInfo(s2).deploymentOrder;

        return order1 - order2;
    }

    private void setReady(boolean ready) {
        if (getServer() != null) {
            try {            
                getServer().setAttribute(_readyMgrName,
                                         new Attribute(READY_ATTR,
                                                       ready ? Boolean.TRUE :
                                                       Boolean.FALSE));
            } catch(Exception e) {
                _log.error("Unable to declare application ready", e);
            }            
        }        
    }
    
    /**
     * @jmx:managed-attribute
     */
    public boolean isReady() {
        Boolean isReady;
        try {
            isReady = (Boolean)getServer().getAttribute(_readyMgrName, 
                                                        READY_ATTR);
        } catch (Exception e) {
            _log.error("Unable to get Application's ready state", e);
            return false;
        }
        
        return isReady.booleanValue();
    }

    private void loadStartupClasses() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream is = 
            loader.getResourceAsStream("META-INF/startup_classes.txt"); 
        List lines;
        
        try {
            lines = FileUtil.readLines(is);
        } catch(IOException e) {
            throw new SystemException(e);
        }

        ServerConfig sc = ServerConfigLocator.locate();
        _app.setRestartStorageDir(sc.getHomeDir());
        File deployDir = new File(sc.getServerHomeDir(), "deploy");
        File earDir    = new File(deployDir, "hq.ear");
        _app.setResourceDir(earDir);
        File warDir    = new File(earDir, "hq.war");
        _app.setWebAccessibleDir(warDir);
        for (Iterator i=lines.iterator(); i.hasNext(); ) {
            String className = (String)i.next();
            
            className = className.trim();
            if (className.length() == 0 || className.startsWith("#"))
                continue;
            
            _app.addStartupClass(className);
        }
        _app.runStartupClasses();
    }

    private void pluginNotify(String name, String type) {
        String action = type.substring(type.lastIndexOf(".") + 1);
        String msg = PRODUCT + " plugin " + name + " " + action;

        Notification notif = new Notification(type, this, ++_notifSequence, 
                                              msg); 

        _log.info(msg);

        _broadcaster.sendNotification(notif);
    }

    private void attributeChangeNotify(String msg, String attr,
                                       Object oldValue, Object newValue) {
        
        Notification notif =
            new AttributeChangeNotification(this,
                                            ++_notifSequence,
                                            System.currentTimeMillis(),
                                            msg,
                                            attr,
                                            newValue.getClass().getName(),
                                            oldValue,
                                            newValue);

        _broadcaster.sendNotification(notif);
    }

    private ProductManagerLocal getProductManager() {
        try {
            return ProductManagerUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private String registerPluginJar(DeploymentInfo di) {
        String pluginJar = di.url.getFile();

        if (!_ppm.isLoadablePluginName(pluginJar)) {
            return null;
        }

        try {
            //di.localCl to find resources such as etc/hq-plugin.xml
            String plugin = _ppm.registerPluginJar(pluginJar, di.localCl);

            pluginNotify(plugin, PLUGIN_REGISTERED);
            return plugin;
        } catch (Exception e) {
            _log.error("Unable to deploy plugin '" + pluginJar + "'", e);
            return null;
        }
    }

    private void deployPlugin(String plugin, ProductManagerLocal pm) 
        throws DeploymentException {

        try {
            pm.deploymentNotify(plugin);
            pluginNotify(plugin, PLUGIN_DEPLOYED);
        } catch (Exception e) {
            _log.error("Unable to deploy plugin '" + plugin + "'", e);
        }
    }

    private void addCustomPluginURL(File dir) {
        ObjectName urlScanner;

        String msg = "Adding custom plugin dir " + dir; 
        _log.info(msg);

        try {
            urlScanner = new ObjectName(URL_SCANNER_NAME);
            server.invoke(urlScanner, "addURL",
                          new Object[] { dir.toURL() },
                          new String[] { URL.class.getName() });
        } catch (Exception e) {
            _log.error(msg, e);
        }        
    }

    //check $jboss.home.url/.. and higher for hq-plugins
    private void addCustomPluginDir() {
        URL url;
        String prop = "jboss.home.url";
        String home = System.getProperty(prop);
        
        if (home == null) {
            return;
        }
        try {
            url = new URL(home);
        } catch (MalformedURLException e) {
            _log.error("Malformed " + prop + "=" + home);
            return;
        }
        
        File dir = new File(url.getFile()).getParentFile();
        while (dir != null) {
            File pluginDir = new File(dir, PLUGIN_DIR);
            if (pluginDir.exists()) {
                addCustomPluginURL(pluginDir);
                break;
            }
            dir = dir.getParentFile();
        }
    }
    
    /**
     * MBean Service start method. This method is called when JBoss is deploying
     * the MBean, unfortunately, the dependencies that this has with 
     * HighAvailService and with other components is such that the only thing
     * this method does is queue up the plugins that are ready for deployment. 
     * The actual deployment occurs when the startDeployer() method is called.
     */
    public void start() throws Exception { 
        if(_isStarted) 
            return;

        _isStarted = true;
        
        super.start();

        _ppm.init();

        try {
            //hq.ear contains sigar_bin/lib with the 
            //native sigar libraries.  we set sigar.install.home 
            //here so plugins which use sigar can find it during Sigar.load()

            String path = getClass().getClassLoader().
                getResource("sigar_bin").getFile();

            _ppm.setProperty("sigar.install.home", path);
        } catch (Exception e) {
            _log.error(e);
        }

        getServer().addNotificationListener(_serverName, this, null, null);
        
        //turn off ready filter asap at shutdown
        //this.stop() won't run until all files are undeploy()ed
        //which may take several minutes.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                setReady(false);
            }
        });
        
        addCustomPluginDir();
    }

    /**
     * @jmx:managed-operation
     */
    public void stop() {
        super.stop();
        pluginNotify("deployer", DEPLOYER_SUSPENDED);
        setReady(false);
        _plugins.clear();
    }

    private void unpackJar(URL url, File destDir, String prefix)
        throws Exception {

        JarFile jar = new JarFile(url.getFile());
        try {
            for (Enumeration e=jar.entries(); e.hasMoreElements();) {
                JarEntry entry = (JarEntry)e.nextElement();
                String name = entry.getName();

                if (name.startsWith(prefix)) {
                    name = name.substring(prefix.length());
                    if (name.length() == 0) {
                        continue;
                    }
                    File file = new File(destDir, name);
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    }
                    else {
                        FileUtil.copyStream(jar.getInputStream(entry),
                                            new FileOutputStream(file));
                    }
                }
            }
        } finally {
            jar.close();
        }
    }

    private void deployHqu(String plugin, DeploymentInfo di)
        throws Exception {

        final String prefix = HQU + "/";
        URL hqu = di.localCl.findResource(prefix);
        if (hqu == null) {
            return;
        }
        File destDir = new File(_hquDir, plugin);
        boolean exists = destDir.exists();
        _log.info("Deploying " + plugin + " " +
                  HQU + " to: " + destDir);

        unpackJar(di.url, destDir, prefix);

        RenditServer rendit = RenditServer.getInstance();
        if (rendit.getSysDir() != null) { //rendit.isReady() ?
            if (exists) {
                //update ourselves to avoid having to delete,sleep,unpack
                rendit.removePluginDir(destDir.getName());
                rendit.addPluginDir(destDir);
            } //else Rendit watcher will deploy the new plugin
        }        
    }

    public void start(DeploymentInfo di)
        throws DeploymentException 
    {
        try {
            start();
        } catch (Exception e) {
            throw new DeploymentException("Bombed", e);
        }

        _log.debug("start: " + di.url.getFile());

        //the plugin jar can be registered at any time
        String plugin = registerPluginJar(di);
        if (plugin == null) {
            return;
        }

        //plugin metadata cannot be deployed until HQ is up
        if (isReady()) {
            ProductManagerLocal pm = getProductManager();
            deployPlugin(plugin, pm);
        }
        else {
            _plugins.add(plugin);
        }

        try {
            deployHqu(plugin, di);
        } catch (Exception e) {
            throw new DeploymentException("Failed to deploy " +
                                          plugin + " " + HQU + ": " + e, e);
        }
    }

    public void stop(DeploymentInfo di)
        throws DeploymentException 
    {
        _log.debug("stop: " + di.url.getFile());

        try {
            String jar = di.url.getFile();
            _ppm.removePluginJar(jar);
            pluginNotify(new File(jar).getName(), PLUGIN_UNDEPLOYED);
        } catch (Exception e) {
            throw new DeploymentException(e);
        }
    }
}
