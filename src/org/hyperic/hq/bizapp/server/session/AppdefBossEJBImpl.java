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

package org.hyperic.hq.bizapp.server.session;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.agent.FileDataResult;
import org.hyperic.hq.appdef.server.session.Platform;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.appdef.shared.AIConversionUtil;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQueueConstants;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AgentValue;
import org.hyperic.hq.appdef.shared.AppSvcClustDuplicateAssignException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateFQDNException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateNameException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityTypeID;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.AppdefGroupManagerLocal;
import org.hyperic.hq.appdef.shared.AppdefGroupNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.AppdefInventorySummary;
import org.hyperic.hq.appdef.shared.AppdefResourcePermissions;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.appdef.shared.AppdefStatManagerLocal;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.ApplicationManagerLocal;
import org.hyperic.hq.appdef.shared.ApplicationNotFoundException;
import org.hyperic.hq.appdef.shared.ApplicationTypeValue;
import org.hyperic.hq.appdef.shared.ApplicationValue;
import org.hyperic.hq.appdef.shared.CPropKeyExistsException;
import org.hyperic.hq.appdef.shared.CPropKeyNotFoundException;
import org.hyperic.hq.appdef.shared.CPropKeyValue;
import org.hyperic.hq.appdef.shared.CPropManagerLocal;
import org.hyperic.hq.appdef.shared.ConfigFetchException;
import org.hyperic.hq.appdef.shared.DependencyTree;
import org.hyperic.hq.appdef.shared.EntityPropertyFetcher;
import org.hyperic.hq.appdef.shared.GroupTypeValue;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.InvalidConfigException;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformTypeValue;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.appdef.shared.ServerTypeValue;
import org.hyperic.hq.appdef.shared.ServerValue;
import org.hyperic.hq.appdef.shared.ServiceClusterValue;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceNotFoundException;
import org.hyperic.hq.appdef.shared.ServiceTypeValue;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.appdef.shared.pager.AppdefGroupPagerFilterExclude;
import org.hyperic.hq.appdef.shared.pager.AppdefGroupPagerFilterGrpEntRes;
import org.hyperic.hq.appdef.shared.pager.AppdefGroupPagerFilterMemExclude;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilter;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterAssignSvc;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterExclude;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterGroupEntityResource;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterGroupMemExclude;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterInternalService;
import org.hyperic.hq.appdef.shared.resourceTree.ResourceTree;
import org.hyperic.hq.auth.shared.SessionException;
import org.hyperic.hq.auth.shared.SessionManager;
import org.hyperic.hq.auth.shared.SessionNotFoundException;
import org.hyperic.hq.auth.shared.SessionTimeoutException;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.authz.server.session.ResourceGroupManagerEJBImpl;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceGroupManagerLocal;
import org.hyperic.hq.authz.shared.ResourceManagerLocal;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.autoinventory.AutoinventoryException;
import org.hyperic.hq.autoinventory.ScanConfigurationCore;
import org.hyperic.hq.autoinventory.shared.AutoinventoryManagerLocal;
import org.hyperic.hq.bizapp.shared.AIBossLocal;
import org.hyperic.hq.bizapp.shared.AllConfigResponses;
import org.hyperic.hq.bizapp.shared.MeasurementBossLocal;
import org.hyperic.hq.bizapp.shared.ProductBossLocal;
import org.hyperic.hq.bizapp.shared.resourceImport.BatchImportData;
import org.hyperic.hq.bizapp.shared.resourceImport.BatchImportException;
import org.hyperic.hq.bizapp.shared.resourceImport.Validator;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceTreeNode;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.events.server.session.AlertDefinitionManagerEJBImpl;
import org.hyperic.hq.grouping.shared.GroupCreationException;
import org.hyperic.hq.grouping.shared.GroupDuplicateNameException;
import org.hyperic.hq.grouping.shared.GroupModificationException;
import org.hyperic.hq.grouping.shared.GroupNotCompatibleException;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.scheduler.ScheduleWillNeverFireException;
import org.hyperic.hq.ui.util.BizappUtils;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.EncodingException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;
import org.hyperic.util.timer.StopWatch;

/**
 * @ejb:bean name="AppdefBoss"
 *      jndi-name="ejb/bizapp/AppdefBoss"
 *      local-jndi-name="LocalAppdefBoss"
 *      view-type="both"
 *      type="Stateless"
 * @ejb:transaction type="Required"
 */
public class AppdefBossEJBImpl
    extends BizappSessionEJB
    implements SessionBean
{
    private final static String APPDEF_PAGER_PROCESSOR =
        "org.hyperic.hq.appdef.shared.pager.AppdefPagerProc";

    private SessionContext sessionCtx = null;

    private SessionManager manager = SessionManager.getInstance();

    protected Log log = LogFactory.getLog(AppdefBossEJBImpl.class.getName());
    protected boolean debug = log.isDebugEnabled();
    protected static final int APPDEF_TYPE_UNDEFINED     = -1;
    protected static final int APPDEF_RES_TYPE_UNDEFINED = -1;
    protected static final int APPDEF_GROUP_TYPE_UNDEFINED = -1;

    public AppdefBossEJBImpl() {}

    /**
     * Find a common appdef resource type among the appdef entities
     * @param sessionID
     * @param aeids the array of appdef entity IDs
     * @return AppdefResourceTypeValue if they are of same type, null otherwise
     * @throws AppdefEntityNotFoundException
     * @throws PermissionException
     * @throws SessionNotFoundException
     * @throws SessionTimeoutException
     * @ejb:interface-method
     */
    public AppdefResourceTypeValue findCommonResourceType(int sessionID,
                                                          String[] aeids)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionNotFoundException, SessionTimeoutException {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        if (aeids == null || aeids.length == 0)
            return null;
        
        // Take the resource type of the first entity
        AppdefEntityID aeid = new AppdefEntityID(aeids[0]);
        int resType = aeid.getType();
        
        AppdefResourceTypeValue retArt = null;
        // Now let's go through and make sure they're of the same type
        for (int i = 0; i < aeids.length; i++) {
            aeid = new AppdefEntityID(aeids[i]);
            // First check to make sure they are same resource type
            if (aeid.getType() != resType)
                return null;
            
            // Now get the appdef resource type value
            AppdefEntityValue arv = new AppdefEntityValue(aeid, subject);
            AppdefResourceTypeValue art = arv.getResourceTypeValue();
            
            if (retArt == null) {
                retArt = art;
            }
            else if (art.getId().intValue() != retArt.getId().intValue()) {
                return null;
            }
        }
     
        return retArt;
    }
    
    /**
     * Find all the platform types defined in the system.
     *
     * @return A list of PlatformTypeValue objects. 
     * @ejb:interface-method
     */
    public PageList findAllPlatformTypes(int sessionID, PageControl pc)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {

        AuthzSubjectValue subject = manager.getSubject(sessionID);
        PageList platTypeList = null ;

        platTypeList =
            getPlatformManager().getAllPlatformTypes(subject, pc);

        return platTypeList;
    }

    /**
     * Find all the viewable platform types defined in the system.
     *
     * @return A list of PlatformTypeValue objects.
     * @ejb:interface-method
     */
    public PageList findViewablePlatformTypes(int sessionID, PageControl pc)
        throws FinderException,
        SessionTimeoutException, SessionNotFoundException,
        PermissionException {

        AuthzSubjectValue subject = manager.getSubject(sessionID);
        PageList platTypeList = null ;

        platTypeList =
            getPlatformManager().getViewablePlatformTypes(subject, pc);

        return platTypeList;
    }

    /**
     * Find all the server types defined in the system.
     *
     * @return A list of ServerTypeValue objects.
     * @ejb:interface-method
     */
    public PageList findAllServerTypes(int sessionID, PageControl pc)
        throws FinderException, SessionNotFoundException, 
               SessionTimeoutException, PermissionException {

        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().getAllServerTypes(subject, pc);
    }

    /**
     * Find all viewable server types defined in the system.
     *
     * @return A list of ServerTypeValue objects.
     * @ejb:interface-method
     */
    public PageList findViewableServerTypes(int sessionID, PageControl pc)
        throws FinderException, SessionNotFoundException, 
               SessionTimeoutException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().getViewableServerTypes(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public List findAllApplicationTypes(int sessionID)
        throws ApplicationException {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        try {
            return getApplicationManager().getAllApplicationTypes(subject);
        } catch (FinderException e) {
            throw new SystemException(e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public ApplicationTypeValue findApplicationTypeById(int sessionId,
                                                        Integer id)
        throws ApplicationException {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);
            return getApplicationManager().findApplicationTypeById(id);
        } catch (FinderException e) {
            throw new ApplicationException(e);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public PageList findAllServiceTypes(int sessionID,
                                        PageControl pc)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {

        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getAllServiceTypes(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findViewableServiceTypes(int sessionID,
                                    PageControl pc)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {

        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getViewableServiceTypes(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findViewablePlatformServiceTypes(int sessionID,
                                                     Integer platId)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {
    
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager()
            .findVirtualServiceTypesByPlatform(subject, platId);
    }

    /**
     * Generate a resource tree based on the root resources and
     * the traversal (one of ResourceTreeGenerator.TRAVERSE_*)
     *
     * @ejb:interface-method
     */
    public ResourceTree getResourceTree(int sessionID,
                                        AppdefEntityID[] ids, int traversal)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException
    {
        AuthzSubjectValue subject = this.manager.getSubject(sessionID);
        return getApplicationManager().getResourceTree(subject, ids, traversal);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findAllApps(int sessionID, PageControl pc)
        throws FinderException, PermissionException,
               SessionTimeoutException, SessionNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getApplicationManager().getAllApplications ( subject, pc );
    }

    /**
     * @ejb:interface-method
     */
    public ApplicationValue findApplicationById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {

        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getApplicationManager().getApplicationById(subject, id);
    }

    /**
     * @ejb:interface-method
     */
    public ApplicationValue findApplicationByName(int sessionID, String name)
        throws PermissionException, AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getApplicationManager().findApplicationByName(subject, name);
    }

    /**
     * <p>Get first-level child resources of a given resource based on
     * the child resource type.</p>
     *
     * <p>For example:
     * <ul>
     * <li><b>platform -</b> list of servers</li>
     * <li><b>server -</b> list of services</li>
     * <li><b>service -</b> <i>not supported</i></li>
     * <li><b>application -</b> list of services</li>
     * <li><b>group -</b> <i>list of members if the group is compatible</i></li>
     * </ul></p>
     *
     * @param parent the resource whose children we want
     * @param childResourceType the type of child resource
     *
     * @return list of <code>{@link
     * org.hyperic.hq.appdef.shared.AppdefResourceValue}</code>
     * objects
     *
     * @ejb:interface-method
     */
    public PageList findChildResources(int sessionID, AppdefEntityID parent,
                                       AppdefEntityTypeID childResourceType,
                                       PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefEntityNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        AppdefEntityValue adev = new AppdefEntityValue(parent, subject);

        switch (childResourceType.getType()) {
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            return adev.getAssociatedServers(childResourceType.getId(), pc);
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            return adev.getAssociatedServices(childResourceType.getId(), pc);
        case AppdefEntityConstants.APPDEF_TYPE_GROUP:
            AppdefGroupValue grp = findGroup(sessionID, parent.getId());
            if (grp.getGroupEntResType() !=
                childResourceType.getId().intValue())
            {
                throw new IllegalArgumentException("childResourceType " +
                    childResourceType + " does not match group resource type" +
                    grp.getGroupEntResType());
            }
            AppdefEntityID[] ids = new AppdefEntityID[grp.getSize()];
            int idx = 0;
            for (Iterator i=grp.getAppdefGroupEntries().iterator(); 
                 i.hasNext();) 
            {
                ids[idx++] = (AppdefEntityID)i.next();
            }
            return findByIds(sessionID, ids, pc);
        default:
            throw new IllegalArgumentException("Unsupported appdef type " + 
                                               parent.getType() );
        }
    }

    /**
     * @ejb:interface-method
     */
    public PageList findApplications(int sessionID, AppdefEntityID id,
                                     PageControl pc )
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getApplicationManager().getApplicationsByResource(subject, id,
                                                                 pc);
    }

    /**
     * @ejb:interface-method
     */
    public Map findMappedPlatformServices(int sessionID, Integer platformId,
                                          PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        // Get the AuthzSubject for the user's session
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getMappedPlatformServices(subject,
                                                             platformId, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findPlatformServices(int sessionID, Integer platformId,
                                         PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        // Get the AuthzSubject for the user's session
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getPlatformServices(subject, platformId, 
                                                       pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findPlatformServices(int sessionID, Integer platformId,
                                         Integer typeId, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        // Get the AuthzSubject for the user's session
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getPlatformServices(subject, platformId,
                                                       typeId, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServicesByApplication(int sessionID, Integer appId, 
                                              PageControl pc)
        throws AppdefEntityNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException 
    {
        AppdefEntityID aeid = AppdefEntityID.newAppID(appId.intValue());
                               
        return findServices(sessionID, aeid, false, pc);
    }

    /**
     * Find service inventory by application - including services and clusters
     * @ejb:interface-method
     */
    public PageList findServiceInventoryByApplication(int sessionID,
                                                      Integer appId,
                                                      PageControl pc)
        throws AppdefEntityNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException 
    {
        AppdefEntityID aeid = AppdefEntityID.newAppID(appId.intValue());
                               
        return findServices(sessionID, aeid, true, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServicesById(int sessionID, Integer[] serviceIds,
                                     PageControl pc)
        throws ApplicationException 
    {
        AuthzSubjectValue caller = manager.getSubject(sessionID);
        return getServiceManager().findServicesById(caller, serviceIds, pc);
    }

    /**
     * Find all services on a server
     *
     * @return A list of ServiceValue objects.
     * @ejb:interface-method
     */
    public PageList findServicesByServer(int sessionID, Integer serverId,
                                         PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AppdefEntityID aeid = AppdefEntityID.newServerID(serverId.intValue());
                               
        return findServices(sessionID, aeid, false, pc);
    }

    private PageList findServices(int sessionID, AppdefEntityID aeid,
                                  boolean allServiceInventory,
                                  PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = null;
        PageList res = null;

        if (pc == null) pc = PageControl.PAGE_ALL;

        // Get the AuthzSubject for the user's session
        subject = manager.getSubject(sessionID);

        switch  (aeid.getType()) {
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
            res = getServiceManager().getPlatformServices(subject,
                                                          aeid.getId(), pc);
            break;
        case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
            // fetch all service inventory including clusters.
            if (allServiceInventory) {
                res = getServiceManager()
                    .getServiceInventoryByApplication(subject,
                                                      aeid.getId(), pc);
                // app services will include service clusters which need
                // to be converted to their service group counterpart.
                for (int i=0;i<res.size();i++) {
                    Object o=res.get(i);
                    if (o instanceof ServiceClusterValue) {
                        res.set(i,findGroup(sessionID,
                                            ((ServiceClusterValue)o).getGroupId()));
                    }
                }
            } else {
                res = getServiceManager()
                    .getServicesByApplication(subject,aeid.getId(),pc);
            }
            break;
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            res = getServiceManager().getServicesByServer(subject,
                                                          aeid.getId(), pc);
            break;
        default:
            log.error("Invalid type given to find services.");
        }
        return res;
    }

    /**
     * @ejb:interface-method
     */
    public PageList findPlatformsByApplication(int sessionID,
                                               Integer appId,
                                               PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getPlatformManager().getPlatformsByApplication(subject, appId, 
                                                              pc);
    }

    /**
     * Find the platform by service.
     * @ejb:interface-method
     */
    public PlatformValue findPlatformByDependentID(int sessionID,
                                                   AppdefEntityID entityId)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        Integer id;
        id = entityId.getId();
        switch(entityId.getType()){
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
            return getPlatformManager().getPlatformValueById(subject, id);
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            return getPlatformManager().getPlatformByServer(subject, id);
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            return getPlatformManager().getPlatformByService(subject, id);
        default:
            throw new IllegalArgumentException("Invalid entity type: " +
                                               entityId.getType());
        }
    }

    /**
     * @ejb:interface-method
     */
    public ServerValue findServerByService(int sessionID, Integer serviceID)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException
    {
        return (ServerValue) 
            findServers(sessionID, AppdefEntityConstants.APPDEF_TYPE_SERVICE,
                        serviceID, null).get(0);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServersByTypeAndPlatform(int sessionId,
                                                 Integer platformId,
                                                 int adResTypeId,
                                                 PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        return findServers(sessionId,
                           AppdefEntityConstants.APPDEF_TYPE_PLATFORM,
                           platformId, adResTypeId, pc);
    }

    /**
     * Get the virtual servers for a given platform
     * @ejb:interface-method
     */
    public PageList findVirtualServersByPlatform(int sessionID,
                                                 Integer platformId,
                                                 PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        List allSvrs =
            getServerManager().getServersByPlatform(subject, platformId, 
                                                    false, pc);
        PageList virtualS = new PageList();
        for(int i = 0; i < allSvrs.size(); i++) {
            ServerValue sv = (ServerValue)allSvrs.get(i);
            if(sv.getServerType().getVirtual()) {
                virtualS.add(sv);
            }
        }
        return virtualS;
    }
    
    /**
     * Get the virtual server for a given platform and service type
     * @ejb:interface-method
     */
    public ServerValue findVirtualServerByPlatformServiceType(int sessionID,
                                                              Integer platId,
                                                              Integer svcTypeId)
        throws ServerNotFoundException, PlatformNotFoundException,
               PermissionException, SessionNotFoundException,
               SessionTimeoutException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        List servers = getServerManager()
            .getServersByPlatformServiceType(subject, platId, svcTypeId);

        // There should only be one
        return (ServerValue) servers.get(0);
    }

    /**
     * Find all servers on a given platform
     *
     * @return A list of ServerValue objects
     * @ejb:interface-method
     */
    public PageList findServersByPlatform(int sessionID, Integer platformId,
                                          PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        return findServers(sessionID,
                           AppdefEntityConstants.APPDEF_TYPE_PLATFORM,
                           platformId, pc);
    }

    /**
     * Get the virtual servers for a given platform
     * @ejb:interface-method
     */
    public PageList findViewableServersByPlatform(int sessionID,
                                                 Integer platformId,
                                                 PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().getServersByPlatform(subject, platformId,
                                                       true, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServerTypesByPlatform (int sessionID,
                                               Integer platformId,
                                               PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
       AuthzSubjectValue subject = manager.getSubject(sessionID);

       return getServerManager().getServerTypesByPlatform(subject, platformId, 
                                                          true, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServerTypesByPlatformType(int sessionID,
                                                  Integer platformId,
                                                  PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);

        return getServerManager().getServerTypesByPlatformType(subject,
                                                               platformId,
                                                               pc);
    }

    private PageList findServers(int sessionID, int findByType,
                                 Integer typeId, PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
      return findServers(sessionID, findByType, typeId,
                         APPDEF_RES_TYPE_UNDEFINED, pc);
    }

    private PageList findServers(int sessionID, int findByType,
                                 Integer typeId, int servTypeId,
                                 PageControl pc)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        ServerManagerLocal serverMan = getServerManager();
        AuthzSubjectValue subject;
        PageList res;

        // Get the AuthzSubject for the user's session
        subject = manager.getSubject(sessionID);

        switch (findByType) {
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM :
            if (servTypeId == APPDEF_RES_TYPE_UNDEFINED) {
                res = serverMan.getServersByPlatform(subject, typeId, 
                                                     false, pc);
            } else {
                // exclude virtual servers
                res = serverMan.getServersByPlatform(subject, typeId, 
                                                     new Integer(servTypeId),
                                                     true, pc);
            }
            break;
        case AppdefEntityConstants.APPDEF_TYPE_APPLICATION :
            res = serverMan.getServersByApplication(subject, typeId, pc);
            break;
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE :
            ServerValue val;
            val = serverMan.getServerByService(subject, typeId);
            res = new PageList();
            res.add(val);
            break;
        default :
            log.error("Invalid type given to find server.");
            res = null;
        }
        return res;
    }

    /**
     * Get all platforms in the inventory.
     *
     * @ejb:interface-method
     * @param sessionID The current session token.
     * @param pc a PageControl object which determines the size of the page and
     * the sorting, if any.
     * @return A List of PlatformValue objects representing all of the
     * platforms that the given subject is allowed to view.
     */
    public PageList findAllPlatforms(int sessionID, PageControl pc)
        throws FinderException, SessionTimeoutException, 
               SessionNotFoundException, PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
    
        return getPlatformManager().getAllPlatforms(subject, pc);
    }

    /**
     * Get recently created platforms in the inventory.
     *
     * @ejb:interface-method
     * @param sessionID The current session token.
     * @return A List of PlatformValue objects representing all of the
     * platforms that the given subject is allowed to view that was created in
     * the past time range specified.
     */
    public PageList findRecentPlatforms(int sessionID, long range, int size)
        throws FinderException, SessionTimeoutException, 
               SessionNotFoundException, PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getPlatformManager().getRecentPlatforms(subject, range, size);
    }

    /**
     * Looks up and returns a list of value objects corresponding
     * to the list of appdef entity represented by the instance ids
     * passed in. The method does not require
     * the caller to know the instance-id's corresponding type. Similarly,
     * the return value is upcasted.
     * @return list of appdefResourceValue
     * @ejb:interface-method
     */
    public PageList findByIds (int sessionId, AppdefEntityID[] entities)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException 
    {
        return findByIds(sessionId, entities, null);
    }

    /**
     * Looks up and returns a list of value objects corresponding
     * to the list of appdef entity represented by the instance ids
     * passed in. The method does not require the caller to know
     * the instance-id's corresponding type. Similarly,
     * the return value is upcasted.
     * @return list of appdefResourceValue
     * @ejb:interface-method
     */
    public PageList findByIds(int sessionId, AppdefEntityID[] entities,
                              PageControl pc)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        // get the user
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        List appdefList = new ArrayList();

        for (int i = 0; i < entities.length; i++) {
            try {
                appdefList.add(findById(subject, entities[i]));
            } catch (AppdefEntityNotFoundException e) {
                log.debug("Entity not found: " + entities[i]);
            }
        }
        
        if (pc != null) {
            Collections.sort(appdefList);            
            if (pc.getSortorder() == PageControl.SORT_DESC)
                Collections.reverse(appdefList);
            
            return Pager.getDefaultPager().seek(appdefList, pc);
        }
        return new PageList(appdefList, appdefList.size());
    }

    /**
     * Looks up and returns a value object corresponding to the appdef entity
     * represented by the instance id passed in. The method does not require
     * the caller to know the instance-id's corresponding type. Similarly,
     * the return value is upcasted.
     * @ejb:interface-method
     * */
    public AppdefResourceValue findById (int sessionId,
                                         AppdefEntityID entityId)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        // get the user
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefEntityValue aev = new AppdefEntityValue(entityId, subject);
        return aev.getResourceValue();
    }

    /**
     * TODO: this needs to be a batch query operation at the DAO layer
     * TODO: requires object model change at the db level to do it properly
     * TODO: AppdefResourceType includes all but the APPDEF_TYPE_GROUP.
     *
     * Looks up and returns a value object corresponding to the appdef entity
     * represented by the instance id passed in. The method does not require
     * the caller to know the instance-id's corresponding type. Similarly,
     * the return value is upcasted.
     * */
    private AppdefResourceValue findById(AuthzSubjectValue subject,
                                         AppdefEntityID entityId)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AppdefResourceValue retVal = null;
        
        switch (entityId.getType()) {
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
            Platform platform = getPlatformManager().getPlatformById(entityId.getId());
            if (platform == null) {
                throw new PlatformNotFoundException(entityId);
            }
            retVal = platform.getPlatformValue();
            break;
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            Server server = getServerManager().getServerById(entityId.getId());
            if (server == null) {
                throw new ServerNotFoundException(entityId);
            }
            retVal = server.getServerValue();
            retVal.setHostName(server.getPlatform().getName());
            break;
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            Service service = getServiceManager().getServiceById(entityId.getId());
            if (service == null) {
                throw new ServiceNotFoundException(entityId);
            }
            retVal = service.getServiceValue();
            retVal.setHostName(service.getServer().getName());
            break;
        case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
            retVal = 
                getApplicationManager().getApplicationById(subject,
                                                           entityId.getId());
            break;
        case AppdefEntityConstants.APPDEF_TYPE_GROUP:
            try {
                retVal = getAppdefGroupManager().findGroup(subject, 
                                                     entityId.getId());
            } catch (Exception e) {
                throw new AppdefGroupNotFoundException(
                    "The group referred to as " + entityId.getID() +
                    " does not exist: " + e.getMessage());
            }
            break;
        }

        if (retVal == null) {
            throw new IllegalArgumentException(entityId.getType()
                    + " is not a valid appdef entity type");
        }
        
        return retVal;
    }

    /**
     * @ejb:interface-method
     */
    public PlatformValue findPlatformById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getPlatformManager().getPlatformValueById(subject, id);
    }

    /**
     * @ejb:interface-method
     */
    public AgentValue findResourceAgent(AppdefEntityID entityId)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AgentNotFoundException 
    {
        return getAgentManager().getAgent(entityId);
    }

    /**
     * @ejb:interface-method
     */
    public PlatformValue findPlatformByName(int sessionID, String name)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getPlatformManager().getPlatformByName(subject, name);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findAllServers(int sessionID, PageControl pc)
        throws FinderException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);

        return getServerManager().getAllServers(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public ServerValue findServerById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().getServerById(subject, id);
    }

    /**
     * @ejb:interface-method
     */
    public ServerValue[] findServersByName(int sessionID, String name)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().getServersByName(subject, name);
    }


    /**
     * @return a List of ServiceValue objects
     * @ejb:interface-method
     */
    public PageList findAllServices(int sessionID, PageControl pc)
        throws FinderException, SessionTimeoutException, 
               SessionNotFoundException, PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getAllServices(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public ServiceValue findServiceById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getServiceById(subject, id);
    }

    /**
     * @ejb:interface-method
     */
    public ServiceValue[] findServicesByName(int sessionID, String name)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().findServicesByName(subject, name);
    }
    
    /**
     * @return A PageList of all registered appdef resource types
     * as well as the three group specific resource types.
     * @ejb:interface-method
     */
     public PageList findAllResourceTypes (int sessionId, PageControl pc )
         throws SessionTimeoutException, SessionNotFoundException,
                SystemException,PermissionException {
         return findAllResourceTypes (sessionId, APPDEF_TYPE_UNDEFINED, pc);
     }
    
    /**
     * @return A PageList of all registered appdef resource types
     * of a particular entity type.
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findAllResourceTypes(int sessionId, int entType,
                                         PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               SystemException, PermissionException 
    {
        List toBePaged;
        Pager defaultPager;
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        toBePaged    = new ArrayList();  // at very least, return empty list.
        defaultPager = Pager.getDefaultPager();
        
        try {
            boolean allFlag  = false;
            PageControl lpc  = PageControl.PAGE_ALL;
            
            PageControl.initDefaults(lpc, SortAttribute.RESTYPE_NAME);
            
            if (entType == APPDEF_TYPE_UNDEFINED) {
                allFlag = true;
            }
            
            if (allFlag ||
                entType == AppdefEntityConstants.APPDEF_TYPE_PLATFORM ) 
            {
                toBePaged.addAll(getPlatformManager().getViewablePlatformTypes(subject, lpc));
            }

            if (allFlag || entType == AppdefEntityConstants.APPDEF_TYPE_SERVER)
            {
                toBePaged.addAll(getServerManager().getViewableServerTypes(subject, lpc));
            }
            if (allFlag ||
                entType == AppdefEntityConstants.APPDEF_TYPE_SERVICE ) 
            {
                toBePaged.addAll(getServiceManager().getViewableServiceTypes(subject, lpc));
            }
            if (allFlag ||
                entType == AppdefEntityConstants.APPDEF_TYPE_APPLICATION ) 
            {
                toBePaged.addAll(getApplicationManager().getAllApplicationTypes(subject));
            }
            if (allFlag || entType == AppdefEntityConstants.APPDEF_TYPE_GROUP){
                AppdefResourceTypeValue tvo;
                
                // For groups we have "psuedo" AppdefResourceTypes.
                int groupTypes[] =
                    AppdefEntityConstants.getAppdefGroupTypesNormalized();
                
                for (int i=0;i<groupTypes.length;i++) {
                    tvo = new GroupTypeValue();
                    tvo.setId( new Integer( groupTypes[i] ) );
                    tvo.setName(AppdefEntityConstants.getAppdefGroupTypeName(
                                                                             groupTypes[i]));
                    toBePaged.add(tvo);
                }
            }
        } catch (FinderException e) {
            log.debug("Caught harmless FinderException no resource " +
                      "types defined.");
        }
        
        return defaultPager.seek(toBePaged,pc.getPagenum(),pc.getPagesize());
     }

    /**
     * @param platTypePK - the type of platform
     * @return PlatformValue - the saved Value object
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PlatformValue createPlatform(int sessionID,
                                        PlatformValue platformVal,
                                        Integer platTypePK,
                                        Integer agent)
        throws NamingException, CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefDuplicateNameException ,
               AppdefDuplicateFQDNException, ApplicationException
    {
        try {
            // Get the AuthzSubject for the user's session
            AuthzSubjectValue subject = manager.getSubject(sessionID);
            Integer pk =
                getPlatformManager().createPlatform(subject, platTypePK,
                                                    platformVal, agent);
            PlatformValue savedPlatform = 
                getPlatformManager().getPlatformValueById(subject, pk);
            return savedPlatform;
        } catch (CreateException e) {
            log.error("Unable to create platform. Rolling back", e);
            throw e;
        } catch (AppdefDuplicateNameException e) {
            log.error("Unable to create platform. Rolling back", e);
            throw e;
        } catch (AppdefDuplicateFQDNException e) {
            log.error("Unable to create platform. Rolling back", e);
            throw e;
        } catch(PlatformNotFoundException e) {
            log.error("Unable to create platform. Rolling back", e);
            throw new CreateException("Error occurred creating platform:"
                                           + e.getMessage());
        } catch (ApplicationException e) {
            log.error("Unable to create platform. Rolling back", e);
            throw e;
        }
    }

    /**
     * @ejb:interface-method
     */
    public AppdefResourceTypeValue findResourceTypeById(int sessionID, 
                                                        AppdefEntityTypeID id)
        throws SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        try {
            switch(id.getType()) {
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                    return getPlatformManager().findPlatformTypeValueById(id.getId());
                case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                    return getServerManager().findServerTypeById(id.getId());
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                    return getServiceManager().findServiceTypeById(id.getId());
                default:
                    throw new IllegalArgumentException("Unknown appdef type: "
                                                       + id);
            }
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }                      
    
    /**
     * @ejb:interface-method
     */
    public PlatformTypeValue findPlatformTypeById(int sessionID, Integer id)
        throws PlatformNotFoundException,
               SessionTimeoutException, SessionNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getPlatformManager().findPlatformTypeValueById(id);
    }

    /**
     * @ejb:interface-method
     */
    public PlatformTypeValue findPlatformTypeByName(int sessionID, String name)
        throws PlatformNotFoundException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getPlatformManager().findPlatformTypeByName(name);
    }

    /**
     * @ejb:interface-method
     */
    public ServiceTypeValue findServiceTypeById(int sessionID, Integer id)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().findServiceTypeById(id);
    }

    /**
     * @ejb:interface-method
     */
    public ServiceTypeValue findServiceTypeByName(int sessionID, String name)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().findServiceTypeByName(name);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServiceTypesByServerType(int sessionID, 
                                                 int serverTypeId)
        throws SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServiceManager().getServiceTypesByServerType(subject, 
                                                               serverTypeId);
    }
    
    /**
     * @ejb:interface-method
     */
    public ServerTypeValue findServerTypeById(int sessionID,  Integer id)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().findServerTypeById(id);
    }

    /**
     * @ejb:interface-method
     */
    public ServerTypeValue findServerTypeByName(int sessionID,
                                                String name)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getServerManager().findServerTypeByName(name);
    }

    /**
     * Private method to call the setCPropValue from a Map
     * @param subject  Subject setting the values
     * @param cProps A map of String key/value pairs to set
     */
    private void setCPropValues(AuthzSubjectValue subject,
                                AppdefEntityID entityId, Map cProps)
        throws SessionNotFoundException, SessionTimeoutException,
               CPropKeyNotFoundException, AppdefEntityNotFoundException,
               PermissionException
    {
        CPropManagerLocal cpropMan;
        AppdefEntityValue aVal;

        cpropMan = this.getCPropManager();
        aVal = new AppdefEntityValue(entityId, subject);
        int typeId = aVal.getResourceTypeValue().getId().intValue();
        for (Iterator i = cProps.keySet().iterator(); i.hasNext(); ) {
            String key = (String)i.next();

            cpropMan.setValue(entityId, typeId, key, (String)cProps.get(key));
        }
    }

    /**
     * @param platformPK - the pk of the host platform
     * @param serverTypePK - the type of server
     * @return ServerValue - the saved server
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServerValue createServer(int sessionID, ServerValue serverVal,
                                    Integer platformPK,
                                    Integer serverTypePK)
        throws NamingException, CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefDuplicateNameException
    {
        try {
            return createServer(sessionID, serverVal, platformPK,
                                serverTypePK, null);
        } catch(CPropKeyNotFoundException exc){
            this.log.error("Error setting no properties for new server");
            throw new SystemException("Error setting no properties.  Hmm",
                                      exc);
        }
    }

    /**
     * Create a server with CProps
     * @param platformPK - the pk of the host platform
     * @param serverTypePK - the type of server
     * @param cProps - the map with Custom Properties for the server
     * @return ServerValue - the saved server
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServerValue createServer(int sessionID, ServerValue serverVal,
                                    Integer platformPK,
                                    Integer serverTypePK, Map cProps)
        throws NamingException, CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefDuplicateNameException,
               CPropKeyNotFoundException
    {
        try {
            // Get the AuthzSubject for the user's session
            AuthzSubjectValue subject = manager.getSubject(sessionID);

            // Call into appdef to create the platform.
            ServerManagerLocal serverMan = getServerManager();
            Integer pk = serverMan.createServer(subject, platformPK,
                                                 serverTypePK, serverVal);
            ServerValue savedServer = serverMan.getServerById(subject, 
                                                              pk);
            if (cProps != null) {
                AppdefEntityID entityId = savedServer.getEntityId();
                setCPropValues(subject, entityId, cProps);
            }

            return savedServer;
        } catch (AppdefEntityNotFoundException e) {
            log.error("Unable to create server.", e);
            throw new SystemException("Unable to find new server");
        }
    }

    /**
     * Create an application
     * @return ApplicationValue - the saved application
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public ApplicationValue createApplication(int sessionID,
                                              ApplicationValue appVal,
                                              Collection services,
                                              ConfigResponse protoProps)
        throws CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               FinderException, PermissionException,
               AppdefDuplicateNameException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        ApplicationManagerLocal appMan = getApplicationManager();
            
        Integer pk =
            appMan.createApplication(subject, appVal, services);
        try {
            return appMan.getApplicationById(subject, pk);
        } catch (ApplicationNotFoundException e) {
            // Should never happen
            throw new SystemException("Could not find app we just created");
        }
    }

    /**
     * @param serviceTypePK - the type of service
     * @param aeid - the appdef entity ID
     * @return ServiceValue - the saved ServiceValue
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceValue createService(int sessionID, ServiceValue serviceVal,
                                      Integer serviceTypePK,
                                      AppdefEntityID aeid)
        throws SessionNotFoundException, SessionTimeoutException,
               ServerNotFoundException, PlatformNotFoundException,
               PermissionException, AppdefDuplicateNameException,
               ValidationException, CreateException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        try {
            Integer serverPK;
            if (aeid.getType() == AppdefEntityConstants.APPDEF_TYPE_PLATFORM) {
                // Look up the platform's virtual server
                List servers = getServerManager()
                    .getServersByPlatformServiceType(subject, aeid.getId(),
                                                     serviceTypePK);
                
                // There should only be 1 virtual server of this type
                ServerValue server = (ServerValue) servers.get(0);
                serverPK = server.getId();
            } else {
                serverPK = aeid.getId();
            }
            return createService(sessionID, serviceVal, serviceTypePK, 
                                 serverPK, null);
        } catch(CPropKeyNotFoundException exc){
            this.log.error("Error setting no properties for new service");
            throw new SystemException("Error setting no properties.  Hmm", 
                                      exc);
        }
    }

    /**
     * @param serviceTypePK -
     *            the type of service
     * @param serverPK -
     *            the server host
     * @return ServiceValue - the saved ServiceValue
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceValue createService(int sessionID,
                                      ServiceValue serviceVal,
                                      Integer serviceTypePK,
                                      Integer serverPK)
        throws NamingException, CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefDuplicateNameException
    {
        try {
            return createService(sessionID, serviceVal, serviceTypePK,
                                 serverPK, null);
        } catch(CPropKeyNotFoundException exc){
            this.log.error("Error setting no properties for new service");
            throw new SystemException("Error setting no properties.  Hmm",
                                      exc);
        }
    }

    /**
     * Create a service with CProps
     * @param serviceTypePK - the type of service
     * @param serverPK - the server host
     * @param cProps - the map with Custom Properties for the service
     * @return ServiceValue - the saved ServiceValue
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceValue createService(int sessionID,
                                      ServiceValue serviceVal,
                                      Integer serviceTypePK,
                                      Integer serverPK, Map cProps)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefDuplicateNameException, ValidationException,
               PermissionException, CreateException, CPropKeyNotFoundException
    {
        try {
            // Get the AuthzSubject for the user's session
            AuthzSubjectValue subject = manager.getSubject(sessionID);
            ServiceManagerLocal svcMan = getServiceManager();
            Integer pk = svcMan.createService(subject, serverPK,
                                              serviceTypePK, serviceVal);
            ServiceValue savedService;
            try {
                savedService = svcMan.getServiceById(subject, pk);
            } catch (ServiceNotFoundException e) {
                throw new SystemException("Could not find service we " +
                                             " just created");
            }
            if(cProps != null ) {
                AppdefEntityID entityId = savedService.getEntityId();
                setCPropValues(subject, entityId, cProps);
            }

            return savedService;
        } catch (AppdefEntityNotFoundException e) {
            log.error("Unable to create service.", e);
            throw new SystemException("Unable to find new service");
        }
    }

    /**
     * Remove a platform.
     *
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removePlatform(int sessionId, Integer platformId)
        throws SessionNotFoundException, SessionTimeoutException,
               ApplicationException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        try {
            // Lookup the platform (make sure someone else hasn't deleted it)
            PlatformValue platRes =
                getPlatformManager().getPlatformValueById(subject,platformId);

            // Add it to the list
            List unscheduleList = new ArrayList();
            unscheduleList.add(platRes);

            // Add dependent children to the list of metrics to unschedule
            // XXX: Use POJOs here.
            ServerManagerLocal svrMgrLoc = getServerManager();
            unscheduleList.addAll(
                svrMgrLoc.getServersByPlatform(subject, platformId, false,
                                               PageControl.PAGE_ALL));

            ServiceManagerLocal svcMgrLoc= getServiceManager();
            unscheduleList.addAll(
                svcMgrLoc.getServicesByPlatform(subject, platformId,
                                                PageControl.PAGE_ALL));

            MeasurementBossLocal measBoss = getMeasurementBoss();
            
            AppdefEntityID[] toDeleteResourceIds =
                new AppdefEntityID[unscheduleList.size()];
            ArrayList toDeleteIdsList = new ArrayList();

            Iterator it = unscheduleList.iterator();
            for (int i = 0; it.hasNext(); i++) {
                AppdefResourceValue v =(AppdefResourceValue)it.next();
                AppdefEntityID thisId = v.getEntityId();

                if (v instanceof ServerValue &&
                    ((ServerValue)v).getServerType().getVirtual()) {
                    // skip virtual servers
                    toDeleteResourceIds[i] = thisId;
                    continue;
                }

                // now remove the alerts
                AlertDefinitionManagerEJBImpl.getOne()
                    .deleteAlertDefinitions(subject, thisId);
                toDeleteIdsList.add(thisId);
                toDeleteResourceIds[i] = thisId;
            }
            AppdefEntityID[] toDeleteIds = (AppdefEntityID[])
                toDeleteIdsList.toArray(new AppdefEntityID[0]);

            // now remove the measurements
            measBoss.removeMeasurements(sessionId, toDeleteIds);

            // Remove from AI queue
            try {
                AIBossLocal aiBoss = getAIBoss();
                List aiplatformList = new ArrayList();
                AIPlatformValue aiPlatform
                    = aiBoss.findAIPlatformByPlatformID(sessionId, 
                                                        platformId.intValue());
                aiplatformList.add(aiPlatform.getId());
                log.info("Removing from AIqueue: " + aiPlatform.getId());
                aiBoss.processQueue(sessionId, aiplatformList, null, null,
                                    AIQueueConstants.Q_DECISION_PURGE);
            } catch (PlatformNotFoundException e) {
                log.debug("AIPlatform not found: " + platformId);
            }
            
            // now, remove the platform.
            getPlatformManager().removePlatform(subject, platformId);

            // Last, remove authz resources
            getAuthzBoss().removeResources(toDeleteResourceIds);

        } catch (RemoveException e) {
            log.error("Caught EJB RemoveException",e);
            throw new SystemException(e);
        } catch (PermissionException e) {
            log.error("Caught PermissionException while removing "+
                      "platform: " + platformId,e);
            throw e;
        } catch (AppdefEntityNotFoundException e) {
            String msg = "Not Found Error while removing platform: " +
                platformId;
            log.error(msg, e);
            throw new PlatformNotFoundException(msg, e);
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public ServerValue updateServer(int sessionId, ServerValue aServer)
        throws NamingException, FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException
    {
        try {
            return updateServer(sessionId, aServer, null);
        } catch(CPropKeyNotFoundException exc){
            this.log.error("Error updating no properties for server");
            throw new SystemException("Error updating no properties.  Hmm",
                                      exc);
        }
    }

    /**
     * Update a server with cprops.
     * @param cProps - the map with Custom Properties for the server
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public ServerValue updateServer(int sessionId, ServerValue aServer,
                                    Map cProps)
        throws NamingException, FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException, CPropKeyNotFoundException
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);

            ServerValue updated
                = getServerManager().updateServer(subject, aServer);

            if(cProps != null ) {
                AppdefEntityID entityId = aServer.getEntityId();
                setCPropValues(subject, entityId, cProps);
            }
            return updated;
        } catch (Exception e) {
            log.error("Error updating server: " + aServer.getId());
            this.rollback();
            if(e instanceof NamingException) {
                throw (NamingException)e;
            } else if (e instanceof CreateException) {
                // change to a update exception as this only occurs
                // if there was a failure instantiating the session
                // bean
                throw new UpdateException("Error creating manager session " +
                                          "bean: " + e.getMessage());
            } else if (e instanceof PermissionException) {
                throw (PermissionException)e;
            } else if (e instanceof FinderException) {
                throw (FinderException)e;
            } else if (e instanceof AppdefDuplicateNameException) {
                throw (AppdefDuplicateNameException)e;
            } else if(e instanceof CPropKeyNotFoundException) {
                throw (CPropKeyNotFoundException)e;
            } else if(e instanceof AppdefEntityNotFoundException) {
                throw new SystemException("Unable to find updated server");
            } else {
                throw new UpdateException("Unknown error updating server: " +
                    aServer.getId(), e);
            }
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public ServiceValue updateService(int sessionId, ServiceValue aService)
        throws NamingException, FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException
    {
        try {
            return updateService(sessionId, aService, null);
        } catch(CPropKeyNotFoundException exc){
            this.log.error("Error updating no properties for service");
            throw new SystemException("Error updating no properties.  Hmm",
                                         exc);
        }
    }

    /**
     * Update a service with cProps.
     * @param cProps - the map with Custom Properties for the service
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public ServiceValue updateService(int sessionId, ServiceValue aService,
                                      Map cProps)
        throws NamingException, FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException, CPropKeyNotFoundException
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);

            ServiceValue updated
                = getServiceManager().updateService(subject, aService);

            if(cProps != null ) {
                AppdefEntityID entityId = aService.getEntityId();
                setCPropValues(subject, entityId, cProps);
            }
            return updated;
        } catch (Exception e) {
            log.error("Error updating service: " + aService.getId());
            this.rollback();
            if(e instanceof NamingException) {
                throw (NamingException)e;
            } else if (e instanceof CreateException) {
                // change to a update exception as this only occurs
                // if there was a failure instantiating the session
                // bean
                throw new UpdateException("Error creating manager session " +
                                          "bean:" + e.getMessage());
            } else if (e instanceof PermissionException) {
                throw (PermissionException)e;
            } else if (e instanceof FinderException) {
                throw (FinderException)e;
            } else if (e instanceof AppdefDuplicateNameException) {
                throw (AppdefDuplicateNameException)e;
            } else if(e instanceof CPropKeyNotFoundException) {
                throw (CPropKeyNotFoundException)e;
            } else if(e instanceof AppdefEntityNotFoundException) {
                throw new SystemException("Unable to find updated service");
            } else {
                throw new UpdateException("Unknown error updating service: " +
                                          aService.getId(), e);
            }
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PlatformValue updatePlatform(int sessionId, PlatformValue aPlatform)
        throws FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException, AppdefDuplicateFQDNException,
               ApplicationException
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);
            return getPlatformManager().updatePlatform(subject, aPlatform);
        } catch (Exception e) {
            log.error("Error updating platform: " + aPlatform.getId());
            // this.rollback();
            if(e instanceof NamingException) {
                throw new SystemException(e);
            } else if (e instanceof CreateException) {
                // change to a update exception as this only occurs
                // if there was a failure instantiating the session
                // bean
                throw new UpdateException("Error creating manager session " +
                                          "bean:" + e.getMessage());
            } else if (e instanceof PermissionException) {
                throw (PermissionException)e;
            } else if (e instanceof FinderException) {
                throw (FinderException)e;
            } else if (e instanceof AppdefDuplicateNameException) {
                throw (AppdefDuplicateNameException)e;
            } else if (e instanceof AppdefDuplicateFQDNException) {
                throw (AppdefDuplicateFQDNException)e;
            } else if(e instanceof ApplicationException) {
                throw (ApplicationException)e;
            } else {
                throw new UpdateException("Unknown error updating platform: " +
                                          aPlatform.getId(), e);
            }
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public ApplicationValue updateApplication(int sessionId,
                                              ApplicationValue app)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            return getApplicationManager().updateApplication(caller, app);
        } catch (PermissionException e) {
            this.rollback();
            throw e;
        } catch (FinderException e) {
            this.rollback();
            throw new ApplicationException(e);
        } catch (AppdefDuplicateNameException e) {
            this.rollback();
            throw e;
        } catch (Exception e) {
            this.rollback();
            throw new SystemException(e);
        }
    }

    /**
     * Set the services used by an application
     * indicate whether the service is an entry point
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void setApplicationServices(int sessionId, Integer appId,
                                       List entityIds)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            getApplicationManager().setApplicationServices(caller, appId, 
                                                           entityIds);
        } catch (PermissionException e) {
            this.rollback();
            throw e;
        } catch (Exception e) {
            this.rollback();
            throw new SystemException(e);
        }
    }

    /**
     * Get the dependency tree for a given application
     * @ejb:interface-method
     */
    public DependencyTree getAppDependencyTree(int sessionId, Integer appId)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            return getApplicationManager()
                .getServiceDepsForApp(caller, appId);
        } catch (PermissionException e) {
            this.rollback();
            throw e;
        } catch (Exception e) {
            this.rollback();
            throw new SystemException(e);
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void setAppDependencyTree(int sessionId, DependencyTree depTree)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            getApplicationManager().setServiceDepsForApp(caller, depTree);
        } catch (PermissionException e) {
            this.rollback();
            throw e;
        } catch (Exception e) {
            this.rollback();
            throw new SystemException(e);
        }
    }

    /**
     * Remove a Server from the inventory.
     *
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removeServer(int sessionId, Integer serverId)
        throws PermissionException, ServerNotFoundException,
               SessionNotFoundException, SessionTimeoutException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        try {
            ServerManagerLocal smLoc = getServerManager();

            // Lookup the server (make sure someone else hasn't deleted it)
            ServerValue serverRes = smLoc.getServerById(subject,serverId);

            // Add it to the list
            List unscheduleList = new ArrayList();
            unscheduleList.add(serverRes);

            // Unschedule service metrics.
            // XXX: Use POJO to lookup services.
            ServiceManagerLocal svcMgrLoc = getServiceManager();
            unscheduleList.addAll(
                svcMgrLoc.getServicesByServer(subject, serverId,
                                              PageControl.PAGE_ALL));


            MeasurementBossLocal measBoss = getMeasurementBoss();
                
            for (Iterator i=unscheduleList.iterator();i.hasNext();) {
                AppdefEntityID thisId =
                    ((AppdefResourceValue)i.next()).getEntityId();
                // now remove the alerts
                AlertDefinitionManagerEJBImpl.getOne()
                    .deleteAlertDefinitions(subject, thisId);

                // now remove the measurements
                measBoss.removeMeasurements(sessionId, thisId);

                // remove any log or config track plugins
                measBoss.removeTrackers(sessionId, thisId);
            }

            try {
                AutoinventoryManagerLocal aiManager = getAutoInventoryManager();
                aiManager.toggleRuntimeScan(getOverlord(),
                                            serverRes.getEntityId(),
                                            false);
            } catch (Exception e) {
                log.error("Error turning off RuntimeScan for: " + serverRes, 
                          e);
            }

            // finally, remove the server
            getServerManager().removeServer(subject, serverId);
        } catch (RemoveException e) {
            this.rollback();
            throw new SystemException(e);
        } catch (AppdefEntityNotFoundException e) {
            this.rollback();
            String msg = "Caught not found exception [server: "+serverId+"]";
            log.error(msg,e);
            throw new ServerNotFoundException(msg,e);
        } catch (PermissionException e) {
            this.rollback();
            log.error("Caught permission exception: [server:"+serverId+"]");
            throw e;
        }
    }

    /**
     * Remove a Service from the inventory.
     *  
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removeService(int sessionId, Integer serviceId)
        throws ApplicationException 
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);

            AppdefEntityID id = 
                AppdefEntityID.newServiceID(serviceId.intValue());

            // first remove the alerts
            AlertDefinitionManagerEJBImpl.getOne()
                .deleteAlertDefinitions(subject, id);

            // now remove any measurements associated with the service
            MeasurementBossLocal measBoss = getMeasurementBoss();
            measBoss.removeMeasurements(sessionId, id);

            // remove any log or config track plugins
            measBoss.removeTrackers(sessionId, id);
            
            getServiceManager().removeService(subject, serviceId);
        } catch (SessionTimeoutException e) {
            this.rollback();
            throw e;
        } catch (SessionNotFoundException e) {
            this.rollback();
            throw e;
        } catch (PermissionException e) {
            this.rollback();
            throw e;
        } catch (FinderException e) {
            this.rollback();
            throw new ApplicationException(e);
        } catch (RemoveException e) {
            this.rollback();
            throw new ApplicationException(e);
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removeApplication(int sessionId, Integer appId)
        throws ApplicationException, PermissionException, 
               SessionTimeoutException, SessionNotFoundException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            getApplicationManager().removeApplication(caller, appId);
        } catch (SessionNotFoundException e) {
            this.rollback();
            throw e;
        } catch (SessionTimeoutException e) {
            this.rollback();
            throw e;
        } catch (PermissionException e) {
            this.rollback();
            throw e;
        } catch (RemoveException e) {
            this.rollback();
            throw new ApplicationException(e);
        }
    }

    /**
     * Remove an application service.
     * @param appId         - The application identifier.
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removeAppService (int sessionId, Integer appId,
                                  Integer serviceId)
        throws ApplicationException, ApplicationNotFoundException,
               PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            getApplicationManager().removeAppService(caller, appId, serviceId);
        } catch (SystemException e) {
            this.rollback();
            throw e;
        }
    }
    /**
     * Process a batch of import data.  The batch consists of
     * platforms/servers/services/applications which need to be updated
     * or added.  A report is spit out the backend, which indicates
     * which operations occurred.
     *
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public String importBatchData(int sessionID, BatchImportData data)
        throws SessionTimeoutException, SessionNotFoundException,
               BatchImportException, PermissionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        ImportHelper helper;

        Validator.validate(data);
        helper = new ImportHelper(subject, data);
        String result = helper.process();
        getServerConfigManager().vacuumAppdef();
        return result;
    }

    /**
     * @return The updated Resource
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public AppdefResourceValue changeResourceOwner(int sessionId,
                                                   AppdefEntityID eid,
                                                   AuthzSubjectValue newOwner)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubjectValue caller = manager.getSubject(sessionId);
            Integer id = eid.getId();
            int type = eid.getType();
            switch(type) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                getPlatformManager().changePlatformOwner(caller, id, newOwner);
                return findPlatformById(sessionId, id);
            case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                getServerManager().changeServerOwner(caller, id, newOwner);
                return findServerById(sessionId, id);
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                getServiceManager().changeServiceOwner(caller, id, newOwner);
                return findServiceById(sessionId, id);
            case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                getApplicationManager().changeApplicationOwner(caller, id, 
                                                               newOwner);
                return findApplicationById(sessionId, id);
            case AppdefEntityConstants.APPDEF_TYPE_GROUP:
                getAppdefGroupManager().changeGroupOwner(caller, id, newOwner);
                return findGroup(sessionId, eid.getId());
            default:
                throw new InvalidAppdefTypeException("Unknown type: " + type);
            }
        } catch (PermissionException e) {
            throw e;
        } catch (FinderException e) {
            throw new ApplicationException(e);
        } catch (Exception e) {
            // everything else is a system error
            throw new SystemException(e);
        }
    }

    /**
     * Create and return a new mixed group value object. This group can
     * contain mixed resources of any entity/resource type combination
     * including platform, server and service.
     * @param name        - The name of the group.
     * @param description - A description of the group contents. (optional)
     * @param location    - Location of group (optional)
     * @return AppdefGroupValue object
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public AppdefGroupValue createGroup(int sessionId, String name,
                                        String description, String location)
        throws GroupCreationException, GroupDuplicateNameException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        return getAppdefGroupManager().createGroup(subject, name, description,
                                             location);
    }

    /**
     * Create and return a new strict mixed group value object. This
     * type of group can contain either applications or other
     * groups. However, the choice between between the
     * two is mutually exclusive because all group members must be
     * of the same entity type. Additionally, groups that contain
     * groups are limited to containing either "application groups" or
     * "platform,server&service groups".
     * @param adType      - The appdef entity type (groups or applications)
     * @param name        - The name of the group.
     * @param description - A description of the group contents. (optional)
     * @param location    - Location of group (optional)
     * @return AppdefGroupValue object
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public AppdefGroupValue createGroup(int sessionId, int adType,
                                        String name, String description, 
                                        String location)
        throws GroupCreationException, GroupDuplicateNameException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        return getAppdefGroupManager().createGroup(subject, adType, name,
                                             description, location);
    }

    /**
     * Create and return a new compatible group type object. This group type
     * can contain any type of platform, server or service. Compatible groups
     * are strict which means that all members must be of the same type.
     * Compatible group members must also be compatible which means that all
     * group members must have the same resource type. Compatible groups of
     * services have an additional designation of being of type "Cluster".
     * @param adType      - The type of entity this group is compatible with.
     * @param adResType   - The resource type this group is compatible with.
     * @param name        - The name of the group.
     * @param description - A description of the group contents. (optional)
     * @param location    - Location of group (optional)
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public AppdefGroupValue createGroup(int sessionId, int adType, 
                                        int adResType, String name, 
                                        String description, String location)
        throws GroupCreationException, GroupDuplicateNameException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        return getAppdefGroupManager().createGroup(subject, adType, adResType,
                                             name, description, location);
    }

    /**
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroupByName (int sessionId, String groupName)
        throws AppdefGroupNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        return findGroupByName(sessionId, groupName, PageControl.PAGE_ALL);
    }

    /**
     * @ejb:interface-method
     */
    public ResourceGroup findGroupById(int sessionId, Integer groupId) 
        throws AppdefGroupNotFoundException, PermissionException,
               SessionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        try {
            return getResourceGroupManager().findResourceGroupById(subject,
                                                                   groupId);
        } catch (FinderException e) {
            throw new AppdefGroupNotFoundException(groupId);
        }
    }
    
    /**
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroupByName(int sessionId, String groupName,
                                            PageControl pc)
        throws AppdefGroupNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);

            return getAppdefGroupManager().findGroupByName(subject, groupName, pc);
        } catch (AppdefGroupNotFoundException e) {
            log.debug("Caught 'group not found' exception.");
            throw e;
        }
    }

    /**
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroup(int sessionId, Integer id)
        throws AppdefGroupNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);
            return getAppdefGroupManager().findGroup(subject, id);
        } catch (AppdefGroupNotFoundException e) {
            log.error("Caught 'group not found' exception.");
            throw e;
        }
    }

    /**
     * Lookup and return a list of group value objects by their identifiers.
     * @return PageList of AppdefGroupValue objects
     * @throws AppdefGroupNotFoundException when group cannot be found.
     * @throws InvalidAppdefTypeException if group is compat and the appdef
     *        type id is incorrect.
     * @ejb:interface-method
     */
    public PageList findGroups(int sessionId, Integer[] groupIds,
                               PageControl pc)
        throws AppdefGroupNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException,
               SystemException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefGroupManagerLocal groupMan = getAppdefGroupManager();
        
        List toBePaged = new ArrayList(groupIds.length);
        for (int i=0; i < groupIds.length; i++) {
            toBePaged.add(groupMan.findGroup(subject, groupIds[i]));
        }
        return getPageList(toBePaged,pc);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Include just those groups that are of types
     * other than "group of group".
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * 
     */
    public PageList findAllGroupsForGroupAdd (int sessionId, PageControl pc,
                                              Integer[] removeIds)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        List filterList  = new ArrayList();
        
        // XXX - rewire to reflect new types
        // Define group member exclusionary filter
        //filterList.add(new AppdefGroupPagerFilterGrpTypeExclude (
        //AppdefEntityConstants.APPDEF_TYPE_GROUP_OF_GROUP));
        
        if (removeIds != null) {
            // convert to set and define exlusion filter on group set.
            Set grpExcludeSet = new HashSet();
            for (int i=0; i < removeIds.length; i++) {
                AppdefEntityID id = 
                    AppdefEntityID.newGroupID(removeIds[i].intValue());

                grpExcludeSet.add(id);
                filterList.add(new AppdefGroupPagerFilterExclude(grpExcludeSet));
            }
        }
        
        return findAllGroups(sessionId, pc, (AppdefPagerFilter[])
                             filterList.toArray(new AppdefPagerFilter[0]));
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Include just those groups that contain the
     * specified appdef entity.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * 
     */
    public PageList findAllGroupsMemberInclusive(int sessionId, PageControl pc,
                                                 AppdefEntityID entity)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException, ApplicationException 
    {
        return findAllGroupsMemberInclusive(sessionId, pc, entity, null, null);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Include just those groups that contain the
     * specified appdef entity.
     * @param entity for use in group member filtering.
     * @param removeIds of group ids for removing.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList findAllGroupsMemberInclusive(int sessionId, PageControl pc,
                                                 AppdefEntityID entity, 
                                                 Integer[] removeIds)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException, ApplicationException 
    {
        return findAllGroupsMemberInclusive(sessionId, pc, entity,
                                            removeIds, null);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Include just those groups that contain the
     * specified appdef entity. Apply group filter to remove unwanted
     * groups.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList 
        findAllGroupsMemberInclusive(int sessionId, PageControl pc,
                                     AppdefEntityID entity, 
                                     Integer[] removeIds,
                                     AppdefResourceTypeValue resType)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException, ApplicationException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        List filterList  = new ArrayList();
        
        if (removeIds != null) {
            // convert to set and define exlusion filter on group set.
            Set grpExcludeSet = new HashSet();
            for (int i = 0; i < removeIds.length; i++) {
                int groupId = removeIds[i].intValue();

                grpExcludeSet.add(AppdefEntityID.newGroupID(groupId));
            }
            filterList.add(new AppdefGroupPagerFilterExclude(grpExcludeSet));
        }
        
        return getAppdefGroupManager().findAllGroups(subject, entity, pc, 
                                               (AppdefPagerFilter[])
                                               filterList.toArray(new AppdefPagerFilter[0]));
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Exclude any groups that contain the appdef entity id.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList findAllGroupsMemberExclusive(int sessionId, PageControl pc,
                                                 AppdefEntityID entity)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        return findAllGroupsMemberExclusive(sessionId, pc, entity, null, null);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Exclude any groups that contain the appdef entity id.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList findAllGroupsMemberExclusive(int sessionId, PageControl pc,
                                                 AppdefEntityID entity, 
                                                 Integer[] removeIds)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        return findAllGroupsMemberExclusive(sessionId, pc, entity,
                                            removeIds, null);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Exclude any groups that contain the appdef entity id. Filter
     * out any unwanted groups specified by groupId array.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList 
        findAllGroupsMemberExclusive(int sessionId, PageControl pc,
                                     AppdefEntityID entity, 
                                     Integer[] removeIds,
                                     AppdefResourceTypeValue resType)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        List filterList  = new ArrayList();
        
        if (entity != null) {
            // Define exclusion filter to filter member set.
            filterList.add(new AppdefGroupPagerFilterMemExclude(entity));
        }
        
        if (resType != null) {
            // Add a filter to weed out groups incompatible with this entity
            filterList.add(new AppdefGroupPagerFilterGrpEntRes(
                    resType.getAppdefTypeId(), resType.getId().intValue(),
                    true));
        }
        
        if (removeIds != null) {
            // convert to set and define exlusion filter on group set.
            Set grpExcludeSet = new HashSet();
            for (int i=0; i<removeIds.length; i++) {
                int groupId = removeIds[i].intValue();
                grpExcludeSet.add(AppdefEntityID.newGroupID(groupId));
            }
            filterList.add(new AppdefGroupPagerFilterExclude(grpExcludeSet));
        }
        
        return findAllGroups(sessionId, pc, (AppdefPagerFilter[])
                             filterList.toArray (new AppdefPagerFilter[0]));
    }

    /**
     * Produce list of all group pojos where caller is authorized
     * @return List containing AppdefGroup.
     * @ejb:interface-method
     * */
    public Collection findAllGroupPojos(int sessionId)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException, FinderException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        ResourceGroupManagerLocal mgr = ResourceGroupManagerEJBImpl.getOne();
        
        Collection resGrps = mgr.getAllResourceGroups(subject, true);
        
        // We only want the appdef resource groups
        for (Iterator it = resGrps.iterator(); it.hasNext(); ) {
            ResourceGroup resGrp = (ResourceGroup) it.next();
            if (resGrp.isSystem()) {
                it.remove();
            }
        }
        return resGrps;
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList findAllGroups(int sessionId, PageControl pc)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        return findAllGroups(sessionId, pc, null);
    }

    private PageList findAllGroups(int sessionId, PageControl pc,
                                   AppdefPagerFilter[] grpFilters)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        PageList retVal;

        AuthzSubjectValue subject = manager.getSubject(sessionId);
        retVal = getAppdefGroupManager().findAllGroups(subject, pc, grpFilters);
        if (retVal == null)
            retVal = new PageList();  // return empty list if no groups.
        return retVal;
    }

    /**
     * Save a group back to persistent storage.
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void saveGroup(int sessionID, AppdefGroupValue gv)
        throws GroupNotCompatibleException, GroupModificationException,
               GroupDuplicateNameException, 
               AppSvcClustDuplicateAssignException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException 
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionID);
            getAppdefGroupManager().saveGroup(subject, gv);
        } catch (GroupModificationException e) {
            log.debug("Caught group modification exception on save.");
            throw e;
        }
    }
    
    // Return a PageList of authz resources.
    private List findViewableEntityIds(AuthzSubjectValue subject, 
                                       int appdefTypeId, String rName,
                                       Integer filterType, PageControl pc) 
    {
        List appentResources = new ArrayList();

        ResourceManagerLocal resMgr = getResourceManager();

        if (appdefTypeId != APPDEF_TYPE_UNDEFINED) {
            String authzResType = 
                AppdefUtil.appdefTypeIdToAuthzTypeStr(appdefTypeId);

            String appdefTypeStr;
            if (filterType != null) {
                switch(appdefTypeId) {
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                    appdefTypeStr =
                        AppdefEntityConstants.typeToString(appdefTypeId);
                    break;
                default:
                    appdefTypeStr = null;
                    break;
                }
            }
            else {
                appdefTypeStr = null;
            }
            
            List instanceIds =
                resMgr.findViewableInstances(subject, authzResType, rName, 
                                             appdefTypeStr, filterType, pc);
            
            for (Iterator i = instanceIds.iterator(); i.hasNext(); ) {
                appentResources.add(new AppdefEntityID(appdefTypeId, 
                                                       (Integer)i.next()));
            }
        } else {
            Map authzResources = resMgr.findAllViewableInstances(subject);
            for (Iterator it = authzResources.entrySet().iterator();
                 it.hasNext(); ) 
            {
                Map.Entry entry = (Map.Entry) it.next();
                
                int appdefType;
                try {
                    String typeName = (String) entry.getKey();
                    appdefType = AppdefUtil.resNameToAppdefTypeId(typeName);
                } catch (InvalidAppdefTypeException e) {
                    // ignore type
                    continue;
                }
                
                List instIds = (List) entry.getValue();
                
                Iterator instIdsIt = instIds.iterator();
                for (int i = 0; instIdsIt.hasNext(); i++) {
                    Integer instId = (Integer) instIdsIt.next();
                    appentResources.add(new AppdefEntityID(appdefType, 
                                                           instId));
                }
             }
        }
        return appentResources;
    }

   /**
     * Produce list of compatible, viewable inventory items.
     * The returned list of value objects will consist only of group inventory
     * compatible with the the specified group type.
     *
     * NOTE: This method returns an empty page list when no compatible
     *       inventory is found.
     * @param groupType       - the optional group type
     * @param appdefTypeId    - the id correponding to the type of entity.
     *                          example: group, platform, server, service
     *                          NOTE: A valid entity type id is now MANDATORY!
     * @param appdefResTypeId - the id corresponding to the type of resource
     *                          example: linux, jboss, vhost
     * @param resourceName    - resource name (or name substring) to search for.
     * @return page list of value objects that extend AppdefResourceValue
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PageList findCompatInventory(int sessionId, int groupType,
                                        int appdefTypeId, int groupEntTypeId,
                                        int appdefResTypeId,
                                        String resourceName,
                                        AppdefEntityID[] pendingEntities,
                                        PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {
        if ( groupType != APPDEF_GROUP_TYPE_UNDEFINED &&
             !AppdefEntityConstants.groupTypeIsValid(groupType) ) {
            throw new IllegalArgumentException ("Invalid group type: " +
                                                groupType);
        }

        return findCompatInventory(sessionId, appdefTypeId, appdefResTypeId, 
                                   groupEntTypeId, null, false,
                                   pendingEntities, resourceName, null, groupType, pc);
    }

    /**
     * Produce list of compatible, viewable inventory items.
     *
     * NOTE: This method returns an empty page list when no compatible
     *       inventory is found.
     *
     * @param appdefTypeId    - the id correponding to the type of entity.
     *                          example: platform, server, service
     *                          NOTE: A valid entity type id is now MANDATORY!
     * @param appdefResTypeId - the id corresponding to the type of resource
     *                          example: linux, jboss, vhost
     * @return page list of value objects that extend AppdefResourceValue
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PageList findCompatInventory(int sessionId, int appdefTypeId,
                                        int appdefResTypeId,
                                        AppdefEntityID groupEntity,
                                        AppdefEntityID[] pendingEntities,
                                        PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        return findCompatInventory(sessionId, appdefTypeId, appdefResTypeId,
                                   APPDEF_GROUP_TYPE_UNDEFINED, groupEntity,
                                   false, pendingEntities, null,
                                   null, APPDEF_GROUP_TYPE_UNDEFINED, pc);
    }

   /**
     * Produce list of compatible, viewable inventory items.
     * The returned list of value objects will be filtered
     * on AppdefGroupValue -- if the group contains the entity,
     * then then the entity will not be included in the returned set.
     *
     * NOTE: This method returns an empty page list when no compatible
     *       inventory is found.
     * @param appdefTypeId    - the id correponding to the type of entity
     *                          example: platform, server, service
     *                          NOTE: A valid entity type id is now MANDATORY!
     * @param appdefResTypeId - the id corresponding to the type of resource
     *                          example: linux, jboss, vhost
     * @param groupEntity     - the appdef entity of a group value who's
     *                          members are to be filtered out of result set.
     * @param resourceName    - resource name (or name substring) to search for.
     * @return page list of value objects that extend AppdefResourceValue
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PageList findCompatInventory(int sessionId, int appdefTypeId,
                                        int appdefResTypeId,
                                        AppdefEntityID groupEntity,
                                        AppdefEntityID[] pendingEntities,
                                        String resourceName, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        PageList ret = findCompatInventory(sessionId, appdefTypeId,
                                           appdefResTypeId,
                                           APPDEF_GROUP_TYPE_UNDEFINED,
                                           groupEntity, false,
                                           pendingEntities, resourceName,
                                           null, APPDEF_GROUP_TYPE_UNDEFINED, pc);

        if (appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_SERVER ||
            appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_SERVICE) 
        {
            AuthzSubjectValue subject = manager.getSubject(sessionId);
            
            for (Iterator i = ret.iterator(); i.hasNext(); ) {
                AppdefResourceValue res = (AppdefResourceValue) i.next();

                if (appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_SERVER) {
                    ServerValue server = getServerManager()
                        .getServerById(subject, res.getId());
                    res.setHostName(server.getPlatform().getName());

                } else {
                    ServiceValue service =
                        getServiceManager().getServiceById(subject, res.getId());
                    res.setHostName(service.getServer().getName());
                }
            }
        }
        return ret;
    }

    private PageList findCompatInventory(int sessionId, int appdefTypeId,
                                         int appdefResTypeId, 
                                         int grpEntId, 
                                         AppdefEntityID groupEntity,
                                         boolean members, 
                                         AppdefEntityID[] pendingEntities,
                                         String resourceName,
                                         List filterList, int groupType,
                                         PageControl pc)
    throws PermissionException, SessionTimeoutException, 
           SessionNotFoundException 
    {
        List toBePaged;

        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefPagerFilterGroupEntityResource erFilter;
        AppdefPagerFilterAssignSvc assignedSvcFilter;
        AppdefPagerFilterGroupMemExclude groupMemberFilter;
        boolean groupEntContext = false;

        StopWatch watch = new StopWatch();
        watch.markTimeBegin("findCompatInventory");

        // init our (never-null) page and filter lists
        if (filterList == null) {
            filterList = new ArrayList();
        }
        assignedSvcFilter = null;
        groupMemberFilter = null;

        // add a pager filter for removing pending appdef entities
        if (pendingEntities != null) {
            filterList.add( new AppdefPagerFilterExclude ( pendingEntities ));
        }

        if (grpEntId != APPDEF_GROUP_TYPE_UNDEFINED) {
            groupEntContext = true;
        }

        // If the caller supplied a group entity for filtering, this will be
        // used for (i) removing inventory already in the group and (ii)
        // filtering out incompatible inventory. Otherwise, we assume a context
        // where groupType is explicitly passed in. (i.e. the
        // resource hub)
        if (groupEntity == null) {
            if (groupType == APPDEF_GROUP_TYPE_UNDEFINED) {
                if (appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
                    groupType = appdefResTypeId;
                    appdefResTypeId = APPDEF_RES_TYPE_UNDEFINED;
                }
            }
        } else {
            try {
                AppdefGroupValue gValue = findGroup(sessionId,
                                                    groupEntity.getId());
                groupType = gValue.getGroupType();
                groupMemberFilter = 
                    new AppdefPagerFilterGroupMemExclude(gValue, members);
                filterList.add( groupMemberFilter );
            } catch (AppdefGroupNotFoundException e) {
                // non-fatal; log and continue
                log.error("Unable to lookup group for inventory filtering",e);
            } catch (PermissionException e) {
                // Should never happen, finder accounts for permissions;
                log.error("Caught permission exc filtering on group",e);
            }
        }

        // If the resource search is not conducted by name, or if its
        // conducted by name in a compatible group context, then we
        // need a filter to perform on account of HTML selectors
        if (resourceName == null || appdefResTypeId != -1) {
            // Install a filter that uses group type, entity type and
            // resource type to filter the inventory set. This facilitates
            // the HTML selectors that appear all over the product.
            if (groupEntContext) {
                erFilter =
                    new AppdefPagerFilterGroupEntityResource (subject,
                                                              groupType,
                                                              grpEntId,
                                                              appdefResTypeId,
                                                              true );
                filterList.add( erFilter );
            } else if (groupEntity != null) {
                erFilter =
                    new AppdefPagerFilterGroupEntityResource (subject,
                                                              groupType,
                                                              appdefTypeId,
                                                              appdefResTypeId,
                                                              true );
                erFilter.setGroupSelected(true);
                filterList.add( erFilter );
            }
            else {
                erFilter = null;
            }
        } else {
            erFilter = null;
        }

        // find ALL viewable resources by entity (type or name) and
        // translate to appdef entities.
        // We have to create a new page control because we are no
        // longer limiting the size of the record set in authz.
        watch.markTimeBegin("findViewableEntityIds");
        Integer filterType =
            appdefResTypeId != -1 ? new Integer(appdefResTypeId) : null;
        
        toBePaged = findViewableEntityIds(subject,  appdefTypeId,
                                          resourceName, filterType, pc);
        watch.markTimeEnd("findViewableEntityIds");

        // Page it, then convert to AppdefResourceValue
        List finalList = new ArrayList();
        watch.markTimeBegin("getPageList");
        PageList pl = getPageList (toBePaged, pc, filterList);
        watch.markTimeEnd("getPageList");

        for (Iterator itr = pl.iterator();itr.hasNext();){
            AppdefEntityID ent = (AppdefEntityID) itr.next();
            
            AppdefEntityValue aev = new AppdefEntityValue(ent, subject);
            AppdefResourceValue resourceValue;
            
            try {
                resourceValue = aev.getLiteResourceValue();
            } catch (AppdefEntityNotFoundException e) {
                // XXX - hack to ignore the error.  This must have occurred
                // when we created the resource, and rolled back the
                // AppdefEntity but not the Resource
                log.error("Invalid entity still in resource table: " + ent);
                continue;
            }

            finalList.add(resourceValue);
        }

        int pendingSize = 0;
        if (pendingEntities != null)
            pendingSize = pendingEntities.length;

        int erFilterSize = 0;
        if (erFilter != null)
            erFilterSize = erFilter.getFilterCount();

        int assignedSvcFilterSize = 0;
        if (assignedSvcFilter != null)
            assignedSvcFilterSize = assignedSvcFilter.getFilterCount();

        int groupMemberFilterSize = 0;
        if (groupMemberFilter != null)
            groupMemberFilterSize = groupMemberFilter.getFilterCount();

        int adjustedSize = toBePaged.size() - erFilterSize - pendingSize -
        assignedSvcFilterSize - groupMemberFilterSize;
        watch.markTimeEnd("findCompatInventory");
        log.debug("findCompatInventory(): " + watch);
        return new PageList(finalList,adjustedSize);
    }

   /**
     * Produce list of compatible, viewable inventory items.
     * The returned list of value objects will be filtered
     * on Group -- if the group contains the entity.
     *
     * NOTE: This method returns an empty page list when no compatible
     *       inventory is found.
     * @param grpEntId        - the appdef entity of a group value who's
     *                          members are to be filtered for result set.
     * @param resourceName    - resource name (or name substring) to search for.
     * @return page list of value objects that extend AppdefResourceValue
     * @throws AppdefGroupNotFoundException if the group is not found
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PageList findCompatInventory(int sessionId, int appdefTypeId,
                                        int appdefResTypeId,
                                        AppdefEntityID groupEntity,
                                        String resourceName, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        return findCompatInventory(sessionId, appdefTypeId, appdefResTypeId,
                                   APPDEF_GROUP_TYPE_UNDEFINED, groupEntity,
                                   true, null, resourceName, null,
                                   APPDEF_GROUP_TYPE_UNDEFINED, pc);
    }

   /**
     * Find SERVICE compatible inventory. Specifically, find either all viewable
     * services or service clusters.  Services that are assigned to clusters
     * are not returned by this method. Value objects returned by this
     * method include ServiceValue and/or AppdefGroupValue. An array of pending
     * AppdefEntityID can also be specified for filtering. Additionally, a flag
     * indicating whether internal (true) or deployed (false) services should
     * be included in the return set.
     *
     * NOTE: This method returns an empty page list when no compatible
     *       inventory is found.
     * @param appdefTypeId    - the id correponding to the type of entity
     *                          valid values include: [SERVICE|GROUP]
     * @param appdefResTypeId - the id corresponding to the type of resource
     *                          example: linux, jboss, vhost
     * @param internalFlag    - show internal services (true) or
     *                          deployed services (false)
     * @return page list of value objects that extend AppdefResourceValue
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PageList 
        findCompatServiceInventory(int sessionId, int appdefTypeId,
                                   int appdefResTypeId, 
                                   AppdefEntityID[] pendingEntities, 
                                   boolean internalFlag, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        switch (appdefTypeId) {
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            List filterList = new ArrayList();
            
            if (internalFlag) {
                // show just internal services
                filterList.add(new AppdefPagerFilterInternalService(subject,
                                                                     true));
            } else {
                // filter out the internal services
                filterList.add( new AppdefPagerFilterInternalService(subject));
            }
            
            return findCompatInventory(sessionId, appdefTypeId,
                                       appdefResTypeId,
                                       APPDEF_GROUP_TYPE_UNDEFINED, null,
                                       false, pendingEntities,
                                       null, filterList,
                                       APPDEF_GROUP_TYPE_UNDEFINED, pc);
        case AppdefEntityConstants.APPDEF_TYPE_GROUP:
            return findCompatInventory(sessionId, appdefTypeId,
                                       appdefResTypeId,
                                       APPDEF_GROUP_TYPE_UNDEFINED, null,
                                       false, pendingEntities,
                                       null, null, APPDEF_GROUP_TYPE_UNDEFINED, pc);
        default:
            throw new IllegalArgumentException("Unsupported appdef type");
        }
    }

   /**
     * Find SERVICE compatible inventory. Specifically, find all viewable
     * services and service clusters.  Services that are assigned to clusters
     * are not returned by this method. Value objects returned by this
     * method include ServiceValue and/or AppdefGroupValue. An array of pending
     * AppdefEntityID can also be specified for filtering.
     *
     * NOTE: This method returns an empty page list when no compatible
     *       inventory is found.
     *
     * @param sessionId       - valid auth token
     * @return page list of value objects that extend AppdefResourceValue
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public PageList
        findCompatServiceInventory(int sessionId, Integer appId,
                                   AppdefEntityID[] pendingEntities,
                                   String resourceName, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        List toBePaged, filterList, authzResources;
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        // init our (never-null) page and filter lists
        toBePaged  = new ArrayList();
        filterList = new ArrayList();

        // add a pager filter for removing pending appdef entities
        if (pendingEntities != null) {
            filterList.add( new AppdefPagerFilterExclude ( pendingEntities ));
        }

        int oriPageSize = pc.getPagesize();
        pc.setPagesize( PageControl.SIZE_UNLIMITED );

        authzResources = getResourceManager()
            .findViewableSvcResources(subject, resourceName, pc);

        pc.setPagesize( oriPageSize );

        // Remove existing application assigned inventory
        List assigned = findServiceInventoryByApplication(sessionId, appId,
                                                         PageControl.PAGE_ALL);
        for (int x = 0; x < assigned.size(); x++) {
            assigned.set(x, ((AppdefResourceValue) assigned.get(x))
                         .getEntityId());
        }
        
        for (Iterator i = authzResources.iterator(); i.hasNext();) {
            ResourceValue rv = (ResourceValue) i.next();
            AppdefEntityID id = AppdefUtil.resValToAppdefEntityId(rv);
            if (!assigned.contains(id)) {
                toBePaged.add(id);
            }
        }

        // Page it, then convert to AppdefResourceValue
        List finalList = new ArrayList();
        PageList pl = getPageList (toBePaged, pc, filterList);
        for (Iterator itr = pl.iterator();itr.hasNext();){
            AppdefEntityID ent = (AppdefEntityID) itr.next();
            try {
                finalList.add( this.findById(subject,ent) );
            } catch (AppdefEntityNotFoundException e) {
                // XXX - hack to ignore the error.  This must have occurred when
                // we created the resource, and rolled back the AppdefEntity
                // but not the Resource
                log.error("Invalid entity still in resource table: " + ent);
            }
        }

        int pendingSize = 0;
        if (pendingEntities != null)
            pendingSize = pendingEntities.length;

        int adjustedSize = authzResources.size() - pendingSize;
        return new PageList(finalList,adjustedSize);
    }

    private PageList getPageList (Collection coll, PageControl pc) {
        return Pager.getDefaultPager().seek(coll, pc);
    }

    // Page out the collection, applying any filters in the process.
    private PageList getPageList (Collection coll, PageControl pc,
                                  List filterList) throws SystemException 
    {
        Pager pager;
        AppdefPagerFilter[] filterArr;

        pc = PageControl.initDefaults(pc, SortAttribute.RESTYPE_NAME);

        filterArr = (AppdefPagerFilter[])
            filterList.toArray (new AppdefPagerFilter[0]);

        try {
            pager = Pager.getPager( APPDEF_PAGER_PROCESSOR );
        } catch (InstantiationException e) {
            log.debug("InstantiationException caught instantiating "+
                      APPDEF_PAGER_PROCESSOR);
            throw new SystemException (e.getMessage());
        } catch (IllegalAccessException e) {
            log.debug("IllegalAccessException caught instantiating "+
                      APPDEF_PAGER_PROCESSOR);
            throw new SystemException (e.getMessage());
        } catch (ClassNotFoundException e) {
            log.debug("ClassNotFoundException caught instantiating "+
                      APPDEF_PAGER_PROCESSOR);
            throw new SystemException (e.getMessage());
        }
        return pager.seekAll(coll,pc.getPagenum(), pc.getPagesize(),
                             filterArr);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void deleteGroup(int sessionId, AppdefEntityID entity)
        throws  AppdefGroupNotFoundException, SessionTimeoutException,
                SessionNotFoundException, PermissionException,
                SystemException 
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);
            getAppdefGroupManager().deleteGroup(subject,entity);
        } catch (AppdefGroupNotFoundException e) {
            log.debug("Unable to locate group for removal");
            throw e;
        }
    }
    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void deleteGroup(int sessionId, Integer groupId)
        throws AppdefGroupNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException,
               SystemException 
    {
        try {
            AuthzSubjectValue subject = manager.getSubject(sessionId);
            getAppdefGroupManager().deleteGroup(subject,groupId);
        } catch (AppdefGroupNotFoundException e) {
            log.debug("Unable to locate group for removal");
            throw e;
        }
    }

    /**
     * Add an appdef entity to a batch of groups.
     *
     * @param sessionId representing session identifier
     * @param entityId object to be added.
     * @param groupIds identifier array
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void batchGroupAdd(int sessionId, AppdefEntityID entityId,
                              Integer[] groupIds)
        throws GroupNotCompatibleException, AppdefGroupNotFoundException,
               GroupModificationException, GroupDuplicateNameException,
               AppSvcClustDuplicateAssignException, PermissionException,
               SessionTimeoutException, SessionNotFoundException,
               SystemException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefGroupManagerLocal groupMan = getAppdefGroupManager();

        for (int i=0;i<groupIds.length;i++) {
            AppdefGroupValue agv = groupMan.findGroup(subject,groupIds[i]);
            agv.addAppdefEntity(entityId);
            groupMan.saveGroup(subject,agv);
        }
    }

    /**
     * Update all the appdef resources owned by this user to be owned
     * by the root user. This is done to prevent resources from being
     * orphaned in the UI due to its display restrictions. This method
     * should only get called before a user is about to be deleted
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void resetResourceOwnership(int sessionId,
                                       AuthzSubjectValue currentOwner)
        throws FinderException, UpdateException,
               PermissionException, AppdefEntityNotFoundException 
    {
        try {
            // first look up the appdef resources by owner
            ResourceValue[] resources
                = getResourceManager().findResourceByOwner(currentOwner);
            AuthzSubjectValue root = getAuthzSubjectManager().getRoot();
            for(int i = 0; i < resources.length; i++) {
                ResourceValue aRes = resources[i];
                String resType = aRes.getResourceTypeValue().getName();
                // platforms
                if(resType.equals(AuthzConstants.platformResType)) {
                    // change platform owner
                    getPlatformManager()
                        .changePlatformOwner(root,
                                             aRes.getInstanceId(),
                                             root);
                }
                // servers
                if(resType.equals(AuthzConstants.serverResType)) {
                    // change server owner
                    getServerManager()
                        .changeServerOwner(root, aRes.getInstanceId(), root);
                }
                if(resType.equals(AuthzConstants.serviceResType)) {
                    // change service owner
                    getServiceManager()
                        .changeServiceOwner(root, aRes.getInstanceId(), root);
                }
                if(resType.equals(AuthzConstants.applicationResType)) {
                    // change app owner
                    getApplicationManager()
                        .changeApplicationOwner(root, aRes.getInstanceId(),
                                                root);
                }
                if(resType.equals(AuthzConstants.groupResType)) {
                    // change group owner
                    getAppdefGroupManager()
                        .changeGroupOwner(root, aRes.getInstanceId(), root);
                }
            }
        } catch (CreateException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Remove an appdef entity from a batch of groups.
     * @param entityId object to be removed
     * @param groupIds identifier array
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void batchGroupRemove(int sessionId, AppdefEntityID entityId,
                                 Integer[] groupIds)
        throws GroupNotCompatibleException, AppdefGroupNotFoundException,
               GroupModificationException, AppSvcClustDuplicateAssignException,
               GroupDuplicateNameException, PermissionException,
               SessionTimeoutException, SessionNotFoundException,
               SystemException 
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefGroupManagerLocal groupMan = getAppdefGroupManager();

        for (int i=0;i<groupIds.length;i++) {
            AppdefGroupValue agv = groupMan.findGroup(subject,groupIds[i]);
            agv.removeAppdefEntity(entityId);
            groupMan.saveGroup(subject,agv);
        }
    }

    /**
     * @ejb:interface-method
     */
    public AppdefResourcePermissions getResourcePermissions(int sessionId,
                                                            AppdefEntityID id)
        throws SessionNotFoundException, SessionTimeoutException,
               FinderException 
    {
        try {
            AuthzSubjectValue who = manager.getSubject(sessionId);
            return getPlatformManager().getResourcePermissions(who, id);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public int getAgentCount(int sessionId)
        throws SessionNotFoundException, SessionTimeoutException
    {
        AuthzSubjectValue who = this.manager.getSubject(sessionId);
        return this.getAgentManager().getAgentCount();
    }

    /**
     * @ejb:interface-method
     */
    public PageList findAllAgents(int sessionId, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getAgentManager().getAgents(pc);
    }

    /**
     * Get all the unused agents in the system plus the one agent which
     * is used by the platform whose id = input.
     * @ejb:interface-method
     */
    public PageList findUnusedAgents(int sessionId, PageControl pc,
                                     Integer platformId )
        throws SessionNotFoundException, SessionTimeoutException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getAgentManager().getUnusedAgents(pc, platformId);
    }

    /**
     * Get the value of one agent based on the IP and Port on
     * which the agent is listening
     * @ejb:interface-method
     */
    public AgentValue findAgentByIpAndPort(int sessionId, String ip, int port)
        throws SessionNotFoundException, SessionTimeoutException,
               AgentNotFoundException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getAgentManager().getAgent(ip, port);
    }

    /**
     * Transfer files to a remote agent.
     *
     * @param id Appdef ID of any resource on this agent
     * @param files List of files and destinations
     * @param modes Writer mode.  One of FileData.WRITETYPE_*
     * @ejb:interface-method
     */
    public FileDataResult[] agentSendFileData(int sessionId, AppdefEntityID id,
                                              String[][] files, int[] modes)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, AgentNotFoundException,
               AgentRemoteException, AgentConnectionException,
               FileNotFoundException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getAgentManager().agentSendFileData(who, id, files, modes);
    }

    /**
     * Set (or delete) a custom property for a resource.  If the
     * property already exists, it will be overwritten.
     * @param id  Appdef entity to set the value for
     * @param key Key to associate the value with
     * @param val Value to assicate with the key.  If the value is null,
     *            then the value will simply be removed.
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void setCPropValue(int sessionId, AppdefEntityID id,
                              String key, String val)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException,
               CPropKeyNotFoundException
    {
        AuthzSubjectValue who = this.manager.getSubject(sessionId);
        AppdefEntityValue aVal = new AppdefEntityValue(id, who);
        int typeId = aVal.getResourceTypeValue().getId().intValue();
        this.getCPropManager().setValue(id, typeId, key, val);
    }

    /**
     * Get a custom property for a resource.
     * @param id  Appdef entity to get the value for
     * @param key Key of the value to get
     * @return The value associated with 'key' if found, else null
     *
     * @ejb:interface-method
     */
    public String getCPropValue(int sessionId, AppdefEntityID id, String key)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException,
               CPropKeyNotFoundException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getCPropManager().getValue(new AppdefEntityValue(id, who),
                                               key);
    }

    /**
     * Get a map which holds the keys & their associated values
     * for an appdef entity.
     * @param id  Appdef entity to get the custom entities for
     * @return The properties stored for a specific entity ID
     *         An empty Properties object will be returned if there are
     *         no custom properties defined for the resource
     * @ejb:interface-method
     */
    public Properties getCPropEntries(int sessionId, AppdefEntityID id)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, AppdefEntityNotFoundException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getCPropManager().getEntries(id);
    }

    /**
     * Get a map which holds the descriptions & their associated values
     * for an appdef entity.
     * @param id  Appdef entity to get the custom entities for
     * @return The properties stored for a specific entity ID
     * @ejb:interface-method
     */
    public Properties getCPropDescEntries(int sessionId, AppdefEntityID id)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, AppdefEntityNotFoundException
    {
        AuthzSubjectValue who = this.manager.getSubject(sessionId);
        return this.getCPropManager().getDescEntries(id);
    }

    /**
     * Add a key to a resource type.  The key's 'appdefType' and
     * 'appdefTypeId' fields are used to locate the resource -- if
     * that resource does not exist, an AppdefEntityNotFoundException
     * will be thrown.
     * @param key Key to create
     * @throws CPropKeyExistsException if the key already exists
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void addCPropKey(int sessionId, CPropKeyValue key)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, CPropKeyExistsException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        this.getCPropManager().addKey(key);
    }

    /**
     * Remove a key from a resource type.
     * @param appdefType   One of AppdefEntityConstants.APPDEF_TYPE_*
     * @param appdefTypeId The ID of the resource type
     * @param key          Key to remove
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void deleteCPropKey(int sessionId, int appdefType,
                               int appdefTypeId, String key)
        throws SessionNotFoundException, SessionTimeoutException,
               CPropKeyNotFoundException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        this.getCPropManager().deleteKey(appdefType, appdefTypeId, key);
    }

    /**
     * Get all the keys associated with an appdef resource type.
     * @param appdefType   One of AppdefEntityConstants.APPDEF_TYPE_*
     * @param appdefTypeId The ID of the appdef resource type
     * @return a List of CPropKeyValue objects
     * @ejb:interface-method
     */
    public List getCPropKeys(int sessionId, int appdefType, int appdefTypeId)
        throws SessionNotFoundException, SessionTimeoutException
    {
        AuthzSubjectValue who;

        who = this.manager.getSubject(sessionId);
        return this.getCPropManager().getKeys(appdefType, appdefTypeId);
    }

    /**
     * Get all the keys associated with an appdef type of a resource.
     * @param aeid The ID of the appdef resource
     * @return a List of CPropKeyValue objects
     * @throws PermissionException
     * @throws AppdefEntityNotFoundException
     * @ejb:interface-method
     */
    public List getCPropKeys(int sessionId, AppdefEntityID aeid)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException 
    {
        AuthzSubjectValue who = this.manager.getSubject(sessionId);
        
        AppdefEntityValue av = new AppdefEntityValue(aeid, who);
        int typeId = av.getResourceTypeValue().getId().intValue();
        
        return this.getCPropManager().getKeys(aeid.getType(), typeId);
    }

    /**
     * Get the appdef inventory summary visible to a user
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public AppdefInventorySummary getInventorySummary(int sessionId,
                                                      boolean countTypes)
        throws SessionNotFoundException, SessionTimeoutException 
    {
        AuthzSubjectValue who = this.manager.getSubject(sessionId);
        return new AppdefInventorySummary(who, countTypes);
    }

    /**
     * Get a list of appdef properties based on the appdef id
     * @ejb:interface-method
     */
    public Properties getResourceProperties(int sessionId, AppdefEntityID id)
        throws SessionNotFoundException, SessionTimeoutException
    {
        AuthzSubjectValue subject = this.manager.getSubject(sessionId);
        return EntityPropertyFetcher.getProperties(id, subject);
    }

    /**
     * Returns a 2x2 array mapping "appdef type id" to its corresponding
     * label. Suitable for populating an HTML selector.
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public String[][] getAppdefTypeStrArrMap () {
        int[] validTypes = AppdefEntityConstants.getAppdefTypes();
        String[][] retVal = new String[validTypes.length][2];
        for (int i=0;i<validTypes.length;i++) {
            retVal[i][0] = Integer.toString(validTypes[i]);
            retVal[i][1] = AppdefEntityConstants.typeToString(validTypes[i]);
        }
        return retVal;
    }

   /**
    * A method to set ALL the configs of a resource.  This includes the
    * resourceConfig, metricConfig, rtConfig and controlConfig.This also
    * includes the enabling/disabling of rtMetrics for both service and
    * enduser.
    * NOTE: This method should ONLY be called when a user manually configures
    * a resource.
    * @param allConfigs The full configuation information.
    * @param allConfigsRollback The configuation to rollback to if an error 
    *                           occurs.
    * @ejb:interface-method
    * @ejb:transaction type="REQUIRED"
    */
    public void setAllConfigResponses(int sessionInt, 
                                      AllConfigResponses allConfigs,
                                      AllConfigResponses allConfigsRollback )
        throws PermissionException, ConfigFetchException, EncodingException,
               PluginException, ApplicationException, FinderException,
               ScheduleWillNeverFireException, AgentConnectionException,
               AutoinventoryException          
    {
        boolean doRollback = true;
        boolean doValidation = (allConfigsRollback != null);
        AuthzSubjectValue subject = this.manager.getSubject(sessionInt);
        AppdefEntityID id = allConfigs.getResource().getEntityId();

        try {
            doSetAll(subject, sessionInt, allConfigs, doValidation);
            if (doValidation) {
                getConfigManager().clearValidationError(subject, id);
            }
            
            doRollback = false;
            
            // Wait until we have validated the config, send the configs
            AIConversionUtil.sendNewConfigEvent(subject, id, allConfigs);
            
            //run an auto-scan for platforms
            if (id.isPlatform()) {
                getAutoInventoryManager().startScan(subject, id, 
                                                    new ScanConfigurationCore(),
                                                    null, null, null);
            }
        } catch (InvalidConfigException e) {
            //setValidationError for InventoryHelper.isResourceConfigured
            //so this error will be displayed in the UI
            //getConfigManager().setValidationError(subject, id, e.getMessage());
            throw e;
        } finally {
            if (doRollback && doValidation) {
                doSetAll(subject, sessionInt, allConfigsRollback, false);
            }
        }
    }

    private void doSetAll(AuthzSubjectValue subject, int sessionInt,
                          AllConfigResponses allConfigs, boolean doValidation)
        throws EncodingException, FinderException, PermissionException,
               ConfigFetchException, PluginException, ApplicationException
    {
        try {
            AppdefEntityID entityId = allConfigs.getResource().getEntityId();
            ProductBossLocal productBossLocal = getProductBoss();
            AppdefEntityID[] ids;
            Map validationTypes = new HashMap();
            try {
                if (entityId.isPlatform()) {
                    updatePlatform(sessionInt,
                                   (PlatformValue) allConfigs.getResource());
                } else if (entityId.isService()) {
                    updateService(sessionInt,
                                  (ServiceValue) allConfigs.getResource());
                }

                if (allConfigs.shouldConfigProduct()) {
                    validationTypes.put(ProductPlugin.TYPE_CONTROL,
                                        Boolean.TRUE);
                    validationTypes.put(ProductPlugin.TYPE_RESPONSE_TIME,
                                        Boolean.TRUE);
                    validationTypes.put(ProductPlugin.TYPE_MEASUREMENT,
                                        Boolean.TRUE);
                }

                if (allConfigs.shouldConfigMetric()) {
                    validationTypes.put(ProductPlugin.TYPE_MEASUREMENT,
                                        Boolean.TRUE);
                }

                if (allConfigs.shouldConfigRt()) {
                    validationTypes.put(ProductPlugin.TYPE_RESPONSE_TIME,
                                        Boolean.TRUE);
                }

                if (allConfigs.shouldConfigControl()) {
                    validationTypes.put(ProductPlugin.TYPE_CONTROL,
                                        Boolean.TRUE);
                }

                ids = getConfigManager().configureResource(
                    subject, entityId,
                    ConfigResponse.safeEncode(allConfigs.getProductConfig()),
                    ConfigResponse.safeEncode(allConfigs.getMetricConfig()),
                    ConfigResponse.safeEncode(allConfigs.getControlConfig()),
                    ConfigResponse.safeEncode(allConfigs.getRtConfig()),
                    Boolean.TRUE,
                    true);
                
                if (doValidation) {
                    Iterator validations = validationTypes.keySet().iterator();
                    while (validations.hasNext()) {
                        productBossLocal.doValidation(subject, 
                                                      (String) validations.next(),
                                                      ids);
                    }
                }

                if(entityId.isServer() || entityId.isService()) {
                    getAIBoss().toggleRuntimeScan(sessionInt, entityId,
                                                  allConfigs.getEnableRuntimeAIScan());
                }
            } catch (UpdateException e) {
                log.error("Error while updating resource " +
                          allConfigs.getResource().getName());
                throw new ApplicationException(e);
            }
        } catch (NamingException e) {
            throw new SystemException(e);
        }

    }

    /**
     * Get the navigation map data for a given Appdef entity.
     * @return all navigable resources for the given appdef entity
     * @ejb:interface-method
     */
    public ResourceTreeNode[] getNavMapData(int sessionId, AppdefEntityID adeId)
        throws SessionNotFoundException,
               SessionTimeoutException,
               PermissionException,
               AppdefEntityNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefStatManagerLocal local = getAppdefStatManager();
        switch (adeId.getType()) {
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
            return local.getNavMapDataForPlatform(subject, 
                                                  new Integer(adeId.getID()));
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            return local.getNavMapDataForServer(subject, 
                                                new Integer(adeId.getID()));
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            return local.getNavMapDataForService(subject, 
                                                 new Integer(adeId.getID()));
        case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
            return local.getNavMapDataForApplication(subject, 
                                                     new Integer(adeId.getID()));
        case AppdefEntityConstants.APPDEF_TYPE_GROUP:
            return local.getNavMapDataForGroup(subject,
                                               new Integer(adeId.getID()));
        }
        return new ResourceTreeNode[0];
    }

    /**
     * Get the navigation map data for a an auto-group.
     * @param adeIds the appdef entity ids of the "parents" of the groupd children
     * @param ctype the child resource type
     * @return all navigable resources for the given appdef entities and
     * child resource type
     * @ejb:interface-method
     */
    public ResourceTreeNode[] getNavMapData(int sessionId, 
                                            AppdefEntityID[] adeIds, int ctype)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, AppdefEntityNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getAppdefStatManager().getNavMapDataForAutoGroup(subject,adeIds,
                                                                new Integer(ctype));
    }

    /** 
     * temporary method for determining whether or not we're running
     *  a database that supports navmap 
     * @ejb:interface-method
     */
    public boolean isNavMapSupported () {
        return getAppdefStatManager().isNavMapSupported();    
    }

    /** @ejb:create-method */
    public void ejbCreate() throws CreateException {}
    
    public void ejbRemove() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
}
