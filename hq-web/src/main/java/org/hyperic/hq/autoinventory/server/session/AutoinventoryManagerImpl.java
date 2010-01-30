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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.appdef.server.session.AgentCreateCallback;
import org.hyperic.hq.appdef.server.session.AppdefResource;
import org.hyperic.hq.appdef.server.session.ResourceUpdatedZevent;
import org.hyperic.hq.appdef.server.session.ResourceZevent;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.shared.AIAppdefResourceValue;
import org.hyperic.hq.appdef.shared.AIIpValue;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQueueConstants;
import org.hyperic.hq.appdef.shared.AIQueueManager;
import org.hyperic.hq.appdef.shared.AIServerValue;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.ConfigFetchException;
import org.hyperic.hq.appdef.shared.ConfigManager;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ServerTypeValue;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.shared.ResourceDeletedException;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.authz.shared.ResourceManager;
import org.hyperic.hq.autoinventory.AIHistory;
import org.hyperic.hq.autoinventory.AIPlatform;
import org.hyperic.hq.autoinventory.AutoinventoryException;
import org.hyperic.hq.autoinventory.CompositeRuntimeResourceReport;
import org.hyperic.hq.autoinventory.DuplicateAIScanNameException;
import org.hyperic.hq.autoinventory.ScanConfigurationCore;
import org.hyperic.hq.autoinventory.ScanState;
import org.hyperic.hq.autoinventory.ScanStateCore;
import org.hyperic.hq.autoinventory.ServerSignature;
import org.hyperic.hq.autoinventory.agent.client.AICommandsClient;
import org.hyperic.hq.autoinventory.agent.client.AICommandsClientFactory;
import org.hyperic.hq.autoinventory.shared.AIScheduleManager;
import org.hyperic.hq.autoinventory.shared.AutoinventoryManager;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.NotFoundException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.dao.AIHistoryDAO;
import org.hyperic.hq.dao.AIPlatformDAO;
import org.hyperic.hq.product.AutoinventoryPluginManager;
import org.hyperic.hq.product.GenericPlugin;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.PluginNotFoundException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.product.ServerDetector;
import org.hyperic.hq.product.shared.ProductManager;
import org.hyperic.hq.scheduler.ScheduleValue;
import org.hyperic.hq.scheduler.ScheduleWillNeverFireException;
import org.hyperic.util.StringUtil;
import org.hyperic.util.config.ConfigResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * This class is responsible for managing Autoinventory objects in autoinventory
 * and their relationships
 */
@org.springframework.stereotype.Service
public class AutoinventoryManagerImpl implements AutoinventoryManager {
    private Log log = LogFactory.getLog(AutoinventoryManagerImpl.class.getName());

    private AutoinventoryPluginManager aiPluginManager;
    private AIScheduleManager aiScheduleManager;

    private AgentReportStatusDAO agentReportStatusDao;
    private AIHistoryDAO aiHistoryDao;
    private AIPlatformDAO aiPlatformDao;

    private ProductManager productManager;
    private ServerManager serverManager;
    private ResourceManager resourceManager;
    private ConfigManager configManager;

    private AuthzSubjectManager authzSubjectManager;
    private AIQueueManager aiQueueManager;
    private PermissionManager permissionManager;
    private HQApp hqApp;
    private AICommandsClientFactory aiCommandsClientFactory;
    private ServiceMerger serviceMerger;
    private RuntimePlatformAndServerMerger runtimePlatformAndServerMerger;

    @Autowired
    public AutoinventoryManagerImpl(AgentReportStatusDAO agentReportStatusDao, AIHistoryDAO aiHistoryDao,
                                    AIPlatformDAO aiPlatformDao, ProductManager productManager,
                                    ServerManager serverManager, AIScheduleManager aiScheduleManager,
                                    ResourceManager resourceManager, ConfigManager configManager,
                                    AuthzSubjectManager authzSubjectManager, AIQueueManager aiQueueManager,
                                    PermissionManager permissionManager, HQApp hqApp,
                                    AICommandsClientFactory aiCommandsClientFactory, ServiceMerger serviceMerger,
                                    RuntimePlatformAndServerMerger runtimePlatformAndServerMerger) {
        this.agentReportStatusDao = agentReportStatusDao;
        this.aiHistoryDao = aiHistoryDao;
        this.aiPlatformDao = aiPlatformDao;
        this.productManager = productManager;
        this.serverManager = serverManager;
        this.aiScheduleManager = aiScheduleManager;
        this.resourceManager = resourceManager;
        this.configManager = configManager;
        this.authzSubjectManager = authzSubjectManager;
        this.aiQueueManager = aiQueueManager;
        this.permissionManager = permissionManager;
        this.hqApp = hqApp;
        this.aiCommandsClientFactory = aiCommandsClientFactory;
        this.serviceMerger = serviceMerger;
        this.runtimePlatformAndServerMerger = runtimePlatformAndServerMerger;
    }

    /**
     * Get server signatures for a set of servertypes.
     * @param serverTypes A List of ServerTypeValue objects representing the
     *        server types to get signatures for. If this is null, all server
     *        signatures are returned.
     * @return A Map, where the keys are the names of the ServerTypeValues, and
     *         the values are the ServerSignature objects.
     */
    @Transactional
    public Map<String, ServerSignature> getServerSignatures(AuthzSubject subject, List<ServerTypeValue> serverTypes)
        throws AutoinventoryException {
        // Plug server type names into a map for quick retrieval
        HashMap<String, ServerTypeValue> stNames = null;
        if (serverTypes != null) {
            stNames = new HashMap<String, ServerTypeValue>();
            ServerTypeValue stValue;
            for (int i = 0; i < serverTypes.size(); i++) {
                stValue = (ServerTypeValue) serverTypes.get(i);
                stNames.put(stValue.getName(), stValue);
            }
        }

        Map<String, GenericPlugin> plugins = aiPluginManager.getPlugins();
        Map<String, ServerSignature> results = new HashMap<String, ServerSignature>();
        for (String name : plugins.keySet()) {
            GenericPlugin plugin = (GenericPlugin) plugins.get(name);
            String pluginName = plugin.getName();
            if (!(plugin instanceof ServerDetector)) {
                log.debug("skipping non-server AI plugin: " + pluginName);
                continue;
            }
            if (stNames != null && stNames.get(pluginName) == null) {
                log.debug("skipping unrequested AI plugin: " + pluginName);
                continue;
            }
            results.put(pluginName, ((ServerDetector) plugin).getServerSignature());
        }

        return results;
    }

    /**
     * Check if a given Appdef entity supports runtime auto-discovery.
     * 
     * @param id The entity id to check.
     * @return true if the given resource supports runtime auto-discovery.
     */
    public boolean isRuntimeDiscoverySupported(AuthzSubject subject, AppdefEntityID id) {
        boolean retVal;

        try {
            Server server = serverManager.getServerById(id.getId());
            if (server == null) {
                return false;
            }

            String pluginName = server.getServerType().getName();
            AutoinventoryPluginManager aiPluginManager = (AutoinventoryPluginManager) productManager
                .getPluginManager(ProductPlugin.TYPE_AUTOINVENTORY);
            GenericPlugin plugin = aiPluginManager.getPlugin(pluginName);

            if (plugin instanceof ServerDetector) {
                retVal = ((ServerDetector) plugin).isRuntimeDiscoverySupported();
            } else {
                retVal = false;
            }
        } catch (PluginNotFoundException pne) {
            return false;
        } catch (PluginException e) {
            log.error("Error getting plugin", e);
            return false;
        }

        return retVal;
    }

    /**
     * Turn off runtime-autodiscovery for a server that no longer exists. Use
     * this method when you know the appdefentity identified by "id" exists, so
     * that we'll be able to successfully find out which agent we should create
     * our AICommandsClient from.
     * @param id The AppdefEntityID of the resource to turn off runtime config
     *        for.
     */
    @Transactional
    public void turnOffRuntimeDiscovery(AuthzSubject subject, AppdefEntityID id) throws PermissionException {
        AICommandsClient client;

        try {
            client = aiCommandsClientFactory.getClient(id);
        } catch (AgentNotFoundException e) {
            throw new SystemException("Error looking up agent for resource " + "(" + id + "): " + e);
        }

        try {
            client.pushRuntimeDiscoveryConfig(id.getType(), id.getID(), null, null, null);
        } catch (AgentRemoteException e) {
            throw new SystemException("Error turning off runtime-autodiscovery " + "for resource (" + id + "): " + e);
        }

    }

    /**
     * Turn off runtime-autodiscovery for a server that no longer exists. We
     * need this as a separate method call because when the server no longer
     * exists, we have to manually specify the agent connection to use.
     * @param id The AppdefEntityID of the resource to turn off runtime config
     *        for.
     * @param agentToken Which agent controls the runtime AI scans for this
     *        resource.
     */
    @Transactional
    public void turnOffRuntimeDiscovery(AuthzSubject subject, AppdefEntityID id, String agentToken)
        throws PermissionException {
        AICommandsClient client;

        try {
            client = aiCommandsClientFactory.getClient(agentToken);
        } catch (AgentNotFoundException e) {
            throw new SystemException("Error looking up agent for resource " + "(" + id + "): " + e);
        }

        try {
            client.pushRuntimeDiscoveryConfig(id.getType(), id.getID(), null, null, null);
        } catch (AgentRemoteException e) {
            throw new SystemException("Error turning off runtime-autodiscovery " + "for resource (" + id + "): " + e);
        }
    }

    /**
     * Toggle Runtime-AI config for the given server.
     */
    public void toggleRuntimeScan(AuthzSubject subject, AppdefEntityID id, boolean enable) throws PermissionException,
        AutoinventoryException, ResourceDeletedException {
        Resource res = resourceManager.findResource(id);
        // if resource is asynchronously deleted ignore
        if (res == null || res.isInAsyncDeleteState()) {
            final String m = id + " is asynchronously deleted";
            throw new ResourceDeletedException(m);
        }
        if (!id.isServer()) {
            log.warn("toggleRuntimeScan() called for non-server type=" + id);
            return;
        }

        if (!isRuntimeDiscoverySupported(subject, id)) {
            return;
        }

        try {
            Server server = serverManager.findServerById(id.getId());
            server.setRuntimeAutodiscovery(enable);

            ConfigResponse metricConfig = configManager.getMergedConfigResponse(subject,
                ProductPlugin.TYPE_MEASUREMENT, id, true);

            pushRuntimeDiscoveryConfig(subject, server, metricConfig);
        } catch (ConfigFetchException e) {
            // No config, no need to turn off auto-discovery.
        } catch (Exception e) {
            throw new AutoinventoryException("Error enabling Runtime-AI for " + "server: " + e.getMessage(), e);
        }
    }

    /**
     * Push the metric ConfigResponse out to an agent so it can perform
     * runtime-autodiscovery
     * @param res The appdef entity ID of the server.
     * @param response The configuration info.
     */
    private void pushRuntimeDiscoveryConfig(AuthzSubject subject, AppdefResource res, ConfigResponse response)
        throws PermissionException {
        AppdefEntityID aeid = res.getEntityId();
        if (!isRuntimeDiscoverySupported(subject, aeid)) {
            return;
        }

        AICommandsClient client;

        if (aeid.isServer()) {
            // Setting the response to null will disable runtime
            // autodiscovery at the agent.
            if (!AppdefUtil.areRuntimeScansEnabled((Server) res)) {
                response = null;
            }
        }

        try {
            client = aiCommandsClientFactory.getClient(aeid);
        } catch (AgentNotFoundException e) {
            throw new SystemException("Error looking up agent for server " + "(" + res + "): " + e);
        }
        String typeName = res.getAppdefResourceType().getName();
        String name = null;
        if (!aeid.isServer()) {
            name = res.getName();
        }

        try {
            client.pushRuntimeDiscoveryConfig(aeid.getType(), aeid.getID(), typeName, name, response);
        } catch (AgentRemoteException e) {
            throw new SystemException("Error pushing metric config response to " + "agent for server (" + res + "): " +
                                      e);
        }

    }

    /**
     * Start an autoinventory scan.
     * @param aid The appdef entity whose agent we'll talk to.
     * @param scanConfig The scan configuration to use when scanning.
     * @param scanName The name of the scan - this is ignored (i.e. it can be
     *        null) for immediate, one-time scans.
     * @param scanDesc The description of the scan - this is ignored (i.e. it
     *        can be null) for immediate, one-time scans.
     * @param schedule Described when and how often the scan should run. If this
     *        is null, then the scan will be run as an immediate, one-time only
     *        scan.
     */
    @Transactional
    public void startScan(AuthzSubject subject, AppdefEntityID aid, ScanConfigurationCore scanConfig, String scanName,
                          String scanDesc, ScheduleValue schedule) throws AgentConnectionException,
        AgentNotFoundException, AutoinventoryException, DuplicateAIScanNameException, ScheduleWillNeverFireException,
        PermissionException {
        try {

            permissionManager.checkAIScanPermission(subject, aid);

            ConfigResponse config = configManager.getMergedConfigResponse(subject, ProductPlugin.TYPE_MEASUREMENT, aid,
                false);

            if (log.isDebugEnabled()) {
                log.debug("startScan config=" + config);
            }

            scanConfig.setConfigResponse(config);

            // All scans go through the scheduler.
            aiScheduleManager.doScheduledScan(subject, aid, scanConfig, scanName, scanDesc, schedule);
        } catch (ScheduleWillNeverFireException e) {
            throw e;
        } catch (DuplicateAIScanNameException ae) {
            throw ae;
        } catch (AutoinventoryException ae) {
            log.warn("Error starting scan: " + StringUtil.getStackTrace(ae));
            throw ae;
        } catch (PermissionException ae) {
            throw ae;
        } catch (Exception e) {
            throw new SystemException("Error starting scan " + "for agent: " + e, e);
        }
    }

    /**
     * Start an autoinventory scan by agentToken
     */
    @Transactional
    public void startScan(AuthzSubject subject, String agentToken, ScanConfigurationCore scanConfig)
        throws AgentConnectionException, AgentNotFoundException, AutoinventoryException, PermissionException {

        log.info("AutoinventoryManager.startScan called");

        // Is there an already-approved platform with this agent token? If so,
        // re-call using the other startScan method
        AIPlatform aipLocal = aiPlatformDao.findByAgentToken(agentToken);
        if (aipLocal == null) {
            throw new AutoinventoryException("No platform in auto-discovery " + "queue with agentToken=" + agentToken);
        }
        PlatformValue pValue;
        try {
            pValue = aiQueueManager.getPlatformByAI(subject, aipLocal.getId().intValue());

            // It does exist. Call the other startScan method so that
            // authz checks will apply
            startScan(subject, AppdefEntityID.newPlatformID(pValue.getId()), scanConfig, null, null, null);
            return;

        } catch (PlatformNotFoundException e) {
            log.warn("startScan: no platform exists for queued AIPlatform: " + aipLocal.getId() + ": " + e);
        } catch (Exception e) {
            log.error("startScan: error starting scan for AIPlatform: " + aipLocal.getId() + ": " + e, e);
            throw new SystemException(e);
        }

        try {
            AICommandsClient client = aiCommandsClientFactory.getClient(agentToken);

            client.startScan(scanConfig);
        } catch (AgentRemoteException e) {
            throw new AutoinventoryException(e);
        }
    }

    /**
     * Stop an autoinventory scan.
     * @param aid The appdef entity whose agent we'll talk to.
     */
    @Transactional
    public void stopScan(AuthzSubject subject, AppdefEntityID aid) throws AutoinventoryException {

        log.info("AutoinventoryManager.stopScan called");
        try {
            AICommandsClient client = aiCommandsClientFactory.getClient(aid);
            client.stopScan();
        } catch (Exception e) {
            throw new AutoinventoryException("Error stopping scan " + "for agent: " + e, e);
        }
    }

    /**
     * Get status for an autoinventory scan.
     * @param aid The appdef entity whose agent we'll talk to.
     */
    @Transactional
    public ScanStateCore getScanStatus(AuthzSubject subject, AppdefEntityID aid) throws AgentNotFoundException,
        AgentConnectionException, AgentRemoteException, AutoinventoryException {

        log.info("AutoinventoryManager.getScanStatus called");
        ScanStateCore core;
        try {
            AICommandsClient client = aiCommandsClientFactory.getClient(aid);
            core = client.getScanStatus();
        } catch (AgentNotFoundException ae) {
            throw ae;
        } catch (AgentRemoteException ae) {
            throw ae;
        } catch (AgentConnectionException ae) {
            throw ae;
        } catch (AutoinventoryException ae) {
            throw ae;
        } catch (Exception e) {
            throw new SystemException("Error getting scan status for agent: " + e, e);
        }
        return core;
    }

    /**
     * create AIHistory
     */
    @Transactional
    public AIHistory createAIHistory(AppdefEntityID id, Integer groupId, Integer batchId, String subjectName,
                                     ScanConfigurationCore config, String scanName, String scanDesc, Boolean scheduled,
                                     long startTime, long stopTime, long scheduleTime, String status,
                                     String errorMessage) throws AutoinventoryException {
        return aiHistoryDao.create(id, groupId, batchId, subjectName, config, scanName, scanDesc, scheduled, startTime,
            stopTime, scheduleTime, status, null /* description */, errorMessage);
    }

    /**
     * remove AIHistory
     */
    @Transactional
    public void removeHistory(AIHistory history) {
        aiHistoryDao.remove(history);
    }

    /**
     * update AIHistory
     */
    @Transactional
    public void updateAIHistory(Integer jobId, long endTime, String status, String message) {
        AIHistory local = aiHistoryDao.findById(jobId);

        local.setEndTime(endTime);
        local.setDuration(endTime - local.getStartTime());
        local.setStatus(status);
        local.setMessage(message);
    }

    /**
     * Get status for an autoinventory scan, given the agentToken
     */
    @Transactional
    public ScanStateCore getScanStatusByAgentToken(AuthzSubject subject, String agentToken)
        throws AgentNotFoundException, AgentConnectionException, AgentRemoteException, AutoinventoryException {
        log.info("AutoinventoryManager.getScanStatus called");
        ScanStateCore core;
        try {
            AICommandsClient client = aiCommandsClientFactory.getClient(agentToken);
            core = client.getScanStatus();
        } catch (AgentNotFoundException ae) {
            throw ae;
        } catch (AgentRemoteException ae) {
            throw ae;
        } catch (AgentConnectionException ae) {
            throw ae;
        } catch (AutoinventoryException ae) {
            throw ae;
        } catch (Exception e) {
            throw new SystemException("Error getting scan status " + "for agent: " + e, e);
        }
        return core;
    }

    private static List<Integer> buildAIResourceIds(AIAppdefResourceValue[] aiResources) {
        List<Integer> ids = new ArrayList<Integer>();
        for (int i = 0; i < aiResources.length; i++) {
            Integer id = aiResources[i].getId();
            if (id == null) {
                continue; // unchanged?
            }
            ids.add(id);
        }
        return ids;
    }

    private String getIps(Collection<AIIpValue> aiipValues) {
        StringBuilder rtn = new StringBuilder();
        for (AIIpValue aiip : aiipValues) {
            rtn.append(aiip.getAddress()).append(',');
        }
        return rtn.substring(0, rtn.length() - 1);
    }

    /**
     * Called by agents to report platforms, servers, and services detected via
     * autoinventory scans.
     * @param agentToken The token identifying the agent that sent the report.
     * @param stateCore The ScanState that was detected during the autoinventory
     *        scan.
     */
    public void reportAIData(String agentToken, ScanStateCore stateCore) throws AutoinventoryException {

        ScanState state = new ScanState(stateCore);

        AIPlatformValue aiPlatform = state.getPlatform();

        // This could happen if there was a serious error in the scan,
        // and not even the platform could be detected.
        if (state.getPlatform() == null) {
            log.warn("ScanState did not even contain a platform, ignoring.");
            return;
        }

        // TODO: G
        log.info("Received auto-inventory report from " + aiPlatform.getFqdn() + "; IPs -> " +
                 getIps(aiPlatform.getAddedAIIpValues()) + "; CertDN -> " + aiPlatform.getCertdn() + "; (" +
                 state.getAllServers(log).size() + " servers)");

        if (log.isDebugEnabled()) {
            log.debug("AutoinventoryManager.reportAIData called, " + "scan state=" + state);
            log.debug("AISERVERS=" + state.getAllServers(log));
        }

        // In the future we may want this method to act as
        // another user besides "admin". It might make sense to have
        // a user per-agent, so that actions that are agent-initiated
        // can be tracked. Of course, this will be difficult when the
        // agent is reporting itself to the server for the first time.
        // In that case, we'd have to act as admin and be careful about
        // what we allow that codepath to do.
        AuthzSubject subject = getHQAdmin();

        aiPlatform.setAgentToken(agentToken);

        if (log.isDebugEnabled()) {
            log.debug("AImgr.reportAIData: state.getPlatform()=" + aiPlatform);
        }

        if (stateCore.getAreServersIncluded()) {
            // TODO: G
            Set<AIServerValue> serverSet = state.getAllServers(log);

            for (AIServerValue aiServer : serverSet) {
                // Ensure the server reported has a valid appdef type
                try {
                    serverManager.findServerTypeByName(aiServer.getServerTypeName());
                } catch (NotFoundException e) {
                    log.error("Ignoring non-existent server type: " + aiServer.getServerTypeName(), e);
                    continue;
                }

                aiPlatform.addAIServerValue(aiServer);
            }
        }

        try {
            aiPlatform = aiQueueManager.queue(subject, aiPlatform, stateCore.getAreServersIncluded(), false, true);
        } catch (SystemException cse) {
            throw cse;
        } catch (Exception e) {
            throw new SystemException(e);
        }

        if (aiPlatform.isPlatformDevice()) {
            log.info("Auto-approving inventory for " + aiPlatform.getFqdn());
            List<Integer> platforms = new ArrayList<Integer>();
            platforms.add(aiPlatform.getId());
            List<Integer> ips = buildAIResourceIds(aiPlatform.getAIIpValues());
            List<Integer> servers = buildAIResourceIds(aiPlatform.getAIServerValues());

            try {
                aiQueueManager.processQueue(subject, platforms, servers, ips, AIQueueConstants.Q_DECISION_APPROVE);
            } catch (SystemException cse) {
                throw cse;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
    }

    /**
     * Called by agents to report resources detected at runtime via
     * monitoring-based autoinventory scans.
     * 
     * There are some interesting situations that can occur related to
     * synchronization between the server and agent. If runtime scans are turned
     * off for a server, but the agent is never notified (for example if the
     * agent is not running at the time), then the agent is going to eventually
     * report a runtime scan that includes resources detected by that server's
     * runtime scan. If this happens, we detect it and take the opportunity to
     * tell the agent again that it should not perform runtime AI scans for that
     * server. Any resources reported by that server will be ignored.
     * 
     * A similar situation occurs when the appdef server has been deleted but
     * the agent was never notified to turn off runtime AI. We handle this in
     * the same way, by telling the agent to turn off runtime scans for that
     * server, and ignoring anything in the report from that server.
     * 
     * This method will process all platform and server merging, given by the
     * report. Any services will be added to Zevent queue to be processed in
     * their own transactions.
     * 
     * @param agentToken The token identifying the agent that sent the report.
     * @param crrr The CompositeRuntimeResourceReport that was generated during
     *        the runtime autoinventory scan.
     */
    @Transactional
    public void reportAIRuntimeReport(String agentToken, CompositeRuntimeResourceReport crrr)
        throws AutoinventoryException, PermissionException, ValidationException, ApplicationException {
        runtimePlatformAndServerMerger.schedulePlatformAndServerMerges(agentToken, crrr);
    }

    /**
     * Returns a list of {@link Agent}s which still need to send in a runtime
     * scan (their last runtime scan was unsuccessfully processed)
     */
    @Transactional
    public List<Agent> findAgentsRequiringRuntimeScan() {
        Collection<AgentReportStatus> dirties = agentReportStatusDao.findDirtyStatus();
        List<Agent> res = new ArrayList<Agent>(dirties.size());

        log.debug("Found " + dirties.size() + " agents with " + "serviceDirty = true");

        for (AgentReportStatus s : dirties) {
            if (!serviceMerger.currentlyWorkingOn(s.getAgent())) {
                log.debug("Agent [" + s.getAgent().getAgentToken() + "] is serviceDirty");
                res.add(s.getAgent());
            } else {
                log.debug("Agent [" + s.getAgent().getAgentToken() + "] is serviceDirty, but in process");
            }
        }
        return res;
    }

    /**
     */
    @Transactional
    public void notifyAgentsNeedingRuntimeScan() {
        List<Agent> agents = findAgentsRequiringRuntimeScan();

        for (Agent a : agents) {
            AICommandsClient client;

            try {
                client = aiCommandsClientFactory.getClient(a.getAgentToken());
            } catch (AgentNotFoundException e) {
                log.warn("Unable to find agent [" + a.getAgentToken() + "]");
                continue;
            }

            int type = AppdefEntityConstants.APPDEF_TYPE_PLATFORM;
            ConfigResponse cfg = new ConfigResponse();

            try {
                client.pushRuntimeDiscoveryConfig(type, 0, null, null, cfg);
            } catch (AgentRemoteException e) {
                log.warn("Unable to notify agent needing runtime scan [" + a.getAgentToken() + "]");
                continue;
            }
        }
    }

    /**
     */
    @Transactional
    public void startup() {
        AgentCreateCallback listener = new AgentCreateCallback() {
            public void agentCreated(Agent agent) {
                serviceMerger.markServiceClean(agent, false);
            }
        };
        hqApp.registerCallbackListener(AgentCreateCallback.class, listener);
    }

    /**
     * Handle ResourceZEvents for enabling runtime autodiscovery.
     * 
     * @param events A list of ResourceZevents
     */
    @Transactional
    public void handleResourceEvents(List<ResourceZevent> events) {
        for (ResourceZevent zevent : events) {
            AppdefEntityID id = zevent.getAppdefEntityID();
            boolean isUpdate = zevent instanceof ResourceUpdatedZevent;

            // Only servers have runtime AI.
            if (!id.isServer()) {
                continue;
            }

            // Need to look up the AuthzSubject POJO
            AuthzSubject subj = authzSubjectManager.findSubjectById(zevent.getAuthzSubjectId());
            if (isUpdate) {
                Server s = serverManager.getServerById(id.getId());
                log.info("Toggling Runtime-AI for " + id);
                try {
                    toggleRuntimeScan(subj, id, s.isRuntimeAutodiscovery());
                } catch (ResourceDeletedException e) {
                    log.debug(e);
                } catch (Exception e) {
                    log.warn("Error toggling runtime-ai for server [" + id + "]", e);
                }
            } else {
                log.info("Enabling Runtime-AI for " + id);
                try {
                    toggleRuntimeScan(subj, id, true);
                } catch (ResourceDeletedException e) {
                    log.debug(e);
                } catch (Exception e) {
                    log.warn("Error enabling runtime-ai for server [" + id + "]", e);
                }
            }
        }
    }

    /**
     * Create an autoinventory manager.
     * 
     */
    @PostConstruct
    public void createDependentManagers() {
        // Get reference to the AI plugin manager
        try {
            aiPluginManager = (AutoinventoryPluginManager) productManager
                .getPluginManager(ProductPlugin.TYPE_AUTOINVENTORY);

        } catch (Exception e) {
            log.error("Unable to initialize session beans.", e);
        }
    }

    private AuthzSubject getHQAdmin() throws AutoinventoryException {
        try {
            return authzSubjectManager.getSubjectById(AuthzConstants.rootSubjectId);
        } catch (Exception e) {
            throw new AutoinventoryException("Error looking up subject", e);
        }
    }
}
