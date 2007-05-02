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
package org.hyperic.hq.ui.rendit.servlet;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.ui.rendit.RenditServer;
import org.hyperic.hq.ui.rendit.servlet.DirWatcher.DirWatcherCallback;
import org.hyperic.util.StringUtil;
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
        String servPath = req.getServletPath();
        String reqUri   = req.getRequestURI();
        
        initPlugins();

        reqUri = reqUri.substring(servPath.length());
        List path = StringUtil.explode(reqUri, "/");
        
        if (path.size() < 1) {
            throw new ServletException("Illegal request path");
        }
        
        String plugin = (String)path.get(0);
        _log.info("Request for [" + plugin + "]: " + req.getRequestURI() + 
                  (req.getQueryString() == null ? "" 
                                               : ("?" + req.getQueryString())));
                  
        try {
            RenditServer.getInstance().handleRequest(plugin, req, resp,
                                                     getServletContext());
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
