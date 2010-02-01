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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.impl.SessionImpl;
import org.hyperic.hibernate.PageInfo;
import org.hyperic.hibernate.Util;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.TemplateNotFoundException;
import org.hyperic.hq.measurement.shared.SRNManager;
import org.hyperic.hq.measurement.shared.TemplateManager;
import org.hyperic.hq.product.MeasurementInfo;
import org.hyperic.hq.product.TypeInfo;
import org.hyperic.util.StringUtil;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The TemplateManager can be used to interact with Templates
 */
@Service
@Transactional
public class TemplateManagerImpl implements TemplateManager {
    private final Log log = LogFactory.getLog(TemplateManagerImpl.class);
    private CategoryDAO categoryDAO;
    private MeasurementDAO measurementDAO;
    private MeasurementTemplateDAO measurementTemplateDAO;
    private MonitorableTypeDAO monitorableTypeDAO;
    private ScheduleRevNumDAO scheduleRevNumDAO;
    private SRNManager srnManager;

    @Autowired
    public TemplateManagerImpl(CategoryDAO categoryDAO, MeasurementDAO measurementDAO,
                               MeasurementTemplateDAO measurementTemplateDAO, MonitorableTypeDAO monitorableTypeDAO,
                               ScheduleRevNumDAO scheduleRevNumDAO, SRNManager srnManager) {
        this.categoryDAO = categoryDAO;
        this.measurementDAO = measurementDAO;
        this.measurementTemplateDAO = measurementTemplateDAO;
        this.monitorableTypeDAO = monitorableTypeDAO;
        this.scheduleRevNumDAO = scheduleRevNumDAO;
        this.srnManager = srnManager;
    }

    /**
     * Get a MeasurementTemplate
     */
    public MeasurementTemplate getTemplate(Integer id) {
        return measurementTemplateDAO.get(id);
    }

    /**
     * Look up measurement templates for an array of template IDs
     */
    public List<MeasurementTemplate> getTemplates(List<Integer> ids) {
        Integer[] mtids = ids.toArray(new Integer[ids.size()]);
        return measurementTemplateDAO.findTemplates(mtids);
    }

    /**
     * Look up a measurement templates for an array of template IDs
     * 
     * @throws TemplateNotFoundException if no measurement templates are found.
     * @return a MeasurementTemplate value
     */
    public List<MeasurementTemplate> getTemplates(Integer[] ids, PageControl pc) throws TemplateNotFoundException {
        List<MeasurementTemplate> mts = measurementTemplateDAO.findTemplates(ids);

        if (ids.length != mts.size()) {
            throw new TemplateNotFoundException("Could not look up " + StringUtil.arrayToString(ids));
        }

        if (pc.getSortorder() == PageControl.SORT_DESC) {
            Collections.reverse(mts);
        }

        return mts;
    }

    /**
     * Get all the templates. Must be superuser to execute.
     * 
     * @param pInfo must contain a sort field of type
     *        {@link MeasurementTemplateSortField}
     * @param defaultOn If non-null, return templates with defaultOn ==
     *        defaultOn
     * 
     * @return a list of {@link MeasurementTemplate}s
     */
    public List<MeasurementTemplate> findTemplates(AuthzSubject user, PageInfo pInfo, Boolean defaultOn)
        throws PermissionException {
        assertSuperUser(user);
        return measurementTemplateDAO.findAllTemplates(pInfo, defaultOn);
    }

    /**
     * Get all templates for a given MonitorableType
     * 
     * @param pInfo must contain a sort field of type
     *        {@link MeasurementTemplateSortField}
     * @param defaultOn If non-null, return templates with defaultOn ==
     *        defaultOn
     * 
     * @return a list of {@link MeasurementTemplate}s
     */
    public List<MeasurementTemplate> findTemplatesByMonitorableType(AuthzSubject user, PageInfo pInfo, String type,
                                                                    Boolean defaultOn) throws PermissionException {
        assertSuperUser(user);
        return measurementTemplateDAO.findTemplatesByMonitorableType(pInfo, type, defaultOn);
    }

    private void assertSuperUser(AuthzSubject s) throws PermissionException {
        boolean authorized = PermissionManagerFactory.getInstance().hasAdminPermission(s.getId());

        if (!authorized) {
            throw new PermissionException("Permission denied");
        }
    }

    /**
     * Look up a measurement templates for a monitorable type and category.
     * 
     * @return a MeasurementTemplate value
     */
    public List<MeasurementTemplate> findTemplates(String type, String cat, Integer[] excludeIds, PageControl pc) {
        List<MeasurementTemplate> templates;
        if (cat == null) {
            templates = measurementTemplateDAO.findTemplatesByMonitorableType(type);
        } else {
            templates = measurementTemplateDAO.findTemplatesByMonitorableTypeAndCategory(type, cat);
        }

        if (templates == null) {
            return new PageList<MeasurementTemplate>();
        }

        // Handle excludes
        List<MeasurementTemplate> includes;
        if (excludeIds == null) {
            includes = templates;
        } else {
            HashSet<Integer> excludes = new HashSet<Integer>(Arrays.asList(excludeIds));
            includes = new ArrayList<MeasurementTemplate>();
            for (MeasurementTemplate tmpl : templates) {
                if (!excludes.contains(tmpl.getId()))
                    includes.add(tmpl);
            }
        }

        pc = PageControl.initDefaults(pc, -1);
        if (pc.getSortorder() == PageControl.SORT_DESC) {
            Collections.reverse(includes);
        }

        return templates;
    }

    /**
     * Look up a measurement templates for a monitorable type and filtered by
     * categories and keyword.
     * 
     * @return a MeasurementTemplate value
     */
    public List<MeasurementTemplate> findTemplates(String type, long filters, String keyword) {
        MeasurementTemplateDAO dao = measurementTemplateDAO;
        List<MeasurementTemplate> mts;

        if ((filters & MeasurementConstants.FILTER_AVAIL) == 0 || (filters & MeasurementConstants.FILTER_UTIL) == 0 ||
            (filters & MeasurementConstants.FILTER_THRU) == 0 || (filters & MeasurementConstants.FILTER_PERF) == 0) {
            mts = new ArrayList<MeasurementTemplate>();

            // Go through each filter
            if ((filters & MeasurementConstants.FILTER_AVAIL) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type, MeasurementConstants.CAT_AVAILABILITY));
            }
            if ((filters & MeasurementConstants.FILTER_UTIL) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type, MeasurementConstants.CAT_UTILIZATION));
            }
            if ((filters & MeasurementConstants.FILTER_THRU) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type, MeasurementConstants.CAT_THROUGHPUT));
            }
            if ((filters & MeasurementConstants.FILTER_PERF) > 0) {
                mts.addAll(dao.findTemplatesByMonitorableTypeAndCategory(type, MeasurementConstants.CAT_PERFORMANCE));
            }
        } else {
            mts = dao.findTemplatesByMonitorableType(type);
        }

        if (mts == null) {
            return new PageList<MeasurementTemplate>();
        }

        // Check filter types
        for (Iterator<MeasurementTemplate> it = mts.iterator(); it.hasNext();) {
            MeasurementTemplate tmpl = it.next();

            // First, keyword
            if (StringUtil.stringDoesNotExist(tmpl.getName(), keyword)) {
                it.remove();
                continue;
            }

            switch (tmpl.getCollectionType()) {
                case MeasurementConstants.COLL_TYPE_DYNAMIC:
                    if ((filters & MeasurementConstants.FILTER_DYN) == 0) {
                        it.remove();
                    }
                    break;
                case MeasurementConstants.COLL_TYPE_STATIC:
                    if ((filters & MeasurementConstants.FILTER_STATIC) == 0) {
                        it.remove();
                    }
                    break;
                case MeasurementConstants.COLL_TYPE_TRENDSUP:
                    if ((filters & MeasurementConstants.FILTER_TREND_UP) == 0) {
                        it.remove();
                    }
                    break;
                case MeasurementConstants.COLL_TYPE_TRENDSDOWN:
                    if ((filters & MeasurementConstants.FILTER_TREND_DN) == 0) {
                        it.remove();
                    }
                    break;
                default:
                    break;
            }
        }

        return mts;
    }

    /**
     * Look up a measurement template IDs for a monitorable type.
     * 
     * @return an array of ID values
     */
    public Integer[] findTemplateIds(String type) {
        List<MeasurementTemplate> mts = measurementTemplateDAO.findTemplatesByMonitorableType(type);

        if (mts == null) {
            return new Integer[0];
        }

        Integer[] ids = new Integer[mts.size()];
        Iterator<MeasurementTemplate> it = mts.iterator();
        for (int i = 0; it.hasNext(); i++) {
            MeasurementTemplate tmpl = it.next();
            ids[i] = tmpl.getId();
        }
        return ids;
    }

    /**
     * Update the default interval for a list of meas. templates
     * 
     * @subject - the subject
     * @param templIds - a list of integer template ids
     * @param interval - the interval of collection to set to
     */
    public void updateTemplateDefaultInterval(AuthzSubject subject, Integer[] templIds, long interval) {
        HashSet<AppdefEntityID> toReschedule = new HashSet<AppdefEntityID>();
        for (int i = 0; i < templIds.length; i++) {
            MeasurementTemplate template = measurementTemplateDAO.findById(templIds[i]);

            if (interval != template.getDefaultInterval()) {
                template.setDefaultInterval(interval);
            }

            if (!template.isDefaultOn()) {
                template.setDefaultOn(interval != 0);
            }
            final List<Measurement> measurements = measurementDAO.findByTemplate(template.getId());
            for (Measurement m : measurements) {
                m.setEnabled(template.isDefaultOn());
                m.setInterval(template.getDefaultInterval());
            }

            List<AppdefEntityID> appdefEntityIds = measurementDAO.findAppdefEntityIdsByTemplate(template.getId());

            toReschedule.addAll(appdefEntityIds);
        }

        SRNCache cache = SRNCache.getInstance();

        int count = 0;
        for (AppdefEntityID id : toReschedule) {
            ScheduleRevNum srn = cache.get(id);
            if (srn != null) {
                srnManager.incrementSrn(id, Math.min(interval, srn.getMinInterval()));
                if (++count % 100 == 0) {
                    scheduleRevNumDAO.flushSession();
                }
            }
        }

        scheduleRevNumDAO.flushSession();
    }

    /**
     * Make metrics disabled by default for a list of meas. templates
     * @param templIds - a list of integer template ids
     */
    public void setTemplateEnabledByDefault(AuthzSubject subject, Integer[] templIds, boolean on) {

        long current = System.currentTimeMillis();

        Map<AppdefEntityID, Long> aeids = new HashMap<AppdefEntityID, Long>();
        for (Integer templateId : templIds) {
            MeasurementTemplate template = measurementTemplateDAO.findById(templateId);

            template.setDefaultOn(on);

            List<Measurement> metrics = measurementDAO.findByTemplate(templateId);
            for (Measurement dm : metrics) {
                if (dm.isEnabled() == on) {
                    continue;
                }

                dm.setEnabled(on);
                dm.setMtime(current);

                if (dm.isEnabled() && dm.getInterval() == 0) {
                    dm.setInterval(template.getDefaultInterval());
                }

                final AppdefEntityID aeid = new AppdefEntityID(dm.getAppdefType(), dm.getInstanceId());

                Long min = new Long(dm.getInterval());
                if (aeids.containsKey(aeid)) {
                    // Set the minimum interval
                    min = new Long(Math.min(((Long) aeids.get(aeid)).longValue(), min.longValue()));
                }
                aeids.put(aeid, min);
            }
        }

        for (Map.Entry<AppdefEntityID, Long> entry : aeids.entrySet()) {
            AppdefEntityID aeid = entry.getKey();
            ScheduleRevNum srn = srnManager.get(aeid);
            srnManager.incrementSrn(aeid, (srn == null) ? entry.getValue().longValue() : srn.getMinInterval());
        }
    }

    /**
     * Get the MonitorableType id, creating it if it does not exist.
     * 
     * @todo: This should just return the pojo and be named getMonitorableType.
     */
    public MonitorableType getMonitorableType(String pluginName, TypeInfo info) {
        MonitorableType t = monitorableTypeDAO.findByName(info.getName());

        if (t == null) {
            int e = info.getType();
            int a = entityInfoTypeToAppdefType(e);
            t = monitorableTypeDAO.create(info.getName(), a, pluginName);
        }

        return t;
    }

    private int entityInfoTypeToAppdefType(int entityInfoType) {
        switch (entityInfoType) {
            case TypeInfo.TYPE_PLATFORM:
                return AppdefEntityConstants.APPDEF_TYPE_PLATFORM;
            case TypeInfo.TYPE_SERVER:
                return AppdefEntityConstants.APPDEF_TYPE_SERVER;
            case TypeInfo.TYPE_SERVICE:
                return AppdefEntityConstants.APPDEF_TYPE_SERVICE;
            default:
                throw new IllegalArgumentException("Unknown TypeInfo type");
        }
    }

    /**
     * Update measurement templates for a given entity. This still needs some
     * refactoring.
     * 
     * @return A map of measurement info's that are new and will need to be
     *         created.
     */
    public Map<String, MeasurementInfo> updateTemplates(String pluginName, TypeInfo ownerEntity,
                                                        MonitorableType monitorableType, MeasurementInfo[] tmpls) {
        // Organize the templates first
        Map<String, MeasurementInfo> tmap = new HashMap<String, MeasurementInfo>();
        for (int i = 0; i < tmpls.length; i++) {
            tmap.put(tmpls[i].getAlias(), tmpls[i]);
        }

        Collection<MeasurementTemplate> mts = measurementTemplateDAO.findRawByMonitorableType(monitorableType);

        for (MeasurementTemplate mt : mts) {
            // See if this is in the list
            MeasurementInfo info = (MeasurementInfo) tmap.remove(mt.getAlias());

            if (info == null) {
                measurementDAO.remove(mt);
                measurementTemplateDAO.remove(mt);
            } else {
                measurementTemplateDAO.update(mt, pluginName, info);
            }
        }
        return tmap;
    }

    /**
     * Add new measurement templates for a plugin.
     * 
     * This does a batch style insert, and expects a map of maps indexed by the
     * monitorable type id.
     */
    public void createTemplates(String pluginName, Map<MonitorableType, Map<?, MeasurementInfo>> toAdd) {
        // Add the new templates
        PreparedStatement stmt;

        SessionFactoryImpl sessionFactory = (SessionFactoryImpl) Util.getSessionFactory();
        Session session = measurementTemplateDAO.getSession();
        try {
            IdentifierGenerator tmplIdGenerator = sessionFactory
                .getEntityPersister(MeasurementTemplate.class.getName()).getIdentifierGenerator();

            Connection conn = Util.getConnection();

            final String templatesql = "INSERT INTO EAM_MEASUREMENT_TEMPL "
                                       + "(id, name, alias, units, collection_type, default_on, "
                                       + "default_interval, designate, monitorable_type_id, "
                                       + "category_id, template, plugin, ctime, mtime) "
                                       + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            long current = System.currentTimeMillis();

            // can assume this is called in a single thread
            // This is called at hq server startup
            HashMap<String, Category> cats = new HashMap<String, Category>();
            for (Map.Entry<MonitorableType, Map<?, MeasurementInfo>> entry : toAdd.entrySet()) {
                MonitorableType monitorableType = entry.getKey();
                Map<?, MeasurementInfo> newMetrics = entry.getValue();

                for (MeasurementInfo info : newMetrics.values()) {
                    Category cat = (Category) cats.get(info.getCategory());
                    if (cat == null) {
                        cat = categoryDAO.findByName(info.getCategory());
                        if (cat == null) {
                            cat = categoryDAO.create(info.getCategory());
                        }
                        cats.put(info.getCategory(), cat);
                    }

                    int col = 1;
                    Integer rawid = (Integer) tmplIdGenerator
                        .generate((SessionImpl) session, new MeasurementTemplate());

                    stmt = conn.prepareStatement(templatesql);
                    stmt.setInt(col++, rawid.intValue());
                    stmt.setString(col++, info.getName());
                    stmt.setString(col++, info.getAlias());
                    stmt.setString(col++, info.getUnits());
                    stmt.setInt(col++, info.getCollectionType());
                    stmt.setBoolean(col++, info.isDefaultOn());
                    stmt.setLong(col++, info.getInterval());
                    stmt.setBoolean(col++, info.isIndicator());
                    stmt.setInt(col++, monitorableType.getId().intValue());
                    stmt.setInt(col++, cat.getId().intValue());
                    stmt.setString(col++, info.getTemplate());
                    stmt.setString(col++, pluginName);
                    stmt.setLong(col++, current);
                    stmt.setLong(col, current);
                    stmt.execute();
                    stmt.close();
                }
            }
        } catch (SQLException e) {
            this.log.error("Unable to add measurements for: " + pluginName, e);
        } finally {
            // Util.endConnection();
        }
    }

    /** 
     */
    public void setDesignated(MeasurementTemplate tmpl, boolean designated) {
        if (tmpl.isAvailability()) {
            return;
        }
        tmpl.setDesignate(designated);
    }

    /**
     * Set the measurement templates to be "designated" for a monitorable type.
     */
    public void setDesignatedTemplates(String mType, Integer[] desigIds) {
        List<MeasurementTemplate> derivedTemplates = measurementTemplateDAO.findDerivedByMonitorableType(mType);

        HashSet<Integer> designates = new HashSet<Integer>();
        designates.addAll(Arrays.asList(desigIds));

        for (MeasurementTemplate template : derivedTemplates) {
            // Never turn off Availability as an indicator
            if (template.isAvailability()) {
                continue;
            }

            boolean designated = designates.contains(template.getId());

            if (designated != template.isDesignate()) {
                template.setDesignate(designated);
            }
        }
    }
}
