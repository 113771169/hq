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

package org.hyperic.hibernate.dao;

import org.hibernate.Session;
import org.hyperic.hq.auth.shared.PrincipalsPK;
import org.hyperic.hq.auth.Principal;
import org.jboss.security.Util;

import java.util.Collection;

/**
 *
 */
public class PrincipalDAO extends HibernateDAO
{
    public PrincipalDAO(Session session)
    {
        super(Principal.class, session);
    }

    public Principal findById(Integer id)
    {
        return (Principal)super.findById(id);
    }

    public void evict(Principal entity)
    {
        super.evict(entity);
    }

    public Principal merge(Principal entity)
    {
        return (Principal)super.merge(entity);
    }

    public void save(Principal entity)
    {
        super.save(entity);
    }

    public void remove(Principal entity)
    {
        super.remove(entity);
    }

    public Principal create(String principal, String password)
    {
        // All passwords are stored encrypted
        String passwordHash = Util.createPasswordHash("MD5", "base64",
                                                      null, null, password);
        Principal p = new Principal();

        p.setPrincipal(principal);
        p.setPassword(passwordHash);
        save(p);
        return p;
    }

    public Principal findByUsername(String s)
    {
        String sql = "from Principals where principal=?";
        return (Principal)getSession().createQuery(sql)
            .setString(0, s)
            .uniqueResult();
    }

    public Collection findAllUsers()
    {
        return findAll();
    }

    /**
     * @deprecated use findById()
     * @param pk
     * @return
     */
    public Principal findByPrimaryKey(PrincipalsPK pk)
    {
        return findById(pk.getId());
    }
}
