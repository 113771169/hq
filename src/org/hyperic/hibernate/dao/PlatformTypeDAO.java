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
import org.hyperic.hq.appdef.server.session.PlatformType;
import org.hyperic.hq.appdef.shared.PlatformTypeValue;

import java.util.Collection;

public class PlatformTypeDAO extends HibernateDAO
{
    public PlatformTypeDAO(Session session)
    {
        super(PlatformType.class, session);
    }

    public PlatformType findById(Integer id)
    {
        return (PlatformType)super.findById(id);
    }

    public void evict(PlatformType entity)
    {
        super.evict(entity);
    }

    public void save(PlatformType entity)
    {
        super.save(entity);
    }

    public void remove(PlatformType entity) {
        super.remove(entity);
    }

    public PlatformType create(PlatformTypeValue pvalue) {
        PlatformType pt = new PlatformType(pvalue);
        save(pt);
        return pt;
    }
    
    public PlatformType findByName(String name)
    {
        String sql = "from PlatformType where name=?";
        return (PlatformType)getSession().createQuery(sql)
            .setString(0, name)
            .uniqueResult();
    }

    public Collection findByPlugin(String plugin)
    {
        String sql = "from PlatformType where plugin=?";
        return getSession().createQuery(sql)
            .setString(0, plugin)
            .list();
    }
}
