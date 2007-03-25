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

package org.hyperic.hq.livedata.server.session;

import edu.emory.mathcs.backport.java.util.concurrent.ThreadPoolExecutor;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue;
import org.hyperic.hq.livedata.agent.client.LiveDataClient;
import org.hyperic.hq.livedata.shared.LiveDataResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class LiveDataExecutor extends ThreadPoolExecutor {

    private static Log _log = LogFactory.getLog(LiveDataExecutor.class);

    private static final int THREAD_MIN = 1;
    private static final int THREAD_MAX = 30;

    private List _results;

    public LiveDataExecutor() {
        super(THREAD_MIN, THREAD_MAX, 1, TimeUnit.HOURS,
              new LinkedBlockingQueue());
        _results = new ArrayList();
    }

    public void getData(LiveDataClient client, List commands) {
        execute(new LiveDataGatherer(client, commands));
    }

    public LiveDataResult[] getResult() {
        try {
            awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            _log.warn("Executor interrputed!");
        }

        _log.debug("Returning results for " + _results.size() + " elements");
        return (LiveDataResult[])_results.toArray(new LiveDataResult[0]);
    }
        
    private class LiveDataGatherer implements Runnable {

        private LiveDataClient _client;
        private List _commands;

        LiveDataGatherer(LiveDataClient client, List commands) {
            _client = client;
            _commands = commands;
        }

        public void run() {
            _log.debug("Starting gather thread...");
            for (Iterator i = _commands.iterator(); i.hasNext(); ) {
                LiveDataExecutorCommand cmd = (LiveDataExecutorCommand)i.next();
                _log.debug("Running cmd '" + cmd + "' in thread " +
                           Thread.currentThread().getName());
                LiveDataResult res = _client.getData(cmd.getType(),
                                                     cmd.getCommand(),
                                                     cmd.getConfig());
                _results.add(res);
            }
        }
    }
}
