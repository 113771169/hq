/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2007], Hyperic, Inc.
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

package org.hyperic.hq.bizapp.server.action.email;

import java.io.File;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.AuthzSubjectManagerEJBImpl;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceDAO;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerLocal;
import org.hyperic.hq.bizapp.shared.action.EmailActionConfig;
import org.hyperic.hq.escalation.server.session.Escalatable;
import org.hyperic.hq.escalation.server.session.EscalationStateChange;
import org.hyperic.hq.escalation.server.session.PerformsEscalations;
import org.hyperic.hq.events.ActionExecuteException;
import org.hyperic.hq.events.ActionExecutionInfo;
import org.hyperic.hq.events.ActionInterface;
import org.hyperic.hq.events.AlertDefinitionInterface;
import org.hyperic.hq.events.AlertInterface;
import org.hyperic.hq.events.InvalidActionDataException;
import org.hyperic.hq.events.Notify;
import org.hyperic.hq.hqu.rendit.RenditServer;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.util.config.ConfigResponse;

public class EmailAction extends EmailActionConfig
    implements ActionInterface, Notify
{
    public static final String RES_NAME_HOLDER = "RES_NAME_REPL";
    public static final String RES_DESC_HOLDER = "RES_DESC_REPL";

    protected static String baseUrl = null;

    private Log _log = LogFactory.getLog(EmailAction.class);

    private AuthzSubjectManagerLocal subjMan;

    public EmailAction() {
    }

    protected AuthzSubjectManagerLocal getSubjMan() {
        if (subjMan == null) {
            subjMan = AuthzSubjectManagerEJBImpl.getOne();
        }
        return subjMan;
    }

    private String renderTemplate(String filename, Map params) {
        File templateDir = new File(HQApp.getInstance().getResourceDir(),
                                    "alertTemplates");
        File templateFile = new File(templateDir, filename);
        StringWriter output = new StringWriter();
        try {
            RenditServer.getInstance().renderTemplate(templateFile, params, 
                                                      output);
            
            if (_log.isDebugEnabled())
                _log.debug("Template rendered\n" + output.toString());
        } catch(Exception e) {
            _log.warn("Unable to render template", e);
        }
        return output.toString();
    }

    private String createSubject(AlertDefinitionInterface alertdef,
                                 AlertInterface alert, Resource resource,
                                 String status) {
        Map params = new HashMap();
        params.put("resource", resource);
        params.put("alertDef", alertdef);
        params.put("alert", alert);
        params.put("status", status);
        params.put("isSms", new Boolean(isSms()));

        return renderTemplate("subject.gsp", params);
    }

    private String createText(AlertDefinitionInterface alertdef,
                              ActionExecutionInfo info, Resource resource, 
                              AlertInterface alert, String templateName,
                              AuthzSubject user)
        throws MeasurementNotFoundException
    {
        Map params = new HashMap();
        
        params.put("alertDef", alertdef);
        params.put("alert", alert);
        params.put("action", info);
        params.put("resource", resource);
        params.put("user", user);
        
        return renderTemplate(templateName, params);
    }
    
    private AppdefEntityID getResource(AlertDefinitionInterface def) {
        return new AppdefEntityID(def.getResource());
    }

    public String execute(AlertInterface alert, ActionExecutionInfo info) 
        throws ActionExecuteException 
    {
        try {
            Map addrs = lookupEmailAddr();
            
            if (addrs.isEmpty()) {
                return "No valid users or emails found to send alert";
            }

            EmailFilter filter = new EmailFilter();

            AlertDefinitionInterface alertDef =
                alert.getAlertDefinitionInterface();
            AppdefEntityID appEnt = getResource(alertDef);
            
            String logStr = "No notifications sent, see server log for details.";
            if (appEnt != null) {

            	Resource resource = alertDef.getResource();
            	if (resource != null && !resource.isInAsyncDeleteState()) {
            		String[] body = new String[addrs.size()];
            		String[] htmlBody = new String[addrs.size()];
            		EmailRecipient[] to = (EmailRecipient[])
            		addrs.keySet().toArray(new EmailRecipient[addrs.size()]);

            		for (int i = 0; i < to.length; i++) {
            			AuthzSubject user = (AuthzSubject) addrs.get(to[i]);
            			if (to[i].useHtml()) {
            				htmlBody[i] = createText(alertDef, info, resource, alert,
            						"html_email.gsp", user);
            			}
            			body[i] = createText(alertDef, info, resource, alert, 
            					isSms() ? "sms_email.gsp" :
            						"text_email.gsp", user);
            		}

            		filter.sendAlert(appEnt, to,
            				createSubject(alertDef, alert, resource, ""),
            				body, htmlBody, alertDef.getPriority(),
            				alertDef.isNotifyFiltered());

            		StringBuffer result = getLog(to);
            		logStr = result.toString();

            	} else {
            		_log.warn("No resource for alert definition " + alertDef.getId() +
            				  ", perhaps the resource was deleted?  Email notification will not be sent.");
            	}
            } else {
        		_log.warn("No appdef entity ID for alert definition " + alertDef.getId() +
				  ", perhaps the related platform was deleted?  Email notification will not be sent.");
            }
            
            return logStr;
            
        } catch (Exception e) {
            throw new ActionExecuteException(e);
        }
    }

    protected StringBuffer getLog(EmailRecipient[] to) {
        StringBuffer result = new StringBuffer(isSms() ? "SMS" : "Notified");
        // XXX: Should get this strings into a resource file
        switch (getType()) {
        case TYPE_USERS :
            result.append(" users: ");
            break;
        default :
        case TYPE_EMAILS :
            result.append(": ");
            break;
        }
        
        for (int i = 0; i < to.length; i++) {
            result.append(to[i].getAddress().getPersonal());
            if (i < to.length - 1) {
                result.append(", ");
            }
        }

        return result;
    }

    protected Map lookupEmailAddr()
        throws ActionExecuteException
    {
        // First, look up the addresses
        HashSet prevRecipients = new HashSet();
        Map validRecipients = new HashMap();
        for (Iterator it = getUsers().iterator(); it.hasNext(); ) {
            try {
                InternetAddress addr;
                boolean useHtml = false;
                AuthzSubject who = null;
                
                switch (getType()) {
                case TYPE_USERS:
                    Integer uid = (Integer) it.next();
                    who = getSubjMan().getSubjectById(uid);
                    
                    if (who == null) {
                        _log.warn("User not found: " + uid);
                        continue;
                    }

                    if (isSms()) {
                        addr = new InternetAddress(who.getSMSAddress());
                    } else {
                        addr = new InternetAddress(who.getEmailAddress());
                    }
                    addr.setPersonal(who.getName());
                    useHtml = isSms() ? false : who.isHtmlEmail();
                    break;
                default:
                case TYPE_EMAILS:
                    addr = new InternetAddress((String) it.next(), true);
                    addr.setPersonal(addr.getAddress());
                    break;
                }
                
                // Don't send duplicate notifications
                if (prevRecipients.add(addr)) {
                    validRecipients.put(new EmailRecipient(addr, useHtml), who);
                }
            } catch (AddressException e) {
                _log.warn("Mail address invalid", e);
                continue;
            } catch (UnsupportedEncodingException e) {
                _log.warn("Username encoding error", e);
                continue;
            } catch (Exception e) {
                _log.warn("Email lookup failed");
                _log.debug("Email lookup failed", e);
                continue;
            }
        }

        return validRecipients;
    }

    public void setParentActionConfig(AppdefEntityID ent, ConfigResponse cfg)
        throws InvalidActionDataException 
    {
        init(cfg);
    }

    public void send(Escalatable alert, EscalationStateChange change, 
                           String message, Set notified)
        throws ActionExecuteException 
    {
        PerformsEscalations def = alert.getDefinition();
        
        Map addrs = lookupEmailAddr();

        EmailFilter filter = new EmailFilter();

        for (Iterator it = addrs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            EmailRecipient rec = (EmailRecipient) entry.getKey();
            
            // Don't notify again if already notified
            if (notified.contains(rec.getAddress())) {
                it.remove();
                continue;
            }
            
            rec.setHtml(false);
            notified.add(rec.getAddress());
        }
        AlertDefinitionInterface defInfo = def.getDefinitionInfo();
        String[] messages = new String[addrs.size()];
        Arrays.fill(messages, message);
        
        EmailRecipient[] to = (EmailRecipient[])
            addrs.keySet().toArray(new EmailRecipient[addrs.size()]);

        AppdefEntityID appEnt = getResource(defInfo);
        ResourceDAO rDao = new ResourceDAO(DAOFactory.getDAOFactory());
        Resource resource = rDao.findByInstanceId(appEnt.getAuthzTypeId(),
                                                  appEnt.getId());

        filter.sendAlert(getResource(defInfo), to, 
                         createSubject(defInfo, alert.getAlertInfo(), resource,
                                       change.getDescription()), 
                         messages, messages, defInfo.getPriority(), false);
    }
}
