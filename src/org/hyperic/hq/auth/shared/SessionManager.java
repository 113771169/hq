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

package org.hyperic.hq.auth.shared;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Iterator;

import org.hyperic.hq.authz.shared.AuthzSubjectValue;

public class SessionManager {

    /**
     * Our source for random id's
     */
    private static Random _random = new Random();
    
    /**
     * Our session cache
     */
    private static Map _cache = new HashMap();

    /**
     * The singleton instance
     */
    private static SessionManager _manager = new SessionManager();

    /**
     * The timeout for all user sessions.  
     */
    static private final long DEFAULT_TIMEOUT = 90 * 1000 * 60;

    /**
     * Default private constructor
     */
    private SessionManager() {}

    /**
     * @return The singleton instance of the SessionManager
     */
    public static SessionManager getInstance() {
        return _manager;
    }

    /**
     * Associates a userid with a session id.  Uses default timeout.
     *
     * @param subject The AuthzSubjectValue to store
     * @return The session id
     */
    public synchronized int put(AuthzSubjectValue subject) {

        return this.put(subject, DEFAULT_TIMEOUT);
    }

    /**
     * Associates a userid with a session id
     *
     * @param subject The AuthzSubjectValue to store
     * @param timeout The timeout for the session in milliseconds
     * @return The session id
     */
    public synchronized int put(AuthzSubjectValue subject, long timeout) {

        Integer key = null;

        do {
            key = new Integer(_random.nextInt());
        } while (_cache.containsKey(key));
        
        _cache.put(key, new AuthSession(subject, timeout));
        
        return key.intValue();
    }

    /**
     * Lookup and return sessionId given their username.
     *
     * @param username The username.
     * @return sessionId 
     */
    public synchronized int getIdFromUsername (String username) 
        throws SessionNotFoundException, SessionTimeoutException
    {
        int sessionId = -1;

        try {
            Iterator i = _cache.keySet().iterator();
            
            //iterate existing sessions look for matching username
            while (i.hasNext()) {
                
                Integer sessKey = (Integer) i.next();
                
                AuthSession session = (AuthSession)
                    _cache.get(sessKey);
                
                // If found...
                if (session.getAuthzSubjectValue().getName().equals(username)) {
                    
                    // check expiration...
                    if (session.isExpired()) {
                        invalidate(sessionId);
                        throw new SessionTimeoutException();
                    } 
                    else 
                        return sessKey.intValue();  // short circuit for efficiency.
                }
            } // end while
        } catch (NullPointerException e) {
            // this shouldn't ever happen since their will always be atleast
            // one session (belonging to authzsubject) in cache.
        }

        // If the session not found, then throw exception.
        if (sessionId < 0) {
            throw new SessionNotFoundException();
        }
        return sessionId;
    }

    /**
     * Returns a userid given a session id
     *
     * @param sessionId The session id
     * @return The user id
     */
    public synchronized Integer getId(int sessionId) 
        throws SessionNotFoundException, SessionTimeoutException
    {
        
        Integer id = new Integer(sessionId);
        
        AuthSession session = (AuthSession)_cache.get(id);
        
        if (session == null) {
            throw new SessionNotFoundException();
        }

        if (session.isExpired()) {
            // XXX: check for other stale sessions?
            invalidate(sessionId);

            throw new SessionTimeoutException();
        }

        return session.getAuthzSubjectValue().getId();
    }

    /**
     * Returns the AuthzSubjectValue given a session id
     * 
     * @param sessionId The session id
     * @return The AuthzSubjectValue associated with the session id
     */
    public synchronized AuthzSubjectValue getSubject(int sessionId) 
        throws SessionNotFoundException, SessionTimeoutException
    {
        
        Integer id = new Integer(sessionId);

        AuthSession session = (AuthSession)_cache.get(id);
        
        if (session == null) {
            throw new SessionNotFoundException();
        }

        if (session.isExpired()) {
            invalidate(sessionId);

            throw new SessionTimeoutException();
        }

        return session.getAuthzSubjectValue();
    }

    /**
     * Simply perform an authentication when you don't need the actual subject
     */
    public void authenticate(int sessionId)
        throws SessionException
    {
        getSubject(sessionId);
    }
    
    /**
     * Remove the indicated session
     *
     * @param sessionId The session id
     */
    public synchronized void invalidate(int sessionId) {
        // XXX: check for other stale sessions?
        _cache.remove(new Integer(sessionId));
    }
}
