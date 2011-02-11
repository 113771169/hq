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

import java.util.Collection;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.dao.HibernateDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleRevNumDAO
    extends HibernateDAO<ScheduleRevNum> {

    @Autowired
    public ScheduleRevNumDAO(SessionFactory f) {
        super(ScheduleRevNum.class, f);
    }

    public void remove(SrnId id) {
        ScheduleRevNum srn = findById(id);
        if (srn != null) {
            remove(srn);
        }
    }

    public ScheduleRevNum create(int entType, int entId) {

        SrnId srnId = new SrnId(entType, entId);
        ScheduleRevNum srn = new ScheduleRevNum(srnId, 1);
        save(srn);
        return srn;
    }

    /**
     * Get the minimum collection intervals for all entities with metrics
     * enabled.
     * @return A Collection of Object arrays with 2 entries, the Resource and the Long collection interval.
     */
    public Collection<Object[]> getMinIntervals() {
        String sql = "select m.resource, min(m.interval) "
                     + "from Measurement m, " +  "MeasurementTemplate t "
                     + "where m.enabled = true and " + "m.template.id = t.id "
                     + "group by m.instanceId";
        return getSession().createQuery(sql).list();
    }

    /**
     * @param id The appdef entity to look up.
     * @return The minimum collection interval for the given entity.
     */
    public Long getMinInterval(AppdefEntityID id) {
        String sql = "select min(m.interval) " + "from Measurement m, "
                     + "MeasurementTemplate t " + "where m.enabled = true and "
                     + "m.instanceId = ? and " + "m.template.id = t.id  "
                     + "group by m.instanceId";

        return (Long) getSession().createQuery(sql).setInteger(0, id.getID()).setInteger(1,
            id.getType()).uniqueResult();
    }

    /**
     * Find the minimum collection interval for the given entity, potentially
     * allowing for the query to return a stale value (for efficiency reasons).
     * 
     * @param id The appdef entity to look up.
     * @param allowStale <code>true</code> to allow the query to return a stale
     *        value; <code>false</code> to never allow a stale value,
     *        potentially always forcing a sync with the database.
     * @return The minimum collection interval for the given entity.
     */
    public Long getMinInterval(AppdefEntityID id, boolean allowStale) {
        Session session = this.getSession();
        FlushMode oldFlushMode = session.getFlushMode();

        try {
            if (allowStale) {
                session.setFlushMode(FlushMode.MANUAL);
            }

            return getMinInterval(id);
        } finally {
            session.setFlushMode(oldFlushMode);
        }
    }
}
