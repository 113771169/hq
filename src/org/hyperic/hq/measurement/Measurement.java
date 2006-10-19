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
 * Measurement generated by hbm2java
 */
public class Measurement  implements java.io.Serializable {

    // Fields    

     private Integer id;
     private long _version_;
     private Integer instanceId;
     private MeasurementTemplate template;
     private Integer cid;
     private long mtime;
     private Integer appdefType;

     // Constructors

    /** default constructor */
    public Measurement() {
    }

	/** minimal constructor */
    public Measurement(Integer instanceId, long mtime) {
        this.instanceId = instanceId;
        this.mtime = mtime;
    }
    /** full constructor */
    public Measurement(Integer instanceId, MeasurementTemplate template, Integer cid, long mtime, Integer appdefType) {
        this.instanceId = instanceId;
        this.template = template;
        this.cid = cid;
        this.mtime = mtime;
        this.appdefType = appdefType;
    }
    
   
    // Property accessors
    public Integer getId() {
        return this.id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    public long get_version_() {
        return this._version_;
    }
    
    public void set_version_(long _version_) {
        this._version_ = _version_;
    }
    public Integer getInstanceId() {
        return this.instanceId;
    }
    
    public void setInstanceId(Integer instanceId) {
        this.instanceId = instanceId;
    }
    public MeasurementTemplate getTemplate() {
        return this.template;
    }
    
    public void setTemplate(MeasurementTemplate template) {
        this.template = template;
    }
    public Integer getCid() {
        return this.cid;
    }
    
    public void setCid(Integer cid) {
        this.cid = cid;
    }
    public long getMtime() {
        return this.mtime;
    }
    
    public void setMtime(long mtime) {
        this.mtime = mtime;
    }
    public Integer getAppdefType() {
        return this.appdefType;
    }
    
    public void setAppdefType(Integer appdefType) {
        this.appdefType = appdefType;
    }


   public boolean equals(Object other) {
         if ( (this == other ) ) return true;
		 if ( (other == null ) ) return false;
		 if ( !(other instanceof Measurement) ) return false;
		 Measurement castOther = ( Measurement ) other; 
         
		 return ( (this.getInstanceId()==castOther.getInstanceId()) || ( this.getInstanceId()!=null && castOther.getInstanceId()!=null && this.getInstanceId().equals(castOther.getInstanceId()) ) )
 && ( (this.getTemplate()==castOther.getTemplate()) || ( this.getTemplate()!=null && castOther.getTemplate()!=null && this.getTemplate().equals(castOther.getTemplate()) ) );
   }
   
   public int hashCode() {
         int result = 17;
         
         
         
         
         
         
         
         
         return result;
   }   


}


