/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.hyperic.hq.plugin.db2jdbc;

import java.util.ArrayList;
import java.util.List;
import org.hyperic.hq.product.AutoServerDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServerDetector;
import org.hyperic.hq.product.ServerResource;
import org.hyperic.hq.product.ServiceResource;
import org.hyperic.util.config.ConfigResponse;

/**
 *
 * @author laullon
 */
public class DataBase8ServerDetector extends DataBaseServerDetector {

    @Override
    protected List discoverServices(ConfigResponse config) throws PluginException {
        List res = new ArrayList();
        String type = getTypeInfo().getName();
        //String dbName = config.getValue("db2.jdbc.database");

        /**
         * Table
         */
        String schema = config.getValue("db2.jdbc.user").toUpperCase();
        List<String> tbl = getList(config, "select TABLE_NAME from table (SNAPSHOT_TABLE('sample', -2)) as T"); // XXX revisar si se pueden sacar de otro sitio WHERE TABSCHEMA='" + schema + "'");
        for (String tbName : tbl) {
//            if (!tbName.toUpperCase().startsWith("SYS")) {
                ServiceResource tb = new ServiceResource();
                tb.setType(type + " Table");
                tb.setServiceName("Table " + schema + "." + tbName);

                ConfigResponse conf = new ConfigResponse();
                conf.setValue("table", tbName);
                conf.setValue("schema", schema);
                setProductConfig(tb, conf);
                tb.setMeasurementConfig();
                tb.setResponseTimeConfig(new ConfigResponse());
                tb.setControlConfig();

                res.add(tb);
//            }
        }

        /**
         * Table Space
         */
        List<String> tbspl = getList(config, "select TABLESPACE_NAME from table (SNAPSHOT_TBS('sample', -2)) as T");
        for (String tbspName : tbspl) {
            ServiceResource bpS = new ServiceResource();
            bpS.setType(type + " Table Space");
            bpS.setServiceName("Table Space " + tbspName);

            ConfigResponse conf = new ConfigResponse();
            conf.setValue("tablespace", tbspName);
            setProductConfig(bpS, conf);
            bpS.setMeasurementConfig();
            bpS.setResponseTimeConfig(new ConfigResponse());
            bpS.setControlConfig();

            res.add(bpS);
        }

        /**
         * Buffer Pool
         */
        List<String> bpl = getList(config, "select BP_NAME from table (SNAPSHOT_BP('sample', -2)) as T");
        for (String bpName : bpl) {
            ServiceResource bpS = new ServiceResource();
            bpS.setType(type + " Buffer Pool");
            bpS.setServiceName("Buffer Pool " + bpName);

            ConfigResponse conf = new ConfigResponse();
            conf.setValue("bufferpool", bpName);
            setProductConfig(bpS, conf);
            bpS.setMeasurementConfig();
            bpS.setResponseTimeConfig(new ConfigResponse());
            bpS.setControlConfig();

            res.add(bpS);
        }

        /**
         * Mempory Pool
         */
        /*List<String> mpl = getList(config, "SELECT concat(concat(POOL_ID, '|'), COALESCE(POOL_SECONDARY_ID,'')) as name FROM SYSIBMADM.SNAPDB_MEMORY_POOL where POOL_SECONDARY_ID is NULL or POOL_ID='BP'");
        for (String mpN : mpl) {
            String[] names = mpN.split("\\|");
            String mpId=names[0].trim();
            String mpSId=(names.length==2)?names[1].trim():"";
            String mpName=(mpId+" "+mpSId).trim();

            ServiceResource mpS = new ServiceResource();
            mpS.setType(type + " Memory Pool");
            mpS.setServiceName("Memory Pool " + mpName);

            ConfigResponse conf = new ConfigResponse();
            conf.setValue("pool_id", mpId);
            conf.setValue("sec_pool_id", mpSId);
            setProductConfig(mpS, conf);
            mpS.setMeasurementConfig();
            mpS.setResponseTimeConfig(new ConfigResponse());
            mpS.setControlConfig();

            res.add(mpS);
        }*/

        return res;
    }



    public List getServerResources(ConfigResponse pconf) {
        List res = new ArrayList();
        /*String name="tets";
        String iPath="/";
        getLog().debug("[createDataBase] name='" + name + "' iPath='" + iPath + "'");
        ServerResource server = new ServerResource();
        server.setType(getTypeInfo().getName());
        //res.setName(getPlatformName() + " " + getTypeInfo().getName() + " " + name);
        server.setName(getPlatformName() + " DB2 " + name);
        server.setInstallPath(iPath);
        server.setIdentifier(server.getName());

        ConfigResponse conf = new ConfigResponse();
        conf.setValue("db2.jdbc.database", name);
        conf.setValue("db2.jdbc.version", getTypeInfo().getVersion());
        setProductConfig(server, conf);

        res.add(server);*/
        return res;
    }

}
