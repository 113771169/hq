/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2009-2010], VMware, Inc.
 * This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify
 *  it under the terms version 2 of the GNU General Public License as
 *  published by the Free Software Foundation. This program is distributed
 *  in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more
 *  details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 *  USA.
 */

package org.hyperic.hq.agent.handler.autoinventory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.agent.AgentAPIInfo;
import org.hyperic.hq.agent.AgentAssertionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.agent.AgentRemoteValue;
import org.hyperic.hq.agent.bizapp.CommandsAPIInfo;
import org.hyperic.hq.agent.bizapp.callback.AutoinventoryCallbackClient;
import org.hyperic.hq.agent.bizapp.callback.StorageProviderFetcher;
import org.hyperic.hq.agent.server.*;
import org.hyperic.hq.autoinventory.*;
import org.hyperic.hq.autoinventory.agent.AICommandsAPI;
import org.hyperic.hq.autoinventory.agent.client.AICommandsClient;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.product.AutoinventoryPluginManager;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.util.StringUtil;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

@Component
public class AutoinventoryCommandsServer implements AgentServerHandler, AgentNotificationHandler, ScanListener  {

    private Log _log = LogFactory.getLog(AutoinventoryCommandsServer.class);

    private AgentService agentService;
    
    // max sleep is 1 hour between attempts to send AI report to server.
    public static final long AIREPORT_MAX_SLEEP_WAIT = (60000 * 60);

    // we'll keep trying for 30 days to send our a report.
    public static final long AIREPORT_MAX_TRY_TIME = AIREPORT_MAX_SLEEP_WAIT * 24 * 30;
    
    private AICommandsAPI               _verAPI = new AICommandsAPI();

    private AgentStorageProvider        _storage;        

    private RuntimeAutodiscoverer       _rtAutodiscoverer;

    private AICommandsService           _aiCommandsService;

    // The CertDN uniquely identifies this agent
    protected String _certDN;

    private ScanManager _scanManager;
    private ScanState   _lastCompletedDefaultScanState;

    private AutoinventoryCallbackClient _client;


    public AgentAPIInfo getAPIInfo(){
        return _verAPI;
    }

    public String[] getCommandSet(){
        return AICommandsAPI.commandSet;
    }

    public AgentRemoteValue dispatchCommand(String cmd, AgentRemoteValue args,
                                            InputStream in, OutputStream out)
        throws AgentRemoteException {

        _log.debug("AICommandsServer: asked to invoke cmd=" + cmd);

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return dispatchCommand_internal(cmd, args);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private AgentRemoteValue dispatchCommand_internal(String cmd, 
                                                      AgentRemoteValue args)
        throws AgentRemoteException {

        // Anytime we get a request from the server, it means the server
        // is available.  So, if there is a scan sleeping in "scanComplete",
        // wake it up now
        _scanManager.interruptHangingScan();

        if(cmd.equals(_verAPI.command_startScan)){
            ScanConfigurationCore scanConfig = null;
            
            try {
                scanConfig = ScanConfigurationCore.fromAgentRemoteValue(
                                        AICommandsAPI.PROP_SCANCONFIG, args);  
            } catch ( Exception e ) {
                _log.error("Error starting scan.", e);
                throw new AgentRemoteException("Error starting scan: " + 
                                               e.toString());
            }
            
            _aiCommandsService.startScan(scanConfig, false);
            return null;

        } else if(cmd.equals(_verAPI.command_stopScan)){
            _aiCommandsService.stopScan(false);
            return null;

        } else if(cmd.equals(_verAPI.command_getScanStatus)){
            AgentRemoteValue rval = new AgentRemoteValue();
            ScanStateCore state = _aiCommandsService.getScanStatus(false);
            
            try {
                state.toAgentRemoteValue("scanState", rval);
            } catch ( Exception e ) {
                _log.error("Error getting scan state.", e);
                throw new AgentRemoteException("Error getting scan status: " + 
                                               e.toString());
            }
            
            return rval;

        } else if(cmd.equals(_verAPI.command_pushRuntimeDiscoveryConfig)){
            _aiCommandsService.pushRuntimeDiscoveryConfig(args, false);
            return null;
        } else {
            throw new AgentRemoteException("Unknown command: " + cmd);
        }
    }

    public void startup(AgentService agentService) throws AgentStartException {
            this.agentService = agentService;

        try {

            _storage = agentService.getStorageProvider();
            _client  = setupClient();
            _certDN  = _storage.getValue(agentService.getCertDn());
        } catch(AgentRunningException exc){
            throw new AgentAssertionException("Agent should be running here");
        }
        
        AutoinventoryPluginManager pluginManager;

        try {
            pluginManager = (AutoinventoryPluginManager) agentService.getPluginManager(ProductPlugin.TYPE_AUTOINVENTORY);
        } catch (Exception e) {
            throw new AgentStartException("Unable to get auto inventory " +
                                          "plugin manager: " + 
                                          e.getMessage());
        }

        // Initialize the runtime autodiscoverer
        _rtAutodiscoverer = new RuntimeAutodiscoverer(this, _storage,  agentService, _client);

        // Fire up the scan manager
        _scanManager = new ScanManager(this, _log, pluginManager,  
                                      _rtAutodiscoverer);
        
        _aiCommandsService = new AICommandsService(pluginManager, 
                                                   _rtAutodiscoverer, 
                                                   _scanManager);
        
        AgentTransportLifecycle agentTransportLifecycle;
        
        try {
            agentTransportLifecycle = agentService.getAgentTransportLifecycle();
        } catch (Exception e) {
            throw new AgentStartException("Unable to get agent transport lifecycle: " + e.getMessage());
        }
        
        _log.info("Registering AI Commands Service with Agent Transport");
        
        try {
            agentTransportLifecycle.registerService(AICommandsClient.class, _aiCommandsService);
        } catch (Exception e) {
            throw new AgentStartException("Failed to register AI Commands Service.", e);
        }    
                
        _scanManager.startup();

        // Do we have a provider?
        if ( CommandsAPIInfo.getProvider(_storage) == null ) {
            agentService.registerNotifyHandler(this, CommandsAPIInfo.NOTIFY_SERVER_SET);
        } else {
            _rtAutodiscoverer.triggerDefaultScan();
        }
                        
        _log.info("Autoinventory Commands Server started up");
    }

    public void handleNotification(String msgClass, String msg) {
        if (msgClass.equals(CommandsAPIInfo.NOTIFY_SERVER_SET)) {
            _scanManager.interruptHangingScan();
            _rtAutodiscoverer.triggerDefaultScan();
        }
    }

    /**
     * This is the scan that's run when the agent first starts up,
     * and periodically thereafter.  This method is called by the
     * ScanManager when the RuntimeAutodiscoverer says it's time for
     * a DefaultScan (by default, every 15 mins)
     */
    protected void scheduleDefaultScan () {
        _log.debug("Scheduling DefaultScan...");
        ScanConfiguration scanConfig = new ScanConfiguration();
        
        scanConfig.setIsDefaultScan(true);

        _aiCommandsService.startScan(scanConfig);
    }

    public void shutdown () {
        _log.info("Autoinventory Commands Server shutting down");
        // Give the scan manager 3 seconds to shut down.
        synchronized ( _scanManager ) {
            _scanManager.shutdown(3000);
        }
        _log.info("Autoinventory Commands Server shut down");
    }

    private AutoinventoryCallbackClient setupClient() {
        StorageProviderFetcher fetcher =
            new StorageProviderFetcher(_storage);

        return new AutoinventoryCallbackClient(fetcher);
    }

    /**
     * This is where we report our autoinventory-detected data to
     * the EAM server.
     * @see org.hyperic.hq.autoinventory.ScanListener#scanComplete
     */
    public void scanComplete (ScanState scanState) 
        throws AutoinventoryException, SystemException {

        // Special handling for periodic default scans
        if (scanState.getIsDefaultScan()) {
            if (_lastCompletedDefaultScanState != null) {
                try {
                    if (_lastCompletedDefaultScanState.isSameState(scanState)) {
                        // If this default scan is the same as the last one,
                        // don't send anything to the server
                        _log.debug("Default scan didn't find any changes, not "
                                  + "sending report to the server");
                        return;
                    }
                } catch (AutoinventoryException e) {
                    // Just log it and continue, I guess we'll send the report
                    // to the server in this case
                    _log.error("Error comparing default scan states: " + e, e);
                }
            }
            _lastCompletedDefaultScanState = scanState;
        }

        // Anytime a scan completes, we update the most recent state
        _aiCommandsService.setMostRecentState(scanState);

        // Issue a warning if we could not even detect the platform
        if ( scanState.getPlatform() == null ) {
            try {
                ByteArrayOutputStream errInfo = new ByteArrayOutputStream();
                PrintStream errInfoPS = new PrintStream(errInfo);
                scanState.printFullStatus(errInfoPS);
                _log.warn("AICommandsServer: scan completed, but we could not even "
                         + "detect the platform, so nothing will be reported "
                         + "to the server.  Here is some information about the error "
                         + "that occurred: \n" + errInfo.toString() + "\n");
            } catch ( Exception e ) {
                _log.warn("AICommandsServer: scan completed, but we could not even "
                         + "detect the platform, so nothing will be reported "
                         + "to the server.  More information would be provided, "
                         + "but this error occurred just trying to generate more "
                         + "information about the error: " + e, e);
            }
        }

        // But regardless, we always report back to the server, so it
        // knows the scan has been completed.
        scanState.setCertDN(_certDN);

        long sleepWaitMillis = 15000;
        long firstTryTime = System.currentTimeMillis();
        long diffTime;
        while ( true ) {
            try {
                if (_log.isDebugEnabled()) {
                    _log.debug("Sending autoinventory report to server: "
                             + scanState
                    /*+ "\nWITH SERVERS=" + StringUtil.iteratorToString(scanState.getAllServers(null).iterator())*/);

                }
                _client.aiSendReport(scanState);
                _log.info("Autoinventory report " + 
                         "successfully sent to server.");
                break;

            } catch (Exception e) {
                diffTime = System.currentTimeMillis() - firstTryTime;
                if (diffTime > AIREPORT_MAX_TRY_TIME) {
                    final String eMsg = "Unable to send autoinventory " +
                        "platform data to server for maximum time of " +
                        StringUtil.formatDuration(AIREPORT_MAX_TRY_TIME) +
                        ", giving up.  Error was: " + e.getMessage();
                        
                    if(_log.isDebugEnabled()){
                        _log.debug(eMsg, e);
                    } else {
                        _log.error(eMsg);
                    }
                    return;
                }
                final String eMsg = "Unable to send autoinventory " +
                    "platform data to server, sleeping for " +
                    String.valueOf(sleepWaitMillis/1000) + " secs before "+
                    "retrying.  Error: " + e.getMessage();

                if(_log.isDebugEnabled()){
                    _log.debug(eMsg, e);
                } else {
                    _log.error(eMsg);
                }

                try {
                    Thread.sleep(sleepWaitMillis);
                    sleepWaitMillis += (sleepWaitMillis/2);
                    if ( sleepWaitMillis > AIREPORT_MAX_SLEEP_WAIT ) {
                        sleepWaitMillis = AIREPORT_MAX_SLEEP_WAIT;
                    }
                } catch ( InterruptedException ie ) {}
            }
        }
    }
}
