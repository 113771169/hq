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

package org.hyperic.hq.appdef;

import org.hyperic.hq.appdef.shared.AppSvcDependencyLocal;
import org.hyperic.hq.appdef.shared.ApplicationPK;
import org.hyperic.hq.appdef.shared.ServicePK;
import org.hyperic.hq.appdef.shared.ServiceClusterPK;
import org.hyperic.hq.appdef.shared.ServiceLightValue;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.ServerLocal;
import org.hyperic.hq.appdef.shared.ServiceClusterLocal;
import org.hyperic.hq.appdef.shared.ServiceTypeLocal;
import org.hyperic.hq.appdef.shared.ServiceClusterLocalHome;
import org.hyperic.hq.appdef.shared.ServiceClusterUtil;
import org.hyperic.hq.appdef.shared.AppServiceLocal;
import org.hyperic.hq.appdef.shared.AppServiceUtil;
import org.hyperic.hq.appdef.shared.AppSvcDependencyUtil;
import org.hyperic.hibernate.dao.ServiceTypeDAO;
import org.hyperic.dao.DAOFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.ObjectNotFoundException;

import javax.naming.NamingException;
import javax.ejb.FinderException;
import javax.ejb.CreateException;
import java.util.Collection;

/**
 *
 */
public class Service extends AppdefResource
{
    private static final Log log = LogFactory.getLog(Service.class);

    private boolean autodiscoveryZombie;
    private boolean serviceRt;
    private boolean endUserRt;
    private Service parentService;
    private Server server;
    private ServiceType serviceType;
    private ServiceCluster serviceCluster;
    private ConfigResponseDB configResponse;
    private Collection appServices;

    /**
     * default constructor
     */
    public Service()
    {
        super();
    }

    public boolean isAutodiscoveryZombie()
    {
        return this.autodiscoveryZombie;
    }

    /**
     * legacy EJB getter
     * @deprecated use isAutodiscoveryZombie() instead
     * @return
     */
    public boolean getAutodiscoveryZombie()
    {
        return isAutodiscoveryZombie();
    }

    public void setAutodiscoveryZombie(boolean autodiscoveryZombie)
    {
        this.autodiscoveryZombie = autodiscoveryZombie;
    }

    public boolean isServiceRt()
    {
        return this.serviceRt;
    }

    /**
     * legacy EJB getter
     * @deprecated use isServiceRt() instead
     * @return
     */
    public boolean getServiceRt()
    {
        return isServiceRt();
    }

    public void setServiceRt(boolean serviceRt)
    {
        this.serviceRt = serviceRt;
    }

    public boolean isEndUserRt()
    {
        return this.endUserRt;
    }

    /**
     * legacy EJB getter
     * @deprecated use isEndUserRt() instead
     * @return
     */
    public boolean getEndUserRt()
    {
        return isEndUserRt();
    }

    public void setEndUserRt(boolean endUserRt)
    {
        this.endUserRt = endUserRt;
    }

    public Service getParentService()
    {
        return this.parentService;
    }

    /**
     * legacy EJB getter for parent service id
     * @deprecated use getParentService().getId() instead
     * @return
     */
    public Integer getParentId()
    {
        return parentService != null ? parentService.getId() : null;
    }

    public void setParentService(Service parentService)
    {
        this.parentService = parentService;
    }

    /**
     * legacy EJB getter for configresponse id
     * @deprecated use setParentService() instead
     * @return
     */
    public void setParentId(Integer parentId)
    {
        if (parentId != null) {
            Service s = new Service();
            s.setId(parentId);
            setParentService(s);
        }
    }

    public Server getServer()
    {
        return this.server;
    }

    public void setServer(Server server)
    {
        this.server = server;
    }

    public ServiceType getServiceType()
    {
        return this.serviceType;
    }

    public void setServiceType(ServiceType serviceType)
    {
        this.serviceType = serviceType;
    }

    public ServiceCluster getServiceCluster()
    {
        return this.serviceCluster;
    }

    public void setServiceCluster(ServiceCluster serviceCluster)
    {
        this.serviceCluster = serviceCluster;
    }

    public ConfigResponseDB getConfigResponse()
    {
        return this.configResponse;
    }

    /**
     * legacy EJB getter for configresponse id
     * @deprecated use getConfigResponse().getId() instead
     * @return
     */
    public Integer getConfigResponseId()
    {
        return configResponse != null ? configResponse.getId() : null;
    }

    public void setConfigResponse(ConfigResponseDB configResponse)
    {
        this.configResponse = configResponse;
    }

    /**
     * legacy EJB getter for configresponse id
     * @deprecated use setConfigResponse() instead
     * @return
     */
    public void setConfigResponseId(Integer crif)
    {
        if (crif != null) {
            ConfigResponseDB c = new ConfigResponseDB();
            c.setId(crif);
            setConfigResponse(c);
        }
    }

    public Collection getAppServices()
    {
        return this.appServices;
    }

    public void setAppServices(Collection appServices)
    {
        this.appServices = appServices;
    }

    public AppSvcDependencyLocal addDependentService(ApplicationPK appPK,
                                                     ServicePK depPK)
        throws NamingException, CreateException
    {
        // first we see if we can find an existing AppService object
        // if we cant, we add it
        AppServiceLocal appSvc = null;
        AppServiceLocal depSvc = null;
        // look for the app service for **this** Service
        try {
            appSvc = AppServiceUtil.getLocalHome()
                .findByAppAndService(appPK.getId(), this.getId());
        } catch (FinderException e) {
            // didnt find it... create it.
            log.debug(
                "Creating new app service object for Application: "
                + appPK.getId() + " Service: " + getId());
            appSvc =
                AppServiceUtil.getLocalHome().create(
                    new ServicePK(getId()), appPK, true);
        }
        // try to find the app service for the dependent service
        try {
            depSvc = AppServiceUtil.getLocalHome()
                .findByAppAndService(appPK.getId(), depPK.getId());
        } catch (FinderException e) {
            log.debug(
                "Creating new dependent app service object for Application: "
                + appPK.getId() + " Service: " + getId());
            // dependent services are not allowed to be entry points
            // at least not here ;)
            depSvc =
                AppServiceUtil.getLocalHome().create(depPK, appPK, false);
        }
        // now we add the dependency
        AppSvcDependencyLocal  appDep =
            AppSvcDependencyUtil.getLocalHome().create(appSvc, depSvc);
        return appDep;
    }

    public AppSvcDependencyLocal addDependentServiceCluster(ApplicationPK appPK,
                                                            ServiceClusterPK depPK)
        throws NamingException, CreateException
    {
        // first we see if we can find an existing AppService object
        // if we cant, we add it
        AppServiceLocal appSvc = null;
        AppServiceLocal depSvc = null;
        // look for the app service for **this** Service
        try {
            appSvc = AppServiceUtil.getLocalHome()
                .findByAppAndService(appPK.getId(), this.getId());
        } catch (FinderException e) {
            // didnt find it... create it.
            log.debug(
                "Creating new app service object for Application: "
                + appPK.getId() + " Service: " + getId());
            appSvc =
                AppServiceUtil.getLocalHome().create(
                    new ServicePK(getId()), appPK, true);
        }
        // try to find the app service for the dependent service
        try {
            depSvc = AppServiceUtil.getLocalHome()
                .findByAppAndCluster(appPK.getId(), depPK.getId());
        } catch (FinderException e) {
            log.debug(
                "Creating new dependent app service object for Application: "
                + appPK.getId() + " ServiceCluster: " + getId());
            // dependent services are not allowed to be entry points
            // at least not here ;)
            depSvc =
                AppServiceUtil.getLocalHome().create(depPK, appPK);
        }
        // now we add the dependency
        AppSvcDependencyLocal  appDep =
            AppSvcDependencyUtil.getLocalHome().create(appSvc, depSvc);
        return appDep;
    }

    private ServiceLightValue serviceLightValue = new ServiceLightValue();
    /**
     * legacy EJB DTO pattern
     * @deprecated use (this) Service object instead
     * @return
     */
    public ServiceLightValue getServiceLightValue()
    {
        serviceLightValue.setSortName(getSortName());
        serviceLightValue.setAutodiscoveryZombie(getAutodiscoveryZombie());
        serviceLightValue.setServiceRt(getServiceRt());
        serviceLightValue.setEndUserRt(getEndUserRt());
        serviceLightValue.setModifiedBy(getModifiedBy());
        serviceLightValue.setOwner(getOwner());
        serviceLightValue.setLocation(getLocation());
        serviceLightValue.setConfigResponseId(getConfigResponseId());
        serviceLightValue.setParentId(getParentId());
        serviceLightValue.setName(getName());
        serviceLightValue.setDescription(getDescription());
        serviceLightValue.setId(getId());
        serviceLightValue.setMTime(getMTime());
        serviceLightValue.setCTime(getCTime());
        if ( getServiceType() != null ) {
            // temporarily rely on EJB until
            // it is hibernized
            try {
                ServiceTypeDAO shome =
                    DAOFactory.getDAOFactory().getServiceTypeDAO();
                ServiceType st =
                    shome.findById(getServiceType().getId());
                serviceLightValue.setServiceType(
                    st.getServiceTypeValue());
            } catch (ObjectNotFoundException e) {
            }
        }
        else
            serviceLightValue.setServiceType(null);
        return serviceLightValue;
    }

    private ServiceValue serviceValue = new ServiceValue();
    /**
     * legacy EJB DTO pattern
     * @deprecated use (this) Service object instead
     * @return
     */
    public ServiceValue getServiceValue()
    {
        serviceValue.setSortName(getSortName());
        serviceValue.setAutodiscoveryZombie(getAutodiscoveryZombie());
        serviceValue.setServiceRt(getServiceRt());
        serviceValue.setEndUserRt(getEndUserRt());
        serviceValue.setModifiedBy(getModifiedBy());
        serviceValue.setOwner(getOwner());
        serviceValue.setLocation(getLocation());
        serviceValue.setConfigResponseId(getConfigResponseId());
        serviceValue.setParentId(getParentId());
        serviceValue.setName(getName());
        serviceValue.setDescription(getDescription());
        serviceValue.setId(getId());
        serviceValue.setMTime(getMTime());
        serviceValue.setCTime(getCTime());
        if (getServer() != null) {
            serviceValue.setServer(getServer().getServerLightValue());
        }
        else
            serviceValue.setServer( null );
        if ( getServiceCluster() != null ) {
            // temporarily rely on EJB until
            // it is hibernized
            try {
                ServiceClusterLocalHome shome =
                    ServiceClusterUtil.getLocalHome();
                ServiceClusterLocal sc =
                    shome.findByPrimaryKey(getServiceCluster().getPrimaryKey());
                serviceValue.setServiceCluster(
                    sc.getServiceClusterValue());
            } catch(NamingException ignore) {
            } catch (javax.ejb.FinderException e) {
            }
        }
        else
            serviceValue.setServiceCluster( null );
        if ( getServiceType() != null ) {
            // temporarily rely on EJB until
            // it is hibernized
            try {
                ServiceTypeDAO shome =
                    DAOFactory.getDAOFactory().getServiceTypeDAO();
                ServiceType st =
                    shome.findById(getServiceType().getId());
                serviceValue.setServiceType(st.getServiceTypeValue());
            } catch (ObjectNotFoundException e) {
            }
        }
        else
            serviceValue.setServiceType( null );
        return serviceValue;
    }

    /**
     * legacy EJB DTO pattern.
     * Compare this entity bean to a value object
     * @deprecated should use (this) Service object and hibernate dirty() check
     * @return true if the service value matches this entity
     */
    public boolean matchesValueObject(ServiceValue obj)
    {
        boolean matches = true;
        matches = super.matchesValueObject(obj) &&
            (getName() != null ? getName().equals(obj.getName())
                : (obj.getName() == null)) &&
            (getDescription() != null ?
                getDescription().equals(obj.getDescription())
                : (obj.getDescription() == null)) &&
            (getLocation() != null ?
                getLocation().equals(obj.getLocation())
                : (obj.getLocation() == null)) &&
            (getOwner() != null ? getOwner().equals(obj.getOwner())
                : (obj.getOwner() == null)) &&
            (getEndUserRt() == obj.getEndUserRt()) &&
            (getServiceRt() == obj.getServiceRt());
        return matches;
    }

    public void setServer(ServerLocal server)
    {
        if (server != null) {
            Server s = new Server();
            s.setId(server.getId());
            setServer(s);
        }
    }

    public void setServiceCluster(ServiceClusterLocal serviceCluster)
    {
        if (serviceCluster != null) {
            ServiceCluster s = new ServiceCluster();
            s.setId(serviceCluster.getId());
            setServiceCluster(s);
        }
    }

    public void setServiceType(ServiceTypeLocal serviceType)
    {
        if (serviceType != null) {
            ServiceType s = new ServiceType();
            s.setId(serviceType.getId());
            setServiceType(s);
        }
    }

    /**
     * legacy EJB DTO pattern for copying attribute values from value
     * object.
     *
     * Set the value object. This method does *NOT* update any of the CMR's
     * included in the value object. This is for speed/locking reasons
     */
    public void updateService(ServiceValue valueHolder)
    {
        setDescription( valueHolder.getDescription() );
        setAutodiscoveryZombie( valueHolder.getAutodiscoveryZombie() );
        setServiceRt( valueHolder.getServiceRt() );
        setEndUserRt( valueHolder.getEndUserRt() );
        setModifiedBy( valueHolder.getModifiedBy() );
        setOwner( valueHolder.getOwner() );
        setLocation( valueHolder.getLocation() );
        setParentId( valueHolder.getParentId() );
        setName( valueHolder.getName() );
        setModifiedTime( valueHolder.getMTime() );
        setCreationTime( valueHolder.getCTime() );
    }

    private ServicePK pkey = new ServicePK();
    /**
     * legacy EJB primary key getter
     * @deprecated use getId() instead
     * @return
     */
    public ServicePK getPrimaryKey()
    {
        pkey.setId(getId());
        return pkey;
    }

    public boolean equals(Object obj)
    {
        if (!super.equals(obj) || !(obj instanceof Service)) {
            return false;
        }
        Service o = (Service)obj;
        return
            (autodiscoveryZombie==o.isAutodiscoveryZombie())
            &&
            (serviceRt==o.isServiceRt())
            &&
            (endUserRt==o.isEndUserRt());
    }

    public int hashCode()
    {
        int result=super.hashCode();

        result = 37*result + (autodiscoveryZombie ? 0 : 1);
        result = 37*result + (serviceRt ? 0 : 1);
        result = 37*result + (endUserRt ? 0 : 1);
        
        return result;
    }
}
