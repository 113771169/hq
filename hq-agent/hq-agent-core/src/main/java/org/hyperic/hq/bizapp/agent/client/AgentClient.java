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

package org.hyperic.hq.bizapp.agent.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;
import org.hyperic.hq.agent.AgentConfig;
import org.hyperic.hq.agent.AgentConfigException;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.agent.client.AgentCommandsClient;
import org.hyperic.hq.agent.client.LegacyAgentCommandsClientImpl;
import org.hyperic.hq.agent.server.AgentDaemon;
import org.hyperic.hq.bizapp.agent.ProviderInfo;
import org.hyperic.hq.bizapp.agent.commands.CreateToken_args;
import org.hyperic.hq.bizapp.agent.commands.CreateToken_result;
import org.hyperic.hq.bizapp.client.AgentCallbackClient;
import org.hyperic.hq.bizapp.client.AgentCallbackClientException;
import org.hyperic.hq.bizapp.client.BizappCallbackClient;
import org.hyperic.hq.bizapp.client.RegisterAgentResult;
import org.hyperic.hq.bizapp.client.StaticProviderFetcher;
import org.hyperic.hq.common.shared.ProductProperties;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.util.StringUtil;
import org.hyperic.util.security.SecurityUtil;
import org.tanukisoftware.wrapper.WrapperManager;

import sun.misc.Signal;
import sun.misc.SignalHandler;


/**
 * This class provides the command line entry point into dealing with 
 * the agent.
 */
public class AgentClient {
    private static final PrintStream SYSTEM_ERR = System.err;
    private static final PrintStream SYSTEM_OUT = System.out;

    private static final String PRODUCT = "HQ";

    // The following QPROP_* defines are properties which can be
    // placed in the agent properties file to perform automatic setup
    private static final String QPROP_PRE = "agent.setup.";
    public  static final String QPROP_IPADDR     = QPROP_PRE + "camIP";
    public  static final String QPROP_PORT       = QPROP_PRE + "camPort";
    public  static final String QPROP_SSLPORT    = QPROP_PRE + "camSSLPort";
    public  static final String QPROP_NEWTRANSPORT = QPROP_PRE + "newTransport";    
    public  static final String QPROP_UNI        = QPROP_PRE + "unidirectional";
    public  static final String QPROP_UNI_POLLING_FREQUENCY = QPROP_PRE + "uniPollingFrequency";    
    private static final String QPROP_SECURE     = QPROP_PRE + "camSecure";
    private static final String QPROP_LOGIN      = QPROP_PRE + "camLogin";
    private static final String QPROP_PWORD      = QPROP_PRE + "camPword";
    private static final String QPROP_AGENTIP    = QPROP_PRE + "agentIP";
    private static final String QPROP_AGENTPORT  = QPROP_PRE + "agentPort";
    private static final String QPROP_RESETUPTOK = QPROP_PRE + "resetupTokens";
    private static final String QPROP_TIMEOUT    = QPROP_PRE + "serverTimeout";

    private static final String PROP_LOGFILE    = "agent.logFile";
    private static final String PROP_STARTUP_TIMEOUT = "agent.startupTimeOut";
    private static final String PROP_FQDN = "platform.fqdn";
    
    private static final String AGENT_CLASS   = 
        "org.hyperic.hq.agent.server.AgentDaemon";

    private static final int AGENT_STARTUP_TIMEOUT = (60 * 5) * 1000; // 5 min
    private static final int FORCE_SETUP           = -42;

    private static final String JAAS_CONFIG = "jaas.config";

    private AgentCommandsClient agtCommands; 
    private CommandsClient   camCommands; 
    private AgentConfig   config;
    private String              sslHandlerPkg;
    private Log                 log;                 
    private boolean             nuking;
    private boolean             redirectedOutputs = false;

    private AgentClient(AgentConfig config, SecureAgentConnection conn){
        this.agtCommands = new LegacyAgentCommandsClientImpl(conn);
        this.camCommands = new CommandsClient(conn);
        this.config      = config;
        this.log         = LogFactory.getLog(AgentClient.class);
        this.nuking      = false;
    }

    private long cmdPing(int numAttempts)
        throws AgentConnectionException, AgentRemoteException
    {
        AgentConnectionException lastExc;

        lastExc = new AgentConnectionException("Failed to connect to agent");
        while(numAttempts-- != 0){
            try {
                return this.agtCommands.ping();
            } catch(AgentConnectionException exc){
                // Loop around to the next attempt
                lastExc = exc;
            }
            try {
                Thread.sleep(1000);
            } catch(InterruptedException exc){
                throw new AgentConnectionException("Connection interrupted");
            }
        }
        throw lastExc;
    }

    private void cmdStatus()
        throws AgentConnectionException, AgentRemoteException
    {
        ProviderInfo pInfo;
        String address;
        String currentAgentBundle;

        try {
            currentAgentBundle = this.agtCommands.getCurrentAgentBundle();
            
            pInfo = this.camCommands.getProviderInfo();
        } catch(AgentConnectionException exc){
            SYSTEM_ERR.println("Unable to contact agent: " + exc.getMessage());
            return;
        } catch(AgentRemoteException exc){
            SYSTEM_ERR.println("Error executing remote method: " +
                               exc.getMessage());
            return;
        }
        
        SYSTEM_OUT.println("Current agent bundle: "+currentAgentBundle);

        if(pInfo == null || (address = pInfo.getProviderAddress()) == null){
            SYSTEM_OUT.println("Agent not yet setup");
            return;
        }
        
        try {
            String proto;

            URL url = new URL(address);

            SYSTEM_OUT.println("Server IP address: " + url.getHost());
            proto = url.getProtocol();
            if(proto.equalsIgnoreCase("https"))
                SYSTEM_OUT.print("Server (SSL) port: ");
            else
                SYSTEM_OUT.print("Server port:       ");
            SYSTEM_OUT.println(url.getPort());
            
            if (pInfo.isNewTransport()) {
                SYSTEM_OUT.println("Using new transport; unidirectional="+
                                  pInfo.isUnidirectional());
            }
        } catch(Exception exc){
            SYSTEM_OUT.println("Unable to parse provider info (" + 
                               address + "): " + exc.getMessage());
        }
        
        SYSTEM_OUT.println("Agent listen port: " + 
                           this.config.getListenPort());
        
        
        if (this.config.isProxyServerSet()) {
            SYSTEM_OUT.println("Proxy server IP address: "+this.config.getProxyIp());
            SYSTEM_OUT.println("Proxy server port: "+this.config.getProxyPort());
        }
        
    }

    private void cmdDie(int waitTime)
        throws AgentConnectionException, AgentRemoteException
    {
        try {
            this.agtCommands.die();
        } catch(AgentConnectionException exc){
            return; // If we can't connect then we know the agent is dead
        } catch(AgentRemoteException exc){
            throw new AgentRemoteException("Error making remote agent call: "+
                                           exc.getMessage());
        }

        // Loop waiting to see if it died before returning
        while(waitTime-- != 0){
            try {
                this.agtCommands.ping();
            } catch(AgentConnectionException exc){
                return;  // Success!
            } catch(AgentRemoteException exc){
                exc.printStackTrace(SYSTEM_ERR);
                throw exc;  // Something bizarro occurred
            }
            try {
                Thread.sleep(1000);
            } catch(InterruptedException exc){
                throw new AgentConnectionException("Connection interrupted");
            }
        }

        throw new AgentRemoteException("Unable to kill agent within timeout");
    }
    

    private void cmdRestart()
    throws AgentConnectionException, AgentRemoteException
{
    try {
        this.agtCommands.restart();
    } catch(AgentConnectionException exc){
        throw new AgentConnectionException("Unable to connect to agent: " +
                                           "already dead?");
    } catch(AgentRemoteException exc){
        throw new AgentRemoteException("Error making remote agent call: "+
                                       exc.getMessage());
    }
}    
    
    private class AutoQuestionException extends Exception {
        AutoQuestionException(String s){
            super(s);
        }
    }

    private String askQuestion(String question, String def, boolean invis,
                               String questionProp)
        throws IOException
    {
        BufferedReader in;
        String res, bootProp;

        bootProp = this.config.getBootProperties().getProperty(questionProp);

        while(true){
            SYSTEM_OUT.print(question);
            if(def != null){
                SYSTEM_OUT.print(" [default=" + def + "]");
            }
            
            SYSTEM_OUT.print(": ");

            if(invis){
                if(bootProp != null){
                    SYSTEM_OUT.println("**Not echoing value**");
                    return bootProp;
                }
                return Sigar.getPassword("");
            } else {
                if(bootProp != null){
                    if(bootProp.equals("*default*") && def != null){
                        bootProp = def;
                    }
                    
                    SYSTEM_OUT.println(bootProp);
                    return bootProp;
                }

                in = new BufferedReader(new InputStreamReader(System.in));
                if((res = in.readLine()) != null){
                    res = res.trim();
                    if(res.length() == 0){
                        res = null;
                    }
                }
                
                if(res == null){
                    if(def != null){
                        return def;
                    }
                } else {
                    return res;
                }
            }
        }
    }

    private String askQuestion(String question, String def, 
                               String questionProp)
        throws IOException
    {
        return this.askQuestion(question, def, false, questionProp);
    }

    private boolean askYesNoQuestion(String question, boolean def,
                                     String questionProp)
        throws IOException, AutoQuestionException
    {
        boolean isAuto;

        isAuto = this.config.getBootProperties().getProperty(questionProp) !=
            null;

        while(true){
            String res;

            res = this.askQuestion(question, def ? "yes" : "no",
                                   questionProp);
            if(res.equalsIgnoreCase("yes") ||
               res.equalsIgnoreCase("y"))
            {
                return true;
            } else if(res.equalsIgnoreCase("no") ||
                      res.equalsIgnoreCase("n"))
            {
                return false;
            }

            if(isAuto){
                throw new AutoQuestionException("Property '" + questionProp +
                                                "' must be 'yes' or " +
                                                "'no'");
            }

            SYSTEM_OUT.println("- Value must be 'yes' or 'no'");
        }
    }


    private int askIntQuestion(String question, int def, String questionProp)
        throws IOException, AutoQuestionException
    {
        boolean isAuto;

        isAuto = this.config.getBootProperties().getProperty(questionProp) !=
            null;

        while(true){
            String res;
            int iVal;

            res = this.askQuestion(question, Integer.toString(def),
                                   questionProp);
            try {
                iVal = Integer.parseInt(res);
                return iVal;
            } catch(NumberFormatException exc){
                if(isAuto){
                    throw new AutoQuestionException("Property '" + 
                                    questionProp +"' must be a valid integer");
                }
                SYSTEM_OUT.println("- Value must be an integer");
            }
        }
    }

    private BizappCallbackClient testProvider(String provider)
        throws AgentCallbackClientException
    {
        StaticProviderFetcher fetcher;
        BizappCallbackClient res;

        fetcher = new StaticProviderFetcher(new ProviderInfo(provider, 
                                                             "no-auth"));
        res = new BizappCallbackClient(fetcher, config);
        res.bizappPing();
        return res;
    }

    /**
     * Test the connection information.
     */
    private BizappCallbackClient getConnection(String provider,
                                               boolean secure)
        throws AutoQuestionException, AgentCallbackClientException
    {
        BizappCallbackClient bizapp;
        Properties bootP = this.config.getBootProperties();
        long start = System.currentTimeMillis();
       
        while (true) {
            String sec = secure ? "secure" : "insecure";
            SYSTEM_OUT.print("- Testing " + sec  + " connection ... ");

            try {
                bizapp = this.testProvider(provider);
                SYSTEM_OUT.println("Success");
                return bizapp;
            } catch (AgentCallbackClientException exc) {
                String msg = exc.getMessage();
                if (msg.indexOf("is still starting") != -1) {
                    SYSTEM_ERR.println("HQ is still starting " +
                                       "(retrying in 10 seconds)");
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (InterruptedException e) {}
                    // Try again
                    continue;
                }

                // Check for configured server timeout
                String propTimeout = bootP.getProperty(QPROP_TIMEOUT);
                if (propTimeout != null) {
                    long timeout;
                    try {
                        timeout = Integer.parseInt(propTimeout) * 1000;
                    } catch(NumberFormatException nfe){
                        // If the timeout is improperly configured,
                        // bail out
                        throw new AutoQuestionException("Mis-configured" +
                                                        QPROP_TIMEOUT +
                                                        "property: " +
                                                        propTimeout);
                    }

                    SYSTEM_ERR.println("Failure (retrying in 10 seconds)");
                    if (start + timeout > System.currentTimeMillis()) {
                        try {
                            Thread.sleep(10 * 1000);
                        } catch (InterruptedException ie) {}
                        // Try again
                        continue;
                    }
                }

                SYSTEM_ERR.println("Failure");

                if (bootP.getProperty(QPROP_IPADDR) != null ||
                    bootP.getProperty(QPROP_PORT) != null ||
                    bootP.getProperty(QPROP_SSLPORT) != null) {
                    throw new AutoQuestionException("Unable to connect to " +
                                                    PRODUCT);
                }
                throw exc;
            }
        }
    }

    private static int getCpuCount () throws SigarException {
        Sigar sigar = new Sigar();
        try {
            return sigar.getCpuInfoList().length;
        } finally {
            sigar.close();
        }
    }

    private String getDefaultIpAddress() {
        String address;
        final String loopback = "127.0.0.1";

        try {
            address =
                InetAddress.getLocalHost().getHostAddress();
            if (!loopback.equals(address)) {
                return address;
            }
        } catch(UnknownHostException e) {
            //hostname not in DNS or /etc/hosts
        }

        Sigar sigar = new Sigar();
        try {
            address =
                sigar.getNetInterfaceConfig().getAddress();
        } catch (SigarException e) {
            address = loopback;
        } finally {
            sigar.close();
        }

        return address;
    }
    
    private void cmdSetupIfNoProvider() 
        throws AgentConnectionException, AgentRemoteException, 
               IOException, AutoQuestionException {
               
        Properties bootProps = this.config.getBootProperties();        
        int timeout = getStartupTimeout(bootProps);
        
        // Sleep until the agent is started
        this.cmdPing(timeout / 1000);
        
        // Prompt the agent to setup if the provider info is not specified.
        ProviderInfo providerInfo = this.camCommands.getProviderInfo();

        if (providerInfo == null) {
            this.cmdSetup();
        }
                
    }

    private void cmdSetup()
        throws AgentConnectionException, AgentRemoteException, IOException,
               AutoQuestionException
    {
        BizappCallbackClient bizapp;
        InetAddress localHost;
        CreateToken_result tokenRes;
        ProviderInfo providerInfo;
        String provider, host, user, pword,  
            agentToken, response;
        String agentIP;
        Properties bootP;
        int agentPort = -1;
        boolean isNewTransportAgent = false;
        boolean unidirectional = false;
        int unidirectionalPort = -1;

        bootP = this.config.getBootProperties();
        
        try {
            this.cmdPing(1);
        } catch(AgentConnectionException exc){
            SYSTEM_ERR.println("Unable to setup agent: " + exc.getMessage());
            SYSTEM_ERR.println("The Agent must be running prior to running " +
                               "setup");
            return;
        }

        SYSTEM_OUT.println("[ Running agent setup ]");
        
        // FIXME - For now we will only setup the new transport if the 
        // unidirectional agent is available (since currently we don't 
        // have a bidirectional agent). Once we have a bidirectional 
        // agent, we should always ask if agent communications should 
        // use the new transport.

//        isNewTransportAgent = askYesNoQuestion("Should Agent communications to " + 
//                PRODUCT + " use the new transport ", 
//                false, QPROP_NEWTRANSPORT);   
                
        String version = ProductProperties.getVersion();
        
        log.debug("Agent version: "+version);
        
        boolean isUnidirectionalAgentSupported = 
            version.toLowerCase().indexOf("ee") != -1;
        
        if (isUnidirectionalAgentSupported) {
            unidirectional = askYesNoQuestion("Should Agent communications to " + 
                                              PRODUCT + " be unidirectional", 
                                              false, QPROP_UNI);
            
            // FIXME For now enable the new transport only if we have 
            // a unidirectional agent.
            isNewTransportAgent = unidirectional;
        }
        
        boolean secure;
        int port;

        while(true){
            host = this.askQuestion("What is the " + PRODUCT +
                                    " server IP address",
                                    null, QPROP_IPADDR);
                        
            secure = askYesNoQuestion("Should Agent communications " +
                                      "to " + PRODUCT + " always " +
                                      "be secure", 
                                      false, QPROP_SECURE);
            if (secure) {
                // Always secure.  Ask for SSL port and verify
                port = this.askIntQuestion("What is the " + PRODUCT +
                                           " server SSL port",
                                           7443, QPROP_SSLPORT);
                provider = AgentCallbackClient.getDefaultProviderURL(host,
                                                                     port,
                                                                     true);
            } else {
                // Never secure.  Only ask for non-ssl port and verify
                port    = this.askIntQuestion("What is the " + PRODUCT +
                                              " server port    ", 
                                              7080, QPROP_PORT);

                provider = AgentCallbackClient.getDefaultProviderURL(host,
                                                                     port,
                                                                     false);
            }

            try {
                bizapp = getConnection(provider, secure);
            } catch (AgentCallbackClientException e) {
                continue;
            }

            break;
        }
                
        if (unidirectional) {
            // workaround to uniquely identify an agent based on host and port combination
            // we set the agent port to the hashcode of the FQDN if specified 
            // note that this is not actually a valid port number but rather used as an identifier
            // during agent registration to support multiple unidirectional agents on same host
            String fqdn = bootP.getProperty(PROP_FQDN);
            if (fqdn != null) {
                agentPort = fqdn.hashCode();
            }
            
            if (secure) {
                unidirectionalPort = port;                
            } else {
                // unidirectional only uses secure communication. Ask for SSL port and verify
                while(true){
                    unidirectionalPort = this.askIntQuestion("What is the " + PRODUCT +
                                       " server SSL port for unidirectional communications",
                                                   7443, QPROP_SSLPORT);
                    
                    // The unidirectional transport is not hosted via the 
                    // Lather servlet, but this is ok, since the 
                    // undirectional servlet is in the same container as 
                    // the Lather servlet. We are just testing connectivity 
                    // to the servlet container here.
                    String unidirectionalProvider = 
                        AgentCallbackClient.getDefaultProviderURL(host,
                                                        unidirectionalPort,
                                                        true);
                    try {
                        getConnection(unidirectionalProvider, true);
                    } catch (AgentCallbackClientException e) {
                        continue;
                    }

                    break;
                }                            
            }
        }
        
        while(true){
            user  = this.askQuestion("What is your " + PRODUCT +
                                     " login", "hqadmin",
                                     QPROP_LOGIN);
            pword = this.askQuestion("What is your " + PRODUCT +
                                     " password", null, true,
                                     QPROP_PWORD);
            try {
                if(bizapp.userIsValid(user, pword))
                    break;
            } catch(AgentCallbackClientException exc){
                SYSTEM_ERR.println("Error validating user: " + 
                                   exc.getMessage());
                return;
            }
            
            SYSTEM_ERR.println("- Invalid username/password");
            if(bootP.getProperty(QPROP_LOGIN) != null ||
               bootP.getProperty(QPROP_PWORD) != null)
            {
                throw new AutoQuestionException("Invalid username/password");
            }
        }

        // Get info about agent
        while(true){
            String question;
            
            if (unidirectional) {
                question = "What is the agent IP address";
            } else {
                question = "What IP should "+PRODUCT+" use to contact the agent";
            }
            
            agentIP = this.askQuestion(question, 
                                       getDefaultIpAddress(),
                                       QPROP_AGENTIP);
            
            // Attempt to resolve, as a safeguard
            try {
                localHost = InetAddress.getByName(agentIP);
                localHost.getHostAddress();
                break;
            } catch(UnknownHostException exc){
                SYSTEM_ERR.println("- Unable to resolve host");
            }
        } 

        if (!unidirectional) {
            while(true){
        	int listenPort = this.config.getListenPort();
                agentPort = this.askIntQuestion("What port should " + PRODUCT +
                                                " use to contact the agent",
                                                listenPort,
                                                QPROP_AGENTPORT);
                if(agentPort < 1 || agentPort > 65535){
                    SYSTEM_ERR.println("- Invalid port");
                } else {
                    if (agentPort!= listenPort){
                        SYSTEM_ERR.println("- To setup agent port to "+ 
                				agentPort + "," +
                                           " Stop the agent," +
                                           " Update agent properties" +
                                           " for agent.listenPort and start" +
                                           " the agent again");
                        SYSTEM_OUT.println("- Now Agent uses the default port:"
                                            +listenPort);
                        agentPort = listenPort;
                    }
                    break;
                }
            }            
        }
        
        // The old agent token may be needed if re-registering an existing agent 
        // but changing from non-unidirectional to unidirectional transport. 
        // In this case, the agent port will change, so we will need to lookup 
        // the agent by the old agent token instead of agent IP and port.
        String oldAgentToken = null;

        /* Check to see if this agent already has a setup for a server.
           If it does, allow the user to re-register with the new IP address */
        if((providerInfo = this.camCommands.getProviderInfo()) != null &&
           providerInfo.getProviderAddress() != null &&
           providerInfo.getAgentToken() != null)
        {
            oldAgentToken = providerInfo.getAgentToken();
            
            boolean setupTokens;

            SYSTEM_OUT.println("- Agent is already setup for " +
                               PRODUCT + " @ " +
                               providerInfo.getProviderAddress());
            setupTokens =
                this.askYesNoQuestion("Would you like to re-setup the auth " +
                                      "tokens", false, QPROP_RESETUPTOK);
            if(setupTokens == false){
                // Here we basically just need to inform the server that the 
                // agent with a given AgentToken will re-use that, but
                // with a different IP address
                SYSTEM_OUT.println("- Informing " + PRODUCT +
                                   " about agent setup changes");
                try {
                    response = bizapp.updateAgent(providerInfo.getAgentToken(),
                                                  user, pword, agentIP, 
                                                  agentPort, 
                                                  isNewTransportAgent, 
                                                  unidirectional);
                    if(response != null)
                        SYSTEM_ERR.println("- Error updating agent: " +
                                           response);
                } catch(Exception exc){
                    SYSTEM_ERR.println("- Error updating agent: " + 
                                       exc.getMessage());
                }
                
                if (providerInfo.isNewTransport()!=isNewTransportAgent || 
                    providerInfo.isUnidirectional()!=unidirectional) {
                    
                    ProviderInfo registeredProviderInfo = 
                        new ProviderInfo(provider, providerInfo.getAgentToken());
                    
                    if (isNewTransportAgent) {
                        registeredProviderInfo.setNewTransport(unidirectional, 
                                                               unidirectionalPort);
                    }
                    
                    this.camCommands.setProviderInfo(registeredProviderInfo);
                }
                
                return;
            }
        }

        // Ask agent for a new connection token
        try {
            InetAddress.getByName(host);
        } catch(UnknownHostException exc){
            SYSTEM_ERR.println("Unable to resolve provider (strange): " +
                               exc.getMessage());
            return;
        }
        tokenRes = this.camCommands.createToken(new CreateToken_args());
        
        SYSTEM_OUT.println("- Received temporary auth token from agent");

        // Ask server to verify agent
        SYSTEM_OUT.println("- Registering agent with " + PRODUCT);
        RegisterAgentResult result;
        try {
            result = bizapp.registerAgent(oldAgentToken, 
                                          user, pword, 
                                          tokenRes.getToken(), 
                                          agentIP, agentPort,
                                          ProductProperties.getVersion(),
                                          getCpuCount(), isNewTransportAgent, 
                                          unidirectional);
            response = result.response;
            if(!response.startsWith("token:")){
                SYSTEM_ERR.println("- Unable to register agent: " + response);
                return;
            }

            // Else the bizapp responds with the token that the agent needs
            // to use to contact it
            agentToken = response.substring("token:".length());
        } catch(Exception exc){
            exc.printStackTrace(SYSTEM_ERR);
            SYSTEM_ERR.println("- Error registering agent: "+exc.getMessage());
            return;
        }
        
        SYSTEM_OUT.println("- " + PRODUCT +
                           " gave us the following agent token");
        SYSTEM_OUT.println("    " + agentToken);
        SYSTEM_OUT.println("- Informing agent of new " + PRODUCT + " server");
        
        ProviderInfo registeredProviderInfo = new ProviderInfo(provider, agentToken);
        
        if (isNewTransportAgent) {
            registeredProviderInfo.setNewTransport(unidirectional, unidirectionalPort);
        }
        
        this.camCommands.setProviderInfo(registeredProviderInfo);
        SYSTEM_OUT.println("- Validating");
        providerInfo = this.camCommands.getProviderInfo();
        
        if(providerInfo == null || 
           providerInfo.getProviderAddress().equals(provider) == false ||
           providerInfo.getAgentToken().equals(agentToken) == false)
        {
            if(providerInfo == null){
                SYSTEM_ERR.println(" - Failure - Agent is reporting no " +
                                   "" + PRODUCT + " provider information");
            } else {
                SYSTEM_ERR.println("- Failure - Agent is using " +
                                   PRODUCT + " server '" + 
                                   providerInfo.getProviderAddress() + 
                                   "' with token '" +
                                   providerInfo.getAgentToken() + "'");
            }

        } else {
            SYSTEM_OUT.println("- Successfully setup agent");
            
            if (providerInfo.isNewTransport()) {
                String unidirectionalPortString = "";
                if (providerInfo.isUnidirectional()) {
                    unidirectionalPortString = ", port="+
                    providerInfo.getUnidirectionalPort();
                }
                
                SYSTEM_OUT.println("- Agent using new transport, unidirectional="+
                                    providerInfo.isUnidirectional()+
                                    unidirectionalPortString);
            }
        }

        redirectOutputs(bootP); //win32
    }

    private void verifyAgentRunning(ServerSocket startupSock)
        throws AgentInvokeException
    {
        try {
            
            Socket conn = startupSock.accept();

            DataInputStream dIs  = new DataInputStream(conn.getInputStream());
            
            if(dIs.readInt() != 1){
                throw new AgentInvokeException("Agent reported an error " +
                                               "while starting up");
            }                
                        
        } catch(InterruptedIOException exc){
            throw new AgentInvokeException("Timed out waiting for Agent " +
                                           "to report startup success");
        } catch(IOException exc){
            throw new AgentInvokeException("Agent failure while starting");
        } finally {
            try { startupSock.close(); } catch(IOException exc){}
        }
                
        try {
            this.agtCommands.ping();
        } catch(Exception exc){
            throw new AgentInvokeException("Unable to ping agent: " +
                                           exc.getMessage());
        }     
    }

    private void nukeAgentAndDie(){
        synchronized(this){
            if(this.nuking){
                return;
            }
            
            this.nuking = true;
        }

        try {
            SYSTEM_ERR.println("Received interrupt while starting.  " +
                               "Shutting agent down ...");
            this.cmdDie(10);
        } catch (Exception e){
        }

        System.exit(-1);
    }

    private void handleSIGINT() {
        try {
            Signal.handle(new Signal("INT"), new SignalHandler() {
                public void handle(Signal sig) {
                    nukeAgentAndDie();
                }
            });
        } catch(Exception e) {
            // avoid "Signal already used by VM: SIGINT", e.g. ibm jdk
        }
    }

    private void redirectOutputs(Properties bootProp) {
        if (this.redirectedOutputs) {
            return;
        }
        this.redirectedOutputs = true;
                
        try {
            String startupLogFile = getStartupLogFile(bootProp);
            PrintStream os = 
                new PrintStream(new FileOutputStream(startupLogFile));
            System.setErr(os);
            System.setOut(os);
        } catch (Exception e) {
            e.printStackTrace(SYSTEM_ERR);
        }
    }

    private String getPdkClasspath(Properties props) {
        String pdkLib =
            props.getProperty("agent.pdkLibDir", "./pdk/lib");
        File dir = new File(pdkLib);
        if (!dir.exists()) {
            throw new IllegalArgumentException(pdkLib +
                                               " does not exist!");
        }
        StringBuffer sb = new StringBuffer();
        String[] jars = dir.list();
        for (int i=0; i<jars.length; i++) {
            String jar = jars[i];
            if (!(jar.endsWith(".jar") ||
                  jar.endsWith(".zip")))
            {
                continue;
            }
            sb.append(pdkLib).append('/').append(jar);
            if (i < jars.length-1) {
                sb.append(':');
            }
        }

        return sb.toString();
    }
    
    private int cmdStart(boolean force) 
        throws AgentInvokeException
    {
        ServerSocket startupSock;
        ProviderInfo providerInfo;
        Properties bootProps;

        // Try to ping the agent one time to see if the agent is already up
        try {
            this.cmdPing(1);
            SYSTEM_OUT.println("Agent already running");
            return -1;
        } catch(AgentConnectionException exc){ 
            // Normal operation 
        } catch(AgentRemoteException exc){
            // Very nearly a normal operation
        }

        bootProps = this.config.getBootProperties();

        try {
            int iSleepTime = getStartupTimeout(bootProps);

            startupSock = new ServerSocket(0);
            startupSock.setSoTimeout(iSleepTime);
        } catch(IOException exc){
            throw new AgentInvokeException("Unable to setup a socket to " +
                                           "listen for Agent startup: " +
                                           exc.getMessage());
        }

        SYSTEM_OUT.println("- Invoking agent");

        try {
            this.config.setNotifyUpPort(startupSock.getLocalPort());
        } catch (AgentConfigException e) {
            throw new AgentInvokeException("Invalid notify up port: "+startupSock.getLocalPort());
        }
                
        Thread t = new Thread(new AgentDaemon.RunnableAgent(this.config));
        t.start();
        SYSTEM_OUT.println("- Agent thread running");

        /* Now comes the painful task of figuring out if the agent
           started correctly. */
        SYSTEM_OUT.println("- Verifying if agent is running...");
        this.verifyAgentRunning(startupSock);
        SYSTEM_OUT.println("- Agent is running");            

        // Ask the agent if they have a server setup
        try {
            providerInfo = this.camCommands.getProviderInfo();
        } catch(Exception exc){
            // This should rarely (never) occur, since we just ensured things
            // were operational.
            throw new AgentInvokeException("Unexpected connection exception: "+
                                           "agent is still running");
        }

        SYSTEM_OUT.println("Agent successfully started");
        
        // Only force a setup if we are not running the agent in Java Service Wrapper mode
        if(providerInfo == null && !WrapperManager.isControlledByNativeWrapper()){
            SYSTEM_OUT.println();
            return FORCE_SETUP;
        } else {
            redirectOutputs(bootProps); //win32
            return 0;            
        }

    }
    
    private String getStartupLogFile(Properties bootProps)  throws AgentConfigException {
            String logFile;
            
            if((logFile = bootProps.getProperty(PROP_LOGFILE)) == null){
                throw new AgentConfigException(PROP_LOGFILE + " is undefined");
            }
            
            return logFile + ".startup";
    }
    
    // returns the startup timeout in milliseconds
    private static int getStartupTimeout(Properties bootProps) {
        int iSleepTime = AGENT_STARTUP_TIMEOUT;
        String sleepTime = bootProps.getProperty(PROP_STARTUP_TIMEOUT);

        try {
            iSleepTime = Integer.parseInt(sleepTime) * 1000;
        } catch(NumberFormatException exc){
            // do nothing - keep default
        }
        return iSleepTime;
    }

    private static int getUseTime(String val){
        try {
            return Integer.parseInt(val);
        } catch(NumberFormatException exc){
            return 1;
        }
    }

    /**
     * Initialize the AgentClient
     *
     * @param generateToken If set to true, generate the agent token, otherwise
     * wait until the tokens are available.
     *
     * @return An initialized AgentClient
     */
    private static AgentClient initializeAgent(boolean generateToken){
        SecureAgentConnection conn;
        AgentConfig cfg;
        String connIp, listenIp, authToken, propFile;
        propFile =
            System.getProperty(AgentConfig.PROP_PROPFILE,
                               AgentConfig.DEFAULT_PROPFILE);

        //console appender until we have configured logging.
        BasicConfigurator.configure();

        try {
            cfg = AgentConfig.newInstance(propFile);
        } catch(IOException exc){
            SYSTEM_ERR.println("Error: " + exc);
            return null;
        } catch(AgentConfigException exc){
            SYSTEM_ERR.println("Agent Properties error: " + exc.getMessage());
            return null;
        }

        //we wait until AgentConfig.newInstance has merged
        //all properties to configure logging.
        Properties bootProps = cfg.getBootProperties();
        if (!checkCanWriteToLog(bootProps)) {
            return null;
        }
        PropertyConfigurator.configure(bootProps);
        
        listenIp = cfg.getListenIp();
        try {
            if(listenIp.equals(AgentConfig.IP_GLOBAL)){
                connIp = "127.0.0.1";
            } else {
                connIp = InetAddress.getByName(listenIp).getHostAddress();
            }
        } catch(UnknownHostException exc){
            SYSTEM_ERR.println("Failed to lookup agent address '" + 
                               listenIp + "'");
            return null;
        }
        
        String tokenFile = cfg.getTokenFile();
        if (generateToken) {
            try {
                authToken = AgentClientUtil.getLocalAuthToken(tokenFile);
            } catch(FileNotFoundException exc){
                SYSTEM_ERR.print("- Unable to load agent token file.  Generating" +
                                 " a new one ... ");
                try {
                    String nToken = SecurityUtil.generateRandomToken();

                    AgentClientUtil.generateNewTokenFile(tokenFile, nToken);
                    authToken = AgentClientUtil.getLocalAuthToken(tokenFile);
                } catch(IOException oexc){
                    SYSTEM_ERR.println("Unable to setup preliminary agent auth " +
                                       "tokens: " + exc.getMessage());
                    return null;
                }
                SYSTEM_ERR.println("Done");
            } catch(IOException exc){
                SYSTEM_ERR.println("Unable to get necessary authentication tokens"+
                               " to talk to agent: " + exc.getMessage());
                return null;
            }

            conn = new SecureAgentConnection(connIp, cfg.getListenPort(), authToken);
            return new AgentClient(cfg, conn);
        } else {
            // Not the main agent daemon process, wait for the token to become
            // available.  We will only wait up to the configured agent.startupTimeOut
            long initializeStartTime = System.currentTimeMillis();
            long startupTimeout = getStartupTimeout(bootProps);
            while (initializeStartTime > (System.currentTimeMillis() - startupTimeout)) {
                try {
                    authToken = AgentClientUtil.getLocalAuthToken(tokenFile);
                    conn = new SecureAgentConnection(connIp, cfg.getListenPort(), authToken);
                    return new AgentClient(cfg, conn);
                } catch(FileNotFoundException exc){
                    SYSTEM_ERR.println("- No token file found, waiting for " +
                                       "Agent to initialize");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        SYSTEM_ERR.println("Interrupted! Shutting down");
                        return null;
                    }
                } catch(IOException e) {
                    SYSTEM_ERR.println("Unable to read preliminary agent auth " +
                                       "tokens, waiting for Agent to initialize " +
                                       "(error was: " + e.getMessage() + ")");
                }
            }
            SYSTEM_ERR.println("Timeout waiting for token file");
            return null;
        }
    }

    public static void main(String args[]) {

        if(args.length < 1 || 
           !(args[0].equals("ping") || 
             args[0].equals("die")  ||
             args[0].equals("start") || 
             args[0].equals("status") ||
             args[0].equals("restart") ||
             args[0].equals("setup") ||
             args[0].equals("setup-if-no-provider")))
        {
            SYSTEM_ERR.println("Syntax: program " +
                               "<ping [numAttempts] | die [dieTime] | start " +
                               "| status | restart | setup | setup-if-no-provider >");
            return;
        }

        AgentClient client;
        if (args[0].equals("start")) {
            // Only generate tokens on agent startup.
            client = initializeAgent(true);
        } else {
            client = initializeAgent(false);
        }
        
        if (client == null) {
            return;
        }

        try {
            int nWait;

            if(args[0].equals("ping")){
                if(args.length == 3){
                    nWait = getUseTime(args[2]);
                } else {
                    nWait = 1;
                }
                client.cmdPing(nWait);
            } else if(args[0].equals("die")){
                if(args.length == 2){
                    nWait = getUseTime(args[1]);
                } else {
                    nWait = 1;
                }
                SYSTEM_OUT.println("Stopping agent ... ");
                try {
                    client.cmdDie(nWait);
                    SYSTEM_OUT.println("Success -- agent is stopped!");
                } catch(Exception exc){
                    SYSTEM_OUT.println("Failed to stop agent: " +
                                       exc.getMessage());
                }
            } else if(args[0].equals("start")){
                int errVal = client.cmdStart(false);
                if(errVal == FORCE_SETUP){
                    errVal = 0;
                    client.cmdSetupIfNoProvider();
                }
            } else if(args[0].equals("status")){
                client.cmdStatus();
            } else if(args[0].equals("setup")){
                client.cmdSetup();
            } else if(args[0].equals("setup-if-no-provider")) {
                client.cmdSetupIfNoProvider();
            } else if(args[0].equals("restart")){
                client.cmdRestart();           
            } else
                throw new IllegalStateException("Unhandled condition");
        } catch(AutoQuestionException exc){
            SYSTEM_ERR.println("Unable to automatically setup: " +
                               exc.getMessage());
        } catch(AgentInvokeException exc){
            SYSTEM_ERR.println("Error invoking agent: " + exc.getMessage());
        } catch(AgentConnectionException exc){
            SYSTEM_ERR.println("Error contacting agent: " + exc.getMessage());
        } catch(AgentRemoteException exc){
            SYSTEM_ERR.println("Error executing remote method: " +
                               exc.getMessage());
        } catch(Exception exc){
            SYSTEM_ERR.println("Error: " + exc.getMessage());
            exc.printStackTrace(SYSTEM_ERR);
        }
    }

    private static boolean checkCanWriteToLog (Properties props) {

        String logFileName = props.getProperty("agent.logFile");
        File logFile = new File(logFileName);
        File logDir = logFile.getParentFile();
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                SYSTEM_ERR.println("Log directory does not exist and " 
                                   + "could not be created: "
                                   + logDir.getAbsolutePath() 
                                   + "\nCannot start HQ agent.");
                return false;
            }
        }
        if (!logDir.canWrite()) {
            SYSTEM_ERR.println("Cannot write to log directory: " 
                               + logDir.getAbsolutePath() 
                               + "\nMake sure this directory is owned by user '"
                               + System.getProperty("user.name") + "' and is "
                               + "not a read-only directory."
                               + "\nCannot start HQ agent.");
            return false;
        }
        if (logFile.exists() && !logFile.canWrite()) {
            SYSTEM_ERR.println("Cannot write to log file: " 
                               + logFile.getAbsolutePath() 
                               + "\nMake sure this file is owned by user '"
                               + System.getProperty("user.name") + "' and is "
                               + "not a read-only file."
                               + "\nCannot start HQ agent.");
            return false;
        }
        
        return true;
    }

    /**
     * @param s A string that might contain unix-style classpath separators.
     * @return The correct path for this platform (i.e, if win32, replace : with ;).
     */
    private static String normalizeClassPath(String s) {
        return StringUtil.replace(s, ":", File.pathSeparator);
    }
}
