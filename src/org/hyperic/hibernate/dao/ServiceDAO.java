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
import org.hyperic.hq.appdef.ConfigResponseDB;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.appdef.server.session.ServiceType;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.dao.DAOFactory;

import java.util.Collection;
import java.util.List;

/**
 * CRUD methods, finders, etc. for Service
 */
public class ServiceDAO extends HibernateDAO
{
    public ServiceDAO(Session session)
    {
        super(Service.class, session);
    }

    public Service findById(Integer id)
    {
        return (Service)super.findById(id);
    }

    public void evict(Service entity)
    {
        super.evict(entity);
    }

    public Service merge(Service entity)
    {
        return (Service)super.merge(entity);
    }

    public void save(Service entity)
    {
        super.save(entity);
    }

    public void remove(Service entity)
    {
        super.remove(entity);
    }

    public Service create(ServiceValue sv, Server parent) {
        ConfigResponseDB configResponse =
            DAOFactory.getDAOFactory().getConfigResponseDAO().create();
        
        Service s = new Service();
        s.setName(sv.getName());
        s.setAutodiscoveryZombie(false);
        s.setServiceRt(false);
        s.setEndUserRt(false);
        s.setDescription(sv.getDescription());
        s.setModifiedBy(sv.getModifiedBy());
        s.setLocation(sv.getLocation());
        s.setOwner(sv.getOwner());
        s.setParentId(sv.getParentId());

        if (sv.getServiceType() != null) {
            Integer stId = sv.getServiceType().getId();
            ServiceType st = 
                DAOFactory.getDAOFactory().getServiceTypeDAO().findById(stId);
            s.setServiceType(st);
        }

        s.setServer(parent);
        s.setConfigResponse(configResponse);
        save(s);
        return s;
    }

    public Service createService(Server s, ServiceValue sv)
        throws ValidationException
    {
        // validate the service
        s.validateNewService(sv);
        // get the Service home
        return create(sv, s);
    }

    public Collection findByParent(Integer parentId)
    {
        String sql="from Service where parentService.id=?";
        return getSession().createQuery(sql)
            .setInteger(0, parentId.intValue())
            .list();
    }

    public Collection findByParentAndType(Integer parentId, Integer typeId)
    {
        String sql="from Service where parentService.id=? and serviceType.id=?";
        return getSession().createQuery(sql)
            .setInteger(0, parentId.intValue())
            .setInteger(1, typeId.intValue())
            .list();
    }

    /**
     * legacy EJB finder
     * @deprecated use finaAll_orderName(boolean)
     * @return
     */
    public Collection findAll_orderName_asc()
    {
        return findAll_orderName(true);
    }

    /**
     * legacy EJB finder
     * @deprecated use finaAll_orderName(boolean)
     * @return
     */
    public Collection findAll_orderName_desc()
    {
        return findAll_orderName(false);
    }

    public Collection findAll_orderName(boolean asc)
    {
        return getSession()
            .createQuery("from Service order by sortName " +
                         (asc ? "asc" : "desc"))
            .list();
    }

    /**
     * legacy EJB finder
     * @deprecated use finaAll_orderCtime(boolean)
     * @return
     */
    public Collection findAll_orderCtime_asc()
    {
        return findAll_orderCtime(true);
    }

    /**
     * legacy EJB finder
     * @deprecated use finaAll_orderCtime(boolean)
     * @return
     */
    public Collection findAll_orderCtime_desc()
    {
        return findAll_orderCtime(false);
    }

    public Collection findAll_orderCtime(boolean asc)
    {
        return getSession()
            .createQuery("from Service order by creationTime " +
                         (asc ? "asc" : "desc"))
            .list();
    }

    public Collection findByType(Integer st)
    {
        String sql="from Service where serviceType.id=?";
        return getSession().createQuery(sql)
            .setInteger(0, st.intValue())
            .list();
    }

    public List findByName(String name)
    {
        String sql="from Service where sortName=?";
        return getSession().createQuery(sql)
            .setString(0, name.toUpperCase())
            .list();
    }

    /**
     * legacy EJB finder
     * @deprecated use finaByPlatform_orderName(boolean)
     * @return
     */
    public Collection findByPlatform_orderName_asc(Integer id)
    {
        return findByPlatform_orderName(id, true);
    }

    /**
     * legacy EJB finder
     * @deprecated use finaByPlatform_orderName(boolean)
     * @return
     */
    public Collection findByPlatform_orderName_desc(Integer id)
    {
        return findByPlatform_orderName(id, false);
    }

    public Collection findByPlatform_orderName(Integer id, boolean asc)
    {
        String sql="select sv from Service sv " +
                   " join fetch sv.server s " +
                   " join fetch s.platform p "+
                   "where p.id=?" +
                   "order by s.sortName " +
                   (asc ? "asc" : "desc");
        return getSession().createQuery(sql)
            .setInteger(0, id.intValue())
            .list();
    }

    /**
     * legacy EJB finder
     * @deprecated use finaByPlatform_orderType(boolean)
     * @return
     */
    public Collection findByPlatform_orderType_asc(Integer id)
    {
        return findByPlatform_orderType(id, true);
    }

    /**
     * legacy EJB finder
     * @deprecated use finaByPlatform_orderType(boolean)
     * @return
     */
    public Collection findByPlatform_orderType_desc(Integer id)
    {
        return findByPlatform_orderType(id, false);
    }

    public Collection findByPlatform_orderType(Integer id, boolean asc)
    {
        String sql="select sv from Service sv " +
                   " join fetch sv.server s " +
                   " join fetch s.serverType st " +
                   " join fetch s.platform p "+
                   "where p.id=?" +
                   "order by st.sortName "+
                   (asc ? "asc" : "desc") +
                   ", s.sortName";
        return getSession().createQuery(sql)
            .setInteger(0, id.intValue())
            .list();
    }

    /**
     * legacy EJB finder
     * @deprecated use finaPlatformServices_orderName(boolean)
     * @return
     */
    public Collection findPlatformServices_orderName(Integer platId, boolean b)
    {
        return findPlatformServices_orderName(platId, b, true);
    }

    /**
     * legacy EJB finder
     * @deprecated use finaPlatformServices_orderName(boolean)
     * @return
     */
    public Collection findPlatformServices_orderName_desc(Integer platId,
                                                          boolean b)
    {
        return findPlatformServices_orderName(platId, b, false);
    }

    public Collection findPlatformServices_orderName(Integer platId,
                                                      boolean b,
                                                      boolean asc)
    {
        String sql="select sv from Service sv " +
                   " join fetch sv.server s " +
                   " join fetch s.serverType st " +
                   " join fetch s.platform p " +
                   "where p.id=? " +
                   " and st.virtual=? " +
                   "order by sv.sortName " +
                   (asc ? "asc" : "desc");
        return getSession().createQuery(sql)
            .setInteger(0, platId.intValue())
            .setBoolean(1, b)
            .list();
    }

    public List findByServer_orderName(Integer id)
    {
        String sql="from Service where server.id=? order by sortName";
        return getSession().createQuery(sql)
            .setInteger(0, id.intValue())
            .list();
    }

    public List findByServer_orderType(Integer id)
    {
        String sql="select s from Service s " +
                   " join fetch s.serviceType st " +
                   "where s.server.id=? " +
                   "order by st.sortName";
        return getSession().createQuery(sql)
            .setInteger(0, id.intValue())
            .list();
    }

    public List findByServerAndType_orderName(Integer id, Integer tid)
    {
        String sql="from Service where server.id=? and serviceType.id=? " +
                   "order by sortName";
        return getSession().createQuery(sql)
            .setInteger(0, id.intValue())
            .setInteger(1, tid.intValue())
            .list();
    }

    public Service findByApplication(Integer appId)
    {
        String sql="select s from Service s " +
                   " join fetch s.appServices a " +
                   "where a.application.id=? ";
        return (Service)getSession().createQuery(sql)
            .setInteger(0, appId.intValue())
            .uniqueResult();
    }

    public Collection findByCluster(Integer clusterId)
    {
        String sql="select s from Service s " +
                   " join fetch s.serviceCluster c " +
                   "where c.id=?";
        return getSession().createQuery(sql)
            .setInteger(0, clusterId.intValue())
            .list();
    }

    /**
     * legacy EJB finder
     * @deprecated use findAllClusterUnassigned_orderName(boolean)
     * @return
     */
    public Collection findAllClusterUnassigned_orderName_asc()
    {
        return findAllClusterUnassigned_orderName(true);
    }

    /**
     * legacy EJB finder
     * @deprecated use findAllClusterUnassigned_orderName(boolean)
     * @return
     */
    public Collection findAllClusterUnassigned_orderName_desc()
    {
        return findAllClusterUnassigned_orderName(false);
    }

    public Collection findAllClusterUnassigned_orderName(boolean asc)
    {
        String sql="from Service where serviceCluster is null " +
                   "order by sortName " +
                   (asc ? "asc" : "desc");
        return getSession().createQuery(sql).list();
    }

    /**
     * legacy EJB finder
     * @deprecated use findAllClusterUnassigned_orderName(boolean)
     * @return
     */
    public Collection findAllClusterAppUnassigned_orderName_asc()
    {
        return findAllClusterAppUnassigned_orderName(true);
    }

    /**
     * legacy EJB finder
     * @deprecated use findAllClusterUnassigned_orderName(boolean)
     * @return
     */
    public Collection findAllClusterAppUnassigned_orderName_desc()
    {
        return findAllClusterAppUnassigned_orderName(false);
    }

    public Collection findAllClusterAppUnassigned_orderName(boolean asc)
    {
        String sql="from Service where serviceCluster is null and " +
                   "appServices.size=0 " +
                   "order by sortName " +
                   (asc ? "asc" : "desc");
        return getSession().createQuery(sql).list();
    }
}
