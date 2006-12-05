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

package org.hyperic.hq.measurement.server.session;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.Session;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.impl.SessionImpl;
import org.hyperic.hibernate.Util;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.measurement.server.session.Category;
import org.hyperic.hq.measurement.server.session.DerivedMeasurement;
import org.hyperic.hq.measurement.server.session.MeasurementArg;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.server.session.MeasurementTemplate;
import org.hyperic.hq.measurement.server.session.MonitorableType;
import org.hyperic.hq.measurement.TemplateNotFoundException;
import org.hyperic.hq.measurement.shared.MeasurementArgValue;
import org.hyperic.hq.measurement.shared.MeasurementTemplateLiteValue;
import org.hyperic.hq.measurement.shared.MeasurementTemplateValue;
import org.hyperic.hq.measurement.shared.RawMeasurementManagerLocal;
import org.hyperic.hq.measurement.shared.RawMeasurementManagerUtil;
import org.hyperic.hq.measurement.shared.SRNManagerLocal;
import org.hyperic.hq.product.MeasurementInfo;
import org.hyperic.hq.product.PluginNotFoundException;
import org.hyperic.hq.product.TypeInfo;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;

/** The TemplateManagerEJB class is a stateless session bean that can be
 *  used to interact with Template EJB's
 *
 * @ejb:bean name="TemplateManager"
 *      jndi-name="ejb/measurement/TemplateManager"
 *      local-jndi-name="LocalTemplateManager"
 *      view-type="local"
 *      type="Stateless"
 *
 * @ejb:transaction type="REQUIRED"
 */
public class TemplateManagerEJBImpl extends SessionEJB implements SessionBean {
    private final Log log = LogFactory.getLog(TemplateManagerEJBImpl.class);

    protected final String VALUE_PROCESSOR =
        PagerProcessor_measurement.class.getName();
        
    private Pager valuePager = null;

    /**
     * Create a DerivedMeasurement Template
     * @todo This needs to support Designate and DefaultOn
     * @return a MeasurementTemplate ID
     * @ejb:interface-method
     */
    public MeasurementTemplateValue createTemplate(AuthzSubjectValue subject,
                                                   String name, String alias,
                                                   String type,
                                                   String catName,
                                                   String template,
                                                   String units,
                                                   int collectionType,
                                                   MeasurementArgValue[] args)
        throws CreateException {

        MonitorableType t = getMonitorableTypeDAO().findByName(type);
        Category cat = null;
        if (catName != null) { 
           cat = getCategoryDAO().findByName(catName);
        }
        
        ArrayList lis = null;
            
        if (args != null) {
            lis = new ArrayList();
            for (int i = 0; i < args.length; i++) {
                MeasurementTemplate arg =
                    getMeasurementTemplateDAO().findById(args[i].getId());
                MeasurementArg li =
                    getMeasurementArgDAO().create(new Integer(i+1), arg,
                                                  args[i].getTicks(),
                                                  args[i].getWeight(),
                                                  args[i].getPrevious());
                lis.add(li);
            }
        }
        
        MeasurementTemplateLiteValue lite =
            new MeasurementTemplateLiteValue(null, name, alias, units, 
                                             collectionType, false, 
                                             MeasurementConstants.
                                             INTERVAL_DEFAULT_MILLIS,
                                             false, template, null, null);
        MeasurementTemplate mt =
            getMeasurementTemplateDAO().create(lite, t, cat, lis);
        return mt.getMeasurementTemplateValue();
    }

    /**
     * Get a measurement template
     *
     * @return a MeasurementTemplate value
     * @ejb:interface-method
     */
    public MeasurementTemplateValue getTemplate(Integer id) {
       MeasurementTemplate m = getMeasurementTemplateDAO().findById(id);
       return m.getMeasurementTemplateValue();
    }

    /**
     * Look up a measurement templates for an array of template IDs
     *
     * @throws FinderException if no measurement templates are found.
     * @return a MeasurementTemplate value
     * 
     * @ejb:interface-method
     */
    public PageList getTemplates(Integer[] ids, PageControl pc)
        throws TemplateNotFoundException {
        List mts = getMeasurementTemplateDAO().findTemplates(ids);

        if (ids.length != mts.size())
            throw new TemplateNotFoundException("Could not look up " + ids);

        if (pc.getSortorder() == PageControl.SORT_DESC)
            Collections.reverse(mts);

        return valuePager.seek(mts, pc);
    }

    /**
     * Look up a measurement templates for a monitorable type and
     * category.
     *
     * @return a MeasurementTemplate value
     * @ejb:interface-method
     */
    public PageList findTemplates(String type, String cat,
                                  Integer[] excludeIds, PageControl pc) {
        MeasurementTemplateDAO dao = getMeasurementTemplateDAO();

        List templates;
        if (cat == null) {
            templates = dao.findTemplatesByMonitorableType(type);
        } else {
            templates = dao.findTemplatesByMonitorableTypeAndCategory(type,
                                                                      cat);
        }

        if (templates == null) {
            return new PageList();
        }

        // Handle excludes
        List includes;
        if (excludeIds == null) {
            includes = templates;
        } else {
            HashSet excludes = new HashSet(Arrays.asList(excludeIds));
            includes = new ArrayList();
            for (Iterator it = templates.iterator(); it.hasNext(); ) {
                MeasurementTemplate tmpl =
                    (MeasurementTemplate)it.next();
                if (!excludes.contains(tmpl.getId()))
                    includes.add(tmpl);
            }
        }
        
        pc = PageControl.initDefaults(pc, -1);
        if (pc.getSortorder() == PageControl.SORT_DESC)
            Collections.reverse(includes);
        
        return valuePager.seek(includes, pc);
    }

    /**
     * Look up a measurement templates for a monitorable type and filtered
     * by categories and keyword.
     *
     * @return a MeasurementTemplate value
     * @ejb:interface-method
     */
    public List findTemplates(String type, long filters, String keyword) {
        MeasurementTemplateDAO dao = getMeasurementTemplateDAO();
        List mts;
            
        if ((filters & MeasurementConstants.FILTER_AVAIL) == 0 ||
            (filters & MeasurementConstants.FILTER_UTIL)  == 0 ||
            (filters & MeasurementConstants.FILTER_THRU)  == 0 ||
            (filters & MeasurementConstants.FILTER_PERF)  == 0) {
            mts = new ArrayList();
                
            // Go through each filter
            if ((filters & MeasurementConstants.FILTER_AVAIL) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type,
                               MeasurementConstants.CAT_AVAILABILITY));
            }
            if ((filters & MeasurementConstants.FILTER_UTIL) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type,
                               MeasurementConstants.CAT_UTILIZATION));
            }
            if ((filters & MeasurementConstants.FILTER_THRU) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type,
                               MeasurementConstants.CAT_THROUGHPUT));
            }
            if ((filters & MeasurementConstants.FILTER_PERF) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type,
                               MeasurementConstants.CAT_PERFORMANCE));
            }
        } else {
            mts = dao.findTemplatesByMonitorableType(type);
        }

        if (mts == null) {
            return new PageList();
        }
    
        // Check filter types
        for (Iterator it = mts.iterator(); it.hasNext(); ) {
            MeasurementTemplate tmpl = (MeasurementTemplate)it.next();

            // First, keyword
            if (keyword != null && keyword.length() > 0) {
                if (tmpl.getName().indexOf(keyword) < 0) {
                    it.remove();
                    continue;
                }
            }

            switch (tmpl.getCollectionType()) {
            case MeasurementConstants.COLL_TYPE_DYNAMIC:
                if ((filters & MeasurementConstants.FILTER_DYN) == 0)
                    it.remove();
                break;
            case MeasurementConstants.COLL_TYPE_STATIC:
                if ((filters & MeasurementConstants.FILTER_STATIC) == 0)
                    it.remove();
                break;
            case MeasurementConstants.COLL_TYPE_TRENDSUP:
                if ((filters&MeasurementConstants.FILTER_TREND_UP) == 0)
                    it.remove();
                break;
            case MeasurementConstants.COLL_TYPE_TRENDSDOWN:
                if ((filters&MeasurementConstants.FILTER_TREND_DN) == 0)
                    it.remove();
                break;
            default:
                break;
            }
        }
        
        return valuePager.seek(mts, PageControl.PAGE_ALL);
    }

    /**
     * Look up a measurement template IDs for a monitorable type.
     *
     * @return an array of ID values
     * @ejb:interface-method
     */
    public Integer[] findTemplateIds(String type) {
        List mts = getMeasurementTemplateDAO().
            findTemplatesByMonitorableType(type);

        if (mts == null) {
            return new Integer[0];
        }

        Integer[] ids = new Integer[mts.size()];
        Iterator it = mts.iterator();
        for (int i = 0; it.hasNext(); i++) {
            MeasurementTemplate tmpl =
                (MeasurementTemplate) it.next();
            ids[i] = tmpl.getId();
        }
        return ids;
    }

    /**
     * Look up default measurement templates for a monitorable
     * and agent type.
     *
     * @return a MeasurementTemplate value
     * @ejb:interface-method
     */
    public List findDefaultTemplates(String mtype, int atype) {
        List mts = getMeasurementTemplateDAO().
            findDefaultsByMonitorableType(mtype, atype);
        if (mts == null) {
            return new ArrayList();
        }
        
        ArrayList mtList = new ArrayList();
        for (Iterator i = mts.iterator(); i.hasNext();) {
            MeasurementTemplate mt =
                (MeasurementTemplate)i.next();
            mtList.add(mt.getMeasurementTemplateValue());
        }
        return mtList;
    }

    /**
     * Look up designated measurement templates for a monitorable
     * and agent type.
     *
     * @return a MeasurementTemplate value
     * @ejb:interface-method
     */
    public List findDesignatedTemplates(String mtype, int atype) {
        List mts = getMeasurementTemplateDAO().
            findDesignatedByMonitorableType(mtype, atype);
        if (mts == null) {
            return new ArrayList();
        }
        
        ArrayList mtList = new ArrayList();
        for (Iterator i = mts.iterator(); i.hasNext();) {
            MeasurementTemplate mt =
                (MeasurementTemplate)i.next();
            mtList.add(mt.getMeasurementTemplateValue());
        }
        return mtList;
    }

    /** List of all monitorable types
     * @return List of monitorable types
     * @ejb:interface-method
     */
    public List findMonitorableTypes() {
        List monitorableTypes = new ArrayList();

        Collection types = getMonitorableTypeDAO().findAll();
        for (Iterator i = types.iterator(); i.hasNext(); ) {
            MonitorableType mt = (MonitorableType)i.next();
            monitorableTypes.add(mt.getMonitorableTypeValue());
        }
        return monitorableTypes;
    }

    /**
     * Remove a measurement template
     *
     * @return a MeasurementTemplate value
     * @ejb:interface-method
     */
    public void removeTemplate(AuthzSubjectValue subject, Integer tid) {
        MeasurementTemplate t = getMeasurementTemplateDAO().findById(tid);
        getMeasurementTemplateDAO().remove(t);

        Collection args = t.getMeasurementArgs();
        // Remove raw measurement templates as well
        for (Iterator i = args.iterator(); i.hasNext(); ) {
            MeasurementArg arg = (MeasurementArg)i.next();
            MeasurementTemplate raw = arg.getTemplateArg();
            getMeasurementTemplateDAO().remove(raw);
        }
    }

    /**
     * Get the Product Monitoring Configuration
     *
     * @return A String of HTML help.
     * @ejb:interface-method
     */
    public String getMonitoringHelp(AuthzSubjectValue subject,
                                    AppdefEntityValue entityVal,
                                    Map props)
        throws PluginNotFoundException, PermissionException,
               AppdefEntityNotFoundException
    {
        return this.getProductMan().getMonitoringHelp(subject, entityVal,
                                                      props);
    }
    
    /**
     * Update the default interval for a list of meas. templates
     * @param templIds - a list of integer template ids
     * @param interval - the interval of collection to set to
     * @ejb:interface-method 
     */
    public void updateTemplateDefaultInterval(Integer[] templIds, long interval) 
        throws TemplateNotFoundException {
        long current = System.currentTimeMillis();

        for (int i = 0; i < templIds.length; i++) {
            MeasurementTemplate template =
                getMeasurementTemplateDAO().findById(templIds[i]);

            if (interval != template.getDefaultInterval())
                template.setDefaultInterval(interval);

            if (!template.isDefaultOn())
                template.setDefaultOn(interval != 0);

            template.setMtime(current);

            List metrics =
                getDerivedMeasurementDAO().findByTemplate(templIds[i]);
            SRNManagerLocal srnManager = getSRNManager();
            SRNCache cache = SRNCache.getInstance();

            for (Iterator it = metrics.iterator(); it.hasNext(); ) {
                DerivedMeasurement dm = (DerivedMeasurement)it.next();

                if (dm.getInterval() != interval)
                    dm.setInterval(interval);

                if (template.isDefaultOn() != dm.isEnabled())
                    dm.setEnabled(template.isDefaultOn());

                AppdefEntityID aeid =
                    new AppdefEntityID(dm.getAppdefType(),
                                       dm.getInstanceId());
                ScheduleRevNum srn = cache.get(aeid);
                if (srn != null) {  // Increment SRN only if not null
                    srnManager.beginIncrementSrn(aeid,
                                                 Math.min(interval,
                                                          srn.
                                                          getMinInterval()));
                    srnManager.endIncrementSrn(aeid);
                }
            }
        }
    }
    
    /**
     * Make metrics disabled by default for a list of meas. templates
     * @param templIds - a list of integer template ids
     * @ejb:interface-method 
     */
    public void enableTemplateByDefault(Integer[] templIds, boolean on) 
        throws TemplateNotFoundException {
        long current = System.currentTimeMillis();

        for (int i = 0; i < templIds.length; i++) {
            MeasurementTemplate template =
                getMeasurementTemplateDAO().findById(templIds[i]);

            template.setDefaultOn(on);
            template.setMtime(current);

            List metrics =
                getDerivedMeasurementDAO().findByTemplate(templIds[i]);
            SRNManagerLocal srnManager = getSRNManager();
            for (Iterator it = metrics.iterator(); it.hasNext(); ) {
                DerivedMeasurement dm = (DerivedMeasurement)it.next();

                if (dm.isEnabled() == on || dm.getInterval() == 0)
                    continue;

                dm.setEnabled(on);
                dm.setMtime(current);

                AppdefEntityID aeid =
                    new AppdefEntityID(dm.getAppdefType(),
                                       dm.getInstanceId());
                ScheduleRevNum srn = srnManager.get(aeid);
                long minInterval = (srn == null) ? dm.getInterval() :
                    srn.getMinInterval();
                srnManager.beginIncrementSrn(aeid, minInterval);
                srnManager.endIncrementSrn(aeid);
            }
        }
    }
    
    /**
     * Get the MonitorableType id, creating it if it does not exist.
     *
     * @todo: This should just return the pojo and be named getMonitorableType.
     *
     * @ejb:interface-method
     */
    public Integer getMonitorableTypeId(String pluginName, TypeInfo info) {
        MonitorableType t = getMonitorableTypeDAO().findByName(info.getName());
        
        if (t == null) {
            int a, e;
            e = info.getType();
            a = AppdefEntityConstants.entityInfoTypeToAppdefType(e);
            t = getMonitorableTypeDAO().create(info.getName(), a, pluginName);
        }
      
        return t.getId();
    }

    /**
     * Update measurement templates for a given entity.  This still
     * needs some refactoring.
     *
     * @return A map of measurement info's that are new and will need
     * to be created.
     * @ejb:interface-method 
     */
    public Map updateTemplates(String pluginName, 
                               TypeInfo ownerEntity,
                               Integer monitorableTypeId,
                               MeasurementInfo[] tmpls)
        throws CreateException, RemoveException {

        MeasurementTemplateDAO dao = getMeasurementTemplateDAO();

        // Organize the templates first
        HashMap tmap = new HashMap();
        for (int i = 0; i < tmpls.length; i++) {
            tmap.put(tmpls[i].getAlias(), tmpls[i]);
        }
        
        Collection mts = dao.findRawByMonitorableType(monitorableTypeId);
        
        for (Iterator i = mts.iterator(); i.hasNext();) {
            MeasurementTemplate mt =
                (MeasurementTemplate) i.next();
            
            // See if this is in the list
            MeasurementInfo info =
                (MeasurementInfo) tmap.remove(mt.getAlias());

            if (info == null) {
                dao.remove(mt);
            } else {
                dao.update(mt, pluginName, info);
            }
        }
        return tmap;
    }

    /**
     * Add new measurement templates for a plugin.
     *
     * This does a batch style insert, and expects a map of maps
     * indexed by the monitorable type id.
     *
     * @ejb:interface-method 
     */
    public void createTemplates(String pluginName, Map toAdd)
        throws CreateException {
        // Add the new templates
        Connection conn = null;
        PreparedStatement tStmt = null;
        PreparedStatement aStmt = null;

        SessionFactoryImpl sessionFactory =
            (SessionFactoryImpl)Util.getSessionFactory();
        Session session =
            getMeasurementTemplateDAO().getSession();
        try {
            IdentifierGenerator tmplIdGenerator =
                sessionFactory
                .getEntityPersister(MeasurementTemplate.class.getName())
                .getIdentifierGenerator();

            IdentifierGenerator argIdGenerator =
                sessionFactory
                .getEntityPersister(MeasurementArg.class.getName())
                .getIdentifierGenerator();

            conn = session.connection();

            final String templatesql = "INSERT INTO EAM_MEASUREMENT_TEMPL " +
                "(id, name, alias, units, collection_type, default_on, " +
                "default_interval, designate, monitorable_type_id, " +
                "category_id, template, plugin, ctime, mtime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            final String argsql = "INSERT INTO EAM_MEASUREMENT_ARG " +
                "(id, measurement_template_id, measurement_template_arg_id, " +
                "placement, ticks, weight, previous) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

            long current = System.currentTimeMillis();

            // can assume this is called in a single thread
            // This is called at hq server startup
            HashMap cats = new HashMap();
            for (Iterator i = toAdd.keySet().iterator(); i.hasNext();) {
                Integer monitorableTypeId = (Integer)i.next();
                Map newMetrics = (Map)toAdd.get(monitorableTypeId);
                
                for (Iterator j = newMetrics.values().iterator(); j.hasNext();){
                    MeasurementInfo info = (MeasurementInfo)j.next();

                    Category cat =
                        (Category) cats.get(info.getCategory());
                    if (cat == null) {
                        cat = getCategoryDAO().findByName(info.getCategory());
                        if (cat == null) {
                            cat = getCategoryDAO().create(info.getCategory());
                        }
                        cats.put(info.getCategory(), cat);
                    }

                    int col = 1;
                    Integer rawid = (Integer)tmplIdGenerator.
                        generate((SessionImpl)session, new MeasurementTemplate());

                    tStmt = conn.prepareStatement(templatesql);
                    tStmt.setInt(col++, rawid.intValue());
                    tStmt.setString(col++, info.getName());
                    tStmt.setString(col++, info.getAlias());
                    tStmt.setString(col++, info.getUnits());
                    tStmt.setInt(col++, info.getCollectionType());
                    tStmt.setBoolean(col++, false);
                    tStmt.setLong(col++, 0l);
                    tStmt.setBoolean(col++, false);
                    tStmt.setInt(col++, monitorableTypeId.intValue());
                    tStmt.setInt(col++, cat.getId().intValue());
                    tStmt.setString(col++, info.getTemplate());
                    tStmt.setString(col++, pluginName);
                    tStmt.setLong(col++, current);
                    tStmt.setLong(col++, current);
                    tStmt.execute();
                    tStmt.close();

                    Integer derivedid = (Integer)tmplIdGenerator.
                        generate((SessionImpl)session, new MeasurementTemplate());
                    
                    // Next, create the derived measurement
                    col = 1;
                    tStmt = conn.prepareStatement(templatesql);
                    tStmt.setInt(col++, derivedid.intValue());
                    tStmt.setString(col++, info.getName());
                    tStmt.setString(col++, info.getAlias());
                    tStmt.setString(col++, info.getUnits());
                    tStmt.setInt(col++, info.getCollectionType());
                    tStmt.setBoolean(col++, info.isDefaultOn());
                    tStmt.setLong(col++, info.getInterval());
                    tStmt.setBoolean(col++, info.isIndicator());
                    tStmt.setInt(col++, monitorableTypeId.intValue());
                    tStmt.setInt(col++, cat.getId().intValue());
                    tStmt.setString(col++, MeasurementConstants.TEMPL_IDENTITY);
                    tStmt.setString(col++, pluginName);
                    tStmt.setLong(col++, current);
                    tStmt.setLong(col++, current);
                    tStmt.execute();
                    tStmt.close();
                
                    Integer argid = (Integer)argIdGenerator.
                        generate((SessionImpl)session, new MeasurementArg());

                    // Lastly, create the line item
                    col = 1;
                    aStmt = conn.prepareStatement(argsql);
                    aStmt.setInt(col++, argid.intValue());
                    aStmt.setInt(col++, derivedid.intValue());
                    aStmt.setInt(col++, rawid.intValue());
                    aStmt.setInt(col++, 1);
                    aStmt.setInt(col++, 0);
                    aStmt.setFloat(col++, 0f);
                    aStmt.setInt(col++, 0);
                    aStmt.execute();
                    aStmt.close();
                }
            }
        } catch (SQLException e) {
            this.log.error("Unable to add measurements for: " +
                           pluginName, e);
        } finally {
            session.disconnect();
        }
    }
 
    /** 
     * Set the measurement templates to be "designated" for a monitorable type.
     *
     * @ejb:interface-method
     */
    public void setDesignatedTemplates(String mType, Integer[] desigIds) {

        List derivedTemplates = getMeasurementTemplateDAO()
            .findDerivedByMonitorableType(mType);
            
        HashSet designates = new HashSet();
        designates.addAll(Arrays.asList(desigIds));
            
        for (Iterator i = derivedTemplates.iterator(); i.hasNext();) {
            MeasurementTemplate template = (MeasurementTemplate)i.next();

            // Never turn off Availability as an indicator
            if (template.getAlias().equalsIgnoreCase(
                    MeasurementConstants.CAT_AVAILABILITY))
                    continue;

            boolean designated = designates
                    .contains(template.getId());

            if (designated != template.isDesignate()) {
                template.setDesignate(designated);
            }
        }
    }

    public void ejbCreate() throws CreateException {
        try {
            valuePager = Pager.getPager(VALUE_PROCESSOR);
        } catch (Exception e) {
            throw new CreateException("Could not create value pager: " + e);
        }
    }

    public void ejbPostCreate() {}

    public void ejbActivate() {}

    public void ejbPassivate() {}

    public void ejbRemove() {
        this.ctx = null;
    }

    public void setSessionContext(SessionContext ctx)
        throws EJBException, RemoteException {
        this.ctx = ctx;
    }
}
