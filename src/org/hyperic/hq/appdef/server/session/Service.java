/*                                                                 
 * NOTE: This copyright does *not* cover user programs that use HQ 
 * program services by normal system calls through the application 
 * program interfaces provided as part of the Hyperic Plug-in Development 
 * Kit or the Hyperic Client Development Kit - this is merely considered 
 * normal use of the program, and does *not* fall under the heading of 
 * "derived work". 
 *  
 * Copyright (C) [2004-2009], Hyperic, Inc. 
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.hyperic.hq.appdef.ConfigResponseDB;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.appdef.shared.ServiceValue;
import org.hyperic.hq.authz.HasAuthzOperations;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.authz.shared.AuthzConstants;

public class Service extends AppdefResource
    implements HasAuthzOperations
{
    private static final Map _authOps;
    static {
        _authOps = new HashMap();
        
        _authOps.put("create",       AuthzConstants.serviceOpCreateService);
        _authOps.put("modify",       AuthzConstants.serviceOpModifyService);
        _authOps.put("remove",       AuthzConstants.serviceOpRemoveService);
        _authOps.put("view",         AuthzConstants.serviceOpViewService);
        _authOps.put("monitor",      AuthzConstants.serviceOpMonitorService);
        _authOps.put("control",      AuthzConstants.serviceOpControlService);
        _authOps.put("manageAlerts", AuthzConstants.serviceOpManageAlerts);
    }
    
    private boolean _autodiscoveryZombie;
    private boolean _serviceRt;
    private boolean _endUserRt;
    private Service _parentService;
    private Server _server;
    private ServiceType _serviceType;
    private ResourceGroup _resourceGroup;
    private ConfigResponseDB _configResponse;
    private Collection _appServices;
    private Resource _resource;

    public Service() {
    }

    public AppdefEntityID getEntityId() {
        return AppdefEntityID.newServiceID(getId());
    }

    public boolean isAutodiscoveryZombie() {
        return _autodiscoveryZombie;
    }

    void setAutodiscoveryZombie(boolean autodiscoveryZombie) {
        _autodiscoveryZombie = autodiscoveryZombie;
    }

    public boolean isServiceRt() {
        return _serviceRt;
    }

    public void setServiceRt(boolean serviceRt) {
        _serviceRt = serviceRt;
    }

    public boolean isEndUserRt() {
        return _endUserRt;
    }

    public void setEndUserRt(boolean endUserRt) {
        _endUserRt = endUserRt;
    }

    public Service getParentService() {
        return _parentService;
    }

    void setParentService(Service parentService) {
        _parentService = parentService;
    }

    /**
     * legacy EJB getter for configresponse id
     * @deprecated use setParentService() instead
     */
    void setParentId(Integer parentId) {
        if (parentId != null && parentId.intValue() != 0) {
            Service s = new Service();
            s.setId(parentId);
            setParentService(s);
        } else {
            setParentService(null);
        }
    }

    public Server getServer() {
        return _server;
    }

    void setServer(Server server) {
        _server = server;
    }

    public ServiceType getServiceType() {
        return _serviceType;
    }

    void setServiceType(ServiceType serviceType) {
        _serviceType = serviceType;
    }

    public ResourceGroup getResourceGroup() {
        return _resourceGroup;
    }

    void setResourceGroup(ResourceGroup resourceGroup) {
        _resourceGroup = resourceGroup;
    }

    public ConfigResponseDB getConfigResponse() {
        return _configResponse;
    }

    void setConfigResponse(ConfigResponseDB configResponse) {
        _configResponse = configResponse;
    }

    /**
     * @return the resource
     */
    public Resource getResource() {
        return _resource;
    }

    /**
     * @param resource the resource to set
     */
    void setResource(Resource resource) {
        this._resource = resource;
    }

    public Collection getAppServices() {
        return _appServices;
    }

    void setAppServices(Collection appServices) {
        _appServices = appServices;
    }

    private ServiceValue _serviceValue = new ServiceValue();
    /**
     * legacy EJB DTO pattern
     * @deprecated use (this) Service object instead
     */
    public ServiceValue getServiceValue()
    {
        _serviceValue.setSortName(getSortName());
        _serviceValue.setAutodiscoveryZombie(isAutodiscoveryZombie());
        _serviceValue.setServiceRt(isServiceRt());
        _serviceValue.setEndUserRt(isEndUserRt());
        _serviceValue.setModifiedBy(getModifiedBy());
        _serviceValue.setOwner(getOwner());
        _serviceValue.setLocation(getLocation());
        _serviceValue.setConfigResponseId(_configResponse != null ?
                                          _configResponse.getId() : null);
        _serviceValue.setParentId(_parentService != null ?
                                  _parentService.getId() : null);
        _serviceValue.setName(getName());
        _serviceValue.setDescription(getDescription());
        _serviceValue.setId(getId());
        _serviceValue.setMTime(getMTime());
        _serviceValue.setCTime(getCTime());
        if (getServer() != null) {
            _serviceValue.setServer(getServer().getServerLightValue());
        }
        else
            _serviceValue.setServer( null );
        if ( getResourceGroup() != null ) {
            _serviceValue.setResourceGroup(
                getResourceGroup().getResourceGroupValue());
        }
        else
            _serviceValue.setResourceGroup( null );
        if ( getServiceType() != null ) {
            _serviceValue.setServiceType(getServiceType().getServiceTypeValue());
        }
        else
            _serviceValue.setServiceType( null );
        return _serviceValue;
    }

    private String getOwner() {
        return getResource() != null && getResource().getOwner() != null ?
                getResource().getOwner().getName() : "";
    }

    /**
     * legacy EJB DTO pattern.
     * Compare this entity bean to a value object
     * @deprecated should use (this) Service object and hibernate dirty() check
     * @return true if the service value matches this entity
     */
    public boolean matchesValueObject(ServiceValue obj)
    {
        boolean matches;
        matches = super.matchesValueObject(obj) &&
            (getName() != null ? getName().equals(obj.getName())
                : (obj.getName() == null)) &&
            (getDescription() != null ?
                getDescription().equals(obj.getDescription())
                : (obj.getDescription() == null)) &&
            (getLocation() != null ?
                getLocation().equals(obj.getLocation())
                : (obj.getLocation() == null)) &&
            (isEndUserRt() == obj.getEndUserRt()) &&
            (isServiceRt() == obj.getServiceRt());
        return matches;
    }

    /**
     * legacy EJB DTO pattern for copying attribute values from value
     * object.
     *
     * Set the value object. This method does *NOT* update any of the CMR's
     * included in the value object. This is for speed/locking reasons
     */
    void updateService(ServiceValue valueHolder) {
        setDescription( valueHolder.getDescription() );
        setAutodiscoveryZombie( valueHolder.getAutodiscoveryZombie() );
        setServiceRt( valueHolder.getServiceRt() );
        setEndUserRt( valueHolder.getEndUserRt() );
        setModifiedBy( valueHolder.getModifiedBy() );
        setLocation( valueHolder.getLocation() );
        setParentId( valueHolder.getParentId() );
        setName( valueHolder.getName() );
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Service) || !super.equals(obj)) {
            return false;
        }
        Service o = (Service)obj;
        return
            ((_server == o.getServer()) ||
             (_server!=null && o.getServer()!=null &&
              _server.equals(o.getServer())));
    }

    public int hashCode() {
        int result = super.hashCode();

        result = 37*result + (_server != null ? _server.hashCode() : 0);

        return result;
    }

    public AppdefResourceType getAppdefResourceType() {
        return _serviceType;
    }

    public AppdefResourceValue getAppdefResourceValue() {
        return getServiceValue();
    }
    
    protected String _getAuthzOp(String op) {
        return (String)_authOps.get(op);
    }
}
