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

package org.hyperic.hq.bizapp.server.mdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.events.AbstractEvent;
import org.hyperic.hq.events.LoggableInterface;
import org.hyperic.hq.events.server.session.EventLog;
import org.hyperic.hq.events.server.session.EventLogManagerEJBImpl;
import org.hyperic.hq.events.shared.EventLogManagerLocal;

/** 
 * The LoggingDispatcher Message-Drive Bean is intended to be used
 * to log Events
 *
 * @ejb:bean name="LoggingDispatcher"
 *      jndi-name="ejb/bizapp/LoggingDispatcher"
 *      local-jndi-name="LocalLoggingDispatcher"
 *      transaction-type="Container"
 *      acknowledge-mode="Auto-acknowledge"
 *      destination-type="javax.jms.Topic"
 *
 * @ejb:transaction type="Required"
 *
 * @jboss:destination-jndi-name name="topic/eventsTopic"
 */
public class LoggingDispatcherEJBImpl 
    implements MessageDrivenBean, MessageListener {
    private final Log log =
        LogFactory.getLog(LoggingDispatcherEJBImpl.class);

    /**
     * The onMessage method
     */
    public void onMessage(Message inMessage) {
        if (!(inMessage instanceof ObjectMessage)) {
            return;
        }

        ObjectMessage om = (ObjectMessage) inMessage;
            
        try {
            Object obj = om.getObject();
            
            if (obj instanceof AbstractEvent) {
                AbstractEvent event = (AbstractEvent) obj;
                logEvent(event);
            } else if (obj instanceof Collection) {
                Collection events = (Collection) obj;
                logEvents(events);
            }
        } catch (JMSException e) {
            log.error("Cannot open message object", e);
        }
        
    }
    
    /**
     * Log the event if it supports logging.
     * 
     * @param event The event.
     */
    private void logEvent(AbstractEvent event) {
        EventLogManagerLocal elMan = EventLogManagerEJBImpl.getOne();
        
        if (event.isLoggingSupported()) {
            LoggableInterface le = (LoggableInterface) event;            
            elMan.createLog(event, le.getSubject(), le.getLevelString(), true);
        }        
    }
    
    private void logEvents(Collection events) {
        EventLogManagerLocal elMan = EventLogManagerEJBImpl.getOne();
        List loggableEvents = new ArrayList();
        
        for (Iterator it = events.iterator(); it.hasNext();) {
            AbstractEvent event = (AbstractEvent) it.next();
            
            if (event.isLoggingSupported()) {
                LoggableInterface le = (LoggableInterface) event;
                EventLog eventLog = elMan.createLog(event, 
                                                    le.getSubject(), 
                                                    le.getLevelString(), 
                                                    false);
                loggableEvents.add(eventLog);
            }
        }
        
        if (!loggableEvents.isEmpty()) {
            elMan.insertEventLogs((EventLog[])loggableEvents.toArray(
                                       new EventLog[loggableEvents.size()]));
        }
    }

    /**
     * @see javax.ejb.MessageDrivenBean#ejbCreate()
     * @ejb:create-method
     */
    public void ejbCreate() {}

    /**
     * @see javax.ejb.MessageDrivenBean#ejbPostCreate()
     */
    public void ejbPostCreate() {}

    /**
     * @see javax.ejb.MessageDrivenBean#ejbActivate()
     */
    public void ejbActivate() {}

    /**
     * @see javax.ejb.MessageDrivenBean#ejbPassivate()
     */
    public void ejbPassivate() {}

    /**
     * @see javax.ejb.MessageDrivenBean#ejbRemove()
     * @ejb:remove-method
     */
    public void ejbRemove() {}

    public void setMessageDrivenContext(MessageDrivenContext ctx){}
}
