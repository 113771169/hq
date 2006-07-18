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

package org.hyperic.hq.appdef.shared;

import java.text.DateFormat;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.InvalidAppdefTypeException;

/**
 * Abstract base class for firt class appdef resource types.  This was 
 * carbon copied from Soltero's AppdefResourceValue equivalent because
 * 75% of it is the same.
 *
 * The accessors provided in this class represent what the UI model labels
 * "General Properties". Any other attribute is assumed to be specific
 * to the resource type.
 *
 *
 */
public abstract class AppdefResourceTypeValue {
    DateFormat dateFmt = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                                                        DateFormat.MEDIUM);
    // they all have id's
    public abstract Integer getId();
    public abstract void setId(Integer id);

    // they all have names
    public abstract String getName();
    public abstract void setName(String name);

    // they all have descriptions
    public abstract String getDescription();
    public abstract void setDescription(String desc);

    // they all have ctime 
    public abstract Long getCTime();

    // they all have mtime
    public abstract Long getMTime();

    /** used by the UI
     * 
     * @return formatted create time
     */
    public String getCreateTime()
    {
        Long ctime = getCTime();
        if ( ctime == null ) ctime = new Long(System.currentTimeMillis());
        return dateFmt.format(ctime);
    }
    
    /** used by the UI
     * 
     * @return formatted modified time
     */
    public String getModifiedTime()
    {
        Long mtime = getMTime();
        if ( mtime == null ) mtime = new Long(System.currentTimeMillis());
        return dateFmt.format(mtime);
    }
    
    /**
     * returns a stringified id in the form
     *  
     * [appdefType id]:[id]
     * 
     * @return a string based id
     */
    public String getAppdefTypeKey(){
        return this.getAppdefTypeId() + ":" + this.getId();
    }

    /** This method determines the appdef type based on an instanceof
     *  comparison of the class that extends us.
     * 
     * Note: This design uncovers the evil of xdoclet and its limitations.
     * Even super classes of lowly value objects should never have to
     * have knowledge of its descendents!
     * 
     * @return appdef int value designator of entity type.
     * @throws InvalidAppdefTypeException
     */
    public int getAppdefTypeId () throws InvalidAppdefTypeException {
        if (this instanceof
               org.hyperic.hq.appdef.shared.PlatformTypeValue)
            return AppdefEntityConstants.APPDEF_TYPE_PLATFORM;
        else if (this instanceof
               org.hyperic.hq.appdef.shared.ServerTypeValue)
            return AppdefEntityConstants.APPDEF_TYPE_SERVER;
        else if (this instanceof 
               org.hyperic.hq.appdef.shared.ServiceTypeValue)
            return AppdefEntityConstants.APPDEF_TYPE_SERVICE;
        else if (this instanceof
               org.hyperic.hq.appdef.shared.ApplicationTypeValue)
           return AppdefEntityConstants.APPDEF_TYPE_APPLICATION;
        else if (this instanceof
               org.hyperic.hq.appdef.shared.GroupTypeValue)
            return AppdefEntityConstants.APPDEF_TYPE_GROUP;
        else
            throw new
              InvalidAppdefTypeException("No appdef entity constant "+
                                         "defined for this class.");
    }

}
