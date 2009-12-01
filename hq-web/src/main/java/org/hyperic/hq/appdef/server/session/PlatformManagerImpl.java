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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.NonUniqueResultException;
import org.hibernate.ObjectNotFoundException;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.appdef.AppService;
import org.hyperic.hq.appdef.ConfigResponseDB;
import org.hyperic.hq.appdef.Ip;
import org.hyperic.hq.appdef.shared.AIIpValue;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQueueConstants;
import org.hyperic.hq.appdef.shared.AIQueueManager;
import org.hyperic.hq.appdef.shared.AgentManager;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateFQDNException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateNameException;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.ApplicationNotFoundException;
import org.hyperic.hq.appdef.shared.CPropManager;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.IpValue;
import org.hyperic.hq.appdef.shared.PlatformManager;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformTypeValue;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.appdef.shared.ServiceManager;
import org.hyperic.hq.appdef.shared.ServiceNotFoundException;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Operation;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.authz.server.session.ResourceType;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.authz.shared.ResourceGroupManager;
import org.hyperic.hq.authz.shared.ResourceManager;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.VetoException;
import org.hyperic.hq.common.server.session.Audit;
import org.hyperic.hq.common.server.session.ResourceAudit;
import org.hyperic.hq.common.shared.AuditManager;
import org.hyperic.hq.common.shared.ProductProperties;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.measurement.server.session.AgentScheduleSyncZevent;
import org.hyperic.hq.product.PlatformDetector;
import org.hyperic.hq.product.PlatformTypeInfo;
import org.hyperic.hq.zevents.ZeventEnqueuer;
import org.hyperic.sigar.NetFlags;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * This class is responsible for managing Platform objects in appdef and their
 * relationships
 * 
 */
@org.springframework.stereotype.Service
@Transactional
public class PlatformManagerImpl implements PlatformManager {

    private final Log log = LogFactory.getLog(PlatformManagerImpl.class.getName());

    private static final String VALUE_PROCESSOR = PagerProcessor_platform.class.getName();

    private Pager valuePager;

    private PlatformTypeDAO platformTypeDAO;

    private PermissionManager permissionManager;

    private AgentDAO agentDAO;

    private ServerManager serverManager;

    private CPropManager cpropManager;

    private ResourceManager resourceManager;

    private ResourceGroupManager resourceGroupManager;

    private AuthzSubjectManager authzSubjectManager;

    private ServiceManager serviceManager;

    private ApplicationDAO applicationDAO;

    private ConfigResponseDAO configResponseDAO;

    private PlatformDAO platformDAO;

    private ServerDAO serverDAO;

    private ServiceDAO serviceDAO;

    private AuditManager auditManager;

    private AgentManager agentManager;

    private ZeventEnqueuer zeventManager;

    @Autowired
    public PlatformManagerImpl(PlatformTypeDAO platformTypeDAO, PermissionManager permissionManager, AgentDAO agentDAO,
                               ServerManager serverManager, CPropManager cpropManager, ResourceManager resourceManager,
                               ResourceGroupManager resourceGroupManager, AuthzSubjectManager authzSubjectManager,
                               ServiceManager serviceManager, ApplicationDAO applicationDAO,
                               ConfigResponseDAO configResponseDAO, PlatformDAO platformDAO, ServerDAO serverDAO,
                               ServiceDAO serviceDAO, AuditManager auditManager, AgentManager agentManager,
                               ZeventEnqueuer zeventManager) {
        this.platformTypeDAO = platformTypeDAO;
        this.permissionManager = permissionManager;
        this.agentDAO = agentDAO;
        this.serverManager = serverManager;
        this.cpropManager = cpropManager;
        this.resourceManager = resourceManager;
        this.resourceGroupManager = resourceGroupManager;
        this.authzSubjectManager = authzSubjectManager;
        this.serviceManager = serviceManager;
        this.applicationDAO = applicationDAO;
        this.configResponseDAO = configResponseDAO;
        this.platformDAO = platformDAO;
        this.serverDAO = serverDAO;
        this.serviceDAO = serviceDAO;
        this.auditManager = auditManager;
        this.agentManager = agentManager;
        this.zeventManager = zeventManager;
    }

    // TODO resolve circular dependency
    private AIQueueManager getAIQueueManager() {
        return Bootstrap.getBean(AIQueueManager.class);
    }

    // TODO remove after HE-54 allows injection
    private PlatformCounter getCounter() {
        PlatformCounter counter = (PlatformCounter) ProductProperties
            .getPropertyInstance("hyperic.hq.platform.counter");

        if (counter == null) {
            counter = new DefaultPlatformCounter();
        }
        return counter;
    }

    /**
     * Find a PlatformType by id
     * 
     * 
     */
    public PlatformType findPlatformType(Integer id) throws ObjectNotFoundException {
        return platformTypeDAO.findById(id);
    }

    /**
     * Find a platform type by name
     * 
     * @param type - name of the platform type
     * @return platformTypeValue
     * 
     */
    public PlatformType findPlatformTypeByName(String type) throws PlatformNotFoundException {
        PlatformType ptype = platformTypeDAO.findByName(type);
        if (ptype == null) {
            throw new PlatformNotFoundException(type);
        }
        return ptype;
    }

    /**
     * @return {@link PlatformType}s
     * 
     */
    public Collection<PlatformType> findAllPlatformTypes() {
        return platformTypeDAO.findAll();
    }

    /**
     * @return {@link PlatformType}s
     * 
     */
    public Collection<PlatformType> findSupportedPlatformTypes() {
        Collection<PlatformType> platformTypes = findAllPlatformTypes();

        for (Iterator<PlatformType> it = platformTypes.iterator(); it.hasNext();) {
            PlatformType pType = it.next();
            if (!PlatformDetector.isSupportedPlatform(pType.getName())) {
                it.remove();
            }
        }
        return platformTypes;
    }

    /**
     * @return {@link PlatformType}s
     * 
     */
    public Collection<PlatformType> findUnsupportedPlatformTypes() {
        Collection<PlatformType> platformTypes = findAllPlatformTypes();

        for (Iterator<PlatformType> it = platformTypes.iterator(); it.hasNext();) {
            PlatformType pType = it.next();
            if (PlatformDetector.isSupportedPlatform(pType.getName())) {
                it.remove();
            }
        }
        return platformTypes;
    }

    /**
     * 
     */
    public Resource findResource(PlatformType pt) {

        ResourceType rType;

        String typeName = AuthzConstants.platformPrototypeTypeName;
        try {
            rType = resourceManager.findResourceTypeByName(typeName);
        } catch (FinderException e) {
            throw new SystemException(e);
        }
        return resourceManager.findResourceByInstanceId(rType, pt.getId());
    }

    /**
     * Find all platform types
     * 
     * @return List of PlatformTypeValues
     * 
     */
    public PageList<PlatformTypeValue> getAllPlatformTypes(AuthzSubject subject, PageControl pc) {
        Collection<PlatformType> platTypes = platformTypeDAO.findAllOrderByName();
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platTypes, pc);
    }

    /**
     * Find viewable platform types
     * 
     * @return List of PlatformTypeValues
     * 
     */
    public PageList<PlatformTypeValue> getViewablePlatformTypes(AuthzSubject subject, PageControl pc)
        throws FinderException, PermissionException {

        // build the platform types from the visible list of platforms
        Collection platforms;
        try {
            platforms = getViewablePlatforms(subject, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }

        Collection<AppdefResourceType> platTypes = filterResourceTypes(platforms);

        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platTypes, pc);
    }

    /**
     * Get PlatformPluginName for an entity id. There is no authz in this method
     * because it is not needed.
     * 
     * @return name of the plugin for the entity's platform such as
     *         "Apache 2.0 Linux". It is used as to look up plugins via a
     *         generic plugin manager.
     * 
     */
    public String getPlatformPluginName(AppdefEntityID id) throws AppdefEntityNotFoundException {
        Platform p;
        String typeName;

        if (id.isService()) {
            // look up the service ejb
            Service service = serviceDAO.get(id.getId());

            if (service == null) {
                throw new ServiceNotFoundException(id);
            }

            p = service.getServer().getPlatform();
            typeName = service.getServiceType().getName();
        } else if (id.isServer()) {
            // look up the server
            Server server = serverDAO.get(id.getId());

            if (server == null) {
                throw new ServerNotFoundException(id);
            }
            p = server.getPlatform();
            typeName = server.getServerType().getName();
        } else if (id.isPlatform()) {
            p = findPlatformById(id.getId());
            typeName = p.getPlatformType().getName();
        } else if (id.isGroup()) {
            ResourceGroup g = resourceGroupManager.findResourceGroupById(id.getId());
            return g.getResourcePrototype().getName();
        } else {
            throw new IllegalArgumentException("Unsupported entity type: " + id);
        }

        if (id.isPlatform()) {
            return typeName;
        } else {
            return typeName + " " + p.getPlatformType().getName();
        }
    }

    /**
     * Delete a platform
     * 
     * @param subject The user performing the delete operation.
     * @param id - The id of the Platform
     * 
     */
    public void removePlatform(AuthzSubject subject, Platform platform) throws RemoveException,
        PlatformNotFoundException, PermissionException, VetoException {
        final AppdefEntityID aeid = platform.getEntityId();
        final Resource r = platform.getResource();
        final Audit audit = ResourceAudit.deleteResource(r, subject, 0, 0);
        boolean pushed = false;
        try {
            auditManager.pushContainer(audit);
            pushed = true;
            permissionManager.checkRemovePermission(subject, platform.getEntityId());
            // keep the configresponseId so we can remove it later
            ConfigResponseDB config = platform.getConfigResponse();
            removeServerReferences(platform);

            // this flush ensures that the server's platform_id is set to null
            // before the platform is deleted and the servers cascaded
            platformDAO.getSession().flush();
            getAIQueueManager().removeAssociatedAIPlatform(platform);
            cleanupAgent(platform);
            platform.getIps().clear();
            platformDAO.remove(platform);
            if (config != null) {
                configResponseDAO.remove(config);
            }
            cpropManager.deleteValues(aeid.getType(), aeid.getID());
            resourceManager.removeAuthzResource(subject, aeid, r);
            platformDAO.getSession().flush();
        } catch (RemoveException e) {
            log.debug("Error while removing Platform");

            throw e;
        } catch (PermissionException e) {
            log.debug("Error while removing Platform");

            throw e;
        } finally {
            if (pushed)
                auditManager.popContainer(false);
        }
    }

    private void cleanupAgent(Platform platform) {
        final Agent agent = platform.getAgent();
        if (agent == null) {
            return;
        }
        final Collection<Platform> platforms = agent.getPlatforms();
        Platform phys = null;
        for (final Iterator<Platform> it = platforms.iterator(); it.hasNext();) {
            final Platform p = it.next();
            if (p == null) {
                continue;
            }
            final String platType = platform.getPlatformType().getName();
            if (PlatformDetector.isSupportedPlatform(platType)) {
                phys = p;
            }
            if (p.getId().equals(platform.getId())) {
                it.remove();
            }
        }
        if (phys == null) {
            return;
        }
        if (phys.getId().equals(platform.getId())) {
            agentManager.removeAgentStatus(agent);
        }
    }

    private void removeServerReferences(Platform platform) {

        final Collection<Server> servers = platform.getServersBag();
        // since we are using the hibernate collection
        // we need to synchronize
        synchronized (servers) {
            for (final Iterator<Server> i = servers.iterator(); i.hasNext();) {
                try {
                    // this looks funky but the idea is to pull the server
                    // obj into the session so that it is updated when flushed
                    final Server server = serverManager.findServerById(i.next().getId());
                    // there are instances where we may have a duplicate
                    // autoinventory identifier btwn platforms
                    // (sendmail, ntpd, CAM Agent Server, etc...)
                    final String uniqAiid = server.getPlatform().getId() + server.getAutoinventoryIdentifier();
                    server.setAutoinventoryIdentifier(uniqAiid);
                    server.setPlatform(null);
                    i.remove();
                } catch (ServerNotFoundException e) {
                    log.warn(e.getMessage());
                }
            }
        }
    }

    /**
     * 
     */
    public void handleResourceDelete(Resource resource) {
        platformDAO.clearResource(resource);
    }

    /**
     * Create a Platform of a specified type
     * 
     * 
     */
    public Platform createPlatform(AuthzSubject subject, Integer platformTypeId, PlatformValue pValue, Integer agentPK)
        throws CreateException, ValidationException, PermissionException, AppdefDuplicateNameException,
        AppdefDuplicateFQDNException, ApplicationException {
        // check if the object already exists

        if (platformDAO.findByName(pValue.getName()) != null) {
            // duplicate found, throw a duplicate object exception
            throw new AppdefDuplicateNameException();
        }
        if (platformDAO.findByFQDN(pValue.getFqdn()) != null) {
            // duplicate found, throw a duplicate object exception
            throw new AppdefDuplicateFQDNException();
        }

        try {

            ConfigResponseDB config;
            Platform platform;
            Agent agent = null;

            if (agentPK != null) {
                agent = agentDAO.findById(agentPK);
            }
            if (pValue.getConfigResponseId() == null) {
                config = configResponseDAO.createPlatform();
            } else {
                config = configResponseDAO.findById(pValue.getConfigResponseId());
            }

            trimStrings(pValue);
            getCounter().addCPUs(pValue.getCpuCount().intValue());
            validateNewPlatform(pValue);
            PlatformType pType = findPlatformType(platformTypeId);

            pValue.setOwner(subject.getName());
            pValue.setModifiedBy(subject.getName());

            platform = pType.create(pValue, agent, config);
            platformDAO.save(platform); // To setup its ID
            // AUTHZ CHECK
            // in order to succeed subject has to be in a role
            // which allows creating of authz resources
            createAuthzPlatform(subject, platform);

            // Create the virtual server types
            for (ServerType st : pType.getServerTypes()) {

                if (st.isVirtual()) {
                    serverManager.createVirtualServer(subject, platform, st);
                }
            }

            platformDAO.getSession().flush();

            // Send resource create event
            ResourceCreatedZevent zevent = new ResourceCreatedZevent(subject, platform.getEntityId());
            zeventManager.enqueueEventAfterCommit(zevent);

            return platform;
        } catch (FinderException e) {
            throw new CreateException("Unable to find PlatformType: " + platformTypeId + " : " + e.getMessage());
        }
    }

    private void throwDupPlatform(Serializable id, String platName) {
        throw new NonUniqueObjectException(id, "Duplicate platform found " + "with name: " + platName);
    }

    /**
     * Create a Platform from an AIPlatform
     * 
     * @param aipValue the AIPlatform to create as a regular appdef platform.
     * 
     */
    public Platform createPlatform(AuthzSubject subject, AIPlatformValue aipValue) throws ApplicationException,
        CreateException {
        getCounter().addCPUs(aipValue.getCpuCount().intValue());

        PlatformType platType = platformTypeDAO.findByName(aipValue.getPlatformTypeName());

        if (platType == null) {
            throw new SystemException("Unable to find PlatformType [" + aipValue.getName() + "]");
        }

        Platform checkP = platformDAO.findByName(aipValue.getName());
        if (checkP != null) {
            throwDupPlatform(checkP.getId(), aipValue.getName());
        }

        Agent agent = agentDAO.findByAgentToken(aipValue.getAgentToken());

        if (agent == null) {
            throw new ApplicationException("Unable to find agent: " + aipValue.getAgentToken());
        }
        ConfigResponseDB config = configResponseDAO.createPlatform();

        Platform platform = platType.create(aipValue, subject.getName(), config, agent);
        platformDAO.save(platform);

        // AUTHZ CHECK
        try {
            createAuthzPlatform(subject, platform);
        } catch (Exception e) {
            throw new SystemException(e);
        }

        // Send resource create event
        ResourceCreatedZevent zevent = new ResourceCreatedZevent(subject, platform.getEntityId());
        zeventManager.enqueueEventAfterCommit(zevent);

        return platform;
    }

    /**
     * Get all platforms.
     * 
     * 
     * @param subject The subject trying to list platforms.
     * @param pc a PageControl object which determines the size of the page and
     *        the sorting, if any.
     * @return A List of PlatformValue objects representing all of the platforms
     *         that the given subject is allowed to view.
     */
    public PageList<PlatformValue> getAllPlatforms(AuthzSubject subject, PageControl pc) throws FinderException,
        PermissionException {

        Collection<Platform> ejbs;
        try {
            ejbs = getViewablePlatforms(subject, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(ejbs, pc);
    }

    /**
     * Get platforms created within a given time range.
     * 
     * 
     * @param subject The subject trying to list platforms.
     * @param range The range in milliseconds.
     * @param size The number of platforms to return.
     * @return A List of PlatformValue objects representing all of the platforms
     *         that the given subject is allowed to view that were created
     *         within the given range.
     */
    public PageList<PlatformValue> getRecentPlatforms(AuthzSubject subject, long range, int size)
        throws FinderException, PermissionException {
        PageControl pc = new PageControl(0, size);

        Collection<Platform> platforms = platformDAO.findByCTime(System.currentTimeMillis() - range);

        // now get the list of PKs
        List<Integer> viewable = getViewablePlatformPKs(subject);
        // and iterate over the list to remove any item not viewable
        for (Iterator<Platform> i = platforms.iterator(); i.hasNext();) {
            Platform platform = i.next();
            if (!viewable.contains(platform.getId())) {
                // remove the item, user cant see it
                i.remove();
            }
        }

        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platforms, pc);
    }

    /**
     * Get platform light value by id. Does not check permission.
     * 
     * 
     */
    public Platform getPlatformById(AuthzSubject subject, Integer id) throws PlatformNotFoundException,
        PermissionException {
        Platform platform = findPlatformById(id);
        permissionManager.checkViewPermission(subject, platform.getEntityId());
        // Make sure that resource is loaded as to not get
        // LazyInitializationException
        platform.getName();
        return platform;
    }

    /**
     * Find a Platform by Id.
     * 
     * @param id The id to look up.
     * @return A Platform object representing this Platform.
     * @throws PlatformNotFoundException If the given Platform is not found.
     * 
     */
    public Platform findPlatformById(Integer id) throws PlatformNotFoundException {
        Platform platform = platformDAO.get(id);

        if (platform == null) {
            throw new PlatformNotFoundException(id);
        }

        // Make sure that resource is loaded as to not get
        // LazyInitializationException
        platform.getName();

        return platform;
    }

    /**
     * Get the Platform object based on an AIPlatformValue. Checks against FQDN,
     * CertDN, then checks to see if all IP addresses match. If all of these
     * checks fail null is returned.
     * 
     * 
     */
    public Platform getPlatformByAIPlatform(AuthzSubject subject, AIPlatformValue aiPlatform)
        throws PermissionException {
        Platform p = null;

        String fqdn = aiPlatform.getFqdn();
        String certdn = aiPlatform.getCertdn();

        final AIIpValue[] ipvals = aiPlatform.getAIIpValues();
        if (!isAgentPorker(Arrays.asList(ipvals))) {
            // We can't use the FQDN to find a platform, because
            // the FQDN can change too easily. Instead we use the
            // IP address now. For now, if we get one IP address
            // match (and it isn't localhost), we assume that it is
            // the same platform. In the future, we are probably going
            // to need to do better.
            for (int i = 0; i < ipvals.length; i++) {
                AIIpValue qip = ipvals[i];

                String address = qip.getAddress();
                // XXX This is a hack that we need to get rid of
                // at some point. The idea is simple. Every platform
                // has the localhost address. So, if we are looking
                // for a platform based on IP address, searching for
                // localhost doesn't give us any information. Long
                // term, when we are trying to match all addresses,
                // this can go away.
                if (address.equals(NetFlags.LOOPBACK_ADDRESS) && i < (ipvals.length - 1)) {
                    continue;
                }

                Collection<Platform> platforms = platformDAO.findByIpAddr(address);

                if (!platforms.isEmpty()) {
                    Platform ipMatch = null;

                    for (Platform plat : platforms) {

                        // Make sure the types match
                        if (!plat.getPlatformType().getName().equals(aiPlatform.getPlatformTypeName())) {
                            continue;
                        }

                        // If we got any platforms that match this IP address,
                        // then
                        // we just take it and see if we can match up more
                        // criteria.
                        // We can assume that is a candidate for the platform we
                        // are
                        // looking for. This should only fall apart if we have
                        // multiple platforms defined for the same IP address,
                        // which
                        // should be a rarity.

                        if (plat.getFqdn().equals(fqdn)) { // Perfect
                            p = plat;
                            break;
                        }

                        // FQDN changed
                        if (platformMatchesAllIps(plat, Arrays.asList(ipvals))) {
                            ipMatch = plat;
                        }
                    }

                    // If FQDN was not matched, but all IPs are
                    p = (p == null) ? ipMatch : p;
                }

                // Found a match
                if (p != null) {
                    break;
                }
            }
        }

        // One more try
        if (p == null) {
            p = platformDAO.findByFQDN(fqdn);
        }

        String agentToken = aiPlatform.getAgentToken();
        if (p == null) {
            p = getPhysPlatformByAgentToken(agentToken);
        }

        if (p != null) {
            permissionManager.checkViewPermission(subject, p.getEntityId());
            if (isAgentPorker(Arrays.asList(ipvals)) && // Let agent porker
                // create new platforms
                !(p.getFqdn().equals(fqdn) || p.getCertdn().equals(certdn) || p.getAgent().getAgentToken().equals(
                    agentToken))) {
                p = null;
            }
        }

        return p;
    }

    /**
     * @return non-virtual, physical, {@link Platform} associated with the
     *         agentToken or null if one does not exist.
     * 
     */
    public Platform getPhysPlatformByAgentToken(String agentToken) {
        try {

            Agent agent = agentManager.getAgent(agentToken);
            Collection<Platform> platforms = agent.getPlatforms();
            for (Platform platform : platforms) {

                String platType = platform.getPlatformType().getName();
                // need to check if the platform is not a platform device
                if (PlatformDetector.isSupportedPlatform(platType)) {
                    return platform;
                }
            }
        } catch (AgentNotFoundException e) {
            return null;
        }
        return null;
    }

    private boolean isAgentPorker(List<AIIpValue> ips) {
        // anytime a new agent comes in (ip / port being unique) it creates a
        // new object mapping in the db. Therefore if the agent is found but
        // with no associated platform, we need to check the ips in the
        // agent table. If there are more than one IPs match,
        // then assume this is the Agent Porker

        for (AIIpValue ip : ips) {

            if (ip.getAddress().equals(NetFlags.LOOPBACK_ADDRESS)) {
                continue;
            }
            List<Agent> agents = agentManager.findAgentsByIP(ip.getAddress());
            if (agents.size() > 1) {
                return true;
            }
        }
        return false;
    }

    private boolean platformMatchesAllIps(Platform p, List<AIIpValue> ips) {
        Collection<Ip> platIps = p.getIps();
        if (platIps.size() != ips.size()) {
            return false;
        }
        Set<String> ipSet = new HashSet<String>();
        for (AIIpValue ip : ips) {

            ipSet.add(ip.getAddress());
        }
        for (Ip ip : platIps) {
            if (!ipSet.contains(ip.getAddress())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find a platform by name
     * 
     * 
     * @param subject - who is trying this
     * @param name - the name of the platform
     */
    public PlatformValue getPlatformByName(AuthzSubject subject, String name) throws PlatformNotFoundException,
        PermissionException {
        Platform p = platformDAO.findByName(name);
        if (p == null) {
            throw new PlatformNotFoundException("platform " + name + " not found");
        }
        // now check if the user can see this at all
        permissionManager.checkViewPermission(subject, p.getEntityId());
        return p.getPlatformValue();
    }

    /**
     * 
     */
    public Platform getPlatformByName(String name) {
        return platformDAO.findBySortName(name);
    }

    /**
     * Get the Platform that has the specified Fqdn
     * 
     * 
     */
    public Platform findPlatformByFqdn(AuthzSubject subject, String fqdn) throws PlatformNotFoundException,
        PermissionException {
        Platform p;
        try {
            p = platformDAO.findByFQDN(fqdn);
        } catch (NonUniqueResultException e) {
            p = null;
        }
        if (p == null) {
            throw new PlatformNotFoundException("Platform with fqdn " + fqdn + " not found");
        }
        // now check if the user can see this at all
        permissionManager.checkViewPermission(subject, p.getEntityId());
        return p;
    }

    /**
     * Get the Collection of platforms that have the specified Ip address
     * 
     * 
     */
    public Collection<Platform> getPlatformByIpAddr(AuthzSubject subject, String address) throws PermissionException {
        return platformDAO.findByIpAddr(address);
    }

    /**
     * Get the platform by agent token
     * 
     * 
     */
    public Collection<Integer> getPlatformPksByAgentToken(AuthzSubject subject, String agentToken)
        throws PlatformNotFoundException {
        Collection<Platform> platforms = platformDAO.findByAgentToken(agentToken);
        if (platforms == null || platforms.size() == 0) {
            throw new PlatformNotFoundException("Platform with agent token " + agentToken + " not found");
        }

        List<Integer> pks = new ArrayList<Integer>();
        for (Platform plat : platforms) {
            pks.add(plat.getId());
        }
        return pks;
    }

    /**
     * Get the platform that hosts the server that provides the specified
     * service.
     * 
     * 
     * @param subject The subject trying to list services.
     * @param serviceId service ID.
     * @return the Platform
     */
    public PlatformValue getPlatformByService(AuthzSubject subject, Integer serviceId)
        throws PlatformNotFoundException, PermissionException {
        Platform p = platformDAO.findByServiceId(serviceId);
        if (p == null) {
            throw new PlatformNotFoundException("platform for service " + serviceId + " not found");
        }
        // now check if the user can see this at all
        permissionManager.checkViewPermission(subject, p.getEntityId());
        return p.getPlatformValue();
    }

    /**
     * Get the platform ID that hosts the server that provides the specified
     * service.
     * 
     * 
     * @param serviceId service ID.
     * @return the Platform
     */
    public Integer getPlatformIdByService(Integer serviceId) throws PlatformNotFoundException {
        Platform p = platformDAO.findByServiceId(serviceId);
        if (p == null) {
            throw new PlatformNotFoundException("platform for service " + serviceId + " not found");
        }
        return p.getId();
    }

    /**
     * Get the platform for a server.
     * 
     * 
     * @param subject The subject trying to list services.
     * @param serverId Server ID.
     */
    public PlatformValue getPlatformByServer(AuthzSubject subject, Integer serverId) throws PlatformNotFoundException,
        PermissionException {
        Server server = serverDAO.get(serverId);

        if (server == null || server.getPlatform() == null) {
            // This should throw server not found. Servers always have
            // platforms..
            throw new PlatformNotFoundException("platform for server " + serverId + " not found");
        }

        Platform p = server.getPlatform();
        permissionManager.checkViewPermission(subject, p.getEntityId());
        return p.getPlatformValue();
    }

    /**
     * Get the platform ID for a server.
     * 
     * 
     * @param serverId Server ID.
     */
    public Integer getPlatformIdByServer(Integer serverId) throws PlatformNotFoundException {
        Server server = serverDAO.get(serverId);

        if (server == null)
            throw new PlatformNotFoundException("platform for server " + serverId + " not found");

        return server.getPlatform().getId();
    }

    /**
     * Get the platforms for a list of servers.
     * 
     * 
     * @param subject The subject trying to list services.
     */
    public PageList<PlatformValue> getPlatformsByServers(AuthzSubject subject, List<AppdefEntityID> sIDs)
        throws PlatformNotFoundException, PermissionException {
        Set<Integer> authzPks;
        try {
            authzPks = new HashSet<Integer>(getViewablePlatformPKs(subject));
        } catch (FinderException exc) {
            return new PageList<PlatformValue>();
        }

        Integer[] ids = new Integer[sIDs.size()];
        int i = 0;
        for (Iterator<AppdefEntityID> it = sIDs.iterator(); it.hasNext(); i++) {
            AppdefEntityID svrId = it.next();
            ids[i] = svrId.getId();
        }

        List<Platform> foundPlats = platformDAO.findByServers(ids);

        ArrayList<Platform> platforms = new ArrayList<Platform>();
        for (Platform platform : foundPlats) {
            if (authzPks.contains(platform.getId())) {
                platforms.add(platform);
            }
        }

        return valuePager.seek(platforms, null);
    }

    /**
     * Get all platforms by application.
     * 
     * 
     * 
     * @param subject The subject trying to list services.
     * @param appId Application ID. but when they are, they should live
     *        somewhere in appdef/shared so that clients can use them too.
     * @return A List of ApplicationValue objects representing all of the
     *         services that the given subject is allowed to view.
     */
    public PageList<PlatformValue> getPlatformsByApplication(AuthzSubject subject, Integer appId, PageControl pc)
        throws ApplicationNotFoundException, PlatformNotFoundException, PermissionException {

        Application appLocal = applicationDAO.get(appId);
        if (appLocal == null) {
            throw new ApplicationNotFoundException(appId);
        }

        Collection<PlatformValue> platCollection = new ArrayList<PlatformValue>();
        // XXX Call to authz, get the collection of all services
        // that we are allowed to see.
        // OR, alternatively, find everything, and then call out
        // to authz in batches to find out which ones we are
        // allowed to return.

        Collection<AppService> serviceCollection = appLocal.getAppServices();
        Iterator<AppService> it = serviceCollection.iterator();
        while (it != null && it.hasNext()) {
            AppService appService = it.next();

            if (appService.isIsGroup()) {
                Collection<Service> services = serviceManager.getServiceCluster(appService.getResourceGroup())
                    .getServices();

                for (Service service : services) {

                    PlatformValue pValue = getPlatformByService(subject, service.getId());
                    if (!platCollection.contains(pValue)) {
                        platCollection.add(pValue);
                    }
                }
            } else {
                Integer serviceId = appService.getService().getId();
                PlatformValue pValue = getPlatformByService(subject, serviceId);
                // Fold duplicate platforms
                if (!platCollection.contains(pValue)) {
                    platCollection.add(pValue);
                }
            }
        }

        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platCollection, pc);
    }

    /**
     * builds a list of resource types from the list of resources
     * @param resources - {@link Collection} of {@link AppdefResource}
     * @param {@link Collection} of {@link AppdefResourceType}
     */
    private Collection<AppdefResourceType> filterResourceTypes(Collection<AppdefResource> resources) {
        final Set<AppdefResourceType> resTypes = new HashSet<AppdefResourceType>();
        for (final AppdefResource o : resources) {

            if (o == null) {
                continue;
            }
            final AppdefResourceType rt = o.getAppdefResourceType();
            if (rt != null) {
                resTypes.add(rt);
            }
        }
        final List<AppdefResourceType> rtn = new ArrayList<AppdefResourceType>(resTypes);
        Collections.sort(rtn, new Comparator<AppdefResourceType>() {
            private String getName(AppdefResourceType obj) {

                return ((AppdefResourceType) obj).getSortName();

            }

            public int compare(AppdefResourceType o1, AppdefResourceType o2) {
                return getName(o1).compareTo(getName(o2));
            }
        });
        return rtn;
    }

    protected List<Integer> getViewablePlatformPKs(AuthzSubject who) throws FinderException, PermissionException {
        // now get a list of all the viewable items

        Operation op = getOperationByName(resourceManager.findResourceTypeByName(AuthzConstants.platformResType),
            AuthzConstants.platformOpViewPlatform);
        return permissionManager.findOperationScopeBySubject(who, op.getId());
    }

    /**
     * Find an operation by name inside a ResourcetypeValue object
     */
    protected Operation getOperationByName(ResourceType rtV, String opName) throws PermissionException {
        Collection<Operation> ops = rtV.getOperations();
        for (Operation op : ops) {
            if (op.getName().equals(opName)) {
                return op;
            }
        }
        throw new PermissionException("Operation: " + opName + " not valid for ResourceType: " + rtV.getName());
    }

    /**
     * Get server IDs by server type and platform.
     * 
     * 
     * 
     * @param subject The subject trying to list servers.
     * @return A PageList of ServerValue objects representing servers on the
     *         specified platform that the subject is allowed to view.
     */
    public Integer[] getPlatformIds(AuthzSubject subject, Integer platTypeId) throws PermissionException {

        try {

            Collection<Platform> platforms = platformDAO.findByType(platTypeId);
            Collection<Integer> platIds = new ArrayList<Integer>();

            // now get the list of PKs
            Collection<Integer> viewable = getViewablePlatformPKs(subject);
            // and iterate over the ejbList to remove any item not in the
            // viewable list
            for (Platform aEJB : platforms) {

                if (viewable.contains(aEJB.getId())) {
                    // remove the item, user cant see it
                    platIds.add(aEJB.getId());
                }
            }

            return (Integer[]) platIds.toArray(new Integer[0]);
        } catch (FinderException e) {
            // There are no viewable platforms
            return new Integer[0];
        }
    }

    /**
     * Get server IDs by server type and platform.
     * 
     * 
     * 
     * @param subject The subject trying to list servers.
     * @param pc The page control.
     * @return A PageList of ServerValue objects representing servers on the
     *         specified platform that the subject is allowed to view.
     */
    public List<Platform> getPlatformsByType(AuthzSubject subject, String type) throws PermissionException,
        InvalidAppdefTypeException {
        try {
            PlatformType ptype = platformTypeDAO.findByName(type);
            if (ptype == null) {
                return new PageList<Platform>();
            }

            List<Platform> platforms = platformDAO.findByType(ptype.getId());
            if (platforms.size() == 0) {
                // There are no viewable platforms
                return platforms;
            }
            // now get the list of PKs
            Collection<Integer> viewable = getViewablePlatformPKs(subject);
            // and iterate over the ejbList to remove any item not in the
            // viewable list
            for (Iterator<Platform> it = platforms.iterator(); it.hasNext();) {
                Platform aEJB = it.next();
                if (!viewable.contains(aEJB.getId())) {
                    // remove the item, user can't see it
                    it.remove();
                }
            }

            return platforms;
        } catch (FinderException e) {
            // There are no viewable platforms
            return new PageList<Platform>();
        }
    }

    /**
     * Get the scope of viewable platforms for a given user
     * @param whoami - the user
     * @return List of PlatformLocals for which subject has
     *         AuthzConstants.platformOpViewPlatform XXX scottmf, this needs to
     *         be completely rewritten. It should not query all the platforms
     *         and mash that list together with the viewable resources. This
     *         will potentially bloat the session with useless pojos, not to
     *         mention the poor performance implications. Instead it should get
     *         the viewable resources then select those platform where id in
     *         (:pids) OR look them up from cache.
     */
    protected Collection<Platform> getViewablePlatforms(AuthzSubject whoami, PageControl pc) throws FinderException,
        PermissionException, NamingException {
        // first find all, based on the sorting attribute passed in, or
        // with no sorting if the page control is null
        Collection<Platform> platforms;
        // if page control is null, find all platforms
        if (pc == null) {
            platforms = platformDAO.findAll();
        } else {
            pc = PageControl.initDefaults(pc, SortAttribute.RESOURCE_NAME);
            int attr = pc.getSortattribute();
            switch (attr) {
                case SortAttribute.RESOURCE_NAME:
                    platforms = platformDAO.findAll_orderName(pc.isAscending());
                    break;
                case SortAttribute.CTIME:
                    platforms = platformDAO.findAll_orderCTime(pc.isAscending());
                    break;
                default:
                    throw new FinderException("Invalid sort attribute: " + attr);
            }
        }
        // now get the list of PKs
        Set<Integer> viewable = new HashSet<Integer>(getViewablePlatformPKs(whoami));
        // and iterate over the ejbList to remove any item not in the
        // viewable list
        for (Iterator<Platform> i = platforms.iterator(); i.hasNext();) {
            Platform platform = i.next();
            if (!viewable.contains(platform.getId())) {
                // remove the item, user cant see it
                i.remove();
            }
        }
        return platforms;
    }

    /**
     * Get the platforms that have an IP with the specified address. If no
     * matches are found, this method DOES NOT throw a
     * PlatformNotFoundException, rather it returns an empty PageList.
     * 
     * 
     */
    public PageList<PlatformValue> findPlatformsByIpAddr(AuthzSubject subject, String addr, PageControl pc)
        throws PermissionException {
        Collection<Platform> platforms = platformDAO.findByIpAddr(addr);
        if (platforms.size() == 0) {
            return new PageList<PlatformValue>();
        }
        return valuePager.seek(platforms, pc);
    }

    /**
     * @param subj
     * @param pType platform type
     * @param nameRegEx regex which matches either the platform fqdn or the
     *        resource sortname XXX scottmf need to add permission checking
     * 
     */
    public List<Platform> findPlatformPojosByTypeAndName(AuthzSubject subj, Integer pType, String regEx) {
        return platformDAO.findByTypeAndRegEx(pType, regEx);
    }

    /**
     * @param subj
     * @param platformTypeIds List<Integer> of platform type ids
     * @param hasChildren indicates whether the platform is the parent of a
     *        network hierarchy
     * @return a list of {@link Platform}s
     * 
     */
    @SuppressWarnings("unchecked")
    public List<Platform> findParentPlatformPojosByNetworkRelation(AuthzSubject subj, List<Integer> platformTypeIds,
                                                                   String platformName, Boolean hasChildren) {
        List<PlatformType> unsupportedPlatformTypes = new ArrayList<PlatformType>(findUnsupportedPlatformTypes());
        List<Integer> pTypeIds = new ArrayList<Integer>();

        if (platformTypeIds != null && !platformTypeIds.isEmpty()) {
            for (Integer pTypeId : platformTypeIds) {

                PlatformType pType = findPlatformType(pTypeId);
                if (unsupportedPlatformTypes.contains(pType)) {
                    pTypeIds.add(pTypeId);
                }
            }
            if (pTypeIds.isEmpty()) {
                return Collections.EMPTY_LIST;
            }
        } else {
            // default values
            for (PlatformType pType : unsupportedPlatformTypes) {

                pTypeIds.add(pType.getId());
            }
        }

        return platformDAO.findParentByNetworkRelation(pTypeIds, platformName, hasChildren);
    }

    /**
     * @param subj
     * @param platformTypeIds List<Integer> of platform type ids
     * @return a list of {@link Platform}s
     * 
     */
    @SuppressWarnings("unchecked")
    public List<Platform> findPlatformPojosByNoNetworkRelation(AuthzSubject subj, List<Integer> platformTypeIds,
                                                               String platformName) {
        List<PlatformType> supportedPlatformTypes = new ArrayList<PlatformType>(findSupportedPlatformTypes());
        List<Integer> pTypeIds = new ArrayList<Integer>();

        if (platformTypeIds != null && !platformTypeIds.isEmpty()) {
            for (Integer pTypeId : platformTypeIds) {

                PlatformType pType = findPlatformType(pTypeId);
                if (supportedPlatformTypes.contains(pType)) {
                    pTypeIds.add(pTypeId);
                }
            }
            if (pTypeIds.isEmpty()) {
                return Collections.EMPTY_LIST;
            }
        } else {
            // default values
            for (PlatformType pType : supportedPlatformTypes) {

                pTypeIds.add(pType.getId());
            }
        }

        return platformDAO.findByNoNetworkRelation(pTypeIds, platformName);
    }

    /**
     * Get the platforms that have an IP with the specified address.
     * 
     * @return a list of {@link Platform}s
     * 
     */
    public Collection<Platform> findPlatformPojosByIpAddr(String addr) {
        return platformDAO.findByIpAddr(addr);
    }

    /**
     * 
     */
    public Collection<Platform> findDeletedPlatforms() {
        return platformDAO.findDeletedPlatforms();
    }

    /**
     * Update an existing Platform. Requires all Ip's to have been re-added via
     * the platformValue.addIpValue(IpValue) method due to bug 4924
     * 
     * @param existing - the value object for the platform you want to save
     * 
     */
    public Platform updatePlatformImpl(AuthzSubject subject, PlatformValue existing) throws UpdateException,
        PermissionException, AppdefDuplicateNameException, PlatformNotFoundException, AppdefDuplicateFQDNException,
        ApplicationException {
        permissionManager.checkPermission(subject, existing.getEntityId(), AuthzConstants.platformOpModifyPlatform);
        existing.setModifiedBy(subject.getName());
        existing.setMTime(new Long(System.currentTimeMillis()));
        trimStrings(existing);

        Platform plat = platformDAO.findById(existing.getId());

        if (existing.getCpuCount() == null) {
            // cpu count is no longer an option in the UI
            existing.setCpuCount(plat.getCpuCount());
        }

        if (plat.matchesValueObject(existing)) {
            log.debug("No changes found between value object and entity");
            return plat;
        } else {
            int newCount = existing.getCpuCount().intValue();
            int prevCpuCount = plat.getCpuCount().intValue();
            if (newCount > prevCpuCount) {
                getCounter().addCPUs(newCount - prevCpuCount);
            }

            if (!(existing.getName().equals(plat.getName()))) {
                if (platformDAO.findByName(existing.getName()) != null)
                    // duplicate found, throw a duplicate object exception
                    throw new AppdefDuplicateNameException();
            }

            if (!(existing.getFqdn().equals(plat.getFqdn()))) {
                if (platformDAO.findByFQDN(existing.getFqdn()) != null)
                    // duplicate found, throw a duplicate object exception
                    throw new AppdefDuplicateFQDNException();
            }

            // See if we need to create an AIPlatform
            if (existing.getAgent() != null) {
                if (plat.getAgent() == null) {
                    // Create AIPlatform for manually created platform

                    AIPlatformValue aiPlatform = new AIPlatformValue();
                    aiPlatform.setFqdn(existing.getFqdn());
                    aiPlatform.setName(existing.getName());
                    aiPlatform.setDescription(existing.getDescription());
                    aiPlatform.setPlatformTypeName(existing.getPlatformType().getName());
                    aiPlatform.setCertdn(existing.getCertdn());
                    aiPlatform.setAgentToken(existing.getAgent().getAgentToken());

                    IpValue[] ipVals = existing.getIpValues();
                    for (int i = 0; i < ipVals.length; i++) {
                        AIIpValue aiIpVal = new AIIpValue();
                        aiIpVal.setAddress(ipVals[i].getAddress());
                        aiIpVal.setNetmask(ipVals[i].getNetmask());
                        aiIpVal.setMACAddress(ipVals[i].getMACAddress());
                        aiPlatform.addAIIpValue(aiIpVal);
                    }

                    getAIQueueManager().queue(subject, aiPlatform, false, false, true);
                } else if (!plat.getAgent().equals(existing.getAgent())) {
                    // Need to enqueue the ResourceUpdatedZevent if the
                    // agent changed to get the metrics scheduled
                    List<ResourceUpdatedZevent> events = new ArrayList<ResourceUpdatedZevent>();
                    events.add(new ResourceUpdatedZevent(subject, plat.getEntityId()));
                    for (Server svr : plat.getServers()) {

                        events.add(new ResourceUpdatedZevent(subject, svr.getEntityId()));

                        for (Service svc : svr.getServices()) {

                            events.add(new ResourceUpdatedZevent(subject, svc.getEntityId()));
                        }
                    }

                    zeventManager.enqueueEventsAfterCommit(events);
                }
            }
            platformDAO.updatePlatform(plat, existing);
            return plat;
        }
    }

    /**
     * Update an existing Platform. Requires all Ip's to have been re-added via
     * the platformValue.addIpValue(IpValue) method due to bug 4924
     * 
     * @param existing - the value object for the platform you want to save
     * 
     */
    public Platform updatePlatform(AuthzSubject subject, PlatformValue existing) throws UpdateException,
        PermissionException, AppdefDuplicateNameException, PlatformNotFoundException, AppdefDuplicateFQDNException,
        ApplicationException {
        return updatePlatformImpl(subject, existing);
    }

    /**
     * Private method to validate a new PlatformValue object
     * 
     * @throws ValidationException
     */
    private void validateNewPlatform(PlatformValue pv) throws ValidationException {
        String msg = null;
        // first check if its new
        if (pv.idHasBeenSet()) {
            msg = "This platform is not new. It has id: " + pv.getId();
        }
        // else if(someotherthing) ...

        // Now check if there's a msg set and throw accordingly
        if (msg != null) {
            throw new ValidationException(msg);
        }
    }

    /**
     * Create the Authz resource and verify that the subject has the
     * createPlatform permission.
     * 
     * @param subject - the user creating
     */
    private void createAuthzPlatform(AuthzSubject subject, Platform platform) throws FinderException,
        PermissionException {
        log.debug("Begin Authz CreatePlatform");
        // check to make sure the user has createPlatform permission
        // on the root resource type
        permissionManager.checkCreatePlatformPermission(subject);

        ResourceType platProtoType = resourceManager.findResourceTypeByName(AuthzConstants.platformPrototypeTypeName);
        Resource proto = resourceManager.findResourceByInstanceId(platProtoType, platform.getPlatformType().getId());
        log.debug("User has permission to create platform. " + "Adding AuthzResource");
        Resource resource = resourceManager.createResource(subject, resourceManager
            .findResourceTypeByName(AuthzConstants.platformResType), proto, platform.getId(), platform.getName(),
            false, null);
        platform.setResource(resource);
    }

    /**
     * DevNote: This method was refactored out of updatePlatformTypes. It does
     * not work.
     * 
     * 
     */
    public void deletePlatformType(PlatformType pt) throws VetoException, RemoveException {

        Resource proto = resourceManager.findResourceByInstanceId(AuthzConstants.authzPlatformProto, pt.getId());
        AuthzSubject overlord = authzSubjectManager.getOverlordPojo();

        try {
            log.debug("Removing PlatformType: " + pt.getName());

            resourceGroupManager.removeGroupsCompatibleWith(proto);

            // Remove all platforms
            for (Platform platform : pt.getPlatforms()) {

                try {
                    removePlatform(overlord, platform);
                } catch (PlatformNotFoundException e) {
                    assert false : "Delete based on a platform should not " + "result in PlatformNotFoundException";
                }
            }
        } catch (PermissionException e) {
            assert false : "Overlord should not run into PermissionException";
        }

        // Need to remove all server types, too

        for (ServerType st : pt.getServerTypes()) {
            serverManager.deleteServerType(st, overlord, resourceGroupManager, resourceManager);
        }

        // TODO: Need to remove the Resource prototype associated with this
        // platform.
        platformTypeDAO.remove(pt);

        resourceManager.removeResource(overlord, proto);
    }

    /**
     * Update platform types
     * 
     * 
     */
    public void updatePlatformTypes(String plugin, PlatformTypeInfo[] infos) throws CreateException, FinderException,
        RemoveException, VetoException {
        // First, put all of the infos into a Hash
        HashMap<String, PlatformTypeInfo> infoMap = new HashMap<String, PlatformTypeInfo>();
        for (int i = 0; i < infos.length; i++) {
            infoMap.put(infos[i].getName(), infos[i]);
        }

        Collection<PlatformType> curPlatforms = platformTypeDAO.findByPlugin(plugin);

        for (PlatformType ptlocal : curPlatforms) {

            String localName = ptlocal.getName();
            PlatformTypeInfo pinfo = (PlatformTypeInfo) infoMap.remove(localName);

            // See if this exists
            if (pinfo == null) {
                deletePlatformType(ptlocal);
            } else {
                String curName = ptlocal.getName();
                String newName = pinfo.getName();

                // Just update it
                log.debug("Updating PlatformType: " + localName);

                if (!newName.equals(curName))
                    ptlocal.setName(newName);
            }
        }

        Resource prototype = resourceManager.findRootResource();
        AuthzSubject overlord = authzSubjectManager.getOverlordPojo();

        // Now create the left-overs
        for (PlatformTypeInfo pinfo : infoMap.values()) {

            log.debug("Creating new PlatformType: " + pinfo.getName());
            PlatformType pt = platformTypeDAO.create(pinfo.getName(), plugin);
            resourceManager.createResource(overlord, resourceManager
                .findResourceTypeByName(AuthzConstants.platformPrototypeTypeName), prototype, pt.getId(), pt.getName(),
                false, null);
        }
    }

    /**
     * Update an existing appdef platform with data from an AI platform.
     * 
     * @param aiplatform the AI platform object to use for data
     * 
     */
    public void updateWithAI(AIPlatformValue aiplatform, AuthzSubject subj) throws PlatformNotFoundException,
        ApplicationException {

        String certdn = aiplatform.getCertdn();
        String fqdn = aiplatform.getFqdn();

        Platform platform = this.getPlatformByAIPlatform(subj, aiplatform);
        if (platform == null) {
            throw new PlatformNotFoundException("Platform not found with either FQDN: " + fqdn + " nor CertDN: " +
                                                certdn);
        }
        int prevCpuCount = platform.getCpuCount().intValue();
        Integer count = aiplatform.getCpuCount();
        if ((count != null) && (count.intValue() > prevCpuCount)) {
            getCounter().addCPUs(aiplatform.getCpuCount().intValue() - prevCpuCount);
        }

        // Get the FQDN before we update
        String prevFqdn = platform.getFqdn();

        platform.updateWithAI(aiplatform, subj.getName(), platform.getResource());

        // If FQDN has changed, we need to update servers' auto-inventory tokens
        if (!prevFqdn.equals(platform.getFqdn())) {
            for (Server server : platform.getServers()) {

                if (server.getAutoinventoryIdentifier().startsWith(prevFqdn)) {
                    String newAID = server.getAutoinventoryIdentifier().replace(prevFqdn, fqdn);
                    server.setAutoinventoryIdentifier(newAID);
                }
            }
        }

        // need to check if IPs have changed, if so update Agent
        List<AIIpValue> ips = Arrays.asList(aiplatform.getAIIpValues());

        Agent currAgent = platform.getAgent();
        boolean removeCurrAgent = false;
        for (AIIpValue ip : ips) {

            if (ip.getQueueStatus() == AIQueueConstants.Q_STATUS_ADDED) {
                try {
                    Agent agent = agentManager.getAgent(aiplatform.getAgentToken());
                    platform.setAgent(agent);
                    enableMeasurements(subj, platform);
                } catch (AgentNotFoundException e) {
                    throw new ApplicationException(e.getMessage(), e);
                }
            } else if (ip.getQueueStatus() == AIQueueConstants.Q_STATUS_REMOVED &&
                       currAgent.getAddress().equals(ip.getAddress())) {
                removeCurrAgent = true;
            }
        }
        if (removeCurrAgent) {
            agentManager.removeAgent(currAgent);
        }
    }

    private void enableMeasurements(AuthzSubject subj, Platform platform) {
        List<AppdefEntityID> eids = new ArrayList<AppdefEntityID>();
        eids.add(platform.getEntityId());
        Collection<Server> servers = platform.getServers();
        for (Server server : servers) {

            eids.add(server.getEntityId());
            Collection<Service> services = server.getServices();
            for (Service service : services) {

                eids.add(service.getEntityId());
            }
        }
        AgentScheduleSyncZevent event = new AgentScheduleSyncZevent(eids);
        zeventManager.enqueueEventAfterCommit(event);
    }

    /**
     * Used to trim all string based attributes present in a platform value
     * object
     */
    private void trimStrings(PlatformValue plat) {
        if (plat.getName() != null) {
            plat.setName(plat.getName().trim());
        }
        if (plat.getCertdn() != null) {
            plat.setCertdn(plat.getCertdn().trim());
        }
        if (plat.getCommentText() != null) {
            plat.setCommentText(plat.getCommentText().trim());
        }
        if (plat.getDescription() != null) {
            plat.setDescription(plat.getDescription().trim());
        }
        if (plat.getFqdn() != null) {
            plat.setFqdn(plat.getFqdn().trim());
        }
        // now the Ips
        for (IpValue ip : plat.getAddedIpValues()) {

            if (ip.getAddress() != null) {
                ip.setAddress(ip.getAddress().trim());
            }
            if (ip.getMACAddress() != null) {
                ip.setMACAddress(ip.getMACAddress().trim());
            }
            if (ip.getNetmask() != null) {
                ip.setNetmask(ip.getNetmask().trim());
            }
        }
        // and the saved ones in case this is an update
        for (int i = 0; i < plat.getIpValues().length; i++) {
            IpValue ip = plat.getIpValues()[i];
            if (ip.getAddress() != null) {
                ip.setAddress(ip.getAddress().trim());
            }
            if (ip.getMACAddress() != null) {
                ip.setMACAddress(ip.getMACAddress().trim());
            }
            if (ip.getNetmask() != null) {
                ip.setNetmask(ip.getNetmask().trim());
            }
        }
    }

    /**
     * Add an IP to a platform
     * 
     * 
     */
    public Ip addIp(Platform platform, String address, String netmask, String macAddress) {
        return platform.addIp(address, netmask, macAddress);
    }

    /**
     * Update an IP on a platform
     * 
     * 
     */
    public Ip updateIp(Platform platform, String address, String netmask, String macAddress) {
        return platform.updateIp(address, netmask, macAddress);
    }

    /**
     * Remove an IP on a platform
     * 
     * 
     */
    public void removeIp(Platform platform, String address, String netmask, String macAddress) {
        Ip ip = platform.removeIp(address, netmask, macAddress);
        if (ip != null) {
            platformDAO.remove(ip);
        }
    }

    /**
     * Returns a list of 2 element arrays. The first element is the name of the
     * platform type, the second element is the # of platforms of that type in
     * the inventory.
     * 
     * 
     */
    public List<Object[]> getPlatformTypeCounts() {
        return platformDAO.getPlatformTypeCounts();
    }

    /**
     * 
     */
    public Number getPlatformCount() {
        return platformDAO.getPlatformCount();
    }

    /**
     * 
     */
    public Number getCpuCount() {
        return platformDAO.getCpuCount();
    }

    public static PlatformManager getOne() {
        return Bootstrap.getBean(PlatformManager.class);
    }

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        valuePager = Pager.getPager(VALUE_PROCESSOR);
    }

}
