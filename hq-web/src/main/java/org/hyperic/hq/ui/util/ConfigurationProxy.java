package org.hyperic.hq.ui.util;

import java.rmi.RemoteException;

import javax.servlet.http.HttpSession;

import org.hyperic.hq.auth.shared.SessionNotFoundException;
import org.hyperic.hq.auth.shared.SessionTimeoutException;
import org.hyperic.hq.authz.server.session.Role;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.util.config.ConfigResponse;

public interface ConfigurationProxy {

    void setPreference(HttpSession session, WebUser user, String key, String value) throws ApplicationException,
        RemoteException;

    void setUserDashboardPreferences(ConfigResponse userPrefs, WebUser user) throws ApplicationException,
        RemoteException;

    void setDashboardPreferences(HttpSession session, WebUser user, ConfigResponse dashConfigResp)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException, RemoteException;

    void setRoleDashboardPreferences(ConfigResponse preferences, WebUser user, Role role) throws ApplicationException,
        RemoteException;

}
