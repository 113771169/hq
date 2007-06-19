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

package org.hyperic.hq.application;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.transaction.Status;
import javax.transaction.Synchronization;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Transaction;
import org.hyperic.hibernate.Util;
import org.hyperic.hq.hibernate.SessionManager;
import org.hyperic.txsnatch.TxSnatch;
import org.hyperic.util.callback.CallbackDispatcher;
import org.jboss.ejb.Interceptor;
import org.jboss.invocation.Invocation;


/**
 * This class represents the central concept of the Hyperic HQ application.  
 * (not the Application resource)
 */
public class HQApp { 
    private static final HQApp INSTANCE = new HQApp(); 
    private static final Log _log = LogFactory.getLog(HQApp.class);
    
    private ThreadLocal        _txListeners    = new ThreadLocal();
    private List               _startupClasses = new ArrayList();
    private CallbackDispatcher _callbacks;
    private ShutdownCallback   _shutdown;
    private File               _restartStorage;
    
    static {
        TxSnatch.setSnatcher(new Snatcher());
    }
    
    private HQApp() {
        _callbacks = new CallbackDispatcher();
        _shutdown = (ShutdownCallback)
            _callbacks.generateCaller(ShutdownCallback.class);
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                _log.info("Running shutdown hooks");
                _shutdown.shutdown();
                _log.info("Done running shutdown hooks");
            }
        });
    }

    public void setRestartStorageDir(File dir) {
        synchronized (_startupClasses) {
            _restartStorage = dir;
        } 
    }
    
    /**
     * Get a directory which can have files placed into it which will carry
     * over for a restart.  This should not be used to place files for
     * extensive periods of time.
     */
    public File getRestartStorageDir() {
        synchronized (_startupClasses) {
            return _restartStorage;
        } 
    }
    
    /**
     * @see CallbackDispatcher#generateCaller(Class)
     */
    public Object registerCallbackCaller(Class iFace) {
        return _callbacks.generateCaller(iFace);
    }
    
    /**
     * @see CallbackDispatcher#registerListener(Class, Object)
     */
    public void registerCallbackListener(Class iFace, Object listener) {
        _callbacks.registerListener(iFace, listener);
    }
    
    /**
     * Adds a class to the list of classes to invoke when the application has
     * started.
     */
    public void addStartupClass(String className) {
        synchronized (_startupClasses) {
            _startupClasses.add(className);
        }
    }
    
    private static class Snatcher implements TxSnatch.Snatcher  {
        private Object invokeNextBoth(Interceptor next, Invocation v,
                                      boolean isHome) 
            throws Exception 
        {
            Method meth       = v.getMethod();
            String methName   = meth.getName();
            Class c           = meth.getDeclaringClass();
            String className  = c.getName();
            boolean created   = false;
            boolean readWrite = false;
            
            created = SessionManager.setupSession(methName);
                                                  
            try {
                if (!(methName.startsWith("get") ||
                      methName.startsWith("find") ||
                      methName.startsWith("is") ||
                      methName.startsWith("check") ||
                      methName.equals("create")))
                {
                    if (_log.isDebugEnabled()) {
                        _log.debug("Upgrading session, due to [" + methName + 
                                   "] on [" + className + "]");
                    }
                    readWrite = true;
                    SessionManager.setSessionReadWrite();
                }
                if (isHome)
                    return next.invokeHome(v);
                else
                    return next.invoke(v);
            } finally { 
                if (created) {
                    if (!readWrite && _log.isDebugEnabled()) {
                        _log.debug("Successfully ran read-only transaction " + 
                                   "for [" + methName + "] on [" + 
                                   className + "]");
                    }
                    SessionManager.cleanupSession();
                }
            }
        }

        public Object invokeProxyNext(org.jboss.proxy.Interceptor next, 
                                      Invocation v) 
            throws Throwable 
        {
            return next.invoke(v);
        }

        public Object invokeNext(Interceptor next, Invocation v) 
            throws Exception 
        {
            return invokeNextBoth(next, v, false);
        }
        
        public Object invokeHomeNext(Interceptor next, Invocation v) 
            throws Exception 
        {
            return invokeNextBoth(next, v, true);
        }
    }
    
    /**
     * Execute the registered startup classes.
     */
    public void runStartupClasses() {
        List classNames;
        
        synchronized (_startupClasses) {
            classNames = new ArrayList(_startupClasses);
        }
        
        for (Iterator i=classNames.iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            
            try {
                Class c = Class.forName(name);
                StartupListener l = (StartupListener)c.newInstance();
     
                _log.info("Executing startup: " + name);
                l.hqStarted();
            } catch(Exception e) {
                _log.warn("Error executing startup listener [" + name + "]", e);
            }
        }
    }
    
    private void scheduleCommitCallback() {
        Transaction t = 
            Util.getSessionFactory().getCurrentSession().getTransaction();

        t.registerSynchronization(new Synchronization() {
            public void afterCompletion(int status) {
                runPostCommitListeners(status == Status.STATUS_COMMITTED);
            }

            public void beforeCompletion() {
            }
        });
    }
    
    /**
     * Register a listener to be called after a tx has been committed.
     */
    public void addTransactionListener(TransactionListener listener) {
        List listeners = (List)_txListeners.get();
        
        if (listeners == null) {
            listeners = new ArrayList(1);
            _txListeners.set(listeners);
            scheduleCommitCallback();
        }
        
        listeners.add(listener);
        
        // Unfortunately, it seems that the Tx synchronization will get called
        // before Hibernate does its flush.  This wasn't the behaviour before,
        // and looks like it will be fixed up again in 3.3.. :-(
        Util.getSessionFactory().getCurrentSession().flush();
    }

    /**
     * Execute all the post-commit listeners registered with the current thread
     */
    private void runPostCommitListeners(boolean success) {
        List list = (List)_txListeners.get();
        
        if (list == null)
            return;
        
        try {
            for (Iterator i=list.iterator(); i.hasNext(); ) {
                TransactionListener l = (TransactionListener)i.next();
            
                try {
                    l.afterCommit(success);
                } catch(Exception e) {
                    _log.warn("Error running commit listener [" + l + "]", e);
                }
            } 
        } finally {
            _txListeners.set(null);
        }
    }
    
    public static HQApp getInstance() {
        return INSTANCE;
    }
}
