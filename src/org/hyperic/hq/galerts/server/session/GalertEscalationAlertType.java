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
package org.hyperic.hq.galerts.server.session;

import java.util.Iterator;

import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.escalation.server.session.Escalatable;
import org.hyperic.hq.escalation.server.session.Escalation;
import org.hyperic.hq.escalation.server.session.EscalationAlertType;
import org.hyperic.hq.escalation.server.session.EscalationStateChange;
import org.hyperic.hq.escalation.server.session.PerformsEscalations;
import org.hyperic.hq.events.server.session.Action;
import org.hyperic.hq.galerts.shared.GalertManagerLocal;

public final class GalertEscalationAlertType 
    extends EscalationAlertType
{
    public static final GalertEscalationAlertType GALERT = 
        new GalertEscalationAlertType(0xbadbabe, "Group Alert");

    private static Object INIT_LOCK = new Object();
    private static GalertManagerLocal _alertMan;
    
    private GalertManagerLocal getGalertMan() {
        synchronized (INIT_LOCK) {
            if (_alertMan == null) {
                _alertMan = GalertManagerEJBImpl.getOne();
            }
        }
        return _alertMan;
    }
    
    public Escalatable findEscalatable(Integer id) {
        return getGalertMan().findEscalatableAlert(id);
    }

    public PerformsEscalations findDefinition(Integer defId) {
        return getGalertMan().findById(defId);
    }

    protected void setEscalation(Integer defId, Escalation escalation) {
        getGalertMan().findById(defId).setEscalation(escalation);
    }

    protected void changeAlertState(Integer alertId, AuthzSubject who, 
                                    EscalationStateChange newState) 
    {
        GalertManagerLocal gMan = getGalertMan();
        GalertLog alert = gMan.findAlertLog(alertId);

        if (newState.isFixed()) 
            gMan.fixAlert(alert);
    }

    protected void logActionDetails(Integer alertId, Action action, 
                                    String detail, AuthzSubject subject) 
    {
        GalertLog alert = getGalertMan().findAlertLog(alertId);
        
        getGalertMan().createActionLog(alert, detail, action, subject);
    }

    private GalertEscalationAlertType(int code, String desc) {
        super(code, desc);
    }

    protected String getLastFixedNote(PerformsEscalations def) {
        GalertLog alert = getGalertMan().findLastFixedByDef((GalertDef) def);
        if (alert != null) {
            long lastlog = 0;
            String fixedNote = null;
            for (Iterator it = alert.getActionLog().iterator(); it.hasNext(); )
            {
                GalertActionLog log = (GalertActionLog) it.next();
                if (log.getAction() == null && log.getTimeStamp() > lastlog) {
                    fixedNote = log.getDetail();
                }
            }
            return fixedNote;
        }
        return null;
    }
}
