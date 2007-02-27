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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.Query;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.dao.HibernateDAO;

public class RawMeasurementDAO extends HibernateDAO {
    public RawMeasurementDAO(DAOFactory f) {
        super(RawMeasurement.class, f);
    }

    public RawMeasurement findById(Integer id) {
        return (RawMeasurement)super.findById(id);
    }

    void remove(Integer id) {
        RawMeasurement m = findById(id);
        remove(m);
    }
    
    void remove(RawMeasurement entity) {
        MeasurementStartupListener
            .getDeleteMetricCallback() 
            .beforeMetricsDeleted(Collections.singleton(entity));
        super.remove(entity);
    }

    RawMeasurement create(Integer instanceId, MeasurementTemplate mt,
                          String dsn) {
        RawMeasurement rm = new RawMeasurement(instanceId, mt, dsn);
        save(rm);
        return rm;
    }

    List findByInstance(int appdefType, int appdefId) {
        String sql =
            "select distinct m from RawMeasurement m " +
            "join m.template as t " +
            "join t.monitorableType as mt " +
            "where mt.appdefType = ? and m.instanceId = ? " +
            "and t.measurementArgs is empty";

        return getSession().createQuery(sql)
            .setInteger(0, appdefType)
            .setInteger(1, appdefId).list();
    }

    int deleteByInstances(AppdefEntityID[] ids)
    {
        Map map = AppdefUtil.groupByAppdefType(ids);
        StringBuffer sql = new StringBuffer()
            .append("from RawMeasurement where ");
        for (int i = 0; i < map.size(); i++) {
            if (i > 0) {
                sql.append(" or ");
            }
            sql.append(" id in " +
                       "(select m.id from RawMeasurement m " +
                       "join m.template as t " +
                       "join t.monitorableType as mt where ")
                .append("mt.appdefType=")
                .append(":appdefType"+i)
                .append(" and ")
                .append("m.instanceId in (:list" + i + ")")
                .append(") ");
        }
        int j = 0;
        Query q = getSession().createQuery(sql.toString());
        for (Iterator i = map.keySet().iterator(); i.hasNext(); j++) {
            Integer appdefType = (Integer)i.next();
            List list = (List)map.get(appdefType);
            q.setInteger("appdefType"+j, appdefType.intValue())
                .setParameterList("list"+j, list);
        }
        
        List v = q.list();
        MeasurementStartupListener
            .getDeleteMetricCallback()
            .beforeMetricsDeleted(v);
        for (Iterator i=v.iterator(); i.hasNext(); ) {
            super.remove(i.next());
        }
        return v.size();
    }

    RawMeasurement findByDsnForInstance(String dsn, Integer id) {
        String sql =
            "from RawMeasurement m " +
            "where m.dsn = ? and m.instanceId = ?";
        
        return (RawMeasurement)getSession().createQuery(sql)
            .setString(0, dsn)
            .setInteger(1, id.intValue()).uniqueResult();
    }

    RawMeasurement findByTemplateForInstance(Integer tid, Integer instanceId) {
        String sql =
            "select m from RawMeasurement m " +
            "join fetch m.template as t " +
            "where t.id = ? and m.instanceId = ?";

        return (RawMeasurement)getSession().createQuery(sql)
            .setInteger(0, tid.intValue())
            .setInteger(1, instanceId.intValue())
            .setCacheable(true)
            .setCacheRegion("RawMeasurement.findByTemplateForInstance")
            .uniqueResult();
    }

    List findByTemplate(Integer id) {
        String sql =
            "select m from RawMeasurement m " +
            "join fetch m.template as t " +
            "where t.id = ?";

        return getSession().createQuery(sql)
            .setInteger(0, id.intValue()).list();
    }

    List findByDerivedMeasurement(Integer did) {
        String sql =
            "select distinct r from RawMeasurement r, " +
            "DerivedMeasurement m " +
            "join m.template t " +
            "join t.measurementArgs a " +
            "where r.instanceId = m.instanceId and " +
            "a.templateArg.id = r.template.id and " +
            "m.id = ? ";

        return getSession().createQuery(sql)
                .setInteger(0, did.intValue()).list();
    }
}
