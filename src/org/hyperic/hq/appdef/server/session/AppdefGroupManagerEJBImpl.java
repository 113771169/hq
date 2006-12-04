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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.naming.NamingException;

import org.hyperic.hq.appdef.shared.AppSvcClustDuplicateAssignException;
import org.hyperic.hq.appdef.shared.AppSvcClustIncompatSvcException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.ApplicationManagerLocal;
import org.hyperic.hq.appdef.shared.ApplicationManagerUtil;
import org.hyperic.hq.appdef.shared.ApplicationTypeValue;
import org.hyperic.hq.appdef.shared.GroupTypeValue;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformManagerUtil;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.PlatformTypeValue;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServerManagerUtil;
import org.hyperic.hq.appdef.shared.ServerTypeValue;
import org.hyperic.hq.appdef.shared.ServiceClusterValue;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceManagerUtil;
import org.hyperic.hq.appdef.shared.ServiceTypeValue;
import org.hyperic.hq.appdef.shared.pager.AppdefPagerFilter;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceManagerLocal;
import org.hyperic.hq.authz.shared.ResourceManagerUtil;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.grouping.shared.GroupCreationException;
import org.hyperic.hq.grouping.shared.GroupDuplicateNameException;
import org.hyperic.hq.grouping.shared.GroupManagerLocal;
import org.hyperic.hq.grouping.shared.GroupManagerUtil;
import org.hyperic.hq.grouping.shared.GroupModificationException;
import org.hyperic.hq.grouping.shared.GroupNotCompatibleException;
import org.hyperic.hq.grouping.shared.GroupNotFoundException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** 
 * Appdef Group Manager - Appdef's view of the grouping subsystem's
 *  Group mechanism. Augments GroupValues with appdef-like qualities.
 *
 * @ejb:bean name="AppdefGroupManager"
 *      jndi-name="ejb/appdef/AppdefGroupManager"
 *      local-jndi-name="LocalAppdefGroupManager"
 *      view-type="both"
 *      type="Stateless"
 * @ejb:util generate="physical"
 * 
 */
public class AppdefGroupManagerEJBImpl extends AppdefSessionEJB
    implements SessionBean {
    private final String APPDEF_PAGER_PROCESSOR =
        "org.hyperic.hq.appdef.shared.pager.AppdefPagerProc";
    private final Log log = LogFactory.getLog(
        "org.hyperic.hq.appdef.server.session.AppdefGroupManagerEJBImpl" );
    private final int APPDEF_TYPE_UNDEFINED = -1;
    private final int APPDEF_RES_TYPE_UNDEFINED = -1;
    private final int CLUSTER_UNDEFINED = -1;
    private GroupManagerLocal groupManager;
    private ServiceManagerLocal serviceManager;
    
    private GroupManagerLocal getGroupManager () {
        if ( groupManager == null ) {
            try {
                groupManager = GroupManagerUtil.getLocalHome().create();
            } catch (NamingException ne) {
                log.error(
                    "Unable to lookup GroupManagerLocalHome interface", ne);
                throw new SystemException(
                    "Unable to lookup GroupManagerLocalHome interface", ne);
            } catch (CreateException ce) {
                log.error("Unable to create GroupManagerLocal", ce);
                throw new SystemException(
                    "Unable to create GroupManagerLocal interface", ce);
            }
        }
        return groupManager;
    }

    private ServiceManagerLocal getServiceManager() {
        if (serviceManager == null) {
            try {
                serviceManager = ServiceManagerUtil.getLocalHome().create();
            } catch (CreateException e) {
                throw new SystemException(
                    "Unable to create ServiceManagerLocal", e);
            } catch (NamingException e) {
                throw new SystemException(
                    "Unable to lookup ServiceManagerLocalHome interface", e);
            }
        }
        return serviceManager;
    }
    
    public AppdefGroupManagerEJBImpl() {}

    /**
     * Create and return a new mixed group value object. This group can 
     * contain mixed resources of any entity/resource type combination 
     * including platform, server and service.
     *
     * @param subject     - A valid spider subject value
     * @param name        - The name of the group.
     * @param description - A description of the group contents. (optional)
     * @param location    - Location of group (optional)
     * @return AppdefGroupValue object
     * @throws GroupCreationException
     *
     * @ejb:interface-method
     */
    public AppdefGroupValue createGroup(AuthzSubjectValue subject, String name,
                                        String description, String location )
        throws GroupCreationException, GroupDuplicateNameException {

        return createGroup (subject,
                            AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_PSS,
                            APPDEF_TYPE_UNDEFINED, 
                            APPDEF_RES_TYPE_UNDEFINED,
                            name, description, location);
    }

    /**
     * Create and return a new strict mixed group value object. This 
     * type of group can contain either applications or other
     * groups. However, the choice between between the 
     * two is mutually exclusive because all group members must be
     * of the same entity type. Additionally, groups that contain
     * groups are limited to containing either "application groups" or
     * "platform,server&service groups".
     *
     * @param subject     - A valid spider subject value
     * @param adType      - The appdef entity type (groups or applications)
     * @param name        - The name of the group.
     * @param description - A description of the group contents. (optional)
     * @param location    - Location of group (optional)
     * @return AppdefGroupValue object
     * @throws GroupCreationException
     *
     * @ejb:interface-method
     */
    public AppdefGroupValue createGroup(AuthzSubjectValue subject, int adType,
                                        String name, String description,
                                        String location)
        throws GroupCreationException, GroupDuplicateNameException {
        int groupType;

        if (adType == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_GRP;
        } else if (adType == AppdefEntityConstants.APPDEF_TYPE_APPLICATION) {
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_APP;
        } else {
            throw new IllegalArgumentException(
                "Invalid group type. Strict mixed group types can be " +
                "group or application");
        }

        return createGroup (subject, groupType,
                            adType, APPDEF_RES_TYPE_UNDEFINED,
                            name, description, location);
    }

    /**
     * Create and return a new compatible group type object. This group type
     * can contain any type of platform, server or service. Compatible groups
     * are strict which means that all members must be of the same type. 
     * Compatible group members must also be compatible which means that all
     * group members must have the same resource type. Compatible groups of
     * services have an additional designation of being of type "Cluster". 
     *
     * @param subject     - A valid spider subject value
     * @param adType      - The type of entity this group is compatible with.
     * @param adResType   - The resource type this group is compatible with.
     * @param name        - The name of the group.
     * @param description - A description of the group contents. (optional)
     * @param location    - Location of group (optional)
     * @return AppdefGroupValue object
     * @throws GroupCreationException
     *
     * @ejb:interface-method
     */
    public AppdefGroupValue createGroup(AuthzSubjectValue subject, int adType,
        int adResType, String name, String description, String location)
        throws  GroupCreationException, GroupDuplicateNameException {
        int groupType;

        if (adType == AppdefEntityConstants.APPDEF_TYPE_SERVICE) {
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC;
        } else if (adType == AppdefEntityConstants.APPDEF_TYPE_PLATFORM ||
                   adType == AppdefEntityConstants.APPDEF_TYPE_SERVER) {
            groupType = AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_PS;
        } else {
            throw new IllegalArgumentException ("Invalid group compatibility " +
                "type specified");
        }

        return createGroup (subject, groupType, adType, adResType,
                            name, description, location );
    }

    // Creates groups of all types
    // XXX- enforce service to cluster membership uniqueness types
    // XXX- hit appdef service manager with list of services.
    // XXX- add cluster_id
    // XXX- authz checks
    private AppdefGroupValue createGroup(AuthzSubjectValue subject, int gType,
                                         int adType, int adResType, String name,
                                         String description, String location)
        throws GroupCreationException, GroupDuplicateNameException 
    {
        // groups must have at minimum a name between 1 and 100 chars.
        if (name == null || name.length() == 0 || name.length() > 100 )
            throw new GroupCreationException ("Name must be between 1 and "+
                                              "100 characters in length.");

        // and desc (if provided) should be between 1 and 100 chars.
        if (description != null && description.length() > 100)
            throw new GroupCreationException ("Description must be between "+ 
                                              "0 and 100 characters in "+
                                              "length.");

        // and location (if provided) should be between 1 and 100 chars.
        if (location != null && location.length() > 100)
            throw new GroupCreationException ("Location must be between "+
                                              "0 and 100 characters in "+
                                              "length.");

        AppdefGroupValue gv = new AppdefGroupValue();
        gv.setName            ( name        );
        gv.setDescription     ( description );
        gv.setLocation        ( location    );
        gv.setGroupEntType    ( adType      );
        gv.setGroupEntResType ( adResType   );
        gv.setGroupType       ( gType       );

        GroupManagerLocal manager = getGroupManager();

        gv = (AppdefGroupValue) manager.createGroup(subject,gv);

        // Setup the group to contain a valid type object. 
        setGroupAppdefResourceType(subject,gv);

        // Setup any group visitors
        registerVisitors (gv);

        return gv;
    }

    /**
     * Lookup and return a group value object by its name.
     * @param Spider subject value.
     * @param groupName - the unique group name.
     * @throws AppdefGroupNotFoundException when group cannot be located in db.
     * @throws PermissionException if the caller is not authorized.
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public AppdefGroupValue findGroupByName(AuthzSubjectValue subject,
                                            String groupName)
        throws AppdefGroupNotFoundException, PermissionException 
    {
        return findGroup(subject, null, groupName, PageControl.PAGE_ALL);
    }

    /**
     * Lookup and return a group value object by its name.
     * @param groupName - the unique group name.
     * @param pc - page control
     * @return AppdefGroupValue object
     * @throws AppdefGroupNotFoundException when group cannot be located in db.
     * @throws PermissionException if the caller is not authorized.
     * @ejb:interface-method
     * @ejb:transaction type="NOTSUPPORTED"
     */
    public AppdefGroupValue findGroupByName(AuthzSubjectValue subject,
                                            String groupName, PageControl pc)
        throws AppdefGroupNotFoundException, PermissionException 
    {
        return findGroup(subject, null, groupName, pc);
    }

    /**
     * Lookup and return a group value object by its identifier.
     * 
     * @param Spider subject value.
     * @param Integer representing the group identifier
     * @return AppdefGroupValue object
     * @throw AppdefGroupNotFoundException when group cannot be found.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroup(AuthzSubjectValue subject, Integer id)
        throws AppdefGroupNotFoundException, PermissionException {
        return findGroup(subject, id, PageControl.PAGE_ALL);
    }

    /**
     * Lookup and return a group value object by its identifier.
     * 
     * @param Spider subject value.
     * @param Integer represetning group identifier
     * @param page control for group members.
     * @return AppdefGroupValue object
     * @throw AppdefGroupNotFoundException when group cannot be found.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroup(AuthzSubjectValue subject, Integer id,
                                      PageControl pc)
        throws AppdefGroupNotFoundException, PermissionException {
        AppdefEntityID aeid =
            new AppdefEntityID(AppdefEntityConstants.APPDEF_TYPE_GROUP, id);
        return findGroup(subject, aeid, pc);
    }

    /**
     * Lookup and return a group value object.
     * @param Spider subject value.
     * @param appdef entity id
     * @return AppdefGroupValue object
     * @ejb:interface-method
     * @throws AppdefGroupNotFoundException when group cannot be located in db.
     * @throws PermissionException if the caller is not authorized.
     */
    public AppdefGroupValue findGroup(AuthzSubjectValue subject,
                                      AppdefEntityID id)
        throws AppdefGroupNotFoundException, PermissionException {
        return findGroup(subject, id, PageControl.PAGE_ALL);
    }

    /**
     * Lookup and return a group value object.
     * @param Spider subject value.
     * @param appdef entity id
     * @param pc - page control
     * @return AppdefGroupValue object
     * @throws AppdefGroupNotFoundException when group cannot be located in db.
     * @throws PermissionException if the caller is not authorized.
     * @ejb:interface-method
     */
    public AppdefGroupValue findGroup(AuthzSubjectValue subject,
                                      AppdefEntityID id, PageControl pc)
        throws AppdefGroupNotFoundException, PermissionException {
        if (id.getType() != AppdefEntityConstants.APPDEF_TYPE_GROUP)
            throw new InvalidAppdefTypeException(
                "findGroup() requires a entity id of type group.");

        return findGroup(subject, id, null, pc);
    }

    /*
     * Lookup and return a group value object by either its name or identifier.
     */
    private AppdefGroupValue findGroup(AuthzSubjectValue subject,
                                       AppdefEntityID id,
                                       String groupName, PageControl pc)
        throws AppdefGroupNotFoundException, PermissionException
    {
        AppdefGroupValue retVal;

        try {
            GroupManagerLocal manager = getGroupManager();

            retVal = new AppdefGroupValue();

            // One of name or id is required to find the group.
            if (groupName != null) {
                retVal.setName(groupName);
            } else if (id != null) {
                retVal.setId(id.getId());
            } else {
                // One of either id or name is required..
                throw new AppdefGroupNotFoundException("Unable to find " +
                                                       "group.  No name or " +
                                                       "id specified.");
            }

            retVal = (AppdefGroupValue) manager.findGroup(subject, retVal, pc);

            // Check permission, making sure to generate an appdef id if only
            // a group name was passed in.
            if (id == null) {
                id = new AppdefEntityID(AppdefEntityConstants.APPDEF_TYPE_GROUP,
                                        retVal.getId());
            }

            checkPermission(subject, id,
                            AuthzConstants.groupOpViewResourceGroup);

            // Setup the group to contain a valid type object.
            setGroupAppdefResourceType(subject, retVal);
   
            // register any visitors
            registerVisitors(retVal);
        } catch (GroupNotFoundException e) {
            log.debug("findGroup() Unable to find group:" + id); 
            throw new AppdefGroupNotFoundException ("Unable to find group:",e);
        }

        return retVal;
    }

     /**
      * Fetch a group's members as a paged list of resource values. 
      *
      * Note: This method is expensive and unnecessary for most scenarios as
      * each group member's appdef value is looked up. Please USE SPARINGLY.
      * Use findGroup().getAppdefGroupEntries() for low cost alternative.
      *
      * @param subject - valid spider subject
      * @param gid     - group id
      * @param pc      - page control
      * @throws AppdefGroupNotFoundException  - non-existent group
      * @throws AppdefEntityNotFoundException - group member doesn't exist
      * @throws PermissionException           - unable to view group
      */
    public PageList getGroupMemberValues(AuthzSubjectValue subject,
                                         Integer gid, PageControl pc)
        throws AppdefGroupNotFoundException, AppdefEntityNotFoundException,
               PermissionException {
        PageList retVal = null;
        AppdefGroupValue groupVo = findGroup(subject,gid,pc);
        retVal = groupVo.getAppdefGroupEntries();

        // Replace each AppdefEntityID with an AppdefResourceValue
        for (int i = 0; i < retVal.size(); i++) {
            AppdefEntityID id = (AppdefEntityID) retVal.get(i);
            retVal.set(i, findById(subject, id));
        }
        return retVal;
    }

    // This is, in effect, a service cluster CRUD method that manages the
    // lifecycle of service clusters and their association with groups.
    // Groups of type COMPAT_SVC and Service Clusters are interdependent.
    // However, since their entities reside in disparate subsystems, there
    // are no physical constraints between them. This method realizes a
    // logical constraint.
    private void manageServiceCluster(AuthzSubjectValue subject,
                                      AppdefGroupValue gv) 
        throws CreateException, FinderException, RemoveException,
               PermissionException, AppSvcClustDuplicateAssignException,
               AppSvcClustIncompatSvcException {

        // Group/cluster pre-existed, user flushed group of all
        // members, Delete the cluster.
        if (gv.getClusterId() != CLUSTER_UNDEFINED && gv.getSize() == 0) {
            removeServiceCluster(subject, gv.getClusterId());
            gv.setClusterId( CLUSTER_UNDEFINED );
        } 
        // Create/Update (only if they've added something to the group)
        else if (gv.getSize()>0) {
            ServiceClusterValue clusterVo = new ServiceClusterValue();
            clusterVo.setName(gv.getName());
            clusterVo.setDescription(gv.getDescription());
            clusterVo.setGroupId(gv.getId());

            List svcList = new ArrayList();
            List grpMembers = gv.getAppdefGroupEntries();
            for (Iterator i=grpMembers.iterator(); i.hasNext();) {
                AppdefEntityID member = (AppdefEntityID)i.next();
                svcList.add(member.getId());
            }
            // Group/cluster doesn't exist, create it.
            if (gv.getClusterId() == CLUSTER_UNDEFINED)  {
                createServiceCluster(subject, gv, clusterVo, svcList);
            }
            // Group/cluster pre-existed, group has members, update
            else {
                updateServiceCluster(subject, gv, clusterVo, svcList);
            }
        }
    }

    private void createServiceCluster (AuthzSubjectValue subject,
        AppdefGroupValue gv, ServiceClusterValue clusterVo, List svcList)
        throws FinderException, AppSvcClustDuplicateAssignException,
               PermissionException, AppSvcClustIncompatSvcException {
        try {
            Integer pk =
                getServiceManager().createCluster(subject, clusterVo, svcList);
            gv.setClusterId(pk.intValue());
        } catch (CreateException e) {
            throw new SystemException(e);
        }
    }

    private void updateServiceCluster (AuthzSubjectValue subject,
        AppdefGroupValue gv, ServiceClusterValue clusterVo, List svcList)
        throws FinderException, AppSvcClustDuplicateAssignException,
               PermissionException, AppSvcClustIncompatSvcException {
        clusterVo.setId(new Integer(gv.getClusterId()));
        getServiceManager().updateCluster(subject, clusterVo, svcList);
    }

    private void removeServiceCluster (AuthzSubjectValue subject, int clusterId)
        throws RemoveException, FinderException, PermissionException {
        getServiceManager().removeCluster(subject, new Integer(clusterId));
    }

    /**
     * Save a group back to persistent storage.
     * @param AuthzSubject
     * @param appdef group vo
       @throws GroupNotCompatibleException - compat group contain incompt items
     * @throws GroupDuplicateNameException - group name already exists 
     * @throws AppSvcClustDuplicateAssignException - service already assigned
     * @throws PermissionException when authorization cannot be confirmed.
     * @throws GroupModificationException in all other odd occurances (i.e.
               ancillary resources can't be found)
     * @ejb:interface-method
     */
    public void saveGroup(AuthzSubjectValue subject, AppdefGroupValue gv)
        throws GroupNotCompatibleException, GroupModificationException, 
               GroupDuplicateNameException, AppSvcClustDuplicateAssignException, 
               PermissionException {
        try {

            // validate strictness and compatibility
            validateGroup (subject,gv);

            if (gv.getGroupType() == 
                    AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC) {
                manageServiceCluster(subject, gv);
            }
            GroupManagerLocal manager = getGroupManager();
            manager.saveGroup(subject, gv);
            
        } catch (AppSvcClustIncompatSvcException e) {
            log.error("Caught AppSvcClustIncompatSvcException exception "+
                      "creating cluster.");
            throw new GroupNotCompatibleException (
                    "Caught group not compatible exc creating cluster:" +
                    e.getMessage(), gv.getEntityId());
        } catch (CreateException e) {
            log.error("Caught ejb create exception creating cluster.");
            throw new GroupModificationException (
                    "Caught ejb create exc creating cluster", e);
        } catch (FinderException e) {
            log.error("Caught ejb finder exception updating cluster.");
            throw new GroupModificationException (
                    "Caught ejb finder exc updating cluster ", e);
        } catch (RemoveException e) {
            log.error("Caught ejb remove exception removing cluster.");
            throw new GroupModificationException (
                    "Caught ejb remove exc removing cluster ", e);
        } catch (AppdefEntityNotFoundException e) {
            log.error("Caught appdef entity not found exception " +
                      " removing cluster.");
            throw new GroupModificationException (
                    "Caught ejb remove exc removing cluster ", e);
        } catch (GroupModificationException e) {
            log.error("Caught group modification exception on save.");
            throw e;
        }
    }

    // Enforce group strictness and compatibility. This is no longer 
    // implemented as a visitor and therefore will only be performed
    // at time of group save.
    private void validateGroup (AuthzSubjectValue subject, AppdefGroupValue gv) 
        throws GroupNotCompatibleException, AppdefEntityNotFoundException, 
               PermissionException, GroupModificationException {

        if (gv.getId() == null) {
            throw new GroupModificationException(
                "Invalid group identifier [null]");
        }

        int groupType       = gv.getGroupType();
        int groupEntType    = gv.getGroupEntType();
        int groupEntResType = gv.getGroupEntResType();
        final int PLATFORM  = AppdefEntityConstants.APPDEF_TYPE_PLATFORM;
        final int SERVER    = AppdefEntityConstants.APPDEF_TYPE_SERVER;
        final int SERVICE   = AppdefEntityConstants.APPDEF_TYPE_SERVICE;
        final int APP       = AppdefEntityConstants.APPDEF_TYPE_APPLICATION;
        final int GROUP     = AppdefEntityConstants.APPDEF_TYPE_GROUP;
        final int GROUP_PSS = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_PSS;
        final int GROUP_APP = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_APP;
        final int GROUP_GRP = AppdefEntityConstants.APPDEF_TYPE_GROUP_ADHOC_GRP;

        // if compatible group, all members must be of same resource type.
        for (Iterator i = gv.getAppdefGroupEntries().iterator(); i.hasNext();) {
            AppdefEntityID member = (AppdefEntityID)i.next();
            if (gv.isGroupCompat()) { 
                AppdefResourceValue arv = findById(subject,member);
                if (arv.getEntityId().getType() != groupEntType ||
                    arv.getAppdefResourceTypeValue()
                        .getId().intValue() != groupEntResType) {
                    throw new GroupNotCompatibleException(
                        "Incompatible group member violation", member);
                }
            } else {
                if ( (groupType == GROUP_PSS &&(member.getType() != PLATFORM  &&
                                                member.getType() != SERVER    &&
                                                member.getType() != SERVICE)) ||
                     (groupType == GROUP_APP && member.getType() != APP) ||
                     (groupType == GROUP_GRP && member.getType() != GROUP)){
                    throw new GroupNotCompatibleException(
                        "Mixed group member type violation", member);
                }
            }
        }
    }

    /**
     * Produce a paged list of all groups where caller is authorized
     * to modify.
     * @param Spider subject value.
     * @param page control
     * @return List containing AppdefGroupValue.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public PageList findAllGroups(AuthzSubjectValue subject, PageControl pc)
        throws PermissionException {
        return findAllGroups(subject, (ResourceValue) null, pc, null);
    }

    /**
     * Produce list of all groups where caller is authorized
     * to modify. Apply filterSet to control group list membership.
     * @param Spider subject
     * @param page control
     * @param filter set for groups
     * @return PageList containing AppdefGroupValues.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public PageList findAllGroups(AuthzSubjectValue subject, PageControl pc,
                                  AppdefPagerFilter[] grpFilters)
        throws PermissionException {
        return findAllGroups(subject, (ResourceValue) null, pc, grpFilters);
    }

    /**
     * Produce list of all groups that contain the specified appdef entity.
     * Apply filterSet to control group list membership.
     *
     * @param Spider subject
     * @param AppdefentityID for inclusive search.
     * @param page control
     * @param filter set for groups
     * @return PageList containing AppdefGroupValues.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     * */
    public PageList findAllGroups(AuthzSubjectValue subject, 
        AppdefEntityID id, PageControl pc, AppdefPagerFilter[] grpFilters)
        throws PermissionException, ApplicationException {
        ResourceValue rv;

        try {
            rv = null;
            if (id != null) {
                rv = getResourceFromInstance( 
                    AppdefUtil.appdefTypeIdToAuthzTypeStr(id.getType()),
                    new Integer (id.getID()));
            } 
        }
        catch (FinderException fe) {
            // XXX - Temporary! Throw more appdef specific exception.
            // need a clean way to convert from authz to appdef type
            throw new ApplicationException("unable to find entity: "+
                                          id.toString(),fe);
        }
        return findAllGroups(subject,rv,pc,grpFilters);
    }

    private PageList findAllGroups(AuthzSubjectValue subject, 
        ResourceValue rv, PageControl pc, AppdefPagerFilter[] grpFilters)
        throws PermissionException {
        PageList retVal = null;

        try {
            GroupManagerLocal manager = getGroupManager();

            // create a valid appdef group vo for cloning.
            AppdefGroupValue gv = new AppdefGroupValue();

            if (pc == null)
                pc = new PageControl();

            PageList allGroups = manager.findAllGroups(subject, gv, rv,
                                                       PageControl.PAGE_ALL);
            
            log.debug("All groups size: " + allGroups.size());

            List toBePaged = new ArrayList();
            for (Iterator i=allGroups.iterator();i.hasNext();) {
                gv = (AppdefGroupValue)i.next();

                // Setup the group to contain a valid type object.
                setGroupAppdefResourceType(subject,gv);

                // register any visitors
                registerVisitors((AppdefGroupValue)gv);

                toBePaged.add(gv);
            }
            retVal = getPageList ( toBePaged, pc, grpFilters );

            if (log.isDebugEnabled()) {
                log.debug("Filtered groups size: " + retVal.size() +
                          " filter size: " +
                          (grpFilters == null ? 0 : grpFilters.length));
                if (grpFilters != null) {
                    for (int i = 0; i < grpFilters.length; i++) {
                        log.debug("Filter type: " +
                                  grpFilters[i].getClass().getName());                
                    }
                }
            }
        }
        catch (GroupNotFoundException gnf) {
            // Catch exception to return empty list.
            log.debug("Caught harmless GroupNotFound exception whilest looking "+
                "for groups [subject="+subject.toString()+"]- "+gnf.getMessage());
        }
        catch (CloneNotSupportedException e) {
            log.error("The group value object does not support cloning.",e);
            throw new SystemException ("Group value object doesn't support "+
                                          "cloning.",e);
        }
        if (retVal==null)
            retVal = new PageList();  // return empty list if no groups.

        return retVal;
    }

    /**
     * Produce list of all groups that contain the specified appdef entity.
     * Apply filterSet to control group list membership.
     *
     * @param Spider subject
     * @param AppdefentityID for inclusive search.
     * @param page control
     * @param filter set for groups
     * @return PageList containing AppdefGroupValues.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     * @ejb:transaction type="SUPPORTS"
     * */
    public Integer[] findClusterIds(AuthzSubjectValue subject, 
                                    AppdefEntityID id)
        throws PermissionException, ApplicationException {
        ResourceValue rv = null;
        try {
            rv = getResourceFromInstance( 
                AppdefUtil.appdefTypeIdToAuthzTypeStr(id.getType()),
                                                      id.getId());
        }
        catch (FinderException fe) {
            // XXX - Temporary! Throw more appdef specific exception.
            // need a clean way to convert from authz to appdef type
            throw new ApplicationException("unable to find entity: " + id, fe);
        }
        
        List grps = findAllGroups(subject, rv, PageControl.PAGE_ALL,
                                  new AppdefPagerFilter[0]);

        ArrayList clusterIds = new ArrayList(0);
        for (Iterator it = grps.iterator(); it.hasNext(); ) {
            AppdefGroupValue gv = (AppdefGroupValue) it.next();

            if (gv.getGroupType() == 
                AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC && 
                gv.getClusterId() != CLUSTER_UNDEFINED) {
                clusterIds.add(new Integer(gv.getClusterId()));
            }
        }
        return (Integer[]) clusterIds.toArray(new Integer[clusterIds.size()]);
    }
    
    // dbfetch the resource
    private ResourceValue getResourceFromInstance (String authzTypeStr, 
        Integer instanceId) throws FinderException {
        try {
            ResourceManagerLocal rmLoc = 
                ResourceManagerUtil.getLocalHome().create();
            return rmLoc.findResourceByInstanceId(rmLoc
                .findResourceTypeByName(authzTypeStr), instanceId);
        } catch (NamingException ne) {
            throw new SystemException(ne);
        } catch (CreateException ce) {
            throw new SystemException(ce);
        }
    }

    /**
     * Removes a group corresponding to the provided group id.
     * @param AuthzSubject value.
     * @param appdef entity id
     * @throw AppdefGroupNotFoundException when group cannot be found.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public void deleteGroup(AuthzSubjectValue subject, AppdefEntityID entityId)
        throws AppdefGroupNotFoundException, PermissionException {
        deleteGroup(subject, entityId.getId());
    }

    /**
     * Removes a group corresponding to the provided group id.
     * @param AuthzSubject value.
     * @param group id
     * @throw AppdefGroupNotFoundException when group cannot be found.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public void deleteGroup(AuthzSubjectValue subject, Integer groupId)
        throws AppdefGroupNotFoundException, PermissionException {
        try {
            GroupManagerLocal manager = getGroupManager();
            AppdefGroupValue gv = findGroup(subject,groupId);
            manager.deleteGroup(subject, groupId );

            if (gv.getGroupType() == 
                AppdefEntityConstants.APPDEF_TYPE_GROUP_COMPAT_SVC && 
                gv.getClusterId() != CLUSTER_UNDEFINED) {
                removeServiceCluster (subject,gv.getClusterId());
            }
        }
        catch (FinderException e) {
            // shouldn't happen, however, not fatal...
            log.error("Successfully removed group. But caught finder exc "+
                      "trying to remove it's service cluster",e);
        } catch (RemoveException e) {
            // shouldn't happen, however, not fatal...
            log.error("Successfully removed group. But caught remove exc "+
                      "trying to remove it's service cluster",e);
        } catch (GroupNotFoundException e) {
            log.debug("deleteGroup() caught group not found exc looking for:"+
                       groupId);
            throw new AppdefGroupNotFoundException ("caught group not " +
                        "found exc looking for:" + groupId);
        }
    }

    /**
     * Change group owner
     * @param subject value of caller.
     * @param group id to effect change.
     * @param the subject of the new owner.
     * @throw AppdefGroupNotFoundException when group cannot be found.
     * @throw PermissionException when group access is not authorized.
     * @ejb:interface-method
     */
    public AppdefGroupValue changeGroupOwner(AuthzSubjectValue subject,
        Integer groupId, AuthzSubjectValue newOwner)
        throws AppdefGroupNotFoundException, PermissionException {
        try {
            GroupManagerLocal manager = getGroupManager();

            // first lookup the group to test permissions
            AppdefGroupValue agv;
            agv = this.findGroup(subject, groupId);

            // now change the owner.
            manager.changeGroupOwner(subject, agv, newOwner);

            return agv;
        }
        catch (GroupNotFoundException e) {
            log.error("Caught group not found exc looking for:" + groupId);
            throw new AppdefGroupNotFoundException ("Caught group not " +
                        "found exc looking for:" + groupId);
        }
    }

    /** Internal helper methods below **/
    // Page out the collection, applying any filters in the process.
    private PageList getPageList (Collection coll, PageControl pc,
        AppdefPagerFilter[] filters) {
        PageList retVal;
        Pager pager;

        pc = PageControl.initDefaults(pc,SortAttribute.RESOURCE_NAME);
        try {
            pager = Pager.getPager( APPDEF_PAGER_PROCESSOR );
        }
        catch (InstantiationException e) {
            log.debug("InstantiationException caught instantiating " +
                      APPDEF_PAGER_PROCESSOR);
            throw new SystemException (e.getMessage());
        }
        catch (IllegalAccessException e) {
            log.debug("IllegalAccessException caught instantiating " +
                      APPDEF_PAGER_PROCESSOR);
            throw new SystemException (e.getMessage());
        }
        catch (ClassNotFoundException e) {
            log.debug("ClassNotFoundException caught instantiating " +
                      APPDEF_PAGER_PROCESSOR);
            throw new SystemException (e.getMessage());
        }
        retVal = pager.seek(coll, pc.getPagenum(),pc.getPagesize(), filters);
 
        int adj = 0; // keep track of number filtered for offsetting
        if (filters != null) {
            for (int i = 0; i < filters.length; i++) {
                adj += filters[i].getFilterCount(); 
            }
        }
        retVal.setTotalSize(retVal.getTotalSize() - adj);

        return retVal;
    }

    /* This had to be adopted from appdef boss's similar version. This will
     * eventually be available from a new EJB that will represent all of the
     * appdef entities agnostically.*/
    private AppdefResourceValue findById(AuthzSubjectValue subject,
                                         AppdefEntityID entityId)
        throws AppdefEntityNotFoundException, PermissionException {

        PlatformManagerLocal    platformManagerLocal    = null;
        ServerManagerLocal      serverManagerLocal      = null;
        ApplicationManagerLocal appManagerLocal         = null;

        try {
            switch (entityId.getType()) {
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM: 
                    platformManagerLocal =
                        PlatformManagerUtil.getLocalHome().create();
                    return platformManagerLocal.getPlatformById(
                            subject, entityId.getId());
                case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                    serverManagerLocal =
                        ServerManagerUtil.getLocalHome().create();
                    return serverManagerLocal.getServerById(
                            subject, entityId.getId());
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                    return getServiceManager().getServiceById(
                            subject, entityId.getId());
                case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                    appManagerLocal =
                        ApplicationManagerUtil.getLocalHome().create();
                    return appManagerLocal.getApplicationById(
                            subject, entityId.getId());
                case AppdefEntityConstants.APPDEF_TYPE_GROUP:
                    try {
                        return findGroup(subject, entityId.getId());
                    }
                    catch (AppdefGroupNotFoundException e) {
                        log.debug("getById() failed to find specified group.");
                        throw e;
                    }
                default:
                    throw new InvalidAppdefTypeException (entityId.getType()
                            + " is not a valid appdef entity type");
            }
        } catch (NamingException e) {
            throw new SystemException(e);
        } catch (CreateException e) {
            throw new SystemException(e);
        }
    }

    // Register any visitors for a group value.
    private void registerVisitors(AppdefGroupValue gv) {
        // XXX - forthcoming, required reworking to 
        // handle new group types and subtypes.
    }

    // All UIs will require a group to express a valid type (encapsulated by a
    // concrete decendent of AppdefResourceTypeValue).  However, since a group
    // is not an actual appdef entity, we set the group up as a surrogate
    // for either a dummy type (GroupTypeValue) or a copy of one of its
    // member's type objects.
    private void setGroupAppdefResourceType(AuthzSubjectValue subject,
                                            AppdefGroupValue gv) {

        try {
            if (gv.isGroupCompat()) {
                gv.setAppdefResourceTypeValue(
                    getResourceTypeById(gv.getGroupEntType(),
                                        gv.getGroupEntResType()));
            }
            else {
                AppdefResourceTypeValue tvo = new GroupTypeValue();
                tvo.setId ( new Integer(gv.getGroupType()) );
                tvo.setName( AppdefEntityConstants.getAppdefGroupTypeName(
                    gv.getGroupType()) );
                gv.setAppdefResourceTypeValue( tvo );
            }
        }
        catch (FinderException e) {
            // this is not a fatal error
            if (log.isDebugEnabled())
                log.debug("Caught exception setting group resource type value.",
                          e);
        } catch (AppdefEntityNotFoundException e) {
            // this is not a fatal error
            if (log.isDebugEnabled())
                log.debug("Caught exception setting group resource type value.",
                          e);
        }
    }

    private AppdefResourceTypeValue getResourceTypeById (int type, int id)
        throws FinderException, AppdefEntityNotFoundException {
        switch (type) {
            case (AppdefEntityConstants.APPDEF_TYPE_PLATFORM) :
                return getPlatformTypeById(id);
            case (AppdefEntityConstants.APPDEF_TYPE_SERVER) :
                return getServerTypeById(id);
            case (AppdefEntityConstants.APPDEF_TYPE_SERVICE) :
                return getServiceTypeById(id);
            case (AppdefEntityConstants.APPDEF_TYPE_APPLICATION) :
                return getApplicationTypeById(id);
            default:
                throw new IllegalArgumentException ("Invalid resource type:"
                                                    +type);
        }
    }

    private PlatformTypeValue getPlatformTypeById (int id)
        throws PlatformNotFoundException {
        PlatformManagerLocal platLoc;
        try {
            platLoc = PlatformManagerUtil.getLocalHome().create();
        } catch (CreateException e) {
            throw new SystemException(e);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        return platLoc.findPlatformTypeById(new Integer(id));
    }

    private ServerTypeValue getServerTypeById (int id)
        throws FinderException {
        ServerManagerLocal servLoc;
        try {
            servLoc = ServerManagerUtil.getLocalHome().create();
        } catch (CreateException e) {
            throw new SystemException(e);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        return servLoc.findServerTypeById(new Integer(id));
    }

    private ServiceTypeValue getServiceTypeById(int id) throws FinderException {
        return getServiceManager().findServiceTypeById(new Integer(id));
    }

    private ApplicationTypeValue getApplicationTypeById (int id)
        throws FinderException {
        ApplicationManagerLocal appLoc;
        try {
            appLoc = ApplicationManagerUtil.getLocalHome().create();
        } catch (CreateException e) {
            throw new SystemException(e);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        return appLoc.findApplicationTypeById(new Integer(id));
    }

    // ******* EJB lifecycle junk ...
    /**
     * @see javax.ejb.SessionBean#ejbCreate()
     * @ejb:create-method
     * @throws javax.ejb.CreateException
     */
    public void ejbCreate() throws CreateException {
    }

    /**
     * @see javax.ejb.SessionBean#ejbPostCreate()
     */
    public void ejbPostCreate() {}

    /**
     * @see javax.ejb.SessionBean#ejbActivate()
     */
    public void ejbActivate() {}

    /**
     * @see javax.ejb.SessionBean#ejbPassivate()
     */
    public void ejbPassivate() {}

    /**
     * @see javax.ejb.SessionBean#ejbRemove()
     */
    public void ejbRemove() {}
}
