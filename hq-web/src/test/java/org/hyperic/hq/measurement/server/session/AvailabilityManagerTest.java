/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2010], Hyperic, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.context.TestContextLoader;
import org.hyperic.hq.db.DatabasePopulator;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.TimingVoodoo;
import org.hyperic.hq.measurement.shared.AvailabilityManager;
import org.hyperic.hq.measurement.shared.MeasurementManager;
import org.hyperic.hq.product.MetricValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;



@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners( { DependencyInjectionTestExecutionListener.class,
                          TransactionalTestExecutionListener.class })
@Transactional
@ContextConfiguration(loader = TestContextLoader.class, locations = { "classpath:META-INF/spring/*-context.xml", "AvailabilityManagerTest-context.xml" })
public class AvailabilityManagerTest {

    private final Log log = LogFactory.getLog(AvailabilityManagerTest.class);
    private static final String AVAIL_TAB = "HQ_AVAIL_DATA_RLE";
    private static final Integer PLAT_MEAS_ID = 10100;
    private static final Integer SERVICE_MEAS_ID = 10224;
    private final List<DataPoint> list = new ArrayList<DataPoint>();

    @Autowired
    private AvailabilityManager aMan;
    @Autowired
    private MeasurementManager mMan;
    @Autowired
    private AvailabilityDataDAO dao;
    @Autowired
    private AvailabilityCheckService availabilityCheckService;
    @Autowired
    private DatabasePopulator dbPopulator;

    public AvailabilityManagerTest() {
    }

    @Before
    public void initializeData() throws Exception {
        //populate DB here so it will be rolled back with rest of test transaction
        //can use DBPopulator or just create test data programatically
        //TODO may not need to load all this data for every test - move this
        //into only test methods that need it?
        dbPopulator.restoreDatabase();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFindLastAvail() {
        List<AvailabilityDataRLE> rle = dao.findLastAvail(Collections.singletonList(10100));
        Assert.assertTrue("rle value is incorrect",
            rle.get(0).getAvailVal() == MeasurementConstants.AVAIL_UP);
        Assert.assertTrue(dao.getClass().getName() == AvailabilityDataDAO.class.getName());
    }

    @Test
    public void testCatchup() throws Exception {
        // need to invoke backfiller once so that its initial time is set
        // so that it can start when invoked the next time
        testCatchup(PLAT_MEAS_ID);
        testCatchup(SERVICE_MEAS_ID);
    }

    /*
     * This test will insert into the middle of two availability points Hits
     * this code path updateOutOfOrderState(List) --> merge(DataPoint) -->
     * insertAvail(AvailabilityDataRLE, AvailabilityDataRLE, DataPoint) MATCHES
     * THIS CONDITION -> } else if (state.getValue() == after.getAvailVal() &&
     * state.getValue() != before.getAvailVal()) {
     */
    @Test
    public void testInsertIntoMiddle() throws Exception {
        setupAvailabilityTable();
        int INCRTIME = 240000;
        long baseTime = TimingVoodoo.roundDownTime(now(), 60000);
        long tmpTime = baseTime;
        DataPoint pt = addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += INCRTIME));
        pt = addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += INCRTIME));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
        pt = addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += INCRTIME));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
        pt = addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += INCRTIME));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
        // insert into the middle
        long middleTime = baseTime + (INCRTIME * 3) + (INCRTIME / 2);
        pt = addData(PLAT_MEAS_ID, new MetricValue(1.0, middleTime));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    @Test
    public void testOverlap() throws Exception {
        List<DataPoint> list = new ArrayList<DataPoint>();
        long now = now();
        long baseTime = TimingVoodoo.roundDownTime(now, 60000);
        DataPoint pt = new DataPoint(PLAT_MEAS_ID, new MetricValue(1.0, baseTime));
        pt = new DataPoint(PLAT_MEAS_ID, new MetricValue(0.5, baseTime));
        pt = new DataPoint(PLAT_MEAS_ID, new MetricValue(1.0, baseTime));
        list.add(pt);
        addData(list);
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    @Test
    public void stressTest2() throws Exception {
        setupAvailabilityTable();
        ArrayList<DataPoint> list = new ArrayList<DataPoint>();
        long now = now();
        long baseTime = TimingVoodoo.roundDownTime(now, 60000);
        long incrTime = 60000;
        long tmpTime = baseTime;
        for (int i = 0; i < 1000; i++) {
            double val = 1.0;
            if ((i % 5) == 0) {
                val = .5;
            }
            list.add(new DataPoint(PLAT_MEAS_ID.intValue(), val, tmpTime += incrTime));
        }
        addData(list);
        list.clear();
        for (int i = 0; i < 1000; i++) {
            list.add(new DataPoint(PLAT_MEAS_ID.intValue(), 0.0, tmpTime += incrTime));
        }
        DataPoint pt = addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    @Test
    public void stressTest1() throws Exception {
        setupAvailabilityTable();
        long now = now();
        long baseTime = TimingVoodoo.roundDownTime(now, 60000);
        long incrTime = 60000;
        DataPoint pt = testCatchup(PLAT_MEAS_ID, baseTime, incrTime);
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
        pt = testCatchup(PLAT_MEAS_ID, (baseTime + 120000), incrTime);
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    private void backfill(long baseTime) {
        availabilityCheckService.backfill(baseTime);
        dao.getSession().clear();
    }

    private void testCatchup(Integer measId) throws Exception {
        setupAvailabilityTable();
        Measurement meas = mMan.getMeasurement(measId);
        long interval = meas.getInterval();
        long now = now();
        long baseTime = TimingVoodoo.roundDownTime(now, 60000);
        DataPoint pt;
        backfill(baseTime);
        List<DataPoint> list = new ArrayList<DataPoint>();
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime));
        list.add(pt);
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime + interval));
        list.add(pt);
        addData(list);
        // want to invoke backfiller slightly offset from the regular interval
        backfill(baseTime + (interval * 6) - 5000);
        List<AvailabilityDataRLE> avails = aMan.getHistoricalAvailData(meas.getResource(),
            baseTime, baseTime + (interval * 100));
        if (avails.size() != 2) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 2);
        // all points should be green in db after this
        for (int i = 0; i < 10; i++) {
            pt = new DataPoint(measId, new MetricValue(1.0, baseTime + (interval * i)));
            list.add(pt);
        }
        addData(list);
        Assert.assertTrue(isAvailDataRLEValid(measId, pt));
        avails = aMan.getHistoricalAvailData(meas.getResource(), baseTime, baseTime +
                                                                           (interval * 10));
        if (avails.size() != 1) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 1);
    }

    private void dumpAvailsToLogger(List<AvailabilityDataRLE> avails) {
        Integer id = null;
        for (AvailabilityDataRLE avail : avails) {
            id = avail.getMeasurement().getId();
            String msg = id + ", " + avail.getStartime() + ", " + avail.getEndtime() + ", " +
                         avail.getAvailVal();
            log.error(msg);
        }
        AvailabilityCache cache = AvailabilityCache.getInstance();
        if (id == null) {
            return;
        }
        synchronized (cache) {
            log.error("Cache info -> " + cache.get(id).getTimestamp() + ", " +
                       cache.get(id).getValue());
        }
    }

    private DataPoint testCatchup(Integer measId, long baseTime, long incrTime) throws Exception {
        baseTime = TimingVoodoo.roundDownTime(baseTime, 60000);
        List<DataPoint> list = new ArrayList<DataPoint>();
        long tmpTime = baseTime;
        DataPoint pt;
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime));
        list.add(pt);
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime + incrTime * 2));
        list.add(pt);
        for (int i = 0; i < 5; i++) {
            double val = 1.0;
            if ((i % 2) == 0) {
                val = 0.0;
            }
            pt = new DataPoint(measId, new MetricValue(val, tmpTime += incrTime));
            list.add(pt);
        }
        addData(list);
        list.clear();
        pt = new DataPoint(measId, new MetricValue(1.0, tmpTime));
        list.add(pt);
        addData(list);
        return pt;
    }

    @Test
    public void testAvailabilityStatusWhenNtwkDwn() throws Exception {
        testAvailabilityForPlatform(PLAT_MEAS_ID);
    }

    private void testAvailabilityForPlatform(Integer measId) throws Exception {
        setupAvailabilityTable();
        Measurement meas = mMan.getMeasurement(measId);
        long interval = meas.getInterval();
        long now = now();
        long baseTime = TimingVoodoo.roundDownTime(now, 600000);
        DataPoint pt;
        List<DataPoint> list = new ArrayList<DataPoint>();
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime));
        list.add(pt);
        pt = new DataPoint(measId, new MetricValue(0.0, baseTime + interval));
        list.add(pt);
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime + interval * 2));
        list.add(pt);
        // Add DataPoints for three consecutive intervals with varying
        // availability data
        addData(list);
        List<AvailabilityDataRLE> avails = aMan.getHistoricalAvailData(meas.getResource(),
            baseTime, baseTime + (interval * 10));
        if (avails.size() != 3) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 3);
        // Assume that the network is down from the interval
        // "baseTime+interval*2"
        // Invoke the backfiller for every two interval
        backfill(baseTime + interval * 4);
        backfill(baseTime + interval * 6);
        backfill(baseTime + interval * 8);
        backfill(baseTime + interval * 10);
        // Expect the backfiller to fill in the unavailable data
        avails = aMan.getHistoricalAvailData(meas.getResource(), baseTime, baseTime +
                                                                           (interval * 10));
        if (avails.size() != 4) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 4);
        list.clear();
        // After the network is up we start getting the availability data for
        // the period when the network was down
        for (int i = 3; i <= 10; i++) {
            pt = new DataPoint(measId, new MetricValue(1.0, baseTime + interval * (i)));
            list.add(pt);
        }
        addData(list);
        // Expect to have 3 availability data after processing the agent data
        // that is sent after network is up
        avails = aMan.getHistoricalAvailData(meas.getResource(), baseTime, baseTime +
                                                                           (interval * 100));
        if (avails.size() != 3) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 3);
    }

    @Test
    public void testBackfillingForService() throws Exception {
        // Following method will verify that when the platform is down it's
        // associated resources will be marked down by the backfiller
        // after waiting for one interval from the last cache update time
        testAvailabilityForService(SERVICE_MEAS_ID);
    }

    private void testAvailabilityForService(Integer measId) throws Exception {
        setupAvailabilityTable();
        setupAvailabilityTable(measId);
        Measurement meas = mMan.getMeasurement(measId);
        long interval = meas.getInterval();
        long now = now();
        long baseTime = TimingVoodoo.roundDownTime(now, 600000);
        DataPoint pt;
        List<DataPoint> list = new ArrayList<DataPoint>();
        // First, let's make the platform as down
        pt = new DataPoint(PLAT_MEAS_ID, new MetricValue(0.0, baseTime));
        list.add(pt);
        pt = new DataPoint(measId, new MetricValue(1.0, baseTime + interval * 10));
        list.add(pt);
        addData(list);
        List<AvailabilityDataRLE> avails = aMan.getHistoricalAvailData(meas.getResource(),
            baseTime, baseTime + (interval * 20));
        if (avails.size() != 1) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 1);
        Measurement meas1 = mMan.getMeasurement(PLAT_MEAS_ID);
        avails = aMan.getHistoricalAvailData(meas1.getResource(), baseTime, baseTime +
                                                                            (interval * 10));
        if (avails.size() != 1) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 1);
        // Invoking the backfiller with exactly the same time of the last update
        // time
        backfill(baseTime + interval * 10);
        avails = aMan.getHistoricalAvailData(meas.getResource(), baseTime, baseTime +
                                                                           (interval * 20));
        if (avails.size() != 1) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 1);
        // Invoking the backfiller one interval after the last update time
        backfill(baseTime + interval * 11);
        avails = aMan.getHistoricalAvailData(meas.getResource(), baseTime, baseTime +
                                                                           (interval * 20));
        if (avails.size() != 2) {
            dumpAvailsToLogger(avails);
        }
        Assert.assertTrue(avails.size() == 2);
        list.clear();
    }

    /*
     * This test will insert into the middle of two availability points Hits
     * this code path updateOutOfOrderState(List) --> merge(DataPoint) -->
     * updateDup(DataPoint, AvailabilityDataRLE) -->
     * insertPointOnBoundry(AvailabilityDataRLE, long, DataPoint) MATCHES THIS
     * CONDITION -> } else if (newStartime == avail.getEndtime()) { } else {
     * dao.updateVal(after, pt.getValue()); }
     */
    @Test
    public void testNonOneorZeroDupPtInsertAtBegin() throws Exception {
        setupAvailabilityTable();
        long INCRTIME = 60000;
        long baseTime = now();
        baseTime = TimingVoodoo.roundDownTime(baseTime, 60000);
        long tmpTime = baseTime;
        DataPoint pt;
        addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += INCRTIME));
        addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += INCRTIME));
        addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += INCRTIME));
        pt = addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += INCRTIME));
        // overwrite first val
        addData(PLAT_MEAS_ID, new MetricValue(0.5, baseTime + INCRTIME));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    /*
     * This test will insert into the middle of two availability points Hits
     * this code path updateOutOfOrderState(List) --> merge(DataPoint) MATCHES
     * THIS CONDITION -> } else if (before == null) {
     */
    @Test
    public void testPrependWithDupValue() throws Exception {
        setupAvailabilityTable();
        long baseTime = now();
        baseTime = TimingVoodoo.roundDownTime(baseTime, 60000);
        long tmpTime = baseTime;
        DataPoint pt;
        addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += 120000));
        addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += 60000));
        addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += 60000));
        pt = addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += 60000));
        // prepend state on to the beginning
        addData(PLAT_MEAS_ID, new MetricValue(1.0, baseTime + 60000));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    /*
     * This test will insert into the middle of two availability points Hits
     * this code path updateOutOfOrderState(List) --> merge(DataPoint) MATCHES
     * THIS CONDITION -> } else if (before == null) {
     */
    @Test
    public void testPrepend() throws Exception {
        setupAvailabilityTable();
        long baseTime = now();
        baseTime = TimingVoodoo.roundDownTime(baseTime, 60000);
        long tmpTime = baseTime;
        DataPoint pt;
        addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += 120000));
        addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += 60000));
        addData(PLAT_MEAS_ID, new MetricValue(0.0, tmpTime += 60000));
        pt = addData(PLAT_MEAS_ID, new MetricValue(1.0, tmpTime += 60000));
        // prepend state on to the beginning
        addData(PLAT_MEAS_ID, new MetricValue(1.0, baseTime + 60000));
        Assert.assertTrue(isAvailDataRLEValid(PLAT_MEAS_ID, pt));
    }

    @SuppressWarnings("unchecked")
    private boolean isAvailDataRLEValid(Integer mId, DataPoint lastPt) {
        List<Integer> mids = Collections.singletonList(mId);
        return isAvailDataRLEValid(mids, lastPt);
    }

    private boolean isAvailDataRLEValid(List<Integer> mids, DataPoint lastPt) {
        boolean descending = false;
        Map<Integer, TreeSet<AvailabilityDataRLE>> avails = dao.getHistoricalAvailMap(
            (Integer[]) mids.toArray(new Integer[0]), 0, descending);
        for (Map.Entry<Integer, TreeSet<AvailabilityDataRLE>> entry : avails.entrySet()) {
            Integer mId = (Integer) entry.getKey();
            Collection<AvailabilityDataRLE> rleList = entry.getValue();
            if (!isAvailDataRLEValid(mId, lastPt, rleList)) {
                return false;
            }
        }
        return true;
    }

    private void setupAvailabilityTable() throws Exception {
        AvailabilityCache cache = AvailabilityCache.getInstance();
        cache.clear();
        dao.getSession().clear();
        boolean descending = false;
        long start = 0l;
        long end = AvailabilityDataRLE.getLastTimestamp();
        Integer[] mids = new Integer[1];
        mids[0] = PLAT_MEAS_ID;
        List<AvailabilityDataRLE> avails = dao.getHistoricalAvails(mids, start, end, descending);
        for (AvailabilityDataRLE avail : avails) {
            dao.remove(avail);
        }
        log.info("deleted " + avails.size() + " rows from " + AVAIL_TAB +
                  " with measurement Id = " + PLAT_MEAS_ID);
    }

    private void setupAvailabilityTable(Integer measId) throws Exception {
        AvailabilityCache cache = AvailabilityCache.getInstance();
        cache.clear();
        dao.getSession().clear();
        boolean descending = false;
        long start = 0l;
        long end = AvailabilityDataRLE.getLastTimestamp();
        Integer[] mids = new Integer[1];
        mids[0] = measId;
        List<AvailabilityDataRLE> avails = dao.getHistoricalAvails(mids, start, end, descending);
        for (AvailabilityDataRLE avail : avails) {
            dao.remove(avail);
        }
        log.info("deleted " + avails.size() + " rows from " + AVAIL_TAB +
                  " with measurement Id = " + PLAT_MEAS_ID);
    }

    private boolean isAvailDataRLEValid(Integer measId, DataPoint lastPt,
                                        Collection<AvailabilityDataRLE> avails) {
        AvailabilityDataRLE last = null;
        Set<Long> endtimes = new HashSet<Long>();
        for (AvailabilityDataRLE avail : avails) {
            Long endtime = new Long(avail.getEndtime());
            if (endtimes.contains(endtime)) {
                log.error("list for MID=" + measId + " contains two or more of the same endtime=" +
                           endtime);
                return false;
            }
            endtimes.add(endtime);
            if (last == null) {
                last = avail;
                continue;
            }
            if (last.getAvailVal() == avail.getAvailVal()) {
                log.error("consecutive availpoints have the same value");
                return false;
            } else if (last.getEndtime() != avail.getStartime()) {
                log.error("there are gaps in the availability table");
                return false;
            }
            last = avail;
        }
        AvailabilityCache cache = AvailabilityCache.getInstance();
        if (cache.get(measId).getValue() != lastPt.getValue()) {
            log.error("last avail data point does not match cache");
            return false;
        }
        return true;
    }

    private void addData(List<DataPoint> vals) {
        for (DataPoint val : vals) {
            log.info("adding timestamp=" + val.getTimestamp() + ", value=" + val.getValue());
        }
        list.clear();
        list.addAll(vals);
        aMan.addData(list);
        dao.getSession().clear();
    }

    private DataPoint addData(Integer measId, MetricValue mVal) {
        log.info("adding timestamp=" + mVal.getTimestamp() + ", value=" + mVal.getValue());
        list.clear();
        DataPoint pt = new DataPoint(measId, mVal);
        list.add(pt);
        aMan.addData(list);
        dao.getSession().clear();
        return pt;
    }

    private static final long now() {
        // return System.currentTimeMillis();
        return 1265438400000l;
    }

}
