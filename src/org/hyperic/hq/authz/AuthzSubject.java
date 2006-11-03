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

package org.hyperic.hq.authz;

import java.util.ArrayList;
import java.util.Collection;

import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;

public class AuthzSubject extends AuthzNamedBean {
    private String     _dsn;
    private Integer    _cid;
    private String     _firstName;
    private String     _lastName;
    private String     _emailAddress;
    private String     _smsAddress;
    private String     _phoneNumber;
    private String     _department;
    private boolean    _active = true;
    private boolean    _system = false;
    private Resource   _resource;
    private Collection _roles = new ArrayList();
    private Collection _userConfigs = new ArrayList();

    private AuthzSubjectValue _valueObj;

    protected AuthzSubject() {
    }

    public AuthzSubject(AuthzSubjectValue val) {
        setAuthzSubjectValue(val);
    }

    public String getAuthDsn() {
        return _dsn;
    }

    public void setAuthDsn(String val) {
        _dsn = val;
    }

    public Integer getCid() {
        return _cid;
    }

    public void setCid(Integer val) {
        _cid = val;
    }

    public String getFirstName() {
        return _firstName;
    }

    public void setFirstName(String val) {
        _firstName = val;
    }

    public String getLastName() {
        return _lastName;
    }

    public void setLastName(String val) {
        _lastName = val;
    }

    public String getEmailAddress() {
        return _emailAddress;
    }

    public void setEmailAddress(String val) {
        _emailAddress = val;
    }

    public String getSMSAddress() {
        return _smsAddress;
    }

    public void setSMSAddress(String val) {
        _smsAddress = val;
    }

    public String getPhoneNumber() {
        return _phoneNumber;
    }

    public void setPhoneNumber(String val) {
        _phoneNumber = val;
    }

    public String getDepartment() {
        return _department;
    }

    public void setDepartment(String val) {
        _department = val;
    }

    public boolean isActive() {
        return _active;
    }

    public boolean getActive() {
        return isActive();
    }

    public void setActive(boolean val) {
        _active = val;
    }

    public boolean isSystem() {
        return _system;
    }

    public boolean getSystem() {
        return isSystem();
    }

    public void setSystem(boolean val) {
        _system = val;
    }

    public Resource getResource() {
        return _resource;
    }

    public void setResource(Resource val) {
        _resource = val;
    }

    public Collection getRoles() {
        return _roles;
    }

    public void setRoles(Collection val) {
        _roles = val;
    }

    public void addRole(Role role) {
        _roles.add(role);
    }

    public void removeRole(Role role) {
        _roles.remove(role);
    }

    public void removeAllRoles() {
        _roles.clear();
    }

    public Collection getUserConfigs() {
        return _userConfigs;
    }

    public void setUserConfigs(Collection val) {
        _userConfigs = val;
    }

    public Object getValueObject() {
        return getAuthzSubjectValue();
    }
    
    public AuthzSubjectValue getAuthzSubjectValue() {
        if (_valueObj == null) 
            _valueObj = new AuthzSubjectValue();

        _valueObj.setSortName(getSortName());
        _valueObj.setActive(getActive());
        _valueObj.setSystem(getSystem());
        _valueObj.setAuthDsn((getAuthDsn() == null) ? "" : getAuthDsn());
        _valueObj.setEmailAddress((getEmailAddress() == null) ? "" : 
                                  getEmailAddress());
        _valueObj.setSMSAddress((getSMSAddress() == null) ? "" : 
                                getSMSAddress());
        _valueObj.setFirstName((getFirstName() == null) ? "" : getFirstName());
        _valueObj.setLastName((getLastName() == null) ? "" : getLastName());
        _valueObj.setPhoneNumber((getPhoneNumber() == null) ? "" : 
                                 getPhoneNumber());
        _valueObj.setDepartment((getDepartment() == null) ? "" : 
                                getDepartment());
        _valueObj.setName((getName() == null) ? "" : getName());
        _valueObj.setId(getId());
        return _valueObj;
    }

    public void setAuthzSubjectValue(AuthzSubjectValue authzSubjectValue) {
        setActive(authzSubjectValue.getActive());
        setAuthDsn(authzSubjectValue.getAuthDsn());
        setDepartment(authzSubjectValue.getDepartment());
        setEmailAddress(authzSubjectValue.getEmailAddress());
        setFirstName(authzSubjectValue.getFirstName());
        setName(authzSubjectValue.getName());
        setPhoneNumber(authzSubjectValue.getPhoneNumber());
        setSMSAddress(authzSubjectValue.getSMSAddress());
        setSystem(authzSubjectValue.getSystem());
    }

    public boolean isRoot() {
        return getId().equals(AuthzConstants.rootSubjectId);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof AuthzSubject) || !super.equals(obj)) {
            return false;
        }
        AuthzSubject o = (AuthzSubject) obj;
        return ((_dsn == o.getAuthDsn()) ||
                 (_dsn != null && o.getAuthDsn() != null &&
                  _dsn.equals(o.getAuthDsn())));
    }

    public int hashCode() {
        int result = super.hashCode();

        result = 37 * result + (_dsn != null ? _dsn.hashCode() : 0);

        return result;
    }
}
