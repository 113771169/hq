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

package org.hyperic.hq.measurement.server.session;

import java.util.HashSet;
import java.util.Set;

import org.hyperic.hq.appdef.server.session.ResourceCreatedZevent;
import org.hyperic.hq.appdef.server.session.ResourceUpdatedZevent;
import org.hyperic.hq.application.HQApp;
import org.hyperic.hq.application.StartupListener;
import org.hyperic.hq.zevents.ZeventManager;

public class MeasurementStartupListener
    implements StartupListener
{
    private static final Object LOCK = new Object();
    private static DefaultMetricEnableCallback _defEnableCallback;
    private static MetricDeleteCallback _deleteCallback;
    
    public void hqStarted() {
        SRNManagerEJBImpl.getOne().initializeCache();
    
        /**
         * Add measurement enabler listener to enable metrics for newly
         * created resources or to reschedule when resources are updated.
         */
        Set listenEvents = new HashSet();
        listenEvents.add(ResourceCreatedZevent.class);
        listenEvents.add(ResourceUpdatedZevent.class);
        ZeventManager.getInstance()
                     .addBufferedListener(listenEvents,
                                          new MeasurementEnabler());

        HQApp app = HQApp.getInstance();

        synchronized (LOCK) {
            _defEnableCallback = (DefaultMetricEnableCallback)
                app.registerCallbackCaller(DefaultMetricEnableCallback.class);
            _deleteCallback = (MetricDeleteCallback)
                app.registerCallbackCaller(MetricDeleteCallback.class);
        }
    }
    
    static DefaultMetricEnableCallback getDefaultEnableObj() {
        synchronized (LOCK) {
            return _defEnableCallback;
        }
    }
    
    static MetricDeleteCallback getDeleteMetricCallback() {
        synchronized (LOCK) {
            return _deleteCallback;
        }
    }
}
