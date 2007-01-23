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

package org.hyperic.hq.appdef.server.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.server.session.Platform;
import org.hyperic.hq.appdef.server.session.PlatformType;
import org.hyperic.hq.appdef.shared.AIIpValue;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQueueManagerLocal;
import org.hyperic.hq.appdef.shared.AIQueueManagerUtil;
import org.hyperic.hq.appdef.shared.AppdefDuplicateFQDNException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateNameException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupManagerUtil;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.ApplicationNotFoundException;
import org.hyperic.hq.appdef.shared.IpValue;
import org.hyperic.hq.appdef.shared.PlatformLightValue;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformTypeValue;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.appdef.shared.ServiceNotFoundException;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformManagerUtil;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.appdef.AppService;
import org.hyperic.hq.appdef.ConfigResponseDB;
import org.hyperic.hq.appdef.Ip;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.shared.ProductProperties;
import org.hyperic.hq.product.PlatformTypeInfo;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;
import org.hyperic.hq.dao.PlatformDAO;
import org.hyperic.hq.dao.PlatformTypeDAO;
import org.hyperic.hq.dao.ConfigResponseDAO;
import org.hyperic.hq.dao.ApplicationDAO;
import org.hyperic.hq.zevents.ZeventManager;
import org.hyperic.hq.autoinventory.AIPlatform;
import org.hyperic.dao.DAOFactory;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.NonUniqueResultException;

/**
 * This class is responsible for managing Platform objects in appdef
 * and their relationships
 * @ejb:bean name="PlatformManager"
 *      jndi-name="ejb/appdef/PlatformManager"
 *      local-jndi-name="LocalPlatformManager"
 *      view-type="local"
 *      type="Stateless"
 * @ejb:util generate="physical"
 * @ejb:transaction type="REQUIRED"
 */
public class PlatformManagerEJBImpl extends AppdefSessionEJB
    implements SessionBean 
{
    private String ctx = PlatformManagerEJBImpl.class.getName();
    private final Log _log = LogFactory.getLog(ctx);
        
    private final String VALUE_PROCESSOR =
        PagerProcessor_platform.class.getName();
    private Pager valuePager;
    private PlatformCounter counter;

     /**
     * Find a PlatformType by id
     * @ejb:interface-method
     */
    public PlatformType findPlatformTypeById(Integer id)
        throws PlatformNotFoundException {
        try {
            return getPlatformTypeDAO().findById(id);
        } catch (ObjectNotFoundException e) {
            throw new PlatformNotFoundException(id);
        }
    }

    /**
     * Find a PlatformTypeValue by id.
     * @deprecated Use findPlatformTypeById instead.
     * @ejb:interface-method
     */
    public PlatformTypeValue findPlatformTypeValueById(Integer id)
        throws PlatformNotFoundException {
        return findPlatformTypeById(id).getPlatformTypeValue();
    }

    /**
     * Find a platform type by name
     * @param type - name of the platform type
     * @return platformTypeValue 
     * @ejb:interface-method
     */
    public PlatformTypeValue findPlatformTypeByName(String type) 
        throws PlatformNotFoundException
    {
        PlatformType ptype = getPlatformTypeDAO().findByName(type);
        if (ptype == null) {
            throw new PlatformNotFoundException(type);
        }
        return ptype.getPlatformTypeValue();
    }

    /**
     * Find all platform types
     * @return List of PlatformTypeValues
     * @ejb:interface-method
     */
    public PageList getAllPlatformTypes(AuthzSubjectValue subject,
                                        PageControl pc) 
    {
        Collection platTypes = getPlatformTypeDAO().findAll();
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platTypes, pc);
    }

    /**
     * Find viewable platform types
     * @return List of PlatformTypeValues
     * @ejb:interface-method
     */
    public PageList getViewablePlatformTypes(AuthzSubjectValue subject,
                                             PageControl pc) 
        throws FinderException, PermissionException {
        
        // build the platform types from the visible list of platforms
        Collection platforms;
        try {
            platforms = getViewablePlatforms(subject, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }

        Collection platTypes = filterResourceTypes(platforms);
        
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platTypes, pc);
    }

    /**
     * Get PlatformPluginName for an entity id.
     * There is no authz in this method because it is not needed.
     * @return name of the plugin for the entity's platform
     * such as "Apache 2.0 Linux". It is used as to look up plugins
     * via a generic plugin manager.
     * @throws CreateException
     * @throws ValidationException
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public String getPlatformPluginName(AppdefEntityID id) 
        throws AppdefEntityNotFoundException {
        
        try {
            PlatformLightValue pv;
            String typeName;
            if(id.getType() == AppdefEntityConstants.APPDEF_TYPE_SERVICE) {
                // look up the service ejb
                try {
                    Service service = getServiceDAO().findById(id.getId());
                    pv = service.getServer().getPlatform().getPlatformLightValue();
                    typeName = service.getServiceType().getName();
                } catch (ObjectNotFoundException e) {
                    throw new ServiceNotFoundException(e.getMessage());
                }                
            }
            else if(id.getType() == AppdefEntityConstants.APPDEF_TYPE_SERVER) {
                // look up the server
                try {
                    Server server = getServerDAO().findById(id.getId());
                    pv = server.getPlatform().getPlatformLightValue();
                    typeName = server.getServerType().getName();
                } catch (ObjectNotFoundException e) {
                    throw new ServerNotFoundException(e.getMessage());
				}
            }
            else if(id.getType() == AppdefEntityConstants.APPDEF_TYPE_PLATFORM) {
                try {
					Platform platform = getPlatformDAO().findById(id.getId());
					pv = platform.getPlatformLightValue();    
					typeName = pv.getPlatformType().getName();
				} catch (ObjectNotFoundException e) {
                    throw new PlatformNotFoundException(e.getMessage());
				}
            }
            else if(id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
                try {
                    AppdefGroupValue agv = AppdefGroupManagerUtil.getLocalHome()   
                        .create().findGroup(getOverlord(), id);
                    return agv.getGroupTypeLabel();
                } catch (PermissionException e) {
                    // never happens... its the overlord.
                    throw new SystemException(e);
                } catch (CreateException e) {
                    throw new SystemException(e);
                }
            }
            else {
                throw new IllegalArgumentException("Unsupported AppdefEntityType: "
                    + id);
            }
            if (id.isPlatform()) {
                return typeName;
            }
            else {
                return typeName + " " + pv.getPlatformType().getName();
            }
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Create a platform type
     * @return PlatformTypeValue
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRESNEW"
     */
    public Integer createPlatformType(AuthzSubjectValue subject,
                                      PlatformTypeValue pType)
        throws CreateException, ValidationException 
    {
        return getPlatformTypeDAO().create(pType).getId();
    }

    /**
     * Delete a platform
     * @param subject The user performing the delete operation.
     * @param id - The id of the Platform
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removePlatform(AuthzSubjectValue subject, Integer id)
        throws RemoveException, PlatformNotFoundException, PermissionException 
    {
        try {
            // find it
            Platform platform = getPlatformDAO().findById(id);
            removePlatform(subject, platform);
        } catch (ObjectNotFoundException e) {
            throw new PlatformNotFoundException(id);
        }
    }
    
    private void removePlatform(AuthzSubjectValue subject,
                                Platform platform)
        throws RemoveException, PermissionException, PlatformNotFoundException
    {
        try {
            checkRemovePermission(subject, platform.getEntityId());

            // keep the configresponseId so we can remove it later
            Integer cid = platform.getConfigResponseId();

            ServerManagerLocal srvMgr = getServerMgrLocal();
            // Server manager will update the collection, so we need to copy
            Collection servers = new ArrayList(platform.getServers());
            for (Iterator i = servers.iterator(); i.hasNext(); ) {
                Server server = (Server)i.next();
                try {
                    // Remove servers
                    srvMgr.removeServer(subject, server);
                } catch (ServerNotFoundException e) {
                    _log.error("Unable to remove server", e);
                }
            }

            // now remove the resource for the platform
            removeAuthzResource(platform.getEntityId());

            getPlatformDAO().remove(platform);

            // remove the config response
            if (cid != null) {
                try {
                    ConfigResponseDAO cdao =
                        DAOFactory.getDAOFactory().getConfigResponseDAO();
                    cdao.remove(cdao.findById(cid));
                } catch (ObjectNotFoundException e) {
                    // OK, no config response, just log it
                    _log.warn("Invalid config ID " + cid);
                }
            }

            // remove custom properties
            deleteCustomProperties(AppdefEntityConstants.APPDEF_TYPE_PLATFORM, 
                                   platform.getId().intValue());

            // Send resource delete event
            ResourceDeletedZevent zevent =
                new ResourceDeletedZevent(subject, platform.getEntityId());
            ZeventManager.getInstance().enqueueEventAfterCommit(zevent);
        } catch (RemoveException e) {
            _log.debug("Error while removing Platform");
            rollback();
            throw e;
        } catch (PermissionException e) {
            _log.debug("Error while removing Platform");
            rollback();
            throw e;
        } catch (FinderException e) {
            throw new PlatformNotFoundException(platform.getId());
        }
    } 

    /**
     * Create a Platform of a specified type
     * @return PlatformValue - the saved value object
     * @exception CreateException - if it fails to add the platform
     * @exception ValidationException - if the subject is not allowed
     * to create Platforms
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public Integer createPlatform(AuthzSubjectValue subject,
                                  Integer platformTypeId,
                                  PlatformValue pValue, Integer agentPK)
        throws CreateException, ValidationException, PermissionException,
               AppdefDuplicateNameException, AppdefDuplicateFQDNException,
               ApplicationException 
    {
        // check if the object already exists
        try {
            try {
                getPlatformByName(getOverlord(), pValue.getName());
                throw new AppdefDuplicateNameException();
            } catch ( PlatformNotFoundException e ) {
                // ok
            }
            try {
                findPlatformByFqdn(getOverlord(), pValue.getFqdn());
                throw new AppdefDuplicateFQDNException();
            } catch ( PlatformNotFoundException e ) {
                // ok
            }
        } catch (PermissionException e) {
            // fall through, will validate later
        }
        
        try {
            ConfigResponseDAO configDAO = getConfigResponseDAO();
            ConfigResponseDB config;
            Platform platform;
            Agent agent = null;
            
            if (agentPK != null)
                agent = getAgentDAO().findById(agentPK);
            
            if (pValue.getConfigResponseId() == null)
                config = configDAO.createPlatform();
            else
                config = configDAO.findById(pValue.getConfigResponseId());
                
            trimStrings(pValue);
            counter.addCPUs(pValue.getCpuCount().intValue());
            validateNewPlatform(pValue);
            PlatformTypeDAO ptLHome =
                DAOFactory.getDAOFactory().getPlatformTypeDAO();
            PlatformType pType = ptLHome.findById(platformTypeId);

            pValue.setOwner(subject.getName());
            pValue.setModifiedBy(subject.getName());

            platform = pType.create(pValue, agent, config);
            getPlatformDAO().save(platform);  // To setup its ID
            // AUTHZ CHECK
            // in order to succeed subject has to be in a role 
            // which allows creating of authz resources
            createAuthzPlatform(pValue.getName(), platform.getId(),
                                subject);

            // Send resource create event
            ResourceCreatedZevent zevent =
                new ResourceCreatedZevent(subject, platform.getEntityId());
            ZeventManager.getInstance().enqueueEventAfterCommit(zevent);

            return platform.getId();
        } catch (FinderException e) {
            throw new CreateException("Unable to find PlatformType: " +
                                      platformTypeId + " : " + e.getMessage());
        } catch (NamingException e) {
            throw new CreateException("Unable to get LocalHome " +
                                      e.getMessage());
        }
    }

    private void throwDupPlatform(Serializable id, String platName) {
        throw new NonUniqueObjectException(id, "Duplicate platform found " + 
                                           "with name: " + platName);
    }
                                  
    /**
     * Create a Platform from an AIPlatform
     * @param aipValue the AIPlatform to create as a regular appdef platform.
     * @return PlatformValue - the value object for the newly created platform.
     * @ejb:interface-method
     * @ejb:transaction type="RequiresNew"
     */
    public Integer createPlatform(AuthzSubjectValue subject,
                                  AIPlatformValue aipValue)
        throws ApplicationException, CreateException
    {
        this.counter.addCPUs(aipValue.getCpuCount().intValue());

        PlatformTypeDAO ptDAO = 
            DAOFactory.getDAOFactory().getPlatformTypeDAO();
        PlatformType platType = ptDAO.findByName(aipValue.getPlatformTypeName());
        PlatformDAO pDAO = DAOFactory.getDAOFactory().getPlatformDAO(); 
            
        if (platType == null) {
            throw new SystemException("Unable to find PlatformType [" + 
                                      aipValue.getName() + "]");
        }
        
        Platform checkP = pDAO.findByName(aipValue.getName());
        if (checkP != null) {
            throwDupPlatform(checkP.getId(), aipValue.getName());
        }

        Agent agent = getAgentDAO().findByAgentToken(aipValue.getAgentToken());
        
        if (agent == null) {
            throw new ObjectNotFoundException(aipValue.getId(),
                                              "Unable to find agent: " +
                                              aipValue.getAgentToken());
        }
        ConfigResponseDB config = getConfigResponseDAO().createPlatform();

        Platform platform = platType.create(aipValue, subject.getName(),
                                            config, agent);
        getPlatformDAO().save(platform);
        
        // AUTHZ CHECK
        try {
            createAuthzPlatform(aipValue.getName(), platform.getId(), 
                                subject);
        } catch(Exception e) {
            throw new SystemException(e);
        }

        // Send resource create event
        ResourceCreatedZevent zevent =
            new ResourceCreatedZevent(subject, platform.getEntityId());
        ZeventManager.getInstance().enqueueEventAfterCommit(zevent);
        
        return platform.getId();
    }

    /**
     * Get all platforms.
     * @ejb:interface-method
     * @param subject The subject trying to list platforms.
     * @param pc a PageControl object which determines the size of the page and
     * the sorting, if any.
     * @return A List of PlatformValue objects representing all of the
     * platforms that the given subject is allowed to view.
     */
    public PageList getAllPlatforms(AuthzSubjectValue subject,
                                    PageControl pc)
        throws FinderException, PermissionException {

        Collection ejbs;
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
     * @ejb:interface-method
     * @param subject The subject trying to list platforms.
     * @param range The range in milliseconds.
     * @param size The number of platforms to return.
     * @return A List of PlatformValue objects representing all of the
     * platforms that the given subject is allowed to view that were created
     * within the given range.
     */
    public PageList getRecentPlatforms(AuthzSubjectValue subject,
                                       long range, int size)
        throws FinderException, PermissionException {

        Collection platforms;
        PageControl pc = new PageControl(0, size);

        try {
            platforms =
                getPlatformDAO().findByCTime(System.currentTimeMillis() - range);

            // now get the list of PKs
            List viewable = getViewablePlatformPKs(subject);
            // and iterate over the list to remove any item not viewable
            for(Iterator i = platforms.iterator(); i.hasNext();) {
                Platform platform = (Platform)i.next();
                if(!viewable.contains(platform.getId())) {
                    // remove the item, user cant see it
                    i.remove();
                }
            }
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platforms, pc);
    }

    /**
     * Get platform light value by id.  Does not check permission.
     * @ejb:interface-method
     */
    public PlatformLightValue getPlatformLightValue(Integer id)
        throws PlatformNotFoundException {
        PlatformDAO platformLocalHome = getPlatformDAO();
        try {
            Platform platform = platformLocalHome.findById(id);
            return platform.getPlatformLightValue();
        } catch (ObjectNotFoundException e) {
            throw new PlatformNotFoundException(id, e);
        }
    }

    /**
     * Find a Platform by Id.
     * @param id The id to look up.
     * @return A Platform object representing this Platform.
     * @throws PlatformNotFoundException If the given Platform is not found.
     * @ejb:interface-method
     */
    public Platform findPlatformById(Integer id)
        throws PlatformNotFoundException
    {
        try {
            return getPlatformDAO().findById(id);
        } catch (ObjectNotFoundException e) {
            throw new PlatformNotFoundException(id, e);
        }
    }

    /**
     * Get a Platform by id.
     * @param id The id to look up.
     * @return The Platform object with the given id, or null if it does not
     * exist.
     * @ejb:interface-method 
     */
    public Platform getPlatformById(Integer id) {
        return getPlatformDAO().get(id);
    }

    /**
     * Get a PlatformValue object by id.
     * @deprecated use findPlatformById instead.
     * @ejb:interface-method
     */
    public PlatformValue getPlatformValueById(AuthzSubjectValue subject,
                                              Integer id)
        throws PlatformNotFoundException, PermissionException
    {
        return findPlatformById(id).getPlatformValue();
    }

    /**
     * Get the Platform object based on an AIPlatformValue.
     * @ejb:interface-method
     */
    public Platform getPlatformByAIPlatform(AuthzSubjectValue subject,
                                            AIPlatformValue aiPlatform)
        throws PermissionException {
        
        String certdn = aiPlatform.getCertdn();
        String fqdn = aiPlatform.getFqdn();

        try {
            try {
                // First try to find by FQDN
                return findPlatformByFqdn(subject, fqdn);
            } catch (PlatformNotFoundException e) {
                // Now try to find by certdn
                return getPlatformByCertDN(subject, certdn);
            } catch (Exception e) {
                _log.info("Error finding platform by certdn: " + certdn);
                throw new SystemException(e);
            }
        } catch (PlatformNotFoundException e) {
            _log.info("Error finding platform by fqdn: " + fqdn);
            return null;
        }
    }

    /**
     * Find a platform by name
     * @ejb:interface-method
     * @param subject - who is trying this
     * @param name - the name of the platform
     */
    public PlatformValue getPlatformByName(AuthzSubjectValue subject,
                                           String name)
        throws PlatformNotFoundException, PermissionException {
        Platform p = getPlatformDAO().findByName(name);
        if (p == null) {
            throw new PlatformNotFoundException("platform " + name +
                                                " not found");
        }
        PlatformValue platformValue = p.getPlatformValue();
        // now check if the user can see this at all
        checkViewPermission(subject, platformValue.getEntityId());
        return platformValue;
    }

    /**
     * Get the platform that has the specified CertDN
     */
    private Platform getPlatformByCertDN(AuthzSubjectValue subject,
                                         String certDN)
        throws PlatformNotFoundException, PermissionException {
        Platform p;
        try {
            p = getPlatformDAO().findByCertDN(certDN);
        } catch (NonUniqueResultException e) {
            p = null;
        }
        if (p == null) {
            throw new PlatformNotFoundException("Platform with certdn " +
                                                certDN + " not found");
        }
        
        checkViewPermission(subject, p.getEntityId());
        return p;
    }

    /**
     * Get the Platform that has the specified Fqdn
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Platform findPlatformByFqdn(AuthzSubjectValue subject,
                                       String fqdn)
        throws PlatformNotFoundException, PermissionException
    {
        Platform p;
        try {
            p = getPlatformDAO().findByFQDN(fqdn);
        } catch (NonUniqueResultException e) {
            p = null;
        }
        if (p == null) {
            throw new PlatformNotFoundException("Platform with fqdn "
                                                + fqdn + " not found");
        }
        // now check if the user can see this at all
        checkViewPermission(subject, p.getEntityId());
        return p;
    }

    /**
     * Get the Collection of platforms that have the specified Ip address
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Collection getPlatformByIpAddr(AuthzSubjectValue subject,
                                          String address)
        throws FinderException, PermissionException
    {
        return getPlatformDAO().findByIpAddr(address);
    }

    /**
     * Get the platform by agent token
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Collection getPlatformPksByAgentToken(AuthzSubjectValue subject,
                                                String agentToken)
        throws PlatformNotFoundException
    {
        Collection platforms = getPlatformDAO().findByAgentToken(agentToken);
        if (platforms == null || platforms.size() == 0) {
            throw new PlatformNotFoundException(
                "Platform with agent token " + agentToken + " not found");
        }
        
        List pks = new ArrayList();
        for (Iterator it = platforms.iterator(); it.hasNext(); ) {
            Platform plat = (Platform) it.next();
            pks.add(plat.getId());
        }
        return pks;
    }

    /**
     * Get the platform that hosts the server that provides the 
     * specified service.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @param subject The subject trying to list services.
     * @param serviceId service ID.
     * @return the Platform
     */
    public PlatformValue getPlatformByService ( AuthzSubjectValue subject,
                                                Integer serviceId ) 
        throws PlatformNotFoundException, PermissionException {
        Platform p = getPlatformDAO().findByServiceId(serviceId);
        if (p == null) {
            throw new PlatformNotFoundException(
                "platform for service " + serviceId + " not found");
        }
        PlatformValue platformValue = p.getPlatformValue();
        // now check if the user can see this at all
        checkViewPermission(subject, platformValue.getEntityId());
        return platformValue;
    }
    
    /**
     * Get the platform ID that hosts the server that provides the 
     * specified service.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @param serviceId service ID.
     * @return the Platform
     */
    public Integer getPlatformIdByService ( Integer serviceId ) 
        throws PlatformNotFoundException
    {
        Platform p = getPlatformDAO().findByServiceId(serviceId);
        if (p == null) {
            throw new PlatformNotFoundException("platform for service " +
                                                serviceId + " not found");
        }
        return p.getId();
    }
    
    /**
     * Get the platform for a server.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @param subject The subject trying to list services.
     * @param serverId Server ID.
     */
    public PlatformValue getPlatformByServer ( AuthzSubjectValue subject,
                                               Integer serverId ) 
        throws PlatformNotFoundException, PermissionException {
        try {
            Server serverLocal = getServerDAO().findById(serverId);
            Platform p = serverLocal.getPlatform();
            PlatformValue platformValue = p.getPlatformValue();
            checkViewPermission(subject, platformValue.getEntityId());
            return platformValue;
        } catch (ObjectNotFoundException e) {
            throw new PlatformNotFoundException("platform for server " +
                                                serverId + " not found", e);
        }
    }

    /**
     * Get the platform ID for a server.
     * @ejb:interface-method
     * @param serverId Server ID.
     */
    public Integer getPlatformIdByServer ( Integer serverId ) 
        throws PlatformNotFoundException {
        try {
            Server serverLocal = getServerDAO().findById(serverId);
            return serverLocal.getPlatform().getId();
        } catch (ObjectNotFoundException e) {
            throw new PlatformNotFoundException("platform for server " +
                                                serverId + " not found", e);
        }
    }

    /**
     * Get the platforms for a list of servers.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @param subject The subject trying to list services.
     */
    public PageList getPlatformsByServers(AuthzSubjectValue subject,
                                          List sIDs) 
        throws PlatformNotFoundException, PermissionException {
        List authzPks;
        try {
            authzPks = getViewablePlatformPKs(subject);
        } catch(FinderException exc){
            return new PageList();
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        
        Integer[] ids = new Integer[sIDs.size()];
        int i = 0;
        for (Iterator it = sIDs.iterator(); it.hasNext(); i++) {
            AppdefEntityID svrId = (AppdefEntityID) it.next();
            ids[i] = svrId.getId();
        }

        List foundPlats = getPlatformDAO().findByServers(ids);

        ArrayList platforms = new ArrayList();
        for (Iterator it = foundPlats.iterator(); it.hasNext();) {
            Platform platform = (Platform) it.next();
            if(authzPks.contains(platform.getId())) {
                platforms.add(platform);
            }
        }
        
        return valuePager.seek(platforms, null);
    }


    /**
     * Get all platforms by application.
     * @ejb:interface-method
     *
     * @param subject The subject trying to list services.
     * @param appId Application ID.
     * but when they are, they should live somewhere in appdef/shared so that clients
     * can use them too.
     * @return A List of ApplicationValue objects representing all of the
     * services that the given subject is allowed to view.
     */
    public PageList getPlatformsByApplication ( AuthzSubjectValue subject,
                                                Integer appId,
                                                PageControl pc ) 
        throws ApplicationNotFoundException, PlatformNotFoundException, 
               PermissionException {
        ApplicationDAO appLocalHome = getApplicationDAO();

        Application appLocal;
        Collection serviceCollection;
        Iterator it;
        Collection platCollection;

        try {
            appLocal = appLocalHome.findById(appId);
        } catch(ObjectNotFoundException e){
            throw new ApplicationNotFoundException(appId, e);
        }

        platCollection = new ArrayList();
        // XXX Call to authz, get the collection of all services
        // that we are allowed to see.
        // OR, alternatively, find everything, and then call out
        // to authz in batches to find out which ones we are 
        // allowed to return.
        
        serviceCollection = appLocal.getAppServices();
        it = serviceCollection.iterator();
        while (it != null && it.hasNext()) {
            AppService appService = (AppService) it.next();

            if (appService.getIsCluster()) {
                Collection services =
                    appService.getServiceCluster().getServices();

                for (Iterator i = services.iterator(); i.hasNext();) {
                    Service service = (Service)i.next();
                    PlatformValue pValue = 
                        getPlatformByService(subject, service.getId());
                    if (!platCollection.contains(pValue))
                        platCollection.add(pValue);
                }
            } else {
                Integer serviceId = appService.getService().getId();
                PlatformValue pValue = getPlatformByService(subject,
                                                            serviceId);
                // Fold duplicate platforms
                if (!platCollection.contains(pValue))
                    platCollection.add(pValue);
            } 
        }
            
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(platCollection, pc);
    }

    /**
     * Get server IDs by server type and platform.
     * @ejb:interface-method
     *
     * @param subject The subject trying to list servers.
     * @return A PageList of ServerValue objects representing servers on the
     * specified platform that the subject is allowed to view.
     */
    public Integer[] getPlatformIds(AuthzSubjectValue subject,
                                    Integer platTypeId)
        throws PermissionException {
        PlatformDAO pLHome;
        try {
            pLHome = getPlatformDAO();
            Collection platforms = pLHome.findByType(platTypeId);
            Collection platIds = new ArrayList();
         
            // now get the list of PKs
            Collection viewable = super.getViewablePlatformPKs(subject);
            // and iterate over the ejbList to remove any item not in the
            // viewable list
            for(Iterator i = platforms.iterator(); i.hasNext();) {
                Platform aEJB = (Platform)i.next();
                if(viewable.contains(aEJB.getId())) {
                    // remove the item, user cant see it
                    platIds.add(aEJB.getId());
                }
            }
        
            return (Integer[]) platIds.toArray(new Integer[0]);
        } catch (NamingException e) {
            throw new SystemException(e);
        } catch (FinderException e) {
            // There are no viewable platforms
            return new Integer[0];
        }
    }

    /**
     * Get server IDs by server type and platform.
     * @ejb:interface-method
     *
     * @param subject The subject trying to list servers.
     * @param pc The page control.
     * @return A PageList of ServerValue objects representing servers on the
     * specified platform that the subject is allowed to view.
     */
    public PageList findPlatformsByType(AuthzSubjectValue subject,
                                        Integer platTypeId,
                                        PageControl pc)
        throws PermissionException {
        try {
            Collection platforms = getPlatformDAO().findByType(platTypeId);
            if (platforms.size() == 0) {
                // There are no viewable platforms
                return new PageList();
            }
            // now get the list of PKs
            Collection viewable = super.getViewablePlatformPKs(subject);
            // and iterate over the ejbList to remove any item not in the
            // viewable list
            for(Iterator i = platforms.iterator(); i.hasNext();) {
                Platform aEJB = (Platform)i.next();
                if(!viewable.contains(aEJB.getId())) {
                    // remove the item, user cant see it
                    i.remove();
                }
            }
        
            return valuePager.seek(platforms, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        } catch (FinderException e) {
            // There are no viewable platforms
            return new PageList();
        }
    }

    /**
     * Get the platforms that have an IP with the specified address.
     * If no matches are found, this method DOES NOT throw a
     * PlatformNotFoundException, rather it returns an empty PageList.
     * @ejb:interface-method
     */
    public PageList findPlatformsByIpAddr ( AuthzSubjectValue subject,
                                            String addr,
                                            PageControl pc ) 
        throws PermissionException
    {
        Collection platforms = getPlatformDAO().findByIpAddr(addr);
        if (platforms.size() == 0) {
            return new PageList();
        }
        return valuePager.seek(platforms, pc);
    }

    /**
     * Update an existing Platform. Requires all Ip's to have been 
     * re-added via the platformValue.addIpValue(IpValue) method due
     * to bug 4924
     * @param existing - the value object for the platform you want to
     * save
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public boolean updatePlatformImpl(AuthzSubjectValue subject,
                                      PlatformValue existing)
        throws UpdateException, PermissionException,
               AppdefDuplicateNameException, PlatformNotFoundException, 
               AppdefDuplicateFQDNException, ApplicationException {
        try {
            checkPermission(subject, getPlatformResourceType(),
                    existing.getId(), AuthzConstants.platformOpModifyPlatform);
            existing.setModifiedBy(subject.getName());
            existing.setMTime(new Long(System.currentTimeMillis()));
            this.trimStrings(existing);

            Platform plat = getPlatformDAO().findById(existing.getId());

            if (existing.getCpuCount() == null) {
                //cpu count is no longer an option in the UI
                existing.setCpuCount(plat.getCpuCount());
            }

            if(plat.matchesValueObject(existing)) {
                _log.debug("No changes found between value object and entity");
                return false;
            } else {
                int newCount = existing.getCpuCount().intValue();
                int prevCpuCount = plat.getCpuCount().intValue();
                if (newCount > prevCpuCount) {
                    this.counter.addCPUs(newCount - prevCpuCount);
                }
                
                if( !(existing.getName().equals(plat.getName()))) { 
                    // name has changed. check for duplicate and update 
                    // authz resource table
                    try {
                        getPlatformByName(subject, existing.getName());
                        // duplicate found, throw a duplicate object exception
                        throw new AppdefDuplicateNameException();
                    } catch (PlatformNotFoundException e) {
                        // ok
                    } catch (PermissionException e) {
                        // fall through, will validate later
                    }

                    // name has changed. Update authz resource table
                    ResourceValue rv =
                        getAuthzResource(getPlatformResourceType(), existing.getId());
                    rv.setName(existing.getName());
                    updateAuthzResource(rv);
                }

                if(! (existing.getFqdn().equals(plat.getFqdn())))  {
                    // fqdn has changed. check for duplicate and throw
                    // duplicateFQDNException
                    try {
                        findPlatformByFqdn(subject, existing.getFqdn());
                        // duplicate found, throw a duplicate object exception
                        throw new AppdefDuplicateFQDNException();
                    } catch (PlatformNotFoundException e) {
                        // ok
                    } catch (PermissionException e) {
                        // fall through, will validate later
                    }
                }

                // See if we need to create an AIPlatform
                if (plat.getAgent() == null && existing.getAgent() != null) {
                    // Create AIPlatform for manually created platform
                    AIQueueManagerLocal aiqManagerLocal;
                    try {
                        aiqManagerLocal =
                            AIQueueManagerUtil.getLocalHome().create();
                    } catch (CreateException e) {
                        throw new SystemException(e);
                    }
                    AIPlatformValue aiPlatform = new AIPlatformValue();
                    aiPlatform.setFqdn(existing.getFqdn());
                    aiPlatform.setName(existing.getName());
                    aiPlatform.setDescription(existing.getDescription());
                    aiPlatform.setPlatformTypeName(existing.getPlatformType().getName());
                    aiPlatform.setCertdn(existing.getCertdn());
                    aiPlatform.setAgentToken(
                            existing.getAgent().getAgentToken());
                    
                    IpValue[] ipVals = existing.getIpValues();
                    for (int i = 0; i < ipVals.length; i++) {
                        AIIpValue aiIpVal = new AIIpValue();
                        aiIpVal.setAddress(ipVals[i].getAddress());
                        aiIpVal.setNetmask(ipVals[i].getNetmask());
                        aiIpVal.setMACAddress(ipVals[i].getMACAddress());
                        aiPlatform.addAIIpValue(aiIpVal);
                    }

                    try {
                        aiqManagerLocal.queue(subject, aiPlatform,
                                              false, false, true);
                    } catch (CreateException e) {
                        _log.error("Cannot create AIPlatform for " +
                                  existing.getName(), e);
                    } catch (RemoveException e) {
                        _log.error("Cannot remove from AIQueue", e);
                    }
                }
                getPlatformDAO().updatePlatform(existing);
                return true;
            }
        } catch (FinderException e) {
            throw new SystemException(e);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Update an existing Platform. Requires all Ip's to have been 
     * re-added via the platformValue.addIpValue(IpValue) method due
     * to bug 4924
     * @param existing - the value object for the platform you want to
     * save
     * @ejb:interface-method
     * @ejb:transaction type="RequiresNew"
     */
    public PlatformValue updatePlatform(AuthzSubjectValue subject,
                                        PlatformValue existing)
        throws UpdateException, PermissionException,
               AppdefDuplicateNameException, PlatformNotFoundException, 
               AppdefDuplicateFQDNException, ApplicationException
    {
        boolean updated = updatePlatformImpl(subject, existing);
        if (updated)
            return getPlatformValueById(subject, existing.getId());
        return existing;
    }
    
    /**
     * Change platform owner
     * @param who
     * @param newOwner
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRESNEW"
     */
    public void changePlatformOwner(AuthzSubjectValue who,
                                    Integer platformId,
                                    AuthzSubjectValue newOwner)
        throws FinderException, PermissionException {
        try {
            // first lookup the platform
            Platform platEJB = getPlatformDAO().findById(platformId);
            // check if the caller can modify this platform
            checkModifyPermission(who, platEJB.getEntityId());
            // now get its authz resource
            ResourceValue authzRes = getPlatformResourceValue(platformId);
            // change the authz owner
            getResourceManager().setResourceOwner(who, authzRes, newOwner);
            // update the owner field in the appdef table -- YUCK
            platEJB.setOwner(newOwner.getName());
            platEJB.setModifiedBy(who.getName());
        } catch (NamingException e) {
            throw new SystemException(e);
        } catch (CreateException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Private method to validate a new PlatformValue object
     * @throws ValidationException
     */
    private void validateNewPlatform(PlatformValue pv)
        throws ValidationException {
        String msg = null;
        // first check if its new 
        if(pv.idHasBeenSet()) {
            msg = "This platform is not new. It has id: " + pv.getId();
        }
        // else if(someotherthing)  ...

        // Now check if there's a msg set and throw accordingly
        if(msg != null) {
            throw new ValidationException(msg);
        }
    }     

    /**
     * Create the Authz resource and verify that the subject
     * has the createPlatform permission.
     * @param subject - the user creating
     */
    private void createAuthzPlatform(String platName,
                                     Integer platId,
                                     AuthzSubjectValue subject)
        throws CreateException, NamingException, FinderException,
               PermissionException {
        _log.debug("Begin Authz CreatePlatform");
        // check to make sure the user has createPlatform permission
        // on the root resource type
        this.checkCreatePlatformPermission(subject);
        _log.debug("User has permission to create platform. Adding AuthzResource");
        createAuthzResource(subject, getPlatformResourceType(),platId,
                            platName);
    }

    /**
     * Update platform types
     * @ejb:interface-method
     * @ejb:transaction type="RequiresNew"
     */
    public void updatePlatformTypes(String plugin, PlatformTypeInfo[] infos)
        throws CreateException, FinderException, RemoveException {
        // First, put all of the infos into a Hash
        HashMap infoMap = new HashMap();
        for (int i = 0; i < infos.length; i++) {
            infoMap.put(infos[i].getName(), infos[i]);
        }
        PlatformTypeDAO ptLHome = getPlatformTypeDAO();
        Collection curPlatforms = ptLHome.findByPlugin(plugin);
            
        for (Iterator i = curPlatforms.iterator(); i.hasNext();) {
            PlatformType ptlocal = (PlatformType) i.next();
            String localName = ptlocal.getName();
            PlatformTypeInfo pinfo =
                (PlatformTypeInfo) infoMap.remove(localName);

            // See if this exists
            if (pinfo == null) {
                // Remove platforms which are not supposed to exist
                _log.debug("Removing PlatformType: " + localName);
                ptLHome.remove(ptlocal);
            } else {
                String curName = ptlocal.getName();
                String newName = pinfo.getName();

                // Just update it
                _log.debug("Updating PlatformType: " + localName);

                if (!newName.equals(curName))
                    ptlocal.setName(newName);
            }
        }
            
        // Now create the left-overs
        for (Iterator i = infoMap.values().iterator(); i.hasNext(); ) {
            PlatformTypeInfo pinfo = (PlatformTypeInfo) i.next();
            PlatformTypeValue ptype = new PlatformTypeValue();

            _log.debug("Creating new PlatformType: " + pinfo.getName());
            ptype.setPlugin(plugin);
            ptype.setName(pinfo.getName());

            // Now create the platform
            ptLHome.create(ptype);
        }
    }

    /**
     * Update an existing appdef platform with data from an AI platform.
     * @param aiplatform the AI platform object to use for data
     * @ejb:transaction type="REQUIRESNEW"
     * @ejb:interface-method
     */
    public void updateWithAI(AIPlatformValue aiplatform, String owner)
        throws PlatformNotFoundException, ApplicationException {
        
        String certdn = aiplatform.getCertdn();
        String fqdn = aiplatform.getFqdn();
        PlatformDAO plh = getPlatformDAO();
        // First try to find by fqdn

        Platform pLocal;
        try {
            pLocal = plh.findByFQDN(fqdn);
        } catch (NonUniqueResultException e) {
            pLocal = null;
        }
        if(pLocal == null) {
            // Now try to find by certdn
            try {
                pLocal = plh.findByCertDN(certdn);
            } catch (NonUniqueResultException e) {
                pLocal = null;
            }
            if(pLocal ==  null) {
                throw new PlatformNotFoundException(
                    "Platform not found with either FQDN: " + fqdn +
                    " nor CertDN: " + certdn);
            }
        }
        int prevCpuCount = pLocal.getCpuCount().intValue();
        Integer count = aiplatform.getCpuCount();
        if ((count != null) && (count.intValue() > prevCpuCount)) {
            this.counter.addCPUs(
                aiplatform.getCpuCount().intValue() - prevCpuCount);
        }
        pLocal.updateWithAI(aiplatform, owner);
    }

    /**
     * Used to trim all string based attributes present in a platform value
     * object
     */
    private void trimStrings(PlatformValue plat) {
        if (plat.getName() != null)
            plat.setName(plat.getName().trim());
        if (plat.getCertdn() != null)    
            plat.setCertdn(plat.getCertdn().trim());
        if (plat.getCommentText() != null) 
            plat.setCommentText(plat.getCommentText().trim());
        if (plat.getDescription() != null)
            plat.setDescription(plat.getDescription().trim());
        if (plat.getFqdn() != null)
            plat.setFqdn(plat.getFqdn().trim());
        // now the Ips
        for(Iterator i = plat.getAddedIpValues().iterator(); i.hasNext();) {
            IpValue ip = (IpValue)i.next();
            if (ip.getAddress() != null) 
                ip.setAddress(ip.getAddress().trim());
            if (ip.getMACAddress() != null)
                ip.setMACAddress(ip.getMACAddress().trim());
            if (ip.getNetmask() != null) 
                ip.setNetmask(ip.getNetmask().trim());
        }
        // and the saved ones in case this is an update
        for(int i = 0; i < plat.getIpValues().length; i++) {
            IpValue ip = plat.getIpValues()[i];
            if (ip.getAddress() != null) 
                ip.setAddress(ip.getAddress().trim());
            if (ip.getMACAddress() != null)
                ip.setMACAddress(ip.getMACAddress().trim());
            if (ip.getNetmask() != null) 
                ip.setNetmask(ip.getNetmask().trim());
        }
    }

    /**
     * Add an IP to a platform
     * @ejb:interface-method
     */
    public Ip addIp(Platform platform, String address, String netmask,
                    String macAddress) {
        return platform.addIp(address, netmask, macAddress);
    }

    /**
     * Remove an IP on a platform
     * @ejb:interface-method
     */
    public void removeIp(Platform platform, String address, String netmask,
                         String macAddress) {
        platform.removeIp(address, netmask, macAddress);
    }

    public static PlatformManagerLocal getOne() {
        try {
            return PlatformManagerUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    /**
     * Create a platform manager session bean.
     * @exception CreateException If an error occurs creating the pager
     * for the bean.
     */
    public void ejbCreate() throws CreateException {
        counter = (PlatformCounter) ProductProperties
                .getPropertyInstance("hyperic.hq.platform.counter");

        if (counter == null) {
            counter = new DefaultPlatformCounter();
        }

        try {
            valuePager = Pager.getPager(VALUE_PROCESSOR);
        } catch ( Exception e ) {
            throw new CreateException("Could not create value pager:" + e);
        }
    }

    public void ejbRemove() { }
    public void ejbActivate() { }
    public void ejbPassivate() { }
}
