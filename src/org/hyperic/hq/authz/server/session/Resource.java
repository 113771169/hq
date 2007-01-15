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

package org.hyperic.hq.authz.server.session;

import java.util.Collection;
import java.util.ArrayList;

import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Resource extends AuthzNamedBean
{
    public static final Log _log = LogFactory.getLog(Resource.class);

    private ResourceType _resourceType;
    private Integer _instanceId;
    private Integer _cid;
    private AuthzSubject _owner;
    private boolean _system = false;
    private Collection _resourceGroups = new ArrayList();
    private Collection _virtuals = new ArrayList();

    private ResourceValue resourceValue = new ResourceValue();

    public Resource() {
        super();
    }

    public Resource(ResourceValue val) {
        setResourceValue(val);
    }

    public Resource(ResourceType resourceTypeId, Integer instanceId,
                    Integer cid, AuthzSubject subjectId, String name,
                    boolean fsystem, Collection resourceGroups)
    {
        super(name);
        _resourceType = resourceTypeId;
        _instanceId = instanceId;
        _cid = cid;
        _owner = subjectId;
        _system = fsystem;
        _resourceGroups = resourceGroups;
    }

    public ResourceType getResourceType() {
        return _resourceType;
    }

    protected void setResourceType(ResourceType resourceTypeId) {
        _resourceType = resourceTypeId;
    }

    public Integer getInstanceId() {
        return _instanceId;
    }

    protected void setInstanceId(Integer val) {
        _instanceId = val;
    }

    public Integer getCid() {
        return _cid;
    }

    protected void setCid(Integer val) {
        _cid = val;
    }

    public AuthzSubject getOwner() {
        return _owner;
    }

    protected void setOwner(AuthzSubject val) {
        _owner = val;
    }

    public boolean isSystem() {
        return _system;
    }

    protected void setSystem(boolean fsystem) {
        _system = fsystem;
    }

    public Collection getResourceGroups() {
        return _resourceGroups;
    }

    public Collection getVirtuals() {
        return _virtuals;
    }

    protected void setVirtuals(Collection virtuals) {
        _virtuals = virtuals;
    }

    protected void setResourceGroups(Collection val)
    {
        _resourceGroups = val;
    }

    /**
     * @deprecated use (this) Resource instead
     */
    public ResourceValue getResourceValue()
    {
        resourceValue.setId(getId());
        resourceValue.setAuthzSubjectValue(getOwner().getAuthzSubjectValue());
        resourceValue.setInstanceId(getInstanceId());
        resourceValue.setName(getName());
        resourceValue.setSortName(getSortName());
        resourceValue.setSystem(isSystem());

        // Resource type of a resource should never change
        if (resourceValue.getResourceTypeValue() == null)
            resourceValue
                .setResourceTypeValue(getResourceType().getResourceTypeValue());

        return resourceValue;
    }

    protected void setResourceValue(ResourceValue val) {
        setId(val.getId());
        setInstanceId(val.getInstanceId());
        setName(val.getName());
        setResourceType(new ResourceType(val.getResourceTypeValue()));
        setSystem(val.getSystem());
    }

    public Object getValueObject() {
        return getResourceValue();
    }

    public boolean isOwner(Integer possibleOwner)
    {
        boolean is = false;

        if (possibleOwner == null) {
            //XXX: Throw exception instead.
            _log.error("possible Owner is NULL. This is probably not " +
                       "what you want.");
        } else {
            // Overlord owns everything.
            if (is = possibleOwner.equals(AuthzConstants.overlordId) ==
                false) {
                if (_log.isDebugEnabled() && possibleOwner != null) {
                    _log.debug("User is " + possibleOwner +
                               " owner is " + getOwner().getId());
                }
                is = (possibleOwner.equals(getOwner().getId()));
            }
        }
        return is;
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof Resource) || !super.equals(obj)) {
            return false;
        }
        Resource o = (Resource) obj;
        return
            ((_resourceType == o.getResourceType()) ||
             (_resourceType != null && o.getResourceType() != null &&
              _resourceType.equals(o.getResourceType())))
            &&
            ((_instanceId == o.getInstanceId()) ||
             (_instanceId != null && o.getInstanceId() != null &&
              _instanceId.equals(o.getInstanceId())));
    }

    public int hashCode()
    {
        int result = super.hashCode();

        result = 37 * result + (_resourceType != null ? _resourceType.hashCode() : 0);
        result = 37 * result + (_instanceId != null ? _instanceId.hashCode() : 0);

        return result;
    }
}
