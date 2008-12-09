/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc.
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

package org.hyperic.hq.bizapp.server.session;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.UpdateException;
import org.hyperic.hq.auth.shared.SessionException;
import org.hyperic.hq.auth.shared.SessionManager;
import org.hyperic.hq.auth.shared.SessionNotFoundException;
import org.hyperic.hq.auth.shared.SessionTimeoutException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerLocal;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.PermissionManager;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.authz.shared.ResourceManagerLocal;
import org.hyperic.hq.bizapp.shared.AuthzBossLocal;
import org.hyperic.hq.bizapp.shared.AuthzBossUtil;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.ui.server.session.DashboardManagerEJBImpl;
import org.hyperic.util.ConfigPropertyException;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;

/** 
 * The BizApp's interface to the Authz Subsystem
 *
 * @ejb:bean name="AuthzBoss"
 *      jndi-name="ejb/bizapp/AuthzBoss"
 *      local-jndi-name="LocalAuthzBoss"
 *      view-type="both"
 *      type="Stateless"
 * @ejb:transaction type="Required"
 */
public class AuthzBossEJBImpl extends BizappSessionEJB 
    implements SessionBean {

    private SessionManager manager    = SessionManager.getInstance();

    protected Log log = LogFactory.getLog(AuthzBossEJBImpl.class.getName());
    protected boolean debug = log.isDebugEnabled();

    public AuthzBossEJBImpl() {}

    /**
     * Check if the current logged in user can administer CAM
     * @return true - if user has adminsterCAM op false otherwise
     * @ejb:interface-method
     */
    public boolean hasAdminPermission(int sessionId)
        throws FinderException, 
               SessionTimeoutException, SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        PermissionManager pm = PermissionManagerFactory.getInstance();
        return pm.hasAdminPermission(subject.getId());
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>ResourceTypeValue</code> objects representing every
     * resource type in the system that the user is allowed to view.
     *
     * @ejb:interface-method
     */
    public List getAllResourceTypes(Integer sessionId, PageControl pc)
        throws CreateException, FinderException,
               PermissionException, SessionTimeoutException, 
               SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        return getResourceManager().getAllResourceTypes(subject, pc);
    }

    /**
     * Return the full <code>List</code> of
     * <code>ResourceTypeValue</code> objects representing every
     * resource type in the system that the user is allowed to view.
     *
     * @ejb:interface-method
     */
    public List getAllResourceTypes(Integer sessionId)
        throws CreateException, FinderException,
               PermissionException, SessionTimeoutException, 
               SessionNotFoundException {
        return getAllResourceTypes(sessionId, null);
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>OperationValue</code> objects representing every
     * resource type in the system that the user is allowed to view.
     *
     * @ejb:interface-method
     */
    public List getAllOperations(Integer sessionId, PageControl pc)
        throws FinderException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        PermissionManager pm = PermissionManagerFactory.getInstance();
        return pm.getAllOperations(subject, pc);
    }

    /**
     * Return the full <code>List</code> of
     * <code>OperationValue</code> objects representing every
     * resource type in the system that the user is allowed to view.
     *
     * @ejb:interface-method
     */
    public List getAllOperations(Integer sessionId)
        throws FinderException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {
        return getAllOperations(sessionId, null);
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>AuthzSubjectValue</code> objects representing every
     * resource type in the system that the user is allowed to view.
     *
     * @ejb:interface-method
     */
    public PageList getAllSubjects(Integer sessionId, Collection excludes,
                                   PageControl pc)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {
        AuthzSubject subject = manager.getSubject(sessionId);
        return getAuthzSubjectManager().getAllSubjects(subject, excludes, pc);
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>AuthzSubjectValue</code> objects corresponding to the specified
     * id values.
     * 
     * @ejb:interface-method
     */
    public PageList getSubjectsById(Integer sessionId, Integer[] ids,
                                    PageControl pc)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        return getAuthzSubjectManager().getSubjectsById(subject, ids, pc);
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>AuthzSubjectValue</code> objects matching name as substring
     *  
     * @ejb:interface-method
     */
    public PageList getSubjectsByName(Integer sessionId, String name,
                                      PageControl pc)
        throws PermissionException, SessionTimeoutException,
               SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        return getAuthzSubjectManager().findMatchingName(name, pc);
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>ResourceGroupValue</code> objects representing every
     * resource type in the system that the user is allowed to view.
     *
     * @ejb:interface-method
     */
    public List getAllResourceGroups(Integer sessionId, PageControl pc)
        throws FinderException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        return getResourceGroupManager().getAllResourceGroups(subject, pc);
    }

    /**
     * Return a sorted, paged <code>List</code> of
     * <code>ResourceGroupValue</code> objects corresponding to the
     * specified id values.
     *
     * @ejb:interface-method
     */
    public PageList getResourceGroupsById(Integer sessionId, Integer[] ids,
                                          PageControl pc)
        throws FinderException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {
        AuthzSubject subject = manager.getSubject(sessionId);
        return getResourceGroupManager().getResourceGroupsById(subject, ids,
                                                               pc);
    }
    
    /**
     * @ejb.interface-method
     */
    public Map findResourcesByIds(Integer sessionId, AppdefEntityID[] entities)
        throws SessionNotFoundException, SessionTimeoutException
    {
        // get the user
        AuthzSubject subject = manager.getSubject(sessionId);
        Map appdefMap = new LinkedHashMap();

        // cheaper to find the resource first
        ResourceManagerLocal resMan = getResourceManager();
        for (int i = 0; i < entities.length; i++) {
            Resource res = resMan.findResource(entities[i]);
            if (res != null) {
                try {
                    appdefMap.put(new AppdefEntityID(res), res);
                } catch (IllegalArgumentException e) {
                    // Not a valid appdef resource, continue
                }
            }
        }
        
        return appdefMap;
    }

    /**
     * Remove the user identified by the given ids from the subject as well 
     * as principal tables.
     *
     * @ejb:interface-method
     */
    public void removeSubject(Integer sessionId, Integer[] ids)
        throws FinderException, RemoveException, PermissionException,
               SessionTimeoutException, SessionNotFoundException {
        // check for timeout
        AuthzSubject whoami = manager.getSubject(sessionId);
        try {
            AuthzSubjectManagerLocal mgr = getAuthzSubjectManager();
            for (int i = 0; i < ids.length; i++) {
                AuthzSubject aSubject = findSubjectById(sessionId, ids[i]); 
                /* Note: This has not been finalized. At present, however,
                    the consensus is that a user should be able to be deleted
                    if they are logged in. Therefore, this fix may not be
                    needed ...  BUG-4169 - DSE
                if (isLoggedIn(username)) {
                    throw new RemoveException ("User is logged in");
                } 
                */

                // Verify that the user is not trying to delete themself.
                if (whoami.getName().equals(aSubject.getName())) {
                    throw new PermissionException(
                        "Users are not permitted to remove themselves.");
                }
                // reassign ownership of all things appdef
                getAppdefBoss().resetResourceOwnership(
                    sessionId.intValue(), aSubject);
                // reassign ownership of all things authz
                resetResourceOwnership(sessionId, aSubject);
                
                // delete in auth
                getAuthManager().deleteUser(whoami, aSubject.getName());
                
                // remove from authz
                mgr.removeSubject(whoami, ids[i]);
            }
        } catch (UpdateException e) {
            rollback();
            throw new RemoveException(
                "Unable to reset ownership of owned resources: " 
                + e.getMessage());   
        } catch (AppdefEntityNotFoundException e) {
            rollback();
            throw new RemoveException(
                "Unable to reset ownership of owned resources: "
                + e.getMessage());
        }
    }
    
    /**
     * Update all the authz resources owned by this user to be owned
     * by the root user. This is done to prevent resources from being
     * orphaned in the UI due to its display restrictions. This method
     * should only get called before a user is about to be deleted
     * @param subject- the user about to be removed
     * 
     */
    private void resetResourceOwnership(Integer sessionId,
                                        AuthzSubject currentOwner) 
        throws FinderException, UpdateException, PermissionException {
        // first look up the resources by owner
        Collection resources
            = getResourceManager().findResourceByOwner(currentOwner);
        for(Iterator it = resources.iterator(); it.hasNext(); ) {
            Resource aRes = (Resource) it.next();
            String resType = aRes.getResourceType().getName();    
            if(resType.equals(AuthzConstants.roleResourceTypeName)) {
                getResourceManager().setResourceOwner(getOverlord(), aRes,
                                                      getOverlord());
            }
        }
    }
                            
    /**
     * Update a subject
     *
     * @ejb:interface-method
     */
    public void updateSubject(Integer sessionId, AuthzSubject target,
                              Boolean active, String dsn, String dept,
                              String email, String first, String last,
                              String phone, String sms, Boolean useHtml)
        throws PermissionException, SessionException 
    {
        AuthzSubject whoami = manager.getSubject(sessionId);
        getAuthzSubjectManager().updateSubject(whoami, target, active, dsn,
                                               dept, email, first, last,
                                               phone, sms, useHtml);
    }
    
    /**
     * Create the user identified by the given ids from the subject as well 
     * as principal tables.
     *
     * @ejb:interface-method
     */
    public AuthzSubject createSubject(Integer sessionId, String name,
                                      boolean active, String dsn, String dept,
                                      String email, String first, String last,
                                      String phone, String sms,
                                      boolean useHtml) 
        throws PermissionException, CreateException, SessionException 
    {
        // check for timeout
        AuthzSubject whoami = manager.getSubject(sessionId);

        AuthzSubjectManagerLocal subjMan = getAuthzSubjectManager();
        return subjMan.createSubject(whoami, name, active, dsn, dept, email,
                                     first, last, phone, sms, useHtml);
    }

    /**
     * @ejb:interface-method
     */
    public AuthzSubject getCurrentSubject(int sessionid) 
        throws SessionException
    {
        return manager.getSubject(sessionid);
    }
    
    /**
     * @ejb:interface-method
     */
    public AuthzSubject getCurrentSubject(String name)
        throws SessionException, ApplicationException
    {
        int sessionId = getAuthManager().getUnauthSessionId(name);
        return getCurrentSubject(sessionId);
    }
    
    /**
     * Return the <code>AuthzSubject</code> object identified by
     * the given subject id.
     * @throws SessionTimeoutException 
     * @throws SessionNotFoundException 
     * @throws PermissionException 
     *
     * @ejb:interface-method
     */
    public AuthzSubject findSubjectById(Integer sessionId, Integer subjectId)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException {
        // check for timeout
        AuthzSubject subj = manager.getSubject(sessionId);
        return getAuthzSubjectManager().findSubjectById(subj, subjectId);
    }

    /**
     * Return the <code>AuthzSubject</code> object identified by
     * the given username.
     *
     * @ejb:interface-method
     */
    public AuthzSubject findSubjectByName(Integer sessionId, String subjectName)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {
        // check for timeout
        AuthzSubject subj = manager.getSubject(sessionId);
        return getAuthzSubjectManager().findSubjectByName(subj, subjectName);
    }

    /**
     * Return the <code>AuthzSubject</code> object identified by
     * the given username. This method should only be used in cases
     * where displaying the user does not require an Authz check. An
     * example of this is when the owner and last modifier need to 
     * be displayed, and the user viewing the resource does not 
     * have permissions to view other users.
     * See bug #5452 for more information
     * @ejb:interface-method
     */
    public AuthzSubject findSubjectByNameNoAuthz(Integer sessionId,
                                                 String subjectName)
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException, PermissionException {
        // check for timeout
        manager.authenticate(sessionId.intValue());
        return getAuthzSubjectManager().findSubjectByName(subjectName);
    }

    /**
     * Return a ConfigResponse matching the UserPreferences
     * @throws ApplicationException
     * @throws ConfigPropertyException
     * @throws LoginException
     * @ejb:interface-method
     */
    public ConfigResponse getUserPrefs(String username)
        throws SessionNotFoundException, ApplicationException,
               ConfigPropertyException {
        int sessionId = getAuthManager().getUnauthSessionId(username);
        AuthzSubject subject = manager.getSubject(sessionId);
        return getUserPrefs(new Integer(sessionId), subject.getId());
    }
    
    /**
     * Return a ConfigResponse matching the UserPreferences
     * @ejb:interface-method
     */
    public ConfigResponse getUserPrefs(Integer sessionId, Integer subjectId) {
        try {
            AuthzSubject who = manager.getSubject(sessionId);
            return getAuthzSubjectManager().getUserPrefs(who, subjectId);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    /**
     * Set the UserPreferences 
     * @ejb:interface-method
     */
    public void setUserPrefs(Integer sessionId, Integer subjectId,
                             ConfigResponse prefs)
        throws ApplicationException, SessionTimeoutException,
               SessionNotFoundException 
    {
        AuthzSubject who = manager.getSubject(sessionId);
        getAuthzSubjectManager().setUserPrefs(who, subjectId, prefs);
        getUserPrefs(sessionId, subjectId);
    }
    
    /**
     * Get the current user's dashboard
     * @ejb:interface-method
     */
    public ConfigResponse getUserDashboardConfig(Integer sessionId)
        throws SessionNotFoundException, SessionTimeoutException,
               PermissionException {
        AuthzSubject subj = manager.getSubject(sessionId);
        return DashboardManagerEJBImpl.getOne()
            .getUserDashboard(subj, subj).getConfig();
    }

    /**
     * Get the email of a user by name
     * @ejb:interface-method
     */
    public String getEmailByName(Integer sessionId, String userName) 
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException {
        manager.authenticate(sessionId.intValue());
        return getAuthzSubjectManager().getEmailByName(userName);
    }

    /**
     * Get the email of a user by id
     * @ejb:interface-method
     */
    public String getEmailById(Integer sessionId, Integer userId) 
        throws FinderException, SessionTimeoutException,
               SessionNotFoundException {
        manager.authenticate(sessionId.intValue());
        return getAuthzSubjectManager().getEmailById(userId);
    }
    
    public static AuthzBossLocal getOne() {
        try {
            return AuthzBossUtil.getLocalHome().create();
        } catch(Exception e) {
            throw new SystemException(e);
        }
    }

    /** @ejb:create-method */
    public void ejbCreate() {}
    public void ejbRemove() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
}
