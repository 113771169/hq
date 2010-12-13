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

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.zevents.Zevent;
import org.hyperic.hq.zevents.ZeventPayload;
import org.hyperic.hq.zevents.ZeventSourceId;

/**
 * Abstract class for appdef resource events.
 */
public abstract class ResourceZevent extends Zevent {

    protected AppdefEntityID appdefEntityID;
    
    public ResourceZevent(Integer subject, AppdefEntityID id) {
        super(new ResourceZeventSource(id.getId()),
              new ResourceZeventPayload(subject, id.getId()));
        this.appdefEntityID = id;
    }

    public ResourceZevent(ResourceZeventSource source,
                          ResourceZeventPayload payload) {
        super(source, payload);
    }

    public Integer getId() {
        return ((ResourceZeventPayload)getPayload()).
            getId();
    }

    public Integer getAuthzSubjectId() {
        return ((ResourceZeventPayload)getPayload()).
            getAuthzSubjectId();
    }
    
    public AppdefEntityID getAppdefEntityID() {
        return appdefEntityID;
    }

    protected static class ResourceZeventSource
        implements ZeventSourceId
    {
        private static final long serialVersionUID = -2799620967593343325L;
        
        private Integer _id;

        public ResourceZeventSource(Integer id) {
            _id = id;
        }

        public int hashCode() {
            return _id.hashCode();
        }

        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            
            if (other instanceof ResourceZeventSource) {
                ResourceZeventSource src = (ResourceZeventSource)other;
                return _id.equals(src._id);                
            }
            
            return false;
        }
    }

    protected static class ResourceZeventPayload
        implements ZeventPayload
    {
        private Integer _id;
        private Integer _subject;

        public ResourceZeventPayload(Integer subject, Integer id) {
            _subject = subject;
            _id = id;
        }

        public Integer getAuthzSubjectId() {
            return _subject;
        }

        public Integer getId() {
            return _id;
        }
    }
}
