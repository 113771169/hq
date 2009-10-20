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

package org.hyperic.hq.events.server.session;

import java.util.Collection;
import java.util.Iterator;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.AuthzSubjectManagerEJBImpl;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.events.server.session.AlertDefinition;
import org.hyperic.hq.events.server.session.AlertDefinitionManagerImpl;
import org.hyperic.hq.events.shared.AlertDefinitionManager;

/**
 * This class is an in-memory map of whether "availability down"
 * alert definitions exist for a resource
 */
public class AvailabilityDownAlertDefinitionCache {
    private Log log = LogFactory.getLog(AvailabilityDownAlertDefinitionCache.class);

    public static final String CACHENAME = "AvailabilityDownAlertDefinitionCache";
    private static final Object _cacheLock = new Object();
    private final Cache _cache;

    private static final AvailabilityDownAlertDefinitionCache singleton = 
        new AvailabilityDownAlertDefinitionCache();

    private AvailabilityDownAlertDefinitionCache() {
        _cache = CacheManager.getInstance().getCache(CACHENAME);
    }

    public boolean exists(AppdefEntityID key) {        
        Boolean bool = null;
        Element el = _cache.get(key);
        
        if (el != null) {
            bool = (Boolean) el.getObjectValue();
        } else {
            bool = load(key);
        }
        return (bool == null ? false : bool.booleanValue());
    }

    public void put(AppdefEntityID key, Boolean value) {
        Element el = new Element(key, value);
                                                          
        synchronized (_cacheLock) {
            _cache.put(el);
        }
    }
    
    public void remove(AppdefEntityID key) {
        synchronized (_cacheLock) {
            _cache.remove(key);
        }
    }

    public void clear() {
        synchronized (_cacheLock) {
            _cache.removeAll();
        }
    }
    
    private Boolean load(AppdefEntityID key) {
        Boolean value = null;
             
        try {
            AlertDefinitionManager adm = AlertDefinitionManagerImpl.getOne();
            AuthzSubject hqadmin = AuthzSubjectManagerEJBImpl.getOne()
                                        .getSubjectById(AuthzConstants.rootSubjectId);
            Collection alertDefs = adm.findAlertDefinitions(hqadmin, key);

            for (Iterator it=alertDefs.iterator(); it.hasNext(); ) {
                AlertDefinition def = (AlertDefinition) it.next();

                if (def.isActive() && def.isAvailability(false)) {
                    value = Boolean.TRUE;
                    break;
                }
            }
            if (value == null) {
                value = Boolean.FALSE;
            }
            singleton.put(key, value);
            
        } catch (Exception e) {
            log.error("Could not get alert definitions for " + key, e);
        }

        return value;
    }

    public static AvailabilityDownAlertDefinitionCache getInstance() {
        return singleton;
    }

}
