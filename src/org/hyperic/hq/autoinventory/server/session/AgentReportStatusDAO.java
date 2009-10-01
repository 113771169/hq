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

package org.hyperic.hq.autoinventory.server.session;

import java.util.Collection;

import org.hibernate.SessionFactory;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.dao.HibernateDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
@Repository
public class AgentReportStatusDAO extends HibernateDAO {
    @Autowired
    public AgentReportStatusDAO(SessionFactory f) {
        super(AgentReportStatus.class, f);
    }

    public void remove(AgentReportStatus status) {
        super.remove(status);
    }

    void save(AgentReportStatus status) {
        super.save(status);
    }

    public AgentReportStatus getReportStatus(Agent a) {
        String sql = "from AgentReportStatus where agent = :agent";
        return (AgentReportStatus)getSession().createQuery(sql)
            .setParameter("agent", a)
            .uniqueResult();
    }

    /**
     * Get or create a report status object for an associated agent.
     */
    AgentReportStatus getOrCreate(Agent a) {
        AgentReportStatus res = getReportStatus(a);
        if (res == null) {
            res = new AgentReportStatus();
            res.setAgent(a);
            save(res);
        }
        return res;
    }

    /**
     * Find a collection of {@link AgentReportStatus} where the services
     * have not been totally processed.
     */
    Collection findDirtyStatus() {
        String sql="from AgentReportStatus where serviceDirty = true";

        return getSession().createQuery(sql).list();
    }
}
