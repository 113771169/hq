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

package org.hyperic.hq.autoinventory.server.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ejb.CreateException;
import javax.ejb.FinderException;

import org.hyperic.hq.appdef.shared.AIConversionUtil;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIServerExtValue;
import org.hyperic.hq.appdef.shared.AIServerValue;
import org.hyperic.hq.appdef.shared.AIServiceValue;
import org.hyperic.hq.appdef.shared.AppdefDuplicateNameException;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.CPropManagerLocal;
import org.hyperic.hq.appdef.shared.ConfigManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerLightValue;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.appdef.shared.ServerValue;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceNotFoundException;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerLocal;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.autoinventory.AutoinventoryException;
import org.hyperic.hq.autoinventory.CompositeRuntimeResourceReport;
import org.hyperic.hq.autoinventory.shared.AutoinventoryManagerLocal;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.product.RuntimeResourceReport;
import org.hyperic.util.StringUtil;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.hibernate.Util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class RuntimeReportProcessor {

    private Log log = LogFactory.getLog(RuntimeReportProcessor.class);
    
    public RuntimeReportProcessor () {}

    public void processRuntimeReport ( AuthzSubjectValue subject,
                                       String agentToken,
                                       CompositeRuntimeResourceReport crrr, 
                                       AutoinventoryManagerLocal aiMgr,
                                       PlatformManagerLocal platformMgr,
                                       ServerManagerLocal serverMgr,
                                       ServiceManagerLocal serviceMgr,
                                       ConfigManagerLocal configMgr,
                                       CPropManagerLocal cpropMgr,
                                       AuthzSubjectManagerLocal subjectMgr) 
        throws AutoinventoryException, CreateException, 
               PermissionException, ValidationException, 
               ApplicationException {
        long startTime = System.currentTimeMillis();
        boolean isDebug = log.isDebugEnabled();
        log.info("Processing Runtime AI Report: " + crrr.simpleSummary());

        // Before we queue or approve anything, verify that all
        // servers used in the scan both (a) exist in appdef
        // and (b) have runtime-ai turned on.  If we find ones
        // that don't then we don't trust the platform/servers
        // to queue, because we don't know if some of those were
        // reported by servers that shouldn't be doing runtime scans.
        // (we don't keep track of which server reports other platforms
        // and servers)
        int i, j;
        ServerValue[] appdefServers = null;
        RuntimeResourceReport[] serverReports;
        RuntimeResourceReport serverReport;
        AIPlatformValue[] aiplatforms;
        Integer serverId;

        serverReports = crrr.getServerReports();
        appdefServers = new ServerValue[serverReports.length];
        for (i=0; i<serverReports.length; i++) {

            serverReport = serverReports[i];

            // Make sure the server that generated this report
            // still exists in appdef.
            serverId = new Integer(serverReport.getServerId());
            if (isDebug) {
                log.debug("Looking up server using serverReport.getServerId=" +
                          serverReport.getServerId());
            }

            try {
                appdefServers[i]
                    = serverMgr.findServerById(subject, serverId);
                log.info("Found REPORTING appdef server: " + appdefServers[i]);
            } catch (ServerNotFoundException e) {
                // OK, it doesn't exist, that's bad.
                log.error("Error finding existing server: " + serverId);
                turnOffRuntimeDiscovery(subject, serverId, aiMgr, 
                                        agentToken);
                appdefServers[i] = null;
                continue;
            }

            // Even if it does exist, make sure runtime reporting is turned
            // on for the server
            if ( !appdefServers[i].getRuntimeAutodiscovery() ) {
                log.warn("Server reported a runtime report, but "
                         + "autodiscovery should be off, turning off.");
                turnOffRuntimeDiscovery(subject, serverId, aiMgr, 
                                        agentToken);
                appdefServers[i] = null;
                continue;
            }
        }

        // Now, for each server report that had a corresponding appdef server,
        // process that report.
        log.info("Merging server reports into appdef "
                + "(server count=" + appdefServers.length+ ")");
        for (i=0; i<appdefServers.length; i++) {

            if (appdefServers[i] == null) continue;
            serverReport = serverReports[i];
            aiplatforms = serverReport.getAIPlatforms();
            if (aiplatforms == null) continue;

            log.info("Merging platforms "
                    + "(platform count=" + aiplatforms.length
                    + ", reported by serverId="+appdefServers[i].getId()+") into appdef...");
            for (j=0; j<aiplatforms.length; j++) {
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

                        aiplatforms[j].setAgentToken(agentToken);
                    }
                    mergePlatformIntoInventory(subject, aiplatforms[j],
                                               appdefServers[i], 
                                               platformMgr,
                                               serverMgr,
                                               serviceMgr,
                                               configMgr,
                                               cpropMgr,
                                               subjectMgr);
                    Util.flushCurrentSession();
                } else {
                    log.error("Runtime Report from server: " + appdefServers[i].getName() 
                            + " contained null aiPlatform. Skipping.");
                }
            }
        }
        long endTime = System.currentTimeMillis() - startTime;
        log.info("Completed processing Runtime AI report in: " + endTime/1000 + "secs");
    }

    private void mergePlatformIntoInventory ( AuthzSubjectValue subject,
                                              AIPlatformValue aiplatform,
                                              ServerValue reportingServer,
                                              PlatformManagerLocal platformMgr,
                                              ServerManagerLocal serverMgr,
                                              ServiceManagerLocal serviceMgr,
                                              ConfigManagerLocal configMgr,
                                              CPropManagerLocal cpropMgr,
                                              AuthzSubjectManagerLocal subjectMgr )
        throws CreateException, PermissionException, ValidationException,
               ApplicationException {

        AIServerValue[] aiservers = aiplatform.getAIServerValues();
        PlatformValue appdefPlatform;
        ServerLightValue[] appdefServers;
        List appdefServerList;
        ServerLightValue appdefServer;
        int i;
        boolean isDebug = log.isDebugEnabled();

        if (isDebug) {
            log.debug("Merging platform: " + aiplatform);
        }
        else {
            log.info("Merging platform into appdef: " + aiplatform.getFqdn());
        }

        // Does this platform exist (by fqdn) ?
        String fqdn = aiplatform.getFqdn();
        try {
            appdefPlatform = platformMgr.getPlatformByFqdn(subject, fqdn);
            if (isDebug) {
                log.debug("Platform : " + appdefPlatform.getFqdn() +
                          " was found in inventory.");
            }
        } catch (PlatformNotFoundException e) {
            // Platform doesn't exist by fqdn, so let's try by IP address.
            // This is needed for servers like weblogic, which report platforms 
            // for node servers with an IP address, and not an FQDN.
            PageList platforms = platformMgr.findPlatformsByIpAddr(subject, 
                                                                   fqdn, null);
            if (platforms.size() == 1) {
                appdefPlatform = (PlatformValue) platforms.get(0);

            } else if (platforms.size() > 1) {
                log.warn("RRP: multiple platforms have IP address " + fqdn 
                         + ", could not determine a definitive platform");
                appdefPlatform = null;

            } else {
                log.warn("RRP: could not find a platform with FQDN or IP "
                         + "address that matched: " + fqdn);
                appdefPlatform = null;
            }
        }

        if (appdefPlatform == null) {
            // Add the platform
            log.info("Creating new platform: " + aiplatform);
            try {
                Integer pk = platformMgr.createPlatform(subject, aiplatform);
                appdefPlatform = platformMgr.getPlatformById(subject, pk);
            } catch (PlatformNotFoundException e) {
                // should never happen
                log.fatal("Failed to find platform we just created!", e);
                throw new SystemException("Unable to find the platform " + 
                                             " we just created", e);
            }

        } else {
            // Platform already exists, don't update it, only update servers
            // that are within it.
        }

        appdefServers = appdefPlatform.getServerValues();
        appdefServerList = new ArrayList();
        appdefServerList.addAll(Arrays.asList(appdefServers));

        if (aiservers == null) return;
        for (i=0; i<aiservers.length; i++) {
            if(aiservers[i] != null) {
                mergeServerIntoInventory(subject, appdefPlatform, aiservers[i],
                                         appdefServerList, reportingServer,
                                         serverMgr, serviceMgr, configMgr, 
                                         cpropMgr, subjectMgr);
                Util.flushCurrentSession();
            } else {
                log.error("Platform: " + appdefPlatform.getName() + 
                          " reported null aiServer. Skipping.");
            }
        }

        // any servers that we haven't handled, we should mark them
        // as AI-zombies.
        for (i=0; i<appdefServerList.size(); i++) {
            appdefServer = (ServerLightValue) appdefServerList.get(i);

            // Annoying - serverMgr doesn't support updateServer on 
            // ServerLightValue objects, so we lookup the full value object.
            ServerValue serverValue = null;
            try {
                serverValue = serverMgr.getServerById(subject,
                                                      appdefServer.getId());
                // server zombies are now supported....
                if (serverValue.getWasAutodiscovered()) {
                    serverValue.setAutodiscoveryZombie(true);
                    serverMgr.updateServer(subject, serverValue);
                }
            } catch (AppdefDuplicateNameException e) {
                // Change this to a warning, per bug 8259.
                log.warn("RuntimeReportProcessor: Error updating server: " 
                         + serverValue + ": " + e.getMessage());
                continue;
            } catch (UpdateException e) {
                log.error("RRP: Error updating server: " + serverValue, e);
                continue;
            } catch (ServerNotFoundException e) {
                log.error("RRP: Error updating server: " + serverValue, e);
                continue;
            }
        }
        log.info("Completed Merging platform into appdef: " + aiplatform.getFqdn());
    }

    /**
     * @param platform the platform that the server is on
     * @param aiserver the server we're going to merge into inventory
     * @param appdefServers the existing servers on the platform.
     * this method is expected to remove a server from this collection
     * if the aiserver is found amongs the appdefServers.
     * @param reportingServer The server that reported the aiserver.
     */
    private void mergeServerIntoInventory ( AuthzSubjectValue subject,
                                            PlatformValue platform,
                                            AIServerValue aiserver,
                                            List appdefServers,
                                            ServerValue reportingServer,
                                            ServerManagerLocal serverMgr,
                                            ServiceManagerLocal serviceMgr,
                                            ConfigManagerLocal configMgr,
                                            CPropManagerLocal cpropMgr,
                                            AuthzSubjectManagerLocal subjectMgr )
        throws CreateException, PermissionException, ValidationException {

        int i;
        ServerLightValue appdefServerLight;
        ServerValue foundAppdefServer = null;
        Integer serverTypePK;

        // Does this server exist (by autoinventory identifier) ?
        String appdefServerAIID, aiServerAIID;
        aiServerAIID = aiserver.getAutoinventoryIdentifier();
        Integer aiserverId = aiserver.getId();
        boolean isDebug = log.isDebugEnabled();

        log.info("Merging Server into inventory: name: "
                + aiserver.getName() + " AIIdentifier: "
                + aiserver.getAutoinventoryIdentifier());
        for (i=0; i<appdefServers.size(); i++) {
            appdefServerLight = (ServerLightValue) appdefServers.get(i);

            // We can match either on autoinventory identifier, or if
            // this is the reporting server, we can match on its appdef ID
            appdefServerAIID = appdefServerLight.getAutoinventoryIdentifier();
            if ( appdefServerAIID.equals(aiServerAIID) ||
                 (aiserverId != null &&
                  aiserverId.equals(reportingServer.getId()) &&
                  aiserverId.equals(appdefServerLight.getId())) ) {
                try {
                    foundAppdefServer
                        = serverMgr.getServerById(subject,
                                                  appdefServerLight.getId());
                    log.info("Found existing server: " + foundAppdefServer.getName() 
                            + " as match for: " + aiserver.getAutoinventoryIdentifier());
                    appdefServerAIID =
                        foundAppdefServer.getAutoinventoryIdentifier();
                    if (!appdefServerAIID.equals(aiServerAIID)) {
                        log.info("Setting AIID to existing=" +
                                 appdefServerAIID);
                        aiserver.setAutoinventoryIdentifier(appdefServerAIID);
                    }
                } catch (ServerNotFoundException e) {
                    log.error("RRP: error finding server: " + appdefServerLight);
                    throw new SystemException(e);
                }
                appdefServers.remove(i);
                break;
            }
        }

        boolean isCreate;

        try {
            if (foundAppdefServer == null) {
                isCreate = true;
                // CREATE the server
                // replace %serverName% in aisever's name.
                String newServerName = StringUtil.replace(aiserver.getName(),
                                                          "%serverName%",
                                                          reportingServer.getName());
                aiserver.setName(newServerName);
                if (isDebug) {
                    log.debug("Creating new server: " + aiserver);
                }
                else {
                    log.info("Creating new server: name: " + aiserver.getName() +
                             " AIIdentifier: " + aiserver.getAutoinventoryIdentifier());
                }
                foundAppdefServer
                    = AIConversionUtil.convertAIServerToServer(aiserver, 
                                                               serverMgr);
            
                foundAppdefServer.setWasAutodiscovered(true);
                if (isDebug) {
                    log.debug("Converted aiserver into: " + foundAppdefServer);
                }
                serverTypePK
                    = foundAppdefServer.getServerType().getId();
                
                // The server will be owned by whomever owns the platform
                String serverOwnerName = platform.getOwner();
                AuthzSubjectValue serverOwner
                    = subjectMgr.findSubjectByName(subject, serverOwnerName);
                Integer platformPK = platform.getId();
                Integer pk = serverMgr.createServer(serverOwner,
                                                    platformPK,
                                                    serverTypePK,
                                                    foundAppdefServer);
                try {
                    foundAppdefServer = serverMgr.getServerById(serverOwner, pk);
                } catch (ServerNotFoundException e) {
                    log.fatal("Could not find server we just created", e);
                    throw new SystemException("Could not find server we"
                                                 + " just created", e);
                }
                if (isDebug) {
                    log.debug("New server created: " + foundAppdefServer);
                }
                else {
                    log.info("New server created: " + foundAppdefServer.getName() +
                             " (newserverid=" + foundAppdefServer.getId() + ")");
                }
            } else {
                isCreate = false;
                // UPDATE SERVER
                if (isDebug) {
                    log.debug("Updating server: " + foundAppdefServer);                    
                }
                else {
                    log.info("Updating server: " + foundAppdefServer.getName());
                }

                foundAppdefServer
                    = AIConversionUtil.mergeAIServerIntoServer(aiserver,
                                                               foundAppdefServer);
                serverMgr.updateServer(subject, foundAppdefServer);
                if (isDebug) {
                    log.debug("RRP: server updated: " + foundAppdefServer);
                }
            }
        } catch (ApplicationException e) {
            log.error("RRP: Failed to merge server: " + aiserver, e);
            log.info("RRP: Server: " + aiserver + " will be skipped.");
            return;
        } catch (FinderException e) {
            log.error("RRP: Failed to merge server: " + aiserver, e);
            log.info("RRP: Server: " + aiserver + " will be skipped.");
            return;
        }

        // Only update the server and its config if it is not
        // a placeholder.  A placeholder is an AIServerExtValue that
        // exists solely to hold services underneath it.
        AIServerExtValue aiserverExt = null;
        if (aiserver instanceof AIServerExtValue) {
            aiserverExt = (AIServerExtValue) aiserver;
        }

        if (!isPlaceholder(aiserverExt)) {             
            // CONFIGURE SERVER
            try {
                if (isDebug) {
                    log.debug("Configuring server " + foundAppdefServer.getName());
                }
                AIConversionUtil.configureServer(subject,
                                                 foundAppdefServer.getId(),
                                                 aiserver.getProductConfig(),
                                                 aiserver.getMeasurementConfig(),
                                                 aiserver.getControlConfig(),
                                                 null,
                                                 true, //isCreate,
                                                 configMgr);
            } catch (Exception e) {
                log.error("Error configuring server: " 
                          + foundAppdefServer.getId() + ": " + e, e);
            }
        }

        //setCustomProperties regardless if the server is a placeholder or not.
        //if cprops == null, this is a no-op.  else, JBoss for example will get
        //the majority of its cprops via JMX, which it doesnt use until runtime-ai
        try {
            // SET CUSTOM PROPERTIES FOR SERVER
            if (isDebug) {
                log.debug("Setting Custom Properties for server: " + 
                          foundAppdefServer.getName());
            }
            int typeId =
                foundAppdefServer.getServerType().getId().intValue();
            cpropMgr.setConfigResponse(foundAppdefServer.getEntityId(),
                                       typeId,
                                       aiserver.getCustomProperties());
        } catch (Exception e) {
            log.warn("Error setting server custom properties: " + e, e);
        }

        if (aiserverExt != null) {

            log.info("Updating services for server: " + foundAppdefServer.getName());

            AIServiceValue[] aiservices;
            List appdefServices;

            aiservices
                = ((AIServerExtValue) aiserver).getAIServiceValues();
            if (aiservices != null) {
                // ServerValue.getServiceValues not working here for some reason,
                // get the services explicitly from the ServiceManager.
                try {
                    appdefServices = 
                        serviceMgr.getServicesByServer(subject,
                                                       foundAppdefServer.getId(),
                                                       PageControl.PAGE_ALL);
                } catch (Exception e) {
                    appdefServices = new ArrayList();
                }

                for (i=0; i<aiservices.length; i++) {
                    if(aiservices[i] != null) {
                        mergeServiceIntoInventory(subject, foundAppdefServer, aiservices[i],
                                                  appdefServices, reportingServer,
                                                  serviceMgr, configMgr,
                                                  cpropMgr, subjectMgr);
                        Util.flushCurrentSession();
                    } else {
                        log.error("Server: " + reportingServer.getName() + 
                                  " reported null aiservice. Skipping.");
                    }
                }

                // any services that we haven't handled, we should mark them
                // as AI-zombies.
                ServiceValue appdefService;
                for (i=0; i<appdefServices.size(); i++) {
                    appdefService = (ServiceValue) appdefServices.get(i);
                    try {
                        appdefService.setAutodiscoveryZombie(true);
                        serviceMgr.updateService(subject, appdefService);
                        if (isDebug) {
                            log.debug("Service marked as zombie: " +
                                      appdefService);
                        }
                        Util.flushCurrentSession();
                    } catch (ApplicationException e) {
                        log.error("RRP: Error marking service as zombie: " +
                                  appdefService.getName(), e);
                        continue;
                    } 
                }
            }
        }
        log.info("Completed merging server: " + reportingServer.getName() + " into inventory");
    }

    private void mergeServiceIntoInventory( AuthzSubjectValue subject, 
                                            ServerValue server,
                                            AIServiceValue aiservice,
                                            List appdefServices,
                                            ServerValue reportingServer,
                                            ServiceManagerLocal serviceMgr,
                                            ConfigManagerLocal configMgr,
                                            CPropManagerLocal cpropMgr,
                                            AuthzSubjectManagerLocal subjectMgr )
        throws CreateException, PermissionException, ValidationException {

        int i;
        ServiceValue appdefService;
        ServiceValue foundAppdefService = null;
        boolean isDebug = log.isDebugEnabled();

        // Fix bug 6773 - replace %serverName% in aisever's name.
        String newServerName = StringUtil.replace(aiservice.getName(),
                                                  "%serverName%",
                                                  server.getName());
        aiservice.setName(newServerName);

        if (isDebug) {
            log.debug("Merging Service into inventory: " + aiservice);
        }

        // Does this service exist (by name) ?
        for (i=0; i<appdefServices.size(); i++) {
            appdefService = (ServiceValue) appdefServices.get(i);
            if (appdefService.getName().equals(aiservice.getName())) {
                foundAppdefService = appdefService;
                if (isDebug) {
                    log.debug("Found existing service: " + foundAppdefService);
                }
                // Remove from list so all that's left are zombies
                appdefServices.remove(i);
                break;
            }
        }

        boolean isCreate;

        try {
            if (foundAppdefService == null) {
                isCreate = true;
                // CREATE SERVICE
                if (isDebug) {
                    log.debug("Creating new service: " + aiservice);
                }
                else {
                    log.info("Creating new service: " + aiservice.getName());
                }

                try {
                    foundAppdefService
                        = AIConversionUtil.convertAIServiceToService(aiservice,
                                                                     serviceMgr);
                } catch (FinderException e) {
                    // Most likely a plugin bug
                    log.error("Unable to find reported resource type: " + 
                              aiservice.getServiceTypeName() + " for " +
                              "resource: " + aiservice.getName() + ", ignoring");
                    return;
                }

                // let's see if we should enable RT collection
                // if there's a config on the inbound service object, we'll set rt to true
                if(isRtEnabled(aiservice)) {
                    foundAppdefService.setServiceRt(true);
                }
                Integer serviceTypePK
                    = foundAppdefService.getServiceType().getId();
                String serviceOwnerName = server.getOwner();
                AuthzSubjectValue serviceOwner
                    = subjectMgr.findSubjectByName(subject, serviceOwnerName);
                Integer pk  = serviceMgr.createService(serviceOwner,
                                                       server.getId(),
                                                       serviceTypePK,
                                                       foundAppdefService);
                try {
                    foundAppdefService = serviceMgr.getServiceById(serviceOwner,
                                                               pk);
                } catch (ServiceNotFoundException e) {
                    log.fatal("Unable to find service we just created.", e);
                    throw new SystemException("Unable to find service we "
                                                 + "just created", e);
                }
                log.debug("RRP: new service created: " + foundAppdefService);
            } else {
                isCreate = false;
                // UPDATE SERVICE
                if (isDebug) {
                    log.debug("Updating service: " + foundAppdefService);
                }
                else {
                    log.info("Updating service: " + foundAppdefService.getName());
                }

                foundAppdefService
                    = AIConversionUtil.mergeAIServiceIntoService(aiservice,
                                                                 foundAppdefService);
                // and while we're at it, let's see if we should enable RT collection
                // if there's a config on the inbound service object, we'll set rt to true
                if(isRtEnabled(aiservice)) {
                    foundAppdefService.setServiceRt(true);
                }
                serviceMgr.updateService(subject, foundAppdefService);
                if (isDebug) {
                    log.debug("Service updated: " + foundAppdefService);
                }
            }
            
            // CONFIGURE SERVICE
            if (isDebug) {
                log.debug("Configuring service: " + aiservice.getName());
            }
            AIConversionUtil.configureService(subject,
                                              foundAppdefService.getId(),
                                              aiservice.getProductConfig(),
                                              aiservice.getMeasurementConfig(),
                                              aiservice.getControlConfig(),
                                              aiservice.getResponseTimeConfig(),
                                              null,
                                              true, //isCreate,
                                              configMgr);
            
            // SET CUSTOM PROPERTIES FOR SERVICE
            if (aiservice.getCustomProperties() != null) {
                if (isDebug) {
                    log.debug("Setting custom properties for service: " +
                              aiservice.getName());
                }
                int typeId =
                    foundAppdefService.getServiceType().getId().intValue();
                cpropMgr.setConfigResponse(foundAppdefService.getEntityId(),
                                           typeId,
                                           aiservice.getCustomProperties());            
            }
        } catch (ApplicationException e) {
            log.error("RRP: failed to merge service: " + aiservice, e);
            log.info("RRP: Skipping merging of service: " + aiservice);
            return;
        } catch (FinderException e) {
            log.error("RRP: failed to merge service: " + aiservice, e);
            log.info("RRP: Skipping merging of service: " + aiservice);
            return;
        }
        if (isDebug) {
            log.debug("Completed merging service: " +
                      aiservice.getName() + " into inventory.");
        }
    }

    private boolean turnOffRuntimeDiscovery ( AuthzSubjectValue subject,
                                              Integer serverId,
                                              AutoinventoryManagerLocal aiMgr,
                                              String agentToken ) {
        AppdefEntityID aid = new AppdefEntityID(
            AppdefEntityConstants.APPDEF_TYPE_SERVER, serverId);
        log.info("Disabling RuntimeDiscovery for server: " + serverId);
        try {
            aiMgr.turnOffRuntimeDiscovery(subject, aid, agentToken);
            return true;
            
        } catch (Exception e) {
            log.error("RRP: Error turning off runtime scans for "
                      + "server: " + serverId, e);
            return false;
        }
    }

    private boolean isPlaceholder (AIServerExtValue aiserverExt) {
        return (aiserverExt != null && aiserverExt.getPlaceholder());
    }
    
    private boolean isRtEnabled(AIServiceValue aiSvc) {
        return (aiSvc.getResponseTimeConfig() != null && aiSvc.getResponseTimeConfig().length > 0);
    }
}
