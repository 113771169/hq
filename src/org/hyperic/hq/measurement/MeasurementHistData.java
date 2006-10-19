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


import java.math.BigDecimal;

/**
 * MeasurementHistData generated by hbm2java
 */
public class MeasurementHistData  implements java.io.Serializable {

    // Fields    

     private MeasurementDataId id;
     private BigDecimal value;
     private BigDecimal minValue;
     private BigDecimal maxValue;

     // Constructors

    /** default constructor */
    public MeasurementHistData() {
    }

	/** minimal constructor */
    public MeasurementHistData(MeasurementDataId id) {
        this.id = id;
    }
    /** full constructor */
    public MeasurementHistData(MeasurementDataId id, BigDecimal value, BigDecimal minValue, BigDecimal maxValue) {
        this.id = id;
        this.value = value;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
    
   
    // Property accessors
    public MeasurementDataId getId() {
        return this.id;
    }
    
    public void setId(MeasurementDataId id) {
        this.id = id;
    }
    public BigDecimal getValue() {
        return this.value;
    }
    
    public void setValue(BigDecimal value) {
        this.value = value;
    }
    public BigDecimal getMinValue() {
        return this.minValue;
    }
    
    public void setMinValue(BigDecimal minValue) {
        this.minValue = minValue;
    }
    public BigDecimal getMaxValue() {
        return this.maxValue;
    }
    
    public void setMaxValue(BigDecimal maxValue) {
        this.maxValue = maxValue;
    }


   public boolean equals(Object other) {
         if ( (this == other ) ) return true;
		 if ( (other == null ) ) return false;
		 if ( !(other instanceof MeasurementHistData) ) return false;
		 MeasurementHistData castOther = ( MeasurementHistData ) other; 
         
		 return ( (this.getId()==castOther.getId()) || ( this.getId()!=null && castOther.getId()!=null && this.getId().equals(castOther.getId()) ) );
   }
   
   public int hashCode() {
         int result = 17;
         
         result = 37 * result + ( getId() == null ? 0 : this.getId().hashCode() );
         
         
         
         return result;
   }   


}


