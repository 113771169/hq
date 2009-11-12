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

package org.hyperic.hq.bizapp.server.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.PatternSyntaxException;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hibernate.PageInfo;
import org.hyperic.hibernate.SortField;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.appdef.ConfigResponseDB;
import org.hyperic.hq.appdef.server.session.AIQueueManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.AppdefManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.AppdefResource;
import org.hyperic.hq.appdef.server.session.AppdefResourceType;
import org.hyperic.hq.appdef.server.session.Application;
import org.hyperic.hq.appdef.server.session.ApplicationType;
import org.hyperic.hq.appdef.server.session.CPropResource;
import org.hyperic.hq.appdef.server.session.CPropResourceSortField;
import org.hyperic.hq.appdef.server.session.Cprop;
import org.hyperic.hq.appdef.server.session.DownResSortField;
import org.hyperic.hq.appdef.server.session.DownResource;
import org.hyperic.hq.appdef.server.session.Platform;
import org.hyperic.hq.appdef.server.session.PlatformType;
import org.hyperic.hq.appdef.server.session.ResourceUpdatedZevent;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.server.session.ServerType;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.appdef.server.session.ServiceType;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQApprovalException;
import org.hyperic.hq.appdef.shared.AIQueueConstants;
import org.hyperic.hq.appdef.shared.AIQueueManagerLocal;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateFQDNException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateNameException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityTypeID;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.AppdefGroupNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.AppdefInventorySummary;
import org.hyperic.hq.appdef.shared.AppdefManagerLocal;
import org.hyperic.hq.appdef.shared.AppdefResourcePermissions;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.appdef.shared.AppdefStatManagerLocal;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.ApplicationManagerLocal;
import org.hyperic.hq.appdef.shared.ApplicationNotFoundException;
import org.hyperic.hq.appdef.shared.ApplicationValue;
import org.hyperic.hq.appdef.shared.CPropKeyNotFoundException;
import org.hyperic.hq.appdef.shared.CPropManager;
import org.hyperic.hq.appdef.shared.ConfigFetchException;
import org.hyperic.hq.appdef.shared.DependencyTree;
import org.hyperic.hq.appdef.shared.GroupTypeValue;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.InvalidConfigException;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformValue;
import org.hyperic.hq.appdef.shared.ResourcesCleanupZevent;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.appdef.shared.ServerValue;
import org.hyperic.hq.appdef.shared.ServiceClusterValue;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilter;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterAssignSvc;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterExclude;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterGroupEntityResource;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilterGroupMemExclude;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.auth.shared.SessionException;
import org.hyperic.hq.auth.shared.SessionManager;
import org.hyperic.hq.auth.shared.SessionNotFoundException;
import org.hyperic.hq.auth.shared.SessionTimeoutException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.authz.server.session.ResourceGroupManagerImpl;
import org.hyperic.hq.authz.server.session.ResourceGroupSortField;
import org.hyperic.hq.authz.server.session.ResourceManagerImpl;
import org.hyperic.hq.authz.server.session.ResourceGroup.ResourceGroupCreateInfo;
import org.hyperic.hq.authz.server.shared.ResourceDeletedException;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.GroupCreationException;
import org.hyperic.hq.authz.shared.MixedGroupType;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceGroupManager;
import org.hyperic.hq.authz.shared.ResourceManager;
import org.hyperic.hq.autoinventory.AutoinventoryException;
import org.hyperic.hq.autoinventory.ScanConfigurationCore;
import org.hyperic.hq.bizapp.shared.AIBossLocal;
import org.hyperic.hq.bizapp.shared.AllConfigResponses;
import org.hyperic.hq.bizapp.shared.AppdefBossLocal;
import org.hyperic.hq.bizapp.shared.AppdefBossUtil;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceTreeNode;
import org.hyperic.hq.bizapp.shared.uibeans.SearchResult;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.VetoException;
import org.hyperic.hq.common.shared.ProductProperties;
import org.hyperic.hq.events.MaintenanceEvent;
import org.hyperic.hq.events.server.session.EventLog;
import org.hyperic.hq.events.server.session.EventLogManagerEJBImpl;
import org.hyperic.hq.events.shared.EventLogManagerLocal;
import org.hyperic.hq.grouping.Critter;
import org.hyperic.hq.grouping.CritterList;
import org.hyperic.hq.grouping.CritterTranslationContext;
import org.hyperic.hq.grouping.CritterTranslator;
import org.hyperic.hq.grouping.critters.AvailabilityCritterType;
import org.hyperic.hq.grouping.critters.CompatGroupTypeCritterType;
import org.hyperic.hq.grouping.critters.GroupMembershipCritterType;
import org.hyperic.hq.grouping.critters.MixedGroupTypeCritterType;
import org.hyperic.hq.grouping.critters.OwnedCritterType;
import org.hyperic.hq.grouping.critters.ProtoCritterType;
import org.hyperic.hq.grouping.critters.ResourceNameCritterType;
import org.hyperic.hq.grouping.critters.ResourceTypeCritterType;
import org.hyperic.hq.grouping.shared.GroupDuplicateNameException;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.ext.DownMetricValue;
import org.hyperic.hq.measurement.server.session.Measurement;
import org.hyperic.hq.measurement.server.session.MeasurementTemplate;
import org.hyperic.hq.measurement.shared.AvailabilityType;
import org.hyperic.hq.measurement.shared.MeasurementManager;
import org.hyperic.hq.measurement.shared.TrackerManager;
import org.hyperic.hq.product.MetricValue;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.scheduler.ScheduleWillNeverFireException;
import org.hyperic.hq.zevents.ZeventListener;
import org.hyperic.hq.zevents.ZeventManager;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.EncodingException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;
import org.hyperic.util.timer.StopWatch;

import org.quartz.SchedulerException;

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
    private final String APPDEF_PAGER_PROCESSOR =
        "org.hyperic.hq.appdef.shared.pager.AppdefPagerProc";

    private SessionManager manager = SessionManager.getInstance();

    protected Log log = LogFactory.getLog(AppdefBossEJBImpl.class.getName());
    protected boolean debug = log.isDebugEnabled();
    protected final int APPDEF_TYPE_UNDEFINED     = -1;
    protected final int APPDEF_RES_TYPE_UNDEFINED = -1;
    protected final int APPDEF_GROUP_TYPE_UNDEFINED = -1;

    public AppdefBossEJBImpl() {}

    private AppdefManagerLocal getAppdefManager() {
        return AppdefManagerEJBImpl.getOne();
    }

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
    public AppdefResourceType findCommonResourceType(int sessionID,
                                                          String[] aeids)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionNotFoundException, SessionTimeoutException {
        AuthzSubject subject = manager.getSubject(sessionID);
        if (aeids == null || aeids.length == 0)
            return null;
        
        // Take the resource type of the first entity
        AppdefEntityID aeid = new AppdefEntityID(aeids[0]);
        int resType = aeid.getType();
        
        AppdefResourceType retArt = null;
        // Now let's go through and make sure they're of the same type
        for (int i = 0; i < aeids.length; i++) {
            aeid = new AppdefEntityID(aeids[i]);
            // First check to make sure they are same resource type
            if (aeid.getType() != resType)
                return null;
            
            // Now get the appdef resource type value
            AppdefEntityValue arv = new AppdefEntityValue(aeid, subject);
            try {
                AppdefResourceType art = arv.getAppdefResourceType();
                
                if (retArt == null) {
                    retArt = art;
                }
                else if (!art.equals(retArt)) {
                    return null;
                }
            } catch (IllegalStateException e) {
                // Mixed group
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
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException {

        AuthzSubject subject = manager.getSubject(sessionID);
        PageList platTypeList =
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
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, FinderException {
        AuthzSubject subject = manager.getSubject(sessionID);
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

        AuthzSubject subject = manager.getSubject(sessionID);
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
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServerManager().getViewableServerTypes(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public List findAllApplicationTypes(int sessionID)
        throws ApplicationException {
        AuthzSubject subject = manager.getSubject(sessionID);
        try {
            return getApplicationManager().getAllApplicationTypes(subject);
        } catch (FinderException e) {
            throw new SystemException(e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public ApplicationType findApplicationTypeById(int sessionId,
                                                        Integer id)
        throws ApplicationException 
    {
        manager.authenticate(sessionId);
        return getApplicationManager().findApplicationType(id);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findAllServiceTypes(int sessionID, PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException {
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServiceManager().getAllServiceTypes(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findViewableServiceTypes(int sessionID,
                                    PageControl pc)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {

        AuthzSubject subject = manager.getSubject(sessionID);
        return getServiceManager().getViewableServiceTypes(subject, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findViewablePlatformServiceTypes(int sessionID,
                                                     Integer platId)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException {
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServiceManager()
            .findVirtualServiceTypesByPlatform(subject, platId);
    }

    /**
     * @ejb:interface-method
     */
    public ApplicationValue findApplicationById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {

        AuthzSubject subject = manager.getSubject(sessionID);
        return getApplicationManager().findApplicationById(subject, id)
            .getApplicationValue();
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
        throws SessionException, PermissionException, 
               AppdefEntityNotFoundException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
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
        AuthzSubject subject = manager.getSubject(sessionID);
        return getApplicationManager().getApplicationsByResource(subject, id,
                                                                 pc);
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
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServiceManager().getPlatformServices(subject, platformId, pc);
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
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServiceManager().getPlatformServices(subject, platformId,
                                                       typeId, pc);
    }

    /**
     * Find service inventory by application - including services and clusters
     * @ejb:interface-method
     */
    public PageList findServiceInventoryByApplication(int sessionID,
                                                      Integer appId,
                                                      PageControl pc)
        throws AppdefEntityNotFoundException, SessionException,
               PermissionException
    {
        AppdefEntityID aeid = AppdefEntityID.newAppID(appId);
                               
        return findServices(sessionID, aeid, true, pc);
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
               SessionException
    {
        AppdefEntityID aeid = AppdefEntityID.newServerID(serverId);
        return findServices(sessionID, aeid, false, pc);
    }

    private PageList findServices(int sessionID, AppdefEntityID aeid,
                                  boolean allServiceInventory, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionException
    {
        PageList res = null;

        if (pc == null)
            pc = PageControl.PAGE_ALL;

        // Get the AuthzSubject for the user's session
        AuthzSubject subject = manager.getSubject(sessionID);

        AppdefEntityValue aeval = new AppdefEntityValue(aeid, subject);
        switch  (aeid.getType()) {
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            res = aeval.getAssociatedServices(pc);
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
        default:
            log.error("Invalid type given to find services.");
        }
        return res;
    }

    /**
     * Find the platform by service.
     * @ejb:interface-method
     */
    public PlatformValue findPlatformByDependentID(int sessionID,
                                                   AppdefEntityID entityId)
        throws AppdefEntityNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        Integer id = entityId.getId();
        switch(entityId.getType()){
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
            return findPlatformById(sessionID, id);
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
            findServers(sessionID,
                        AppdefEntityID.newServiceID(serviceID), null).get(0);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServersByTypeAndPlatform(int sessionId,
                                                 Integer platformId,
                                                 int adResTypeId,
                                                 PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException
    {
        return findServers(sessionId, AppdefEntityID.newPlatformID(platformId),
                           adResTypeId, pc);
    }

    /**
     * Get the virtual server for a given platform and service type
     * 
     * @ejb:interface-method
     */
    public ServerValue findVirtualServerByPlatformServiceType(int sessionID,
                                                              Integer platId,
                                                              Integer svcTypeId)
        throws ServerNotFoundException, PlatformNotFoundException,
               PermissionException, SessionNotFoundException,
               SessionTimeoutException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
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
        throws AppdefEntityNotFoundException, SessionTimeoutException, 
               SessionNotFoundException, PermissionException 
    {
        return findServers(sessionID, AppdefEntityID.newPlatformID(platformId),
                           pc);
    }

    /**
     * Get the virtual servers for a given platform
     * 
     * @ejb:interface-method
     */
    public PageList findViewableServersByPlatform(int sessionID,
                                                 Integer platformId,
                                                 PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServerManager().getServersByPlatform(subject, platformId,
                                                       true, pc);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServerTypesByPlatform (int sessionID,
                                               Integer platformId,
                                               PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException
    {
        AuthzSubject subject = manager.getSubject(sessionID);
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
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServerManager().getServerTypesByPlatformType(subject,
                                                               platformId,
                                                               pc);
    }

    private PageList findServers(int sessionID, AppdefEntityID aeid,
                                 PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException
    {
      return findServers(sessionID, aeid, APPDEF_RES_TYPE_UNDEFINED, pc);
    }

    private PageList findServers(int sessionID, AppdefEntityID aeid,
                                 int servTypeId, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException
    {
        ServerManagerLocal serverMan = getServerManager();
        PageList res;

        // Get the AuthzSubject for the user's session
        AuthzSubject subject = manager.getSubject(sessionID);

        switch (aeid.getType()) {
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM :
            if (servTypeId == APPDEF_RES_TYPE_UNDEFINED) {
                res = serverMan.getServersByPlatform(subject, aeid.getId(), 
                                                     false, pc);
            } else {
                // exclude virtual servers
                res = serverMan.getServersByPlatform(subject, aeid.getId(), 
                                                     new Integer(servTypeId),
                                                     true, pc);
            }
            break;
        case AppdefEntityConstants.APPDEF_TYPE_APPLICATION :
            res = serverMan.getServersByApplication(subject, aeid.getId(), pc);
            break;
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE :
            ServerValue val;
            val = serverMan.getServerByService(subject, aeid.getId());
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
        AuthzSubject subject = manager.getSubject(sessionID);
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
        AuthzSubject subject = manager.getSubject(sessionID);
        return getPlatformManager().getRecentPlatforms(subject, range, size);
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
        AuthzSubject subject = manager.getSubject(sessionId);
        List appdefList = new ArrayList();

        // cheaper to find the resource first
        ResourceManager resMan = getResourceManager();
        for (int i = 0; i < entities.length; i++) {
            if (pc != null) {
                Resource res = resMan.findResource(entities[i]);
                if (res != null && !res.isInAsyncDeleteState())
                    appdefList.add(res);
            }
            else {
                try {
                    AppdefResourceValue res = findById(subject, entities[i]);
                    if (res != null)
                        appdefList.add(res);
                } catch (AppdefEntityNotFoundException e) {
                    log.debug("Entity not found: " + entities[i]);
                }
            }
        }
        
        if (pc == null) {
            return new PageList(appdefList, appdefList.size());
        }
        
        Collections.sort(appdefList);            
        if (pc.getSortorder() == PageControl.SORT_DESC)
            Collections.reverse(appdefList);
        
        PageList pl = Pager.getDefaultPager().seek(appdefList, pc);
        
        // Replace the list objects with AppdefResourceValue
        appdefList.clear();
        for (Iterator it = pl.iterator(); it.hasNext(); ) {
            Resource res = (Resource) it.next();
            try {
                appdefList.add(findById(subject, new AppdefEntityID(res)));
            } catch (AppdefEntityNotFoundException e) {
                log.error("Resource not found in Appdef: " + res.getId());
            }
        }
        
        return new PageList(appdefList, pl.getTotalSize());
    }

    /**
     * Looks up and returns a value object corresponding to the appdef entity
     * represented by the instance id passed in. The method does not require
     * the caller to know the instance-id's corresponding type. Similarly,
     * the return value is upcasted.
     * @ejb:interface-method
     * */
    public AppdefResourceValue findById(int sessionId, AppdefEntityID entityId)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        // get the user
        AuthzSubject subject = manager.getSubject(sessionId);
        return findById(subject, entityId);
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
    private AppdefResourceValue findById(AuthzSubject subject,
                                         AppdefEntityID entityId)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionTimeoutException, SessionNotFoundException 
    {
        AppdefEntityValue aeval = new AppdefEntityValue(entityId, subject);
        AppdefResourceValue retVal = aeval.getResourceValue();
        
        if (retVal == null) {
            throw new IllegalArgumentException(entityId.getType()
                    + " is not a valid appdef entity type");
        }
        
        if (entityId.isServer()) {
            ServerValue server = (ServerValue) retVal;
            retVal.setHostName(server.getPlatform().getName());
        }
        else if (entityId.isService()) {
            ServiceValue service = (ServiceValue) retVal;
            retVal.setHostName(service.getServer().getName());
        }

        return retVal;
    }

    /**
     * @ejb:interface-method
     */
    public PlatformValue findPlatformById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        return (PlatformValue) findById(subject,
                                        AppdefEntityID.newPlatformID(id));
    }

    /**
     * @ejb:interface-method
     */
    public Agent findResourceAgent(AppdefEntityID entityId)
        throws AppdefEntityNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AgentNotFoundException 
    {
        return getAgentManager().getAgent(entityId);
    }

    /**
     * @ejb:interface-method
     */
    public ServerValue findServerById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        return (ServerValue) findById(subject, AppdefEntityID.newServerID(id));
    }

    /**
     * @ejb:interface-method
     */
    public ServiceValue findServiceById(int sessionID, Integer id)
        throws AppdefEntityNotFoundException, SessionTimeoutException,
               SessionNotFoundException, PermissionException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        return (ServiceValue) findById(subject, AppdefEntityID.newServiceID(id));
    }
    
    /**
     * @return A PageList of all registered appdef resource types
     * as well as the three group specific resource types.
     * @ejb:interface-method
     */
     public PageList findAllResourceTypes (int sessionId, PageControl pc )
         throws SessionTimeoutException, SessionNotFoundException,
                PermissionException {
         return findAllResourceTypes (sessionId, APPDEF_TYPE_UNDEFINED, pc);
     }
    
    /**
     * @return A PageList of all registered appdef resource types
     * of a particular entity type.
     * @ejb:interface-method
     */
    public PageList findAllResourceTypes(int sessionId, int entType,
                                         PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
                PermissionException 
    {
        List toBePaged;
        Pager defaultPager;
        AuthzSubject subject = manager.getSubject(sessionId);
        
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
     */
    public Platform createPlatform(int sessionID,
                                        PlatformValue platformVal,
                                        Integer platTypePK,
                                        Integer agent)
        throws CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefDuplicateNameException ,
               AppdefDuplicateFQDNException, ApplicationException
    {
        try {
            // Get the AuthzSubject for the user's session
            AuthzSubject subject = manager.getSubject(sessionID);
            Platform platform =
                getPlatformManager().createPlatform(subject, platTypePK,
                                                    platformVal, agent);
            return platform;
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
        try {
            AppdefResourceType type = null;
            switch(id.getType()) {
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                    type = findPlatformTypeById(sessionID, id.getId());
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                    type = findServerTypeById(sessionID, id.getId());
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                    type = findServiceTypeById(sessionID, id.getId());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown appdef type: "
                                                       + id);
            }
            return type.getAppdefResourceTypeValue();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }                      
    
    /**
     * @ejb:interface-method
     */
    public PlatformType findPlatformTypeById(int sessionID, Integer id)
        throws PlatformNotFoundException,
               SessionTimeoutException, SessionNotFoundException
    {
        manager.authenticate(sessionID);
        return getPlatformManager().findPlatformType(id);
    }

    /**
     * @ejb:interface-method
     */
    public PlatformType findPlatformTypeByName(int sessionID, String name)
        throws PlatformNotFoundException,
               SessionTimeoutException, SessionNotFoundException 
    {
        manager.authenticate(sessionID);
        return getPlatformManager().findPlatformTypeByName(name);
    }

    /**
     * @ejb:interface-method
     */
    public ServiceType findServiceTypeById(int sessionID, Integer id)
        throws SessionTimeoutException, SessionNotFoundException 
    {
        manager.authenticate(sessionID);
        return getServiceManager().findServiceType(id);
    }

    /**
     * @ejb:interface-method
     */
    public PageList findServiceTypesByServerType(int sessionID, 
                                                 int serverTypeId)
        throws SessionTimeoutException, SessionNotFoundException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        return getServiceManager().getServiceTypesByServerType(subject, 
                                                               serverTypeId);
    }
    
    /**
     * @ejb:interface-method
     */
    public ServerType findServerTypeById(int sessionID,  Integer id)
        throws SessionTimeoutException, SessionNotFoundException 
    {
        manager.authenticate(sessionID);
        return getServerManager().findServerType(id);
    }

    /**
     * Private method to call the setCPropValue from a Map
     * @param subject  Subject setting the values
     * @param cProps A map of String key/value pairs to set
     */
    private void setCPropValues(AuthzSubject subject,
                                AppdefEntityID entityId, Map cProps)
        throws SessionNotFoundException, SessionTimeoutException,
               CPropKeyNotFoundException, AppdefEntityNotFoundException,
               PermissionException
    {
        CPropManager cpropMan;
        AppdefEntityValue aVal;

        cpropMan = getCPropManager();
        aVal = new AppdefEntityValue(entityId, subject);
        int typeId = aVal.getAppdefResourceType().getId().intValue();
        for (Iterator i = cProps.keySet().iterator(); i.hasNext(); ) {
            String key = (String)i.next();

            cpropMan.setValue(entityId, typeId, key, (String)cProps.get(key));
        }
    }

    /**
     * Create a server with CProps
     * @param platformPK - the pk of the host platform
     * @param serverTypePK - the type of server
     * @param cProps - the map with Custom Properties for the server
     * @return ServerValue - the saved server
     * @ejb:interface-method
     */
    public ServerValue createServer(int sessionID, ServerValue serverVal,
                                    Integer platformPK, Integer serverTypePK,
                                    Map cProps)
        throws CreateException, ValidationException, SessionTimeoutException,
               SessionNotFoundException, PermissionException,
               AppdefDuplicateNameException, CPropKeyNotFoundException
    {
        try {
            // Get the AuthzSubject for the user's session
            AuthzSubject subject = manager.getSubject(sessionID);

            // Call into appdef to create the platform.
            ServerManagerLocal serverMan = getServerManager();
            Server server = serverMan.createServer(subject, platformPK,
                                                 serverTypePK, serverVal);
            if (cProps != null) {
                AppdefEntityID entityId = server.getEntityId();
                setCPropValues(subject, entityId, cProps);
            }

            return server.getServerValue();
        } catch (AppdefEntityNotFoundException e) {
            log.error("Unable to create server.", e);
            throw new SystemException("Unable to find new server");
        }
    }

    /**
     * Create an application
     * @return ApplicationValue - the saved application
     * @ejb:interface-method
     */
    public ApplicationValue createApplication(int sessionID,
                                              ApplicationValue appVal,
                                              Collection services,
                                              ConfigResponse protoProps)
        throws CreateException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefDuplicateNameException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        ApplicationManagerLocal appMan = getApplicationManager();
            
        Application pk = appMan.createApplication(subject, appVal, services);
        return pk.getApplicationValue();
    }

    /**
     * @param serviceTypePK - the type of service
     * @param aeid - the appdef entity ID
     * @return ServiceValue - the saved ServiceValue
     * @ejb:interface-method
     */
    public ServiceValue createService(int sessionID, ServiceValue serviceVal,
                                      Integer serviceTypePK,
                                      AppdefEntityID aeid)
        throws SessionNotFoundException, SessionTimeoutException,
               ServerNotFoundException, PlatformNotFoundException,
               PermissionException, AppdefDuplicateNameException,
               ValidationException, CreateException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        try {
            Integer serverPK;
            if (aeid.isPlatform()) {
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
            Service newSvc = createService(subject, serviceVal, serviceTypePK,
                                           serverPK, null);
            return newSvc.getServiceValue();
        } catch(CPropKeyNotFoundException exc){
            log.error("Error setting no properties for new service");
            throw new SystemException("Error setting no properties.", exc);
        }
    }

    /**
     * Create a service with CProps
     * 
     * @param serviceTypePK - the type of service
     * @param serverPK - the server host
     * @param cProps - the map with Custom Properties for the service
     * @return Service - the saved Service
     * @ejb:interface-method
     */
    public Service createService(AuthzSubject subject, ServiceValue serviceVal,
                                 Integer serviceTypePK, Integer serverPK,
                                 Map cProps)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefDuplicateNameException, ValidationException,
               PermissionException, CreateException, CPropKeyNotFoundException
    {
        try {
            ServiceManagerLocal svcMan = getServiceManager();
            Service savedService =
                svcMan.createService(subject, serverPK,
                                     serviceTypePK,
                                     serviceVal.getName(),
                                     serviceVal.getDescription(),
                                     serviceVal.getLocation());
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
     * Removes an appdef entity by nulling out any reference from its children
     * and then deleting it synchronously.  The children are then cleaned up
     * in the zevent queue by issuing a {@link ResourcesCleanupZevent}
     * @return AppdefEntityID[] - an array of the resources (including children)
     * deleted
     * @ejb:interface-method
     */
    public AppdefEntityID[] removeAppdefEntity(int sessionId, AppdefEntityID aeid)
        throws SessionNotFoundException, SessionTimeoutException,
               ApplicationException, VetoException {
        final StopWatch timer = new StopWatch();
        final ResourceManager resMan = getResourceManager();
        final AuthzSubject subject = manager.getSubject(sessionId);
        final Resource res = resMan.findResource(aeid);
        
        if (aeid.isGroup()) {
            // HQ-1577: Do not delete group if downtime schedule exists
            try {
                MaintenanceEvent event = 
                    getEventsBoss().getMaintenanceEvent(sessionId, aeid.getId());
                
                if (event != null && event.getStartTime() != 0) {
                    throw new VetoException("Could not remove resource " + aeid +
                                            " because a downtime schedule exists.");
                }
            } catch (SchedulerException se) {
                throw new ApplicationException(se);
            }
        }
        if (res == null) {
            log.warn("AppdefEntityId=" + aeid +
                " is not associated with a Resource");
            return new AppdefEntityID[0];
        }
        AppdefEntityID[] removed = resMan.removeResourcePerms(
            subject, res, false);
        try {
            final Integer id = aeid.getId();
            switch (aeid.getType()) {
                case AppdefEntityConstants.APPDEF_TYPE_SERVER :
                    final ServerManagerLocal sMan = getServerManager();
                    sMan.removeServer(subject, sMan.findServerById(id));
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                    final PlatformManagerLocal pMan = getPlatformManager();
                    removePlatform(subject, pMan.findPlatformById(id));
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE :
                    final ServiceManagerLocal svcMan = getServiceManager();
                    svcMan.removeService(subject, svcMan.findServiceById(id));
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_GROUP:
                    final ResourceGroupManager rgMan =
                        getResourceGroupManager();
                    rgMan.removeResourceGroup(
                        subject, rgMan.findResourceGroupById(id));
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                    final ApplicationManagerLocal aMan = getApplicationManager();
                    aMan.removeApplication(subject, id);
                    break;
                default:
                    break;
            }
        } catch (RemoveException e) {
            throw new ApplicationException(e);
        }
        if (log.isDebugEnabled()) {
            log.debug("removeAppdefEntity() for " + aeid + " executed in " +
                      timer.getElapsed());
        }
        
        ZeventManager.getInstance().enqueueEventAfterCommit(
            new ResourcesCleanupZevent());
        
        return removed;
    }
    
    //TODO modify javadoc comment below regarding NotSupported
    /**
     * Remove all delete resources
     * Method was "NotSupported" since all the resource deletes may take longer
     * than the jboss transaction timeout.  No need for a transaction in this
     * context.
     * @ejb:interface-method
     */
    public void removeDeletedResources()
        throws ApplicationException, VetoException, RemoveException {
        final StopWatch watch = new StopWatch();
        final AuthzSubject subject =
            getAuthzSubjectManager().findSubjectById(AuthzConstants.overlordId);
        
        watch.markTimeBegin("removeApplications");
        Collection applications =
            getApplicationManager().findDeletedApplications();
        for (Iterator it = applications.iterator(); it.hasNext(); ) {
            try {
                getOne()._removeApplicationInNewTran(
                    subject, (Application)it.next());
            } catch (Exception e) {
                log.error("Unable to remove application: " + e, e);
            }
        }
        watch.markTimeEnd("removeApplications");
        if (log.isDebugEnabled()) {
            log.debug("Removed " + applications.size() + " applications");
        }

        watch.markTimeBegin("removeResourceGroups");
        Collection groups = getResourceGroupManager().findDeletedGroups();
        for (Iterator it = groups.iterator(); it.hasNext(); ) {
            try {
                getOne()._removeGroupInNewTran(subject, (ResourceGroup)it.next());
            } catch (Exception e) {
                log.error("Unable to remove group: " + e, e);
            }
        }
        watch.markTimeEnd("removeResourceGroups");
        if (log.isDebugEnabled()) {
            log.debug("Removed " + groups.size() + " resource groups");
        }

        // Look through services, servers, platforms, applications, and groups
        Collection services = getServiceManager().findDeletedServices();
        removeServices(subject, services);

        Collection servers = getServerManager().findDeletedServers();
        removeServers(subject, servers);

        watch.markTimeBegin("removePlatforms");
        Collection platforms = getPlatformManager().findDeletedPlatforms();
        for (final Iterator it = platforms.iterator(); it.hasNext(); ) {
            final Platform platform = (Platform)it.next();
            try {
                removeServers(subject, platform.getServers());
                getOne()._removePlatformInNewTran(subject, platform);
            } catch (Exception e) {
                log.error("Unable to remove platform: " + e, e);
            }
        }
        watch.markTimeEnd("removePlatforms");
        if (log.isDebugEnabled()) {
            log.debug("Removed " + platforms.size() + " platforms");
            log.debug("removeDeletedResources() timing: " + watch);
        }
    }

    private final void removeServers(AuthzSubject subject, Collection servers) {
        final StopWatch watch = new StopWatch();
        watch.markTimeBegin("removeServers");
        final List svrs = new ArrayList(servers);
        // can't use iterator for loop here.  Since we are modifying the
        // internal hibernate collection, which this collection is based on,
        // it will throw a ConcurrentModificationException
        // This occurs even if you disassociate the Collection by trying
        // something like new ArrayList(servers).  Not sure why.
        for (int i=0; i<svrs.size(); i++) {
            try {
                final Server server = (Server)svrs.get(i);
                removeServices(subject, server.getServices());
                getOne()._removeServerInNewTran(subject, server);
            } catch (Exception e) {
                log.error("Unable to remove server: " + e, e);
            }
        }
        watch.markTimeEnd("removeServers");
        if (log.isDebugEnabled()) {
            log.debug("Removed " + servers.size() + " services");
        }
    }

    private final void removeServices(AuthzSubject subject, Collection services) {
        final StopWatch watch = new StopWatch();
        watch.markTimeBegin("removeServices");
        final List svcs = new ArrayList(services);
        // can't use iterator for loop here.  Since we are modifying the
        // internal hibernate collection, which this collection is based on,
        // it will throw a ConcurrentModificationException
        // This occurs even if you disassociate the Collection by trying
        // something like new ArrayList(services).  Not sure why.
        for (int i=0; i<svcs.size(); i++) {
            try {
                final Service service = (Service)svcs.get(i);
                getOne()._removeServiceInNewTran(subject, service);
            } catch (Exception e) {
                log.error("Unable to remove service: " + e, e);
            }
        }
        watch.markTimeEnd("removeServices");
        if (log.isDebugEnabled()) {
            log.debug("Removed " + services.size() + " services");
        }
    }

    /**
     * Disable all measurements for a resource
     * @param id the resource's ID
     */
    private void disableMeasurements(AuthzSubject subject, Resource res)
        throws PermissionException
    {
        getMetricManager().disableMeasurements(subject, res);
    }

    /**
     * Remove config and track plugins for a given resource
     */
    private void removeTrackers(AuthzSubject subject, AppdefEntityID id)
        throws PermissionException
    {
        TrackerManager trackManager = getTrackerManager();
        ConfigResponse response;
    
        try {
            response = getConfigManager().
                getMergedConfigResponse(subject,
                                        ProductPlugin.TYPE_MEASUREMENT,
                                        id, true);
        } catch (Exception e) {
            // If anything goes wrong getting the config, just move
            // along.  The plugins will be removed on the next agent
            // restart.
            return;
        }
    
        try {
            trackManager.disableTrackers(subject, id, response);
        } catch (PluginException e) {
            // Not much we can do.. plugins will be removed on next
            // agent restart.
            log.error("Unable to remove track plugins", e);
        }
    }

    /**
     * 
     * @ejb:interface-method
     */
    public void _removePlatformInNewTran(AuthzSubject subject, Platform platform)
        throws ApplicationException, VetoException {
        removePlatform(subject, platform);
    }

    /**
     * @ejb:interface-method
     */
    public void removePlatform(AuthzSubject subject, Platform platform)
        throws ApplicationException, VetoException 
    {
        try {
            // Disable all measurements for this platform.  We don't actually
            // remove the measurements here to avoid delays in deleting
            // resources.
            disableMeasurements(subject, platform.getResource());

            // Remove from AI queue
            try {
                List aiplatformList = new ArrayList();
                Integer platformID = platform.getId();
                final AIQueueManagerLocal aiMan = getAIManager();
                AIPlatformValue aiPlatform =
                    aiMan.getAIPlatformByPlatformID(subject, platformID);

                if (aiPlatform != null) {
                    aiplatformList.add(aiPlatform.getId());
                    log.info("Removing from AIqueue: " + aiPlatform.getId());
                    aiMan.processQueue(subject, aiplatformList, null, null,
                                       AIQueueConstants.Q_DECISION_PURGE);
                }                
            } catch (AIQApprovalException e) {
                log.error("Error removing from AI queue", e);
            } catch (FinderException e) {
                log.debug("AIPlatform resources not found: " + platform.getId());
            }

            // now, remove the platform.
            getPlatformManager().removePlatform(subject, platform);
        } catch (RemoveException e) {
            log.error("Caught EJB RemoveException",e);
            throw new SystemException(e);
        } catch (PermissionException e) {
            log.error("Caught PermissionException while removing platform: " +
                      platform.getId(),e);
            throw e;
        }
    }

    private void removeServer(AuthzSubject subject, Server server)
        throws PermissionException,
               VetoException {
        try {
            // now remove the measurements
            disableMeasurements(subject, server.getResource());
            try {
                getAutoInventoryManager().toggleRuntimeScan(
                    getOverlord(), server.getEntityId(), false);
            } catch (ResourceDeletedException e) {
                log.debug(e);
            } catch (Exception e) {
                log.error("Error turning off RuntimeScan for: " + server, e);
            }
            // finally, remove the server
            getServerManager().removeServer(subject, server);
        } catch (RemoveException e) {
            rollback();
            throw new SystemException(e);
        } catch (PermissionException e) {
            rollback();
            log.error("Caught permission exception: [server:" + server.getId()
                    + "]");
            throw (PermissionException) e;
        }
    }

    /**
     * 
     * @ejb:interface-method
     */
    public void _removeServerInNewTran(AuthzSubject subject, Server server)
        throws VetoException,
               PermissionException {
        removeServer(subject, server);
    }

    /**
     * 
     * @ejb:interface-method
     */
    public void _removeServiceInNewTran(AuthzSubject subject, Service service)
        throws VetoException, PermissionException, RemoveException 
    {
        try {    
            // now remove any measurements associated with the service
            disableMeasurements(subject, service.getResource());
            removeTrackers(subject, service.getEntityId());
            getServiceManager().removeService(subject, service);
        } catch (PermissionException e) {
            rollback();
            throw (PermissionException) e;
        } catch (RemoveException e) {
            rollback();
            throw (RemoveException) e;
        }
    }

    /**
     * 
     * @ejb:interface-method
     */
    public void _removeGroupInNewTran(AuthzSubject subject, ResourceGroup group)
        throws SessionException, PermissionException, VetoException
    {
        getResourceGroupManager().removeResourceGroup(subject, group);
    }

    /**
     * 
     * @ejb:interface-method
     */
    public void _removeApplicationInNewTran(AuthzSubject subject, Application app)
        throws ApplicationException,
               PermissionException,
               SessionException,
               VetoException {
        try {
            getApplicationManager().removeApplication(subject, app.getId());
        } catch (PermissionException e) {
            rollback();
            throw e;
        } catch (RemoveException e) {
            rollback();
            throw new ApplicationException(e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public ServerValue updateServer(int sessionId, ServerValue aServer)
        throws PermissionException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               FinderException, UpdateException, AppdefDuplicateNameException
    {
        try {
            return updateServer(sessionId, aServer, null);
        } catch(CPropKeyNotFoundException exc){
            log.error("Error updating no properties for server");
            throw new SystemException("Error updating no properties.", exc);
        }
    }

    /**
     * Update a server with cprops.
     * @param cProps - the map with Custom Properties for the server
     * @ejb:interface-method
     */
    public ServerValue updateServer(int sessionId, ServerValue aServer,
                                    Map cProps)
        throws FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException, CPropKeyNotFoundException
    {
        try {
            try {
                AuthzSubject subject = manager.getSubject(sessionId);

                Server updated =
                    getServerManager().updateServer(subject, aServer);

                if(cProps != null ) {
                    AppdefEntityID entityId = aServer.getEntityId();
                    setCPropValues(subject, entityId, cProps);
                }
                return updated.getServerValue();
            } catch (Exception e) {
                log.error("Error updating server: " + aServer.getId());
                rollback();
                throw e;
            }
        } catch (CreateException e) {
            // change to a update exception as this only occurs
            // if there was a failure instantiating the session
            // bean
            throw new UpdateException("Error creating manager session bean: " +
                                      e.getMessage());
        } catch (PermissionException e) {
            throw (PermissionException) e;
        } catch (FinderException e) {
            throw (FinderException) e;
        } catch (AppdefDuplicateNameException e) {
            throw (AppdefDuplicateNameException) e;
        } catch (CPropKeyNotFoundException e) {
            throw (CPropKeyNotFoundException) e;
        } catch (AppdefEntityNotFoundException e) {
            throw new SystemException("Unable to find updated server");
        } catch (Exception e) {
            throw new UpdateException("Unknown error updating server: "
                                      + aServer.getId(), e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public ServiceValue updateService(int sessionId, ServiceValue aService)
        throws PermissionException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               FinderException, UpdateException, AppdefDuplicateNameException
    {
        try {
            return updateService(sessionId, aService, null);
        } catch(CPropKeyNotFoundException exc){
            log.error("Error updating no properties for service");
            throw new SystemException("Error updating no properties.", exc);
        }
    }

    /**
     * Update a service with cProps.
     * @param cProps - the map with Custom Properties for the service
     * @ejb:interface-method
     */
    public ServiceValue updateService(int sessionId, ServiceValue aService,
                                      Map cProps)
        throws FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException, CPropKeyNotFoundException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        return updateService(subject, aService, cProps);
    }
    
    /**
     * Update a service with cProps.
     * @param cProps - the map with Custom Properties for the service
     * @ejb:interface-method
     */
    public ServiceValue updateService(AuthzSubject subject, ServiceValue aService,
                                      Map cProps)
        throws FinderException, ValidationException,
               SessionTimeoutException, SessionNotFoundException,
               PermissionException, UpdateException,
               AppdefDuplicateNameException, CPropKeyNotFoundException
    {
        try {
            Service updated = getServiceManager().updateService(subject,
                                                                aService);

            if (cProps != null) {
                AppdefEntityID entityId = aService.getEntityId();
                setCPropValues(subject, entityId, cProps);
            }
            return updated.getServiceValue();
        } catch (Exception e) {
            log.error("Error updating service: " + aService.getId());
            rollback();
            if (e instanceof CreateException) {
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
     */
    public PlatformValue updatePlatform(int sessionId, PlatformValue aPlatform)
        throws FinderException, ValidationException, PermissionException, 
               SessionTimeoutException, SessionNotFoundException,
               UpdateException, ApplicationException,
               AppdefDuplicateNameException, AppdefDuplicateFQDNException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        return updatePlatform(subject, aPlatform);
    }
    
    /**
     * @ejb:interface-method
     */
    public PlatformValue updatePlatform(AuthzSubject subject,
                                   PlatformValue aPlatform)
        throws FinderException, ValidationException, PermissionException, 
               SessionTimeoutException, SessionNotFoundException,
               UpdateException, ApplicationException,
               AppdefDuplicateNameException, AppdefDuplicateFQDNException
    {
        try {
            return getPlatformManager().updatePlatform(subject, aPlatform)
                .getPlatformValue();
        } catch (Exception e) {
            log.error("Error updating platform: " + aPlatform.getId());
            // rollback();
            if(e instanceof CreateException) {
                // change to a update exception as this only occurs
                // if there was a failure instantiating the session bean
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
     */
    public ApplicationValue updateApplication(int sessionId,
                                              ApplicationValue app)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubject caller = manager.getSubject(sessionId);
            return getApplicationManager().updateApplication(caller, app);
        } catch (PermissionException e) {
            rollback();
            throw e;
        } catch (FinderException e) {
            rollback();
            throw new ApplicationException(e);
        } catch (AppdefDuplicateNameException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
            throw new SystemException(e);
        }
    }

    /**
     * Set the services used by an application
     * indicate whether the service is an entry point
     * @ejb:interface-method
     */
    public void setApplicationServices(int sessionId, Integer appId,
                                       List entityIds)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubject caller = manager.getSubject(sessionId);
            getApplicationManager().setApplicationServices(caller, appId, 
                                                           entityIds);
        } catch (PermissionException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
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
            AuthzSubject caller = manager.getSubject(sessionId);
            return getApplicationManager().getServiceDepsForApp(caller, appId);
        } catch (PermissionException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
            throw new SystemException(e);
        }
    }

    /**
     * @ejb:interface-method
     */
    public void setAppDependencyTree(int sessionId, DependencyTree depTree)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubject caller = manager.getSubject(sessionId);
            getApplicationManager().setServiceDepsForApp(caller, depTree);
        } catch (PermissionException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
            throw new SystemException(e);
        }
    }
    
    /**
     * @ejb:interface-method
     */
    public void removeServer(AuthzSubject subj, Integer serverId)
        throws ServerNotFoundException, SessionNotFoundException,
               SessionTimeoutException, PermissionException,
               SessionException, VetoException {
        Server server = getServerManager().findServerById(serverId);
        removeServer(subj, server);
    }

    /**
     * Remove an application service.
     * @param appId         - The application identifier.
     * @ejb:interface-method
     */
    public void removeAppService (int sessionId, Integer appId,
                                  Integer serviceId)
        throws ApplicationException, ApplicationNotFoundException,
               PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        try {
            AuthzSubject caller = manager.getSubject(sessionId);
            getApplicationManager().removeAppService(caller, appId, serviceId);
        } catch (SystemException e) {
            rollback();
            throw e;
        }
    }

    /**
     * @return The updated Resource
     * @ejb:interface-method
     */
    public AppdefResourceValue changeResourceOwner(int sessionId,
                                                   AppdefEntityID eid,
                                                   Integer newOwnerId)
        throws ApplicationException, PermissionException 
    {
        try {
            AuthzSubject caller = manager.getSubject(sessionId);
            AuthzSubject newOwner =
                getAuthzSubjectManager().findSubjectById(newOwnerId);
           
            if (eid.isGroup()) {
                ResourceGroup g = getResourceGroupManager()
                    .findResourceGroupById(caller, eid.getId());
                getResourceGroupManager().changeGroupOwner(caller, g, newOwner);
                return findGroup(sessionId, eid.getId());
            }
            
            AppdefEntityValue aev = new AppdefEntityValue(eid, caller);
            getAppdefManager().changeOwner(caller, aev.getResourcePOJO(),
                                           newOwner);
            return aev.getResourceValue();
        } catch (PermissionException e) {
            throw e;
        } catch (Exception e) {
            // everything else is a system error
            throw new SystemException(e);
        }
    }

    private List getResources(String[] resources) {
        if (resources == null)
            return Collections.EMPTY_LIST;
        
        List ret = new ArrayList(resources.length);
        ResourceManager resMan = getResourceManager();
        for (int i = 0; i < resources.length; i++) {
            AppdefEntityID aeid = new AppdefEntityID(resources[i]);
            ret.add(resMan.findResource(aeid));
        }
        return ret;
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
     */
    public ResourceGroup createGroup(int sessionId, String name,
                                     String description, String location,
                                     String[] resources, boolean privGrp)
        throws GroupCreationException, GroupDuplicateNameException,
               SessionException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupCreateInfo cInfo = 
            new ResourceGroupCreateInfo(name, description,
                              AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_PSS,
                              null,      // prototype
                              location,
                              0,         // clusterId 
                              false,     // system?
                              privGrp);

        // No roles or resources
        return getResourceGroupManager()
            .createResourceGroup(subject, cInfo,
                                 Collections.EMPTY_LIST,
                                 getResources(resources));
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
     */
    public ResourceGroup createGroup(int sessionId, int adType, String name,
                                     String description, String location,
                                     String[] resources, boolean privGrp)
        throws GroupCreationException, SessionException,
               GroupDuplicateNameException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        int groupType;
        
        if (adType == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_GRP;
        } else if (adType == AppdefEntityConstants.APPDEF_TYPE_APPLICATION) {
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_APP;
        } else {
            throw new IllegalArgumentException("Invalid group type. Strict " + 
                                               "mixed group types can be " +
                                               "group or application");
        }        
        
        ResourceGroupCreateInfo cInfo = 
            new ResourceGroupCreateInfo(name, description,
                                        groupType,
                                        null,      // prototype
                                        location,
                                        0,         // clusterId 
                                        false,     // system?
                                        privGrp);

        // No roles or resources
        return getResourceGroupManager()
            .createResourceGroup(subject, cInfo,
                                 Collections.EMPTY_LIST,
                                 getResources(resources));
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
     */
    public ResourceGroup createGroup(int sessionId, int adType, int adResType,
                                     String name, String description,
                                     String location, String[] resources,
                                     boolean privGrp)
        throws GroupCreationException, GroupDuplicateNameException,
               SessionException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        int groupType;
        
        switch (adType) {
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC;
            break;
        case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_PS;
            break;
        default:
            throw new IllegalArgumentException("Invalid group compatibility " +
                                               "type specified");
        }
        
        Resource prototype =  getResourceManager()
            .findResourcePrototype(new AppdefEntityTypeID(adType, adResType));
        
        ResourceGroupCreateInfo cInfo = 
            new ResourceGroupCreateInfo(name, description,
                                        groupType,
                                        prototype,      
                                        location,
                                        0,          // clusterId 
                                        false,      // system?
                                        privGrp);

        // No roles or resources
        return getResourceGroupManager()
            .createResourceGroup(subject, cInfo,
                                 Collections.EMPTY_LIST,
                                 getResources(resources));
    }

    /**
     * Remove resources from the group's contents.
     * 
     * @ejb:interface-method
     */
    public void removeResourcesFromGroup(int sessionId, ResourceGroup group,
                                         Collection resources)
        throws SessionException, PermissionException
    {
        AuthzSubject subject = manager.getSubject(sessionId);

        getResourceGroupManager().removeResources(subject, group, resources);
    }
    
    /**
     * @ejb:interface-method
     */
    public ResourceGroup findGroupById(int sessionId, Integer groupId) 
        throws PermissionException, SessionException
    {
        AuthzSubject subject = manager.getSubject(sessionId);

        return getResourceGroupManager().findResourceGroupById(subject,
                                                               groupId);
    }
    
    /**
     * @ejb:interface-method
     */
    public Map getResourceTypeCountMap(int sessionId, Integer groupId)
        throws PermissionException, SessionException {
        ResourceGroup g = findGroupById(sessionId, groupId);
        return getResourceGroupManager().getMemberTypes(g);
    }

    /**
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroup(int sessionId, Integer id)
        throws PermissionException, SessionException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupManager groupMan = getResourceGroupManager();
        ResourceGroup group = groupMan.findResourceGroupById(subject, id);
        return groupMan.getGroupConvert(subject, group);
    }

    /**
     * @ejb:interface-method
     */
    public Collection getGroupsForResource(int sessionId, Resource r)
        throws SessionNotFoundException, SessionTimeoutException {
        manager.authenticate(sessionId);
        ResourceGroupManager groupMan = getResourceGroupManager();
        return groupMan.getGroups(r);
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
        throws PermissionException, SessionException
    {
        List toBePaged = new ArrayList(groupIds.length);
        for (int i=0; i < groupIds.length; i++) {
            toBePaged.add(findGroup(sessionId, groupIds[i]));
        }
        return getPageList(toBePaged,pc);
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
        return findAllGroupsMemberInclusive(sessionId, pc, entity, 
                                            new Integer[0]);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Include just those groups that contain the
     * specified appdef entity. Apply group filter to remove unwanted
     * groups.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * */
    private PageList findAllGroupsMemberInclusive(int sessionId, PageControl pc,
                                                  AppdefEntityID entity,
                                                  Integer[] excludeIds)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException, ApplicationException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupManager groupMan = getResourceGroupManager();
        
        List excludeGroups = new ArrayList(excludeIds.length);
        for (int i=0; i<excludeIds.length; i++) {
            excludeGroups.add(groupMan.findResourceGroupById(excludeIds[i]));
        }
        
        Resource r = getResourceManager().findResource(entity);
        PageInfo pInfo = PageInfo.create(pc, ResourceGroupSortField.NAME);
        PageList res = groupMan.findGroupsContaining(subject, r,
                                                     excludeGroups, pInfo);
        List appVals = new ArrayList(res.size());
        for (Iterator i=res.iterator(); i.hasNext(); ) {
            ResourceGroup g = (ResourceGroup)i.next();
            appVals.add(groupMan.getGroupConvert(subject, g));
        }
        
        return new PageList(appVals, res.getTotalSize());
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
                                     Resource resourceType)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupManager groupMan = getResourceGroupManager();
        List excludeGroups = new ArrayList(removeIds.length);
        
        for (int i=0; i<removeIds.length; i++) {
            excludeGroups.add(groupMan.findResourceGroupById(removeIds[i]));
        }
        Resource r = getResourceManager().findResource(entity);
        PageInfo pInfo = PageInfo.create(pc, ResourceGroupSortField.NAME);
        PageList res = groupMan.findGroupsNotContaining(subject, r,
                                                        resourceType, 
                                                        excludeGroups,
                                                        pInfo);
        
        // Now convert those ResourceGroups into AppdefResourceGroupValues
        List appVals = new ArrayList(res.size());
        for (Iterator i=res.iterator(); i.hasNext(); ) {
            ResourceGroup g = (ResourceGroup)i.next();
            appVals.add(groupMan.getGroupConvert(subject, g));
        }
        
        return new PageList(appVals, res.getTotalSize());
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Exclude any groups that contain the appdef entity id. Filter
     * out any unwanted groups specified by groupId array.
     * @param entity for use in group member filtering.
     * @return List containing AppdefGroupValue.
     * @ejb:interface-method
     * */
    public PageList findAllGroupsMemberExclusive(int sessionId, PageControl pc,
                                                 AppdefEntityID[] entities)
        throws PermissionException, SessionException 
    {
        List commonList = new ArrayList();
        List result = null;
        AppdefEntityID eid = null;
        Resource resource = null;
        ResourceManager resourceMan = ResourceManagerImpl.getOne();
        
        for (int i=0; i<entities.length; i++) {
            eid = entities[i];
            resource = resourceMan.findResource(eid);
            result = findAllGroupsMemberExclusive(
                                sessionId, 
                                pc, 
                                eid, 
                                new Integer[] {}, 
                                resource.getPrototype());
            
            if (i==0) {
                commonList.addAll(result);
            } else {
                commonList.retainAll(result);
            }

            if (commonList.isEmpty()) {
                // no groups in common, so exit
                break;                
            }
        }
        
        return new PageList(commonList, commonList.size());
    }
    
    /**
     * Produce list of all group pojos where caller is authorized
     * @return List containing AppdefGroup.
     * @ejb:interface-method
     * */
    public Collection findAllGroupPojos(int sessionId)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupManager mgr = ResourceGroupManagerImpl.getOne();
        
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
     * Add entities to a resource group
     * @ejb:interface-method
     */
    public void addResourcesToGroup(int sessionID, ResourceGroup group,
                                    List aeids)
        throws SessionException, PermissionException 
    {
        AuthzSubject subject = manager.getSubject(sessionID);
        ResourceGroupManager groupMan = 
            ResourceGroupManagerImpl.getOne();
        ResourceManager resourceMan =
            ResourceManagerImpl.getOne();
        
        for (Iterator i = aeids.iterator(); i.hasNext(); ) {
            AppdefEntityID aeid = (AppdefEntityID)i.next();
            Resource resource = resourceMan.findResource(aeid);
            groupMan.addResource(subject, group, resource);
        }
    }

    /**
     * Update properties of a group.
     * 
     * @see ResourceGroupManagerImpl.updateGroup
     * @ejb:interface-method
     */
    public void updateGroup(int sessionId, ResourceGroup group,
                            String name, String description, String location) 
        throws SessionException, PermissionException,
               GroupDuplicateNameException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        
        getResourceGroupManager().updateGroup(subject, group, name, description, 
                                              location); 
    }
    
    // Return a PageList of authz resources.
    private List findViewableEntityIds(AuthzSubject subject, 
                                       int appdefTypeId, String rName,
                                       Integer filterType, PageControl pc) 
    {
        List appentResources = new ArrayList();

        ResourceManager resMgr = getResourceManager();

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
                    appentResources.add(new AppdefEntityID(appdefType, instId));
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
     */
    public PageList findCompatInventory(int sessionId, int groupType,
                                        int appdefTypeId, int groupEntTypeId,
                                        int appdefResTypeId,
                                        String resourceName,
                                        AppdefEntityID[] pendingEntities,
                                        PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionException
    {
        if ( groupType != APPDEF_GROUP_TYPE_UNDEFINED &&
             !AppdefEntityConstants.groupTypeIsValid(groupType) ) {
            throw new IllegalArgumentException ("Invalid group type: " +
                                                groupType);
        }

        return findCompatInventory(sessionId, appdefTypeId, appdefResTypeId,
                                   groupEntTypeId, null, false, pendingEntities,
                                   resourceName, null, groupType, pc);
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
     */
    public PageList findCompatInventory(int sessionId, int appdefTypeId,
                                        int appdefResTypeId,
                                        AppdefEntityID groupEntity,
                                        AppdefEntityID[] pendingEntities,
                                        String resourceName, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionException
    {
        PageList ret = findCompatInventory(sessionId, appdefTypeId,
                                           appdefResTypeId,
                                           APPDEF_GROUP_TYPE_UNDEFINED,
                                           groupEntity, false,
                                           pendingEntities, resourceName, null,
                                           APPDEF_GROUP_TYPE_UNDEFINED, pc);

        if (appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_SERVER ||
            appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_SERVICE) 
        {
            for (Iterator i = ret.iterator(); i.hasNext(); ) {
                AppdefResourceValue res = (AppdefResourceValue) i.next();

                if (appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_SERVER) {
                    Server server =
                        getServerManager().findServerById(res.getId());
                    res.setHostName(server.getPlatform().getName());

                } else {
                    Service service =
                        getServiceManager().findServiceById(res.getId());
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
        throws PermissionException, SessionException 
    {
        List toBePaged;

        AuthzSubject subj = manager.getSubject(sessionId);
        AppdefPagerFilterGroupEntityResource erFilter;
        AppdefPagerFilterAssignSvc assignedSvcFilter;
        AppdefPagerFilterGroupMemExclude groupMemberFilter;
        boolean groupEntContext = groupType != APPDEF_GROUP_TYPE_UNDEFINED ||
                                  grpEntId != APPDEF_GROUP_TYPE_UNDEFINED;

        StopWatch watch = new StopWatch();
        watch.markTimeBegin("findCompatInventory");

        // init our (never-null) page and filter lists
        if (filterList == null) {
            filterList = new ArrayList();
        }
        assignedSvcFilter = null;
        groupMemberFilter = null;

    	// This list can contain items having different appdef types
    	// we need to screen the list and only include items matching the 
    	// value of appdefTypeId.  Otherwise, the paging logic below
    	// will be thrown off (it assumes that list of pendingEntities is always
    	// a subset of the available, which is not always the case) [HHQ-3026]
    	List pendingEntitiesFiltered = new ArrayList();

        // add a pager filter for removing pending appdef entities
        if (pendingEntities != null) {
        	
        	for (int x = 0; x < pendingEntities.length; x++) {
        		if (pendingEntities[x].getType() == appdefTypeId) {
        			pendingEntitiesFiltered.add(pendingEntities[x]);
        		}
        	}
        	
            filterList.add( new AppdefPagerFilterExclude ( 
            		(AppdefEntityID[]) pendingEntitiesFiltered.toArray(
            				new AppdefEntityID[pendingEntitiesFiltered.size()])));
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
            } catch (PermissionException e) {
                // Should never happen, finder accounts for permissions;
                log.error("Caught permission exc filtering on group",e);
            }
        }

        // Install a filter that uses group type, entity type and
        // resource type to filter the inventory set. This facilitates
        // the HTML selectors that appear all over the product.
        if (groupEntContext) {
            erFilter =
                new AppdefPagerFilterGroupEntityResource (subj,
                                                          groupType,
                                                          grpEntId,
                                                          appdefResTypeId,
                                                          true );
            filterList.add( erFilter );
        } else if (groupEntity != null) {
            erFilter =
                new AppdefPagerFilterGroupEntityResource (subj,
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

        // find ALL viewable resources by entity (type or name) and
        // translate to appdef entities.
        // We have to create a new page control because we are no
        // longer limiting the size of the record set in authz.
        watch.markTimeBegin("findViewableEntityIds");
        Integer filterType = appdefResTypeId != -1 ?
                             new Integer(appdefResTypeId) : null;

        toBePaged = findViewableEntityIds(subj,  appdefTypeId,
                                          resourceName, filterType, pc);
        watch.markTimeEnd("findViewableEntityIds");

        // Page it, then convert to AppdefResourceValue
        List finalList = new ArrayList();
        watch.markTimeBegin("getPageList");
        PageList pl = getPageList (toBePaged, pc, filterList);
        watch.markTimeEnd("getPageList");

        for (Iterator itr = pl.iterator(); itr.hasNext();) {
            AppdefEntityID ent = (AppdefEntityID) itr.next();
            AppdefEntityValue aev = new AppdefEntityValue(ent, subj);

            try {
                if (ent.isGroup()) {
                    finalList.add(aev.getAppdefGroupValue());
                }
                else {
                    AppdefResource resource = aev.getResourcePOJO();
                    finalList.add(resource.getAppdefResourceValue());
                }
            } catch (AppdefEntityNotFoundException e) {
                // XXX - hack to ignore the error.  This must have occurred
                // when we created the resource, and rolled back the
                // AppdefEntity but not the Resource
                log.error("Invalid entity still in resource table: " + ent);
                continue;
            }
        }

        // Use pendingEntitiesFiltered as it will contain the correct number of 
        // items based on the selected appdeftype [HHQ-3026]
        int pendingSize = 0;
        if (pendingEntitiesFiltered != null)
            pendingSize = pendingEntitiesFiltered.size();

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
     * Perform a search for resources from the resource hub
     * @ejb:interface-method
     */
    public PageList search(int sessionId, int appdefTypeId, String searchFor,
                           AppdefEntityTypeID appdefResType, Integer groupId,
                           int[] groupSubType, boolean matchAny, 
                           boolean matchOwn, boolean matchUnavail,
                           PageControl pc)
        throws PermissionException, SessionException, PatternSyntaxException
    {
        int grpEntId = APPDEF_GROUP_TYPE_UNDEFINED;

        if (appdefTypeId == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            grpEntId = (appdefResType == null) ? 
                AppdefEntityConstants.APPDEF_TYPE_GROUP :
                appdefResType.getType();
        }
        AppdefEntityID grpId = (groupId == null) ?
            null : AppdefEntityID.newGroupID(groupId);
        
        if (groupSubType != null) {
            appdefTypeId = AppdefEntityConstants.APPDEF_TYPE_GROUP;
        }
        
        AuthzSubject subject = manager.getSubject(sessionId);
        PageList res = new PageList();
        
        CritterTranslator trans       = new CritterTranslator();
        CritterTranslationContext ctx = new CritterTranslationContext(subject);
        CritterList cList = getCritterList(subject, matchAny,appdefResType,
                                           searchFor, grpId, grpEntId,
                                           groupSubType, appdefTypeId, matchOwn,
                                           matchUnavail);
        PageList children = trans.translate(ctx, cList, pc);
        res.ensureCapacity(children.size());
        res.setTotalSize(children.getTotalSize());
        for (Iterator j = children.iterator(); j.hasNext();) {
            try {
                Resource child = (Resource) j.next();
                AppdefEntityID aeid = new AppdefEntityID(child);
                AppdefEntityValue arv = new AppdefEntityValue(aeid, subject);
                if (aeid.isGroup()) {
                    res.add(arv.getAppdefGroupValue());
                } else {
                    AppdefResource resource = arv.getResourcePOJO();
                    res.add(resource.getAppdefResourceValue());
                }
            } catch (AppdefEntityNotFoundException e) {
                log.warn(e.getMessage(), e);
            }
        }
        return res;
    }
    
    private CritterList getCritterList(AuthzSubject subj, boolean matchAny,
                                       AppdefEntityTypeID appdefResType,
                                       String resourceName,
                                       AppdefEntityID grpId, int grpEntId,
                                       int[] groupTypes, int appdefTypeId,
                                       boolean matchOwn, boolean matchUnavail)
        throws PatternSyntaxException
    {
        Critter tmp;
        Resource proto = null;
        if (appdefResType != null) {
           ResourceManager rman = ResourceManagerImpl.getOne();
           proto = rman.findResourcePrototype(appdefResType); 
        }
        boolean isGroup = (groupTypes == null) ? false : true;
        List critters = new ArrayList();
        if (isGroup) {
            critters.add(getGrpTypeCritter(groupTypes, proto));
            if (null != (tmp = getResourceTypeCritter(grpEntId))) {
                critters.add(tmp);
            } else if (null != (tmp = getResourceTypeCritter(appdefTypeId))) {
                critters.add(tmp);
            }
        } else {
            if (null != (tmp = getProtoCritter(appdefResType, proto))) {
                critters.add(tmp);
            }
            if (null != (tmp = getResourceTypeCritter(appdefTypeId))) {
                critters.add(tmp);
            }
        }
        if (null != (tmp = getResourceNameCritter(resourceName))) {
            critters.add(tmp);
        }
        if (null != (tmp = getGrpMemCritter(grpId))) {
            critters.add(tmp);
        }
        if (matchOwn) {
            critters.add(getOwnCritter(subj));
        }
        if (matchUnavail) {
            critters.add(getUnavailCritter());
        }
        return new CritterList(critters, matchAny);
    }
    
    private Critter getGrpTypeCritter(int[] groupTypes, Resource proto) {
        if (groupTypes.length == 0 ||
            groupTypes[0] == APPDEF_GROUP_TYPE_UNDEFINED) {
            return null;
        }
        if (AppdefEntityConstants.isGroupCompat(groupTypes[0])) {
            CompatGroupTypeCritterType critter =
                new CompatGroupTypeCritterType();
            return critter.newInstance(proto);
        } else {
            MixedGroupType type = MixedGroupType.findByCode(groupTypes);
            MixedGroupTypeCritterType critter =
                new MixedGroupTypeCritterType();
            return critter.newInstance(type);
        }
    }

    private Critter getProtoCritter(AppdefEntityTypeID appdefResType,
                                    Resource proto) {
        if (appdefResType != null && proto != null) {
            ProtoCritterType protoType = new ProtoCritterType();
            return protoType.newInstance(proto);
        }
        return null;
    }

    private Critter getGrpMemCritter(AppdefEntityID grpId) {
        if (grpId != null) {
            ResourceGroupManager rgman =
                ResourceGroupManagerImpl.getOne();
            ResourceGroup group =
                rgman.findResourceGroupById(grpId.getId());
            GroupMembershipCritterType groupMemType =
                new GroupMembershipCritterType();
            return groupMemType.newInstance(group);
        }
        return null;
    }

    private Critter getResourceTypeCritter(int appdefTypeId) {
        if (appdefTypeId == APPDEF_GROUP_TYPE_UNDEFINED ||
            appdefTypeId == APPDEF_TYPE_UNDEFINED ||
            appdefTypeId == APPDEF_RES_TYPE_UNDEFINED) {
            return null;
        }
        String resTypeName = AppdefUtil.appdefTypeIdToAuthzTypeStr(appdefTypeId);
        ResourceTypeCritterType type = new ResourceTypeCritterType();
        return type.newInstance(resTypeName);
    }

    private Critter getResourceNameCritter(String resourceName)
        throws PatternSyntaxException
    {
        if (resourceName != null) {
            ResourceNameCritterType resNameCritterType =
                new ResourceNameCritterType();
            return resNameCritterType.newInstance(resourceName);
        }
        return null;
    }
    
    private Critter getOwnCritter(AuthzSubject subj) {
        OwnedCritterType ct = new OwnedCritterType();
        return ct.newInstance(subj);
    }
    
    private Critter getUnavailCritter() {
        AvailabilityCritterType ct = new AvailabilityCritterType();
        return ct.newInstance(AvailabilityType.AVAIL_DOWN);
    }

    /**
     * Perform a search for resources
     * @ejb:interface-method
     */
    public PageList search(int sessionId, String searchFor, PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException {
        AuthzSubject subject = manager.getSubject(sessionId);
        PageList resources =
            getResourceManager().findViewables(subject, searchFor, pc);
        
        List searchResults = new ArrayList(resources.size());
        for (Iterator it = resources.iterator(); it.hasNext(); ) {
            Resource res = (Resource) it.next();
            AppdefEntityID aeid = new AppdefEntityID(res);
            searchResults
                .add(new SearchResult(res.getName(),
                                      AppdefEntityConstants
                                          .typeToString(aeid.getType()),
                                      aeid.getAppdefKey()));
        }
        
        return new PageList(searchResults, resources.getTotalSize());        
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
     */
    public PageList
        findAvailableServicesForApplication(int sessionId, Integer appId,
                                            AppdefEntityID[] pendingEntities,
                                            String resourceName, PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionException
    {
        List toBePaged, filterList, authzResources;
        AuthzSubject subject = manager.getSubject(sessionId);

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
            Resource rv = (Resource) i.next();
            AppdefEntityID id = new AppdefEntityID(rv);
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
                finalList.add( findById(subject,ent) );
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
                                  List filterList) {
        Pager pager;
        AppdefPagerFilter[] filterArr;

        pc = PageControl.initDefaults(pc, SortAttribute.RESTYPE_NAME);

        filterArr = (AppdefPagerFilter[])
            filterList.toArray (new AppdefPagerFilter[0]);

        try {
            pager = Pager.getPager( APPDEF_PAGER_PROCESSOR );
        } catch (Exception e) {
            throw new SystemException("Unable to get a pager", e);
        }
        return pager.seekAll(coll,pc.getPagenum(), pc.getPagesize(),
                             filterArr);
    }

    /**
     * Add an appdef entity to a batch of groups.
     *
     * @param sessionId representing session identifier
     * @param entityId object to be added.
     * @param groupIds identifier array
     * @ejb:interface-method
     */
    public void batchGroupAdd(int sessionId, AppdefEntityID entityId,
                              Integer[] groupIds)
        throws SessionException, PermissionException, VetoException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupManager groupMan = getResourceGroupManager();
        ResourceManager resourceMan = getResourceManager();
        Resource resource = resourceMan.findResource(entityId);
        
        for (int i=0; i < groupIds.length; i++) {
            ResourceGroup group = groupMan.findResourceGroupById(subject, 
                                                                 groupIds[i]);
            groupMan.addResource(subject, group, resource);
        }
    }

    /**
     * Update all the appdef resources owned by this user to be owned
     * by the root user. This is done to prevent resources from being
     * orphaned in the UI due to its display restrictions. This method
     * should only get called before a user is about to be deleted
     * @ejb:interface-method
     */
    public void resetResourceOwnership(int sessionId, AuthzSubject currentOwner)
        throws UpdateException, PermissionException,
               AppdefEntityNotFoundException
    {
        final ResourceGroupManager groupMan = getResourceGroupManager();
        
        // first look up the appdef resources by owner
        final ResourceManager resMan = getResourceManager();
        Collection resources = resMan.findResourceByOwner(currentOwner);
        AuthzSubject overlord = getAuthzSubjectManager().getOverlordPojo();
        for(Iterator it = resources.iterator(); it.hasNext(); ) {
            Resource aRes = (Resource) it.next();
            AppdefEntityID aeid = new AppdefEntityID(aRes);

            if (aeid.isGroup()) {
                ResourceGroup g = 
                    groupMan.findResourceGroupById(overlord, 
                                                   aRes.getInstanceId());
                groupMan.changeGroupOwner(overlord, g, overlord);
            }
            else {
                resMan.setResourceOwner(overlord, aRes, overlord);
                AppdefEntityValue aev = new AppdefEntityValue(aeid, overlord);
                AppdefResource appdef = aev.getResourcePOJO();
                appdef.setModifiedBy(overlord.getName());
            }
        }
    }

    /**
     * Remove an appdef entity from a batch of groups.
     * @param entityId object to be removed
     * @param groupIds identifier array
     * @ejb:interface-method
     */
    public void batchGroupRemove(int sessionId, AppdefEntityID entityId,
                                 Integer[] groupIds)
        throws PermissionException, SessionException, VetoException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
        ResourceGroupManager groupMan = getResourceGroupManager();
        ResourceManager resourceMan = getResourceManager();
        Resource resource = resourceMan.findResource(entityId);
        
        for (int i=0;i<groupIds.length;i++) {
            ResourceGroup group = groupMan.findResourceGroupById(subject, 
                                                                 groupIds[i]);
            groupMan.removeResources(subject, group, 
                                     Collections.singleton(resource));
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
        AuthzSubject who = manager.getSubject(sessionId);
        return getAppdefManager().getResourcePermissions(who, id);
    }

    /**
     * @ejb:interface-method
     */
    public int getAgentCount(int sessionId)
        throws SessionNotFoundException, SessionTimeoutException
    {
        manager.authenticate(sessionId);
        return getAgentManager().getAgentCount();
    }

    /**
     * @ejb:interface-method
     */
    public List findAllAgents(int sessionId)
        throws SessionNotFoundException, SessionTimeoutException
    {
        manager.authenticate(sessionId);
        return getAgentManager().getAgents();
    }

    /**
     * Get the value of one agent based on the IP and Port on
     * which the agent is listening
     * @ejb:interface-method
     */
    public Agent findAgentByIpAndPort(int sessionId, String ip, int port)
        throws SessionNotFoundException, SessionTimeoutException,
               AgentNotFoundException
    {
        manager.authenticate(sessionId);
        return getAgentManager().getAgent(ip, port);
    }

    /**
     * Set (or delete) a custom property for a resource.  If the
     * property already exists, it will be overwritten.
     * @param id  Appdef entity to set the value for
     * @param key Key to associate the value with
     * @param val Value to assicate with the key.  If the value is null,
     *            then the value will simply be removed.
     * @ejb:interface-method
     */
    public void setCPropValue(int sessionId, AppdefEntityID id,
                              String key, String val)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException,
               CPropKeyNotFoundException
    {
        AuthzSubject who = manager.getSubject(sessionId);
        AppdefEntityValue aVal = new AppdefEntityValue(id, who);
        int typeId = aVal.getAppdefResourceType().getId().intValue();
        getCPropManager().setValue(id, typeId, key, val);
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
        manager.authenticate(sessionId);
        return getCPropManager().getDescEntries(id);
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
        manager.authenticate(sessionId);
        return getCPropManager().getKeys(appdefType, appdefTypeId);
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
        AuthzSubject who = manager.getSubject(sessionId);
        
        AppdefEntityValue av = new AppdefEntityValue(aeid, who);
        int typeId = av.getAppdefResourceType().getId().intValue();
        
        return getCPropManager().getKeys(aeid.getType(), typeId);
    }

    /**
     * Get the appdef inventory summary visible to a user
     * @ejb:interface-method
     */
    public AppdefInventorySummary getInventorySummary(int sessionId,
                                                      boolean countTypes)
        throws SessionNotFoundException, SessionTimeoutException 
    {
        AuthzSubject who = manager.getSubject(sessionId);
        return new AppdefInventorySummary(who, countTypes);
    }

    /**
     * Returns a 2x2 array mapping "appdef type id" to its corresponding
     * label. Suitable for populating an HTML selector.
     * @ejb:interface-method
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
    */
    public void setAllConfigResponses(int sessionInt, 
                                      AllConfigResponses allConfigs,
                                      AllConfigResponses allConfigsRollback )
        throws PermissionException, EncodingException, PluginException,
               ApplicationException, AutoinventoryException,
               ScheduleWillNeverFireException, AgentConnectionException
    {
        AuthzSubject subject = manager.getSubject(sessionInt);
        boolean doRollback = true;
        boolean doValidation = (allConfigsRollback != null);
        AppdefEntityID id = allConfigs.getResource();

        try {
            doSetAll(subject, allConfigs, doValidation, false);
            
            if (doValidation) {
            	getConfigManager().clearValidationError(subject, id);
            }
            
            doRollback = false;
            
            // run an auto-scan for platforms
            if (id.isPlatform()) {
                // HQ-1259: Use hqadmin as the subject to propagate platform  
                // configuration changes to platform services if the user
                // as insufficient permissions
            	AuthzSubject aiSubject = subject;
            	try {
            		AIQueueManagerEJBImpl.getOne().checkAIScanPermission(subject, id);
            	} catch (PermissionException pe) {
            		aiSubject = getAuthzSubjectManager()
            						.getSubjectById(AuthzConstants.rootSubjectId);
            	}
            	getAutoInventoryManager().startScan(aiSubject, id, 
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
                doSetAll(subject, allConfigsRollback, false, true);
            }
        }
    }

    private void doSetAll(AuthzSubject subject, AllConfigResponses allConfigs,
                          boolean doValidation, boolean force)
        throws EncodingException, PermissionException,
               ConfigFetchException, PluginException, ApplicationException
    {
    	AppdefEntityID entityId = allConfigs.getResource();
        Set ids = new HashSet();
        ConfigResponseDB existingConfig;
        Service svc = null;
        try {
            existingConfig = getConfigManager().getConfigResponse(entityId);
            
            if (getConfigManager().configureResponse(
                subject, existingConfig, entityId,
                ConfigResponse.safeEncode(allConfigs.getProductConfig()),
                ConfigResponse.safeEncode(allConfigs.getMetricConfig()),
                ConfigResponse.safeEncode(allConfigs.getControlConfig()),
                ConfigResponse.safeEncode(allConfigs.getRtConfig()),
                Boolean.TRUE, !doValidation, force) != null) {
                ids.add(entityId);
            }
            
            if (doValidation) {
                Set validationTypes = new HashSet();

                if (allConfigs.shouldConfigProduct()) {
                    validationTypes.add(ProductPlugin.TYPE_CONTROL);
                    validationTypes.add(ProductPlugin.TYPE_RESPONSE_TIME);
                    validationTypes.add(ProductPlugin.TYPE_MEASUREMENT);
                }

                if (allConfigs.shouldConfigMetric()) {
                    validationTypes.add(ProductPlugin.TYPE_MEASUREMENT);
                }

                // Need to set the flags on the service so that they
                // can be looked up immediately and RtEnabler to work
                if (svc != null) {
                    // These flags
                    if (allConfigs.getEnableServiceRT() != svc.isServiceRt()
                        || allConfigs.getEnableEuRT() != svc.isEndUserRt())
                    {
                        allConfigs.setShouldConfig(
                            ProductPlugin.CFGTYPE_IDX_RESPONSE_TIME, true);
                        svc.setServiceRt(allConfigs.getEnableServiceRT());
                        svc.setEndUserRt(allConfigs.getEnableEuRT());
                    }
                }

                if (allConfigs.shouldConfigRt()) {
                    validationTypes.add(ProductPlugin.TYPE_RESPONSE_TIME);
                }

                if (allConfigs.shouldConfigControl()) {
                    validationTypes.add(ProductPlugin.TYPE_CONTROL);
                }

                ConfigValidator configValidator =
                    (ConfigValidator) ProductProperties
                    .getPropertyInstance(ConfigValidator.PDT_PROP);

                // See if we can validate
                if (configValidator != null) {
                    Iterator validations = validationTypes.iterator();
                    AppdefEntityID[] idArr = (AppdefEntityID[]) ids
                            .toArray(new AppdefEntityID[0]);

                    while (validations.hasNext()) {
                        configValidator.validate(subject,
                                                 (String)validations.next(),
                                                 idArr);
                    }
                }
            }

            if (allConfigs.shouldConfigProduct() ||
                allConfigs.shouldConfigMetric()) {
                List servers = new ArrayList();
                if (entityId.isServer()) {
                    servers.add(
                        getServerManager().findServerById(entityId.getId()));
                }
                else if (entityId.isPlatform()) {
                    // Get the virtual servers
                    Platform plat =
                        getPlatformManager().findPlatformById(entityId.getId());
                    for (Iterator it = plat.getServers().iterator();
                         it.hasNext(); ) {
                        Server server = (Server) it.next();
                        if (server.getServerType().isVirtual()) {
                            servers.add(server);
                        }
                    }
                }
                
                for (Iterator it = servers.iterator(); it.hasNext(); ) {
                    // Look up the server's services
                    Server server = (Server) it.next();
                    for (Iterator sit = server.getServices().iterator();
                         sit.hasNext(); ) {
                        Service service = (Service) sit.next();
                        ids.add(service.getEntityId());
                    }
                }
            }
            
            // if should configure RT
            if (allConfigs.shouldConfigRt())
                ids.add(entityId);

            if (ids.size() > 0) {   // Actually updated 
                List events = new ArrayList(ids.size());
                
                AppdefEntityID ade;
                AuthzSubject eventSubject;
                AuthzSubject hqadmin = getAuthzSubjectManager()
                				.getSubjectById(AuthzConstants.rootSubjectId);
                
                for (Iterator it = ids.iterator(); it.hasNext(); ) {
                    eventSubject = subject;
                	ade = (AppdefEntityID)it.next();
                	
                    // HQ-1259: Use hqadmin as the subject to propagate platform  
                    // configuration changes to platform services if the user
                    // as insufficient permissions
                    if (entityId.isPlatform() && !ade.isPlatform()) {
                    	try {
                    		getPlatformManager().checkModifyPermission(subject, ade);
                    	} catch (PermissionException pe) {
                    		eventSubject = hqadmin;
                    	}
                    }
                	events.add(
                        new ResourceUpdatedZevent(eventSubject,
                                                  ade,
                                                  allConfigs));
                }
                ZeventManager.getInstance().enqueueEventsAfterCommit(events);
            }

            if (entityId.isServer() || entityId.isService()) {
                getAIBoss()
                    .toggleRuntimeScan(subject, entityId,
                                       allConfigs.getEnableRuntimeAIScan());
            }
        } catch (UpdateException e) {
            log.error("Error while updating resource " +
                      allConfigs.getResource());
            throw new ApplicationException(e);
        }
    }

    /**
     * Get the navigation map data for a given Appdef entity.
     * @return all navigable resources for the given appdef entity
     * @ejb:interface-method
     */
    public ResourceTreeNode[] getNavMapData(int sessionId, AppdefEntityID adeId)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, AppdefEntityNotFoundException
    {
        AuthzSubject subject = manager.getSubject(sessionId);
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
        AuthzSubject subject = manager.getSubject(sessionId);
        return getAppdefStatManager()
            .getNavMapDataForAutoGroup(subject, adeIds, new Integer(ctype));
    }

    /**
     * Get the list of resources that are unavailable
     * @ejb:interface-method 
     */
    public Collection getUnavailableResources(AuthzSubject user, String typeId,
                                              PageInfo info)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException {
        List unavailEnts = getAvailManager().getUnavailEntities(null);
        
        if (unavailEnts.size() == 0)
            return unavailEnts;
        
        DownResSortField sortField = (DownResSortField) info.getSort();
        Set ret = new TreeSet(sortField.getComparator(!info.isAscending()));
        
        int appdefType = -1;
        int appdefTypeId = -1;
        
        if (typeId != null && typeId.length() > 0) {
            try {
                appdefType = Integer.parseInt(typeId);
            } catch (NumberFormatException e) {
                AppdefEntityTypeID aetid = new AppdefEntityTypeID(typeId);
                appdefType = aetid.getType();
                appdefTypeId = aetid.getID();
            }
        }

        Set viewables = new HashSet(
            findViewableEntityIds(user, APPDEF_TYPE_UNDEFINED, null, null, null));        
        for (Iterator it = unavailEnts.iterator(); it.hasNext(); ) {
            DownMetricValue dmv = (DownMetricValue) it.next();
            AppdefEntityID entityId = dmv.getEntityId();
            if (!viewables.contains(entityId))
                continue;
            
            AppdefEntityValue res = new AppdefEntityValue(entityId, user);
                        
            // Look up the resource type
            if (appdefType != -1) {
                if (entityId.getType() != appdefType)
                    continue;
                
                if (appdefTypeId != -1) {
                    AppdefResourceType type = res.getAppdefResourceType();
                    if (type.getId().intValue() != appdefTypeId)
                        continue;
                }
            }
            
            if (log.isDebugEnabled()) {
                log.debug(res.getName() + " down for " +
                          (dmv.getDuration() / 60000) + "min");
            }
    
            ret.add(new DownResource(res.getResourcePOJO(), dmv));
        }
        
        if (!info.isAll() && ret.size() > info.getPageSize()) {
            // Have to reduce the size
            List reduced = new ArrayList(ret);
            return reduced.subList(0, info.getPageSize() - 1);
        }
        return ret;
    }
    
    /**
     * Get the map of unavailable resource counts by type
     * @ejb:interface-method
     */
    public Map getUnavailableResourcesCount(AuthzSubject user)
        throws AppdefEntityNotFoundException, PermissionException {
        // Keys for the Map table, UI should localize instead of showing key
        // values directly
        final String PLATFORMS = "Platforms";
        final String SERVERS   = "Servers";
        final String SERVICES  = "Services";
        
        List unavailEnts = getAvailManager().getUnavailEntities(null);
        Map ret = new LinkedHashMap();
        ret.put(PLATFORMS, new ArrayList());
        ret.put(SERVERS,   new ArrayList());
        ret.put(SERVICES,  new ArrayList());
        
        if (unavailEnts.size() == 0)
            return ret;

        Set viewables = new HashSet(
            findViewableEntityIds(user, APPDEF_TYPE_UNDEFINED, null, null, null));        
        for (Iterator it = unavailEnts.iterator(); it.hasNext(); ) {
            DownMetricValue dmv = (DownMetricValue) it.next();
            
            AppdefEntityID aeid = dmv.getEntityId();
            
            if (!viewables.contains(aeid))
                continue;
            
            List list;
            
            if (aeid.isPlatform()) {
                list = (List) ret.get(PLATFORMS);
            }
            else if (aeid.isServer()) {
                list = (List) ret.get(SERVERS);
            }
            else if (aeid.isService()) {
                list = (List) ret.get(SERVICES);
            }
            else {
                if (log.isDebugEnabled()) {
                    log.debug("Can't handle appdef type: " + aeid.getType());
                }
                continue;
            }
            
            AppdefEntityValue aev = new AppdefEntityValue(aeid, user);
            list.add(aev.getAppdefResourceType());
        }
        
        // Now sort each of the lists
        for (Iterator it = ret.values().iterator(); it.hasNext(); ) {
            List list = (List) it.next();
            Collections.sort(list);
        }
        return ret;
    }
    
    private class ValueComparator implements Comparator {
        boolean _asc;
        
        ValueComparator(boolean asc) {
            _asc = asc;
        }
        
        public int compare(Object o1, Object o2) {
            Map.Entry me1 = (Map.Entry) o1;
            Map.Entry me2 = (Map.Entry) o2;
            
            MetricValue mv1, mv2;
            
            if (_asc) {
                mv1 = (MetricValue) me1.getValue();
                mv2 = (MetricValue) me2.getValue();
            }
            else {
                mv1 = (MetricValue) me2.getValue();
                mv2 = (MetricValue) me1.getValue();
            }
            
            if (mv1.getValue() < mv2.getValue())
                return -1;
            
            if (mv1.getValue() > mv2.getValue())
                return 1;
            
            return 0;
        }
    }

    private class TimestampComparator implements Comparator {
        boolean _asc;
        
        TimestampComparator(boolean asc) {
            _asc = asc;
        }
        
        public int compare(Object o1, Object o2) {
            Map.Entry me1 = (Map.Entry) o1;
            Map.Entry me2 = (Map.Entry) o2;
            
            MetricValue mv1, mv2;
            
            if (_asc) {
                mv1 = (MetricValue) me1.getValue();
                mv2 = (MetricValue) me2.getValue();
            }
            else {
                mv1 = (MetricValue) me2.getValue();
                mv2 = (MetricValue) me1.getValue();
            }
            
            if (mv1.getTimestamp() < mv2.getTimestamp())
                return -1;
            
            if (mv1.getTimestamp() > mv2.getTimestamp())
                return 1;
            
            return 0;
        }
    }

    /**
     * Get Service resources and their display information
     * @ejb:interface-method
     * @param subject the caller
     * @param proto the type name of the services
     * @param cprop a unique custom property name to be fetched
     */
    public List getServicesView(AuthzSubject subject, Resource proto,
                                String cprop, String metricName, PageInfo pi)
        throws PermissionException, InvalidAppdefTypeException 
    {
        String typeName = proto.getName();
        // Find all resources of Nagios type
        List services =
            getServiceManager().getServicesByType(subject, typeName,
                                                  pi.isAscending());
        
        if (services.size() == 0)
            return new ArrayList();
        
        AppdefResourceTypeValue type =
            ((AppdefResourceValue) services.get(0)).getAppdefResourceTypeValue();
        
        // Get the Cprop values
        CPropManager cpropMan = getCPropManager();
        List cprops = cpropMan.getCPropValues(type, cprop, pi.isAscending());
        
        List ret = new ArrayList(cprops.size());
        
        Map res = new HashMap();
        for (Iterator it = cprops.iterator(); it.hasNext(); ) {
            Cprop prop = (Cprop) it.next();
            Integer id = prop.getAppdefId();
            res.put(id, prop);
        }
        
        for (Iterator it = services.iterator(); it.hasNext(); ) {
            AppdefResourceValue appRes = (AppdefResourceValue) it.next();
            Integer id = appRes.getId();

            if (res.containsKey(id)) {
                try {
                    Properties cpropProps =
                        cpropMan.getEntries(appRes.getEntityId());
                    CPropResource cpRes =
                        new CPropResource(appRes, cpropProps);
                    res.put(id, cpRes);
                }
                catch (AppdefEntityNotFoundException e) {
                    log.warn("Could not find ", e);
                    res.remove(id);
                }
            }
        }
        
        // Get the resource templates
        List templs = getTemplateManager()
            .findTemplates(type.getName(), MeasurementConstants.FILTER_NONE,
                           metricName);
        
        // There should at least one template
        assert(templs.size() > 0);
        MeasurementTemplate mt = (MeasurementTemplate) templs.get(0);

        // Find all measurement IDs
        MeasurementManager dmMan = getMetricManager();
        
        Integer[] instIds = (Integer[])
            res.keySet().toArray(new Integer[services.size()]);

        Integer[] avIds =
            dmMan.findMeasurementIds(subject, mt.getId(), instIds);

        // Now get the metric values
        Map avail = getAvailManager().getLastAvail(avIds);
        
        // Get the sort field
        SortField sf = pi.getSort();
        
        Collection entries = avail.entrySet();
        boolean sortByValue =
            sf.equals(CPropResourceSortField.METRIC_VALUE) ||
            sf.equals(CPropResourceSortField.METRIC_TIMESTAMP);

        if (sortByValue) {
            entries = new ArrayList(entries);
            Comparator comparator;
            
            if (sf.equals(CPropResourceSortField.METRIC_VALUE)) {
                comparator = new ValueComparator(pi.isAscending());
            }
            else {
                comparator = new TimestampComparator(pi.isAscending());
            }
            
            Collections.sort((List) entries, comparator);
        }
        
        long minTimestamp = Long.MAX_VALUE;
        for (Iterator it = entries.iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            Integer mid = (Integer) entry.getKey();
            
            Measurement metric = dmMan.getMeasurement(mid);
            CPropResource cpRes =
                (CPropResource) res.get(metric.getInstanceId());
            
            MetricValue mval = (MetricValue) entry.getValue();
            if (mval != null) {
                cpRes.setLastValue(mval);
                minTimestamp = Math.min(minTimestamp, mval.getTimestamp());
            }
            
            if (sortByValue) {
                ret.add(cpRes);
            }
        }
        
        // Now get their last events
        EventLogManagerLocal elMan = EventLogManagerEJBImpl.getOne();
        List events = elMan.findLastLogs(proto);
                               
        for (Iterator it = events.iterator(); it.hasNext(); ) {
            EventLog log = (EventLog) it.next();
            CPropResource cpRes =
                (CPropResource) res.get(log.getResource().getInstanceId());
            if (cpRes != null)
                cpRes.setLastEvent(log);
        }
        
        // Sort the result set if not previously sorted
        if (!sortByValue && sf.equals(CPropResourceSortField.RESOURCE)) {
            for (Iterator it = services.iterator(); it.hasNext(); ) {
                AppdefResourceValue appRes = (AppdefResourceValue) it.next();
                if (res.containsKey(appRes.getId()))
                    ret.add(res.get(appRes.getId()));
            }
        }
        else {
            // First clear out any that we've already added to the return array
            for (Iterator it = ret.iterator(); it.hasNext(); ) {
                CPropResource cpRes = (CPropResource) it.next();
                res.remove(cpRes.getEntityId().getId());
            }
            
            // Now add the rest of the resources
            for (Iterator it = cprops.iterator(); it.hasNext(); ) {
                Cprop prop = (Cprop) it.next();
                if (res.containsKey(prop.getAppdefId()))
                    ret.add(res.get(prop.getAppdefId()));
            }
        }

        return ret;
    }
    
    /** 
     * temporary method for determining whether or not we're running
     *  a database that supports navmap 
     * @ejb:interface-method
     */
    public boolean isNavMapSupported () {
        return getAppdefStatManager().isNavMapSupported();    
    }
    
    /**
     * @ejb:interface-method
     */
    public void startup() {
        log.info("AppdefBoss Boss starting up!");
        
        // Add listener to remove alert definition and alerts after resources
        // are deleted.
        HashSet events = new HashSet();
        events.add (ResourcesCleanupZevent.class);
        ZeventManager.getInstance().addBufferedListener(events,
            new ZeventListener() {
                public void processEvents(List events) {
                    for (Iterator i = events.iterator(); i.hasNext();) {
                        try {
                            getOne().removeDeletedResources();
                        } catch (Exception e) {
                            log.error("removeDeletedResources() failed", e);
                        }
                        // Only need to run this once
                        break;
                    }
                }

                public String toString() {
                    return "AppdefBoss.removeDeletedResources";
                }
            }
        );
        ZeventManager.getInstance().enqueueEventAfterCommit(
            new ResourcesCleanupZevent());
    }

    public static AppdefBossLocal getOne() {
        try {
            return AppdefBossUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    /** @ejb:create-method */
    public void ejbCreate() throws CreateException {}
    
    public void ejbRemove() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
}
