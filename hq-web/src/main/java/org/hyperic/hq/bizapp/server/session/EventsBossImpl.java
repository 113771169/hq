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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hibernate.PageInfo;
import org.hyperic.hq.appdef.server.session.AppdefResourceType;
import org.hyperic.hq.appdef.server.session.ResourceDeletedZevent;
import org.hyperic.hq.appdef.server.session.ResourceZevent;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityTypeID;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.PlatformManager;
import org.hyperic.hq.appdef.shared.ServerManager;
import org.hyperic.hq.appdef.shared.ServiceManager;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.auth.shared.AuthManager;
import org.hyperic.hq.auth.shared.SessionException;
import org.hyperic.hq.auth.shared.SessionManager;
import org.hyperic.hq.auth.shared.SessionNotFoundException;
import org.hyperic.hq.auth.shared.SessionTimeoutException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceDeleteCallback;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.authz.server.session.SubjectRemoveCallback;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectManager;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.authz.shared.ResourceGroupManager;
import org.hyperic.hq.authz.shared.ResourceManager;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.AuthBoss;
import org.hyperic.hq.bizapp.shared.EventsBoss;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.DuplicateObjectException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.VetoException;
import org.hyperic.hq.context.Bootstrap;
import org.hyperic.hq.escalation.server.session.Escalatable;
import org.hyperic.hq.escalation.server.session.Escalation;
import org.hyperic.hq.escalation.server.session.EscalationAlertType;
import org.hyperic.hq.escalation.server.session.EscalationState;
import org.hyperic.hq.escalation.server.session.PerformsEscalations;
import org.hyperic.hq.escalation.shared.EscalationManager;
import org.hyperic.hq.events.ActionConfigInterface;
import org.hyperic.hq.events.ActionCreateException;
import org.hyperic.hq.events.ActionExecuteException;
import org.hyperic.hq.events.ActionInterface;
import org.hyperic.hq.events.AlertConditionCreateException;
import org.hyperic.hq.events.AlertDefinitionCreateException;
import org.hyperic.hq.events.AlertDefinitionInterface;
import org.hyperic.hq.events.AlertInterface;
import org.hyperic.hq.events.AlertNotFoundException;
import org.hyperic.hq.events.AlertSeverity;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.events.MaintenanceEvent;
import org.hyperic.hq.events.TriggerCreateException;
import org.hyperic.hq.events.ext.RegisterableTriggerInterface;
import org.hyperic.hq.events.server.session.Action;
import org.hyperic.hq.events.server.session.Alert;
import org.hyperic.hq.events.server.session.AlertDefinition;
import org.hyperic.hq.events.server.session.AlertSortField;
import org.hyperic.hq.events.server.session.ClassicEscalationAlertType;
import org.hyperic.hq.events.shared.ActionManager;
import org.hyperic.hq.events.shared.ActionValue;
import org.hyperic.hq.events.shared.AlertConditionValue;
import org.hyperic.hq.events.shared.AlertDefinitionManager;
import org.hyperic.hq.events.shared.AlertDefinitionValue;
import org.hyperic.hq.events.shared.AlertManager;
import org.hyperic.hq.events.shared.MaintenanceEventManager;
import org.hyperic.hq.events.shared.RegisteredTriggerManager;
import org.hyperic.hq.events.shared.RegisteredTriggerValue;
import org.hyperic.hq.galerts.server.session.GalertDef;
import org.hyperic.hq.galerts.server.session.GalertEscalationAlertType;
import org.hyperic.hq.galerts.server.session.GalertLogSortField;
import org.hyperic.hq.galerts.shared.GalertManager;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.hq.measurement.action.MetricAlertAction;
import org.hyperic.hq.measurement.server.session.DefaultMetricEnableCallback;
import org.hyperic.hq.measurement.server.session.Measurement;
import org.hyperic.hq.measurement.shared.MeasurementManager;
import org.hyperic.hq.zevents.ZeventEnqueuer;
import org.hyperic.hq.zevents.ZeventListener;
import org.hyperic.util.ConfigPropertyException;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.ConfigSchema;
import org.hyperic.util.config.EncodingException;
import org.hyperic.util.config.InvalidOptionException;
import org.hyperic.util.config.InvalidOptionValueException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.SortAttribute;
import org.hyperic.util.timer.StopWatch;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The BizApp's interface to the Events Subsystem
 */
@Service
@Transactional
public class EventsBossImpl implements EventsBoss {
    private Log log = LogFactory.getLog(EventsBossImpl.class);

    private static final String BUNDLE = "org.hyperic.hq.bizapp.Resources";

    private SessionManager sessionManager;

    private ActionManager actionManager;

    private AlertDefinitionManager alertDefinitionManager;

    private AlertManager alertManager;

    private AppdefBoss appdefBoss;

    private AuthBoss authBoss;

    private EscalationManager escalationManager;

    private MeasurementManager measurementManager;

    private PlatformManager platformManager;

    private RegisteredTriggerManager registeredTriggerManager;

    private ResourceManager resourceManager;

    private ServerManager serverManager;

    private ServiceManager serviceManager;

    private PermissionManager permissionManager;

    private GalertManager galertManager;

    private ResourceGroupManager resourceGroupManager;

    private AuthzSubjectManager authzSubjectManager;
    
    private HQApp app;
    
    private ZeventEnqueuer zEventManager;

    @Autowired
    public EventsBossImpl(SessionManager sessionManager, ActionManager actionManager,
                          AlertDefinitionManager alertDefinitionManager, AlertManager alertManager,
                          AppdefBoss appdefBoss, AuthBoss authBoss,
                          EscalationManager escalationManager, MeasurementManager measurementManager,
                          PlatformManager platformManager, RegisteredTriggerManager registeredTriggerManager,
                          ResourceManager resourceManager, ServerManager serverManager,
                          ServiceManager serviceManager, PermissionManager permissionManager,
                          GalertManager galertManager, ResourceGroupManager resourceGroupManager,
                          AuthzSubjectManager authzSubjectManager, HQApp app, ZeventEnqueuer zEventManager) {
        this.sessionManager = sessionManager;
        this.actionManager = actionManager;
        this.alertDefinitionManager = alertDefinitionManager;
        this.alertManager = alertManager;
        this.appdefBoss = appdefBoss;
        this.authBoss = authBoss;
        this.escalationManager = escalationManager;
        this.measurementManager = measurementManager;
        this.platformManager = platformManager;
        this.registeredTriggerManager = registeredTriggerManager;
        this.resourceManager = resourceManager;
        this.serverManager = serverManager;
        this.serviceManager = serviceManager;
        this.permissionManager = permissionManager;
        this.galertManager = galertManager;
        this.resourceGroupManager = resourceGroupManager;
        this.authzSubjectManager = authzSubjectManager;
        this.app = app;
        this.zEventManager = zEventManager;
    }

    /**
     * TODO possibly find another way to return the correct impl of interface if
     * in HQ or HQ EE. For now, this has to be lazy b/c using SPEL
     * permissionManager.maintenanceEventManager causes the
     * Bootstrap.getBean(MaintenanceEventManager) to be invoked during creation
     * of ejb-context.xml (which doesn't work b/c Boostrap.context is still set
     * to dao-context.xml)
     * @return
     */
    private MaintenanceEventManager getMaintenanceEventManager() {
        return permissionManager.getMaintenanceEventManager();
    }

    /*
     * How the Boss figures out which triggers to create based on conditions
     */
    private void createTriggers(AuthzSubject subject, AlertDefinitionValue alertdef) throws TriggerCreateException,
        InvalidOptionException, InvalidOptionValueException {
        registeredTriggerManager.createTriggers(subject, alertdef);
    }

    /**
     * Clone the parent conditions into the alert definition.
     * 
     * @param subject The subject.
     * @param id The entity to which the alert definition is assigned.
     * @param adval The alert definition where the cloned conditions are set.
     * @param conds The parent conditions to clone.
     * @param failSilently <code>true</code> fail silently if cloning fails
     *        because no measurement is found corresponding to the measurement
     *        template specified in a parent condition; <code>false</code> to
     *        throw a {@link MeasurementNotFoundException} when this occurs.
     * @return <code>true</code> if cloning succeeded; <code>false</code> if
     *         cloning failed.
     */
    private boolean cloneParentConditions(AuthzSubject subject, AppdefEntityID id, AlertDefinitionValue adval,
                                          AlertConditionValue[] conds, boolean failSilently)
        throws MeasurementNotFoundException {
        // scrub and copy the parent's conditions
        adval.removeAllConditions();

        for (int i = 0; i < conds.length; i++) {
            AlertConditionValue clone = new AlertConditionValue(conds[i]);

            switch (clone.getType()) {
                case EventConstants.TYPE_THRESHOLD:
                case EventConstants.TYPE_BASELINE:
                case EventConstants.TYPE_CHANGE:
                    Integer tid = new Integer(clone.getMeasurementId());

                    // Don't need to synch the Measurement with the db
                    // since changes to the Measurement aren't cascaded
                    // on saving the AlertCondition.
                    try {
                        Measurement dmv = measurementManager.findMeasurement(subject, tid, id.getId(), true);
                        clone.setMeasurementId(dmv.getId().intValue());
                    } catch (MeasurementNotFoundException e) {
                        log.error("No measurement found for entity " + id + " associated with template id=" + tid +
                                  ". Alert definition name [" + adval.getName() + "]");
                        log.debug("Root cause", e);

                        if (failSilently) {
                            log.info("Alert condition creation failed. " + "The alert definition for entity " + id +
                                     " with name [" + adval.getName() + "] should not be created.");
                            // Just set to 0, it'll never fire
                            clone.setMeasurementId(0);
                            return false;
                        } else {
                            throw e;
                        }
                    }

                    break;
                case EventConstants.TYPE_ALERT:

                    // Don't need to synch the child alert definition Id lookup.
                    Integer recoverId = alertDefinitionManager.findChildAlertDefinitionId(id, new Integer(clone
                        .getMeasurementId()), true);

                    if (recoverId == null) {
                        // recoverId should never be null, but if it is and
                        // assertions
                        // are disabled, just move on.
                        assert false : "recover Id should not be null.";

                        log.error("A recovery alert has no associated recover "
                                  + "from alert. Setting alert condition " + "measurement Id to 0.");
                        clone.setMeasurementId(0);
                    } else {
                        clone.setMeasurementId(recoverId.intValue());
                    }

                    break;
            }

            // Now add it to the alert definition
            adval.addCondition(clone);
        }

        return true;
    }

    private void cloneParentActions(AppdefEntityID id, AlertDefinitionValue child, ActionValue[] actions) {
        child.removeAllActions();
        for (int i = 0; i < actions.length; i++) {
            ActionValue childAct;
            try {
                ActionInterface actInst = (ActionInterface) Class.forName(actions[i].getClassname()).newInstance();
                ConfigResponse config = ConfigResponse.decode(actions[i].getConfig());
                actInst.setParentActionConfig(id, config);
                childAct = new ActionValue(null, actInst.getImplementor(), actInst.getConfigResponse().encode(),
                    actions[i].getId());
            } catch (Exception e) {
                // Not a valid action, skip it then
                log.debug(actions[i].getClassname(), e);
                continue;
            }
            child.addAction(childAct);
        }
    }

    /**
     * Get the number of alerts for the given array of AppdefEntityID's
     * 
     */
    public int[] getAlertCount(int sessionID, AppdefEntityID[] ids) throws SessionNotFoundException,
        SessionTimeoutException, PermissionException{
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        int[] counts = alertManager.getAlertCount(ids);
        counts = galertManager.fillAlertCount(subject, ids, counts);
        return counts;
    }

    /**
     * Create an alert definition
     * 
     * 
     */
    public AlertDefinitionValue createAlertDefinition(int sessionID, AlertDefinitionValue adval)
        throws AlertDefinitionCreateException, PermissionException, InvalidOptionException,
        InvalidOptionValueException, SessionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        // Verify that there are some conditions to evaluate
        if (adval.getConditions().length == 0) {
            throw new AlertDefinitionCreateException("Conditions cannot be " + "null or empty");

        }

        // Create list of ID's to create the alert definition
        List<AppdefEntityID> appdefIds = new ArrayList<AppdefEntityID>();
        final AppdefEntityID aeid = getAppdefEntityID(adval);
        appdefIds.add(aeid);
        if (aeid.isGroup()) {
            // Look up the group
            AppdefGroupValue group;
            try {
                group = appdefBoss.findGroup(sessionID, adval.getAppdefId());
            } catch (InvalidAppdefTypeException e) {
                throw new AlertDefinitionCreateException(e);
            }

            appdefIds.addAll(group.getAppdefGroupEntries());
        }

        ArrayList<RegisteredTriggerValue> triggers = new ArrayList<RegisteredTriggerValue>();

        AlertDefinitionValue parent = null;

        // Iterate through to create the appropriate triggers and alertdef
        for (AppdefEntityID id : appdefIds) {
            // Reset the value object with this entity ID
            adval.setAppdefType(id.getType());
            adval.setAppdefId(id.getId());

            // Scrub the triggers just in case
            adval.removeAllTriggers();

            if (!id.isGroup()) {
                // If this is for the members of a group, we need to
                // scrub and copy the parent's conditions
                if (parent != null) {
                    adval.setParentId(parent.getId());
                    try {
                        cloneParentConditions(subject, id, adval, parent.getConditions(), false);
                    } catch (MeasurementNotFoundException e) {
                        throw new AlertConditionCreateException(e);
                    }
                }

                // Create the triggers
                createTriggers(subject, adval);
                triggers.addAll(Arrays.asList(adval.getTriggers()));
            }

            // Create a measurement AlertLogAction if necessary
            setMetricAlertAction(adval);

            // Now create the alert definition
            AlertDefinitionValue created = alertDefinitionManager.createAlertDefinition(subject, adval);

            if (parent == null) {
                parent = created;
            }
        }

        return parent;
    }

    /**
     * Create an alert definition for a resource type
     * 
     * 
     */
    public AlertDefinitionValue createResourceTypeAlertDefinition(int sessionID, AppdefEntityTypeID aetid,
                                                                  AlertDefinitionValue adval)
        throws AlertDefinitionCreateException, PermissionException, InvalidOptionException,
        InvalidOptionValueException, SessionNotFoundException, SessionTimeoutException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        // Verify that there are some conditions to evaluate
        if (adval.getConditions().length == 0) {
            throw new AlertDefinitionCreateException("Conditions cannot be null or empty");
        }

        AlertDefinitionValue parent;

        // Create the parent alert definition
        adval.setAppdefType(aetid.getType());
        adval.setAppdefId(aetid.getId());
        adval.setParentId(EventConstants.TYPE_ALERT_DEF_ID);

        // Now create the alert definition
        parent = alertDefinitionManager.createAlertDefinition(subject, adval);

        adval.setParentId(parent.getId());

        // Lookup resources
        Integer[] entIds;
        switch (aetid.getType()) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                entIds = platformManager.getPlatformIds(subject, aetid.getId());
                break;
            case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                entIds = serverManager.getServerIds(subject, aetid.getId());
                break;
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                entIds = serviceManager.getServiceIds(subject, aetid.getId());
                break;
            default:
                throw new InvalidOptionException("Alerts cannot be defined on appdef entity type " + aetid.getType());
        }

        ArrayList<RegisteredTriggerValue> triggers = new ArrayList<RegisteredTriggerValue>();

        // Iterate through to create the appropriate triggers and alertdef

        for (int ei = 0; ei < entIds.length; ei++) {
            AppdefEntityID id = new AppdefEntityID(aetid.getType(), entIds[ei]);

            // Reset the value object with this entity ID
            adval.setAppdefId(id.getId());

            // Scrub the triggers just in case
            adval.removeAllTriggers();

            try {
                boolean succeeded = cloneParentConditions(subject, id, adval, parent.getConditions(), true);

                if (!succeeded) {
                    continue;
                }
            } catch (MeasurementNotFoundException e) {
                throw new AlertDefinitionCreateException("Expected parent condition cloning to fail silently", e);
            }

            // Create the triggers
            createTriggers(subject, adval);
            triggers.addAll(Arrays.asList(adval.getTriggers()));

            // Make sure the actions have the proper parentId
            cloneParentActions(id, adval, parent.getActions());

            // Create a measurement AlertLogAction if necessary
            setMetricAlertAction(adval);

            // Now create the alert definition
            alertDefinitionManager.createAlertDefinition(subject, adval);
        }

        return parent;
    }

    private void setMetricAlertAction(AlertDefinitionValue adval) {
        AlertConditionValue[] conds = adval.getConditions();
        for (int i = 0; i < conds.length; i++) {
            if (conds[i].getType() == EventConstants.TYPE_THRESHOLD ||
                conds[i].getType() == EventConstants.TYPE_BASELINE || conds[i].getType() == EventConstants.TYPE_CHANGE) {
                ActionValue action = new ActionValue();
                action.setClassname(MetricAlertAction.class.getName());

                ConfigResponse config = new ConfigResponse();
                try {
                    action.setConfig(config.encode());
                } catch (EncodingException e) {
                    // This should never happen
                    log.error("Empty ConfigResponse threw an encoding error", e);
                }

                adval.addAction(action);
                break;
            }
        }
    }

    /**
     * 
     */
    public void inheritResourceTypeAlertDefinition(AuthzSubject subject, AppdefEntityID id)
        throws AppdefEntityNotFoundException, PermissionException, InvalidOptionException, InvalidOptionValueException,
        AlertDefinitionCreateException {
        AppdefEntityValue rv = new AppdefEntityValue(id, subject);
        AppdefResourceType type = rv.getAppdefResourceType();

        // Find the alert definitions for the type
        AppdefEntityTypeID aetid = new AppdefEntityTypeID(type.getAppdefType(), type.getId());

        // The alert definitions should be returned sorted by creation time.
        // This should minimize the possibility of creating a recovery alert
        // before the recover from alert.
        PageControl pc = new PageControl(0, PageControl.SIZE_UNLIMITED, PageControl.SORT_ASC, SortAttribute.CTIME);

        List<AlertDefinitionValue> defs = alertDefinitionManager.findAlertDefinitions(subject, aetid, pc);

        ArrayList<RegisteredTriggerValue> triggers = new ArrayList<RegisteredTriggerValue>();
        for (AlertDefinitionValue adval : defs) {
            // Only create if definition does not already exist
            if (alertDefinitionManager.isAlertDefined(id, adval.getId())) {
                continue;
            }

            // Set the parent ID
            adval.setParentId(adval.getId());

            // Reset the value object with this entity ID
            adval.setAppdefId(id.getId());

            try {
                boolean succeeded = cloneParentConditions(subject, id, adval, adval.getConditions(), true);

                if (!succeeded) {
                    continue;
                }
            } catch (MeasurementNotFoundException e) {
                throw new AlertDefinitionCreateException("Expected parent condition cloning to fail silently", e);
            }

            // Create the triggers
            createTriggers(subject, adval);
            triggers.addAll(Arrays.asList(adval.getTriggers()));

            // Recreate the actions
            cloneParentActions(id, adval, adval.getActions());

            // Create a measurement AlertLogAction if necessary
            setMetricAlertAction(adval);

            // Now create the alert definition
            alertDefinitionManager.createAlertDefinition(subject, adval);
        }
    }

    /**
     * 
     */
    public Action createAction(int sessionID, Integer adid, String className, ConfigResponse config)
        throws SessionNotFoundException, SessionTimeoutException, ActionCreateException, 
         PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        ArrayList<AlertDefinition> alertdefs = new ArrayList<AlertDefinition>();

        // check that the user can actually manage alerts for this resource
        AlertDefinition ad = alertDefinitionManager.getByIdAndCheck(subject, adid);
        alertdefs.add(ad);

        // If there are any children
        alertdefs.addAll(ad.getChildren());

        Action root = null;

        for (AlertDefinition alertDef : alertdefs) {
            try {
                if (root == null) {
                    root = actionManager.createAction(alertDef, className, config, null);
                } else {
                    actionManager.createAction(alertDef, className, config, root);
                }
            } catch (EncodingException e) {
                throw new SystemException("Couldn't encode.", e);
            }
        }

        return root;
    }

    /**
     * Activate/deactivate a collection of alert definitions
     * 
     * 
     */
    public void activateAlertDefinitions(int sessionID, Integer[] ids, boolean activate)
        throws SessionNotFoundException, SessionTimeoutException,  PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        alertDefinitionManager.updateAlertDefinitionsActiveStatus(subject, ids, activate);
    }

    /**
     * Activate or deactivate alert definitions by AppdefEntityID.
     * 
     * 
     */
    public void activateAlertDefinitions(int sessionID, AppdefEntityID[] eids, boolean activate)
        throws SessionNotFoundException, SessionTimeoutException, AppdefEntityNotFoundException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        boolean debugEnabled = log.isDebugEnabled();
        String status = (activate ? "enabled" : "disabled");

        for (int i = 0; i < eids.length; i++) {
            AppdefEntityID eid = eids[i];
            if (debugEnabled) {
                log.debug("AppdefEntityID [" + eid + "]");
            }
            if (eid.isGroup()) {
                ResourceGroup group = resourceGroupManager.findResourceGroupById(eid.getId());

                // Get the group alerts
                Collection<GalertDef> allAlerts = galertManager.findAlertDefs(group, PageControl.PAGE_ALL);
                for (GalertDef galertDef : allAlerts) {
                    galertManager.enable(galertDef, activate);
                    if (debugEnabled) {
                        log.debug("Group Alert [" + galertDef + "] " + status);
                    }
                }

                // Get the resource alerts of the group
                Collection<Resource> resources = resourceGroupManager.getMembers(group);
                for (Resource res : resources) {
                    updateAlertDefinitionsActiveStatus(subject, res, activate);
                }
            } else {
                updateAlertDefinitionsActiveStatus(subject, resourceManager.findResource(eid), activate);
            }
        }
    }

    private void updateAlertDefinitionsActiveStatus(AuthzSubject subject, Resource res, boolean activate)
        throws PermissionException {
        boolean debugEnabled = log.isDebugEnabled();
        String status = (activate ? "enabled" : "disabled");

        Collection<AlertDefinition> allAlerts = alertDefinitionManager.findRelatedAlertDefinitions(subject, res);

        for (AlertDefinition alertDef : allAlerts) {
            alertDefinitionManager.updateAlertDefinitionActiveStatus(subject, alertDef, activate);
            if (debugEnabled) {
                log.debug("Resource Alert [" + alertDef + "] " + status);
            }
        }
    }

    /**
     * Update just the basics
     * 
     * 
     */
    public void updateAlertDefinitionBasic(int sessionID, Integer alertDefId, String name, String desc, int priority,
                                           boolean activate) throws SessionNotFoundException, SessionTimeoutException,
          PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        alertDefinitionManager.updateAlertDefinitionBasic(subject, alertDefId, name, desc, priority, activate);
    }

    /**
     * 
     */
    public void updateAlertDefinition(int sessionID, AlertDefinitionValue adval) throws TriggerCreateException,
        InvalidOptionException, InvalidOptionValueException, AlertConditionCreateException, ActionCreateException,
          SessionNotFoundException, SessionTimeoutException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        // Verify that there are some conditions to evaluate
        if (adval.getConditions().length < 1) {
            throw new InvalidOptionValueException("Conditions cannot be null or empty");
        }

        ArrayList<RegisteredTriggerValue> triggers = new ArrayList<RegisteredTriggerValue>();
        if (EventConstants.TYPE_ALERT_DEF_ID.equals(adval.getParentId()) ||
            adval.getAppdefType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            // A little more work to do for group and type alert definition
            adval = alertDefinitionManager.updateAlertDefinition(adval);

            List<AlertDefinitionValue> children = alertDefinitionManager.findAlertDefinitionChildren(adval.getId());

            for (AlertDefinitionValue child : children) {

                AppdefEntityID id = new AppdefEntityID(child.getAppdefType(), child.getAppdefId());

                // Now add parent's conditions, actions, and new triggers
                try {
                    cloneParentConditions(subject, id, child, adval.getConditions(), false);
                } catch (MeasurementNotFoundException e) {
                    throw new AlertConditionCreateException(e);
                }

                cloneParentActions(id, child, adval.getActions());

                // Set the alert definition frequency type
                child.setFrequencyType(adval.getFrequencyType());
                child.setCount(adval.getCount());
                child.setRange(adval.getRange());

                // Set the alert definition filtering options
                child.setWillRecover(adval.getWillRecover());
                child.setNotifyFiltered(adval.getNotifyFiltered());
                child.setControlFiltered(adval.getControlFiltered());

                // Triggers are deleted by the manager
                registeredTriggerManager.deleteAlertDefinitionTriggers(child.getId());
                child.removeAllTriggers();
                createTriggers(subject, child);
                triggers.addAll(Arrays.asList(adval.getTriggers()));

                // Now update the alert definition
                alertDefinitionManager.updateAlertDefinition(child);
            }
        } else {
            // First, get rid of the current triggers
            registeredTriggerManager.deleteAlertDefinitionTriggers(adval.getId());
            adval.removeAllTriggers();

            // Now create the new triggers
            createTriggers(subject, adval);
            triggers.addAll(Arrays.asList(adval.getTriggers()));

            // Now update the alert definition
            alertDefinitionManager.updateAlertDefinition(adval);
        }
    }

    /**
     * Get actions for a given alert.
     * 
     * @param alertId the alert id
     * 
     * 
     */
    public List<ActionValue> getActionsForAlert(int sessionId, Integer alertId) throws SessionNotFoundException,
        SessionTimeoutException {
        sessionManager.authenticate(sessionId);
        return actionManager.getActionsForAlert(alertId.intValue());
    }

    /**
     * Update an action
     * 
     * 
     */
    public void updateAction(int sessionID, ActionValue aval) throws SessionNotFoundException, SessionTimeoutException {
        sessionManager.authenticate(sessionID);
        actionManager.updateAction(aval);
    }

    /**
     * Delete a collection of alert definitions
     * 
     * 
     */
    public void deleteAlertDefinitions(int sessionID, Integer[] ids) throws SessionNotFoundException,
        SessionTimeoutException,  PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        alertDefinitionManager.deleteAlertDefinitions(subject, ids);
    }

    /**
     * Delete list of alerts
     * 
     * 
     */
    public void deleteAlerts(int sessionID, Integer[] ids) throws SessionNotFoundException, SessionTimeoutException,
         PermissionException {
        sessionManager.authenticate(sessionID);
        alertManager.deleteAlerts(ids);
    }

    /**
     * Delete all alerts for a resource
     * 
     * 
     */
    public int deleteAlerts(int sessionID, AppdefEntityID aeid) throws SessionNotFoundException,
        SessionTimeoutException,  PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertManager.deleteAlerts(subject, aeid);
    }

    /**
     * Delete all alerts for a given period of time
     * 
     * 
     */
    public int deleteAlerts(int sessionID, long begin, long end) throws SessionNotFoundException,
        SessionTimeoutException,  PermissionException {
        sessionManager.authenticate(sessionID);
        // XXX - check security
        return alertManager.deleteAlerts(begin, end);
    }

    /**
     * Delete all alerts for a list of alert definitions
     * 
     * 
     * 
     */
    public int deleteAlertsForDefinitions(int sessionID, Integer[] adids) throws SessionNotFoundException,
        SessionTimeoutException,  PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        // Delete alerts for definition and its children
        int count = 0;

        for (int i = 0; i < adids.length; i++) {
            AlertDefinition def = alertDefinitionManager.getByIdAndCheck(subject, adids[i]);
            count += alertManager.deleteAlerts(subject, def);

            Collection<AlertDefinition> children = def.getChildren();

            for (AlertDefinition child : children) {
                count += alertManager.deleteAlerts(subject, child);
            }
        }
        return count;
    }

    /**
     * Get an alert definition by ID
     * 
     * 
     */
    public AlertDefinitionValue getAlertDefinition(int sessionID, Integer id) throws SessionNotFoundException,
        SessionTimeoutException,  PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertDefinitionManager.getById(subject, id);
    }

    /**
     * Find an alert by ID
     * 
     * 
     */
    public Alert getAlert(int sessionID, Integer id) throws SessionNotFoundException, SessionTimeoutException,
        AlertNotFoundException {
        sessionManager.authenticate(sessionID);

        Alert alert = alertManager.findAlertById(id);

        if (alert == null)
            throw new AlertNotFoundException(id);

        return alert;
    }

    /**
     * Get a list of all alert definitions
     * 
     * 
     */
    public PageList<AlertDefinitionValue> findAllAlertDefinitions(int sessionID) throws SessionNotFoundException,
        SessionTimeoutException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertDefinitionManager.findAllAlertDefinitions(subject);
    }

    /**
     * Get a collection of alert definitions for a resource
     * 
     * 
     */
    public PageList<AlertDefinitionValue> findAlertDefinitions(int sessionID, AppdefEntityID id, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertDefinitionManager.findAlertDefinitions(subject, id, pc);
    }

    /**
     * Get a collection of alert definitions for a resource or resource type
     * 
     */
    public PageList<AlertDefinitionValue> findAlertDefinitions(int sessionID, AppdefEntityTypeID id, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertDefinitionManager.findAlertDefinitions(subject, id, pc);
    }

    /**
     * Find all alert definition names for a resource
     * @return Map of AlertDefinition names and IDs
     * 
     */
    public Map<String, Integer> findAlertDefinitionNames(int sessionID, AppdefEntityID id, Integer parentId)
        throws SessionNotFoundException, SessionTimeoutException, AppdefEntityNotFoundException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertDefinitionManager.findAlertDefinitionNames(subject, id, parentId);
    }

    /**
     * Find all alerts for an appdef resource
     * 
     * 
     */
    public PageList<Alert> findAlerts(int sessionID, AppdefEntityID id, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertManager.findAlerts(subject, id, pc);
    }

    /**
     * Find all alerts for an appdef resource
     * 
     * 
     */
    public PageList<Alert> findAlerts(int sessionID, AppdefEntityID id, long begin, long end, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        return alertManager.findAlerts(subject, id, begin, end, pc);
    }

    /**
     * Search alerts given a set of criteria
     * @param username the username
     * @param count the maximum number of alerts to return
     * @param priority allowable values: 0 (all), 1, 2, or 3
     * @param timeRange the amount of time from current time to include
     * @param ids the IDs of resources to include or null for ALL
     * @return a list of {@link Escalatable}s
     * 
     */
    public List<Escalatable> findRecentAlerts(String username, int count, int priority, long timeRange,
                                              AppdefEntityID[] ids) throws LoginException, ApplicationException,
        ConfigPropertyException {
        int sessionId = authBoss.getUnauthSessionId(username);
        return findRecentAlerts(sessionId, count, priority, timeRange, ids);
    }

    /**
     * Search recent alerts given a set of criteria
     * @param sessionID the session token
     * @param count the maximum number of alerts to return
     * @param priority allowable values: 0 (all), 1, 2, or 3
     * @param timeRange the amount of time from current time to include
     * @param ids the IDs of resources to include or null for ALL
     * @return a list of {@link Escalatable}s
     * 
     */
    public List<Escalatable> findRecentAlerts(int sessionID, int count, int priority, long timeRange,
                                              AppdefEntityID[] ids) throws SessionNotFoundException,
        SessionTimeoutException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        long cur = System.currentTimeMillis();

        List<AppdefEntityID> appentResources = ids != null ? appentResources = Arrays.asList(ids) : null;

        // Assume if user can be alerted, then they can view resource,
        // otherwise, it'll be filtered out later anyways
        List<Escalatable> alerts = alertManager.findEscalatables(subject, count, priority, timeRange, cur,
            appentResources);

        // CheckAlertingScope now only used for galerts
        if (ids == null) {
            // find ALL alertable resources
            appentResources = permissionManager.checkAlertingScope(subject);
        }

        List<Escalatable> galerts = galertManager.findEscalatables(subject, count, priority, timeRange, cur,
            appentResources);
        alerts.addAll(galerts);

        Collections.sort(alerts, new Comparator<Escalatable>() {
            public int compare(Escalatable o1, Escalatable o2) {
                long l1 = o1.getAlertInfo().getTimestamp();
                long l2 = o2.getAlertInfo().getTimestamp();

                // Reverse sort
                if (l1 > l2) {
                    return -1;
                } else if (l1 < l2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        Set<AppdefEntityID> goodIds = new HashSet<AppdefEntityID>();
        List<AppdefEntityID> badIds = new ArrayList<AppdefEntityID>();

        List<Escalatable> res = new ArrayList<Escalatable>();
        for (Iterator<Escalatable> i = alerts.iterator(); i.hasNext() && res.size() < count;) {
            Escalatable alert = i.next();
            PerformsEscalations def = alert.getDefinition();
            AlertDefinitionInterface defInfo = def.getDefinitionInfo();
            AppdefEntityID aeid;

            aeid = AppdefUtil.newAppdefEntityId(defInfo.getResource());

            if (badIds.contains(aeid))
                continue;

            // Check to see if we already have the resource in the hash map
            if (!goodIds.contains(aeid)) {
                AppdefEntityValue entVal = new AppdefEntityValue(aeid, subject);

                try {
                    entVal.getName();
                    goodIds.add(aeid);
                } catch (Exception e) {
                    // Probably because the resource does not exist
                    badIds.add(aeid);
                    continue;
                }
            }

            res.add(alert);
        }

        return res;
    }

    /**
     * Get config schema info for an action class
     * 
     * 
     */
    public ConfigSchema getActionConfigSchema(int sessionID, String actionClass) throws SessionNotFoundException,
        SessionTimeoutException, EncodingException {
        sessionManager.authenticate(sessionID);
        ActionInterface iface;
        try {
            Class<?> c = Class.forName(actionClass);
            iface = (ActionInterface) c.newInstance();
        } catch (Exception exc) {
            throw new EncodingException("Failed to instantiate class: " + exc);
        }
        return iface.getConfigSchema();
    }

    /*
     * The following trigger API's are specific to the CLI only
     */

    /**
     * Get config schema info for a trigger class
     * 
     * 
     */
    public ConfigSchema getRegisteredTriggerConfigSchema(int sessionID, String triggerClass)
        throws SessionNotFoundException, SessionTimeoutException, EncodingException {
        sessionManager.authenticate(sessionID);
        RegisterableTriggerInterface iface;
        Class<?> c;

        try {
            c = Class.forName(triggerClass);
            iface = (RegisterableTriggerInterface) c.newInstance();
        } catch (Exception exc) {
            throw new EncodingException("Failed to instantiate class: " + exc);
        }
        return iface.getConfigSchema();
    }

    /**
     * 
     */
    public void deleteEscalationByName(int sessionID, String name) throws SessionTimeoutException,
        SessionNotFoundException, PermissionException, ApplicationException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        Escalation e = escalationManager.findByName(name);

        escalationManager.deleteEscalation(subject, e);
    }

    /**
     * 
     */
    public void deleteEscalationById(int sessionID, Integer id) throws SessionTimeoutException,
        SessionNotFoundException, PermissionException, ApplicationException {
        deleteEscalationById(sessionID, new Integer[] { id });
    }

    /**
     * remove escalation by id
     * 
     */
    public void deleteEscalationById(int sessionID, Integer[] ids) throws SessionTimeoutException,
        SessionNotFoundException, PermissionException, ApplicationException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        for (int i = 0; i < ids.length; i++) {
            Escalation e = escalationManager.findById(ids[i]);

            escalationManager.deleteEscalation(subject, e);
        }
    }

    /**
     * retrieve escalation by alert definition id.
     */
    private Escalation findEscalationByAlertDefId(Integer id, EscalationAlertType type) throws PermissionException {
        return escalationManager.findByDefId(type, id);
    }

    /**
     * retrieve escalation name by alert definition id.
     * 
     * 
     */
    public Integer getEscalationIdByAlertDefId(int sessionID, Integer id, EscalationAlertType alertType)
        throws SessionTimeoutException, SessionNotFoundException, PermissionException{
        sessionManager.authenticate(sessionID);
        Escalation esc = findEscalationByAlertDefId(id, alertType);
        return esc == null ? null : esc.getId();
    }

    /**
     * set escalation name by alert definition id.
     * 
     * 
     */
    public void setEscalationByAlertDefId(int sessionID, Integer id, Integer escId, EscalationAlertType alertType)
        throws SessionTimeoutException, SessionNotFoundException, PermissionException {
        sessionManager.authenticate(sessionID);
        Escalation escalation = findEscalationById(sessionID, escId);
        // TODO: check permission
        escalationManager.setEscalation(alertType, id, escalation);
    }

    /**
     * unset escalation by alert definition id.
     * 
     * 
     */
    public void unsetEscalationByAlertDefId(int sessionID, Integer id, EscalationAlertType alertType)
        throws SessionTimeoutException, SessionNotFoundException, PermissionException {
        sessionManager.authenticate(sessionID);
        // TODO: check permission
        escalationManager.setEscalation(alertType, id, null);
    }

    /**
     * retrieve escalation JSONObject by alert definition id.
     * 
     * 
     */
    public JSONObject jsonEscalationByAlertDefId(int sessionID, Integer id, EscalationAlertType alertType)
        throws SessionException, PermissionException, JSONException {
        sessionManager.authenticate(sessionID);
        Escalation e = findEscalationByAlertDefId(id, alertType);
        return e == null ? null : new JSONObject().put(e.getJsonName(), e.toJSON());
    }

    /**
     * retrieve escalation object by escalation id.
     * 
     * 
     */
    public Escalation findEscalationById(int sessionID, Integer id) throws SessionTimeoutException,
        SessionNotFoundException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        Escalation e = escalationManager.findById(subject, id);

        // XXX: Temporarily get around lazy loading problem
        e.isPauseAllowed();
        e.getMaxPauseTime();
        return e;
    }

    /**
     * 
     */
    public void addAction(int sessionID, Escalation e, ActionConfigInterface cfg, long waitTime)
        throws SessionTimeoutException, SessionNotFoundException, PermissionException {
        sessionManager.authenticate(sessionID);
        escalationManager.addAction(e, cfg, waitTime);
    }

    /**
     * 
     */
    public void removeAction(int sessionID, Integer escId, Integer actId) throws SessionTimeoutException,
        SessionNotFoundException, PermissionException {
        sessionManager.authenticate(sessionID);
        Escalation e = escalationManager.findById(escId);

        if (e != null) {
            escalationManager.removeAction(e, actId);
        }
    }

    /**
     * Retrieve a list of {@link EscalationState}s, representing the active
     * escalations in the system.
     * 
     * 
     */
    public List<EscalationState> getActiveEscalations(int sessionId, int maxEscalations) throws SessionException {
        sessionManager.authenticate(sessionId);

        return escalationManager.getActiveEscalations(maxEscalations);
    }

    /**
     * Gets the escalatable associated with the specified state
     * 
     */
    public Escalatable getEscalatable(int sessionId, EscalationState state) throws SessionException {
        sessionManager.authenticate(sessionId);
        return escalationManager.getEscalatable(state);
    }

    /**
     * retrieve all escalation policy names as a Array of JSONObject.
     * 
     * Escalation json finders begin with json* to be consistent with DAO finder
     * convention
     * 
     * 
     */
    public JSONArray listAllEscalationName(int sessionID) throws JSONException, SessionTimeoutException,
        SessionNotFoundException, PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        Collection<Escalation> all = escalationManager.findAll(subject);
        JSONArray jarr = new JSONArray();
        for (Escalation esc : all) {
            jarr.put(new JSONObject().put("id", esc.getId()).put("name", esc.getName()));
        }
        return jarr;
    }

    private AppdefEntityID getAppdefEntityID(AlertDefinitionValue ad) {
        return new AppdefEntityID(ad.getAppdefType(), ad.getAppdefId());
    }

    /**
     * Create a new escalation. If alertDefId is non-null, the escalation will
     * also be associated with the given alert definition.
     * 
     * 
     */
    public Escalation createEscalation(int sessionID, String name, String desc, boolean allowPause, long maxWaitTime,
                                       boolean notifyAll, boolean repeat, EscalationAlertType alertType,
                                       Integer alertDefId) throws SessionTimeoutException, SessionNotFoundException,
        PermissionException, DuplicateObjectException {
        sessionManager.authenticate(sessionID);

        // XXX -- We need to do perm-checking here

        Escalation res = escalationManager.createEscalation(name, desc, allowPause, maxWaitTime, notifyAll, repeat);

        if (alertDefId != null) {
            // The alert def needs to use this escalation
            escalationManager.setEscalation(alertType, alertDefId, res);
        }
        return res;
    }

    /**
     * Update basic escalation properties
     * 
     * 
     */
    public void updateEscalation(int sessionID, Escalation escalation, String name, String desc, long maxWait,
                                 boolean pausable, boolean notifyAll, boolean repeat) throws SessionTimeoutException,
        SessionNotFoundException, PermissionException, DuplicateObjectException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        escalationManager.updateEscalation(subject, escalation, name, desc, pausable, maxWait, notifyAll, repeat);
    }

    /**
     * 
     */
    public boolean acknowledgeAlert(int sessionID, EscalationAlertType alertType, Integer alertID, long pauseWaitTime,
                                    String moreInfo) throws SessionTimeoutException, SessionNotFoundException,
        PermissionException, ActionExecuteException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        return escalationManager.acknowledgeAlert(subject, alertType, alertID, moreInfo, pauseWaitTime);
    }

    /**
     * Fix a single alert. TODO: remove comment below Method WAS "NotSupported"
     * since all the alert fixes may take longer than the jboss transaction
     * timeout. No need for a transaction in this context.
     * 
     */
    public void fixAlert(int sessionID, EscalationAlertType alertType, Integer alertID, String moreInfo)
        throws SessionTimeoutException, SessionNotFoundException, PermissionException, ActionExecuteException {
        fixAlert(sessionID, alertType, alertID, moreInfo, false);
    }

    /**
     * Fix a batch of alerts. TODO: remove comment below Method is
     * "NotSupported" since all the alert fixes may take longer than the jboss
     * transaction timeout. No need for a transaction in this context.
     * 
     */
    public void fixAlert(int sessionID, EscalationAlertType alertType, Integer alertID, String moreInfo,
                         boolean fixAllPrevious) throws SessionTimeoutException, SessionNotFoundException,
        PermissionException, ActionExecuteException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        if (fixAllPrevious) {
            long fixCount = fixPreviousAlerts(sessionID, alertType, alertID, moreInfo);
            if (fixCount > 0) {
                if (moreInfo == null) {
                    moreInfo = "";
                }
                StringBuffer sb = new StringBuffer();
                MessageFormat messageFormat = new MessageFormat(ResourceBundle.getBundle(BUNDLE).getString(
                    "events.alert.fixAllPrevious"));
                messageFormat.format(new String[] { Long.toString(fixCount) }, sb, null);
                moreInfo = sb.toString() + moreInfo;
            }
        }
        // fix the selected alert
        escalationManager.fixAlert(subject, alertType, alertID, moreInfo, false);
    }

    /**
     * Fix all previous alerts. Method is "NotSupported" since all the alert
     * fixes may take longer than the jboss transaction timeout. No need for a
     * transaction in this context.
     */
    @SuppressWarnings("unchecked")
    private long fixPreviousAlerts(int sessionID, EscalationAlertType alertType, Integer alertID, String moreInfo)
        throws SessionTimeoutException, SessionNotFoundException, PermissionException, ActionExecuteException {
        StopWatch watch = new StopWatch();
        AuthzSubject subject = sessionManager.getSubject(sessionID);
        long fixCount = 0;

        List<? extends AlertInterface> alertsToFix = null;

        // Get all previous unfixed alerts.
        watch.markTimeBegin("fixPreviousAlerts: findAlerts");
        if (alertType.equals(ClassicEscalationAlertType.CLASSIC)) {
            AlertInterface alert = alertManager.findAlertById(alertID);
            alertsToFix = alertManager.findAlerts(subject.getId(), 0, alert.getTimestamp(), alert.getTimestamp(),
                false, true, null, alert.getAlertDefinitionInterface().getId(), PageInfo.getAll(AlertSortField.DATE,
                    false));

        } else if (alertType.equals(GalertEscalationAlertType.GALERT)) {
            AlertInterface alert = galertManager.findAlertLog(alertID);
            alertsToFix = galertManager.findAlerts(subject, AlertSeverity.LOW, alert.getTimestamp(), alert
                .getTimestamp(), false, true, null, alert.getAlertDefinitionInterface().getId(), PageInfo.getAll(
                GalertLogSortField.DATE, false));
        } else {
            alertsToFix = Collections.EMPTY_LIST;
        }
        watch.markTimeEnd("fixPreviousAlerts: findAlerts");

        log.debug("fixPreviousAlerts: alertId = " + alertID + ", previous alerts to fix = " + (alertsToFix.size() - 1) +
                  ", time = " + watch);

        watch.markTimeBegin("fixPreviousAlerts: fixAlert");
        try {
            for (AlertInterface alert : alertsToFix) {
                try {
                    // Suppress notifications for all previous alerts.
                    if (!alert.getId().equals(alertID)) {
                        escalationManager.fixAlert(subject, alertType, alert.getId(), moreInfo, true);
                        fixCount++;
                    }
                } catch (PermissionException pe) {
                    throw pe;
                } catch (Exception e) {
                    // continue with next alert
                    log.error("Could not fix alert id " + alert.getId() + ": " + e.getMessage(), e);
                }
            }
        } finally {
            watch.markTimeEnd("fixPreviousAlerts: fixAlert");
            log.debug("fixPreviousAlerts: alertId = " + alertID + ", previous alerts fixed = " + fixCount +
                      ", time = " + watch);

        }
        return fixCount;
    }

    /**
     * Get the last fix if available
     * 
     */
    public String getLastFix(int sessionID, Integer defId) throws SessionNotFoundException, SessionTimeoutException,
        PermissionException {
        AuthzSubject subject = sessionManager.getSubject(sessionID);

        // Look for the last fixed alert
        AlertDefinition def = alertDefinitionManager.getByIdAndCheck(subject, defId);
        return escalationManager.getLastFix(def);
    }

    /**
     * Get a maintenance event by group id
     * 
     * 
     */
    public MaintenanceEvent getMaintenanceEvent(int sessionId, Integer groupId) throws SessionNotFoundException,
        SessionTimeoutException, PermissionException, SchedulerException {
        AuthzSubject subject = sessionManager.getSubject(sessionId);

        return getMaintenanceEventManager().getMaintenanceEvent(subject, groupId);
    }

    /**
     * Schedule a maintenance event
     * 
     * 
     */
    public MaintenanceEvent scheduleMaintenanceEvent(int sessionId, MaintenanceEvent event)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException, SchedulerException {
        AuthzSubject subject = sessionManager.getSubject(sessionId);
        event.setModifiedBy(subject.getName());

        return getMaintenanceEventManager().schedule(subject, event);
    }

    /**
     * Schedule a maintenance event
     * 
     * 
     */
    public void unscheduleMaintenanceEvent(int sessionId, MaintenanceEvent event) throws SessionNotFoundException,
        SessionTimeoutException, PermissionException, SchedulerException {
        AuthzSubject subject = sessionManager.getSubject(sessionId);
        event.setModifiedBy(subject.getName());

        getMaintenanceEventManager().unschedule(subject, event);
    }

    /**
     * 
     */
    public void startup() {
        log.info("Events Boss starting up!");

        
        app.registerCallbackListener(DefaultMetricEnableCallback.class, new DefaultMetricEnableCallback() {
            public void metricsEnabled(AppdefEntityID ent) {
                try {
                    log.info("Inheriting type-based alert defs for " + ent);
                    AuthzSubject hqadmin = authzSubjectManager.getSubjectById(AuthzConstants.rootSubjectId);
                    getOne().inheritResourceTypeAlertDefinition(hqadmin, ent);
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
        });

        app.registerCallbackListener(ResourceDeleteCallback.class, new ResourceDeleteCallback() {
            public void preResourceDelete(Resource r) throws VetoException {
                alertDefinitionManager.disassociateResource(r);
            }
        });

        app.registerCallbackListener(SubjectRemoveCallback.class, new SubjectRemoveCallback() {
            public void subjectRemoved(AuthzSubject toDelete) {
                escalationManager.handleSubjectRemoval(toDelete);
                alertManager.handleSubjectRemoval(toDelete);
            }

        });

        // Add listener to remove alert definition and alerts after resources
        // are deleted.
        HashSet<Class<ResourceDeletedZevent>> events = new HashSet<Class<ResourceDeletedZevent>>();
        events.add(ResourceDeletedZevent.class);
        zEventManager.addBufferedListener(events, new ZeventListener<ResourceZevent>() {
            public void processEvents(List<ResourceZevent> events) {
                for (ResourceZevent z : events) {
                    if (z instanceof ResourceDeletedZevent) {
                        alertDefinitionManager.cleanupAlertDefinitions(z.getAppdefEntityID());
                    }
                }
            }

            public String toString() {
                return "AlertDefCleanupListener";
            }
        });
    }

    public static EventsBoss getOne() {
        return Bootstrap.getBean(EventsBoss.class);
    }
}
