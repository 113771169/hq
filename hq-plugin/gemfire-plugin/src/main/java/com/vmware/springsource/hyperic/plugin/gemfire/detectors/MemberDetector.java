package com.vmware.springsource.hyperic.plugin.gemfire.detectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.apache.commons.logging.Log;
import org.hyperic.hq.product.AutoServerDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServerDetector;
import org.hyperic.hq.product.ServerResource;
import org.hyperic.hq.product.ServiceResource;
import org.hyperic.hq.product.jmx.MxUtil;
import org.hyperic.util.config.ConfigResponse;

public abstract class MemberDetector extends ServerDetector implements AutoServerDetector {

    Log log = getLog();

    public List getServerResources(ConfigResponse pc) throws PluginException {
        log.debug("[getServerResources] pc=" + pc);
        List servers = new ArrayList();
        try {
            MBeanServerConnection mServer = MxUtil.getMBeanServer(pc.toProperties());
            log.debug("mServer=" + mServer);

            Object[] args = {};
            String[] def = {};
            ObjectName statsMBean = new ObjectName("GemFire:type=MemberInfoWithStatsMBean");
            String[] members = (String[]) mServer.invoke(statsMBean, "getMembers", args, def);
            log.debug("[getServerResources] members=" + Arrays.asList(members));
            for (String menber : members) {
                Object[] args2 = {menber};
                String[] def2 = {String.class.getName()};
                Map<String, Object> memberDetails = (Map) mServer.invoke(statsMBean, "getMemberDetails", args2, def2);
                log.debug("[getServerResources] memberDetails.size()=" + memberDetails.size());

                if (isValidMember(memberDetails)) {
                    ServerResource server = createServerResource("");
                    server.setName(getTypeInfo().getName() + " " + menber);
                    server.setIdentifier(menber);
                    ConfigResponse c = new ConfigResponse();
                    c.setValue("memberID", menber);
                    setMeasurementConfig(server, c);
                    setProductConfig(server, new ConfigResponse());
                    setCustomProperties(server, getAtributtes(memberDetails));
                    servers.add(server);
                }
            }

        } catch (Exception ex) {
            throw new PluginException(ex.getMessage(), ex);
        }
        return servers;
    }

    @Override
    protected List discoverServices(ConfigResponse config) throws PluginException {
        log.debug("[discoverServices] config=" + config);
        List services = new ArrayList();
        try {
            MBeanServerConnection mServer = MxUtil.getMBeanServer(config.toProperties());
            log.debug("mServer=" + mServer);

            String memberId = config.getValue("memberID");
            Object[] args2 = {memberId};
            String[] def2 = {String.class.getName()};
            ObjectName statsMBean = new ObjectName("GemFire:type=MemberInfoWithStatsMBean");

            Map<String, Object> memberDetails = (Map) mServer.invoke(statsMBean, "getMemberDetails", args2, def2);
            Map<String, Map> regions = (Map) memberDetails.get("gemfire.member.regions.map");

            if (regions != null) {
                for (Map region : regions.values()) {
                    String name = (String) region.get("gemfire.region.name.string");
                    ServiceResource service = createServiceResource("Region");
                    service.setName(memberId + " Region " + name);
                    ConfigResponse c = new ConfigResponse();
                    c.setValue("regionID", (String) region.get("gemfire.region.name.string"));
                    c.setValue("name", name);
                    c.setValue("memberID", memberId);
                    log.debug("[discoverServices] region -> c=" + c);

                    ConfigResponse attr = new ConfigResponse();
                    attr.setValue("name", (String) region.get("gemfire.region.name.string"));
                    attr.setValue("path", (String) region.get("gemfire.region.path.string"));
                    attr.setValue("scope", (String) region.get("gemfire.region.scope.string"));
                    attr.setValue("datapolicy", (String) region.get("gemfire.region.datapolicy.string"));
                    attr.setValue("interestpolicy", (String) region.get("gemfire.region.interestpolicy.string"));
                    attr.setValue("diskattrs", (String) region.get("gemfire.region.diskattrs.string"));

                    setMeasurementConfig(service, c);
                    service.setCustomProperties(attr);
                    services.add(service);
                }
            }

            List<Map> gateways = (List) memberDetails.get("gemfire.member.gatewayhub.gateways.collection");
            if (gateways != null) {
                for (Map gateway : gateways) {
                    String id = (String) gateway.get("gemfire.member.gateway.id.string");
                    ServiceResource service = createServiceResource("Gateway");
                    service.setName(memberId + " Gateway " + id);

                    ConfigResponse c = new ConfigResponse();
                    c.setValue("memberID", memberId);
                    c.setValue("gatewayID", id);
                    log.debug("[discoverServices] gateway -> c=" + c);

                    setProductConfig(service, new ConfigResponse());
                    setMeasurementConfig(service, c);
                    services.add(service);
                }
            }

        } catch (Exception ex) {
            throw new PluginException(ex.getMessage(), ex);
        }
        return services;
    }

    abstract boolean isValidMember(Map memberDetails);

    abstract ConfigResponse getAtributtes(Map memberDetails);
}