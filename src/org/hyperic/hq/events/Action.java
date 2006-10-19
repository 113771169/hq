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

package org.hyperic.hq.events;
// Generated Oct 17, 2006 12:40:43 PM by Hibernate Tools 3.1.0.beta4


import java.util.Collection;

/**
 * Action generated by hbm2java
 */
public class Action  implements java.io.Serializable {

    // Fields    

     private Integer id;
     private long _version_;
     private String className;
     private byte[] config;
     private Integer parentId;
     private AlertDefinition alertDefinitionId;
     private Collection alertActions;

     // Constructors

    /** default constructor */
    public Action() {
    }

	/** minimal constructor */
    public Action(String className) {
        this.className = className;
    }
    /** full constructor */
    public Action(String className, byte[] config, Integer parentId, AlertDefinition alertDefinitionId, Collection alertActions) {
        this.className = className;
        this.config = config;
        this.parentId = parentId;
        this.alertDefinitionId = alertDefinitionId;
        this.alertActions = alertActions;
    }
    
   
    // Property accessors
    public Integer getId() {
        return this.id;
    }
    
    private void setId(Integer id) {
        this.id = id;
    }
    public long get_version_() {
        return this._version_;
    }
    
    public void set_version_(long _version_) {
        this._version_ = _version_;
    }
    public String getClassName() {
        return this.className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    public byte[] getConfig() {
        return this.config;
    }
    
    public void setConfig(byte[] config) {
        this.config = config;
    }
    public Integer getParentId() {
        return this.parentId;
    }
    
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
    public AlertDefinition getAlertDefinitionId() {
        return this.alertDefinitionId;
    }
    
    public void setAlertDefinitionId(AlertDefinition alertDefinitionId) {
        this.alertDefinitionId = alertDefinitionId;
    }
    public Collection getAlertActions() {
        return this.alertActions;
    }
    
    public void setAlertActions(Collection alertActions) {
        this.alertActions = alertActions;
    }




}


