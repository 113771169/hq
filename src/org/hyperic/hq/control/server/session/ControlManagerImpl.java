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

package org.hyperic.hq.control.server.session;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.FinderException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.ObjectNotFoundException;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.appdef.ConfigResponseDB;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityTypeID;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.ApplicationManagerLocal;
import org.hyperic.hq.appdef.shared.ConfigFetchException;
import org.hyperic.hq.appdef.shared.ConfigManagerLocal;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceTypeDAO;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.util.Messenger;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.control.ControlEvent;
import org.hyperic.hq.control.agent.client.ControlCommandsClient;
import org.hyperic.hq.control.agent.client.ControlCommandsClientFactory;
import org.hyperic.hq.control.shared.ControlConstants;
import org.hyperic.hq.control.shared.ControlManager;
import org.hyperic.hq.control.shared.ControlScheduleManager;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.grouping.server.session.GroupUtil;
import org.hyperic.hq.grouping.shared.GroupNotCompatibleException;
import org.hyperic.hq.product.ControlPluginManager;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.PluginNotFoundException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.hq.product.shared.ProductManager;
import org.hyperic.hq.scheduler.ScheduleValue;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.EncodingException;
import org.hyperic.util.pager.PageControl;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server-side control system.
 */
@Service
@Transactional
public class ControlManagerImpl implements ControlManager {

    private final Log log = LogFactory.getLog(ControlManagerImpl.class.getName());

    private ProductManager productManager;
    private ControlScheduleManager controlScheduleManager;
    private ControlHistoryDAO controlHistoryDao;
    private ResourceTypeDAO resourceTypeDao;

    private ConfigManagerLocal configManager;
    private PlatformManagerLocal platformManager;
    private AuthzSubjectManager authzSubjectManager;
    private ServerManagerLocal serverManager;
    private ServiceManagerLocal serviceManager;
    private ApplicationManagerLocal applicationManager;

    private ControlPluginManager controlPluginManager;

    @Autowired
    public ControlManagerImpl(ProductManager productManager, ControlScheduleManager controlScheduleManager,
                              ControlHistoryDAO controlHistoryDao,
                              ResourceTypeDAO resourceTypeDao, ConfigManagerLocal configManager,
                              PlatformManagerLocal platformManager,
                              AuthzSubjectManager authzSubjectManager, ServerManagerLocal serverManager,
                              ServiceManagerLocal serviceManager,
                              ApplicationManagerLocal applicationManager) {
        this.productManager = productManager;
        this.controlScheduleManager = controlScheduleManager;
        this.controlHistoryDao = controlHistoryDao;
        this.resourceTypeDao = resourceTypeDao;
        this.configManager = configManager;
        this.platformManager = platformManager;
        this.authzSubjectManager = authzSubjectManager;
        this.serverManager = serverManager;
        this.serviceManager = serviceManager;
        this.applicationManager = applicationManager;
    }

    @PostConstruct
    public void createControlPluginManager() {
        // Get reference to the control plugin manager
        try {
            controlPluginManager = (ControlPluginManager) productManager.getPluginManager(ProductPlugin.TYPE_CONTROL);
        } catch (Exception e) {
            this.log.error("Unable to get plugin manager", e);
        }
    }

    /**
     * Enable an entity for control
     **/
    public void configureControlPlugin(AuthzSubject subject, AppdefEntityID id)
        throws PermissionException, PluginException, ConfigFetchException,
        AppdefEntityNotFoundException, AgentNotFoundException {
        // authz check
        checkModifyPermission(subject, id);

        String pluginName, pluginType;
        ConfigResponse mergedResponse;

        pluginName = id.toString();

        try {
            pluginType = platformManager.getPlatformPluginName(id);
            mergedResponse = configManager.getMergedConfigResponse(subject,
                                                                   ProductPlugin.TYPE_CONTROL,
                                                                   id, true);

            ControlCommandsClient client = ControlCommandsClientFactory.getInstance().getClient(id);
            client.controlPluginAdd(pluginName, pluginType, mergedResponse);
        } catch (EncodingException e) {
            throw new PluginException("Unable to decode config", e);
        } catch (AgentConnectionException e) {
            throw new PluginException("Agent error: " + e.getMessage(), e);
        } catch (AgentRemoteException e) {
            throw new PluginException("Agent error: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a single control action on a given entity.
     */
    public void doAction(AuthzSubject subject, AppdefEntityID id,
                         String action, String args)
        throws PluginException, PermissionException {
        // This method doesn't support groups.
        if (id.isGroup()) {
            throw new IllegalArgumentException("Cannot perform single " +
                                               "action on a group.");
        }

        checkControlEnabled(subject, id);
        checkControlPermission(subject, id);

        controlScheduleManager.doSingleAction(id, subject, action, args, null);
    }

    /**
     * Execute a single control action on a given entity.
     */
    public void doAction(AuthzSubject subject, AppdefEntityID id,
                         String action) throws PluginException, PermissionException {
        String args = null;
        doAction(subject, id, action, args);
    }

    /**
     * Schedule a new control action.
     */
    public void doAction(AuthzSubject subject, AppdefEntityID id,
                         String action, ScheduleValue schedule)
        throws PluginException, PermissionException, SchedulerException {
        // This method doesn't support groups.
        if (id.isGroup()) {
            throw new IllegalArgumentException("Cannot perform single " +
                                               "action on a group.");
        }

        checkControlEnabled(subject, id);
        checkControlPermission(subject, id);

        controlScheduleManager.doScheduledAction(id, subject, action,
                                                 schedule, null);
    }

    /**
     * Single control action for a group of given entities.
     */
    public void doGroupAction(AuthzSubject subject,
                              AppdefEntityID id, String action,
                              String args, int[] order)
        throws PluginException, PermissionException,
               AppdefEntityNotFoundException, GroupNotCompatibleException {
        List<AppdefEntityID> groupMembers = GroupUtil.getCompatGroupMembers(subject, id,
                                                                            order,
                                                                            PageControl.PAGE_ALL);

        // For each entity in the list, sanity check config and permissions
        for (AppdefEntityID entity : groupMembers) {
            checkControlEnabled(subject, entity);
            checkControlPermission(subject, entity);
        }

        controlScheduleManager.doSingleAction(id, subject, action,
                                              args, order);
    }

    /**
     * Schedule a single control action for a group of given entities.
     * @throws SchedulerException
     */
    public void doGroupAction(AuthzSubject subject, AppdefEntityID id,
                              String action, int[] order, ScheduleValue schedule)
        throws PluginException, PermissionException, SchedulerException,
        GroupNotCompatibleException, AppdefEntityNotFoundException {
        List<AppdefEntityID> groupMembers = GroupUtil.getCompatGroupMembers(subject, id, order, PageControl.PAGE_ALL);

        // For each entity in the list, sanity check config and permissions
        for (AppdefEntityID entity : groupMembers) {
            checkControlEnabled(subject, entity);
            checkControlPermission(subject, entity);
        }

        controlScheduleManager.doScheduledAction(id, subject, action, schedule, order);
    }

    /**
     * Get the supported actions for an appdef entity from the local
     * ControlPluginManager
     */
    public List<String> getActions(AuthzSubject subject, AppdefEntityID id)
        throws PermissionException, PluginNotFoundException,
        AppdefEntityNotFoundException, GroupNotCompatibleException {
        if (id.isGroup()) {
            List<AppdefEntityID> groupMembers = GroupUtil.getCompatGroupMembers(subject, id, null, PageControl.PAGE_ALL);

            // For each entity in the list, sanity check permissions
            for (AppdefEntityID entity : groupMembers) {
                checkControlPermission(subject, entity);
            }
        } else {
            checkControlPermission(subject, id);
        }

        String pluginName = platformManager.getPlatformPluginName(id);
        return controlPluginManager.getActions(pluginName);
    }

    /**
     * Get the supported actions for an appdef entity from the local
     * ControlPluginManager
     */
    public List<String> getActions(AuthzSubject subject, AppdefEntityTypeID aetid)
        throws PluginNotFoundException {
        String pluginName = aetid.getAppdefResourceType().getName();
        return controlPluginManager.getActions(pluginName);
    }

    /**
     * Check if a compatible group's members have been enabled for control.
     * A group is enabled for control if and only if all of its members
     * have been enabled for control.
     * @return flag - true if group is enabled
     */
    public boolean isGroupControlEnabled(AuthzSubject subject,
                                         AppdefEntityID id)
        throws AppdefEntityNotFoundException, PermissionException {
        if (!id.isGroup()) {
            throw new IllegalArgumentException("Expecting entity of type " +
                                               "group.");
        }

        List<AppdefEntityID> members;

        try {
            members = GroupUtil.getCompatGroupMembers(subject, id, null);
        } catch (GroupNotCompatibleException ex) {
            // only compatible groups are controllable
            return false;
        }

        if (members.isEmpty()) {
            return false;
        }

        for (AppdefEntityID member : members) {
            try {
                checkControlEnabled(subject, member);
                return true;
            } catch (PluginException e) {
                // continue
            }
        }
        return false;
    }

    /**
     * Checks with the plugin manager to find out if an entity's
     * resource provides support for control.
     * @param resType - appdef entity (of all kinds inc. groups)
     * @return flag - true if supported
     */
    public boolean isControlSupported(AuthzSubject subject, String resType) {
        try {
            controlPluginManager.getPlugin(resType);
            return true;
        } catch (PluginNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks with the plugin manager to find out if an entity's
     * resource provides support for control.
     * @param resType - appdef entity (of all kinds inc. groups)
     * @return flag - true if supported
     */
    public boolean isControlSupported(AuthzSubject subject, AppdefEntityID id,
                                      String resType) {
        try {
            if (id.isGroup()) {
                List<AppdefEntityID> members = GroupUtil.getCompatGroupMembers(subject, id, null);

                if (members.isEmpty()) {
                    return false;
                }

                checkControlPermission(subject, (AppdefEntityID) members.get(0));
            } else {
                checkControlPermission(subject, id);
            }
            controlPluginManager.getPlugin(resType);
            return true;
        } catch (PluginNotFoundException e) {
            // return false
        } catch (PermissionException e) {
            // return false
        } catch (AppdefEntityNotFoundException e) {
            // return false
        } catch (GroupNotCompatibleException e) {
            // return false
        }
        return false;
    }

    /**
     * Check if a an entity has been enabled for control.
     * @return flag - true if enabled
     */
    public boolean isControlEnabled(AuthzSubject subject, AppdefEntityID id) {
        try {
            checkControlEnabled(subject, id);
            return true;
        } catch (PluginException e) {
            return false;
        }
    }

    /**
     * Check if an entity has been enabled for control
     */
    public void checkControlEnabled(AuthzSubject subject, AppdefEntityID id)
        throws PluginException {
        ConfigResponseDB config;

        try {
            config = configManager.getConfigResponse(id);
        } catch (Exception e) {
            throw new PluginException(e);
        }

        if (config == null || config.getControlResponse() == null) {
            throw new PluginException("Control not " +
                                      "configured for " + id);
        }
    }

    /**
     * Get the control config response
     */
    public ConfigResponse getConfigResponse(AuthzSubject subject,
                                            AppdefEntityID id)
        throws PluginException {
        ConfigResponseDB config;
        try {
            config = configManager.getConfigResponse(id);
        } catch (Exception e) {
            throw new PluginException(e);
        }

        if (config == null || config.getControlResponse() == null) {
            throw new PluginException("Control not " +
                                      "configured for " + id);
        }

        byte[] controlResponse = config.getControlResponse();
        ConfigResponse configResponse;
        try {
            configResponse = ConfigResponse.decode(controlResponse);
        } catch (Exception e) {
            throw new PluginException("Unable to decode configuration");
        }

        return configResponse;
    }

    // Remote EJB methods for use by agents.

    /**
     * Send an agent a plugin configuration. This is needed when agents
     * restart, since they do not persist control plugin configuration.
     * 
     * @param pluginName Name of the plugin to get the config for
     * @param merge If true, merge the product and control config data
     */
    public byte[] getPluginConfiguration(String pluginName, boolean merge)
        throws PluginException {
        try {
            AppdefEntityID id = new AppdefEntityID(pluginName);

            AuthzSubject overlord = authzSubjectManager.getOverlordPojo();

            ConfigResponse config = configManager.getMergedConfigResponse(overlord,
                                                                          ProductPlugin.TYPE_CONTROL,
                                                                          id, merge);

            return config.encode();
        } catch (Exception e) {
            // XXX: Could be a bit more specific here when catching
            // exceptions, but ideally this should always
            // succeed since the agent knows when to pull the
            // config.
            throw new PluginException("Unable to get plugin configuration: " +
                                      e.getMessage());
        }
    }

    /**
     * Receive status information about a previous control action
     */
    public void sendCommandResult(int id, int result, long startTime,
                                  long endTime, String message) {
        String status;
        if (result == 0) {
            status = ControlConstants.STATUS_COMPLETED;
        } else {
            status = ControlConstants.STATUS_FAILED;
        }

        String msg;
        if (message != null && message.length() > 500) {
            // Show last 500 characters from the command output
            msg = message.substring(message.length() - 500);
        } else {
            msg = message;
        }

        // Update the control history
        ControlHistory cLocal;
        try {

            Integer pk = new Integer(id);
            cLocal = controlHistoryDao.findById(pk);
        } catch (ObjectNotFoundException e) {
            // We know the ID, this won't happen
            throw new SystemException(e);
        }

        cLocal.setStatus(status);
        cLocal.setStartTime(startTime);
        cLocal.setEndTime(endTime);
        cLocal.setMessage(msg);

        // Send a control event
        ControlEvent event =
                             new ControlEvent(cLocal.getSubject(),
                                              cLocal.getEntityType().intValue(),
                                              cLocal.getEntityId(),
                                              cLocal.getAction(),
                                              cLocal.getScheduled().booleanValue(),
                                              cLocal.getDateScheduled(),
                                              status);
        event.setMessage(msg);
        Messenger sender = new Messenger();
        sender.publishMessage(EventConstants.EVENTS_TOPIC, event);
    }

    /**
     * Accept an array of appdef entity Ids and verify control permission
     * on each entity for specified subject. Return only the set of entities
     * that have authorization.
     * 
     * @return List of entities subject is authz to control
     *         NOTE: Returns an empty list when no resources are found.
     */
    public List<AppdefEntityID> batchCheckControlPermissions(AuthzSubject caller,
                                                             AppdefEntityID[] entities)
        throws AppdefEntityNotFoundException, PermissionException {
        return doBatchCheckControlPermissions(caller, entities);
    }

    protected List<AppdefEntityID> doBatchCheckControlPermissions(AuthzSubject caller,
                                                                  AppdefEntityID[] entities)
        throws AppdefEntityNotFoundException, PermissionException {
        List<ResourceValue> resList = new ArrayList<ResourceValue>();
        List<String> opList = new ArrayList<String>();
        List<AppdefEntityID> retVal = new ArrayList<AppdefEntityID>();
        ResourceValue[] resArr;
        String[] opArr;

        // package up the args for verification
        for (AppdefEntityID entity : entities) {

            // Special case groups. If the group is compatible,
            // pull the members and check each of them. According
            // to Moseley, if any member of a group is control unauthz
            // then the entire group is unauthz.
            if (entity.isGroup()) {
                if (isGroupControlEnabled(caller, entity)) {
                    retVal.add(entity);
                }
                continue;
            }
            // Build up the arguments -- operation name array correlated
            // with resource (i.e. type specific operation names)
            opList.add(getControlPermissionByType(entity));
            ResourceValue rv = new ResourceValue();
            rv.setInstanceId(entity.getId());
            rv.setResourceType(resourceTypeDao.findByName(
                                              AppdefUtil.appdefTypeIdToAuthzTypeStr(entity.getType())));
            resList.add(rv);
        }
        if (resList.size() > 0) {
            opArr = (String[]) opList.toArray(new String[0]);
            resArr = (ResourceValue[]) resList.toArray(new ResourceValue[0]);

            // fetch authz resources and add to return list
            try {
                PermissionManager pm = PermissionManagerFactory.getInstance();
                Resource[] authz =
                                   pm.findOperationScopeBySubjectBatch(caller, resArr, opArr);
                for (int x = 0; x < authz.length; x++) {
                    retVal.add(new AppdefEntityID(authz[x]));
                }
            } catch (FinderException e) {
                // returns empty list as advertised
            }
        }
        return retVal;
    }

    // Authz Helper Methods

    /**
     * Check control modify permission for an appdef entity
     * Control Modify ops are treated as regular modify operations
     */
    protected void checkModifyPermission(AuthzSubject caller,
                                         AppdefEntityID id)
        throws PermissionException {
        int type = id.getType();
        switch (type) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                platformManager.checkModifyPermission(caller, id);
                return;
            case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                serverManager.checkModifyPermission(caller, id);
                return;
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                serviceManager.checkModifyPermission(caller, id);
                return;
            case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                applicationManager.checkModifyPermission(caller, id);
                return;
            default:
                throw new InvalidAppdefTypeException("Unknown type: " + type);
        }
    }

    /** Check control permission for an appdef entity */
    protected void checkControlPermission(AuthzSubject caller,
                                          AppdefEntityID id)
        throws PermissionException {
        int type = id.getType();
        switch (type) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                platformManager.checkControlPermission(caller, id);
                return;
            case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                serverManager.checkControlPermission(caller, id);
                return;
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                serviceManager.checkControlPermission(caller, id);
                return;
            case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                applicationManager.checkControlPermission(caller, id);
                return;
            default:
                throw new InvalidAppdefTypeException("Unknown type: " + type);
        }
    }

    // Lookup the appropriate control permission based on entity type.
    // Groups are fetched and appropriate type is returned.
    private String getControlPermissionByType(AppdefEntityID id) {
        switch (id.getType()) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                return AuthzConstants.platformOpControlPlatform;
            case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                return AuthzConstants.serverOpControlServer;
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                return AuthzConstants.serviceOpControlService;
            case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                return AuthzConstants.appOpControlApplication;
            default:
                throw new IllegalArgumentException("Invalid appdef type:" +
                                                   id.getType());
        }
    }

    public static ControlManager getOne() {
        return Bootstrap.getBean(ControlManager.class);
    }
}
