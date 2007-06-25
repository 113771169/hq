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

package org.hyperic.hq.measurement;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Properties;

import javax.ejb.CreateException;
import javax.ejb.RemoveException;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.shared.HQConstants;
import org.hyperic.hq.common.shared.ServerConfigManagerLocal;
import org.hyperic.hq.common.shared.ServerConfigManagerUtil;
import org.hyperic.hq.events.shared.EventLogManagerLocal;
import org.hyperic.hq.events.shared.EventLogManagerUtil;
import org.hyperic.hq.measurement.shared.DataCompressLocal;
import org.hyperic.hq.measurement.shared.DataCompressUtil;
import org.hyperic.util.TimeUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class DataPurgeJob implements Job {

    private static final Log _log = LogFactory.getLog(DataPurgeJob.class);
    private static final Object RUNNING_LOCK = new Object();
    private static boolean running = false;

    private static long HOUR = MeasurementConstants.HOUR;
    private static long MINUTE = MeasurementConstants.MINUTE;

    /**
     * Public interface for quartz 
     */
    public void execute(JobExecutionContext context)
        throws JobExecutionException {

        try {
            DataPurgeJob.compressData();

            Properties conf; 
            try { 
                conf =
                    ServerConfigManagerUtil.getLocalHome().create().getConfig(); 
            } catch (Exception e) { 
                throw new SystemException(e); 
            }

            long now = System.currentTimeMillis();
            
            purge(conf, now);
        } catch (CreateException e) {
            throw new JobExecutionException(
                "Unable to create instance of DataManager.", e, false);
        } catch (NamingException e) {
            throw new JobExecutionException(
                "Unable to look up DataManager.", e, false);
        }
    }
    
    /**
     * Entry point into compression routine
     */
    public static void compressData()
        throws CreateException, NamingException
    {
        ServerConfigManagerLocal serverConfig =
            ServerConfigManagerUtil.getLocalHome().create();

        DataCompressLocal dataCompress = 
            DataCompressUtil.getLocalHome().create();

        // First check if we are already running
        synchronized (RUNNING_LOCK) {
            if (running) {
                _log.info("Not starting data compression. (Already running)");
                return;
            } else {
                running = true;
            }
        }

        long time_start = System.currentTimeMillis();
        
        try {
            // First purge backfilled data
            try {
                dataCompress.purgeBackfilled();
            } catch (SQLException e) {
                _log.error("Unable to clear out duplicated backfilled data");
            }
    
            // We'll only do the rest of the maintenance if it's 10 past the
            // hour
            if (TimingVoodoo.roundDownTime(time_start, MINUTE) == 10) {
                return;
            }
    
            // Announce
            _log.info("Data compression starting at " +
                      TimeUtil.toString(time_start));
            
            dataCompress.compressData();
        } catch (SQLException e) {
            _log.error("Unable to compress data: " + e, e);
        } finally {
            synchronized (RUNNING_LOCK) {
                running = false;
            }
        }
        
        long time_end = System.currentTimeMillis();
        _log.info("Data compression completed in " +
                  ((time_end - time_start)/1000) +
                  " seconds.");

        // Once compression finishes, we check to see if databae maintaince
        // should be performed.  This is defaulted to 1 hour, so it should
        // always run unless changed by the user.  This is only a safeguard,
        // as usually an ANALYZE only takes a fraction of what a full VACUUM
        // takes.
        //
        // VACUUM will occur every day at midnight.

        Properties conf;
        
        try {
            conf = ServerConfigManagerUtil.getLocalHome().create().getConfig();
        } catch(Exception e) {
            throw new SystemException(e);
        }

        String dataMaintenance = conf.getProperty(HQConstants.DataMaintenance);
        if (dataMaintenance == null) {
            // Should never happen
            _log.error("No data maintenance interval found");
            return;
        }

        long maintInterval = Long.parseLong(dataMaintenance);
        if (maintInterval <= 0) {
            _log.error("Maintenance interval was specified as [" + 
                       dataMaintenance + "] -- which is invalid");
            return;
        }
        
        // At midnight we always perform a VACUUM, otherwise we
        // check to see if it is time to perform normal database
        // maintenance. (On postgres we just rebuild indicies
        // using an ANALYZE)
        Calendar cal = Calendar.getInstance();
        if (TimingVoodoo.roundDownTime(time_start, HOUR) ==
            TimingVoodoo.roundDownTime(time_start, maintInterval)) {
            _log.info("Performing database maintenance (VACUUM ANALYZE)");
            serverConfig.vacuum();

            String reindexStr = conf.getProperty(HQConstants.DataReindex);
            boolean reindexNightly = Boolean.valueOf(reindexStr).booleanValue();
            if (cal.get(Calendar.HOUR_OF_DAY) == 0 && reindexNightly) {
                _log.info("Re-indexing HQ data tables");
                serverConfig.reindex();
            }

            _log.info("Database maintenance completed in " +
                      ((System.currentTimeMillis() - time_end)/1000) +
                      " seconds.");
        } else {
            _log.info("Not performing database maintenance");
        }
    }

    protected void purge(Properties conf, long now)
        throws CreateException, NamingException {
        DataPurgeJob.purgeEventLogs(conf, now);
    }
    
    /**
     * Purge Event Log data
     */
    private static void purgeEventLogs(Properties conf, long now)
        throws CreateException, NamingException {
            String purgeEventString =
                conf.getProperty(HQConstants.EventLogPurge);
            long purgeEventLog = Long.parseLong(purgeEventString);
        
            // Purge event logs
            EventLogManagerLocal eventLogManager =
                EventLogManagerUtil.getLocalHome().create();
            
            _log.info("Purging events older than " +
                     TimeUtil.toString(now - purgeEventLog));
            try {
                eventLogManager.deleteLogs(0, now - purgeEventLog);
                _log.info("Done (Deleting events)");
            } catch (RemoveException e) {
                _log.error("Unable to delete events: " + e.getMessage(), e);
            }
        }
}
