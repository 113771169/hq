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

package org.hyperic.hq.product.ant;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.taskdefs.Manifest.Attribute;
import org.apache.tools.ant.types.FileSet;
import org.hyperic.hq.product.ProductPluginManager;
import org.hyperic.hq.product.pluginxml.PluginData;
import org.hyperic.hq.product.pluginxml.PluginParser;
import org.hyperic.util.StringUtil;

public class PluginJar extends Jar {

    private static final String DEFAULT_PACKAGE =
        "org.hyperic.hq.plugin";
    
    private String name = null;
    private String pluginDir = null;
    private String pluginPackage = null;
    private String pluginClass = null;
   
    public String getDir() {
        if (this.pluginDir == null) {
            String home = getProperty("basedir", ".");
            String dir = home + "/plugins/" + getName(); 
            if (new File(dir).exists()) {
                return dir;
            }
            return getProperty("basedir", ".");
        }
        return this.pluginDir;
    }

    public void setDir(String value) {
        this.pluginDir = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackage() {
        return this.pluginPackage;
    }

    public void setPackage(String value) {
        this.pluginPackage = value;
    }

    public String getMainClass() {
        return this.pluginClass;
    }

    public void setMainClass(String value) {
        this.pluginClass = value;
    }

    private String getProperty(String name, String defval) {
        String value = getProject().getProperty(name);
        if (value == null) {
            return defval;
        }
        return value;
    }

    public static void validatePluginXML(String dir, String name)
        throws BuildException {

        File descriptor = new File(name);
        if (!descriptor.exists()) {
            System.out.println("Skipping " + name + " validation " +
                               "(does not exist)");
            return;
        }
        PluginParser parser = new PluginParser();
        PluginData data = new PluginData();
        FileInputStream is = null;

        data.setFile(name);
        ProductPluginManager.setPdkDir(dir);

        try {
            System.out.println("Validating " + name);
            is = new FileInputStream(descriptor);
            parser.parse(is, data);
        } catch (Exception e) {
            throw new BuildException(e.getMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
    }
    
    public void execute() throws BuildException {
        String buildDir = getProperty("build.dir", "build");
        String pluginDir = getProperty("plugin.dir", "build/plugins");
        String packageName = getPackage();
        if (packageName == null) {
            String name = getName();
            int ix;
            if ((ix = name.indexOf('-')) != -1) {
                //e.g. groovy-scripting -> groovy
                name = name.substring(0, ix);
            }
            packageName = DEFAULT_PACKAGE + "." + name;
        }
        boolean validate =
            getProperty("pluginxml.validate", "true").equals("true");//XXX

        String classesMatch = "*/" + 
            StringUtil.replace(packageName.substring(4), ".", "/");

        if (!new File(pluginDir).exists()) {
            new File(pluginDir).mkdirs();
        }
        File destFile = new File(pluginDir, getName() + "-plugin.jar");
        //setBasedir(new File(buildDir + "/classes"));
        setDestFile(destFile);

        String[] dirs = {
            "etc", "mibs", "lib", "scripts", "hqu"
        };

        //<fileset dir="plugins/${plugin.dirname}" includes="etc/**"/>
        for (int i=0; i<dirs.length; i++) {
            String name = dirs[i];
            if (!new File(getDir(), name).exists()) {
                continue;
            }
            FileSet set = new FileSet();
            set.setDir(new File(getDir()));
            set.setIncludes(name + "/**");
            addFileset(set);
        }

        //<include name="org.hyperic.hq.product/${plugin.package}/**/*.class"/>
        FileSet classes = new FileSet();
        classes.setDir(new File(buildDir, "classes"));
        classes.setIncludes(classesMatch + "/**/*.class");
        addFileset(classes);

        createManifest(packageName);

        super.execute();

        if (validate) {
            final String desc = "/" + PluginData.PLUGIN_XML;
            String plugin = getDir() + desc;
            if (!new File(plugin).exists()) {
                String name =
                    ProductPluginManager.getNameFromFile(destFile.getPath());
                String xml =
                    getDir() + 
                    StringUtil.replace(desc, "hq", name);
                if (new File(xml).exists()) {
                    plugin = xml;
                }
            }
            String pdk = getProperty(ProductPluginManager.PROP_PDK_DIR, "pdk");
            validatePluginXML(pdk, plugin);
        }
    }
    
    private void createManifest(String packageName) {
        Manifest manifest = new Manifest();
        try {
            addConfiguredManifest(manifest);
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Build-Owner", getProperty("user.name",
                                                                                                   "Unknown")));
            String hostName = "Unknown";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                //ignore
            }
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Build-Host", hostName));
            
            if (getProperty("release.comment", null) != null) {
                manifest.getMainSection()
                        .addConfiguredAttribute(new Attribute("Build-Type", getProperty("release.comment", "Unknown")));
            }
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Build-Date", new Date().toString()));
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Specification-Title", "HQU Plugin"));
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Specification-Vendor", "VMware Inc."));
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Specification-Version",
                                                                           getProperty("version", "Unknown") + "-" +
                                                                               getProperty("build", "Unknown")));
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Implementation-Title", getName()));
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Implementation-Version",
                                                                           getProperty("version", "Unknown") + "-" +
                                                                           getProperty("build", "Unknown")));
            manifest.getMainSection().addConfiguredAttribute(new Attribute("Implementation-Vendor", "VMware Inc."));

            String mainClass = getMainClass();
            if (mainClass != null) {
                if (mainClass.indexOf(".") == -1) {
                    mainClass = packageName + "." + mainClass;
                }
                Attribute mainClassAttr = new Attribute("Main-Class", mainClass);
                manifest.addConfiguredAttribute(mainClassAttr);
            }
        } catch (ManifestException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }
}
