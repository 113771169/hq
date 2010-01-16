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

package org.hyperic.hq.appdef.server.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.ObjectNotFoundException;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.agent.client.AgentCommandsClient;
import org.hyperic.hq.agent.client.AgentCommandsClientFactory;
import org.hyperic.hq.appdef.Ip;
import org.hyperic.hq.appdef.shared.AIIpValue;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQApprovalException;
import org.hyperic.hq.appdef.shared.AIQueueConstants;
import org.hyperic.hq.appdef.shared.AIQueueManager;
import org.hyperic.hq.appdef.shared.AIServerValue;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.CPropManager;
import org.hyperic.hq.appdef.shared.ConfigManager;
import org.hyperic.hq.appdef.shared.PlatformManager;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.AuthzSubjectManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.autoinventory.AIIp;
import org.hyperic.hq.autoinventory.AIPlatform;
import org.hyperic.hq.autoinventory.AIServer;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.VetoException;
import org.hyperic.hq.common.shared.AuditManager;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.dao.AIIpDAO;
import org.hyperic.hq.dao.AIPlatformDAO;
import org.hyperic.hq.dao.AIServerDAO;
import org.hyperic.hq.grouping.shared.GroupNotCompatibleException;
import org.hyperic.sigar.NetFlags;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This class is responsible for managing the various autoinventory queues.
 * 
 * 
 */
@Service
public class AIQueueManagerImpl implements AIQueueManager {
    protected static final String AIPLATFORM_PROCESSOR = "org.hyperic.hq.appdef.server.session.PagerProcessor_aiplatform";
    protected static final String AIPLATFORM_PROCESSOR_NOPLACEHOLDERS = "org.hyperic.hq.appdef.server.session.PagerProcessor_aiplatform_excludePlaceholders";
    private Pager aiplatformPager;
    private Pager aiplatformPagerNoPlaceholders;

    private final AI2AppdefDiff appdefDiffProcessor = new AI2AppdefDiff();
    private final AIQSynchronizer queueSynchronizer = new AIQSynchronizer();

    private AIServerDAO aIServerDAO;
    private AIIpDAO aiIpDAO;
    private AIPlatformDAO aiPlatformDAO;
    private ConfigManager configManager;
    private CPropManager cPropManager;
    private PlatformDAO platformDAO;
    private PlatformManager platformManager;
    private ServerManager serverManager;
    private PermissionManager permissionManager;
    private AuditManager auditManager;
    private AuthzSubjectManager authzSubjectManager;
    protected final Log log = LogFactory.getLog(AIQueueManagerImpl.class.getName());

    @Autowired
    public AIQueueManagerImpl(AIServerDAO aIServerDAO, AIIpDAO aiIpDAO, AIPlatformDAO aiPlatformDAO,
                              ConfigManager configManager, CPropManager cPropManager, PlatformDAO platformDAO,
                              PlatformManager platformManager, ServerManager serverManager,
                              PermissionManager permissionManager, AuditManager auditManager,
                              AuthzSubjectManager authzSubjectManager) {

        this.aIServerDAO = aIServerDAO;
        this.aiIpDAO = aiIpDAO;
        this.aiPlatformDAO = aiPlatformDAO;
        this.configManager = configManager;
        this.cPropManager = cPropManager;
        this.platformDAO = platformDAO;
        this.platformManager = platformManager;
        this.serverManager = serverManager;
        this.permissionManager = permissionManager;
        this.auditManager = auditManager;
        this.authzSubjectManager = authzSubjectManager;
    }

    /**
     * Try to queue a candidate platform discovered via autoinventory.
     * @param aiplatform The platform that we got from the recent autoinventory
     *        data that we are wanting to queue. This may return null if the
     *        appdef platform was removed because the AI platform had a qstat of
     *        "remove" that was approved.
     * 
     * 
     */
    @Transactional
    public AIPlatformValue queue(AuthzSubject subject, AIPlatformValue aiplatform, boolean updateServers,
                                 boolean isApproval, boolean isReport) {

        // First, calculate queuestatus and diff with respect to
        // existing appdef data.
        AIPlatformValue revisedAIplatform = appdefDiffProcessor.diffAgainstAppdef(subject, platformManager,
            configManager, cPropManager, aiplatform);

        // A null return from diffAgainstAppdef means that
        // the platform no longer exists in appdef, AND that the aiplatform
        // had status "removed", so everything is kosher we just need to
        // nuke the queue entry.
        if (revisedAIplatform == null) {
            // log.info("AIQmgr.queue (post appdef-diff): aiplatform=NULL");
            AIPlatform aiplatformLocal;
            aiplatformLocal = aiPlatformDAO.get(aiplatform.getId());
            removeFromQueue(aiplatformLocal);
            return null;
        }

        // Synchronize current AI data into existing queue.
        revisedAIplatform = queueSynchronizer.sync(subject, this, aiPlatformDAO, revisedAIplatform, updateServers,
            isApproval, isReport);

        if (revisedAIplatform == null) {
            return null;
        }

        // OK, we do this the hard way: strip out "removed" servers
        // because we never want to show them
        AIServerValue[] servers = revisedAIplatform.getAIServerValues();
        revisedAIplatform.removeAllAIServerValues();
        for (int i = 0; i < servers.length; i++) {
            if (servers[i].getQueueStatus() != AIQueueConstants.Q_STATUS_REMOVED) {
                revisedAIplatform.addAIServerValue(servers[i]);
            }
        }

        return revisedAIplatform;
    }

    /**
     * Re-sync an existing queued platform against appdef.
     * @param aiplatform The platform that we got from the recent autoinventory
     *        data that we are wanting to queue.
     * @param isApproval If true, the platform's servers will be updated as
     *        well.
     */
    private AIPlatformValue syncQueue(AIPlatform aiplatform, boolean isApproval) {
        // XXX: Fix this..
        AuthzSubject subject = authzSubjectManager.getOverlordPojo();

        return queue(subject, aiplatform.getAIPlatformValue(), true, isApproval, false);
    }

    /**
     * Retrieve the contents of the AI queue.
     * @param showIgnored If true, even resources in the AI queue that have the
     *        'ignored' flag set will be returned. By default, resources with
     *        the 'ignored' flag set are excluded when the queue is retrieved.
     * @param showPlaceholders If true, even resources in the AI queue that are
     *        unchanged with respect to appdef will be returned. By default,
     *        resources that are unchanged with respect to appdef are excluded
     *        when the queue is retrieved.
     * @param showAlreadyProcessed If true, even resources that have already
     *        been processed (approved or not approved) will be shown.
     * @return A List of AIPlatformValue objects representing the contents of
     *         the autoinventory queue.
     * 
     * 
     */
    @Transactional
    public PageList<AIPlatformValue> getQueue(AuthzSubject subject, boolean showIgnored, boolean showPlaceholders,
                                              boolean showAlreadyProcessed, PageControl pc) {
        Collection<AIPlatform> queue;
        PageList<AIPlatformValue> results;
        pc = PageControl.initDefaults(pc, SortAttribute.DEFAULT);

        try {
            if (showIgnored) {
                if (showAlreadyProcessed) {
                    queue = aiPlatformDAO.findAllIncludingProcessed();
                } else {
                    queue = aiPlatformDAO.findAll();
                }
            } else {
                if (showAlreadyProcessed) {
                    queue = aiPlatformDAO.findAllNotIgnoredIncludingProcessed();
                } else {
                    queue = aiPlatformDAO.findAllNotIgnored();
                }
            }

            boolean canCreatePlatforms;
            try {
                permissionManager.checkCreatePlatformPermission(subject);
                canCreatePlatforms = true;
            } catch (PermissionException pe) {
                canCreatePlatforms = false;
            }

            // Walk the collection. If the aiplatform is "new", then only
            // keep it if the user has canCreatePlatforms permission.
            // If the aiplatform is not new, then make sure the user has
            // view permissions on the platform that backs the aiplatform.
            Iterator<AIPlatform> iter = queue.iterator();

            AppdefEntityID aid;
            Integer ppk;
            while (iter.hasNext()) {
                AIPlatform aipLocal = iter.next();
                Platform pValue = null;
                if (aipLocal.getQueueStatus() != AIQueueConstants.Q_STATUS_ADDED) {
                    try {
                        pValue = getPlatformByAI(subject, aipLocal);
                    } catch (Exception e) {
                        log.debug("Error finding platform for aiplatform: ", e);
                        pValue = null;
                    }
                }

                if (pValue == null && !canCreatePlatforms) {
                    if (log.isDebugEnabled()) {
                        log.debug("Removing platform because it doesn't exist" +
                                  " and the current user doesn't have the " + "'createPlatform' permission: " +
                                  aipLocal.getId());
                    }
                    iter.remove();

                } else if (pValue != null) {
                    ppk = pValue.getId();
                    aid = AppdefEntityID.newPlatformID(ppk);

                    try {
                        permissionManager.checkModifyPermission(subject, aid);
                    } catch (PermissionException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Removing platform because the " + "current user doesn't have the " +
                                      "'modifyPlatform' permission." + " PlatformID=" + pValue.getId());
                        }
                        iter.remove();
                    }
                }
            }

            // Do paging here
            if (showPlaceholders) {
                results = aiplatformPager.seek(queue, pc.getPagenum(), pc.getPagesize());
            } else {
                results = aiplatformPagerNoPlaceholders.seek(queue, pc.getPagenum(), pc.getPagesize());
            }

        } catch (Exception e) {
            throw new SystemException(e);
        }

        return results;
    }

    /**
     * Get an AIPlatformValue by id.
     * 
     * 
     * 
     * @return An AIPlatformValue with the given id, or null if that platform id
     *         is not present in the queue.
     */
    @Transactional
    public AIPlatformValue findAIPlatformById(AuthzSubject subject, int aiplatformID) {

        AIPlatform aiplatform = aiPlatformDAO.get(new Integer(aiplatformID));

        if (aiplatform == null) {
            return null;
        }

        return syncQueue(aiplatform, false);
    }

    /**
     * Get an AIPlatformValue by FQDN.
     * 
     * 
     * 
     * @return The AIPlatformValue with the given FQDN, or null if that FQDN
     *         does not exist in the queue.
     */
    @Transactional
    public AIPlatformValue findAIPlatformByFqdn(AuthzSubject subject, String fqdn) {
        AIPlatform aiplatform = null;
        AIPlatformValue aiplatformValue = null;

        // XXX Do authz check
        AIPlatform aiPlatform = aiPlatformDAO.findByFQDN(fqdn);
        aiplatformValue = aiPlatform.getAIPlatformValue();

        if (aiplatformValue == null) {
            return null;
        }

        aiplatformValue = syncQueue(aiplatform, false);
        return aiplatformValue;
    }

    /**
     * Get an AIServerValue by Id.
     * 
     * 
     * 
     * @return The AIServerValue with the given id, or null if that server id
     *         does not exist in the queue.
     */
    @Transactional
    public AIServerValue findAIServerById(AuthzSubject subject, int serverID) {
        AIServer aiserver = aIServerDAO.get(new Integer(serverID));

        if (aiserver == null) {
            return null;
        }

        return aiserver.getAIServerValue();
    }

    /**
     * 
     */
    public void removeAssociatedAIPlatform(Platform platform) throws VetoException {
        AIPlatform aiPlat = getAIPlatformByPlatform(platform);
        if (aiPlat == null) {
            return;
        }
        aiPlatformDAO.remove(aiPlat);
    }

    /**
     * Get an AIServerValue by name.
     * 
     * 
     * 
     * @return The AIServerValue with the given id, or null if that server name
     *         does not exist in the queue.
     */
    @Transactional
    public AIServerValue findAIServerByName(AuthzSubject subject, String name) {
        // XXX Do authz check
        AIServer aiserver = aIServerDAO.findByName(name);
        if (aiserver == null) {
            return null;
        }

        return aiserver.getAIServerValue();
    }

    /**
     * Get an AIIp by id.
     * 
     * 
     * 
     * @return The AIIp with the given id, or null if that ip does not exist.
     */
    @Transactional
    public AIIpValue findAIIpById(AuthzSubject subject, int ipID) {
        AIIp aiip = aiIpDAO.get(new Integer(ipID));
        if (aiip == null) {
            return null;
        }
        return aiip.getAIIpValue();
    }

    /**
     * Get an AIIpValue by address.
     * 
     * 
     * 
     * @return The AIIpValue with the given address, or null if an ip with that
     *         address does not exist in the queue.
     */
    @Transactional
    public AIIpValue findAIIpByAddress(AuthzSubject subject, String address) {
        // XXX Do authz check
        List<AIIp> aiips = aiIpDAO.findByAddress(address);
        if (aiips.size() == 0) {
            return null;
        }

        return aiips.get(0).getAIIpValue();
    }

    /**
     * Process resources in the AI queue. This can be used to approve resources
     * for inclusion into appdef, to ignore or unignore resources in the queue,
     * or to purge resources from the queue.
     * @param platformList A List of aiplatform IDs. This may be null, in which
     *        case it is ignored.
     * @param ipList A List of aiip IDs. This may be null, in which case it is
     *        ignored.
     * @param serverList A List of aiserver IDs. This may be null, in which case
     *        it is ignored.
     * @param action One of the AIQueueConstants.Q_DECISION_XXX constants
     *        indicating what to do with the platforms, ips and servers.
     * 
     * @return A List of AppdefResource's that were created as a result of
     *         processing the queue.
     * 
     * 
     * 
     */
    @Transactional
    public List<AppdefResource> processQueue(AuthzSubject subject, List<Integer> platformList,
                                             List<Integer> serverList, List<Integer> ipList, int action)
        throws  PermissionException, ValidationException,  AIQApprovalException {
        AuthzSubject s = authzSubjectManager.findSubjectById(subject.getId());
        boolean approved = false;

        try {
            if (action == AIQueueConstants.Q_DECISION_APPROVE) {
                approved = true;
                auditManager.pushContainer(AIAudit.newImportAudit(s));
            }
            return _processQueue(subject, platformList, serverList, ipList, action, true);
        } finally {
            if (approved)
                auditManager.popContainer(false);
        }
    }

    private List<AppdefResource> _processQueue(AuthzSubject subject, List<Integer> platformList,
                                               List<Integer> serverList, List<Integer> ipList, int action,
                                               boolean verifyLiveAgent) throws  PermissionException,
        ValidationException,  AIQApprovalException {
        boolean isApproveAction = (action == AIQueueConstants.Q_DECISION_APPROVE);
        boolean isPurgeAction = (action == AIQueueConstants.Q_DECISION_PURGE);
        int i;

        Map<Integer, Object> aiplatformsToResync = new HashMap<Integer, Object>();
        Map<String, AIServer> aiserversToRemove = new HashMap<String, AIServer>();
        Object marker = new Object();

        AIPlatform aiplatform = null;
        List<AppdefResource> createdResources = new ArrayList<AppdefResource>();

        // Create our visitor based on the action
        AIQResourceVisitor visitor = AIQResourceVisitorFactory.getVisitor(action);

        if (platformList != null) {
            for (i = 0; i < platformList.size(); i++) {
                final Integer id = platformList.get(i);

                if (id == null) {
                    log.error("processQueue: platform with ID=null");
                    continue;
                }

                aiplatform = aiPlatformDAO.get(id);

                if (aiplatform == null) {
                    if (isPurgeAction) {
                        continue;
                    } else {
                        throw new ObjectNotFoundException(id, AIPlatform.class.getName());
                    }
                }

                // Before processing platforms, ensure the agent is up since
                // the approval process depends on being able to schedule
                // runtime discovery and enable metrics.
                if (isApproveAction && verifyLiveAgent) {
                    try {
                        AgentCommandsClient client = AgentCommandsClientFactory.getInstance().getClient(
                            aiplatform.getAgentToken());
                        client.ping();
                    } catch (AgentNotFoundException e) {
                        // In this case we just want to
                        // remove the AIPlatform from the AIQ since the
                        // agent does not exist anyway
                        // JIRA bug http://jira.hyperic.com/browse/HHQ-2394
                        removeFromQueue(aiplatform);
                        continue;
                    } catch (AgentRemoteException e) {
                        throw new AIQApprovalException("Error invoking remote method on agent " + e.getMessage(), e);
                    } catch (AgentConnectionException e) {
                        // [HHQ-3249] if the IP is being updated then ping may
                        // fail but that is expected so ignore
                        if (ipIsUpdated(aiplatform, ipList)) {
                            final String msg = ", ignoring";
                            log.warn(msg);
                        } else {
                            throw new AIQApprovalException("Error connecting or communicating with agent " +
                                                           e.getMessage(), e);
                        }
                    }
                }

                visitor.visitPlatform(aiplatform, subject, platformManager, configManager, cPropManager,
                    createdResources);
                if (!isPurgeAction)
                    aiplatformsToResync.put(id, marker);
            }
        }
        if (ipList != null) {
            for (i = 0; i < ipList.size(); i++) {
                final Integer id = (Integer) ipList.get(i);
                if (id == null) {
                    log.error("processQueue: " + aiplatform.getName() + " has an IP with ID=null");
                    continue;
                }

                final AIIp aiip = aiIpDAO.get(id);

                if (aiip == null) {
                    if (isPurgeAction)
                        continue;
                    else
                        throw new ObjectNotFoundException(id, AIIp.class.getName());
                }
                visitor.visitIp(aiip, subject, platformManager);
                if (!isPurgeAction) {
                    Integer pk = aiip.getAIPlatform().getId();
                    aiplatformsToResync.put(pk, marker);
                }
            }
        }
        if (serverList != null) {
            for (i = 0; i < serverList.size(); i++) {
                final Integer id = (Integer) serverList.get(i);
                if (id == null) {
                    log.error("processQueue: " + aiplatform.getName() + " has a Server with ID=null");
                    continue;
                }
                final AIServer aiserver = aIServerDAO.get(id);
                if (aiserver == null) {
                    if (isPurgeAction) {
                        continue;
                    } else {
                        throw new ObjectNotFoundException(id, AIServer.class.getName());
                    }
                }

                visitor.visitServer(aiserver, subject, platformManager, serverManager, configManager, cPropManager,
                    createdResources);
                if (isApproveAction) {
                    // Approved servers are removed from the queue
                    String aiid = aiserver.getAutoinventoryIdentifier();
                    aiserversToRemove.put(aiid, aiserver);
                } else if (!isPurgeAction) {
                    Integer pk = aiserver.getAIPlatform().getId();
                    aiplatformsToResync.put(pk, marker);
                }
            }
        }

        // If the action was "approve", then resync queued platforms
        // to appdef, now that appdef may have been updated.

        if (isApproveAction) {

            for (Integer id : aiplatformsToResync.keySet()) {

                aiplatform = aiPlatformDAO.get(id);
                syncQueue(aiplatform, isApproveAction);
            }

            if (aiplatform != null) {
                // See above note, now we remove approved servers from the queue
                Collection<AIServer> servers = aiplatform.getAIServers();
                if (servers != null) {
                    for (Iterator<AIServer> it = servers.iterator(); it.hasNext();) {
                        AIServer aiServer = it.next();
                        String aiid = aiServer.getAutoinventoryIdentifier();
                        if (aiserversToRemove.containsKey(aiid)) {
                            it.remove();
                        }
                    }
                }
            }
        }
        return createdResources;
    }

    private boolean ipIsUpdated(AIPlatform aiplatform, Collection<Integer> ipList) {
        if (AIQueueConstants.Q_STATUS_CHANGED != aiplatform.getQueueStatus()) {
            return false;
        }

        boolean added = false;
        boolean removed = false;
        for (Integer id : ipList) {

            final AIIp aiip = aiIpDAO.get(id);
            if (AIQueueConstants.Q_STATUS_REMOVED == aiip.getQueueStatus()) {
                removed = true;
            }
            if (AIQueueConstants.Q_STATUS_ADDED == aiip.getQueueStatus()) {
                added = true;
            }
        }
        if (added && removed) {
            return true;
        }
        return false;
    }

    /**
     * Remove an AI platform from the queue.
     * 
     * 
     */
    @Transactional
    public void removeFromQueue(AIPlatform aiplatform) {
        // Remove the platform, this should recursively remove all queued
        // servers and IPs
        aiPlatformDAO.remove(aiplatform);
    }

    /**
     * Find a platform given an AI platform id
     * 
     */
    public PlatformValue getPlatformByAI(AuthzSubject subject, int aiPlatformID) throws 
        PermissionException, PlatformNotFoundException {
        AIPlatform aiplatform;

        // XXX Do authz check
        aiplatform = aiPlatformDAO.get(new Integer(aiPlatformID));
        return getPlatformByAI(subject, aiplatform).getPlatformValue();
    }

    /**
     * Get a platform given an AI platform, returns null if none found
     * 
     */
    public AIPlatformValue getAIPlatformByPlatformID(AuthzSubject subject, Integer platformID) {
        AIPlatform aip = getAIPlatformByPlatformID(platformID);
        return (aip == null) ? null : aip.getAIPlatformValue();
    }

    private AIPlatform getAIPlatformByPlatformID(Integer platformID) {
        return getAIPlatformByPlatform(platformDAO.findById(platformID));
    }

    /**
     * may return null if there are no associated AIPlatforms.
     */
    private AIPlatform getAIPlatformByPlatform(Platform platform) {
        Collection<Ip> ips = platform.getIps();
        // We can't use the FQDN to find a platform, because
        // the FQDN can change too easily. Instead we use the
        // IP address now. For now, if we get one IP address
        // match (and it isn't localhost), we assume that it is
        // the same platform. In the future, we are probably going
        // to need to do better.
        for (Iterator<Ip> i = ips.iterator(); i.hasNext();) {
            Ip qip = i.next();

            String mac = qip.getMacAddress();

            if (mac != null && mac.length() > 0 && !mac.equals(NetFlags.NULL_HWADDR)) {
                List<AIIp> addrs = aiIpDAO.findByMACAddress(qip.getMacAddress());
                if (addrs.size() > 0) {
                    AIPlatform aiplatform = ((AIIp) addrs.get(0)).getAIPlatform();
                    return aiplatform;
                }
            }

            String address = qip.getAddress();
            // XXX This is a hack that we need to get rid of
            // at some point. The idea is simple. Every platform
            // has the localhost address. So, if we are looking
            // for a platform based on IP address, searching for
            // localhost doesn't give us any information. Long
            // term, when we are trying to match all addresses,
            // this can go away.
            if ((address.equals(NetFlags.LOOPBACK_ADDRESS) || address.equals(NetFlags.ANY_ADDR)) && i.hasNext()) {
                continue;
            }

            List<AIIp> addrs = aiIpDAO.findByAddress(address);
            if (addrs.size() > 0) {
                AIPlatform aiplatform = addrs.get(0).getAIPlatform();
                return aiplatform;
            }
        }

        return null;
    }

    /**
     * Find an AI platform given an platform
     * 
     */
    public Platform getPlatformByAI(AuthzSubject subject, AIPlatform aipLocal) throws PermissionException,
        PlatformNotFoundException {

        Platform p = platformManager.getPlatformByAIPlatform(subject, aipLocal.getAIPlatformValue());

        if (p != null)
            return p;

        throw new PlatformNotFoundException("platform not found for ai " + "platform: " + aipLocal.getId());
    }

    /**
     * Check to see if the subject can perform an autoinventory scan on the
     * specified resource. 
     * 
     */
    public void checkAIScanPermission(AuthzSubject subject, AppdefEntityID id) throws PermissionException,
        GroupNotCompatibleException {
        permissionManager.checkAIScanPermission(subject, id);
    }

    public static AIQueueManager getOne() {
        return Bootstrap.getBean(AIQueueManager.class);
    }

    /**
     * 
     */
    @PostConstruct
    public void afterPropertiesSet() throws Exception {

        aiplatformPager = Pager.getPager(AIPLATFORM_PROCESSOR);
        aiplatformPagerNoPlaceholders = Pager.getPager(AIPLATFORM_PROCESSOR_NOPLACEHOLDERS);

    }

}
