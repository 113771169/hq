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
package org.hyperic.hq.ui.servlet;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.auth.shared.SessionException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.bizapp.server.session.AuthzBossEJBImpl;
import org.hyperic.hq.hqu.rendit.RenditServer;
import org.hyperic.hq.ui.util.RequestUtils;
import org.hyperic.util.StringUtil;
import org.hyperic.util.file.DirWatcher;
import org.hyperic.util.file.DirWatcher.DirWatcherCallback;
import org.jboss.system.server.ServerConfigLocator;

public class RenditServlet 
    extends HttpServlet
{
    private static final Log _log = LogFactory.getLog(RenditServlet.class);
    private static final Object INIT_LOCK = new Object();
    private static boolean INITIALIZED;
    
    private static DirWatcher _watcher;
    private static Thread     _watcherThread;

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
        throws ServletException, IOException 
    {
        handleRequest(req, resp);
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
        throws ServletException, IOException 
    {
        handleRequest(req, resp);
    }
    
    protected void handleRequest(HttpServletRequest req, 
                                 HttpServletResponse resp)
        throws ServletException, IOException
    {
        String reqUri = req.getRequestURI();
        
        initPlugins();

        List fullPath = StringUtil.explode(reqUri, "/");
        if (fullPath.size() < 2 || !fullPath.get(0).equals("hqu")) {
            throw new ServletException("Illegal request path");
        }
        
        List subPath  = fullPath.subList(1, fullPath.size());
        
        String plugin = (String)subPath.get(0);
        _log.info("Request for [" + plugin + "]: " + req.getRequestURI() + 
                  (req.getQueryString() == null ? "" 
                                               : ("?" + req.getQueryString())));

        int sessId = RequestUtils.getSessionIdInt(req);
        AuthzSubject user;
        
        try {
            user = AuthzBossEJBImpl.getOne().getCurrentSubject(sessId);
        } catch(SessionException e) {
            // Could not get the current user.  We should default to a 'nobody'
            // user here.
            _log.warn("Unable to get current user.  Bailing", e);
            throw new ServletException(e);
        }

        try {
            RenditServer.getInstance().handleRequest(plugin, req, resp,
                                                     getServletContext(), user);
        } catch(Exception e) {
            throw new ServletException(e);
        }
    }
    
    public void init() throws ServletException {
        super.init();
        initPlugins();
    }

    private void initPlugins() {
        synchronized(INIT_LOCK) {
            if (INITIALIZED)
                return;
        
            File homeDir   = ServerConfigLocator.locate().getServerHomeDir();
            File deployDir = new File(homeDir, "deploy");
            File earDir    = new File(deployDir, "hq.ear");
            File warDir    = new File(earDir, "hq.war");
            File pluginDir = new File(warDir, "hqu");
            File sysDir    = new File(earDir, "rendit_sys");
            RenditServer.getInstance().setSysDir(sysDir);

            _log.info("HQU SysDir = [" + sysDir.getAbsolutePath() + "]");
            _log.info("Watching for HQU plugins in [" + 
                      pluginDir.getAbsolutePath() + "]");
            _watcher = new DirWatcher(pluginDir, new DirWatcherCallback() {
                public void fileAdded(File f) {
                    try {
                        RenditServer.getInstance().addPluginDir(f);
                    } catch(Exception e) {
                        _log.warn("Unable to add plugin in [" + 
                                  f.getAbsolutePath() + "]", e);
                    }
                }

                public void fileRemoved(File f) {
                    RenditServer.getInstance().removePluginDir(f.getName());
                }
            });
            
            _watcherThread = new Thread(_watcher);
            _watcherThread.setDaemon(true);
            _watcherThread.start();
                
            INITIALIZED = true;
        }
    }
}
