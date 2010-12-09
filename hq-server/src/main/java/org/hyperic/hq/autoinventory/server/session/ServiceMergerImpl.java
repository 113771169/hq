/**
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 *  "derived work".
 *
 *  Copyright (C) [2009-2010], VMware, Inc.
 *  This file is part of HQ.
 *
 *  HQ is free software; you can redistribute it and/or modify
 *  it under the terms version 2 of the GNU General Public License as
 *  published by the Free Software Foundation. This program is distributed
 *  in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more
 *  details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 *  USA.
 *
 */

package org.hyperic.hq.autoinventory.server.session;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.appdef.server.session.ServiceType;
import org.hyperic.hq.appdef.shared.AIServiceValue;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.CPropManager;
import org.hyperic.hq.appdef.shared.ConfigManager;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ServiceManager;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.AuthzSubjectManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceManager;
import org.hyperic.hq.autoinventory.server.session.RuntimeReportProcessor.ServiceMergeInfo;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.inventory.domain.Resource;
import org.hyperic.hq.measurement.server.session.AgentScheduleSyncZevent;
import org.hyperic.hq.zevents.ZeventEnqueuer;
import org.hyperic.hq.zevents.ZeventManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges in services which have been discovered via runtime AI.
 * 
 * This class also has the responsibility of keeping state about which services
 * are in the queue, waiting to be processed, and notifying the agent that it
 * still needs to get a runtime service scan.
 */
@Component
public class ServiceMergerImpl implements ServiceMerger {

    private final Log log = LogFactory.getLog(ServiceMergerImpl.class);

    private CPropManager cPropManager;
    private ServiceManager serviceManager;
    private ServerManager serverManager;
    private ConfigManager configManager;
    private ResourceManager resourceManager;
    private ZeventEnqueuer zEventManager;
    private AuthzSubjectManager authzSubjectManager;

   

    @Autowired
    public ServiceMergerImpl(CPropManager cPropManager, ServiceManager serviceManager,
                             ServerManager serverManager, ConfigManager configManager,
                             ResourceManager resourceManager,
                             ZeventEnqueuer zEventManager, AuthzSubjectManager authzSubjectManager) {
        this.cPropManager = cPropManager;
        this.serviceManager = serviceManager;
        this.serverManager = serverManager;
        this.configManager = configManager;
        this.resourceManager = resourceManager;
        this.zEventManager = zEventManager;
        this.authzSubjectManager = authzSubjectManager;
    }

    @Transactional
    public void mergeServices(List<ServiceMergeInfo> mergeInfos) throws PermissionException,
        ApplicationException {
        final Set<Resource> updatedResources = new HashSet<Resource>();
        final Set<AppdefEntityID> toSchedule = new HashSet<AppdefEntityID>();
        AuthzSubject subj = authzSubjectManager.getOverlordPojo();
        for (ServiceMergeInfo sInfo : mergeInfos) {
            // this is hacky, but mergeInfos will never be called with multiple
            // subjects
            // and hence the method probably shouldn't be written the way it is
            // anyway.
            subj = sInfo.subject;
            AIServiceValue aiservice = sInfo.aiservice;
            Server server = serverManager.getServerById(sInfo.serverId);

            log.info("Checking for existing service: " + aiservice.getName());

            // this is a propagation of a bug that nobody really runs into.
            // Occurs when a set of services under a server have the same
            // name
            // and therefore the AIID is also the same. In a perfect world
            // the
            // AIIDs will be unique, but there is nothing else that comes
            // from
            // the agent that can uniquely identify a service under a
            // server.
            // The get(0), instead of operating on the whole list, enables
            // us to make the least amount of code changes in a messy code
            // path
            // thus reducing the amount of potential problems.
            final List<Service> tmp = serviceManager.getServicesByAIID(server, aiservice.getName());
            Service service = (tmp.size() > 0) ? (Service) tmp.get(0) : null;
            boolean update = false;

            if (service == null) {
                // CREATE SERVICE
                log.info("Creating new service: " + aiservice.getName());

                String typeName = aiservice.getServiceTypeName();
                ServiceType serviceType = serviceManager.findServiceTypeByName(typeName);
                service = serviceManager.createService(sInfo.subject, server.getId(), serviceType.getId(),
                    aiservice.getName(), aiservice.getDescription(), "");

                log.debug("New service created: " + service);
            } else {
                update = true;
                // UPDATE SERVICE
                log.info("Updating service: " + service.getName());
                final String aiSvcName = aiservice.getName();
                final String svcName = service.getName();
                final String aiid = service.getAutoinventoryIdentifier();
                // if aiid.equals(svcName) this means that the name has
                // not been manually changed. Therefore it is ok to change
                // the current resource name
                if (aiSvcName != null && !aiSvcName.equals(svcName) && aiid.equals(svcName)) {
                    service.setName(aiservice.getName().trim());
                    service.getResource().setName(service.getName());
                }
                if (aiservice.getDescription() != null)
                    service.setDescription(aiservice.getDescription().trim());
            }

            // CONFIGURE SERVICE
            final boolean wasUpdated = configManager.configureResponse(sInfo.subject,service.getEntityId(), aiservice.getProductConfig(),
                aiservice.getMeasurementConfig(), aiservice.getControlConfig(), aiservice
                    .getResponseTimeConfig(), null, false);
            if (update && wasUpdated) {
                updatedResources.add(service.getResource());
            } else {
                // make sure the service's schedule is up to date on the agent
                // side
                toSchedule.add(AppdefUtil.newAppdefEntityId(service.getResource()));
            }

            // SET CUSTOM PROPERTIES FOR SERVICE
            if (aiservice.getCustomProperties() != null) {
                int typeId = service.getServiceType().getId().intValue();
                cPropManager.setConfigResponse(service.getEntityId(), typeId, aiservice
                    .getCustomProperties());
            }
        }
        if (!toSchedule.isEmpty()) {
            resourceManager.resourceHierarchyUpdated(subj, updatedResources);
            zEventManager.enqueueEventAfterCommit(new AgentScheduleSyncZevent(toSchedule));
        }
    }

    public String toString() {
        return "RuntimeAIServiceMerger";
    }

    public void scheduleServiceMerges(final String agentToken,
                                      final List<ServiceMergeInfo> serviceMerges) {
       
        List<MergeServiceReportZevent> evts = new ArrayList<MergeServiceReportZevent>(serviceMerges
            .size());

        for (ServiceMergeInfo sInfo : serviceMerges) {
            if (log.isDebugEnabled()) {
                log.debug("Enqueueing service merge for " + sInfo.aiservice.getName() +
                          " on server id=" + sInfo.serverId);
            }

            evts.add(new MergeServiceReportZevent(sInfo));
        }

        ZeventManager.getInstance().enqueueEventsAfterCommit(evts);
    }

}
