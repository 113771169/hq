/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc.
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

package org.hyperic.hq.autoinventory.server.session;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.CreateException;
import javax.ejb.FinderException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hibernate.Util;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.appdef.server.session.AIAudit;
import org.hyperic.hq.appdef.server.session.AgentManagerImpl;
import org.hyperic.hq.appdef.server.session.CPropManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.ConfigManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.Platform;
import org.hyperic.hq.appdef.server.session.PlatformManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.server.session.ServerManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.appdef.server.session.ServiceManagerEJBImpl;
import org.hyperic.hq.appdef.shared.AIConversionUtil;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIServerExtValue;
import org.hyperic.hq.appdef.shared.AIServerValue;
import org.hyperic.hq.appdef.shared.AIServiceTypeValue;
import org.hyperic.hq.appdef.shared.AIServiceValue;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.CPropManagerLocal;
import org.hyperic.hq.appdef.shared.ConfigManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServerValue;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceTypeFactory;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.AuthzSubjectManagerEJBImpl;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerLocal;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.autoinventory.AutoinventoryException;
import org.hyperic.hq.autoinventory.CompositeRuntimeResourceReport;
import org.hyperic.hq.autoinventory.shared.AutoinventoryManager;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.server.session.Audit;
import org.hyperic.hq.common.server.session.AuditManagerEJBImpl;
import org.hyperic.hq.product.RuntimeResourceReport;
import org.hyperic.hq.product.ServiceType;
import org.hyperic.util.StringUtil;
import org.hyperic.util.pager.PageControl;

public class RuntimeReportProcessor {
    private final Log _log = LogFactory.getLog(RuntimeReportProcessor.class);
    private final AutoinventoryManager
        _aiMgr = AutoinventoryManagerImpl.getOne();
    
    private final PlatformManagerLocal
        _platformMgr = PlatformManagerEJBImpl.getOne();
    
    private final ServerManagerLocal
        _serverMgr = ServerManagerEJBImpl.getOne();
    
    private final ServiceManagerLocal
        _serviceMgr = ServiceManagerEJBImpl.getOne();
    
    private final ConfigManagerLocal
        _configMgr = ConfigManagerEJBImpl.getOne();
    
    private final AuthzSubjectManagerLocal
        _subjectMgr = AuthzSubjectManagerEJBImpl.getOne();
    
    private final CPropManagerLocal
        _cpropMgr = CPropManagerEJBImpl.getOne();
    
    private AuthzSubject _overlord;
    private List<ServiceMergeInfo>         _serviceMerges = new ArrayList<ServiceMergeInfo>();
	private Set<ServiceType> serviceTypeMerges = new HashSet<ServiceType>();
    private String       _agentToken;
    private ServiceTypeFactory serviceTypeFactory = new ServiceTypeFactory();
    
    public RuntimeReportProcessor () {}

    public void processRuntimeReport(AuthzSubject subject, String agentToken,
                                     CompositeRuntimeResourceReport crrr)
        throws AutoinventoryException, CreateException,  PermissionException,
               ValidationException, ApplicationException 
    {
        _overlord   = _subjectMgr.getOverlordPojo();
        _agentToken = agentToken;
        
        Agent agent = AgentManagerImpl.getOne().getAgent(_agentToken);
        Audit audit = AIAudit.newRuntimeImportAudit(agent); 
        boolean pushed = false;
        
        try {
            AuditManagerEJBImpl.getOne().pushContainer(audit);
            pushed = true; 
            _processRuntimeReport(subject, crrr); 
        } finally {
            if (pushed) {
                AuditManagerEJBImpl.getOne().popContainer(false);
            }
        }
    }

    private void _processRuntimeReport(AuthzSubject subject,
                                       CompositeRuntimeResourceReport crrr)
        throws AutoinventoryException, CreateException, PermissionException,
               ValidationException, ApplicationException 
    {
        long startTime = System.currentTimeMillis();

        _log.info("Processing Runtime AI Report: " + crrr.simpleSummary());

        // Before we queue or approve anything, verify that all
        // servers used in the scan both (a) exist in appdef
        // and (b) have runtime-ai turned on.  If we find ones
        // that don't then we don't trust the platform/servers
        // to queue, because we don't know if some of those were
        // reported by servers that shouldn't be doing runtime scans.
        // (we don't keep track of which server reports other platforms
        // and servers)
        RuntimeResourceReport[] serverReports;
        RuntimeResourceReport serverReport;
        AIPlatformValue[] aiplatforms;
        Integer serverId;

        serverReports = crrr.getServerReports();
        Server[] appdefServers = new Server[serverReports.length];
        for (int i = 0; i < serverReports.length; i++) {

            serverReport = serverReports[i];

            // Check that reporting server still exists.
            serverId = new Integer(serverReport.getServerId());
            appdefServers[i] = _serverMgr.getServerById(serverId);
            if (appdefServers[i] == null) {
                _log.error("Error finding existing server: " + serverId);
                turnOffRuntimeDiscovery(subject, serverId);
                continue;
            }

            // Even if it does exist, make sure runtime reporting is turned
            // on for the server
            if (!appdefServers[i].isRuntimeAutodiscovery()) {
                _log.warn("Server reported a runtime report, but " +
                         "autodiscovery should be off, turning off.");
                turnOffRuntimeDiscovery(subject, serverId);
                appdefServers[i] = null;
            }
        }

        // Now, for each server report that had a corresponding appdef server,
        // process that report.
        _log.info("Merging server reports into appdef (server count=" +
                  appdefServers.length + ")");
        for (int i = 0; i < appdefServers.length; i++) {

            if (appdefServers[i] == null)
                continue;
            serverReport = serverReports[i];
            aiplatforms = serverReport.getAIPlatforms();
            if (aiplatforms == null)
                continue;

            _log.info("Merging platforms (platform count=" + aiplatforms.length
                      + ", reported by serverId=" + appdefServers[i].getId() +
                      ") into appdef...");
            for (int j=0; j<aiplatforms.length; j++) {
                if(aiplatforms[j] != null) {
                    if (aiplatforms[j].getAgentToken() == null) {
                        // reassociate agent to the auto discoverred platform
                        // one situation this condition occurs is when
                        // 1. platform is deleted from the ui
                        // 2. agent for that platform is stopped and started
                        //
                        // BTW: there is lot more going on here, then
                        // just setting the agent token. As of today,
                        // inventory rediscovery is supported only if
                        // the "data" directory on the agent is deleted
                        // prior to restarting the agent.  That is, the
                        // supported operation is
                        // 1. platform is deleted from the ui
                        // 2. stop the agent
                        // 3. delete the "data" directory on the agent
                        // 4. start the agent.

                        aiplatforms[j].setAgentToken(_agentToken);
                    }
                    mergePlatformIntoInventory(subject, aiplatforms[j],
                                               appdefServers[i]); 
                    Util.flushCurrentSession();
                } else {
                    _log.error("Runtime Report from server: " +
                               appdefServers[i].getName() +
                               " contained null aiPlatform. Skipping.");
                }
            }
        }
        long endTime = System.currentTimeMillis() - startTime;
        _log.info("Completed processing Runtime AI report in: " + endTime/1000 +
                  " seconds.");
    }

    private void mergePlatformIntoInventory(AuthzSubject subject,
                                            AIPlatformValue aiplatform,
                                            Server reportingServer)
        throws CreateException, PermissionException, ValidationException,
               ApplicationException {

        AIServerValue[] aiservers = aiplatform.getAIServerValues();

        _log.info("Merging platform into appdef: " + aiplatform.getFqdn());

        Platform appdefPlatform = null;
        // checks if platform exists by fqdn, certdn, then ipaddr(s)
        appdefPlatform =
            _platformMgr.getPlatformByAIPlatform(subject, aiplatform);

        if (appdefPlatform == null) {
            // Add the platform
            _log.info("Creating new platform: " + aiplatform);
            appdefPlatform = _platformMgr.createPlatform(subject, aiplatform);
        }
        
        // Else platform already exists, don't update it, only update servers
        // that are within it.
        if (aiservers == null)
            return;

        List appdefServers = new ArrayList(appdefPlatform.getServers());

        for (int i = 0; i < aiservers.length; i++) {
            if(aiservers[i] != null) {
                mergeServerIntoInventory(subject, appdefPlatform, aiplatform,
                                         aiservers[i], appdefServers,
                                         reportingServer);
                Util.flushCurrentSession();
            } else {
                _log.error("Platform: " + appdefPlatform.getName() + 
                          " reported null aiServer. Skipping.");
            }
        }

        // any servers that we haven't handled, we should mark them
        // as AI-zombies.
        for (Iterator it = appdefServers.iterator(); it.hasNext(); ) {
            Server server = (Server) it.next();
            if (server.isWasAutodiscovered())
                _serverMgr.setAutodiscoveryZombie(server, true);
        }
        
        _log.info("Completed Merging platform into appdef: " +
                 aiplatform.getFqdn());
    }
    
	private void updateServiceTypes(AIServerExtValue server, Server foundAppdefServer) {
		final AIServiceTypeValue[] serviceTypes = server.getAiServiceTypes();
		if (serviceTypes != null) {
			for (int i = 0; i < serviceTypes.length; i++) {
				final ServiceType serviceType= serviceTypeFactory.create(serviceTypes[i], foundAppdefServer.getServerType());
				serviceTypeMerges.add(serviceType);
				Util.flushCurrentSession();
			}
		}
	}

    /**
     * @param platform the platform that the server is on
     * @param aiplatform the AIPlatform that is parent to the AI Server
     * @param aiserver the server we're going to merge into inventory
     * @param appdefServers the existing servers on the platform.
     * this method is expected to remove a server from this collection
     * if the aiserver is found amongs the appdefServers.
     * @param reportingServer The server that reported the aiserver.
     */
    private void mergeServerIntoInventory(AuthzSubject subject,
                                          Platform platform,
                                          AIPlatformValue aiplatform,
                                          AIServerValue aiserver,
                                          List appdefServers,
                                          Server reportingServer)
        throws CreateException, PermissionException, ValidationException
    {
        Integer serverTypePK;

        // Does this server exist (by autoinventory identifier) ?
        String appdefServerAIID, aiServerAIID;
        aiServerAIID = aiserver.getAutoinventoryIdentifier();
        Integer aiserverId = aiserver.getId();
        AIServerExtValue aiserverExt = null;
        boolean isPlaceholder = false;
        if (aiserver instanceof AIServerExtValue) {
            aiserverExt = (AIServerExtValue) aiserver;
            isPlaceholder = aiserverExt.getPlaceholder();
        }

        _log.info("Merging Server into inventory: " +
                  " id=" + aiserver.getId() + "," +
                  " placeholder=" + isPlaceholder + "," +
                  " name=" + aiserver.getName() + "," +
                  " AIIdentifier=" + aiserver.getAutoinventoryIdentifier());

        Server server = null;

        for (int i = 0; i < appdefServers.size(); i++) {
            Server appdefServer = (Server) appdefServers.get(i);

            // We can match either on autoinventory identifier, or if
            // this is the reporting server, we can match on its appdef ID
            appdefServerAIID = appdefServer.getAutoinventoryIdentifier();
            if (appdefServerAIID.equals(aiServerAIID) ||
                (aiserverId != null &&
                 aiserverId.equals(reportingServer.getId()) &&
                 aiserverId.equals(appdefServer.getId()))) {
                server = _serverMgr.getServerById(appdefServer.getId());
                _log.info("Found existing server: " +
                         server.getName() + " as match for: " +
                         aiserver.getAutoinventoryIdentifier());
                appdefServerAIID = server.getAutoinventoryIdentifier();
                if (!appdefServerAIID.equals(aiServerAIID)) {
                    _log.info("Setting AIID to existing=" + appdefServerAIID);
                    aiserver.setAutoinventoryIdentifier(appdefServerAIID);
                }
                appdefServers.remove(i);
                break;
            }
        }

        boolean update;

        try {
            if (update = (server != null)) {
                // UPDATE SERVER
                _log.info("Updating server: " + server.getName());

                ServerValue foundAppdefServer = AIConversionUtil
                    .mergeAIServerIntoServer(aiserver, server.getServerValue());
                server = _serverMgr.updateServer(subject, foundAppdefServer);
            } else {
                if (isPlaceholder) {
                    _log.error("Placeholder serverId=" + aiserver.getId() +
                               " not found for platformId=" + platform.getId() +
                               ", fqdn=" + platform.getFqdn());
                    return;
                }
                // CREATE the server
                // replace %serverName% in aisever's name.
                String newServerName =
                    StringUtil.replace(aiserver.getName(), "%serverName%",
                                       reportingServer.getName());

                aiserver.setName(newServerName);
                _log.info("Creating new server: name: " + aiserver.getName() +
                         "AIIdentifier: " +
                         aiserver.getAutoinventoryIdentifier());
                ServerValue foundAppdefServer
                    = AIConversionUtil.convertAIServerToServer(aiserver, 
                                                               _serverMgr);
            
                foundAppdefServer.setWasAutodiscovered(true);

                serverTypePK = foundAppdefServer.getServerType().getId();
                
                // The server will be owned by whomever owns the platform
                AuthzSubject serverOwner = platform.getResource().getOwner();
                Integer platformPK = platform.getId();
                server = _serverMgr.createServer(serverOwner,
                                                 platformPK,
                                                 serverTypePK,
                                                 foundAppdefServer);

                _log.info("New server created: " + foundAppdefServer.getName() +
                          " (id=" + server.getId() + ")");
            }
        } catch (ApplicationException e) {
            _log.error("Failed to merge server: " + aiserver, e);
            _log.info("Server: " + aiserver + " will be skipped.");
            return;
        } catch (FinderException e) {
            _log.error("Failed to merge server: " + aiserver, e);
            _log.info("Server: " + aiserver + " will be skipped.");
            return;
        }

        // Only update the server and its config if it is not
        // a placeholder.  A placeholder is an AIServerExtValue that
        // exists solely to hold services underneath it.

        if (!isPlaceholder) {
            // CONFIGURE SERVER
            try {
                // Configure resource, telling the config manager to send
                // an update event if this resource has been updated.
                _configMgr.configureResponse(subject,
                                             server.getConfigResponse(),
                                             server.getEntityId(),
                                             aiserver.getProductConfig(),
                                             aiserver.getMeasurementConfig(),
                                             aiserver.getControlConfig(),
                                             null, //RT config
                                             null,
                                             update,
                                             false);
            } catch (Exception e) {
                _log.error("Error configuring server: " + server.getId() +
                           ": " + e, e);
            }
        }

        //setCustomProperties regardless if the server is a placeholder or not.
        //if cprops == null, this is a no-op.  else, JBoss for example will get
        //the majority of its cprops via JMX, which it doesnt use until
        // runtime-ai
        try {
            // SET CUSTOM PROPERTIES FOR SERVER
            int typeId = server.getServerType().getId().intValue();
            _cpropMgr.setConfigResponse(server.getEntityId(), typeId,
                                       aiserver.getCustomProperties());
        } catch (Exception e) {
            _log.warn("Error setting server custom properties: " + e, e);
        }

        if (aiserverExt != null) {

            _log.info("Updating services for server: " + server.getName());
            
        	updateServiceTypes(aiserverExt, server);

            List appdefServices;

            // ServerValue.getServiceValues not working here for some reason,
            // get the services explicitly from the ServiceManager.
            try {
                appdefServices = 
                    _serviceMgr.getServicesByServer(subject, server.getId(),
                                                    PageControl.PAGE_ALL);
            } catch (Exception e) {
                appdefServices = new ArrayList();
            }

            List aiServices = aiserverExt.getAIServiceValuesAsList();

            // Change the service names if they require expansion. 
            for (Iterator i=aiServices.iterator(); i.hasNext(); ) {
                AIServiceValue aiSvc = (AIServiceValue)i.next();
                String newName = StringUtil.replace(aiSvc.getName(),
                                                    "%serverName%",
                                                    server.getName());
                aiSvc.setName(newName);
            }

            String fqdn = aiplatform.getName() == null ?
                          aiplatform.getFqdn() : aiplatform.getName();
            
            // Filter out and mark zombie services
            for (Iterator i=appdefServices.iterator(); i.hasNext(); ) {
                ServiceValue tmp = (ServiceValue)i.next();
                final Service service = _serviceMgr.getServiceById(tmp.getId());
                if (service == null || service.getResource() == null ||
                    service.getResource().isInAsyncDeleteState()) {
                    continue;
                }
                final String aiid = service.getAutoinventoryIdentifier();
                boolean found = false;
                
                AIServiceValue aiSvc = null;
                for (final Iterator j=aiServices.iterator(); j.hasNext(); ) {
                    aiSvc = (AIServiceValue) j.next();
                    final String ainame = aiSvc.getName();
                    if (found = aiid.equals(ainame)) {
                        break;
                    } else if (aiid.startsWith(fqdn)) {
                       // Get rid of the FQDN
                       final String subname = ainame.substring(fqdn.length());
                       if (found = aiid.endsWith(subname)) {
                           break;
                       }
                    }
                }
                
                if (found) {
                    // Update name if FQDN changed
                    final String svcName = service.getName();
                    // only change the name if it hasn't been changed already
                    // for example if !svcName.equals(aiid): this means that
                    // the user has explicitly change the service's name
                    if (aiSvc != null && svcName.equals(aiid)) {
                        service.setName(aiSvc.getName());
                    }
                    // this means that the fqdn changed
                    if (aiSvc != null && !aiid.equals(aiSvc.getName())) {
                        service.setAutoinventoryIdentifier(aiSvc.getName());
                    }
                } else {
                    _log.info("Service id=" + service.getId() + " name=" + 
                              service.getName() + " has become a zombie");
                    _serviceMgr.updateServiceZombieStatus(
                        _overlord, service, true); 
                }
            }
            
            for (Iterator i = aiServices.iterator(); i.hasNext();) {
                AIServiceValue aiService = (AIServiceValue)i.next();
                ServiceMergeInfo sInfo = new ServiceMergeInfo();
                sInfo.subject         = subject;
                sInfo.serverId        = server.getId();
                sInfo.aiservice       = aiService;
                sInfo.agentToken      = _agentToken;
                _serviceMerges.add(sInfo);
                Util.flushCurrentSession();
            }
        }
        _log.info("Completed merging server: " + reportingServer.getName() +
                 " into inventory");
    }

    public static class ServiceMergeInfo {
        public AuthzSubject   subject;
        public Integer        serverId;
        public AIServiceValue aiservice;
        public String         agentToken;
    }
    
    public List<ServiceMergeInfo> getServiceMerges() {
        return _serviceMerges;
    }
    
    public Set<ServiceType> getServiceTypeMerges() {
    	return serviceTypeMerges;
    }

    private boolean turnOffRuntimeDiscovery(AuthzSubject subject,
                                            Integer serverId) 
    {
        AppdefEntityID aid = AppdefEntityID.newServerID(serverId);
        _log.info("Disabling RuntimeDiscovery for server: " + serverId);
        try {
            _aiMgr.turnOffRuntimeDiscovery(subject, aid, _agentToken);
            return true;
        } catch (Exception e) {
            _log.error("Error turning off runtime scans for server: " +
                       serverId, e);
            return false;
        }
    }
}
