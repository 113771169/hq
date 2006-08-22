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

package org.hyperic.hq.product;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import org.hyperic.hq.common.shared.ProductProperties;
import org.hyperic.hq.product.pluginxml.PluginData;
import org.hyperic.sigar.OperatingSystem;
import org.hyperic.util.PluginLoader;
import org.hyperic.util.PluginLoaderException;
import org.hyperic.util.StringUtil;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
 * This class is a manager for ProductPlugin implementations
 * and is also a manager of plugin managers.
 */
public class ProductPluginManager extends PluginManager {
    public static final String PROP_PDK_DIR = "pdk.dir";

    //this is really verbose and not very helpful
    static final boolean DEBUG_LIFECYCLE = false;

    private static final String PLUGIN_STUB_NAME =
        "org.hyperic.hq.product.ProductPluginXML";
    
    private static final String PLUGIN_STUB =
        "org/hyperic/hq/product/ProductPluginXML.stub";
    
    private static final String[] CORE_PLUGINS = {
        "system",      //absolute must-have
        "netservices", //many plugins depend on this
        "sqlquery",    //for sql: metrics
    };
    
    private boolean registerTypes = false;
    private boolean unpackNestedJars = true;
    private HashMap managers = new HashMap();
    private HashMap types = new HashMap();
    private HashMap includePlugins = null;
    private HashMap excludePlugins = null;
    private Log log = null;
    private byte[] pluginStub = null;
    private int pluginStubLength = 0;
    
    public static final int DEPLOYMENT_ORDER_LAST = 
        TypeInfo.TYPE_SERVICE + 1;

    private static final File HQ_DIR =
        new File(System.getProperty("user.home"), ".hq");

    public static final File PLUGIN_PROPERTIES_FILE =
        new File(HQ_DIR, "plugin.properties");

    public static final String PROPERTY_PREFIX =
        "hq.plugins.";

    private MeasurementPluginManager mpm;
    private ControlPluginManager cpm;
    private AutoinventoryPluginManager apm;
    private RtPluginManager rpm;
    private LogTrackPluginManager ltpm;
    private ConfigTrackPluginManager ctpm;

    public ProductPluginManager() {
        this(System.getProperties());
    }

    /**
     * If true creates a mapping of the ProductPlugin TypeInfos,
     * only needed on the server side.
     */
    public void setRegisterTypes(boolean registerTypes) {
        this.registerTypes = registerTypes;
    }

    public boolean getRegisterTypes() {
        return this.registerTypes;
    }

    public void setUnpackNestedJars(boolean unpackNestedJars) {
        this.unpackNestedJars = unpackNestedJars;
    }

    public static String getPropertyKey(String plugin, String key) {
        return PROPERTY_PREFIX + plugin + "." + key;
    }

    //ignore failures.  if you want to check for errors, do it yourself.
    private static Properties getFileProperties(File file) {
        Properties props = new Properties();
        FileInputStream is = null;

        try {
            is = new FileInputStream(file);
            props.load(is);
        } catch (IOException e) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ie) { }
            }
        }

        return props;
    }

    public ProductPluginManager(File file) {
        this(getFileProperties(file));
    }

    public ProductPluginManager(Properties props) {
        super(props);
        log = LogFactory.getLog(this.getClass().getName());

        String include = props.getProperty("plugins.include");
        String exclude = props.getProperty("plugins.exclude");
        if ((include != null) && (exclude != null)) {
            log.warn("plugins.{include,exclude} are both defined" +
                     ", use one or the other.");
        }
        if (include != null) {
            this.includePlugins = new HashMap();
            List plugins = StringUtil.explode(include, ",");
            for (int i=0; i<plugins.size(); i++) {
                this.includePlugins.put(plugins.get(i), Boolean.TRUE);                
            }
            //must-haves
            for (int i=0; i<CORE_PLUGINS.length; i++) {
                this.includePlugins.put(CORE_PLUGINS[i], Boolean.TRUE);
            }
        }
        if (exclude != null) {
            this.excludePlugins = new HashMap();
            List plugins = StringUtil.explode(exclude, ",");
            for (int i=0; i<plugins.size(); i++) {
                this.excludePlugins.put(plugins.get(i), Boolean.TRUE);                
            }
            for (int i=0; i<CORE_PLUGINS.length; i++) {
                String plugin = 
                    (String)this.excludePlugins.remove(CORE_PLUGINS[i]);
                if (plugin != null) {
                    this.log.warn("Cannot exclude " + plugin +
                                  " plugin, ignoring.");
                }
            }
        }
    }

    public String getName() {
        return ProductPlugin.TYPE_PRODUCT;
    }

    //assumes type names are unique across plugins,
    //which they should be.  if needed we could index on
    //product name too.
    /**
     * Find TypeInfo for the given platform and type name.
     * @param platform The platform name, e.g. "Linux"
     * @param name The type name, e.g. "Apache 2.0"
     */
    public TypeInfo getTypeInfo(String platform, String name) {
        HashMap platforms = (HashMap)this.types.get(platform);

        if (platforms == null) {
            return null;
        }

        return (TypeInfo)platforms.get(name);
    }

    private void setTypeInfo(String platform,
                             String name,
                             TypeInfo info) {

        HashMap platforms = (HashMap)this.types.get(platform);

        if (platforms == null) {
            platforms = new HashMap();
            this.types.put(platform, platforms);
        }

        platforms.put(name, info);
    }

    //XXX we could just cache lookups in getTypeInfo
    //instead of mapping everything.
    /**
     * Create a mapping of product plugin TypeInfos.
     * @see #getTypeInfo
     * @see #registerPluginJar(String jarName)
     */
    private int registerTypeInfo(TypeInfo[] types) {
        int order = DEPLOYMENT_ORDER_LAST;

        if (types == null) {
            return order;
        }

        for (int i=0; i<types.length; i++) {
            TypeInfo type = types[i];
            String[] platforms = type.getPlatformTypes();

            for (int j=0; j<platforms.length; j++) {
                setTypeInfo(platforms[j], type.getName(), type);
            }

            order = Math.min(order, type.getType());
        }

        return order;
    }

    public void init()
        throws PluginException {

        loadProductPluginStub();

        super.init(null); //null == we dont have a parent manager

        Properties props = getProperties();
        props.putAll(ProductProperties.getProperties());

        this.mpm = new MeasurementPluginManager(props);
        this.cpm = new ControlPluginManager(props);
        this.apm = new AutoinventoryPluginManager(props);
        this.rpm = new RtPluginManager(props);
        this.ltpm = new LogTrackPluginManager(props);
        this.ctpm = new ConfigTrackPluginManager(props);

        PluginManager[] mgrs = {
            this.mpm,
            this.cpm,
            this.apm,
            this.rpm,
            this.ltpm,
            this.ctpm,
            this //note to self
        };

        for (int i=0; i<mgrs.length; i++) {
            PluginManager mgr = mgrs[i];
            mgr.init(this);
            this.managers.put(mgr.getName(), mgr);
        }
    }

    public void shutdown()
        throws PluginException {

        this.managers.remove(getName()); //skip this

        //shutdown all managers and plugins registered within
        Iterator it = this.managers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            PluginManager manager = 
                (PluginManager)entry.getValue();

            try {
                manager.shutdown();
            } catch (PluginException e) {
                log.error(manager.getName() + ".shutdown() failed", e);
            }
        }

        this.types.clear();

        this.managers.clear(); 

        //shutdown() all registered ProductPlugins
        super.shutdown();
    }

    public MeasurementPluginManager getMeasurementPluginManager() {
        return this.mpm;
    }

    public ControlPluginManager getControlPluginManager() {
        return this.cpm;
    }

    public AutoinventoryPluginManager getAutoinventoryPluginManager() {
        return this.apm;
    }

    public RtPluginManager getRtPluginManager() {
        return this.rpm;
    }

    public LogTrackPluginManager getLogTrackPluginManager() {
        return this.ltpm;
    }

    public ConfigTrackPluginManager getConfigTrackPluginManager() {
        return this.ctpm;
    }

    public MeasurementPlugin getMeasurementPlugin(String name) {
        try {
            return (MeasurementPlugin)this.mpm.getPlugin(name);
        } catch (PluginNotFoundException e) {
            log.debug("No MeasurementPlugin found for: " + name);
            return null;
        }
    }

    public ControlPlugin getControlPlugin(String name) {
        try {
            return (ControlPlugin)this.cpm.getPlugin(name);
        } catch (PluginNotFoundException e) {
            log.debug("No ControlPlugin found for: " + name);
            return null;
        }
    }

    public ServerDetector getAutoinventoryPlugin(String name) {
        try {
            return (ServerDetector)this.apm.getPlugin(name);
        } catch (PluginNotFoundException e) {
            log.debug("No AutoinventoryPlugin found for: " + name);
            return null;
        }
    }

    public RtPlugin getRtPlugin(String name) {
        try {
            return (RtPlugin)this.rpm.getPlugin(name);
        } catch (PluginNotFoundException e) {
            log.debug("No RtPlugin found for: " + name);
            return null;
        }
    }

    public ProductPlugin getProductPlugin(String name) {
        try {
            return (ProductPlugin)getPlugin(name);
        } catch (PluginNotFoundException e) {
            log.debug("No ProductPlugin found for: " + name);
            return null;
        }
    }

    /**
     * Register a plugin with the given GenericPluginManger,
     * one instance per-platform of server/service types.
     * @param pm The plugin manager
     * @param info Plugin info from the product plugin where this
     * plugin is implemented.
     * @param plugin The plugin instance to register.
     * @param type The resource type info for this plugin.
     * @param registerTypes If true registers a plugin instance for
     * all platform types (server-side), otherwise the current platform
     * only (agent-side).
     * @throws PluginExistsException If an instance already exists
     * with the same name in the given plugin manager.
     */
    void registerTypePlugin(PluginManager pm,
                            PluginInfo info,
                            GenericPlugin plugin,
                            TypeInfo type)
        throws PluginExistsException {

        boolean register = this.registerTypes;
        boolean hasServer = false;
        ServiceTypeInfo service;
        ServerTypeInfo server = null;
        String[] platforms = null;
        String thisPlatform = OperatingSystem.getInstance().getName();
        String name = plugin.getName();
        String skipMsg;

        if (DEBUG_LIFECYCLE) {
            skipMsg =
                "Skipping registration of '" + name + "' " + 
                pm.getName() + " plugin on this platform";
        }

        switch (type.getType()) {
          case TypeInfo.TYPE_SERVER:
            server = (ServerTypeInfo)type;
            hasServer = true;
            break;
          case TypeInfo.TYPE_SERVICE:
            service = (ServiceTypeInfo)type;
            server = service.getServerTypeInfo();
            hasServer = true;
            break;
          case TypeInfo.TYPE_PLATFORM:
            if (((PlatformTypeInfo)type).isDevice()) {
                //always register devices, so they can be serviced by
                //an agent on another platform
                register = true;
            }
            if (!register && !thisPlatform.equals(name)) {
                if (DEBUG_LIFECYCLE) {
                    log.trace(skipMsg);
                }
                return;
            }
          default:
            break;
        }

        if (hasServer) {
            if (server.isPlatformDevice()) {
                //always register server types on platform devices
                register = true;
            }

            String[] validPlatforms = server.getValidPlatformTypes();
 
            if (register) {
                platforms = validPlatforms;
            }
            else {
                if (Arrays.asList(validPlatforms).contains(thisPlatform)) {
                    platforms = new String[] { thisPlatform };
                }
                else {
                    if (DEBUG_LIFECYCLE) {
                        log.trace(skipMsg + ", validPlatforms=" +
                                  Arrays.asList(validPlatforms));
                    }
                    return;
                }
            }
        }

        try {
            //XXX in the case of server/service type plugins
            //if there are to TypeInfos defined with the same
            //name but different platforms, first one wins
            //here.  this should just be for temporary compat,
            //until subsystems include platform name when
            //looking up a server/service type plugin.

            PluginInfo gInfo = new PluginInfo(name, info);
            pm.setPluginInfo(name, gInfo);

            pm.registerPlugin(plugin, null);
        } catch (PluginExistsException e) {
            if (!hasServer) {
                throw e;
            }
        } catch (PluginException e) {
            this.log.error("registerPlugin=" + plugin.getName(), e);
        }

        if (!hasServer) {
            return;
        }

        //for server/service types we register an instance of
        //the plugin per-platform
        for (int i=0; i<platforms.length; i++) {
            String pName =
                TypeBuilder.composePlatformTypeName(name, platforms[i]);

            try {
                pm.createPlugin(pName, plugin, null);
            } catch (PluginException e) {
                this.log.error("createPlugin=" + plugin.getName(), e);
            }
        }
    }

    public boolean isLoadablePluginName(String name) {
        if (!(name.endsWith("-plugin.jar") ||
              name.endsWith("-plugin.xml")))
        {
            return false;
        }

        name = name.substring(0, name.length()-11);
        if (this.includePlugins != null) {
            if (this.includePlugins.get(name) != Boolean.TRUE) {
                log.debug("Skipping " + name + " (not in plugins.include)");
                return false;
            }
        }
        if (this.excludePlugins != null) {
            if (this.excludePlugins.get(name) == Boolean.TRUE) {
                log.debug("Skipping " + name + " (in plugins.exclude)");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * registerPluginJar() without mapping types.
     * @param jarName The name of the jar file on disk.
     * @see #registerPluginJar(String jarName,ClassLoader resourceLoader)
     */
    public String registerPluginJar(String jarName)
        throws PluginException, PluginExistsException {
        return registerPluginJar(jarName, null);
    }

    private void loadProductPluginStub() throws PluginException {
        this.pluginStub = new byte[1024];
        ClassLoader loader = this.getClass().getClassLoader();

        InputStream is = null;
        try {
            is = loader.getResourceAsStream(PLUGIN_STUB);
            if (is == null) {
                throw new PluginException("Unable to find: " + PLUGIN_STUB);
            }
            this.pluginStubLength = is.read(this.pluginStub);
        } catch (IOException e) {
            throw new PluginException("Unable to read: " +
                                      PLUGIN_STUB + ": " + e);
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) {}
            }
        }
    }

    public int registerCustomPlugins(String startDir)
        throws PluginException, PluginExistsException {
        //check startDir and higher for hq-plugins
        File dir = new File(startDir).getAbsoluteFile();
        while (dir != null) {
            File customPluginDir = new File(dir, "hq-plugins");
            if (customPluginDir.exists()) {
                return registerPlugins(customPluginDir.toString());
            }
            dir = dir.getParentFile();
        }
        return 0;
    }
    
    public int registerPlugins(String path)
        throws PluginException, PluginExistsException {

        int nplugins = 0;
        List dirs = StringUtil.explode(path, File.pathSeparator);

        for (int i=0; i<dirs.size(); i++) {
            
            File dir = new File((String)dirs.get(i));
            if (!dir.exists()) {
                log.debug("register plugins: " + dir +
                          " does not exist");
                continue;
            }
            if (!dir.isDirectory()) {
                log.debug("register plugins: " + dir +
                          " not a directory");
                continue;
            }

            File[] plugins = dir.listFiles();
            for (int j=0; j<plugins.length; j++) {
                String name = plugins[j].getName();
                if (!isLoadablePluginName(name)) {
                    log.debug("register plugins: " + name +
                              " not a plugin");
                    continue;
                }
                log.info("Loading plugin: " + name);
                registerPluginJar(plugins[j].getAbsolutePath());
                nplugins++;
            }
        }

        return nplugins;
    }

    private void addClassPath(PluginLoader loader, String path)
        throws PluginException {

        File jar = new File(path);

        if (log.isDebugEnabled()) {
            String exists;
            if (jar.exists()) {
                if (jar.canRead()) {
                    exists = jar.isDirectory() ? "d" : "+";
                }
                else {
                    exists = "~";
                }
            }
            else {
                exists = "-";
            }
            log.debug("   " + exists + " " + jar);
        }

        try {
            loader.addURL(jar);
        } catch (PluginLoaderException e) {
            throw new PluginException(e.getMessage());
        }
    }

    private void addClassPath(ProductPlugin plugin, PluginLoader loader)
        throws PluginException {
        //XXX expand variables in plugin.getClassPath
        String[] classpath = plugin.getClassPath(this);

        if (classpath.length == 0) {
            log.trace("No classpath configured for: " + plugin.getName());
            return;
        }

        log.debug("adding to " + plugin.getName() + " classpath:");
        String pdkDir = getProperty(PROP_PDK_DIR);

        for (int i=0; i<classpath.length; i++) {
            String path = classpath[i];
            //for commandline usage outside of agent directory
            if (path.startsWith("pdk/") && (pdkDir != null)) {
                path = pdkDir + "/" + path.substring(3);
            }
            addClassPath(loader, path);
        }
    }

    /**
     * Load a product plugin jar.
     * Registers the product plugin, as defined by the Main-Class
     * jar manifest attribute which must be a class which implements
     * the ProductPlugin interface.
     * Registers plugins supported for each plugin type
     * (control, measurement, responsetime) as returned by the
     * ProductPlugin.getPlugin method.
     * @param jarName The name of the jar file on disk.
     * @param resourceLoader ClassLoader used to find jar resources.
     * @return The name of the product plugin as returned by 
     * ProductPlugin.getName.
     * @see org.hyperic.hq.product.ProductPlugin
     */
    public String registerPluginJar(String jarName,
                                    ClassLoader resourceLoader)
        throws PluginException, PluginExistsException {

        ProductPlugin plugin = null; //will be from Main-Class
        Class pluginClass = null;
        PluginData data;

        try {
            PluginLoader loader =
                PluginLoader.create(jarName,
                                    this.unpackNestedJars,
                                    this.getClass().getClassLoader());

            PluginLoader.setClassLoader(loader);
            ClassLoader dataLoader;
            if (resourceLoader != null) {
                dataLoader = resourceLoader;
            }
            else {
                dataLoader = loader;
            }

            data = PluginData.getInstance(this, dataLoader, jarName);
            
            String implName = loader.getPluginClassName(); 
            if (implName == null) {
                implName =
                    data.getPlugin(ProductPlugin.TYPE_PRODUCT,
                                   ProductPlugin.TYPE_PRODUCT);
                if (implName != null) {
                    loader.setPluginClassName(implName);
                }
            }

            if (implName == null) {
                pluginClass =
                    loader.loadPlugin(PLUGIN_STUB_NAME,
                                      this.pluginStub,
                                      this.pluginStubLength);
            }
            else {
                pluginClass = loader.loadPlugin();
            }

            plugin = (ProductPlugin)pluginClass.newInstance();
            plugin.data = data;
            
            //there are 3 ways to set the product name:
            //- legacy ProductPlugin.setName()
            //- <product name="foo"> in hq-plugin.xml
            //- default to name of the plugin file minus "-plugin.{xml,jar}"
            //we try all three and make sure plugin.name and data.name
            //are both set with the same value.
            String pluginName = plugin.getName(); //legacy
            if (pluginName == null) {
                pluginName = data.getName(); //hq-plugin.xml
            }
            if (pluginName == null) {
                //if name not specified, derive from *-plugin.{xml,jar} name
                String jar = new File(jarName).getName();
                int ix = jar.indexOf("-plugin.");
                if (ix != -1) {
                    pluginName = jar.substring(0, ix);
                }
                else {
                    throw new PluginException("Malformed name for: " + jarName);
                }
            }
            
            if (data.getName() == null) {
                data.setName(pluginName);
            }
            if (plugin.getName() == null) {
                plugin.setName(pluginName);
            }

            //only setup classpath on the agentside.
            //server-side within JBoss, ClassLoader is a UCL not PluginLoader.
            if (pluginClass.getClassLoader() instanceof PluginLoader) {
                addClassPath(plugin, loader);
            }

            PluginInfo info = new PluginInfo(plugin, jarName);
            //e.g. for finding hq-plugin.xml
            //when deployed under jboss
            //resourceLoader != plugin.getClass().getClassLoader()
            if (resourceLoader == null) {
                resourceLoader = plugin.getClass().getClassLoader();
            }
            info.resourceLoader = resourceLoader;

            setPluginInfo(pluginName, info);

            registerPlugin(plugin, null);

            TypeInfo[] types = plugin.getTypes();

            if (types == null) {
                this.log.error(pluginName +
                               ".getTypes returned null");
                return null;
            }

            if (this.registerTypes) {
                info.deploymentOrder = registerTypeInfo(types);
            }

            for (int i=0; i<ProductPlugin.TYPES.length; i++) {
                String type = ProductPlugin.TYPES[i];

                if(type.equals(ProductPlugin.TYPE_PRODUCT))
                    continue;

                PluginManager pm =
                    (PluginManager)managers.get(type);

                for (int j=0; j<types.length; j++) {
                    GenericPlugin gPlugin;
                    String typeName = types[j].getName();

                    gPlugin = plugin.getPlugin(type, types[j]);
                    if (gPlugin == null) {
                        if (DEBUG_LIFECYCLE) {
                            log.debug(pluginName +
                                      " does not implement " + type +
                                      " for type=" + typeName);
                        }
                        continue;
                    }
                    
                    gPlugin.data = data;
                    gPlugin.setName(typeName);
                    gPlugin.setTypeInfo(types[j]);

                    if (DEBUG_LIFECYCLE) {
                        log.debug(pluginName + " implements " + type +
                                " for type=" + typeName);
                    }

                    registerTypePlugin(pm, info, gPlugin, types[j]);
                }
            }
            return pluginName;
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException(e.getMessage(), e);
        } finally {
            if (plugin != null) {
                PluginLoader.resetClassLoader(plugin);
            }
        }
    }

    private void removeManagerPlugins(PluginManager mgr,
                                      String jarName)
        throws PluginException {

        Map mPlugins = mgr.getPlugins();

        //cannot use keySet().iterator() else
        //ConcurrentModificationException is thrown during removePlugin()
        String[] keys = (String[])mPlugins.keySet().toArray(new String[0]);

        for (int i=0; i<keys.length; i++) {
            String name = keys[i];
            PluginInfo info = mgr.getPluginInfo(name);

            if (info == null) {
                String msg = "no plugin info found for " + 
                    mgr.getName() + " plugin " + name;
                throw new PluginException(msg);
            }

            //XXX: should prolly check more than jar basename
            //but then again, they live in the same directory
            //so jar basename should be unique.
            if (info.jar.equals(jarName)) {
                try {
                    mgr.removePlugin(name);
                } catch (PluginNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void removePluginJar(String jarName)
        throws PluginException {

        String fileName = new File(jarName).getName();

        Iterator it = this.managers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            PluginManager manager = 
                (PluginManager)entry.getValue();

            removeManagerPlugins(manager, fileName);
        }
    }

    public void updatePluginJar(String jarName)
        throws PluginException {

        removePluginJar(jarName);

        try {
            registerPluginJar(jarName);
        } catch (PluginExistsException e) {
            //should not happen if removePluginJar was a success
            throw new PluginException(e);
        }
    }

    public PluginManager getPluginManager(String type)
        throws PluginException {

        PluginManager mgr =
            (PluginManager)this.managers.get(type);

        if (mgr == null) {
            //XXX PluginManagerNotFoundException
            throw new PluginException();
        }

        return mgr;
    }

    public void setProperty(String key, String value) {
        Iterator it = this.managers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            PluginManager manager = 
                (PluginManager)entry.getValue();

            manager.getProperties().setProperty(key, value);
        }
    }
}
