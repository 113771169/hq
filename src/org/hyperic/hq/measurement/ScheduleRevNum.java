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

package org.hyperic.hq.measurement;
// Generated Oct 19, 2006 11:49:50 AM by Hibernate Tools 3.1.0.beta4



/**
 * ScheduleRevNum generated by hbm2java
 */
public class ScheduleRevNum  implements java.io.Serializable {

    // Fields    

     private SrnId id;
     private long _version_;
     private Integer srn;
     private long minInterval;
     private long lastReported;
     private boolean pending;

     // Constructors

    /** default constructor */
    public ScheduleRevNum() {
    }

	/** minimal constructor */
    public ScheduleRevNum(SrnId id, Integer srn) {
        this.id = id;
        this.srn = srn;
    }
    /** full constructor */
    public ScheduleRevNum(SrnId id, Integer srn, long minInterval, long lastReported, boolean pending) {
        this.id = id;
        this.srn = srn;
        this.minInterval = minInterval;
        this.lastReported = lastReported;
        this.pending = pending;
    }
    
   
    // Property accessors
    public SrnId getId() {
        return this.id;
    }
    
    public void setId(SrnId id) {
        this.id = id;
    }
    public long get_version_() {
        return this._version_;
    }
    
    public void set_version_(long _version_) {
        this._version_ = _version_;
    }
    public Integer getSrn() {
        return this.srn;
    }
    
    public void setSrn(Integer srn) {
        this.srn = srn;
    }
    public long getMinInterval() {
        return this.minInterval;
    }
    
    public void setMinInterval(long minInterval) {
        this.minInterval = minInterval;
    }
    public long getLastReported() {
        return this.lastReported;
    }
    
    public void setLastReported(long lastReported) {
        this.lastReported = lastReported;
    }
    public boolean isPending() {
        return this.pending;
    }
    
    public void setPending(boolean pending) {
        this.pending = pending;
    }


   public boolean equals(Object other) {
         if ( (this == other ) ) return true;
		 if ( (other == null ) ) return false;
		 if ( !(other instanceof ScheduleRevNum) ) return false;
		 ScheduleRevNum castOther = ( ScheduleRevNum ) other; 
         
		 return ( (this.getId()==castOther.getId()) || ( this.getId()!=null && castOther.getId()!=null && this.getId().equals(castOther.getId()) ) );
   }
   
   public int hashCode() {
         int result = 17;
         
         result = 37 * result + ( getId() == null ? 0 : this.getId().hashCode() );
         
         
         
         
         
         return result;
   }   


}


