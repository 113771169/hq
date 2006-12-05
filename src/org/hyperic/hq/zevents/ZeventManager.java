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

package org.hyperic.hq.zevents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.application.TransactionListener;
import org.hyperic.hq.common.DiagnosticObject;
import org.hyperic.hq.common.DiagnosticThread;

import edu.emory.mathcs.backport.java.util.concurrent.BlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue;

/**
 * The Zevent subsystem is an event system for fast, non-reliable transmission
 * of events.  Important data should not be transmitted on this bus, since
 * it is not persisted, and there is never any guarantee of receipt.  

 * This manager provides no transactional guarantees, so the caller must 
 * rollback additions of listeners if the transaction fails. 
 */
public class ZeventManager { 
    private static final int MAX_QUEUE_ENTRIES = 100 * 1000;
    private static final long WARN_INTERVAL = 5 * 60 * 1000;
    private static final int QUEUE_WARN_SIZE = MAX_QUEUE_ENTRIES * 90 / 100;
    
    private final Log _log = LogFactory.getLog(ZeventManager.class);
    
    private static final Object INIT_LOCK = new Object();
    private static ZeventManager INSTANCE;

    // The thread group that the {@link EventQueueProcessor} comes from 
    private final ProcessorThreadGroup _threadGroup;

    // The actual queue processor thread
    private Thread _processorThread;
    
    private final Object _listenerLock = new Object();
    
    /* Set of {@link ZeventListener}s listening to events of all types */
    private final Set _globalListeners = new HashSet(); 

    /* Map of {@link Class}es subclassing {@link ZEvent} onto lists of 
     * {@link ZeventListener}s */
    private final Map _listeners = new HashMap();
    
    // For diagnostics and warnings
    private long _lastWarnTime;
    private long _maxTimeInQueue;
    private long _numEvents;
    
    private QueueProcessor _queueProcessor;
    private BlockingQueue  _eventQueue = 
        new LinkedBlockingQueue(MAX_QUEUE_ENTRIES);

    
    private ZeventManager() {
        _threadGroup = new ProcessorThreadGroup();
        _threadGroup.setDaemon(true);
    }
    
    public void shutdown() throws InterruptedException {
        while (!_eventQueue.isEmpty()) {
            System.out.println("Waiting for empty queue: " + _eventQueue.size());
            Thread.sleep(1000);
        }
        _processorThread.interrupt();
        _processorThread.join(5000);
    }
    
    private void assertClassIsZevent(Class c) {
        if (!Zevent.class.isAssignableFrom(c)) {
            throw new IllegalArgumentException("[" + c.getName() + 
                                               "] does not subclass [" +
                                               Zevent.class.getName() + "]");
        }
    }
    
    /**
     * Register an event class.  These classes must be registered prior to
     * attempting to listen to individual event types.
     * 
     * @param eventClass a subclass of {@link Zevent}
     * @return false if the eventClass was already registered
     */
    public boolean registerEventClass(Class eventClass) {
        assertClassIsZevent(eventClass);
     
        synchronized (_listenerLock) {
            if (_listeners.containsKey(eventClass))
                return false;
            
            _listeners.put(eventClass, new ArrayList());
            return true;
        }
    }
    
    /**
     * Unregister an event class
     * 
     * @param eventClass subclass of {@link Zevent}
     * @return false if the eventClass was not registered
     */
    public boolean unregisterEventClass(Class eventClass) {
        assertClassIsZevent(eventClass);
        
        synchronized (_listenerLock) {
            return _listeners.remove(eventClass) != null;
        }
    }
    
    /**
     * Add an event listener which is called for every event type which
     * comes through the queue.
     *
     * @return false if the listener was already listening
     */
    public boolean addGlobalListener(ZeventListener listener) {
        synchronized (_listeners) {
            return _globalListeners.add(listener);
        }
    }
    
    /**
     * Remove a global event listener
     * 
     * @return false if the listener was not listening
     */
    public boolean removeGlobalListener(ZeventListener listener) {
        synchronized (_listenerLock) {
            return _globalListeners.remove(listener);
        }
    }
    
    private List getEventTypeListeners(Class eventClass) {
        synchronized (_listenerLock) {
            List res = (List)_listeners.get(eventClass);
            
            if (res == null)
                throw new IllegalArgumentException("Event type [" + 
                                                   eventClass.getName() + 
                                                   "] not registered");
            return res;
        }
    }
    
    /**
     * Add a listener for a specific type of event.
     * 
     * @param eventClass A subclass of {@link Zevent}
     * @return false if the listener was already registered
     */
    public boolean addListener(Class eventClass, ZeventListener listener) {
        assertClassIsZevent(eventClass);

        synchronized (_listenerLock) {
            List listeners = getEventTypeListeners(eventClass);

            if (listeners.contains(listener))
                return false;
            listeners.add(listener);
            return true;
        }
    }

    /**
     * Remove a specific event type listener.
     * @see #addListener(Class, ZeventListener)
     */
    public boolean removeListener(Class eventClass, ZeventListener listener) {
        assertClassIsZevent(eventClass);
        
        synchronized (_listenerLock) {
            List listeners = getEventTypeListeners(eventClass);
            
            return listeners.remove(listener);
        }
    }
    
    /**
     * Enqueue events onto the event queue.  This method will block if the
     * thread is full.
     *  
     * @param events List of {@link Zevent}s 
     * @throws InterruptedException if the queue was full and the thread was
     *                              interrupted
     */
    public void enqueueEvents(List events) throws InterruptedException {
        if (_eventQueue.size() > QUEUE_WARN_SIZE && 
            (System.currentTimeMillis() - _lastWarnTime) > WARN_INTERVAL)
        {
            _lastWarnTime = System.currentTimeMillis();
            _log.warn("Your event queue is having a hard time keeping up.  " +
                      "Get a faster CPU, or reduce the amount of events!");
        }
                        
        for (Iterator i=events.iterator(); i.hasNext(); ) {
            Zevent e = (Zevent)i.next();  

            e.enterQueue();
            _eventQueue.put(e);
        }
    }
    
    public void enqueueEventAfterCommit(Zevent event) {
        enqueueEventsAfterCommit(Collections.singletonList(event));
    }
    
    /**
     * Enqueue events if the current running transaction successfully commits.
     * @see #enqueueEvents(List)
     */
    public void enqueueEventsAfterCommit(final List events) {
        HQApp.getInstance().addTransactionListener(new TransactionListener() {
            public void afterCommit(boolean success) {
                try {
                    if (success)
                        enqueueEvents(events);
                } catch(InterruptedException e) {
                    _log.warn("Interrupted while enqueueing events");
                }
            }
        });
    }
    
    public void enqueueEvent(Zevent event) throws InterruptedException {
        enqueueEvents(Collections.singletonList(event));
    }

    /**
     * Wait until the queue is empty.  This is a non-performant function, so
     * please only use it in test suites. 
     */
    public void waitUntilNoEvents() throws InterruptedException {
        while (_eventQueue.size() != 0)
            Thread.sleep(100);
    }
    
    /**
     * Internal method to dispatch events.  Called by the 
     * {@link EventQueueProcessor}
     */
    void dispatchEvent(Zevent e) {
        long timeInQueue = e.getQueueExitTime() - e.getQueueEntryTime();
        List listeners;

        synchronized (INIT_LOCK) {
            if (timeInQueue > _maxTimeInQueue)
                _maxTimeInQueue = timeInQueue;
            _numEvents++;
        }
        
        synchronized (_listenerLock) {
            List typeListeners = (List)_listeners.get(e.getClass());

            if (typeListeners == null) {
                _log.warn("Unable to dispatch event of type [" + 
                          e.getClass().getName() + "]:  Not registered");
                return;
            }
            listeners = new ArrayList(typeListeners.size() + 
                                      _globalListeners.size());
            listeners.addAll(typeListeners);
            listeners.addAll(_globalListeners);
        }
            
        /* Eventually we may want to de-queue a bunch at a time (if they are
         * the same event type, and pass those lists off all at once */
        for (Iterator i=listeners.iterator(); i.hasNext(); ) {
            ZeventListener listener = (ZeventListener)i.next();

            listener.processEvents(Collections.singletonList(e));
        }
    }

    private String getDiagnostics() {
        synchronized (INIT_LOCK) {
            return "ZEvent Manager Diagnostics:\n" +   
                       "    Queue Size:        " + _eventQueue.size() + "\n" +
                       "    Events Handled:    " + _numEvents + "\n" + 
                       "    Max Time In Queue: " + _maxTimeInQueue + "ms";
        }
    }
    
    public static ZeventManager getInstance() {
        synchronized (INIT_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new ZeventManager();
                QueueProcessor p = new QueueProcessor(INSTANCE, 
                                                      INSTANCE._eventQueue);
                INSTANCE._queueProcessor = p;
                INSTANCE._processorThread = new Thread(INSTANCE._threadGroup, 
                                                       p, "ZeventProcessor");
                INSTANCE._processorThread.setDaemon(true);
                INSTANCE._processorThread.start();

                DiagnosticObject myDiag = new DiagnosticObject() {
                    public String getStatus() {
                        return ZeventManager.getInstance().getDiagnostics();
                    }
                    
                    public String toString() {
                        return "ZEvent Subsystem";
                    }
                };
                
                DiagnosticThread.addDiagnosticObject(myDiag);
            }
        }
        return INSTANCE;
    }
}
