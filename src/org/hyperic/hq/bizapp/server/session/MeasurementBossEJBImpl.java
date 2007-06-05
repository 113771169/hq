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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.NamingException;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.server.session.AppdefGroupManagerEJBImpl;
import org.hyperic.hq.appdef.server.session.AppdefResource;
import org.hyperic.hq.appdef.server.session.AppdefResourceType;
import org.hyperic.hq.appdef.server.session.ServiceManagerEJBImpl;
import org.hyperic.hq.appdef.shared.AgentConnectionUtil;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppServiceValue;
import org.hyperic.hq.appdef.shared.AppdefCompatException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityTypeID;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.AppdefGroupManagerLocal;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.appdef.shared.ApplicationNotFoundException;
import org.hyperic.hq.appdef.shared.ApplicationValue;
import org.hyperic.hq.appdef.shared.ConfigFetchException;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.ServerValue;
import org.hyperic.hq.appdef.shared.ServiceClusterValue;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.VirtualManagerLocal;
import org.hyperic.hq.appdef.shared.VirtualManagerUtil;
import org.hyperic.hq.auth.shared.SessionException;
import org.hyperic.hq.auth.shared.SessionNotFoundException;
import org.hyperic.hq.auth.shared.SessionTimeoutException;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.bizapp.shared.AppdefBossLocal;
import org.hyperic.hq.bizapp.shared.AppdefBossUtil;
import org.hyperic.hq.bizapp.shared.uibeans.AutogroupDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.ClusterDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.GroupMetricDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.MeasurementMetadataSummary;
import org.hyperic.hq.bizapp.shared.uibeans.MeasurementSummary;
import org.hyperic.hq.bizapp.shared.uibeans.MetricConfigSummary;
import org.hyperic.hq.bizapp.shared.uibeans.MetricDisplayConstants;
import org.hyperic.hq.bizapp.shared.uibeans.MetricDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.MetricDisplayValue;
import org.hyperic.hq.bizapp.shared.uibeans.ProblemMetricSummary;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceMetricDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.ResourceTypeDisplaySummary;
import org.hyperic.hq.bizapp.shared.uibeans.SingletonDisplaySummary;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.grouping.server.session.GroupUtil;
import org.hyperic.hq.grouping.shared.GroupNotCompatibleException;
import org.hyperic.hq.measurement.DataPurgeJob;
import org.hyperic.hq.measurement.EvaluationException;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.MeasurementCreateException;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.hq.measurement.TemplateNotFoundException;
import org.hyperic.hq.measurement.agent.client.MeasurementCommandsClient;
import org.hyperic.hq.measurement.data.DataNotAvailableException;
import org.hyperic.hq.measurement.monitor.LiveMeasurementException;
import org.hyperic.hq.measurement.server.session.DerivedMeasurement;
import org.hyperic.hq.measurement.server.session.MeasurementTemplate;
import org.hyperic.hq.measurement.server.session.ResourcesWithoutDataHelper;
import org.hyperic.hq.measurement.shared.BaselineValue;
import org.hyperic.hq.measurement.shared.DerivedMeasurementValue;
import org.hyperic.hq.measurement.shared.MeasurementArgValue;
import org.hyperic.hq.measurement.shared.MeasurementTemplateValue;
import org.hyperic.hq.measurement.shared.TemplateManagerLocal;
import org.hyperic.hq.measurement.shared.TrackerManagerLocal;
import org.hyperic.hq.product.ConfigTrackPlugin;
import org.hyperic.hq.product.LogTrackPlugin;
import org.hyperic.hq.product.MetricValue;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.util.ConfigPropertyException;
import org.hyperic.util.collection.IntHashMap;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.EncodingException;
import org.hyperic.util.config.InvalidOptionException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.timer.StopWatch;

/** BizApp interface to the Measurement subsystem
 * @ejb:bean name="MeasurementBoss"
 *      jndi-name="ejb/bizapp/MeasurementBoss"
 *      local-jndi-name="LocalMeasurementBoss"
 *      view-type="both"
 *      type="Stateless"
 * @ejb:transaction type="Required"
 */
public class MeasurementBossEJBImpl extends MetricSessionEJB
    implements SessionBean 
{
    protected static Log log =
        LogFactory.getLog(MeasurementBossEJBImpl.class.getName());

    private final ResourcesWithoutDataHelper resourcesWithoutDataHelper
        = ResourcesWithoutDataHelper.instance();

    private Integer[] getGroupMemberIDs(AuthzSubjectValue subject,
                                        AppdefEntityID gid)
        throws AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        List grpMembers = GroupUtil.getCompatGroupMembers(subject, gid, null,
                                                          PageControl.PAGE_ALL);
            
        // Get the list of group members
        Iterator it = grpMembers.iterator();
        Integer[] ids = new Integer[grpMembers.size()];
        for (int i = 0; it.hasNext(); i++) {
            AppdefEntityID id = (AppdefEntityID) it.next();
            ids[i] = id.getId();
        }
        
        return ids;
    }

    private List findDesignatedMetrics(AuthzSubjectValue subject,
                                       AppdefEntityID id,
                                       Set cats) {
        List metrics;
        
        if (cats.size() == 1) {
            String cat = (String) cats.iterator().next();
            metrics =
                getDerivedMeasurementManager().findDesignatedMeasurements(
                    subject, id, cat);
        }
        else {
            metrics =
                getDerivedMeasurementManager().findDesignatedMeasurements(
                    subject, id);
            
            // Now iterate through and throw out the metrics we don't need
            for (Iterator it = metrics.iterator(); it.hasNext(); ) {
                DerivedMeasurement dm = (DerivedMeasurement) it.next();
                if (!cats.contains(dm.getTemplate().getCategory().getName()))
                    it.remove();
            }
        }

        return metrics;
    }

    /** Create a measurement template
     * @return the ID of the newly created template
     * @ejb:interface-method
     */
    public Integer createDerivedMeasurementTemplate(int sessionId,
                                                    String name, String alias,
                                                    String type,
                                                    String category,
                                                    String template,
                                                    String units,
                                                    int collectionType,
                                                    MeasurementArgValue[] args)
        throws SessionNotFoundException, SessionTimeoutException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
            
        MeasurementTemplate mtv = getTemplateManager()
            .createTemplate(subject, name, alias, type, category, template,
                            units, collectionType, args);
        return mtv.getId();
    }

    /**
     * Update the default interval for a list of template ids
     * @ejb:interface-method
     */
    public void updateMetricDefaultInterval(int sessionId, Integer[] tids,
                                            long interval)
        throws TemplateNotFoundException,
                SessionTimeoutException, SessionNotFoundException {
        getTemplateManager().updateTemplateDefaultInterval(tids, interval);
    }

    /**
     * Update the templates to be indicators or not
     * @ejb:interface-method
     */
    public void updateIndicatorMetrics(int sessionId, AppdefEntityTypeID aetid,
                                       Integer[] tids)
        throws TemplateNotFoundException, SessionTimeoutException,
               SessionNotFoundException {
        String typeName = aetid.getAppdefResourceTypeValue().getName();
        getTemplateManager().setDesignatedTemplates(typeName, tids);
    }

    /**
     * Delete a derived measurement template
     * @ejb:interface-method
     */
    public void removeDerivedMeasurementTemplate(int sessionId, Integer arg)
        throws SessionNotFoundException, SessionTimeoutException,
               RemoveException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        getTemplateManager().removeTemplate(subject, arg);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return a PageList of MeasurementTemplateValue objects
     */
    public PageList findMeasurementTemplates(int sessionId, 
                                             AppdefEntityTypeID typeId,
                                             String category,
                                             PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        String typeName = typeId.getAppdefResourceTypeValue().getName();
        return getTemplateManager().findTemplates(typeName, 
                                                       category, 
                                                       new Integer[]{}, 
                                                       pc);
    }

    /** Retrieve list of measurement templates applicable to a monitorable type
     * @param mtype the monitorableType
     * @return a List of MeasurementTemplateValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementTemplates(int sessionId, String mtype,
                                             PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getTemplateManager().findTemplates(mtype, null, null, pc);
    }

    /** Retrieve list of measurement templates given specific IDs
     * @ejb:interface-method
     */
    public PageList findMeasurementTemplates(String user, Integer[] ids,
                                             PageControl pc)
        throws LoginException, ApplicationException, ConfigPropertyException {
        int sessionId = getAuthManager().getUnauthSessionId(user);
        return findMeasurementTemplates(sessionId, ids, pc);
    }

    /** Retrieve list of measurement templates given specific IDs
     * @return a List of MeasurementTemplateValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementTemplates(int sessionId, Integer[] ids,
                                             PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               TemplateNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getTemplateManager().getTemplates(ids, pc);
    }

    /** Retrieve a measurement template given specific ID
     * @ejb:interface-method
     */
    public MeasurementTemplateValue getMeasurementTemplate(int sessionId,
                                                           Integer id)
        throws SessionNotFoundException, SessionTimeoutException,
               TemplateNotFoundException {
        return getTemplateManager()
            .getTemplate(id).getMeasurementTemplateValue();
    }

    /**
     * Get the the availability metric template for the given autogroup
     * @return The availabililty metric template.
     * @ejb:interface-method
     */
    public MeasurementTemplateValue getAvailabilityMetricTemplate(
            int sessionId, AppdefEntityID aid, AppdefEntityTypeID ctype)
        throws SessionNotFoundException, SessionTimeoutException,
               MeasurementNotFoundException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        // Find the autogroup members
        List entIds = getAGMemberIds(subject, new AppdefEntityID[] { aid },
                                     ctype);
    
        for (Iterator it = entIds.iterator(); it.hasNext();) {
            AppdefEntityID aeId = (AppdefEntityID) it.next();
            try {
                return getAvailabilityMetricTemplate(sessionId, aeId);
            } catch (MeasurementNotFoundException e) {
                // continue;
            }
        }
    
        // Throw a MeasurementNotFoundException here
        throw new MeasurementNotFoundException(
            "Autogroup for : " + aid + " of type : " + ctype +
            " does not contain designated measurements");
    }

    private MeasurementTemplateValue getAvailabilityMetricTemplate(
        AuthzSubjectValue subject, AppdefEntityID aeid)
        throws AppdefEntityNotFoundException, PermissionException,
               MeasurementNotFoundException
    {
        DerivedMeasurement dm = null;
        if (aeid.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION) {
            // Get the appointed front-end service
            AppdefEntityValue aeval = new AppdefEntityValue(aeid, subject);
            ApplicationValue app = (ApplicationValue) aeval.getResourceValue();
            
            AppServiceValue[] appSvcs = app.getAppServiceValues();
            for (int i = 0; i < appSvcs.length; i++) {
                // Let's try it
                if (appSvcs[i].getIsCluster()) {
                    if (appSvcs[i].getServiceCluster() == null)
                        continue;
                        
                    aeid = new AppdefEntityID(
                        AppdefEntityConstants.APPDEF_TYPE_GROUP, 
                        appSvcs[i].getServiceCluster().getGroupId());
                }
                else {
                    // Make sure this is a valid service
                    if (appSvcs[i].getService() == null)
                        continue;
                    
                    // Get the metrics for the service                        
                    aeid = appSvcs[i].getService().getEntityId();
                }
    
                dm = findAvailabilityMetric(subject, aeid);
                
                if (dm != null)
                    break;
            }
        }
        else if (aeid.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP)
        {
            List grpMembers;
            try {
                grpMembers =
                    GroupUtil.getCompatGroupMembers(subject, aeid, null,
                                                    PageControl.PAGE_ALL);
            } catch (GroupNotCompatibleException e) {
                throw new MeasurementNotFoundException(
                    "Incompatible group " + aeid +
                    " cannot have a common designated measurement");
            }
            
            // Go through the group members and return the first measurement
            // that we find
            for (Iterator it = grpMembers.iterator(); it.hasNext(); ) {
                aeid = (AppdefEntityID) it.next();
                dm = findAvailabilityMetric(subject, aeid);
                
                if (dm != null)
                    break;
            }
        }
        else {
            dm = findAvailabilityMetric(subject, aeid);
        }
        
        if (dm != null)
            return dm.getTemplate().getMeasurementTemplateValue();
        else
            throw new MeasurementNotFoundException(
                "Availability metric not found for " + aeid);
    }
    
    /**
     * Get the the availability metric template for the given resource
     * @return template of availabililty metric
     * @ejb:interface-method
     */
    public MeasurementTemplateValue getAvailabilityMetricTemplate(
        int sessionId, AppdefEntityID aeid)
        throws MeasurementNotFoundException, SessionNotFoundException,
               SessionTimeoutException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getAvailabilityMetricTemplate(subject, aeid);
    }

    /**
     * Get the the designated measurement template for the given resource
     * and corresponding category.
     * @return array of derived measurement IDs
     * @ejb:interface-method
     */
    public List getDesignatedTemplates(int sessionId, AppdefEntityID id,
                                       Set cats) 
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException {
        List metrics;
        try {
            metrics = getDesignatedMetrics(sessionId, id, cats);
        } catch (MeasurementNotFoundException e) {
            return new ArrayList(0);
        }
        
        ArrayList tmpls = new ArrayList(metrics.size());
        for (Iterator it = metrics.iterator(); it.hasNext(); ) {
            DerivedMeasurement dm = (DerivedMeasurement) it.next();
            tmpls.add(dm.getTemplate().getMeasurementTemplateValue());
        }
            
        return tmpls;
    }

    /**
     * Get the the designated measurement template for the given resources
     * @return array of derived measurement IDs
     * @ejb:interface-method
     */
    public List getDesignatedTemplates(int sessionId, AppdefEntityID[] ids)
        throws SessionNotFoundException, SessionTimeoutException,
               MeasurementNotFoundException, AppdefEntityNotFoundException,
               PermissionException {
        HashSet cats = new HashSet(4);
        cats.add(MeasurementConstants.CAT_AVAILABILITY);
        cats.add(MeasurementConstants.CAT_UTILIZATION);
        cats.add(MeasurementConstants.CAT_THROUGHPUT);
        cats.add(MeasurementConstants.CAT_PERFORMANCE);
    
        return getDesignatedTemplates(sessionId, ids, cats);
    }

    /**
     * Get the the designated measurement template for the given resources
     * and corresponding categories.
     * @return array of derived measurement IDs
     * @ejb:interface-method
     */
    public List getDesignatedTemplates(int sessionId, AppdefEntityID[] ids,
                                       Set cats)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, PermissionException {
        if (ids.length == 0)
            return null;
        
        TreeMap ret = new TreeMap();
        for (int i = 0; i < ids.length; i++) {
            List metrics = getDesignatedTemplates(sessionId, ids[i], cats);
            for (Iterator it = metrics.iterator(); it.hasNext(); ) {
                MeasurementTemplateValue mtv =
                    (MeasurementTemplateValue) it.next();
                ret.put(mtv.getName(), mtv);
            }
        }

        return new ArrayList(ret.values());
    }

    /**
     * Get the the designated measurement template for the autogroup given
     * a type and corresponding category.
     * @param ctype the AppdefEntityTypeID of the AG members
     * @return array of derived measurement IDs
     * @ejb:interface-method
     */
    public List getAGDesignatedTemplates(int sessionId, AppdefEntityID[] aids, 
                                         AppdefEntityTypeID ctype, Set cats) 
        throws SessionNotFoundException, SessionTimeoutException,
               MeasurementNotFoundException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        // Find the autogroup members
        List entIds = getAGMemberIds(subject, aids, ctype);
        
        for (Iterator it = entIds.iterator(); it.hasNext(); ) {
            AppdefEntityID aeId = (AppdefEntityID) it.next();
            List templs = getDesignatedTemplates(sessionId, aeId, cats);
            if (templs.size() > 0) {
                return templs;
            }
        }
    
        // Throw a MeasurementNotFoundException here
        throw new MeasurementNotFoundException(
            "Autogroup for : " + aids + " of type : " + ctype +
            " does not contain designated measurements");
    }

    /**
     * Create list of measurements for a resource
     * @param id the resource ID
     * @ejb:interface-method
     */
    public void createMeasurements(int sessionId, AppdefEntityID id,
                                   Integer[] tids)
        throws SessionTimeoutException, SessionNotFoundException,
               ConfigFetchException, EncodingException, PermissionException,
               TemplateNotFoundException, AppdefEntityNotFoundException,
               GroupNotCompatibleException, MeasurementCreateException {
        createMeasurements(sessionId, id, tids, 0);
    }

    /**
     * Create list of measurements for a resource
     * @param id the resource ID
     * @ejb:interface-method
     */
    public void createMeasurements(int sessionId, AppdefEntityID id,
                                   Integer[] tids, long interval)
        throws SessionTimeoutException, SessionNotFoundException,
               ConfigFetchException, EncodingException, PermissionException,
               TemplateNotFoundException, AppdefEntityNotFoundException,
               GroupNotCompatibleException, MeasurementCreateException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            // Recursively do this for each of the group members
            List grpMembers =
                GroupUtil.getCompatGroupMembers(subject, id, null,
                                                PageControl.PAGE_ALL);
        
            for (Iterator it = grpMembers.iterator(); it.hasNext(); ) {
                AppdefEntityID aeid = (AppdefEntityID) it.next();
                
                if (interval > 0)
                    createMeasurements(sessionId, aeid, tids, interval);
                else
                    createMeasurements(sessionId, aeid, tids);
            }
        }
        else {
            ConfigResponse mergedCR = getConfigManager()
                .getMergedConfigResponse(subject, 
                                         ProductPlugin.TYPE_MEASUREMENT, 
                                         id, true);
            
            if (interval > 0) {
                long[] intervals = new long[tids.length];
                Arrays.fill(intervals, interval);
    
                getDerivedMeasurementManager().createMeasurements(
                    subject, id, tids, intervals, mergedCR);
            }
            else {
                getDerivedMeasurementManager().createMeasurements(
                    subject, id, tids, mergedCR);
            }
        }
    }

    /**
     * Create a list of measurements for an autogroup resource
     * @param parentAeid - the parent resource of the autogroup
     * @param tids - template ids to create
     * @ejb:interface-method
     */
    public void createAGMeasurements(int sessionId, 
                                     AppdefEntityID parentAeid,
                                     AppdefEntityTypeID childType,
                                     Integer[] tids)
        throws SessionTimeoutException, SessionNotFoundException,
               ConfigFetchException, EncodingException, PermissionException,
               TemplateNotFoundException, AppdefEntityNotFoundException,
               GroupNotCompatibleException, MeasurementCreateException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        List children = getAGMemberIds(subject, parentAeid, childType);
        for(int i = 0; i < children.size(); i++) {
            AppdefEntityID aKid = (AppdefEntityID)children.get(i);
            createMeasurements(sessionId, aKid, tids);
        }
    }
    
    /**
     * Update the measurements - set the interval
     * @ejb:interface-method
     */
    public void updateMeasurements(int sessionID, Integer[] mids, long interval)
        throws SessionTimeoutException, SessionNotFoundException,
               MeasurementNotFoundException, MeasurementCreateException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        getDerivedMeasurementManager().enableMeasurements(subject, mids,
                                                               interval);
    }

    /** Update the measurements - set the interval
     * @param id the resource ID
     * @param tids the array of template ID's
     * @param interval the new interval value
     * @ejb:interface-method
     */
    public void updateMeasurements(int sessionId, AppdefEntityID id,
                                   Integer[] tids, long interval)
        throws MeasurementNotFoundException,
               SessionTimeoutException, SessionNotFoundException,
               TemplateNotFoundException, AppdefEntityNotFoundException,
               GroupNotCompatibleException, MeasurementCreateException,
               ConfigFetchException, PermissionException, EncodingException {
        createMeasurements(sessionId, id, tids, interval);
    }
    
    /**
     * Update measurements for the members of an autogroup
     * @param parentid - the parent resource of the autogroup
     * @param ctype - the type of child resource
     * @param tids - template ids to update
     * @param interval - the interval to set
     * @ejb:interface-method
     */
    public void updateAGMeasurements(int sessionId, 
                                     AppdefEntityID parentid, 
                                     AppdefEntityTypeID ctype,
                                     Integer[] tids,
                                     long interval)
        throws MeasurementNotFoundException, SessionTimeoutException,
               SessionNotFoundException, TemplateNotFoundException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               MeasurementCreateException, ConfigFetchException,
               PermissionException, EncodingException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        List kids = getAGMemberIds(subject, parentid, ctype);
        for(int i = 0; i < kids.size(); i++) {
            // Do create, because we want to create or update
            AppdefEntityID kid = (AppdefEntityID)kids.get(i);
            createMeasurements(sessionId, kid, tids, interval);
        }
    }
    
    /** Remove all measurements for an instance
     * @param id the appdef entity ID
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void removeMeasurements(int sessionId, AppdefEntityID id)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, RemoveException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        // First remove all the Derived Measurements
        AppdefEntityID[] ids = new AppdefEntityID[] { id };
        getDerivedMeasurementManager().removeMeasurements(
            subject, id, ids);

        // Then remove the Raw Measurements
        getRawMeasurementManager().removeMeasurements(ids);
    }

    /**
     * Disable all measurements for an instance
     * @param id the resource's ID
     * @ejb:interface-method
     */
    public void disableMeasurements(int sessionId, AppdefEntityID id)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        getDerivedMeasurementManager().disableMeasurements(subject, id);
    }

    /** 
     * Disable measurements so that they no longer collect data
     * @param mids the array of measurement ID's
     * @ejb:interface-method
     */
    public void disableMeasurements(int sessionId, Integer[] mids)
        throws SessionTimeoutException, SessionNotFoundException,
               MeasurementNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        getDerivedMeasurementManager().disableMeasurements(subject, mids);
    }

    /**
     * Disable all measurements for a resource
     * @param id the resource's ID
     * @param tids the array of measurement ID's
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void disableMeasurements(int sessionId, AppdefEntityID id,
                                   Integer[] tids)
        throws SessionTimeoutException, SessionNotFoundException,
               RemoveException, AppdefEntityNotFoundException,
               GroupNotCompatibleException, PermissionException,
               TemplateNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        if (id == null) {
            getTemplateManager().enableTemplateByDefault(tids, false);
        }
        else if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            // Recursively do this for each of the group members
            List grpMembers =
                GroupUtil.getCompatGroupMembers(subject, id, null,
                                                PageControl.PAGE_ALL);
    
            for (Iterator it = grpMembers.iterator(); it.hasNext();) {
                getDerivedMeasurementManager().disableMeasurements(
                    subject, (AppdefEntityID) it.next(), tids);
            }
        }
        else {
            getDerivedMeasurementManager().disableMeasurements(
                subject, id, tids);
        }
    }

    /**
     * Disable all measurements for a resource
     * @param tids the array of measurement ID's
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public void disableAGMeasurements(int sessionId, AppdefEntityID parentId,
                                      AppdefEntityTypeID childType,
                                      Integer[] tids)
        throws SessionTimeoutException, SessionNotFoundException,
               RemoveException, AppdefEntityNotFoundException,
               GroupNotCompatibleException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        List grpMembers = getAGMemberIds(subject, parentId, childType);
    
        for (Iterator it = grpMembers.iterator(); it.hasNext();) {
            getDerivedMeasurementManager().disableMeasurements(
                subject, (AppdefEntityID) it.next(), tids);
        }
    }

    /**
     * Get the count of enabled metrics for an entity
     * @ejb:interface-method
     */
    public int getEnabledMetricsCount(int sessionId, AppdefEntityID id)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP)
            return findGroupMeasurements(sessionId, id, null,
                                              PageControl.PAGE_ALL).size();
    
        return getDerivedMeasurementManager()
                   .getEnabledMetricsCount(subject, id);
    }

    /**
     * Get the the designated measurement for the given resource
     * and corresponding category.
     * @return array of derived measurement IDs
     */
    private List getDesignatedMetrics(int sessionId, AppdefEntityID id, Set cats) 
        throws SessionNotFoundException, SessionTimeoutException,
               MeasurementNotFoundException, AppdefEntityNotFoundException,
               PermissionException {
        if (cats.size() == 0) {
            throw new MeasurementNotFoundException("No categories specified");
        }
        
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        List metrics = null;
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION) {
            // Get the appointed front-end service
            AppdefEntityValue aeval = new AppdefEntityValue(id, subject);
            ApplicationValue app = (ApplicationValue) aeval.getResourceValue();
            
            AppServiceValue[] appSvcs = app.getAppServiceValues();
            for (int i = 0; i < appSvcs.length; i++) {
                // Let's try it
                if (appSvcs[i].getIsCluster()) {
                    if (appSvcs[i].getServiceCluster() == null)
                        continue;
                        
                    id = new AppdefEntityID(
                        AppdefEntityConstants.APPDEF_TYPE_GROUP, 
                        appSvcs[i].getServiceCluster().getGroupId());
                }
                else {
                    // Make sure this is a valid service
                    if (appSvcs[i].getService() == null)
                        continue;
                    
                    // Get the metrics for the service                        
                    id = appSvcs[i].getService().getEntityId();
                }

                // Require an entry point
                if (appSvcs[i].getIsEntryPoint()) {
                    // Recursively call with the entry point
                    try {
                        metrics = getDesignatedMetrics(sessionId, id, cats);
                        break;
                    } catch (MeasurementNotFoundException ignore) {
                        // No measurement to be used
                    } catch (PermissionException ignore) {
                        // Can't use this service, because user can't see it
                    }
                }
                else if (metrics == null &&
                         cats.contains(MeasurementConstants.CAT_AVAILABILITY)) {
                    DerivedMeasurement dm = findAvailabilityMetric(subject, id);
                    
                    if (dm != null) {
                        metrics = new ArrayList(1);
                        metrics.add(dm);
                    }
                }
            }
        }
        else if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            List grpMembers;
            try {
                grpMembers =
                    GroupUtil.getCompatGroupMembers(subject, id, null,
                                                    PageControl.PAGE_ALL);
            } catch (GroupNotCompatibleException e) {
                throw new MeasurementNotFoundException(
                    "Incompatible group " + id +
                    " cannot have a common designated measurement");
            }
            
            // Go through the group members and return the first measurement
            // that we find
            for (Iterator it = grpMembers.iterator(); it.hasNext(); ) {
                metrics = findDesignatedMetrics(
                        subject, (AppdefEntityID) it.next(), cats);
    
                if (metrics != null && metrics.size() > 0)
                    break;
            }
        }
        else {
            metrics = findDesignatedMetrics(subject, id, cats);
        }
        
        // Make sure we have valid metrics
        if (metrics == null || metrics.size() == 0)
            throw new MeasurementNotFoundException(
                "Designated metric not found for " + id);
    
        // Now iterate through and throw out the metrics we don't need
        for (Iterator it = metrics.iterator(); it.hasNext(); ) {
            DerivedMeasurement dm = (DerivedMeasurement) it.next();
            if (!cats.contains(dm.getTemplate().getCategory().getName()))
                it.remove();
        }
    
        return metrics;
    }

    /**
     * Find a measurement using measurement id
     * @param id measurement id
     * @ejb:interface-method
     */
    public DerivedMeasurementValue getMeasurement(int sessionID, Integer id)
        throws SessionTimeoutException, SessionNotFoundException,
               MeasurementNotFoundException {
        return getDerivedMeasurementManager().getMeasurement(id)
            .getDerivedMeasurementValue();
    }

    /**
     * Find a measurement using the alias and appdef entity ID
     * @ejb:interface-method
     */
    public DerivedMeasurementValue getMeasurement(int sessionID, 
                                                  AppdefEntityID id,
                                                  String alias)
        throws SessionTimeoutException, SessionNotFoundException,
               MeasurementNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionID);
        return getDerivedMeasurementManager().getMeasurement(
            subject, id, alias);
    }

    /** Get the live measurement value
     * @param mid The measurement ID to get
     * @ejb:interface-method
     */
     public MetricValue getLiveMeasurementValue(int sessionId, Integer mid)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, MeasurementNotFoundException,
               EvaluationException, LiveMeasurementException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        Integer[] mids = new Integer[] { mid };
        return getDerivedMeasurementManager()
                    .getLiveMeasurementValues(subject, mids)[0];
    }

    /**
     * Get the last metric values for the given template IDs.
     *
     * @param tids The template IDs to get
     * @ejb:interface-method
     */
    public MetricValue[] getLastMetricValue(int sessionId,
                                            AppdefEntityID aeid,
                                            Integer[] tids)
        throws SessionTimeoutException, SessionNotFoundException,
        PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        Integer mids[] = new Integer[tids.length];
        long interval = 0;
        for (int i = 0; i < tids.length; i++) {
            try {
                DerivedMeasurement dmv = getDerivedMeasurementManager()
                    .findMeasurement(tids[i], aeid.getId());
                mids[i] = dmv.getId();
                interval = Math.max(interval, dmv.getInterval());
            } catch (MeasurementNotFoundException e) {
                mids[i] = new Integer(0);
            }
        }

        return getLastMetricValue(sessionId,  mids, interval);
    }

    /**
     * Get the last metric data for the array of measurement ids.
     *
     * @param mids  The array of measurement ids to get metrics for
     * @param interval The allowable time in ms to go back looking for data.
     * @ejb:interface-method
     */
    public MetricValue[] getLastMetricValue(int sessionId, Integer mids[],
                                            long interval)
    {
        MetricValue[] ret = new MetricValue[mids.length];
        Map data = getDataMan().getLastDataPoints(mids,
                                                  System.currentTimeMillis() -
                                                      (3 * interval));
        for (int i = 0; i < mids.length; i++) {
            if (data.containsKey(mids[i]))
                ret[i] = (MetricValue) data.get(mids[i]);
        }

        return ret;
    }

    /*
     * Private function to find the group measurements
     */
    private PageList findGroupMeasurements(int sessionId, AppdefEntityID gid,
                                           String cat, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        List grpMembers =
            GroupUtil.getCompatGroupMembers(subject, gid, null, 
                                            PageControl.PAGE_ALL);
        return findGroupMeasurements(sessionId, grpMembers, cat, pc);
    }
    
    /*
     * Private function to find the group measurements
     */
    private PageList findGroupMeasurements(int sessionId, List grpMembers,
                                           String cat, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        String mtype = null;
        IntHashMap summaryMap = new IntHashMap();
        for (Iterator it = grpMembers.iterator(); it.hasNext(); ) {
            AppdefEntityID id = (AppdefEntityID) it.next();
            // Get the monitorable type from the first ID
            if (mtype == null) {
                AppdefEntityValue aeVal = new AppdefEntityValue(id, subject);
                mtype = aeVal.getMonitorableType();
            }
    
            // Get the list of measurements for this resource
            PageList metrics =
                getDerivedMeasurementManager().findMeasurements(
                    subject, id, cat, PageControl.PAGE_ALL);
                
            // Iterate through the measurements to get the interval
            for (Iterator it2 = metrics.iterator(); it2.hasNext(); ) {
                DerivedMeasurementValue dmv =
                    (DerivedMeasurementValue) it2.next();
    
                MeasurementTemplateValue tmpl = dmv.getTemplate();
                GroupMetricDisplaySummary gmds = (GroupMetricDisplaySummary)
                    summaryMap.get(tmpl.getId().intValue());
                if (gmds == null) {
                    gmds = new GroupMetricDisplaySummary(
                        tmpl.getId().intValue(), tmpl.getName(),
                        tmpl.getCategory().getName());
                    gmds.setInterval(dmv.getInterval());
                    
                    // Now put it into the map
                    summaryMap.put(tmpl.getId().intValue(), gmds);
                }
    
                // Increment the member count
                gmds.incrementMember();
                
                // Check the interval
                if (gmds.getInterval() != dmv.getInterval()) {
                    // Set it to 0, because the intervals don't agree
                    gmds.setInterval(0);
                }
            }
        }
    
        // No mtype == no group members
        if (mtype == null) {
            return new PageList();
        }
        
        PageList tmpls =
            getTemplateManager().findTemplates(mtype, cat, null, pc);
            
        PageList result = new PageList();
        for (Iterator it = tmpls.iterator(); it.hasNext(); ) {
            MeasurementTemplateValue tmpl =
                (MeasurementTemplateValue) it.next();
            GroupMetricDisplaySummary gmds = (GroupMetricDisplaySummary)
                summaryMap.get(tmpl.getId().intValue());
    
            if (gmds == null) {
                gmds = new GroupMetricDisplaySummary(
                    tmpl.getId().intValue(), tmpl.getName(),
                    tmpl.getCategory().getName());
            }
            
            // Set the total number
            gmds.setTotalMembers(grpMembers.size());
    
            result.add(gmds);
        }
        
        // Total size is equal to the total size of the templates
        result.setTotalSize(tmpls.getTotalSize());
        
        return result;
    }

    /** Retrieve a measurement for a specific instance
     * @return a PageList of DerivedMeasurementValue objects
     * @ejb:interface-method
     */
    public DerivedMeasurementValue findMeasurement(int sessionId,
                                                   Integer tid,
                                                   AppdefEntityID id)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, MeasurementNotFoundException,
               AppdefEntityNotFoundException {
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP)
            return (DerivedMeasurementValue)
                findMeasurements(sessionId, tid,
                                      new AppdefEntityID[] { id }).get(0);

        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getDerivedMeasurementManager().findMeasurement(subject, tid,
                                                                   id.getId());
    }

    /** Retrieve list of measurements for a specific instance
     * @return a PageList of DerivedMeasurementValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurements(int sessionId, AppdefEntityID id,
                                     PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP)
            return findGroupMeasurements(sessionId, id, null, pc);
                   
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getDerivedMeasurementManager().findMeasurements(
            subject, id, null, pc);
    }

    /** Retrieve list of measurements for a specific template and entities
     * @param tid the template ID
     * @param entIds the array of entity IDs
     * @return a List of DerivedMeasurementValue objects
     * @ejb:interface-method
     */
    public List findMeasurements(int sessionId, Integer tid,
                                 AppdefEntityID[] entIds)
        throws SessionTimeoutException, SessionNotFoundException,
               MeasurementNotFoundException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        List ids = new ArrayList();
        
        // Form an array of ID's
        int type = entIds[0].getType();
        for (int i = 0; i < entIds.length; i++) {
            if (entIds[i].getType() != type)
                throw new MeasurementNotFoundException("Entity type != " +type);
            
            if (type == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
                try {
                    Integer[] memIds = getGroupMemberIDs(subject,
                                                              entIds[i]);
                    ids.addAll(Arrays.asList(memIds));
                } catch (GroupNotCompatibleException e) {
                    throw new MeasurementNotFoundException(
                        "Incompatible group " + entIds[i] +
                        " cannot have a common designated measurement");
                }
            }
            else {
                ids.add(entIds[i].getId());
            }
        }
        return getDerivedMeasurementManager().findMeasurements(
            subject, tid, (Integer[]) ids.toArray(new Integer[0]));
    }

    /**
     * Get the enabled measurements for an auto group
     * @param parentId - the parent resource appdefEntityID
     * @param childType - the type of child in the autogroup
     * @return a PageList of DerivedMeasurementValue objects
     * @ejb:interface-method
     */
    public PageList findEnabledAGMeasurements(int sessionId,
                                              AppdefEntityID parentId,
                                              AppdefEntityTypeID childType,
                                              String cat,
                                              PageControl pc)
    throws SessionNotFoundException, SessionTimeoutException,
           AppdefEntityNotFoundException, GroupNotCompatibleException,
           PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return findGroupMeasurements(sessionId, 
                                          getAGMemberIds(subject, 
                                                              parentId, 
                                                              childType), 
                                          cat, 
                                          pc);
    }
    
    /** Retrieve list of measurements for a specific instance and category
     * @return a PageList of DerivedMeasurementValue objects
     * @ejb:interface-method
     */
    public PageList findEnabledMeasurements(int sessionId, AppdefEntityID id,
                                            String cat, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP)
            return findGroupMeasurements(sessionId, id, cat, pc);

        AppdefEntityValue aeVal = new AppdefEntityValue(id, subject);
        String mtype = aeVal.getMonitorableType();

        IntHashMap dmvs = new IntHashMap();
        List enabledMetrics = getDerivedMeasurementManager()
            .findEnabledMeasurements(subject, id, cat);
        
        for (Iterator it = enabledMetrics.iterator(); it.hasNext(); ) {
            DerivedMeasurement dmv = (DerivedMeasurement) it.next();
            dmvs.put(dmv.getTemplate().getId().intValue(), dmv);
        }

        // Create MetricConfigSummary beans
        PageList tmpls =
            getTemplateManager().findTemplates(mtype, cat, null, pc);
        ArrayList beans = new ArrayList(tmpls.size());
        for (Iterator it = tmpls.iterator(); it.hasNext(); ) {
            MeasurementTemplateValue mtv = (MeasurementTemplateValue) it.next();
            MetricConfigSummary mcs =
                new MetricConfigSummary(mtv.getId().intValue(), mtv.getName(),
                                        cat);
            
            DerivedMeasurement dmv =
                (DerivedMeasurement) dmvs.get(mtv.getId().intValue());
            if (dmv != null) {
                mcs.setInterval(dmv.getInterval());
            }
            
            beans.add(mcs);
        }
        
        return new PageList(beans, tmpls.getTotalSize());
    }

    /**
     * Dumps data for a specific measurement
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(int sessionId, Integer mid,
                                        long begin, long end, PageControl pc) {
        try {
            return getDataMan().getHistoricalData(mid, begin, end, pc);
        } catch (DataNotAvailableException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Dumps data for a specific measurement on an interval
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(int sessionId, Integer mid,
                                        long begin, long end, long interval,
                                        boolean returnNulls, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               MeasurementNotFoundException, DataNotAvailableException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        DerivedMeasurement dmv =
            getDerivedMeasurementManager().getMeasurement(mid);
        MeasurementTemplate tmpl = dmv.getTemplate();
        
        Integer[] mids = new Integer[] { mid };
        return getDataMan().getHistoricalData(mids, begin, end, interval,
                                              tmpl.getCollectionType(),
                                              returnNulls, pc);
    }

    /**
     * Dumps data for a specific measurement template for an instance based on
     * an interval
     * @param tid the template ID
     * @param aid the AppdefEntityID
     * @param begin the beginning of the time range
     * @param end the end of the time range
     * @param interval the time interval at which the data should be calculated
     * @param returnNulls whether or not nulls should be inserted for no data
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(int sessionId, Integer tid,
                                        AppdefEntityID aid, long begin,
                                        long end, long interval,
                                        boolean returnNulls, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException, AppdefEntityNotFoundException,
               PermissionException, MeasurementNotFoundException {

        AuthzSubjectValue subject = manager.getSubject(sessionId);

        MeasurementTemplate tmpl = getTemplateManager().getTemplate(tid);
            
        Integer[] mids = getMetricIdsForResource(subject, aid, tmpl);
        if (mids == null || mids.length == 0) {
            throw new MeasurementNotFoundException(
                "There is no measurement for " + aid + " with template " + tid);
        }

        return getDataMan().getHistoricalData(
            mids, begin, end, interval, tmpl.getCollectionType(), returnNulls,
            pc);
    }

    /**
     * Dumps data for a specific measurement template for an auto-group based on
     * an interval.
     *
     * @param tid the measurement template id
     * @param aid the entity id
     * @param ctype the auto-group child type
     * @param begin start of interval
     * @param end end of interval
     * @param interval the interval
     * @param returnNulls whether or not to return nulls
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(int sessionId, Integer tid,
                                        AppdefEntityID aid,
                                        AppdefEntityTypeID ctype,
                                        long begin, long end, long interval,
                                        boolean returnNulls, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException, AppdefEntityNotFoundException,
               PermissionException, MeasurementNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        // Find the autogroup members
        List entIds = getAGMemberIds(subject, aid, ctype);
        
        return findMeasurementData(sessionId, tid, entIds, begin, end, interval,
                                   returnNulls, pc);
    }

    /**
     * Dumps data for a specific measurement template for an auto-group based on
     * an interval.
     *
     * @param tid the measurement template id
     * @param begin start of interval
     * @param end end of interval
     * @param interval the interval
     * @param returnNulls whether or not to return nulls
     *        associated with the platform
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(int sessionId, Integer tid,
                                        List entIds,
                                        long begin, long end, long interval,
                                        boolean returnNulls, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        MeasurementTemplate tmpl = getTemplateManager().getTemplate(tid);

        // Find the measurement IDs of the members in the autogroup for the
        // template
        Integer[] ids = new Integer[entIds.size()];
        Iterator it = entIds.iterator();
        for (int i = 0; it.hasNext(); i++) {
            AppdefEntityID entId = (AppdefEntityID) it.next();
            ids[i] = entId.getId();
        }
        
        Integer[] mids =
            getDerivedMeasurementManager().findMeasurementIds(
                subject, tid, ids);
    
        return getDataMan().getHistoricalData(mids, begin, end, interval,
            tmpl.getCollectionType(), returnNulls, pc);
    }

    /**
     * Dumps data for a specific measurement template for an instance based on
     * an interval
     * @param aid the AppdefEntityID
     * @param begin the beginning of the time range
     * @param end the end of the time range
     * @param interval the time interval at which the data should be calculated
     * @param returnNulls whether or not nulls should be inserted for no data
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(String user, AppdefEntityID aid,
                                        MeasurementTemplateValue tmpl,
                                        long begin, long end, long interval,
                                        boolean returnNulls, PageControl pc)
        throws LoginException, ApplicationException, ConfigPropertyException {
        int sessionId = getAuthManager().getUnauthSessionId(user);
        return findMeasurementData(sessionId, aid, tmpl, begin, end, interval,
                                   returnNulls, pc); 
    }

    /** Dumps data for a specific measurement template for an instance based on
     * an interval
     * @param aid the AppdefEntityID
     * @param tmpl the complete MeasurementTemplate value object
     * @param begin the beginning of the time range
     * @param end the end of the time range
     * @param interval the time interval at which the data should be calculated
     * @param returnNulls whether or not nulls should be inserted for no data
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findMeasurementData(int sessionId, AppdefEntityID aid,
                                        MeasurementTemplateValue tmpl,
                                        long begin, long end, long interval,
                                        boolean returnNulls, PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException, AppdefEntityNotFoundException,
               PermissionException, MeasurementNotFoundException {
            
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        Integer[] mids;
        StopWatch watch = new StopWatch();

        if (aid.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION &&
            tmpl.getCategory().getName().equals(
                MeasurementConstants.CAT_AVAILABILITY)) {
            // Special case for application availability
            log.debug("BEGIN findMeasurementData()");
            
            AppdefEntityValue aeval = new AppdefEntityValue(aid, subject);
            
            // Get the flattened list of services
            AppdefEntityID[] serviceIds = aeval.getFlattenedServiceIds();
            
            Map midMap = getDerivedMeasurementManager()
                             .findDesignatedMeasurementIds(
                                 subject, serviceIds,
                                 MeasurementConstants.CAT_AVAILABILITY);
            mids = (Integer[]) midMap.values().toArray(new Integer[0]);
        }
        else {
            MeasurementTemplate template =
                getTemplateManager().getTemplate(tmpl.getId());
            mids = getMetricIdsForResource(subject, aid, template);
            if (mids == null || mids.length == 0) {
                throw new MeasurementNotFoundException(
                        "There is no measurement for " + aid + " with template " + 
                        tmpl.getId());
            }
        }

        try {
            return getDataMan().getHistoricalData(mids, begin, end, interval,
                tmpl.getCollectionType(), returnNulls, pc);
        } finally {
            if (aid.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION)
                log.debug("END findMeasurementData() - " + watch.getElapsed() +
                          " msec");
        }
    }

    /**
     * Dumps a specific number of data points for a specific
     * measurement template ID and resource id
     * @return a List of MetricValue objects
     * @ejb:interface-method
     */
    public List findMeasurementData(int sessionId, Integer tid, int iid,
                                    int count)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException, MeasurementNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        DerivedMeasurementValue dmv =
            getDerivedMeasurementManager().findMeasurement(
                subject, tid, new Integer(iid));
    
        return findMeasurementData(sessionId, dmv.getId(), count);
    }

    /**
     * Dumps a specific number of data points for a specific measurement ID
     * @return a List of MetricValue objects
     * @ejb:interface-method
     */
    public List findMeasurementData(int sessionId, Integer mid, int count)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
                
        return getDataMan().getLastHistoricalData(mid, count);
    }

    /**
     * Dumps data for a specific measurement template for an auto-group based on
     * an interval.
     * @param ctype the auto-group child type
     * @param begin start of interval
     * @param end end of interval
     * @param interval the interval
     * @param returnNulls whether or not to return nulls
     * @return a PageList of MetricValue objects
     * @throws ConfigPropertyException
     * @throws ApplicationException
     * @throws LoginException
     * @ejb:interface-method
     */
    public PageList findAGMeasurementData(String user, AppdefEntityID[] aids,
                                          MeasurementTemplateValue tmpl,
                                          AppdefEntityTypeID ctype,
                                          long begin, long end, long interval,
                                          boolean returnNulls, PageControl pc)
        throws LoginException, ApplicationException, ConfigPropertyException {
        int sessionId = getAuthManager().getUnauthSessionId(user);
        return findAGMeasurementData(sessionId, aids, tmpl, ctype, begin, end,
                                     interval, returnNulls, pc);
    }

    /**
     * Dumps data for a specific measurement template for an auto-group based on
     * an interval.
     * @param ctype the auto-group child type
     * @param begin start of interval
     * @param end end of interval
     * @param interval the interval
     * @param returnNulls whether or not to return nulls
     * @return a PageList of MetricValue objects
     * @ejb:interface-method
     */
    public PageList findAGMeasurementData(int sessionId, AppdefEntityID[] aids,
                                          MeasurementTemplateValue tmpl,
                                          AppdefEntityTypeID ctype,
                                          long begin, long end, long interval,
                                          boolean returnNulls,
                                          PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               DataNotAvailableException, AppdefEntityNotFoundException,
               PermissionException, MeasurementNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        // Find the autogroup members
        List entIds = getAGMemberIds(subject, aids, ctype);
    
        // Find the measurement IDs of the members in the autogroup for the
        // template
        Integer[] ids = new Integer[entIds.size()];
        Iterator it = entIds.iterator();
        for (int i = 0; it.hasNext(); i++) {
            AppdefEntityID entId = (AppdefEntityID) it.next();
            ids[i] = entId.getId();
        }
        
        Integer[] mids =
            getDerivedMeasurementManager().findMeasurementIds(
                subject, tmpl.getId(), ids);
    
        return getDataMan().getHistoricalData(mids, begin, end, interval,
            tmpl.getCollectionType(), returnNulls, pc);
    }

    /**
     * Returns metadata for particular measurement
     * @ejb:interface-method
     */
    public List findMetricMetadata(int sessionId, AppdefEntityID aid,
                                   AppdefEntityTypeID ctype, Integer tid)
        throws SessionNotFoundException, SessionTimeoutException,
               GroupNotCompatibleException,
               AppdefEntityNotFoundException, ApplicationNotFoundException,
               TemplateNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        // Get the template
        getTemplateManager().getTemplate(tid);
        
        List entities;

        if (aid.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            List memberIds =
                GroupUtil.getCompatGroupMembers(subject, aid, null);
            entities = new ArrayList();
            for (Iterator iter = memberIds.iterator(); iter.hasNext();) {
                AppdefEntityID anId = (AppdefEntityID) iter.next();
                AppdefEntityValue aev = new AppdefEntityValue(anId, subject);
                entities.add(aev.getResourceValue());                
            }
        } else if (ctype != null) {
            // if a child type was specified, then the template must be
            // intended for the autogroup of children of that type
            AppdefEntityValue entVal = new AppdefEntityValue(aid, subject);
            switch (ctype.getType()) {
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                    entities =
                        entVal.getAssociatedPlatforms(PageControl.PAGE_ALL);
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                    // Get server IDs
                    entities = entVal.getAssociatedServers(ctype.getId(),
                        PageControl.PAGE_ALL);
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                    // Get service IDs
                    entities = entVal.getAssociatedServices(ctype.getId(),
                        PageControl.PAGE_ALL);
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Unable to determine autogroup members for appdef type: " +
                        aid.getType());
            }
        } else {
            entities = new ArrayList();
            AppdefEntityValue entVal = new AppdefEntityValue(aid, subject);
            entities.add(entVal.getResourceValue());
        }
        
        Integer[] iids = new Integer[entities.size()];
        IntHashMap resourceMap = new IntHashMap();
        Iterator iter = entities.iterator();
        for (int i = 0; iter.hasNext(); i++) {
            AppdefResourceValue resource = (AppdefResourceValue) iter.next();
            iids[i] = resource.getEntityId().getId();
            resourceMap.put(iids[i].intValue(), resource);
        }

        // get measurement summaries, enriched with metadata
        // tastes good and it's good for you 
        List mds = new ArrayList();
        List mms = getDerivedMeasurementManager()
                        .findMeasurements(subject, tid, iids);
        for (iter = mms.iterator(); iter.hasNext();) {
            DerivedMeasurementValue mm = (DerivedMeasurementValue) iter.next();
            Integer instanceId = mm.getInstanceId();
            AppdefResourceValue resource =
                (AppdefResourceValue)resourceMap.get(instanceId.intValue());
            
            // Fetch the last data point
            MetricValue mv = null;
            try {
                Collection mvs =
                    getDataMan().getLastHistoricalData(mm.getId(), 1);
                
                if (mvs.size() > 0) {
                    mv = (MetricValue) mvs.iterator().next();
                }
            } catch (DataNotAvailableException e) {
                // mv still NULL
            }
            
            MeasurementMetadataSummary summary =
                new MeasurementMetadataSummary(mm, mv, resource);
            mds.add(summary);                        
        }
        return mds;
    }

    /**
     * Method findMetrics.
     * 
     * When the entId is a server, return all of the metrics that
     * are instances of the measurement templates for the server's
     * type.  In this case, the MetricDisplaySummary's attributes to
     * show the number collecting doesn't make sense; 
     * showNumberCollecting should false for each bean.
     * <p>
     * When the entId is a platform, return all of the metrics that
     * are instances of the measurement templates for the platform's
     * type.  In this case, the MetricDisplaySummary's attributes to
     * show the number collecting doesn't make sense; 
     * showNumberCollecting should false for each bean.
     * </p>
     * <p>
     * When the entId is compatible group of servers or platforms, 
     * return all of the metrics for the type.  Each MetricDisplaySummary
     * actually represents the metrics summarized for all of the group
     * members (cumulative/averaged as appropriate), 
     * showNumberCollecting should be true and the numberCollecting
     * as well as the total number of members assigned in each bean.
     * </p>
     * 
     * @return Map keyed on the category (String), values are List's of 
     * MetricDisplaySummary beans
     * @see org.hyperic.hq.bizapp.shared.uibeans.MetricDisplaySummary
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public MetricDisplaySummary findMetric(int sessionId, AppdefEntityID aeid,
                                           AppdefEntityTypeID ctype,
                                           Integer tid, long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefEntityNotFoundException,
               AppdefCompatException, MeasurementNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        List resources;
        try {
            resources = getResourceIds(subject, aeid, ctype);
        } catch (GroupNotCompatibleException e) {
            throw new MeasurementNotFoundException(
                "Incompatible group " + aeid +
                " cannot have a common measurement" + tid);
        }
    
        // Just one metric
        List mtids = new ArrayList();
        mtids.add(tid);
        
        // Look up the metric summaries of all associated resources
        Map results = getResourceMetrics(subject, resources, mtids, begin, end,
                                         null);

        // Should only be one
        if (log.isDebugEnabled()) {
            log.debug("getResourceMetrics() returned " + results.size());
        }

        if (results.size() > 0) {
            Iterator it = results.values().iterator();
            Collection coll = (Collection) it.next();
            it = coll.iterator();
            MetricDisplaySummary summary = (MetricDisplaySummary) it.next();
            
            return summary;
        }
        
        return null;
    }

    /**
     * Method findMetrics.
     * 
     * When the entId is a server, return all of the metrics that
     * are instances of the measurement templates for the server's
     * type.  In this case, the MetricDisplaySummary's attributes to
     * show the number collecting doesn't make sense; 
     * showNumberCollecting should false for each bean.
     * <p>
     * When the entId is a platform, return all of the metrics that
     * are instances of the measurement templates for the platform's
     * type.  In this case, the MetricDisplaySummary's attributes to
     * show the number collecting doesn't make sense; 
     * showNumberCollecting should false for each bean.
     * </p>
     * <p>
     * When the entId is compatible group of servers or platforms, 
     * return all of the metrics for the type.  Each MetricDisplaySummary
     * actually represents the metrics summarized for all of the group
     * members (cumulative/averaged as appropriate), 
     * showNumberCollecting should be true and the numberCollecting
     * as well as the total number of members assigned in each bean.
     * </p>
     * 
     * @return Map keyed on the category (String), values are List's of 
     * MetricDisplaySummary beans
     * @see org.hyperic.hq.bizapp.shared.uibeans.MetricDisplaySummary
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public MetricDisplaySummary findMetric(int sessionId, List resources,
                                           Integer tid, long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefEntityNotFoundException,
               AppdefCompatException, MeasurementNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        // Just one metric
        List mtids = new ArrayList();
        mtids.add(tid);
        
        // Look up the metric summaries of all associated resources
        Map results = getResourceMetrics(subject, resources, mtids, begin, end,
                                         null);
        
        // Should only be one
        if (log.isDebugEnabled()) {
            log.debug("getResourceMetrics() returned " + results.size());
        }
    
        if (results.size() > 0) {
            Iterator it = results.values().iterator();
            Collection coll = (Collection) it.next();
            it = coll.iterator();
            MetricDisplaySummary summary = (MetricDisplaySummary) it.next();
            
            return summary;
        }
        
        return null;
    }
    
    /**
     * Prunes from the list of passed-in AppdefEntityValue array those
     * resources that are not collecting the metric corresponding to
     * the given template id.
     * @param resources the resources
     * @param tid the metric template id
     * @return an array of resources
     * @ejb:interface-method
     */
    public AppdefResourceValue[] pruneResourcesNotCollecting(
        int sessionId, AppdefResourceValue[] resources, Integer tid)
        throws SessionNotFoundException, SessionTimeoutException,
               AppdefEntityNotFoundException, MeasurementNotFoundException,
               PermissionException, DataNotAvailableException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        MeasurementTemplate tmpl = getTemplateManager().getTemplate(tid);

        List pruned = new ArrayList();
        for (int i=0; i<resources.length; ++i) {
            Integer[] metrics =
                getMetricIdsForResource(subject, resources[i].getEntityId(),
                                        tmpl);
            if (metrics.length > 0) {
                pruned.add(resources[i]);
            }
        }
        AppdefResourceValue[] prunedResources =
            new AppdefResourceValue[pruned.size()];
        return (AppdefResourceValue[])pruned.toArray(prunedResources);
    }

    private Integer[] getMetricIdsForResource(AuthzSubjectValue subject,
                                              AppdefEntityID aid,
                                              MeasurementTemplate tmpl)
        throws PermissionException, AppdefEntityNotFoundException,
               MeasurementNotFoundException {
        // Find the measurement ID based on entity type
        if (aid.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION) {
            AppdefEntityValue aeval = new AppdefEntityValue(aid, subject);

            ApplicationValue app =
                (ApplicationValue) aeval.getResourceValue();
            AppServiceValue[] appSvcs = app.getAppServiceValues();
        
            // If it's availability, then we'd have to get data for all svcs
            for (int i = 0; i < appSvcs.length; i++) {
                // Metric is based on the entry point
                if (appSvcs[i].getIsEntryPoint()) {
                    AppdefEntityID id;
                    
                    if (appSvcs[i].getIsCluster()) {
                        id = new AppdefEntityID(
                            AppdefEntityConstants.APPDEF_TYPE_GROUP, 
                            appSvcs[i].getServiceCluster().getGroupId());
                    }
                    else {
                        id = appSvcs[i].getService().getEntityId();
                    }
                    
                    try {
                        return getMetricIdsForResource(subject, id, tmpl);
                    } catch (MeasurementNotFoundException ignore) {}
                }
            }
            
            throw new MeasurementNotFoundException("No entry point found: " +
                                                   aid);
        } else if (aid.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            try {
                Integer[] ids = getGroupMemberIDs(subject, aid);
            
                // Get the list of measurements
                return getDerivedMeasurementManager().findMeasurementIds(
                    subject, tmpl.getId(), ids);
            } catch (GroupNotCompatibleException e) {
                throw new MeasurementNotFoundException(
                    "Incompatible group members: " + aid);
            }
        } else {
            Integer[] aids = { aid.getId() };
            return getDerivedMeasurementManager().findMeasurementIds(
                subject, tmpl.getId(), aids);
        }
    }

    private MetricDisplaySummary getMetricDisplaySummary(
        AuthzSubjectValue subject, MeasurementTemplateValue tmpl, long begin,
        long end, double[] data, AppdefEntityID id)
        throws MeasurementNotFoundException {
        // Get baseline values
        DerivedMeasurementValue dmval =
            getDerivedMeasurementManager().findMeasurement(
                subject, tmpl.getId(), id.getId());

        // Use previous function to set most values, including only 1 resource
        MetricDisplaySummary summary =
            getMetricDisplaySummary(tmpl, new Long(dmval.getInterval()),
                                         begin, end, data, 1);

        if (dmval.getBaseline() != null) {
            BaselineValue bval = dmval.getBaseline();
            if (bval.getMean() != null)
                summary.setMetric(MetricDisplayConstants.BASELINE_KEY,
                        new MetricDisplayValue(bval.getMean()));
            if (bval.getMaxExpectedValue() != null)
                summary.setMetric(MetricDisplayConstants.HIGH_RANGE_KEY,
                        new MetricDisplayValue(bval.getMaxExpectedValue()));
            if (bval.getMinExpectedValue() != null)
                summary.setMetric(MetricDisplayConstants.LOW_RANGE_KEY,
                        new MetricDisplayValue(bval.getMinExpectedValue()));
        }
        return summary;
    }
    
    private List getResourceIds(AuthzSubjectValue subject, AppdefEntityID aeid,
                                AppdefEntityTypeID ctype)
        throws AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        List resources;
        if (ctype == null) {
            if (aeid.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
                resources =
                    GroupUtil.getCompatGroupMembers(subject, aeid, null,
                                                    PageControl.PAGE_ALL);
            }
            else {
                // Just one
                resources = new ArrayList();
                resources.add(aeid);
            }
        }
        else {
            // Autogroup
            resources = getAGMemberIds(subject, aeid, ctype);
        }
        return resources;
    }
    
    /**
     * Method findResourceMetricSummary.
     * 
     * For metric comparisons, the ResourceMetricDisplaySummary beans
     * are returned as a map where the keys are the MeasurementTemplateValue 
     * (or MeasurementTemplateLiteValue?)  objects associated with the given
     * resource's types, the values are Lists of ResourceMetricDisplaySummary
     *
     * The context that the user will be populating the input resource list 
     * from should always be like resource types.  If for some reason that's 
     * not the case, this method will take a garbage in/garbage out 
     * approach (as opposed to enforcing like types) -- comparing apples 
     * and oranges may be performed but if the user ends up with measurement 
     * templates for which there is only one resource to compare, that should 
     * indicate some other problem i.e. the application is presenting 
     * dissimilar objects as available for comparison.
     * 
     * The list of resources can be any concrete AppdefResourceValue (i.e.
     * a platform, server or service), composite AppdefResourceValues
     * (i.e. applications, groups) are inappropriate for this signature.
     * 
     * Used for screen 0.3
     *
     * @param begin the commencement of the timeframe of interest
     * @param end the end of the timeframe of interest
     * @return Map of measure templates and resource metric lists
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public Map findResourceMetricSummary(int sessionId,
                                         AppdefEntityID[] entIds,
                                         long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException, 
               AppdefEntityNotFoundException, MeasurementNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        // Assuming that all AppdefEntityIDs of the same type, use the first one
        AppdefEntityValue rv = new AppdefEntityValue(entIds[0], subject);
        String monitorableType = rv.getMonitorableType();        
    
        // Get the template ID's for that type
        List tmpls =
            findMeasurementTemplates(sessionId, monitorableType,
                                          PageControl.PAGE_ALL);
    
        // Keep the templates in a map        
        IntHashMap tmplMap = new IntHashMap();
        
        // Create an Integer array of template ID's
        Integer[] mtids = new Integer[tmpls.size()];
        Iterator it = tmpls.iterator();
        for (int i = 0; it.hasNext(); i++) {
            MeasurementTemplateValue tmpl =
                (MeasurementTemplateValue) it.next();
            mtids[i] = tmpl.getId();
            tmplMap.put(mtids[i].intValue(), tmpl);
        }
    
        IntHashMap templateMetrics = new IntHashMap();
        HashMap uniqueTemplates = new HashMap();
        // a temp cache to save rountrips to the db
        HashMap seen = new HashMap();      
        
        // Now, iterate through each AppdefEntityID
        for (int i = 0; i < entIds.length; i++) {            
            Integer[] eid = new Integer[] { entIds[i].getId() };
            // Now get the aggregate data, keyed by template ID's
            Map datamap = getDataMan().getAggregateData(mtids, eid,
                                                        begin, end);
    
            // For each template, add a new summary
            for (it = datamap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                    
                Integer mtid = (Integer) entry.getKey();
                // Get the MeasurementTemplateValue
                MeasurementTemplateValue tmpl =
                    (MeasurementTemplateValue) tmplMap.get(mtid.intValue());
                        
                // Use the MeasurementTemplateValue id to get the array List
                List resSummaries =
                    (List) templateMetrics.get(mtid.intValue());
                if (resSummaries == null) // this key hasn't been seen yet
                    resSummaries = new ArrayList();
                    
                // Get the data
                double[] data = (double[]) entry.getValue(); 
                    
                AppdefResourceValue v;
                rv = new AppdefEntityValue(entIds[i], subject);
    
                if (seen.containsKey(entIds[i])) {
                    v = (AppdefResourceValue)seen.get(entIds[i]);
                } else {
                    v = rv.getResourceValue();
                    seen.put(entIds[i], v);  // keep track of what we've seen
                }
                    
                MetricDisplaySummary mds =
                    getMetricDisplaySummary(subject, tmpl, begin, end,
                                            data, entIds[i]);
                    
                resSummaries.add(new ResourceMetricDisplaySummary(mds, v));
                templateMetrics.put(mtid.intValue(), resSummaries);
                uniqueTemplates.put(mtid, tmpl);
            }
        }
        // now take all of the unique lists and unique 
        // MeasurementTemplateValue's and merge them into the result
        HashMap result = new HashMap();
        for (Iterator iter = uniqueTemplates.keySet().iterator(); 
             iter.hasNext();) 
        {
            Integer mtid = (Integer) iter.next();
            result.put(uniqueTemplates.get(mtid),
                       templateMetrics.get(mtid.intValue()));
        }                
        return result;
    }

    /**
     * Return a MetricSummary bean for each of the metrics (template) for the
     * entities in the given time frame
     * @param begin the beginning time frame
     * @param end the ending time frame
     * @return a list of ResourceTypeDisplaySummary beans
     * @throws AppdefCompatException
     * @ejb:interface-method
     */
    public Map findMetrics(int sessionId, AppdefEntityID[] entIds,
                           long filters, String keyword, long begin, long end,
                           boolean showNoCollect)
        throws SessionTimeoutException, SessionNotFoundException,
            InvalidAppdefTypeException, PermissionException,
            AppdefEntityNotFoundException, AppdefCompatException {
        return super.findMetrics(sessionId, entIds, filters, keyword,
                                 begin, end, showNoCollect);
    }

    /**
     * Method findMetrics.
     * 
     * When the entId is a server, return all of the metrics that are instances
     * of the measurement templates for the server's type. In this case, the
     * MetricDisplaySummary's attributes to show the number collecting doesn't
     * make sense; showNumberCollecting should false for each bean.
     * <p>
     * When the entId is a platform, return all of the metrics that are
     * instances of the measurement templates for the platform's type. In this
     * case, the MetricDisplaySummary's attributes to show the number collecting
     * doesn't make sense; showNumberCollecting should false for each bean.
     * </p>
     * <p>
     * When the entId is compatible group of servers or platforms, return all of
     * the metrics for the type. Each MetricDisplaySummary actually represents
     * the metrics summarized for all of the group members (cumulative/averaged
     * as appropriate), showNumberCollecting should be true and the
     * numberCollecting as well as the total number of members assigned in each
     * bean.
     * </p>
     * 
     * @return Map keyed on the category (String), values are List's of
     *         MetricDisplaySummary beans
     * @throws AppdefCompatException
     * @see org.hyperic.hq.bizapp.shared.uibeans.MetricDisplaySummary
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public Map findMetrics(int sessionId, AppdefEntityID entId, List mtids,
                           long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
            PermissionException, AppdefEntityNotFoundException,
            AppdefCompatException {
        return super.findMetrics(sessionId, entId, mtids, begin, end);
    }

    /** Return a MetricSummary bean for each of the servers of a specific type.
     * @param begin the beginning time frame
     * @param end the ending time frame
     * @return a list of ResourceTypeDisplaySummary beans
     * @throws AppdefCompatException
     * @ejb:interface-method
     */
    public Map findAGPlatformMetricsByType(int sessionId, 
                                           AppdefEntityTypeID platTypeId,
                                           long begin, long end,
                                           boolean showAll)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException, AppdefCompatException {
        return super.findAGPlatformMetricsByType(sessionId, platTypeId,
                                                 begin, end, showAll);
    }

    /**
     * Return a Metric summary bean for each of the services of a specific type
     * <p>
     * The map returned has keys for the measurement categories (see
     * MeasurementConstants) and values that are Lists of MetricDisplaySummary
     * beans.
     * </p>
     * <p>
     * This is used to access metrics for entity's internal and deployed
     * services. The metrics returned are only applicable from within the given
     * timeframe of interest.
     * </p>
     * <p>
     * Appropriate entities include
     * <ul>
     * <li>applications (2.1.2.2-3)
     * <li>servers (2.3.2.1-4 - internal/deplyed tabs)
     * <li>services (2.5.2.2 - internal/deplyed tabs)
     * </ul>
     *
     * @param begin the beginning time frame
     * @param end the ending time frame
     * @return a list of CurrentHealthDisplaySummary beans
     * @throws AppdefCompatException
     * @ejb:interface-method
     */
    public Map findAGMetricsByType(int sessionId, AppdefEntityID[] entIds,
                                   AppdefEntityTypeID typeId, long filters,
                                   String keyword, long begin, long end,
                                   boolean showAll)
        throws SessionTimeoutException, SessionNotFoundException,
            InvalidAppdefTypeException, PermissionException,
            AppdefEntityNotFoundException, AppdefCompatException {
        return super.findAGMetricsByType(sessionId, entIds, typeId, filters,
                                         keyword, begin, end, showAll);
    }
    
    /**
     * Find all resources that we're supposed to collect metrics for, where 
     * we haven't seen any metrics in 3x the shortest metric collection 
     * interval.  These are further filtered by relationship -- if a platform
     * is in the list, none of its servers is in the list.  If a server is
     * in the list, none of its services are in the list.
     * @param ignoreList a List of Integers representing the resource ids
     * (that is EAM_RESOURCE.ID) of servers that should NOT be included
     * in the results.
     * @return a List of MiniResourceValue objects
     * @ejb:interface-method
     */
    public List getResourcesWithoutData(int sessionId,
                                        List ignoreList)
        throws SessionTimeoutException, SessionNotFoundException {
        
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return resourcesWithoutDataHelper.getResourcesWithoutData(subject,
                                                                  ignoreList);
    }

    /**
     * Find all resources that could be collecting metrics for, but 
     * have no metrics defined for collection.
     * @param ignoreList a List of Integers representing the resource ids
     * (that is EAM_RESOURCE.ID) of servers that should NOT be included
     * in the results.
     * @return a List of MiniResourceValue objects
     * @ejb:interface-method
     */
    public List getResourcesWithoutMetrics (int sessionId,
                                            List ignoreList)
        throws SessionTimeoutException, SessionNotFoundException {
        
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return resourcesWithoutDataHelper.getResourcesWithoutMetrics(subject,
                                                                     ignoreList);
    }

    /** Return a MeasurementSummary bean for the resource's associated resources
     * specified by type
     * @param entId the entity ID
     * @param appdefType the type (server, service, etc) of the specified resource type
     * @param typeId the specified resource type ID
     * @return a MeasurementSummary bean
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public MeasurementSummary getSummarizedResourceAvailability(
        int sessionId, AppdefEntityID entId, int appdefType, Integer typeId)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionNotFoundException, SessionTimeoutException,
               InvalidOptionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        List resources;
        if (entId.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            // we don't use AppdefEntityValue for groups but GroupUtil
            // will give us what we need
            try {
                resources =
                    GroupUtil.getCompatGroupMembers(subject, entId, null,
                                                    PageControl.PAGE_ALL);
            } catch (GroupNotCompatibleException e) {
                throw new InvalidOptionException(
                    "Requested group (" + entId +
                    ") is not a compatible type");
            }
        } else
        if (entId.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION) {
            AppdefEntityValue aev = new AppdefEntityValue(entId, subject);
            resources = aev.getAssociatedServices(typeId, PageControl.PAGE_ALL);
            if (typeId != null) {
                for (Iterator i = resources.iterator(); i.hasNext();) {
                    AppdefResourceValue r = (AppdefResourceValue) i.next();
                    if (r instanceof ServiceClusterValue)
                        i.remove();
                }
            }
    
        } else {
            AppdefEntityValue aev = new AppdefEntityValue(entId, subject);
            switch (appdefType) {
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                    resources = aev.getAssociatedPlatforms(PageControl.PAGE_ALL);
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVER:
                    resources = aev.getAssociatedServerIds(typeId);
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
                    resources =
                        aev.getAssociatedServices(typeId, PageControl.PAGE_ALL);
                    break;
                default:
                    throw new InvalidOptionException(
                        "Requested type (" + appdefType +
                        ") is not a platform, server, or service");
            }
        } 
        double[] data = getAvailability(subject, 
                toAppdefEntityIDArray(resources));
    
        // Availability counts **this calls getLiveMeasurement
        int availCnt = 0;
        int unavailCnt = 0;
        int unknownCnt = 0;
        for (int i = 0; i < data.length; i++) {
            // switch
            if (MeasurementConstants.AVAIL_UP == data[i]) {
                // yes
                availCnt++;
            }
            else if (MeasurementConstants.AVAIL_UNKNOWN == data[i]) {
                // maybe so
                unknownCnt++;
            }
            else if ((MeasurementConstants.AVAIL_DOWN <= data[i] &&
                      MeasurementConstants.AVAIL_UP > data[i]) ||
                     MeasurementConstants.AVAIL_PAUSED == data[i]) {
                // no
                unavailCnt++;
            }
            else {
                throw new IllegalStateException(data[i] + 
                                " is not a valid availability state");
            }
        }
        
        return new MeasurementSummary(new Integer(availCnt),
                                      new Integer(unavailCnt),
                                      new Integer(unknownCnt));
    }

    /**
     * @return a List of ResourceTypeDisplaySummary's
     * @deprecated use POJO API instead
     */    
    private List 
        getSummarizedResourceCurrentHealth(AuthzSubjectValue subject, 
                                           AppdefResourceValue[] resources)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException 
    {
    
        List summaries = new ArrayList();
    
        // Create Map of auto-group'd/singleton resources and a List of clusters
        // since their current health summarizations are not flattened        
    
        // auto-group'd entities are kept track of in here where keys are the
        // type id's and the values are lists of resources
        HashMap resTypeMap = new HashMap(); 
        // to avoid looking up singleton/autogroup resources again later, we
        // keep them here where keys are AppdefEntityID's and values are the
        // AppdefResourceValues
        HashMap resourcemap = new HashMap();
        // keys are type id's and the values are AppdefResourceTypeValues
        for (int i = 0; i < resources.length; i++) {
            if (resources[i] instanceof ServiceClusterValue) {
                AppdefResourceValue resource = resources[i];
                AppdefEntityID aid = resource.getEntityId();
                AppdefEntityValue aeval = new AppdefEntityValue(aid, subject);
                AppdefGroupValue agval =
                    (AppdefGroupValue) aeval.getResourceValue();
                ClusterDisplaySummary cds = new ClusterDisplaySummary();
                    
                cds.setEntityId(resource.getEntityId());
                cds.setEntityName(agval.getName());
                int size = agval.getTotalSize();
                cds.setNumResources(new Integer(size));
                try {
                    // Replace the IDs with all of the members
                    List memberIds = 
                        GroupUtil.getCompatGroupMembers(subject, aid, null, 
                                                        PageControl.PAGE_ALL);
                    AppdefEntityID[] ids = (AppdefEntityID[])
                        memberIds.toArray(new AppdefEntityID[0]);
                    setResourceTypeDisplaySummary(subject, cds,
                                                  agval.getAppdefResourceTypeValue(),
                                                  ids);
                    summaries.add(cds);
                } catch (GroupNotCompatibleException e) {
                    log.error("Group not compatible: " + aid);
                    throw new IllegalArgumentException("Invalid appdef state");
                }
            } else {
                // all of the non-clusters get organized in here
                resourcemap.put(resources[i].getEntityId(), resources[i]);
                AppdefResourceTypeValue type =
                    resources[i].getAppdefResourceTypeValue();
                Integer typeId = type.getId();
                List siblings = (List) resTypeMap.get(typeId);
                if (siblings == null) {
                    siblings = new ArrayList();
                    resTypeMap.put(typeId, siblings);               
                }            
                // Add resource to list
                siblings.add(resources[i].getEntityId());
            }
        }
        // first deal with the autogroubz and singletons (singletons
        // are just the degenerative case of an autogroup, why it's
        // its own type is... silly)
        for (Iterator it = resTypeMap.entrySet().iterator(); it.hasNext();){
            Map.Entry entry = (Map.Entry)it.next();
            Collection siblings = (Collection) entry.getValue();
            // Make sure we have valid IDs
            if (siblings == null || siblings.size() == 0)
                continue;                    
                
            ResourceTypeDisplaySummary summary;
            AppdefResourceValue resource;
            AppdefEntityID[] ids =
                (AppdefEntityID[])siblings.toArray(new AppdefEntityID[0]);                
            AppdefEntityID aid = ids[0];
            resource = (AppdefResourceValue)resourcemap.get(aid);
            // autogroup
            if (ids.length > 1) {
                summary = new AutogroupDisplaySummary();
                summary.setNumResources(new Integer(ids.length));
            } else {
                // singleton
                summary = new SingletonDisplaySummary(aid, resource.getName());
            }
            setResourceTypeDisplaySummary(subject, summary,
                                          resource.getAppdefResourceTypeValue(),
                                          ids);
            summaries.add(summary);
        }
    
        Collections.sort(summaries);
        return summaries;
    }

    /**
     * @return a List of ResourceTypeDisplaySummary's
     */    
    private List getSummarizedResourceCurrentHealth(AuthzSubjectValue subject, 
                                                    Collection resources)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        List summaries = new ArrayList();

        // auto-group'd entities are kept track of in here where keys are the
        // type id's and the values are lists of resources
        HashMap resTypeMap = new HashMap(); 
        
        // keys are type id's and the values are AppdefResources
        for (Iterator it = resources.iterator(); it.hasNext(); ) {
            AppdefResource resource = (AppdefResource) it.next();
            AppdefResourceType type = resource.getAppdefResourceType();
            Integer typeId = type.getId();
            List siblings = (List) resTypeMap.get(typeId);
            if (siblings == null) {
                siblings = new ArrayList();
                resTypeMap.put(typeId, siblings);               
            }

            // Add resource to list
            siblings.add(resource);
        }
        
        // first deal with the autogroubz and singletons (singletons
        // are just the degenerative case of an autogroup, why it's
        // its own type is... silly)
        for (Iterator it = resTypeMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Collection siblings = (Collection) entry.getValue();
            // Make sure we have valid IDs
            if (siblings == null || siblings.size() == 0)
                continue;                    
                
            ResourceTypeDisplaySummary summary = null;

            AppdefResourceType type = null;
            AppdefEntityID[] ids = new AppdefEntityID[siblings.size()];
            int i = 0;
            for (Iterator sibIt = siblings.iterator(); sibIt.hasNext(); i++) {
                AppdefResource res = (AppdefResource) sibIt.next();
                ids[i] = res.getEntityId();
                
                if (type == null) {
                    type = res.getAppdefResourceType();
                    
                    if (sibIt.hasNext()) {
                        // autogroup
                        summary = new AutogroupDisplaySummary();
                        summary.setNumResources(new Integer(siblings.size()));
                    }
                    else {
                        // singleton
                        summary = new SingletonDisplaySummary(ids[i],
                                                              res.getName());
                    }
                }
            }
            setResourceTypeDisplaySummary(subject, summary,
                                          type.getAppdefResourceTypeValue(),
                                          ids);
            summaries.add(summary);
        }

        Collections.sort(summaries);
        return summaries;
    }

    private void 
        setResourceTypeDisplaySummary(AuthzSubjectValue subject,
                                      ResourceTypeDisplaySummary summary, 
                                      AppdefResourceTypeValue resType,
                                      AppdefEntityID[] ids) 
    {
        // Now get each category of measurements
        long end = System.currentTimeMillis();
        long begin = end - MeasurementConstants.HEALTH_WINDOW_MILLIS;

        summary.setResourceType(resType);
        
        StopWatch watch = new StopWatch(end);
        
        if (log.isDebugEnabled())
            log.debug("BEGIN setResourceTypeDisplaySummary");

        // Availability
        try {
            Map midMap = getDerivedMeasurementManager()
                .findDesignatedMeasurementIds(
                    subject, ids, MeasurementConstants.CAT_AVAILABILITY);
            
            if (midMap.size() > 0) {
                Integer[] mids =
                    (Integer[]) midMap.values().toArray(new Integer[0]);
                
                // Get absolute last data point
                Map avails = getDataMan().getLastDataPoints(mids, end -
                    (resType.getAppdefType() ==
                        AppdefEntityConstants.APPDEF_TYPE_PLATFORM ?
                    MeasurementConstants.ACCEPTABLE_LIVE_MILLIS :            
                    MeasurementConstants.ACCEPTABLE_SERVICE_LIVE_MILLIS));

                if (avails.size() == 0)
                    summary.setAvailability(
                        new Double(MeasurementConstants.AVAIL_UNKNOWN));
                else {
                    double sum = 0;
                    for (Iterator it = avails.values().iterator(); it.hasNext();
                        ) {
                        MetricValue mv = (MetricValue) it.next();
                        sum += mv.getValue();
                    }
                    
                    summary.setAvailability(
                        new Double(sum / (double) midMap.size()));
                }
            }
        } catch (MeasurementNotFoundException e) {
            // No Availability data
            summary.setAvailability(
                new Double(MeasurementConstants.AVAIL_UNKNOWN));
        }

        if (log.isDebugEnabled())
            log.debug("setResourceTypeDisplaySummary() getAvailability: " +
                     watch.reset());
        
        // Throughput a.k.a. Usage
        try {
            Map midMap = getDerivedMeasurementManager()
                .findDesignatedMeasurementIds(
                    subject, ids, MeasurementConstants.CAT_THROUGHPUT);

            if (midMap.size() > 0) {
                Integer[] mids =
                    (Integer[]) midMap.values().toArray(new Integer[0]);

                double[] data =
                    getDataMan().getAggregateData(mids, begin,end);
                log.trace("getting usage data took: " + watch.getElapsed());
                summary.setThroughput(
                    new Double(data[MeasurementConstants.IND_AVG]));
                if (summary.getThroughput() != null && 
                    summary.getThroughput().intValue() > 0) {
                    for (int i = 0; i < mids.length ; i++) {
                        DerivedMeasurement dmv =
                            getDerivedMeasurementManager()
                                .getMeasurement(mids[i]);
                        
                        summary.setThroughputUnits(
                                dmv.getTemplate().getUnits());
                        
                        break;
                    }
                }
            }
        } catch (MeasurementNotFoundException e) {
            // No throughput data
        }

        if (log.isDebugEnabled())
            log.debug("END setResourceTypeDisplaySummary -- " +
                    watch.getElapsed() + " msec");
    }

    /**
     * Method findSummarizedServerCurrentHealth.
     *  <p>
     * Return a ResourceTypeDisplaySummary bean for each of the platform's
     * deployed servers.  Each bean represents a type of server and the
     * measurement data summarized for that type.
     * </p>
     * <p>
     * see screen 2.2.2
     * </p>
     * @return List of ResourceTypeDisplaySummary beans
     * @ejb:interface-method
     */
    public List findSummarizedServerCurrentHealth(int sessionId, 
                                                  AppdefEntityID entId)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        // Get the associated servers        
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        List servers = rv.getAssociatedServers(PageControl.PAGE_ALL);
        return getSummarizedResourceCurrentHealth(subject, 
            (AppdefResourceValue[])servers.toArray(new AppdefResourceValue[0]));
    }

    /**
     * Method findSummarizedServiceCurrentHealth.
     * <p>
     * This is used for the lists of service types for the Current Health 
     * view for
     * <ul> 
     * <li>applications (2.1.2)
     * <li>servers (2.3.2.1-4)
     * <li>services (2.5.2.2)
     * </ul>
     * </p>
     * <p>
     * If <code>internal</code> is <i>true</i>, only the <i>internal</i> 
     * services will be returned, the <i>deployed</i> ones if it's 
     * <i>false</i>.  If <code>internal</code> is <i>null</i>, then
     * both deployed <i>and</i> internal services will be returned.
     * </p>
     * 
     * @param entId the appdef entity with child services
     * @param internal indicates whether only internal services should be 
     *                 accessed
     * @return List a list of ResourceTypeDisplaySummary beans
     * @ejb:interface-method
     */
    public List findSummarizedPlatformServiceCurrentHealth(int sessionId,
                                                           AppdefEntityID entId)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefEntityNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        ServiceManagerLocal svcMgr = ServiceManagerEJBImpl.getOne();
        Collection services = svcMgr.getPlatformServices(subject,
                                                         entId.getId());
        return getSummarizedResourceCurrentHealth(subject, services);
    }
    
    /**
     * @ejb:interface-method
     */
    public List findSummarizedServiceCurrentHealth(int sessionId,
                                                   AppdefEntityID entId)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefEntityNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        // Get the associated services        
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        List services= rv.getAssociatedServices(PageControl.PAGE_ALL);
        return getSummarizedResourceCurrentHealth(subject, 
            (AppdefResourceValue[]) services.toArray(
                new AppdefResourceValue[services.size()]));
    }

    /**
     * Method findSummarizedGroupCurrentHealth.
     * <p>
     * see screen 2.2.2
     * </p>
     * @return List of ResourceTypeDisplaySummary beans
     * @ejb:interface-method
     */
    public List findSummarizedGroupCurrentHealth(int sessionId,
                                                 AppdefEntityID entId)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        AppdefGroupValue grp = GroupUtil.getGroup(subject, entId);
        AppdefBossLocal boss;
        try {
            boss = AppdefBossUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException(e);
        }
        AppdefEntityID[] ids = (AppdefEntityID[]) grp.getAppdefGroupEntries()
                .toArray(new AppdefEntityID[0]);
        List resourceList = boss.findByIds(sessionId, ids);
        AppdefResourceValue[] resources = (AppdefResourceValue[]) resourceList
                .toArray(new AppdefResourceValue[resourceList.size()]);
        return getSummarizedResourceCurrentHealth(subject, resources); 
    }

    /**
     * Method findGroupCurrentHealth.
     *  <p>
     * Return a ResourceDisplaySummary bean for each of the group's
     * member resources.  Each bean represents a resource and the
     * measurement data summarized for that type.
     * </p>
     * <p>
     * see screen 2.2.2
     * </p>
     * @return List of ResourceDisplaySummary beans
     * @ejb:interface-method
     */
    public List findGroupCurrentHealth(int sessionId,
                                       AppdefEntityID entId)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        PageList siblings = new PageList();
        // siblings.addAll(GroupUtil.getGroupMembers(subject, entId, null));
        List members = GroupUtil.getGroupMembers(subject, entId, null);
        for (Iterator it = members.iterator(); it.hasNext(); ) {
            AppdefEntityID eid = (AppdefEntityID) it.next();
            AppdefEntityValue ent = new AppdefEntityValue(eid, subject);
            siblings.add(ent.getResourceValue());
        }
    
        return getResourcesCurrentHealth(subject, siblings);
    }

    /**
     * Return a ResourceDisplaySummary bean for each of the resource's
     * virtual resources.  Each bean represents a resource and the
     * measurement data summarized for that resource.
     * </p>
     * <p>
     * see screen 2.2.2
     * </p>
     * @return List of ResourceDisplaySummary beans
     * @ejb:interface-method
     */
    public List findVirtualsCurrentHealth(int sessionId,
                                         AppdefEntityID entId)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, GroupNotCompatibleException,
               PermissionException {
        
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        try {
            VirtualManagerLocal vman =
                VirtualManagerUtil.getLocalHome().create();
            List resources =
                vman.findVirtualResourcesByPhysical(subject, entId);
            PageList resPageList = new PageList(resources, resources.size());
            return getResourcesCurrentHealth(subject, resPageList);
        } catch (CreateException e) {
            throw new SystemException(e);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    private void setResourceDisplaySummary(ResourceDisplaySummary rds, 
                                           AppdefResourceValue resource,
                                           AppdefResourceValue parentResource)
    {
        rds.setEntityId(resource.getEntityId());
        rds.setResourceName(resource.getName());
        rds.setResourceEntityTypeName(resource.getEntityId().getTypeName());
        rds.setResourceTypeName(
            resource.getAppdefResourceTypeValue().getName());
        if (parentResource == null) {
            rds.setHasParentResource(Boolean.FALSE);
        } else {
            rds.setParentResourceId(parentResource.getId());
            rds.setParentResourceName(parentResource.getName());
            rds.setParentResourceTypeId(
                new Integer(parentResource.getEntityId().getType()));
            rds.setHasParentResource(Boolean.TRUE);
        }
    }

    private PageList getResourcesCurrentHealth(AuthzSubjectValue subject,
                                               PageList resources)
        throws AppdefEntityNotFoundException, PermissionException {
        StopWatch watch = new StopWatch();
        PageList summaries = new PageList();
        for (Iterator it = resources.iterator(); it.hasNext(); ) {
            AppdefResourceValue resource = (AppdefResourceValue) it.next();
            ResourceDisplaySummary summary = new ResourceDisplaySummary();
        
            // Set the resource
            AppdefResourceValue parent = null;
            AppdefEntityValue rv;
            HashSet categories = new HashSet(4);
            switch (resource.getEntityId().getType()) {
                case AppdefEntityConstants.APPDEF_TYPE_SERVER :
                    rv = new AppdefEntityValue(resource.getEntityId(), subject);
                    
                    try {
                        List platforms =
                            rv.getAssociatedPlatforms(PageControl.PAGE_ALL);
                            
                        if (platforms != null && platforms.size() > 0)
                            parent = (AppdefResourceValue) platforms.get(0);
                    } catch (PermissionException e) {
                        // Can't get the parent, leave parent = null
                    }
                    // Fall through to set the monitorable and metrics
                case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
                case AppdefEntityConstants.APPDEF_TYPE_SERVICE :
                    categories.add(MeasurementConstants.CAT_AVAILABILITY);
                    categories.add(MeasurementConstants.CAT_THROUGHPUT);
                
                    setResourceDisplaySummaryValueForCategory(
                        subject, resource.getEntityId(), summary, categories);
                    
                    summary.setMonitorable(Boolean.TRUE);
                    break;
                case AppdefEntityConstants.APPDEF_TYPE_GROUP:
                    AppdefGroupValue agv = (AppdefGroupValue) resource;
                    if (!agv.isGroupCompat()) {
                        // geez, why are we here at all?  well, a user _could_
                        // add a group to their dashboard's resourceHealth
                        // portlet -- they're not going to see anything useful
                        // if it's not a compatible group but the monitorable
                        // flag is the only hint that the UI needs
                        // see PR 8039
                        summary.setMonitorable(Boolean.FALSE);
                        break;
                    }
                    // Else fall through to set monitorable and availability
                case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
                    summary.setMonitorable(Boolean.TRUE);
                    // Set the availability now
                    summary.setAvailability(new Double(
                        getAvailability(subject, resource.getEntityId())));
                    
                    try {
                        // Get the availability template
                        MeasurementTemplateValue tmpl =
                            getAvailabilityMetricTemplate(
                                subject, resource.getEntityId());
                        summary.setAvailTempl(tmpl.getId());
                    } catch (MeasurementNotFoundException e) {
                        // No availability metric, don't set it
                    }
                    break;
                default:
                    throw new InvalidAppdefTypeException(
                        "entity type is not monitorable, id type: " +
                        resource.getEntityId().getType());
            }            
            setResourceDisplaySummary(summary, resource, parent);
            summaries.add(summary);
        }
        if (log.isDebugEnabled()) {
            log.debug("getResourcesCurrentHealth: " + watch);
        }
        summaries.setTotalSize(resources.getTotalSize());
        return summaries;
    }

    /**
     * Given a group of resources, find all the scheduled measurements
     * which are common to everyone in the group.
     * 
     *  TODO:  We may want to make this more flexible by considering 
     *         availability to be the same even though it's actually a 
     *         different metric.
     *  
     * @return a map of {@link Integer} template IDs onto {@link String} 
     *         descriptions
     * @ejb:interface-method
     */
    public Map findCommonGroupMetrics(int sessionId, AppdefEntityID groupId)
        throws SessionException, PermissionException, 
               AppdefEntityNotFoundException, GroupNotCompatibleException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        List vals = findGroupMeasurements(sessionId, groupId, null, 
                                          PageControl.PAGE_ALL);
        AppdefGroupManagerLocal gMan;

        gMan = AppdefGroupManagerEJBImpl.getOne();
        
        int numGroupMembers = gMan.findGroup(subject, groupId).getTotalSize();
        
        SortedMap ents = new TreeMap(); 
        for (Iterator i=vals.iterator(); i.hasNext(); ) {
            GroupMetricDisplaySummary sum = (GroupMetricDisplaySummary)i.next();
            
            if (sum.getActiveMembers() != numGroupMembers)
                continue;
        
            ents.put(sum.getLabel(), sum.getTemplateId());
        }

        // Use the previously created SortedMap to create the new linked hash
        // map, swapping keys and vals
        Map res = new LinkedHashMap();
        for (Iterator i=ents.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry ent = (Map.Entry)i.next();
            
            res.put(ent.getValue(), ent.getKey());
        }
        return res;
    }

    /**
     * @param id the AppdefEntityID of the resource our ResourceDisplaySummary is for
     * @param summary a ResourceDisplaySummary
     * @throws PermissionException
     * @throws AppdefEntityNotFoundException
     */
    private void setResourceDisplaySummaryValueForCategory(
        AuthzSubjectValue subject, AppdefEntityID id,
        ResourceDisplaySummary summary, Set categories)
        throws AppdefEntityNotFoundException, PermissionException {

        // Maybe we're not doing anything
        if (categories.size() == 0)
            return;
        
        StopWatch watch = new StopWatch();
        
        if (categories.remove(MeasurementConstants.CAT_AVAILABILITY)) {
            DerivedMeasurement dm = findAvailabilityMetric(subject, id);
            if (dm != null) {
                summary.setAvailability(
                    new Double(getAvailability(subject, id)));
                summary.setAvailTempl(dm.getTemplate().getId());
            }
        }
        
        watch.markTimeBegin("findDesignatedMetrics");
        List measurements = findDesignatedMetrics(subject, id, categories);
        watch.markTimeEnd("findDesignatedMetrics");
        
        long now = System.currentTimeMillis();
        watch.markTimeBegin("get designated metrics data");

        // Optimization for the fact that we can have multiple indicator
        // metrics for each category
        HashSet done = new HashSet();
        for (Iterator it = measurements.iterator(); it.hasNext(); ) {
            DerivedMeasurement dmv = (DerivedMeasurement) it.next();
            MeasurementTemplate templ = dmv.getTemplate();
            String category = templ.getCategory().getName();

            if (done.contains(category))
                continue;
            
            // an array (of length 1) of measurement id's
            Integer[] mids = { dmv.getId() };

            Double theValue;
            Map dataMap;
            dataMap = getDataMan().
                getLastDataPoints(mids, now - MeasurementConstants.HOUR);

            if (dataMap.size() < 1)
                continue;
                
            theValue = ((MetricValue)dataMap.get(dmv.getId())).getObjectValue();
            
            if (category.equals(MeasurementConstants.CAT_THROUGHPUT)) {
                summary.setThroughput(theValue);
                summary.setThroughputUnits(templ.getUnits());
                summary.setThroughputTempl(templ.getId());
            } else if (category.equals(MeasurementConstants.CAT_PERFORMANCE)) {
                summary.setPerformance(theValue);
                summary.setPerformanceUnits(templ.getUnits());
                summary.setPerformTempl(templ.getId());
            }

            done.add(category);
        }
        watch.markTimeEnd("get designated metrics data");
        if (log.isDebugEnabled()) {
            log.debug("setResourceDisplaySummaryValueForCategory: " + watch);
        }
    }

    private PageList getResourcesCurrentHealth(AuthzSubjectValue subject,
                                               List resources,
                                               long begin, long end,
                                               PageControl pc)
        throws AppdefEntityNotFoundException, PermissionException {
        if (resources.size() == 0) {
            return new PageList();
        }

        // Need to get the templates for this type, using the first resource
        AppdefResourceValue resource = (AppdefResourceValue) resources.get(0);
        Integer[] tids = getTemplateManager().findTemplateIds(
            resource.getAppdefResourceTypeValue().getName());

        // Create a hash set of ID's
        HashSet eidSet;
        try {
            // See which entities actually have values
            Integer[] eids =
                getDataMan().getInstancesWithData(tids, begin, end);
            eidSet = new HashSet(Arrays.asList(eids));
        } catch (DataNotAvailableException e) {
            // Return nothing
            return new PageList();
        }

        // Throw away instances without data    
        for (Iterator it = resources.iterator(); it.hasNext(); ) {
            resource = (AppdefResourceValue) it.next();
            if (!eidSet.contains(resource.getId())) {
                it.remove();
            }
        }
        
        // Create a new PageList
        PageList pagedResources = Pager.getDefaultPager().seek(resources, pc);
        return getResourcesCurrentHealth(subject, pagedResources);
    }

    /**
     * Method findResourcesCurrentHealth.
     * 
     * The size of the list of ResourceDisplaySummary beans returned will
     * be equivalent to the size of the entity ID's passed in.  Called by RSS
     * feed so it does not require valid session ID.
     * 
     * @throws ApplicationException if user is not found
     * @throws LoginException if user account has been disabled
     * @return PageList of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public List findResourcesCurrentHealth(String user, AppdefEntityID[] entIds)
        throws LoginException, ApplicationException, PermissionException,
               AppdefEntityNotFoundException, SessionNotFoundException,
               SessionTimeoutException {
        int sessionId = getAuthManager().getUnauthSessionId(user);
        return findResourcesCurrentHealth(sessionId, entIds);
    }
    
    /**
     * Method findResourcesCurrentHealth.
     * 
     * The size of the list of ResourceDisplaySummary beans returned will
     * be equivalent to the size of the entity ID's passed in.
     * 
     * @return PageList of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public List findResourcesCurrentHealth(int sessionId, 
                                           AppdefEntityID[] entIds)
        throws AppdefEntityNotFoundException, PermissionException,
               SessionNotFoundException, SessionTimeoutException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        Log timingLog = LogFactory.getLog("DASHBOARD-TIMING");
        StopWatch timer = new StopWatch();

        PageList resources = new PageList();
        for (int i = 0; i < entIds.length; i++) {
            AppdefEntityValue entVal =
                new AppdefEntityValue(entIds[i], subject);
            resources.add(entVal.getLiteResourceValue());
        }
        timingLog.trace("findResourceCurrentHealth(2) - timing [" +
                        timer.toString()+"]");

        return getResourcesCurrentHealth(subject, resources);
    }

    /**
     * Find the current health of the entity's host(s)
     * 
     * @return PageList of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public List findHostsCurrentHealth(int sessionId, AppdefEntityID entId,
                                       PageControl pc)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException, AppdefEntityNotFoundException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        int entType = entId.getType();

        if (entType == AppdefEntityConstants.APPDEF_TYPE_GROUP) {
            AppdefGroupValue group =
                (AppdefGroupValue) rv.getLiteResourceValue();
            entType = group.getGroupEntType();
        }
        
        PageList hosts;
        
        switch (entType) {
        case AppdefEntityConstants.APPDEF_TYPE_SERVER:
            hosts = rv.getAssociatedPlatforms(pc);
            break;
        case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            hosts = rv.getAssociatedServers(pc);
            
            // Go through and see if we are looking at virtual servers
            ArrayList virtIds = new ArrayList(hosts.size());
            for (Iterator it = hosts.iterator(); it.hasNext(); ) {
                ServerValue sv = (ServerValue) it.next();
                if (!sv.getServerType().getVirtual())
                    break;
                
                virtIds.add(sv.getEntityId());
            }
            
            if (virtIds.size() > 0)
                hosts = getPlatformManager().getPlatformsByServers(subject,
                                                                   virtIds);
            break;
        default:
            return new PageList();
        }
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, hosts);
    }
    
    /**
     * Method findPlatformsCurrentHealth.
     * 
     * The population of the list of ResourceDisplaySummary beans returned will
     * vary depending on the entId's type.
     * 
     * When the entId is a server, the returned list should have just one
     * ResourceDisplaySummary with a PlatformValue in it, the one that 
     * represents the host that the server resides on.
     * 
     * When the entId is a compatible group of platforms, the returned list 
     * will have as many elements as there are individual PlatformValue's to
     * represent all of the hosts.
     * 
     * @return PageList of ResourceDisplaySummary beans
     * @ejb:interface-method
     */
    public PageList findPlatformsCurrentHealth(int sessionId, 
                                               AppdefEntityID entId,
                                               PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        PageList platforms = rv.getAssociatedPlatforms(pc);
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, platforms);
    }

    /**
     * Method findAGPlatformsCurrentHealthByType
     *  
     * For autogroup of platforms.
     * 
     * If the entId is a platform, the deployed servers view shows
     * the current health of servers.
     *
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public List findAGPlatformsCurrentHealthByType(int sessionId,
                                                   Integer platTypeId)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        PageList platforms;
        PageControl pc = PageControl.PAGE_ALL;
        
        platforms = getPlatformManager()
            .findPlatformsByType(subject, platTypeId, pc);
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, platforms);
    }

    /**
     * Method findServersCurrentHealth
     *  
     * For the screens that rely on this API, the entId is either an 
     * application, a service or a group.
     * 
     * The population of the list varies with the type of appdef entity input.
     * 
     * This is used for all of the application monitoring screens; they all 
     * show a list with current health data for each server that 
     * participates in supplying services for an application.  
     * So if the entity is an application, the list is populated with servers 
     * that host the services on which the application relies.  The
     * timeframe is not used in this context, the list of servers is always
     * the current list.  The timeframe shall still be sent but it will be
     * bounded be the current time and current time - default time
     * window.
     * (see 2.1.2 - 2.1.2.1-3)
     * 
     * If the entId is a platform, the deployed servers view shows
     * the current health of servers in the timeframe that the metrics
     * are shown for.  So if the entity is application, expect to populate
     * the list based on the presence of metrics in the timeframe of 
     * interest.
     * (see 2.2.2.3, it shows deployed servers... I'll give you a dollar if
     * you can come up with a reason why we'd want internal servers.
     * We aren't managing cron or syslog, dude.) 
     * 
     * This is also used for a services' current health page in which case
     * the appdef entity is a service.
     * 
     * @param entId the platform's or application's ID
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServersCurrentHealth(int sessionId,
                                             AppdefEntityID entId,
                                             PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        switch (entId.getType()) {
            case AppdefEntityConstants.APPDEF_TYPE_PLATFORM:
            case AppdefEntityConstants.APPDEF_TYPE_APPLICATION:
            case AppdefEntityConstants.APPDEF_TYPE_SERVICE:
            case AppdefEntityConstants.APPDEF_TYPE_GROUP:
                break;
            default:
                throw new InvalidAppdefTypeException(
                    "entityID is not valid type, id type: " + entId.getType());
        }
        
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        PageList servers = rv.getAssociatedServers(pc);
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, servers);
    }

    /**
     * Method findServersCurrentHealth
     *  
     * For platform's autogroup of servers.
     * 
     * If the entId is a platform, the deployed servers view shows
     * the current health of servers.
     * 
     * @param entId the platform's or application's ID
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServersCurrentHealthByType(int sessionId,
                                                   AppdefEntityID entId,
                                                   Integer serverTypeId,
                                                   PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        if (entId.getType() != AppdefEntityConstants.APPDEF_TYPE_PLATFORM) {
            throw new InvalidAppdefTypeException(
                "findServersCurrentHealthByType() only allows Platforms, " +
                "id type: " + entId.getType());
        }
        
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        PageList servers = rv.getAssociatedServers(serverTypeId, pc);
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, servers);
    }

    /**
     * Method findServersCurrentHealth
     *  
     * For platform's autogroup of servers.
     * 
     * If the entId is a platform, the deployed servers view shows
     * the current health of servers.
     *
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public List findAGServersCurrentHealthByType(int sessionId,
                                                 AppdefEntityID[] entIds,
                                                 Integer serverTypeId)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        PageList servers = new PageList();
        PageControl pc = PageControl.PAGE_ALL;
        
        for (int i = 0; i < entIds.length; i++) {
            AppdefEntityID entId = entIds[i];
            if (entId.getType() != AppdefEntityConstants.APPDEF_TYPE_PLATFORM) {
                throw new InvalidAppdefTypeException(
                    "findServersCurrentHealthByType() only allows Platforms, " +
                    "id type: " + entId.getType());
            }
        
            AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
            servers.addAll(rv.getAssociatedServers(serverTypeId, pc));
        }
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, servers);
    }

    /**
     * Method findServersCurrentHealth
     *  
     * For the screens that rely on this API, the entId is either an 
     * application, a service or a group.
     * 
     * The population of the list varies with the type of appdef entity input.
     * 
     * This is used for all of the application monitoring screens; they all 
     * show a list with current health data for each server that 
     * participates in supplying services for an application.  
     * So if the entity is an application, the list is populated with servers 
     * that host the services on which the application relies.  The
     * timeframe is not used in this context, the list of servers is always
     * the current list.  The timeframe shall still be sent but it will be
     * bounded be the current time and current time - default time
     * window.
     * (see 2.1.2 - 2.1.2.1-3)
     * 
     * If the entId is a platform, the deployed servers view shows
     * the current health of servers in the timeframe that the metrics
     * are shown for.  So if the entity is application, expect to populate
     * the list based on the presence of metrics in the timeframe of 
     * interest.
     * (see 2.2.2.3, it shows deployed servers... I'll give you a dollar if
     * you can come up with a reason why we'd want internal servers.
     * We aren't managing cron or syslog, dude.) 
     * 
     * This is also used for a services' current health page in which case
     * the appdef entity is a service.
     * 
     * @param entId the platform's or application's ID
     * @param begin the commencement of the timeframe of interest
     * @param end the end of the timeframe of interest
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public List findServersCurrentHealth(int sessionId, AppdefEntityID entId,
                                         long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        try {
            AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
            PageList servers = rv.getAssociatedServers(PageControl.PAGE_ALL);
            
            // Return a list of current health
            return getResourcesCurrentHealth(subject, servers, begin, end,
                                             PageControl.PAGE_ALL);
        } catch (IllegalArgumentException e) {
            throw new InvalidAppdefTypeException(
                "entityID is not valid type, id type: " + entId.getType());
        }
    }

    /**
     * Method findServersCurrentHealthByType.
     * 
     * The current health of individual servers is displayed by
     * selecting from the servers that participated in the generation
     * of metrics from the given timeframe of interest.
     * 
     * Appropriate entities include
     * <ul> 
     * <li>applications (2.1.2.2-3)
     * <li>servers (2.3.2.1-4 - internal/deplyed tabs)
     * <li>services (2.5.2.2 - internal/deplyed tabs)
     * </ul>
     *
     * @param begin the commencement of the timeframe of interest
     * @param end the end of the timeframe of interest
     * @return List of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServersCurrentHealthByType(int sessionId, 
                                                   AppdefEntityID entId,
                                                   Integer serverTypeId,
                                                   long begin, long end,
                                                   PageControl pc) 
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
            
        // Get the associated servers        
        List servers =
            rv.getAssociatedServers(serverTypeId,PageControl.PAGE_ALL);
        
        // Return a list of current health
        return getResourcesCurrentHealth(subject, servers, begin, end, pc);
    }

    /** 
     * Return a ResourceDisplaySummary bean for each of the resource's
     * services.  The only applicable resource is currently a compatible group
     * (of services...)
     * 
     * @param entId the server's or application's ID
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServicesCurrentHealth(int sessionId,
                                              AppdefEntityID entId,
                                              PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        Pager defaultPager = Pager.getDefaultPager();
        PageList services = defaultPager.seek(rv.getAssociatedServices(pc), pc);
        
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, services);
    }
    
    /** 
     * Return a ResourceDisplaySummary bean for each of the resource's
     * services.  The only applicable resource is currently a compatible group
     * (of services...)
     * 
     * @param entId the server's or application's ID
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServicesCurrentHealthByType(int sessionId,
                                                    AppdefEntityID entId,
                                                    Integer serviceTypeId,
                                                    PageControl pc)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);

        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        PageList services = rv.getAssociatedServices(serviceTypeId, pc);

        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, services);
    }

    /**
     * Method findServicesCurrentHealthByType.
     * 
     * The current health of individual services is displayed by
     * selecting from the services that participated in the generation
     * of metrics from the given timeframe of interest.
     * 
     * Appropriate entities include
     * <ul> 
     * <li>applications (2.1.2.2-3)
     * <li>servers (2.3.2.1-4 - internal/deplyed tabs)
     * <li>services (2.5.2.2 - internal/deplyed tabs)
     * </ul>
     *
     * @param begin the commencement of the timeframe of interest
     * @param end the end of the timeframe of interest
     * @return List of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServicesCurrentHealthByType(int sessionId, 
                                                    AppdefEntityID entId,
                                                    Integer serviceTypeId,
                                                    long begin, long end,
                                                    PageControl pc) 
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);

        // Get the associated services    
        List services = rv.getAssociatedServices(serviceTypeId, PageControl.PAGE_ALL);
        
        for (Iterator it = services.iterator(); it.hasNext(); ) {
            AppdefResourceValue resource = (AppdefResourceValue) it.next();
            
            // This is the "shallow" version, so filter only top-level
            // services
           if (resource.getEntityId().getType() == AppdefEntityConstants.
                                                        APPDEF_TYPE_SERVICE) {
                ServiceValue service = (ServiceValue) resource;
                if (entId.getType() !=
                    AppdefEntityConstants.APPDEF_TYPE_SERVICE) {
                    if (service.getParentId() != null)
                        it.remove();
                }
                else {
                    if (entId.getID() != service.getParentId().intValue())
                        it.remove();
                }
            }
        }
        
        // Return a list of current health
        return getResourcesCurrentHealth(subject,services, begin, end, pc);
    }

    /**
     * Method findServicesCurrentHealthByType.
     * 
     * This overloads the vanilla findServicesCurrentHealthByType
     * adding an extra flag to traverse any services the entity
     * might have inside and look for services of the type we're
     * interested inside.  This is the "pointy haired boss" view to
     * get <i>everything</i> of that type, regardless of its parental
     * relationships.
     * 
     * See screen 2.3.2.1.3
     * 
     * @param deep point haired boss mode flag
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public PageList findServicesCurrentHealthByType(int sessionId, 
                                                    AppdefEntityID entId,
                                                    Integer serviceTypeId,
                                                    long begin, long end,
                                                    boolean deep,
                                                    PageControl pc) 
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException, AppdefEntityNotFoundException {
        
        // XXX does this api need to be fitted for cluster inclusion support?
        if (!deep) 
            return findServicesCurrentHealthByType(sessionId, entId,
                                                   serviceTypeId,
                                                   begin, end, pc);

        AuthzSubjectValue subject = manager.getSubject(sessionId);

        AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
        
        // Get the associated services    
        List services =
            rv.getAssociatedServices(serviceTypeId, PageControl.PAGE_ALL);
        
        // Return a list of current health
        return getResourcesCurrentHealth(subject, services, begin, end, pc);
    }

    /**
     * Return a ResourceDisplaySummary bean for each of the resource's services.
     * The only applicable resource is currently a compatible group (of
     * services...)
     * @return a list of ResourceDisplaySummary beans
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public List findAGServicesCurrentHealthByType(int sessionId,
                                                  AppdefEntityID[] entIds,
                                                  Integer serviceTypeId)
        throws SessionTimeoutException, SessionNotFoundException,
               InvalidAppdefTypeException, AppdefEntityNotFoundException,
               PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        PageList services = new PageList();
        PageControl pc = PageControl.PAGE_ALL;
        
        for (int i = 0; i < entIds.length; i++){
            AppdefEntityID entId = entIds[i];
            AppdefEntityValue rv = new AppdefEntityValue(entId, subject);
            services.addAll(rv.getAssociatedServices(serviceTypeId, pc));
        }
    
        // Return a paged list of current health        
        return getResourcesCurrentHealth(subject, services);
    }

    private double getAvailability(AuthzSubjectValue subject, AppdefEntityID id)
        throws AppdefEntityNotFoundException, PermissionException {
        StopWatch watch = new StopWatch();
        if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION)
            log.debug("BEGIN getAvailability()");
    
        AppdefEntityID[] ids = new AppdefEntityID[] { id };
        try {
            return getAvailability(subject, ids)[0];
        } finally {
            if (id.getType() == AppdefEntityConstants.APPDEF_TYPE_APPLICATION)
                log.debug("END getAvailability() -- " +
                          watch.getElapsed() + " msec");
        }
    }

    /** Get the availability of the resource
     * @param id the Appdef entity ID
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public double getAvailability(int sessionId, AppdefEntityID id)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return getAvailability(subject, id);
    }

    /** Get the availability of autogroup resources
     * @return a MetricValue for the availability
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public double getAGAvailability(int sessionId, AppdefEntityID[] aids,
                                    AppdefEntityTypeID ctype)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
    
        StopWatch watch = new StopWatch();
        log.debug("BEGIN getAGAvailability()");
    
        List appdefIds = getAGMemberIds(subject, aids, ctype);
        
        double ret = getAggregateAvailability(
            subject, toAppdefEntityIDArray(appdefIds));
        log.debug("END getAGAvailability() -- " + watch.getElapsed() + " msec");
        return ret;
    }

    /**
     * Returns a list of problem metrics for an autogroup, return a summarized
     * list of UI beans
     * @throws SessionTimeoutException
     * @throws SessionNotFoundException
     * @throws AppdefEntityNotFoundException
     * @throws PermissionException
     * @throws InvalidAppdefTypeException
     * @throws AppdefCompatException
     * @ejb:interface-method
     */
    public List findAllMetrics(int sessionId, AppdefEntityID aeid,
                                   AppdefEntityTypeID ctype,
                                   long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException,
               AppdefCompatException, InvalidAppdefTypeException {
        return super.findAllMetrics(sessionId, aeid, ctype, begin, end);
    }

    /**
     * Returns a list of problem metrics for a resource, and the selected
     * children and hosts of that resource.  Return a summarized
     * list of UI beans
     * @ejb:interface-method
     */
    public List findAllMetrics(int sessionId, AppdefEntityID aeid,
                                   AppdefEntityID[] hosts,
                                   AppdefEntityTypeID[] children,
                                   AppdefEntityID[] members,
                                   long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException,
               AppdefCompatException, InvalidAppdefTypeException
    {
        List singlesList = new ArrayList();
        
        if (aeid != null && members == null)
            singlesList.add(aeid);
        
        // Next add its hosts
        if (hosts != null)
            singlesList.addAll(Arrays.asList(hosts));
    
        ArrayList result = new ArrayList();
        
        // Go through the singles list first
        for (Iterator it = singlesList.iterator(); it.hasNext(); ) {
            AppdefEntityID entity = (AppdefEntityID) it.next();
            result.addAll(findAllMetrics(sessionId, entity,
                                         begin, end));
        }                
    
        if (members != null) {
            // AutoGroups and groups pass their entities in the entities
            // parameter
            List metrics = findAllMetrics(sessionId, members, begin, end);
            for (Iterator it = metrics.iterator(); it.hasNext(); ) {
                ProblemMetricSummary metric =
                    (ProblemMetricSummary) it.next();
                if (aeid.getType() ==
                    AppdefEntityConstants.APPDEF_TYPE_GROUP)
                    metric.setSingleAppdefKey(aeid.getAppdefKey());
                else
                    metric.setMultipleAppdefKey(children[0].getAppdefKey());
            }
            
            result.addAll(metrics);
        }
        else if (children != null) {
            // Go through the children list next
            for (int i = 0; i < children.length; i++) {
                result.addAll(findAllMetrics(sessionId, aeid,
                                             children[i], begin, end));
            }
        }
        
        return result;
    }

    /**
     * Returns a list of problem metrics for a resource, and the selected
     * children and hosts of that resource.  Return a summarized
     * list of UI beans
     * @ejb:interface-method
     */
    public List findAllMetrics(int sessionId, AppdefEntityID aeid,
                                   AppdefEntityID[] hosts,
                                   AppdefEntityTypeID[] children,
                                   long begin, long end)
        throws SessionTimeoutException, SessionNotFoundException,
               AppdefEntityNotFoundException, PermissionException,
               AppdefCompatException, InvalidAppdefTypeException
    {
        List singlesList = new ArrayList();
        
        if (aeid != null)
            singlesList.add(aeid);
        
        // Next add its hosts
        if (hosts != null)
            singlesList.addAll(Arrays.asList(hosts));
    
        ArrayList result = new ArrayList();
        
        if (aeid == null ||
            (aeid.getType() == AppdefEntityConstants.APPDEF_TYPE_GROUP &&
                    hosts != null)) {
            // AutoGroups and groups pass their entities in the hosts
            // parameter
            List metrics = findAllMetrics(sessionId, hosts, begin, end);
            
            for (Iterator it = metrics.iterator(); it.hasNext(); ) {
                ProblemMetricSummary metric =
                    (ProblemMetricSummary) it.next();
                if (aeid != null)
                    metric.setSingleAppdefKey(aeid.getAppdefKey());
                else
                    metric.setMultipleAppdefKey(children[0].getAppdefKey());
            }
            
            result.addAll(metrics);
        } else {
            // Go through the singles list first
            for (Iterator it = singlesList.iterator(); it.hasNext(); ) {
                AppdefEntityID entity = (AppdefEntityID) it.next();
                result.addAll(findAllMetrics(sessionId, entity,
                                             begin, end));
            }                
            
            // Go through the children list next
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    result.addAll(findAllMetrics(sessionId, aeid,
                                                 children[i], begin, end));
                }
            }
        }

        return result;
    }

    /** Invoke data compact.
     * @ejb:interface-method
     */
    public void invokeDataCompact(int sessionId)
        throws SessionTimeoutException, SessionNotFoundException
    {
        try {
            DataPurgeJob.compressData();
        } catch (CreateException e) {
            throw new SystemException(e);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Remove config and track plugins for a given resource
     *
     * @ejb:interface-method
     */
    public void removeTrackers(int sessionId, AppdefEntityID id)
        throws SessionTimeoutException, SessionNotFoundException,
               PermissionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        TrackerManagerLocal trackManager = getTrackerManager();
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
            if(ConfigTrackPlugin.isEnabled(response, id.getType())) {
                trackManager.trackPluginRemove(subject, id,
                                               ProductPlugin.
                                               TYPE_CONFIG_TRACK);
            }
    
            if(LogTrackPlugin.isEnabled(response, id.getType())) {
                trackManager.trackPluginRemove(subject, id,
                                               ProductPlugin.TYPE_LOG_TRACK);
            }
        } catch (PluginException e) {
            // Not much we can do.. plugins will be removed on next
            // agent restart.
            log.error("Unable to remove track plugins", e);
        }
    }

    /**
     * XXX:These sigar comands should be moved to a manager at some point.
     *     Maybe when permission checks are added.
     */
    private MeasurementCommandsClient getClient(AppdefEntityID aid) 
        throws PermissionException, AgentNotFoundException {
        return new MeasurementCommandsClient(AgentConnectionUtil.
                                             getClient(aid));
    }

    /**
     * Check if an entity has been enabled for log tracking
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public boolean isLogTrackEnabled(int sessionId, AppdefEntityID id)
        throws AppdefEntityNotFoundException, SessionNotFoundException, 
               SessionTimeoutException, PermissionException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        
        ConfigResponse mergedCR;
        try {
            mergedCR = getConfigManager()
                .getMergedConfigResponse(subject,
                                         ProductPlugin.TYPE_MEASUREMENT,
                                         id, true);
            return LogTrackPlugin.isEnabled(mergedCR, id.getType());
        } catch (ConfigFetchException e) {
            // This is expected for resources that are not fully configured
            log.debug(e.getMessage());
            return false;
        } catch (EncodingException e) {
            log.error(e.getMessage());
            return false;
        }

    }

    /**
     * Get the availability metric for a given resource
     * @ejb:interface-method
     */
    public DerivedMeasurement findAvailabilityMetric(int sessionId,
                                                     AppdefEntityID id)
        throws SessionTimeoutException, SessionNotFoundException
    {
        AuthzSubjectValue subject = manager.getSubject(sessionId);
        return findAvailabilityMetric(subject, id);
    }

    /** @ejb:create-method */
    public void ejbCreate() throws CreateException {}
    public void ejbActivate() throws EJBException, RemoteException {}
    public void ejbPassivate() throws EJBException, RemoteException {}
    public void ejbRemove() throws EJBException, RemoteException {
        ctx = null;
    }
    public void setSessionContext(SessionContext ctx)
        throws EJBException, RemoteException {
        this.ctx = ctx;
    }
    public SessionContext getSessionContext() {
        return ctx;
    }
}
