/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2009], Hyperic, Inc.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.application.Scheduler;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.server.session.AuthzSubjectManagerEJBImpl;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.authz.server.session.ResourceDAO;
import org.hyperic.hq.authz.shared.AuthzSubjectManagerLocal;
import org.hyperic.hq.bizapp.shared.action.EmailActionConfig;
import org.hyperic.hq.common.server.session.ServerConfigManagerEJBImpl;
import org.hyperic.hq.common.shared.ServerConfigManagerLocal;
import org.hyperic.hq.escalation.server.session.Escalatable;
import org.hyperic.hq.escalation.server.session.EscalationStateChange;
import org.hyperic.hq.escalation.server.session.PerformsEscalations;
import org.hyperic.hq.events.ActionExecuteException;
import org.hyperic.hq.events.ActionExecutionInfo;
import org.hyperic.hq.events.ActionInterface;
import org.hyperic.hq.events.AlertDefinitionInterface;
import org.hyperic.hq.events.AlertInterface;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.events.InvalidActionDataException;
import org.hyperic.hq.events.Notify;
import org.hyperic.hq.events.server.session.AlertRegulator;
import org.hyperic.hq.hqu.rendit.RenditServer;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.util.ConfigPropertyException;
import org.hyperic.util.StringUtil;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.stats.ConcurrentStatsCollector;

public class EmailAction extends EmailActionConfig
    implements ActionInterface, Notify
{
    public static final String RES_NAME_HOLDER = "RES_NAME_REPL";
    public static final String RES_DESC_HOLDER = "RES_DESC_REPL";

    protected static String baseUrl = null;
    private static final int _alertThreshold;
    private static final List _emails = new ArrayList();
    private static EmailRecipient[] _emailAddrs;
    // Evaluate number of notifications each period when AlertThreshold is
    // enabled to potentially toggle/block all notifications for 1 - Many
    // THRESHOLD_WINDOW(s).  Notifications are only sent out after each period
    // when mechanism is turned on.
    // Matched it up with ConcurrentStatsCollector in order to obtain easy
    // stats on a deployment's activity
    private static final long EVALUATION_PERIOD =
        ConcurrentStatsCollector.WRITE_PERIOD*1000;
    private static final String EMAIL_ACTIONS =
        ConcurrentStatsCollector.EMAIL_ACTIONS;
    // evaluation window to continue and block notifications or
    // toggle back to regular operation
    private static final int THRESHOLD_WINDOW = 10*60*1000;

    private static final Log _log = LogFactory.getLog(EmailAction.class);
    private static final String BUNDLE = "org.hyperic.hq.bizapp.Resources";

    private ResourceBundle resourceBundle;

    static {
        ServerConfigManagerLocal sConf = ServerConfigManagerEJBImpl.getOne();
        int tmp = 0;
        try {
            final Properties props = sConf.getConfig();
            tmp = Integer.parseInt(props.getProperty("HQ_ALERT_THRESHOLD", "0"));
            String[] array =
                props.getProperty("HQ_ALERT_THRESHOLD_EMAILS", "").split(",");
            _emailAddrs = new EmailRecipient[array.length];
            for (int i=0; i<array.length; i++) {
                try {
                    _emailAddrs[i] =
                        new EmailRecipient(new InternetAddress(array[i]), false);
                } catch (AddressException e) {
                    _log.debug(e.getMessage(), e);
                }
            }
        } catch (NumberFormatException e) {
            _log.debug(e.getMessage(), e);
        } catch (ConfigPropertyException e) {
            _log.debug(e.getMessage(), e);
        }
        _alertThreshold = (_emailAddrs.length == 0) ? 0 : tmp;
        if (_alertThreshold > 0) {
            HQApp.getInstance().getScheduler().scheduleWithFixedDelay(
                new ThresholdWorker(), Scheduler.NO_INITIAL_DELAY,
                EVALUATION_PERIOD);
        }
    }

    public EmailAction() {
    }

    protected final AuthzSubjectManagerLocal getSubjMan() {
        return AuthzSubjectManagerEJBImpl.getOne();
    }
    
    private ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            resourceBundle = ResourceBundle.getBundle(BUNDLE);
        }
        return resourceBundle;
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
            if (!AlertRegulator.getInstance().alertNotificationsAllowed()) {
                return getResourceBundle()
                            .getString("action.email.error.notificationDisabled");
            }
            
            Map addrs = lookupEmailAddr();
            
            if (addrs.isEmpty()) {
                return getResourceBundle()
                            .getString("action.email.error.noEmailAddress");
            }

            EmailFilter filter = new EmailFilter();

            AlertDefinitionInterface alertDef =
                alert.getAlertDefinitionInterface();
            AppdefEntityID appEnt = getResource(alertDef);

            Resource resource = alertDef.getResource();

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

            final String subject = createSubject(alertDef, alert, resource, "");
            sendAlert(filter, appEnt, to, subject, body, htmlBody,
                alertDef.getPriority(), alertDef.isNotifyFiltered());

            StringBuffer result = getLog(to);
            return result.toString();
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

        final String subject = createSubject(
            defInfo, alert.getAlertInfo(), resource, change.getDescription());
        sendAlert(filter, getResource(defInfo), to, subject, messages, messages,
            defInfo.getPriority(), false);
    }

    private void sendAlert(EmailFilter filter, AppdefEntityID appEnt,
                           EmailRecipient[] to, String subject, String[] body,
                           String[] htmlBody, int priority,
                           boolean notifyFiltered) {
        if (_alertThreshold <= 0) {
            final boolean debug = _log.isDebugEnabled();
            if (debug) {
                EmailObj obj = new EmailObj(filter, appEnt, to, subject, body,
                    htmlBody, priority, notifyFiltered);
                debug(obj);
            }
            filter.sendAlert(
                appEnt, to, subject, body, htmlBody, priority, notifyFiltered);
            ConcurrentStatsCollector.getInstance().addStat(1, EMAIL_ACTIONS);
            return;
        }
        synchronized (_emails) {
            EmailObj obj = new EmailObj(filter, appEnt, to, subject, body,
                htmlBody, priority, notifyFiltered);
            _emails.add(obj);
        }
        ConcurrentStatsCollector.getInstance().addStat(1, EMAIL_ACTIONS);
    }

    private static final long now() {
        return System.currentTimeMillis();
    }

    private static void debug(EmailObj obj) {
        final boolean debug = _log.isDebugEnabled();
        if (debug) {
            final String msg = "Sending alert with info -> " +
                obj.getAppEnt().getID() + ':' +
                StringUtil.implode(Arrays.asList(obj.getTo()), ",") + ':' +
                obj.getSubject() + ':' + obj.getPriority();
            _log.debug(msg);
        }
    }

    private static class ThresholdWorker implements Runnable {
        private long _lastEmailTime = -1l;
        private boolean _inThresholdWindow = false;
        private String _endMsg = null,
                       _beginMsg = null,
                       _continueMsg = null,
                       _endSubject = null,
                       _beginSubject = null,
                       _continueSubject = null;

        public synchronized void run() {
            try {
                List toEmail = null;
                synchronized(_emails) {
                    if (_emails.size() == 0) {
                        return;
                    }
                    toEmail = new ArrayList(_emails);
                    _emails.clear();
                }
                if (!_inThresholdWindow) {
                    _inThresholdWindow = 
                        (toEmail.size() >= _alertThreshold) ? true : false;
                }
                if (_inThresholdWindow && lastEmailWithinThresholdWindow()) {
                    // if we are already in a threshold window then there is
                    // nothing to do until the window has ended
                    // just drop all emails
                    if (_log.isDebugEnabled()) {
                        _log.debug("In Threshold Window, dropping " +
                            toEmail.size() + " email(s)");
                    }
                    return;
                } else if (_inThresholdWindow && _lastEmailTime == -1l) {
                    _lastEmailTime = now();
                    sendRollupEmail(true, toEmail.size());
                    return;
                } else if (_inThresholdWindow &&
                           !lastEmailWithinThresholdWindow()) {
                    // this means that the threshold window has ended
                    // Need to send an email notifying the users that it has
                    // ended -OR- that it will continue
                    _inThresholdWindow =
                        (toEmail.size() >= _alertThreshold) ? true : false;
                    sendRollupEmail(false, toEmail.size());
                    _lastEmailTime = _inThresholdWindow ? now() : -1l;
                    return;
                } else {
                    // send all emails, alert storm is not in affect
                    for (final Iterator it=toEmail.iterator(); it.hasNext(); ) {
                        final EmailObj obj = (EmailObj)it.next();
                        final EmailFilter filter = obj.getFilter();
                        debug(obj);
                        filter.sendAlert(obj.getAppEnt(), obj.getTo(),
                            obj.getSubject(), obj.getBody(), obj.getHtmlBody(),
                            obj.getPriority(), obj.isNotifyFiltered());
                    }
                    return;
                }
            } catch (Throwable e) {
                _log.error(e.getMessage(), e);
                return;
            }
        }

        private final boolean lastEmailWithinThresholdWindow() {
            return (_lastEmailTime > (now() - THRESHOLD_WINDOW)) ? true : false;
        }

        private final void sendRollupEmail(boolean startWindow,
                                           int notificationCount)
            throws AddressException
        {
            String msg = "",
                   subject = "";
            if (startWindow) {
                msg = getWindowStartMsg(notificationCount);
                subject = getWindowStartSubject();
            } else if (_inThresholdWindow) {
                msg = getWindowContinueMsg(notificationCount);
                subject = getWindowContinueSubject();
            } else {
                msg = getWindowEndMsg();
                subject = getWindowEndSubject();
            }
            final EmailRecipient[] recipients = getEmailRecipients();
            final String[] message = new String[recipients.length];
            for (int i=0; i<recipients.length; i++) {
                message[i] = msg;
            }
            if (_log.isDebugEnabled()) {
                _log.debug("Sending Threshold Email to " +
                    StringUtil.implode(Arrays.asList(recipients), ",") +
                    ',' + " msg: " + msg);
            }
            EmailFilter.sendEmail(recipients, subject, message,
                message, new Integer(EventConstants.PRIORITY_HIGH));
        }

        private final String getWindowEndSubject() {
            if (_endSubject != null) {
                return _endSubject;
            }
            _endSubject = ResourceBundle.getBundle(BUNDLE).getString(
                "alert.threshold.subject.end.message");
            return _endSubject;
        }

        private final String getWindowContinueSubject() {
            if (_continueSubject != null) {
                return _continueSubject;
            }
            _continueSubject = ResourceBundle.getBundle(BUNDLE).getString(
                "alert.threshold.subject.end.message");
            return _continueSubject;
        }

        private final String getWindowStartSubject() {
            if (_beginSubject != null) {
                return _beginSubject;
            }
            _beginSubject = ResourceBundle.getBundle(BUNDLE).getString(
                "alert.threshold.subject.end.message");
            return _beginSubject;
        }

        private final String getWindowEndMsg() {
            if (_endMsg != null) {
                return _endMsg;
            }
            _endMsg = ResourceBundle.getBundle(BUNDLE).getString(
                "alert.threshold.end.message");
            return _endMsg;
        }

        private final String getWindowContinueMsg(int notificationCount) {
            if (_continueMsg != null) {
                return _continueMsg.replaceAll("\\{0\\}", notificationCount+"");
            }
            _continueMsg = ResourceBundle.getBundle(BUNDLE).getString(
                "alert.threshold.continue.message");
            _continueMsg =
                _continueMsg.replaceAll("\\{1\\}", EVALUATION_PERIOD/1000+"")
                            .replaceAll("\\{2\\}", THRESHOLD_WINDOW/60000+"");
            return _continueMsg;
        }

        private final String getWindowStartMsg(int notificationCount) {
            if (_beginMsg != null) {
                return _beginMsg.replaceAll("\\{0\\}", notificationCount+"")
                                .replaceAll("\\{2\\}", _alertThreshold+"");
            }
            _beginMsg = ResourceBundle.getBundle(BUNDLE).getString(
                "alert.threshold.begin.message");
            _beginMsg =
                _beginMsg.replaceAll("\\{1\\}", EVALUATION_PERIOD/1000+"")
                         .replaceAll("\\{3\\}", THRESHOLD_WINDOW/60000+"");
            return _beginMsg.replaceAll("\\{0\\}", notificationCount+"")
                            .replaceAll("\\{2\\}", _alertThreshold+"");
        }

        private EmailRecipient[] getEmailRecipients() throws AddressException {
            return _emailAddrs;
        }
    }

    private class EmailObj {
        private final EmailFilter _filter;
        private final AppdefEntityID _appEnt;
        private final EmailRecipient[] _to;
        private final String _subject;
        private final String[] _body;
        private final String[] _htmlBody;
        private final int _priority;
        private final boolean _notifyFiltered;
        public EmailObj(EmailFilter filter, AppdefEntityID appEnt,
                        EmailRecipient[] to, String subject, String[] body,
                        String[] htmlBody, int priority,
                        boolean notifyFiltered) {
            _filter = filter;
            _appEnt = appEnt;
            _to = to;
            _subject = subject;
            _body = body;
            _htmlBody = htmlBody;
            _priority = priority;
            _notifyFiltered = notifyFiltered;
        }
        public EmailFilter getFilter() {
            return _filter;
        }
        public AppdefEntityID getAppEnt() {
            return _appEnt;
        }
        public EmailRecipient[] getTo() {
            return _to;
        }
        public String getSubject() {
            return _subject;
        }
        public String[] getBody() {
            return _body;
        }
        public String[] getHtmlBody() {
            return _htmlBody;
        }
        public int getPriority() {
            return _priority;
        }
        public boolean isNotifyFiltered() {
            return _notifyFiltered;
        }
    }
}
