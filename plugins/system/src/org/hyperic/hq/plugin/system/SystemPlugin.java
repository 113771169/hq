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

package org.hyperic.hq.plugin.system;

import org.hyperic.hq.product.ConfigFileTrackPlugin;
import org.hyperic.hq.product.ExecutableMeasurementPlugin;
import org.hyperic.hq.product.ExecutableProcess;
import org.hyperic.hq.product.GenericPlugin;
import org.hyperic.hq.product.LogFileTailPlugin;
import org.hyperic.hq.product.LogTrackPlugin;
import org.hyperic.hq.product.ProcessControlPlugin;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.product.ServerTypeInfo;
import org.hyperic.hq.product.ServiceTypeInfo;
import org.hyperic.hq.product.TypeBuilder;
import org.hyperic.hq.product.TypeInfo;
import org.hyperic.hq.product.Win32ControlPlugin;
import org.hyperic.hq.product.Win32EventLogTrackPlugin;
import org.hyperic.hq.product.Win32MeasurementPlugin;

import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.ConfigSchema;
import org.hyperic.util.config.SchemaBuilder;
import org.hyperic.util.config.StringConfigOption;

public class SystemPlugin extends ProductPlugin {

    public static final String NAME = "system";

    public static final String FILE_SERVER_NAME    = "FileServer";
    public static final String NETWORK_SERVER_NAME = "NetworkServer";
    //XXX we need a dummy server type for 'CPU' and 'Process' services.
    //would like to use one for both, this is probably not the best name.
    //suggestions welcome.
    public static final String PROCESS_SERVER_NAME = "ProcessServer";
    public static final String WINDOWS_SERVER_NAME = "WindowsServer";

    public static final String FS_NAME    = "Mount";
    public static final String FILE_NAME  = "File";
    public static final String SCRIPT_NAME = "Script";
    public static final String DIR_NAME   = "Directory";
    public static final String NETIF_NAME = "Interface";
    public static final String CPU_NAME   = "CPU";
    public static final String PROCESS_NAME = "Process";
    public static final String MPROCESS_NAME = "MultiProcess";
    public static final String SVC_NAME   = "Windows Service";

    public static final String[] FILE_SERVICES = {
        FS_NAME,
        FILE_NAME,
        DIR_NAME,
    };

    public static final String[] NETWORK_SERVICES = {
        NETIF_NAME,
    };

    public static final String[] PROCESS_SERVICES = {
        CPU_NAME,
        PROCESS_NAME,
        MPROCESS_NAME
    };

    public static final String FILE_MOUNT_SERVICE =
        TypeBuilder.composeServiceTypeName(FILE_SERVER_NAME,
                                           FS_NAME);

    public static final String NETWORK_INTERFACE_SERVICE =
        TypeBuilder.composeServiceTypeName(NETWORK_SERVER_NAME,
                                           NETIF_NAME);

    public static final String SCRIPT_SERVICE = SCRIPT_NAME;

    public static final String FILE_SERVICE =
        TypeBuilder.composeServiceTypeName(FILE_SERVER_NAME,
                                           FILE_NAME);
    
    public static final String DIR_SERVICE =
        TypeBuilder.composeServiceTypeName(FILE_SERVER_NAME,
                                           DIR_NAME);

    public static final String PROP_FS    = "mount";

    public static final String PROP_PATH  = "path";

    public static final String PROP_NETIF = "interface";

    public static final String PROP_CPU   = "cpu";

    public static final String PROP_ENABLE_USER_AI = "autodiscover.users";
    
    public static final String PROP_SVC   =
        Win32ControlPlugin.PROP_SERVICENAME;
    
    public static final String PROP_ARGS = "args";

    public SystemPlugin() {
        setName(NAME);
    }

    public GenericPlugin getPlugin(String type, TypeInfo info) {

        if (type.equals(ProductPlugin.TYPE_MEASUREMENT)) {
            if (info.getName().equals(SVC_NAME)) {
                return new Win32MeasurementPlugin();
            }
            else if ((info.getType() == TypeInfo.TYPE_SERVER) &&
                     ((ServerTypeInfo)info).isVirtual())
            {
                //virtual server, no metrics.
            }
            else if (info.getName().equals(SCRIPT_NAME)) {
                return new ExecutableMeasurementPlugin();
            }
            else {
                return new SystemMeasurementPlugin();
            }
        }
        else if (type.equals(ProductPlugin.TYPE_AUTOINVENTORY)) {
            switch (info.getType()) {
              case TypeInfo.TYPE_PLATFORM:
                return new SigarPlatformDetector();
              case TypeInfo.TYPE_SERVER:
                if (info.getName().equals(FILE_SERVER_NAME)) {
                    return new FileSystemDetector();
                }
                else if (info.getName().equals(NETWORK_SERVER_NAME)) {
                    return new NetifDetector();
                }
                else if (info.getName().equals(PROCESS_SERVER_NAME)) {
                    return new ProcessorDetector();
                }
                else if (info.getName().equals(WINDOWS_SERVER_NAME)) {
                    return new WindowsDetector();
                }
            }
        }
        else if (type.equals(ProductPlugin.TYPE_CONTROL)) {
            if (info.isService(FILE_NAME)) {
                return new FileControlPlugin();
            }
            else if (info.getName().equals(SVC_NAME)) {
                return new Win32ControlPlugin();
            }
            else if (info.getName().equals(PROCESS_NAME)) {
                return new ProcessControlPlugin();
            }
        }
        else if (type.equals(ProductPlugin.TYPE_CONFIG_TRACK)) {
            if ((info.getType() == TypeInfo.TYPE_PLATFORM) ||
                info.getName().equals(PROCESS_NAME))
            {
                return new ConfigFileTrackPlugin();
            }
        }
        else if (type.equals(ProductPlugin.TYPE_LOG_TRACK)) {
            if (info.getType() == TypeInfo.TYPE_PLATFORM) {
                if (info.isWin32Platform()) {
                    return new WindowsLogTrackPlugin();
                }
                else {
                    return new UnixLogTrackPlugin();
                }
            }
            else if (info.getName().equals(SVC_NAME)) {
                return new Win32EventLogTrackPlugin();
            }
            else if (info.isService(FILE_NAME)) {
                return new FileServiceLogPlugin();
            }
            else if (info.getName().equals(SCRIPT_NAME)) {
                return new LogTrackPlugin();
            }
            else if (info.getName().equals(PROCESS_NAME)) {
                return new LogFileTailPlugin();
            }
        }
        else if (type.equals(ProductPlugin.TYPE_LIVE_DATA)) {
            return new SystemLiveDataPlugin();
        }

        return null;
    }

    private static final String[][] PLAT_CPROPS = {
        { "arch", "Architecture" },
        { "version", "OS Version" },
        { "vendor", "Vendor" },
        { "vendorVersion", "Vendor Version" },
        { "ram", "RAM" },
        { "cpuSpeed", "CPU Speed" },
        { "primaryDNS", "Primary DNS" },
        { "secondaryDNS", "Secondary DNS" },
        { "defaultGateway", "Default Gateway" },
    };

    private static final String[][] NETIF_CPROPS = {
        { "mtu", "Maximum Transmission Unit" },
        { "flags", "Interface Flags" },
        { "mac", "MAC Address" },
        { "address", "IP Address" },
        { "netmask", "Netmask" },
        { "broadcast", "Broadcast Address" },
    };
    
    private static final String[][] FILE_CPROPS = {
        { "md5", "Message Digest" },
        { "fs", "File System" },
        { "permissions", "Permissions" },
        { "user", "User" },
        { "group", "Group" }
    };
    
    private static final String[][] PROCESS_CPROPS = {
        { "user", "User" },
        { "group", "Group" },
        { "exe", "Executable" },
        { "cwd", "Current Working Directory" }
    };
    
    private static final String[][] SVC_CPROPS = {
        { "path", "Path to executable" },
        { "startupType", "Startup type" },
        { "displayName", "Display name" },
    };

    public ConfigSchema getCustomPropertiesSchema(String name) {
        ConfigSchema schema = new ConfigSchema();
        String[][] cprops;
        if (SigarPlatformDetector.isSupportedPlatform(name)) {
            cprops = PLAT_CPROPS;
        }
        else if (name.equals(NETWORK_INTERFACE_SERVICE)) {
            cprops = NETIF_CPROPS;
        }
        else if (name.equals(FILE_SERVICE) ||
                 name.equals(SCRIPT_SERVICE) ||
                 name.equals(DIR_SERVICE))
        {
            cprops = FILE_CPROPS;
        }
        else if (name.equals(PROCESS_NAME)) {
            cprops = PROCESS_CPROPS;
        }
        else if (name.equals(SVC_NAME)) {
            cprops = SVC_CPROPS;
        }
        else {
            return schema;
        }
        for (int i=0; i<cprops.length; i++) {
            StringConfigOption opt =
                new StringConfigOption(cprops[i][0], cprops[i][1]);
            schema.addOption(opt);
        }        
        return schema;
    }

    private void addWindowsService(TypeBuilder types) {
        /* we dont use TypeBuilder here because we dont want
         * the virtual server name as part of the service name
         */
        ServerTypeInfo server = 
            new ServerTypeInfo(WINDOWS_SERVER_NAME,
                               WINDOWS_SERVER_NAME,
                               TypeBuilder.NO_VERSION);
        server.setValidPlatformTypes(TypeBuilder.WIN32_PLATFORM_NAMES);

        server.setVirtual(true);

        ServiceTypeInfo service =
            new ServiceTypeInfo(SVC_NAME, SVC_NAME, server);

        types.add(server);
        types.add(service);
    }

    private void addProcessServices(TypeBuilder types) {
        /* we dont use TypeBuilder here because we dont want
         * the virtual server name as part of the service name
         */
        ServerTypeInfo server = 
            new ServerTypeInfo(PROCESS_SERVER_NAME,
                               PROCESS_SERVER_NAME,
                               TypeBuilder.NO_VERSION);

        server.setVirtual(true);
        
        types.add(server);

        for (int i=0; i<PROCESS_SERVICES.length; i++) {
            String name = PROCESS_SERVICES[i];
            ServiceTypeInfo service =
                new ServiceTypeInfo(name, name, server);

            types.add(service);
        }
    }

    public TypeInfo[] getTypes() {
        TypeBuilder types = new TypeBuilder();

        String[] platforms = TypeBuilder.ALL_PLATFORM_NAMES;

        for (int i=0; i<platforms.length; i++) {
            types.addPlatform(platforms[i]);
        }

        ServerTypeInfo server;

        server = types.addServer(FILE_SERVER_NAME,
                                 TypeBuilder.NO_VERSION);

        server.setVirtual(true);
        server.setDescription("Platform File Server");

        types.addServices(server, FILE_SERVICES);
        
        ServiceTypeInfo script =
            new ServiceTypeInfo(SCRIPT_NAME, SCRIPT_NAME, server); 

        types.add(script);

        server = types.addServer(NETWORK_SERVER_NAME,
                                 TypeBuilder.NO_VERSION);

        server.setVirtual(true);        
        server.setDescription("Platform Network Server");

        types.addServices(server, NETWORK_SERVICES);

        addProcessServices(types);

        addWindowsService(types);

        return types.getTypes();
    }

    public ConfigSchema getConfigSchema(TypeInfo info, ConfigResponse config) {

        SchemaBuilder schema = new SchemaBuilder(config);

        switch (info.getType()) {
          case TypeInfo.TYPE_PLATFORM:
              //XXX does not work
              //schema.add(PROP_ENABLE_USER_AI,
              //           "Enable autodiscovery of user services",
              //           false);
              break;
          case TypeInfo.TYPE_SERVICE:
            if (info.isService(FS_NAME)) {
                schema.add(PROP_FS,
                           "File System Mount",
                           "/");
            }
            else if (info.isService(FILE_NAME)) {
                schema.add(PROP_PATH,
                           "Path to File", 
                           "log/agent.log");
            }
            else if (info.isService(DIR_NAME)) {
                schema.add(PROP_PATH,
                           "Path to Directory", 
                           "data");
            }
            else if (info.isService(NETIF_NAME)) {
                schema.add(PROP_NETIF,
                           "Network Interface",
                           "eth0");
            }
            else if (info.getName().equals(CPU_NAME)) {
                schema.add(PROP_CPU,
                           "CPU",
                           "0");
            }
            else if (info.getName().equals(PROCESS_NAME)) {
                schema.add(SystemMeasurementPlugin.PTQL_CONFIG,
                           "Process Query",
                           "State.Name.eq=java");
            }
            else if (info.getName().equals(MPROCESS_NAME)) {
                schema.add(SystemMeasurementPlugin.PTQL_CONFIG,
                          "Multi Process Query",
                          "State.Name.eq=httpd");
            }
            else if (info.getName().equals(SVC_NAME)) {
                schema.add(PROP_SVC,
                           SVC_NAME + " Name",
                           "");
            }
            else if(info.getName().equals(SCRIPT_NAME)) {
                schema.add("prefix",
                           "Prefix arguments to script",
                            "").setOptional(true);
                schema.add(PROP_PATH,
                           "Script Name",
                           "/path/to/script");
                schema.add(ExecutableProcess.PROP_ARGS,
                           "Script Arguments",
                           "").setOptional(true);
                schema.add(ExecutableProcess.PROP_TIMEOUT,
                           "Script Timeout (in seconds)",
                           120);
            }
            break;
        }

        return schema.getSchema();
    }
}
