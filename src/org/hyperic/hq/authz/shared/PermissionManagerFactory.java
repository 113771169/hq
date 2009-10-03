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

package org.hyperic.hq.authz.shared;

import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.shared.ProductProperties;
import org.hyperic.util.jdbc.DBUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class PermissionManagerFactory {

    private static String CLASS_PROP = "hyperic.hq.authz.permissionManager";
    private static PermissionManager pm = null;
    
    //TODO better way to create appropriate PermissionManager
    public static PermissionManager getPermissionManager(DBUtil dbUtil) {
        if (pm == null) {
            String pmClass =
                ProductProperties.getProperty(CLASS_PROP);
            
            try {
                Class clazz = Class.forName(pmClass);
                pm = (PermissionManager)clazz.getConstructor(DBUtil.class).newInstance(dbUtil);
            } catch (Exception e) {
                // Should not happen
                throw new SystemException("Unable to load Permission " +
                                          "Manager: " + e, e);
            }
        }
        
        return pm;
    }

    
    public static PermissionManager getInstance() 
        throws SystemException
    {
        
        return pm;
    }
}
