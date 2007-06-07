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
package org.hyperic.hq.escalation.server.session;

import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.events.server.session.Action;
import org.hyperic.util.HypericEnum;

/**
 * This is a dynamic enumeration.  Other subsystems wishing to specify their
 * own alert type into the escalation manager must create a subclass of
 * this enumeration with a unique code.
 */
public abstract class EscalationAlertType 
    extends HypericEnum 
{
    protected EscalationAlertType(int code, String desc) {
        super(EscalationAlertType.class, code, desc);
    }
    
    public static EscalationAlertType findByCode(int code) {
        return (EscalationAlertType)findByCode(EscalationAlertType.class, 
                                               code);
    }

    /**
     * Find an escalatable (alert) given its ID.
     */
    protected abstract Escalatable findEscalatable(Integer alertId);
    
    /**
     * Find an alert definition (or something that performs escalations)
     * given its id.
     */
    protected abstract PerformsEscalations findDefinition(Integer defId);
    
    /**
     * Set the escalation for something which can .. have escalations.. ;-)
     *
     * @param defId ID of the definition to set the escalation for
     */
    protected abstract void setEscalation(Integer defId, Escalation escalation);                                          
    
    /**
     * Change the state of an alert.  This method should simply change the 
     * state, log it & not much else.
     *  
     * @param alertId  AlertID to change state of
     * @param who      Person changing the state
     * @param newState New state
     */
    protected abstract void changeAlertState(Integer alertId, AuthzSubject who,
                                             EscalationStateChange newState); 

    /**
     * Log the result of the execution of an action.  The escalation system
     * executes actions as part of the escalation chain.  Each action spits
     * out some result text.  This method should put that result text into
     * the subsytem's log objects.
     */
    protected abstract void logActionDetails(Integer alertId, Action action,
                                             String detail, 
                                             AuthzSubject subject);
    
    /**
     * Return the note from the last alert instance where the alert was fixed.
     */
    protected abstract String getLastFixedNote(PerformsEscalations def);
}
