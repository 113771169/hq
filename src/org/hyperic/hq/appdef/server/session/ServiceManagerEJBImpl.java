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

package org.hyperic.hq.appdef.server.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.shared.AppSvcClustDuplicateAssignException;
import org.hyperic.hq.appdef.shared.AppSvcClustIncompatSvcException;
import org.hyperic.hq.appdef.shared.AppdefDuplicateNameException;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefGroupValue;
import org.hyperic.hq.appdef.shared.ApplicationNotFoundException;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;
import org.hyperic.hq.appdef.shared.PlatformNotFoundException;
import org.hyperic.hq.appdef.shared.ServerNotFoundException;
import org.hyperic.hq.appdef.shared.ServerTypeValue;
import org.hyperic.hq.appdef.shared.ServiceClusterValue;
import org.hyperic.hq.appdef.shared.ServiceLightValue;
import org.hyperic.hq.appdef.shared.ServiceNotFoundException;
import org.hyperic.hq.appdef.shared.ServiceTypeValue;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.appdef.shared.ServiceManagerLocal;
import org.hyperic.hq.appdef.shared.ServiceManagerUtil;
import org.hyperic.hq.appdef.AppService;
import org.hyperic.hq.appdef.ServiceCluster;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.grouping.server.session.GroupUtil;
import org.hyperic.hq.grouping.shared.GroupNotCompatibleException;
import org.hyperic.hq.product.ServiceTypeInfo;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;
import org.hyperic.hq.dao.ServiceDAO;
import org.hyperic.hq.dao.ServerTypeDAO;
import org.hyperic.hq.dao.AppServiceDAO;
import org.hyperic.hq.dao.ApplicationDAO;
import org.hyperic.hq.dao.ConfigResponseDAO;
import org.hyperic.hq.dao.ServiceClusterDAO;
import org.hyperic.dao.DAOFactory;
import org.hibernate.ObjectNotFoundException;
import org.hyperic.hq.appdef.server.session.Server;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.appdef.server.session.ServerType;
import org.hyperic.hq.appdef.server.session.ServiceType;
import org.hyperic.hq.zevents.ZeventManager;

/**
 * This class is responsible for managing Server objects in appdef
 * and their relationships
 * @ejb:bean name="ServiceManager"
 *      jndi-name="ejb/appdef/ServiceManager"
 *      local-jndi-name="LocalServiceManager"
 *      view-type="local"
 *      type="Stateless"
 * @ejb:util generate="physical"
 * @ejb:transaction type="REQUIRED"
 */
public class ServiceManagerEJBImpl extends AppdefSessionEJB
    implements SessionBean {

    private Log log = LogFactory.getLog(ServiceManagerEJBImpl.class);

    private final String VALUE_PROCESSOR
        = "org.hyperic.hq.appdef.server.session.PagerProcessor_service";
    private Pager valuePager = null;
    private final Integer APPDEF_RES_TYPE_UNDEFINED = new Integer(-1);

    /**
     * Create a Service which runs on a given server
     * @return ServiceValue - the saved value object
     * @exception CreateException - if it fails to add the service
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Integer createService(AuthzSubjectValue subject,
                                 Integer serverId, Integer serviceTypeId,
                                 ServiceValue sValue)
        throws CreateException, ValidationException, PermissionException,
               ServerNotFoundException, AppdefDuplicateNameException
    {
        try {
            validateNewService(sValue);
            trimStrings(sValue);

            Server server = getServerMgrLocal().findServerById(serverId);
            ServiceType serviceType =
                getServiceTypeDAO().findById(serviceTypeId);
            sValue.setServiceType(serviceType.getServiceTypeValue());
            sValue.setOwner(subject.getName());
            sValue.setModifiedBy(subject.getName());
            Service service = getServiceDAO().createService(server, sValue);
            
            try {
                if (server.getServerType().getVirtual()) {
                    // Look for the platform authorization
                    createAuthzService(sValue.getName(), 
                                       service.getId(),
                                       server.getPlatform().getId(),
                                       false, 
                                       subject);
                } else {
                    // now add the authz resource
                    createAuthzService(sValue.getName(), 
                                       service.getId(),
                                       serverId,
                                       true, 
                                       subject);
                }
            } catch (CreateException e) {
                throw e;
            }

            // Add Service to parent collection
            Collection services = server.getServices();
            if (!services.contains(service)) {
                services.add(service);
            }

            // Send resource create event
            ResourceCreatedZevent zevent =
                new ResourceCreatedZevent(subject, service.getEntityId());
            ZeventManager.getInstance().enqueueEventAfterCommit(zevent);

            return service.getId();
        } catch (FinderException e) {
            log.error("Unable to find ServiceType", e);
            throw new CreateException("Unable to find ServiceType: " +
                                      serviceTypeId + " : " + e.getMessage());
        } catch (PermissionException e) {
            // make sure that if there is a permission exception during
            // service creation, rollback the whole service creation process;
            // otherwise, there would be a EAM_SERVICE record without its
            // cooresponding EAM_RESOURCE record
            log.error("User: " + subject.getName() +
                      " can not add services to server: " + serverId);
            throw e;
        }
    }

    /**
     * Create the Authz service resource
     * @param serviceName 
     * @param serviceId 
     * @param subject - the user creating
     */
    private void createAuthzService(String serviceName,
                                    Integer serviceId,
                                    Integer parentId, boolean isServer, AuthzSubjectValue subject)
        throws CreateException, FinderException, PermissionException {
        log.debug("Begin Authz CreateService");
        // check to see that the user has permission to addServices
        if (isServer) {
            // to the server in question
            checkPermission(subject, getServerResourceType(), parentId,
                            AuthzConstants.serverOpAddService);
        }
        else {
            // to the platform in question
            checkPermission(subject, getPlatformResourceType(), parentId,
                            AuthzConstants.platformOpAddServer);
        }

        createAuthzResource(subject, getServiceResourceType(),
                            serviceId, 
                            serviceName);
        
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceValue[] findServicesByName(AuthzSubjectValue subject,
                                             String name)
        throws ServiceNotFoundException, PermissionException
    {
        List serviceLocals = getServiceDAO().findByName(name);

        int numServices = serviceLocals.size();

        List services = new ArrayList();
        for (int i = 0; i < numServices; i++) {
            Service sLocal = (Service)serviceLocals.get(i);
            ServiceValue sValue = sLocal.getServiceValue();
            try {
                checkViewPermission(subject, sValue.getEntityId());
                services.add(sValue);
            } catch (PermissionException e) {
                //Ok, won't be added to the list
            }
        }
        return (ServiceValue[])services.toArray(new ServiceValue[0]);
    }

    /**
     * Get service IDs by service type.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     *
     * @param subject The subject trying to list service.
     * @param servTypeId service type id.
     * @return An array of service IDs.
     */
    public Integer[] getServiceIds(AuthzSubjectValue subject,
                                  Integer servTypeId)
        throws PermissionException {
        ServiceDAO sLHome;
        try {
            sLHome = getServiceDAO();
            Collection services = sLHome.findByType(servTypeId);
            if (services.size() == 0) {
                return new Integer[0];
            }
            List serviceIds = new ArrayList(services.size());
         
            // now get the list of PKs
            Collection viewable = super.getViewableServices(subject);
            // and iterate over the ejbList to remove any item not in the
            // viewable list
            int i = 0;
            for (Iterator it = services.iterator(); it.hasNext(); i++) {
                Service aEJB = (Service) it.next();
                if (viewable.contains(aEJB.getId())) {
                    // add the item, user can see it
                    serviceIds.add(aEJB.getId());
                }
            }
        
            return (Integer[]) serviceIds.toArray(new Integer[0]);
        } catch (NamingException e) {
            throw new SystemException(e);
        } catch (FinderException e) {
            // There are no viewable servers
            return new Integer[0];
        }
    }

    /**
     * @return List of ServiceValue objects
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList findServicesById(AuthzSubjectValue subject,
                                     Integer[] serviceIds, 
                                     PageControl pc) 
        throws ServiceNotFoundException, PermissionException {
        // TODO paging... Not sure if its even needed.
        PageList serviceList = new PageList();
        for(int i = 0; i < serviceIds.length; i++) {
            serviceList.add(getServiceById(subject, serviceIds[i]));
        }
        serviceList.setTotalSize(serviceIds.length);
        return serviceList;
    }

    /**
     * Create a service type supported by a specific server type
     * @ejb:interface-method
     * @ejb:transaction type="RequiresNew"
     */
    public Integer createServiceType(AuthzSubjectValue subject,
        ServiceTypeValue stv, ServerTypeValue serverType) 
        throws CreateException, ValidationException {
        try {
            if(log.isDebugEnabled()) {
                log.debug("Begin createServiceType: " +  stv);
            }
            validateNewServiceType(stv, serverType);
            // first look up the parent server
            ServerType servType = getServerTypeDAO()
                .findById(serverType.getId());
            // now create the service type on it

            ServiceType stype =
                getServiceTypeDAO().createServiceType(servType, stv);
            return stype.getId();
        } catch (ObjectNotFoundException e) {
            throw new CreateException("Unable to find Parent Server Type: " +
                                      e.getMessage());
        }
    }

    /**
     * Find Service by Id.
     * @ejb:interface-method
     */
    public Service findServiceById(Integer id)
        throws ServiceNotFoundException
    {
        try {
            return getServiceDAO().findById(id);
        } catch (ObjectNotFoundException e) {
            throw new ServiceNotFoundException(id);
        }
    }

    /**
     * Get Service by Id.
     * @ejb:interface-method 
     * @return The Service identified by this id, or null if it does not exist.
     */
    public Service getServiceById(Integer id) {
        return getServiceDAO().get(id);
    }

    /**
     * Find ServiceTypeValue by Id.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceTypeValue findServiceTypeById(Integer id) 
        throws FinderException 
    {
        ServiceType st = getServiceTypeDAO().findById(id);
        return st.getServiceTypeValue();
    }

    /**
     * Find service type by name
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceTypeValue findServiceTypeByName(String name) 
        throws FinderException {

        ServiceType st = getServiceTypeDAO().findByName(name);
        if (st == null) {
            throw new FinderException("service type not found: "+ name);
        }
        
        return st.getServiceTypeValue();
    }

    /**     
     * @return PageList of ServiceTypeValues
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getAllServiceTypes(AuthzSubjectValue subject,
                                       PageControl pc)
    {
        Collection serviceTypes = getServiceTypeDAO().findAll();
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(serviceTypes, pc);
    }

    /**     
     * @return List of ServiceTypeValues
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getViewableServiceTypes(AuthzSubjectValue subject,
                                            PageControl pc)
        throws FinderException, PermissionException {
        // build the server types from the visible list of servers
        Collection services;
        try {
            services = getViewableServices(subject, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        
        Collection serviceTypes = filterResourceTypes(services);
        
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(serviceTypes, pc);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServiceTypesByServerType(AuthzSubjectValue subject,
                                                int serverTypeId) {
        PageControl pc = PageControl.PAGE_ALL;
        Collection serviceTypes =
            getServiceTypeDAO().findByServerType_orderName(serverTypeId, true);
        if (serviceTypes.size() == 0) {
            return new PageList();
        }
        return valuePager.seek(serviceTypes, pc);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList findVirtualServiceTypesByPlatform(AuthzSubjectValue subject,
                                                      Integer platformId) {
        PageControl pc = PageControl.PAGE_ALL;
        Collection serviceTypes = getServiceTypeDAO()
                .findVirtualServiceTypesByPlatform(platformId.intValue());
        if (serviceTypes.size() == 0) {
            return new PageList();
        }
        return valuePager.seek(serviceTypes, pc);
    }

    /** 
     * Get service light value by id.  This does not check for permission.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceLightValue getServiceLightValue(Integer id)
        throws ServiceNotFoundException, PermissionException {

        Service s = getServiceDAO().findById(id);
        return s.getServiceLightValue();
    }

    /** 
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceValue getServiceById(AuthzSubjectValue subject, Integer id)
        throws ServiceNotFoundException, PermissionException {

        Service s = getServiceDAO().findById(id);
        ServiceValue sValue = s.getServiceValue();

        checkViewPermission(subject, sValue.getEntityId());
        return sValue;
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return A List of ServiceValue objects representing all of the
     * services that the given subject is allowed to view.
     */
    public PageList getAllServices(AuthzSubjectValue subject, PageControl pc)
        throws FinderException, PermissionException {
            
        Collection toBePaged = new ArrayList();
        try {
            toBePaged = getViewableServices(subject, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
        
        // valuePager converts local/remote interfaces to value objects
        // as it pages through them.
        return valuePager.seek(toBePaged, pc);
    }

    /**
     * Get the scope of viewable services for a given user
     * @return List of ServiceLocals for which subject has 
     * AuthzConstants.serviceOpViewService
     */
    private Collection getViewableServices(AuthzSubjectValue subject,
                                            PageControl pc)
        throws NamingException, FinderException, 
               PermissionException {
        Collection toBePaged = new ArrayList();
        // get list of pks user can view
        List authzPks = getViewableServices(subject);
        Collection services = null;
        pc = PageControl.initDefaults(pc, SortAttribute.RESOURCE_NAME);
        
        switch( pc.getSortattribute() ) {
            case SortAttribute.RESOURCE_NAME:
                if(pc != null) {
                    services =
                        getServiceDAO().findAll_orderName(!pc.isDescending());
                }
                break;
            case SortAttribute.SERVICE_NAME:
                if(pc != null) {
                    services =
                        getServiceDAO().findAll_orderName(!pc.isDescending());
                }
                break;
            case SortAttribute.CTIME:
                if(pc != null) {
                    services =
                        getServiceDAO().findAll_orderCtime(!pc.isDescending());
                }
                break;
            default:
                services = getServiceDAO().findAll();
                break;
        }
        for(Iterator i = services.iterator(); i.hasNext();) {
            Service aService = (Service)i.next();
            // remove service if its not viewable
            if(authzPks.contains(aService.getId())) {
                toBePaged.add(aService);
            }
        }
        return toBePaged;
    }

    /**
     * Get all cluster unassigned services - services that haven't been assigned 
     * to a service cluster.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return A List of ServiceValue objects representing all of the
     * unassigned services that the given subject is allowed to view.
     */
    public PageList getAllClusterUnassignedServices(AuthzSubjectValue subject, 
        PageControl pc) throws FinderException, PermissionException {
        try {
            // get list of pks user can view
            List authzPks = getViewableServices(subject);
            Collection services = null;
            Collection toBePaged = new ArrayList();
            pc = PageControl.initDefaults(pc, SortAttribute.RESOURCE_NAME);

            switch( pc.getSortattribute() ) {
                case SortAttribute.RESOURCE_NAME:
                    if(pc != null) {
                        services = getServiceDAO()
                            .findAllClusterUnassigned_orderName(!pc.isDescending());
                    }
                    break;
                case SortAttribute.SERVICE_NAME:
                    if(pc != null) {
                        services = getServiceDAO()
                            .findAllClusterUnassigned_orderName(!pc.isDescending());
                    }
                    break;
                default:
                    services = getServiceDAO()
                        .findAllClusterUnassigned_orderName_asc();
                    break;
            }
            for(Iterator i = services.iterator(); i.hasNext();) {
                Service aService = (Service)i.next();
                // remove service if its not viewable
                if(authzPks.contains(aService.getId())) {
                    toBePaged.add(aService);
                }
            }
            // valuePager converts local/remote interfaces to value objects
            // as it pages through them.
            return valuePager.seek(toBePaged, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    /**
     * Fetch all services that haven't been assigned to a cluster and that
     * haven't been assigned to any applications.
     * @return A List of ServiceValue objects representing all of the
     * unassigned services that the given subject is allowed to view.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getAllClusterAppUnassignedServices(AuthzSubjectValue subject, 
        PageControl pc) throws FinderException, PermissionException {
        try {
            // get list of pks user can view
            List authzPks = getViewableServices(subject);
            Collection services = null;
            Collection toBePaged = new ArrayList();
            pc = PageControl.initDefaults(pc, SortAttribute.RESOURCE_NAME);

            switch( pc.getSortattribute() ) {
                case SortAttribute.RESOURCE_NAME:
                    if(pc != null) {
                        services = getServiceDAO()
                            .findAllClusterAppUnassigned_orderName(!pc.isDescending());
                    }
                    break;
                case SortAttribute.SERVICE_NAME:
                    if(pc != null) {
                        services = getServiceDAO()
                            .findAllClusterAppUnassigned_orderName(!pc.isDescending());
                    }
                    break;
                default:
                    services = getServiceDAO()
                        .findAllClusterAppUnassigned_orderName_asc();
                    break;
            }
            for(Iterator i = services.iterator(); i.hasNext();) {
                Service aService = (Service)i.next();
                // remove service if its not viewable
                if(authzPks.contains(aService.getId())) {
                    toBePaged.add(aService);
                }
            }
            // valuePager converts local/remote interfaces to value objects
            // as it pages through them.
            return valuePager.seek(toBePaged, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    private PageList filterAndPage(Collection svcCol,
                                   AuthzSubjectValue subject,
                                   Integer svcTypeId, PageControl pc)
        throws ServiceNotFoundException, PermissionException {
        List services = new ArrayList();    
        // iterate over the services and only include those whose pk is 
        // present in the viewablePKs list
        if (svcTypeId != null && svcTypeId != APPDEF_RES_TYPE_UNDEFINED) {
            for (Iterator it = svcCol.iterator(); it.hasNext(); ) {
                Object o = it.next();                
                Integer thisSvcTypeId;
                if (o instanceof Service) {
                    thisSvcTypeId = ((Service)o).getServiceType().getId();
                } else {
                    ServiceCluster cluster = (ServiceCluster)o;
                    thisSvcTypeId = cluster.getServiceType().getId();
                }                
                // first, if they specified a server type, then filter on it
                if (!(thisSvcTypeId.equals(svcTypeId)))
                    continue;
                services.add(o);
            }
        } else {
            services.addAll(svcCol);
        }
        
        List toBePaged = filterUnviewable(subject, services);
        return valuePager.seek(toBePaged, pc);
    }

    private List filterUnviewable(AuthzSubjectValue subject,
                                  Collection services)
        throws PermissionException, ServiceNotFoundException {
        List viewableEntityIds;
        try {
            viewableEntityIds = this.getViewableServiceInventory(subject);
        } catch (FinderException e) {
            throw new ServiceNotFoundException(
                "no viewable services for " + subject);
        } catch (NamingException e) {
            throw new ServiceNotFoundException(
                "no viewable services for " + subject);
        }
    
        List retVal = new ArrayList();
        // if a cluster has some members that aren't viewable then
        // the user can't get at them but we don't worry about it here
        // when the cluster members are accessed, the group subsystem
        // will filter them
        // so here's the case for the ServiceLocal amongst the 
        // List of services
        // ***************** 
        // Note: yes, that's the case with regard to group members,
        // but not groups themselves. Clusters still need to be weeded
        // out here. - desmond
        for (Iterator iter = services.iterator(); iter.hasNext();) {
            Object o = iter.next();
            if (o instanceof Service) {
                Service aService = (Service)o;
                if (viewableEntityIds != null &&
                    viewableEntityIds.contains(aService.getEntityId())) {
                    retVal.add(o);
                }
            }
            else if (o instanceof ServiceCluster) {
                ServiceCluster aCluster = (ServiceCluster)o;
                AppdefEntityID clusterId = new AppdefEntityID(
                    AppdefEntityConstants.APPDEF_TYPE_GROUP,aCluster.getGroupId());
                if (viewableEntityIds != null &&
                    viewableEntityIds.contains(clusterId)) {
                    retVal.add(o);
                }
            }
        }
        return retVal;
    }

    /**
     * Get services by server and type.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByServer(AuthzSubjectValue subject,
                                        Integer serverId, PageControl pc) 
        throws ServiceNotFoundException, ServerNotFoundException, 
               PermissionException {
        return this.getServicesByServer(subject, serverId,
                                        this.APPDEF_RES_TYPE_UNDEFINED, pc);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByServer(AuthzSubjectValue subject,
                                        Integer serverId, Integer svcTypeId,
                                        PageControl pc) 
        throws ServiceNotFoundException, PermissionException {
        if (svcTypeId == null)
            svcTypeId = APPDEF_RES_TYPE_UNDEFINED;

        List services;

        switch (pc.getSortattribute()) {
        case SortAttribute.SERVICE_TYPE:
            services =
                getServiceDAO().findByServer_orderType(serverId);
            break;
        case SortAttribute.SERVICE_NAME:
        default:
            if (svcTypeId != APPDEF_RES_TYPE_UNDEFINED) {
                services =
                    getServiceDAO().findByServerAndType_orderName(
                        serverId, svcTypeId);
            }
            else {
                services =
                    getServiceDAO().findByServer_orderName(
                        serverId);
            }
            break;
        }
        // Reverse the list if descending
        if (pc != null && pc.isDescending()) {
            Collections.reverse(services);
        }
            
        List toBePaged = filterUnviewable(subject, services);
        return valuePager.seek(toBePaged, pc);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Integer[] getServiceIdsByServer(AuthzSubjectValue subject,
                                          Integer serverId, Integer svcTypeId) 
        throws ServiceNotFoundException, PermissionException {
        if (svcTypeId == null)
            svcTypeId = APPDEF_RES_TYPE_UNDEFINED;
 
        List services;
            
        if (svcTypeId == APPDEF_RES_TYPE_UNDEFINED) {
            services = getServiceDAO().findByServer_orderType(serverId);
        }
        else {
            services = getServiceDAO()
                .findByServerAndType_orderName(serverId, svcTypeId);
        }

        // Filter the unviewables
        List viewables = filterUnviewable(subject, services);

        Integer[] ids = new Integer[viewables.size()];
        Iterator it = viewables.iterator();
        for (int i = 0; it.hasNext(); i++) {
            Service local = (Service) it.next();
            ids[i] = local.getId();
        }
            
        return ids;
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public List getServicesByType(AuthzSubjectValue subject,
                                  Integer svcTypeId) 
        throws PermissionException {
        if (svcTypeId == null)
            svcTypeId = APPDEF_RES_TYPE_UNDEFINED;
    
        try {
            Collection services = getServiceDAO().findByType(svcTypeId);
            if (services.size() == 0) {
                return new ArrayList(0);
            }
            List toBePaged = filterUnviewable(subject, services);
            return valuePager.seek(toBePaged, PageControl.PAGE_ALL);
        } catch (ServiceNotFoundException e) {
            return new ArrayList(0);
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByService(AuthzSubjectValue subject,
                                         Integer serviceId, PageControl pc) 
        throws ServiceNotFoundException, PermissionException {
        return this.getServicesByService(subject, serviceId,
                                         this.APPDEF_RES_TYPE_UNDEFINED, pc);
    }
    
    /**
     * Get services by server.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByService(AuthzSubjectValue subject,
                                         Integer serviceId, Integer svcTypeId,
                                         PageControl pc) 
        throws ServiceNotFoundException, PermissionException {
            // find any children
        Collection childSvcs =
            getServiceDAO().findByParentAndType(serviceId, svcTypeId);
        return this.filterAndPage(childSvcs, subject, svcTypeId, pc);
    }

    /**
     * Get service IDs by service.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Integer[] getServiceIdsByService(AuthzSubjectValue subject,
                                            Integer serviceId,
                                            Integer svcTypeId) 
        throws ServiceNotFoundException, PermissionException {
        // find any children
        Collection childSvcs =
            getServiceDAO().findByParentAndType(serviceId, svcTypeId);
        
        List viewables = this.filterUnviewable(subject, childSvcs);
             
        Integer[] ids = new Integer[viewables.size()];
        Iterator it = viewables.iterator();
        for (int i = 0; it.hasNext(); i++) {
            Service local = (Service) it.next();
            ids[i] = local.getId();
        }
            
        return ids;
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByPlatform(AuthzSubjectValue subject,
                                          Integer platId, PageControl pc) 
        throws ServiceNotFoundException, PlatformNotFoundException, 
               PermissionException {
        return this.getServicesByPlatform(subject, platId,
                                          this.APPDEF_RES_TYPE_UNDEFINED, pc);
    }

    /**
     * Get platform services (children of virtual servers)
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getPlatformServices(AuthzSubjectValue subject,
                                        Integer platId, 
                                        PageControl pc)
        throws PlatformNotFoundException, 
               PermissionException, 
               ServiceNotFoundException {    
        return getPlatformServices(subject, platId, this.APPDEF_RES_TYPE_UNDEFINED, pc);
    }
    
    /**
     * Get platform services (children of virtual servers)
     * of a specified type
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getPlatformServices(AuthzSubjectValue subject,
                                        Integer platId, 
                                        Integer typeId,
                                        PageControl pc)
        throws PlatformNotFoundException, PermissionException, 
               ServiceNotFoundException {
        pc = PageControl.initDefaults(pc, SortAttribute.SERVICE_NAME);
        Collection allServices = getServiceDAO()
            .findPlatformServices_orderName(platId,true,pc.isAscending());
        return this.filterAndPage(allServices, subject, typeId, pc);
    }
    
    /**
     * Get platform services (children of virtual servers), mapped by type id
     * of a specified type
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Map getMappedPlatformServices(AuthzSubjectValue subject,
                                         Integer platId, 
                                         PageControl pc)
        throws PlatformNotFoundException, PermissionException, 
               ServiceNotFoundException
    {
        pc = PageControl.initDefaults(pc, SortAttribute.SERVICE_NAME);
            
        Collection allServices = getServiceDAO()
            .findPlatformServices_orderName(platId,true,pc.isAscending());
        HashMap retMap = new HashMap();
            
        // Map all services by type ID
        for (Iterator it = allServices.iterator(); it.hasNext(); ) {
            Service svc = (Service) it.next();
            Integer typeId = svc.getServiceType().getId();
            List addTo = (List) retMap.get(typeId);
                
            if (addTo == null) {
                addTo = new ArrayList();
                retMap.put(typeId, addTo);
            }
                
            addTo.add(svc);
        }
            
        // Page the lists before returning
        for (Iterator it = retMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            Integer typeId = (Integer) entry.getKey();
            List svcs = (List) entry.getValue();
                
            PageControl pcCheck =
                svcs.size() <= pc.getPagesize() ? PageControl.PAGE_ALL : pc;
                    
            svcs = this.filterAndPage(svcs, subject, typeId, pcCheck);
            entry.setValue(svcs);
        }
            
        return retMap;
    }
    
    /**
     * Get services by platform.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByPlatform(AuthzSubjectValue subject,
                                          Integer platId, Integer svcTypeId,
                                          PageControl pc) 
        throws ServiceNotFoundException, PlatformNotFoundException, 
               PermissionException 
    {
        Collection allServices;
        pc = PageControl.initDefaults(pc,SortAttribute.SERVICE_NAME);

        switch (pc.getSortattribute()) {
        case SortAttribute.SERVICE_NAME:
            allServices = getServiceDAO()
                .findByPlatform_orderName(platId, pc.isAscending());
            break;
        case SortAttribute.SERVICE_TYPE:
            allServices = getServiceDAO()
                .findByPlatform_orderType(platId, pc.isAscending());
            break;
        default:
            throw new IllegalArgumentException("Invalid sort attribute");
        }
        return this.filterAndPage(allServices, subject, svcTypeId, pc);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return A List of ServiceValue and ServiceClusterValue objects 
     * representing all of the services that the given subject is allowed to view.
     */
    public PageList getServicesByApplication(AuthzSubjectValue subject,
                                             Integer appId, PageControl pc ) 
        throws ApplicationNotFoundException, ServiceNotFoundException,
               PermissionException {
        return this.getServicesByApplication(subject, appId,
                                          this.APPDEF_RES_TYPE_UNDEFINED, pc);
    }
    
    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return A List of ServiceValue and ServiceClusterValue objects 
     * representing all of the services that the given subject is allowed to view.
     * @throws ApplicationNotFoundException if the appId is bogus
     * @throws ServiceNotFoundException if services could not be looked up
     */
    public PageList getServicesByApplication(AuthzSubjectValue subject,
                                             Integer appId, Integer svcTypeId,
                                             PageControl pc ) 
        throws PermissionException, ApplicationNotFoundException,
               ServiceNotFoundException {

        try {
            // we only look up the application to validate
            // the appId param
            getApplicationDAO().findById(appId);
        } catch (ObjectNotFoundException e) {
            throw new ApplicationNotFoundException(appId, e);
        }

        Collection appServiceCollection;
        AppServiceDAO appServLocHome =
            DAOFactory.getDAOFactory().getAppServiceDAO();
        pc = PageControl.initDefaults (pc, SortAttribute.SERVICE_NAME);

        switch (pc.getSortattribute()) {
        case SortAttribute.SERVICE_NAME :
            appServiceCollection = appServLocHome
                .findByApplication_orderSvcName(appId,pc.isAscending());
            break;
        case SortAttribute.RESOURCE_NAME :
            appServiceCollection = appServLocHome
                .findByApplication_orderSvcName(appId,pc.isAscending());
            break;
        case SortAttribute.SERVICE_TYPE :
            appServiceCollection = appServLocHome
                .findByApplication_orderSvcType(appId,pc.isAscending());
            break;
        default:
            throw new IllegalArgumentException("Unsupported sort " +
                                               "attribute ["+ pc.getSortattribute() +
                                               "] on PageControl : " + pc);
        }
        AppService appService;
        Iterator i = appServiceCollection.iterator();
        List services = new ArrayList();
        while ( i.hasNext() ) {
            appService = (AppService) i.next();
            if ( appService.getIsCluster() ) {
                services.add(appService.getServiceCluster());
            } else {
                services.add(appService.getService());
            }
        }
        return this.filterAndPage(services, subject, svcTypeId, pc);
   }

   /**
    * @ejb:interface-method
    * @ejb:transaction type="Required"
    * @return A List of ServiceValue and ServiceClusterValue objects 
    * representing all of the services that the given subject is allowed to view.
    */
   public PageList getServiceInventoryByApplication(AuthzSubjectValue subject,
                                            Integer appId, PageControl pc ) 
        throws ApplicationNotFoundException, ServiceNotFoundException,
               PermissionException {
        return getServiceInventoryByApplication(subject, appId,
                                                APPDEF_RES_TYPE_UNDEFINED, pc);
    }

    /**
     * Get all services by application.  This is to only be used for the
     * Evident API.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList 
        getFlattenedServicesByApplication(AuthzSubjectValue subject,
                                          Integer appId,
                                          Integer typeId,
                                          PageControl pc) 
        throws ApplicationNotFoundException, ServiceNotFoundException,
               PermissionException
    {
        if (typeId == null)
            typeId = APPDEF_RES_TYPE_UNDEFINED;

        ApplicationDAO appLocalHome = getApplicationDAO();

        Application appLocal;
        try {
            appLocal = appLocalHome.findById(appId);
        } catch(ObjectNotFoundException e){
            throw new ApplicationNotFoundException(appId, e);
        }

        Collection svcCollection = new ArrayList();
        Collection appSvcCollection = appLocal.getAppServices();
        Iterator it = appSvcCollection.iterator();
        while (it != null && it.hasNext()) {
            AppService appService = (AppService) it.next();

            if (appService.getIsCluster()) {
                svcCollection.addAll(
                    appService.getServiceCluster().getServices());
            } else {
                svcCollection.add(appService.getService());
            } 
        }

        return this.filterAndPage(svcCollection, subject, typeId, pc);
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return A List of ServiceValue and ServiceClusterValue objects 
     * representing all of the services that the given subject is allowed to view.
     */
    public PageList getServiceInventoryByApplication(AuthzSubjectValue subject,
                                                     Integer appId,
                                                     Integer svcTypeId,
                                                     PageControl pc ) 
        throws ApplicationNotFoundException, ServiceNotFoundException,
               PermissionException {
        if (svcTypeId == null || svcTypeId.equals(APPDEF_RES_TYPE_UNDEFINED)) {
            List services = getUnflattenedServiceInventoryByApplication(
                    subject, appId, pc);
            return this.filterAndPage(services, subject,
                                      APPDEF_RES_TYPE_UNDEFINED, pc);
        } else {
            return getFlattenedServicesByApplication(subject, appId, svcTypeId,
                                                     pc);
        }
    }

    /**
     * Get all service inventory by application, including those inside an
     * associated cluster
     * 
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * 
     * @param subject
     *            The subject trying to list services.
     * @param appId
     *            Application id.
     * @return A List of ServiceValue objects representing all of the services
     *         that the given subject is allowed to view.
     */
    public Integer[] getFlattenedServiceIdsByApplication(
        AuthzSubjectValue subject, Integer appId) 
        throws ServiceNotFoundException, PermissionException,
               ApplicationNotFoundException {

        List serviceInventory = 
            getUnflattenedServiceInventoryByApplication(subject, appId,
                                                        PageControl.PAGE_ALL);
        
        List servicePKs = new ArrayList();
        // flattening: open up all of the groups (if any) and get their services as well
        try {        
            for (Iterator iter = serviceInventory.iterator(); iter.hasNext();) {
                Object o = iter.next();
                // applications can have both clusters and services
                if (o instanceof Service) {
                    Service service = (Service) o;
                    // servers will only have these
                    servicePKs.add(service.getId());
                } else {
                    // this only happens when entId is for an application and
                    // a cluster is bound to it
                    ServiceCluster cluster = (ServiceCluster) o;
                    AppdefEntityID groupId = 
                        new AppdefEntityID(
                            AppdefEntityConstants.APPDEF_TYPE_GROUP, 
                            cluster.getGroupId().intValue());
                    // any authz resource filtering on the group members happens
                    // inside the group subsystem
                    try {
                        List memberIds = GroupUtil.getCompatGroupMembers(
                            subject, groupId, null, PageControl.PAGE_ALL);
                        for (Iterator memberIter = memberIds.iterator();
                             memberIter.hasNext(); ) {
                            AppdefEntityID memberEntId =
                                (AppdefEntityID) memberIter.next();
                            servicePKs.add(memberEntId.getId());
                        }
                    } catch (PermissionException e) {
                        // User not allowed to see this group
                        log.debug("User " + subject + " not allowed to view " +
                                  "group " + groupId);
                    }
                }            
            }
        } catch (GroupNotCompatibleException e){
            throw new InvalidAppdefTypeException(
                "serviceInventory has groups that are not compatible", e);
        } catch (AppdefEntityNotFoundException e) {
            throw new ServiceNotFoundException("could not return all services",
                                               e);
        }

        return (Integer[]) servicePKs.toArray(
            new Integer[servicePKs.size()]);
    }

    private List getUnflattenedServiceInventoryByApplication(
        AuthzSubjectValue subject, Integer appId, PageControl pc)
        throws ApplicationNotFoundException, ServiceNotFoundException {
        
        ApplicationDAO appLocalHome;
        AppServiceDAO appServLocHome;
        List appServiceCollection;
        Application appLocal;

        try {
            appLocalHome = getApplicationDAO();
            appServLocHome = DAOFactory.getDAOFactory().getAppServiceDAO();
            appLocal = appLocalHome.findById(appId);
        } catch (ObjectNotFoundException e) {
            throw new ApplicationNotFoundException(appId, e);
        }
        // appServiceCollection = appLocal.getAppServices();

        pc = PageControl.initDefaults(pc, SortAttribute.SERVICE_NAME);

        switch (pc.getSortattribute()) {
        case SortAttribute.SERVICE_NAME :
        case SortAttribute.RESOURCE_NAME :
            appServiceCollection =
                appServLocHome.findByApplication_orderName(appId);
            break;
        case SortAttribute.SERVICE_TYPE :
            appServiceCollection =
                appServLocHome.findByApplication_orderType(appId);
            break;
        default :
            throw new IllegalArgumentException(
                "Unsupported sort attribute [" + pc.getSortattribute() +
                "] on PageControl : " + pc);
        }
        if (pc.isDescending())
            Collections.reverse(appServiceCollection);

        // XXX Call to authz, get the collection of all services
        // that we are allowed to see.
        // OR, alternatively, find everything, and then call out
        // to authz in batches to find out which ones we are
        // allowed to return.

        AppService appService;
        Iterator i = appServiceCollection.iterator();
        List services = new ArrayList();
        while (i.hasNext()) {
            appService = (AppService) i.next();
            if (appService.getIsCluster()) {
                services.add(appService.getServiceCluster());
            } else {
                services.add(appService.getService());
            }
        }
        return services;
    }

    /**
     * Private method to validate a new ServiceValue object
     */
    private void validateNewService(ServiceValue sv)
        throws ValidationException {
        String msg = null;
        // first check if its new 
        if(sv.idHasBeenSet()) {
            msg = "This service is not new. It has id: " + sv.getId();
        }
        // else if(someotherthing)  ...

        // Now check if there's a msg set and throw accordingly
        if(msg != null) {
            throw new ValidationException(msg);
        }
    }     

    /**
     * @ejb:interface-method
     * @ejb:transaction type="RequiresNew"
     */
    public ServiceValue updateService(AuthzSubjectValue subject,
                                      ServiceValue existing)
        throws PermissionException, UpdateException, 
               AppdefDuplicateNameException, ServiceNotFoundException {
        try {
            Service service =
                getServiceDAO().findById(existing.getId());
            checkModifyPermission(subject, service.getEntityId());
            existing.setModifiedBy(subject.getName());
            existing.setMTime(new Long(System.currentTimeMillis()));
            trimStrings(existing);
            if(!existing.getName().equals(service.getName())) {
                ResourceValue rv = getAuthzResource(getServiceResourceType(),
                    existing.getId());
                rv.setName(existing.getName());
                updateAuthzResource(rv);
            }
            if(service.matchesValueObject(existing)) {
                log.debug("No changes found between value object and entity");
                return existing;
            } else {
                service.updateService(existing);
                return getServiceById(subject, existing.getId());
            }
        } catch (NamingException e) {
            throw new SystemException(e);
        } catch (FinderException e) {
            throw new ServiceNotFoundException(existing.getEntityId());
        }
    }

    /**
     * Change Service owner.
     *
     * @ejb:interface-method
     */
    public void changeServiceOwner(AuthzSubjectValue who,
                                   Integer serviceId,
                                   AuthzSubjectValue newOwner)
        throws FinderException, PermissionException, CreateException {
        try {
            // first lookup the service
            Service service = getServiceDAO().findById(serviceId);
            // check if the caller can modify this service
            checkModifyPermission(who, service.getEntityId());
            // now get its authz resource
            ResourceValue authzRes = getServiceResourceValue(serviceId);
            // change the authz owner
            getResourceManager().setResourceOwner(who, authzRes, newOwner);
            // update the owner field in the appdef table -- YUCK
            service.setOwner(newOwner.getName());
            service.setModifiedBy(who.getName());
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }


    private void validateNewServiceType(ServiceTypeValue stv,
                                        ServerTypeValue serverType) 
        throws ValidationException {

        String msg = null;
        // check if its new
        if(stv.idHasBeenSet()) {
            msg = "This ServiceType is not new. It has id: " + stv.getId();
        }
        else {
            // insert validation here
        }
        if(msg != null) {
            throw new ValidationException(msg);
        }
    }

    /**
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRESNEW"
     */
    public void updateServiceTypes(String plugin, ServiceTypeInfo[] infos)
        throws CreateException, FinderException, RemoveException {
        AuthzSubjectValue overlord = null;
        
        // First, put all of the infos into a Hash
        HashMap infoMap = new HashMap();
        for (int i = 0; i < infos.length; i++) {
            infoMap.put(infos[i].getName(), infos[i]);
        }

        HashMap serverTypes = new HashMap();

        ServiceTypeDAO stLHome = getServiceTypeDAO();
        try {
            Collection curServices = stLHome.findByPlugin(plugin);
            ServerTypeDAO stHome = getServerTypeDAO();
            
            for (Iterator i = curServices.iterator(); i.hasNext();) {
                ServiceType stlocal = (ServiceType) i.next();

                if (log.isDebugEnabled()) {
                    log.debug("Begin updating ServiceTypeLocal: " +
                              stlocal.getName());
                }

                ServiceTypeInfo sinfo =
                    (ServiceTypeInfo) infoMap.remove(stlocal.getName());

                // See if this exists
                if (sinfo == null) {
                    // Get overlord
                    if (overlord == null)
                        overlord = getOverlord();
                    
                    // Remove all services
                    for (Iterator svcIt = stlocal.getServices().iterator();
                         svcIt.hasNext(); ) {
                        Service svcLocal = (Service) svcIt.next();
                        try {
                            removeService(overlord, svcLocal);
                        } catch (PermissionException e) {
                            // This should never happen, we're the overlord
                            throw new SystemException(e);
                        }
                    }
                    
           
                    stLHome.remove(stlocal);
                } else {
                    // Just update it
                    // XXX TODO MOVE THIS INTO THE ENTITY
                    if (!sinfo.getName().equals(stlocal.getName()))
                        stlocal.setName(sinfo.getName());
                        
                    if (!sinfo.getDescription().equals(
                        stlocal.getDescription()))
                        stlocal.setDescription(sinfo.getDescription());
                    
                    if (sinfo.getInternal() !=  stlocal.getIsInternal())
                        stlocal.setIsInternal(sinfo.getInternal());

                    // Could be null if servertype was deleted/updated by plugin
                    ServerType svrtype = stlocal.getServerType();

                    // Check server type
                    if (svrtype == null ||
                        !sinfo.getServerName().equals(svrtype.getName())) {
                        // Lookup the server type
                        if (serverTypes.containsKey(sinfo.getServerName()))
                            svrtype = (ServerType)
                                serverTypes.get(sinfo.getServerName());
                        else {
                            svrtype = stHome.findByName(sinfo.getServerName());
                            if (svrtype == null) {
                                throw new FinderException(
                                    "Unable to find server " +
                                    sinfo.getServerName() +
                                    " on which service '" +
                                    stlocal.getName() +
                                    "' relies");
                            }
                            serverTypes.put(svrtype.getName(), svrtype);
                        }
                        stlocal.setServerType(svrtype);
                    }
                }
            }
            
            // Now create the left-overs
            for (Iterator i = infoMap.values().iterator(); i.hasNext();) {
                ServiceTypeInfo sinfo = (ServiceTypeInfo) i.next();

                // Just update it
                ServiceType stype = new ServiceType();
                stype.setPlugin(plugin);
                stype.setName(sinfo.getName());
                stype.setDescription(sinfo.getDescription());
                stype.setIsInternal(sinfo.getInternal());

                // Now create the service type
                ServiceType stlocal = stLHome.create(stype);
                ServiceTypeValue stvo = stlocal.getServiceTypeValue();

                // Lookup the server type
                ServerType servTypeEJB;
                if (serverTypes.containsKey(sinfo.getServerName()))
                    servTypeEJB = (ServerType)
                        serverTypes.get(sinfo.getServerName());
                else {
                    servTypeEJB = stHome.findByName(sinfo.getServerName());
                    serverTypes.put(servTypeEJB.getName(), servTypeEJB);
                }
                stlocal.setServerType(servTypeEJB);
            }
        } finally {
            stLHome.getSession().flush();
        }
    }

    /**
     * Remove a Service from the inventory.
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void removeService(AuthzSubjectValue subj, Integer serviceId)
        throws RemoveException, FinderException, PermissionException {
        Service service;
        service = getServiceDAO().findById(serviceId);
        removeService(subj, service);
    }

    /**
     * A removeService method that takes a ServiceLocal.  This is called by
     * ServerManager.removeServer when cascading a delete onto services.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void removeService(AuthzSubjectValue subject, Service service)
        throws RemoveException, FinderException, PermissionException {

        Integer serviceId = service.getId();

        checkRemovePermission(subject, service.getEntityId());
        Integer cid = service.getConfigResponseId();

        // Remove authz resource.
        removeAuthzResource(service.getEntityId());

        // Remove service from parent Server's Services collection
        Server server = service.getServer();
        Collection services = server.getServices();
        for (Iterator i = services.iterator(); i.hasNext(); ) {
            Service s = (Service)i.next();
            if (s.equals(service)) {
                i.remove();
                break;
            }
        }

        // remove from appdef
        getServiceDAO().remove(service);

        // remove the config response
        if (cid != null) {
            try {
                ConfigResponseDAO cdao = getConfigResponseDAO();
                cdao.remove(cdao.findById(cid));
            } catch (ObjectNotFoundException e) {
                // OK, no config response, just log it
                log.warn("Invalid config ID " + cid);
            }
        }

        // remove custom properties
        deleteCustomProperties(AppdefEntityConstants.APPDEF_TYPE_SERVICE,
                               serviceId.intValue());

        // Send resource delete event
        ResourceDeletedZevent zevent =
            new ResourceDeletedZevent(subject, service.getEntityId());
        ZeventManager.getInstance().enqueueEventAfterCommit(zevent);
    }

    /**
     * Create a service cluster from a set of service Ids
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRESNEW"
     */
    public Integer createCluster(AuthzSubjectValue subj,
                                 ServiceClusterValue cluster,
                                 List serviceIdList)
        throws AppSvcClustDuplicateAssignException, 
               AppSvcClustIncompatSvcException, CreateException {
        // TODO check authz createCluster operation 
        ServiceCluster clusterEJB =
            getServiceClusterDAO().create(cluster, serviceIdList);
        return clusterEJB.getId();
    }
    
    /**
     * @param serviceIdList - the list of service id's which comprise the updated cluster
     * @ejb:interface-method
     * @ejb:transaction type="RequiresNew"
     */
    public void updateCluster(AuthzSubjectValue subj,
                              ServiceClusterValue cluster,
                              List serviceIdList)
        throws AppSvcClustDuplicateAssignException,
               AppSvcClustIncompatSvcException,
               FinderException, PermissionException { 
        // find the cluster
        ServiceCluster clusterEJB =
            getServiceClusterDAO().findById(cluster.getId());
        clusterEJB.updateCluster(cluster, serviceIdList);
    }
    
    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void removeCluster(AuthzSubjectValue subj, Integer clusterId)
        throws RemoveException, FinderException, PermissionException {
        ServiceCluster clusterLoc =
            getServiceClusterDAO().findById(clusterId);
        // XXX - Authz chex needed?
        //checkRemovePermission(subj, clusterLoc.getEntityId());
        getServiceClusterDAO().remove(clusterLoc);
    }
    
    /**
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public ServiceClusterValue getClusterById(AuthzSubjectValue subj,
                                              Integer cid)
        throws FinderException, PermissionException {
        // TODO authz        
        return getServiceClusterDAO().findById(cid).getServiceClusterValue();
    }
    
    /**
     * Retrieve all services belonging to a cluster
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList getServicesByCluster(AuthzSubjectValue subj,
                                         Integer clusterId)
        throws FinderException, PermissionException {
        // TODO AUTHZ
        Collection clustSvcs = DAOFactory.getDAOFactory().getServiceDAO()
            .findByCluster(clusterId);
        PageList page = new PageList();
        page.setTotalSize(clustSvcs.size());
        for(Iterator i = clustSvcs.iterator(); i.hasNext();) {
            Service aSvc = (Service)i.next();
            page.add(aSvc.getServiceValue());
        }
        return page;
    }

    /**
     * Get all service clusters.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     * @return A List of ServiceClusterValue objects representing all of the
     * services that the given subject is allowed to view.
     */
    public PageList getAllServiceClusters(AuthzSubjectValue subject, PageControl pc)
        throws FinderException, PermissionException {
        try {
            ServiceClusterDAO clusterLocalHome = getServiceClusterDAO();

            Collection clusters = null;
            Collection toBePaged = new ArrayList();

            // get list of group value objects user can view
            List viewableGroups = null;
            try {
                viewableGroups = getViewableGroups(subject);
            } catch (AppdefGroupNotFoundException e) {
                viewableGroups = new ArrayList();
            }

            pc = PageControl.initDefaults(pc, SortAttribute.RESOURCE_NAME);

            switch( pc.getSortattribute() ) {
                case SortAttribute.RESOURCE_NAME:
                    clusters =
                        clusterLocalHome.findAll_orderName(!pc.isDescending());
                    break;
                case SortAttribute.SERVICE_NAME:
                    clusters =
                        clusterLocalHome.findAll_orderName(!pc.isDescending());
                    break;
                default:
                    clusters = clusterLocalHome.findAll();
                    break;
            }
            // only page cluster if id is assigned to viewable (service) group
            for(Iterator i = clusters.iterator(); i.hasNext();) {
                ServiceCluster aCluster = (ServiceCluster)i.next();
                // only page cluster if it is viewable.
                for (int x=0;x<viewableGroups.size();x++) {
                    AppdefGroupValue thisGroup = 
                        (AppdefGroupValue)viewableGroups.get(x);
                    if (thisGroup.getClusterId() == 
                        aCluster.getId().intValue()) {
                        toBePaged.add(aCluster);
                    }
                }
            }
            // valuePager converts local/remote interfaces to value objects
            // as it pages through them.
            return valuePager.seek(toBePaged, pc);
        } catch (NamingException e) {
            throw new SystemException(e);
        }
    }

    public static ServiceManagerLocal getOne() {
        try {
            return ServiceManagerUtil.getLocalHome().create();    
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public void ejbCreate() throws CreateException {
        try {
            valuePager = Pager.getPager(VALUE_PROCESSOR);
        } catch ( Exception e ) {
            throw new CreateException("Could not create value pager:" + e);
        }
    }

    private void trimStrings(ServiceValue service) {
        if (service.getDescription() != null)
            service.setDescription(service.getDescription().trim());
        if (service.getLocation() != null)
            service.setLocation(service.getLocation().trim());
        if (service.getName() != null)
            service.setName(service.getName().trim());
    }

    public void ejbRemove() { }
    public void ejbActivate() { }
    public void ejbPassivate() { }
}
