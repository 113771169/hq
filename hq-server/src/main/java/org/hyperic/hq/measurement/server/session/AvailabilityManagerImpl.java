/*
 * NOTE: This copyright does *not* cover user programs that use Hyperic
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2010], VMware, Inc.
 * This file is part of Hyperic.
 *
 * Hyperic is free software; you can redistribute it and/or modify
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.AppdefResourceValue;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.appdef.shared.PlatformManager;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.authz.shared.ResourceGroupManager;
import org.hyperic.hq.authz.shared.ResourceManager;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.events.MaintenanceEvent;
import org.hyperic.hq.events.ext.RegisteredTriggers;
import org.hyperic.hq.inventory.domain.Resource;
import org.hyperic.hq.inventory.domain.ResourceGroup;
import org.hyperic.hq.measurement.MeasurementConstants;
import org.hyperic.hq.measurement.MeasurementNotFoundException;
import org.hyperic.hq.measurement.TimingVoodoo;
import org.hyperic.hq.measurement.data.AvailabilityDataRepository;
import org.hyperic.hq.measurement.data.MeasurementRepository;
import org.hyperic.hq.measurement.ext.DownMetricValue;
import org.hyperic.hq.measurement.ext.MeasurementEvent;
import org.hyperic.hq.measurement.shared.AvailabilityManager;
import org.hyperic.hq.measurement.shared.HighLowMetricValue;
import org.hyperic.hq.measurement.shared.MeasurementManager;
import org.hyperic.hq.messaging.MessagePublisher;
import org.hyperic.hq.product.AvailabilityMetricValue;
import org.hyperic.hq.product.MetricValue;
import org.hyperic.hq.stats.ConcurrentStatsCollector;
import org.hyperic.hq.zevents.ZeventManager;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.timer.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The AvailabityManagerImpl class is a stateless session bean that can be used
 * to retrieve Availability Data RLE points
 * 
 */
@Service
@Transactional
public class AvailabilityManagerImpl implements AvailabilityManager {

    private final Log _log = LogFactory.getLog(AvailabilityManagerImpl.class);
    private final Log _traceLog = LogFactory.getLog(AvailabilityManagerImpl.class.getName() + "Trace");
    private static final double AVAIL_NULL = MeasurementConstants.AVAIL_NULL;
    private static final double AVAIL_DOWN = MeasurementConstants.AVAIL_DOWN;
    private static final double AVAIL_UNKNOWN = MeasurementConstants.AVAIL_UNKNOWN;
    private static final int IND_MIN = MeasurementConstants.IND_MIN;
    private static final int IND_AVG = MeasurementConstants.IND_AVG;
    private static final int IND_MAX = MeasurementConstants.IND_MAX;
    private static final int IND_CFG_COUNT = MeasurementConstants.IND_CFG_COUNT;
    private static final int IND_LAST_TIME = MeasurementConstants.IND_LAST_TIME;
    private static final int IND_UP_TIME = IND_LAST_TIME + 1;
    private static final int IND_TOTAL_TIME = IND_UP_TIME + 1;
    private static final long MAX_AVAIL_TIMESTAMP = AvailabilityDataRLE.getLastTimestamp();
    private static final String ALL_EVENTS_INTERESTING_PROP = "org.hq.triggers.all.events.interesting";
    private static final int DEFAULT_INTERVAL = 60;

    private static final String AVAIL_MANAGER_METRICS_INSERTED = ConcurrentStatsCollector.AVAIL_MANAGER_METRICS_INSERTED;

    private static final long MAX_DATA_BACKLOG_TIME = 7 * MeasurementConstants.DAY;

    private MeasurementManager measurementManager;

    private ResourceGroupManager groupManager;
    
    private ResourceManager resourceManager;

    private MessagePublisher messenger;

    private AvailabilityDataRepository availabilityDataRepository;

    private MeasurementRepository measurementRepository;
    private MessagePublisher messagePublisher;
    private RegisteredTriggers registeredTriggers;
    private AvailabilityCache availabilityCache;
    private ConcurrentStatsCollector concurrentStatsCollector;
    private PlatformManager platformManager;
    
    @Autowired
    public AvailabilityManagerImpl(ResourceManager resourceManager, ResourceGroupManager groupManager, MessagePublisher messenger,
                                   AvailabilityDataRepository availabilityDataRepository, MeasurementRepository measurementRepository,
                                   MessagePublisher messagePublisher, RegisteredTriggers registeredTriggers, AvailabilityCache availabilityCache,
                                   ConcurrentStatsCollector concurrentStatsCollector,PlatformManager platformManager) {
        this.resourceManager = resourceManager;
        this.groupManager = groupManager;
        this.messenger = messenger;
        this.availabilityDataRepository = availabilityDataRepository;
        this.measurementRepository = measurementRepository;
        this.messagePublisher = messagePublisher;
        this.registeredTriggers = registeredTriggers;
        this.availabilityCache = availabilityCache;
        this.concurrentStatsCollector = concurrentStatsCollector;
        this.platformManager = platformManager;
    }

    @PostConstruct
    public void initStatsCollector() {
    	concurrentStatsCollector.register(ConcurrentStatsCollector.AVAIL_MANAGER_METRICS_INSERTED);
    }
    
    // To break AvailabilityManager - MeasurementManager circular dependency
    // TODO: Check why we need this when we have
    // MeasurementManagerImpl.setCircularDependencies()?
    @Autowired
    public void setMeasurementManager(MeasurementManager measurementManager) {
        this.measurementManager = measurementManager;
    }

    /**
     * 
     */
    @Transactional(readOnly = true)
    public Measurement getAvailMeasurement(Resource resource) {
        return measurementRepository.findAvailabilityMeasurementByResource(resource.getId());
    }

    /**
     * 
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Measurement> getPlatformResources() {
        Set<Integer> platformIds = platformManager.getAllPlatformIds();
        if(platformIds.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        return measurementRepository.findAvailabilityMeasurementsByResources(new ArrayList<Integer>(platformIds));
    }

    /**
     * @return Down time in ms for the Resource availability
     * 
     * 
     */
    @Transactional(readOnly = true)
    public long getDowntime(Resource resource, long begin, long end) throws MeasurementNotFoundException {
        Measurement meas = measurementRepository.findAvailabilityMeasurementByResource(resource.getId());
        if (meas == null) {
            throw new MeasurementNotFoundException("Availability measurement " + "not found for resource " +
                                                   resource.getId());
        }
        List<AvailabilityDataRLE> availInfo = availabilityDataRepository.getHistoricalAvails(meas, begin, end, false);
        long rtn = 0l;
        for (AvailabilityDataRLE avail : availInfo) {
            if (avail.getAvailVal() != AVAIL_DOWN) {
                continue;
            }
            long endtime = avail.getEndtime();
            if (endtime == MAX_AVAIL_TIMESTAMP) {
                endtime = System.currentTimeMillis();
            }
            long rangeStartTime = avail.getStartime();
            // Make sure the start of the down time is not earlier then the begin time
            if (rangeStartTime < begin){
            	rangeStartTime = begin;
            }
            rtn += (endtime - rangeStartTime);
        }
        return rtn;
    }

    /**
     * 
     */
    @Transactional(readOnly = true)
    public List<Measurement> getAvailMeasurementChildren(Resource resource) {
        final List<Integer> sList = Collections.singletonList(resource.getId());
        List<Measurement> rtn = getAvailMeasurementChildren(sList).get(resource.getId());
        if (rtn == null) {
            rtn = new ArrayList<Measurement>(0);
        }
        return rtn;
    }

    /**
     * @param {@link List} of {@link Integer} resource ids
     * @return {@link Map} of {@link Integer} to {@link List} of
     *         {@link Measurement}
     * 
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<Measurement>> getAvailMeasurementChildren(List<Integer> resourceIds) {
        if (resourceIds.isEmpty()) {
            return new HashMap<Integer,List<Measurement>>(0);
        }
        //TODO this query used to batch by children IDs of all resources, but can't do that now since
        //resource hierarchy info is not in relational DB. If this becomes a perf issue, investigate other ways to optimize
        Map<Integer,List<Integer>> childrenIds = new HashMap<Integer,List<Integer>>();
        for(Integer resourceId : resourceIds) {
            List<Integer> sortedChildrenIds = new ArrayList<Integer>();
            sortedChildrenIds.addAll(resourceManager.findResourceById(resourceId).getChildrenIds(true));
            Collections.sort(sortedChildrenIds);
            childrenIds.put(resourceId, sortedChildrenIds);
        }
        return measurementRepository.findRelatedAvailabilityMeasurements(childrenIds);
    }

    /**
     * Get Availability measurements (disabled) in scheduled downtime. 
     */
    @Transactional(readOnly = true) 
    public Map<Integer, Measurement> getAvailMeasurementsInDowntime(Collection<AppdefEntityID> eids) {
        Map<Integer, Measurement> measMap = new HashMap<Integer, Measurement>();
        
        try {
            // TODO: Resolve circular dependency and autowire MaintenanceEventManager
            List<MaintenanceEvent> events = PermissionManagerFactory.getInstance()
                .getMaintenanceEventManager().getRunningMaintenanceEvents();

            for (MaintenanceEvent event : events) {
                ResourceGroup group = groupManager.findResourceGroupById(event.getGroupId());
                Collection<Resource> resources = groupManager.getMembers(group);

                for (Resource resource : resources) {
                    List<Measurement> measurements = getAvailMeasurementChildren(
                        resource);

                    measurements.add(getAvailMeasurement(resource));

                    if (!measurements.isEmpty()) {
                        for (Measurement m : measurements) {
                        	if (m == null) {
                        		// measurement could be null if resource has not been configured
                        		continue;
                        	}
                            Integer r = m.getResource();
                            if (r == null) {
                                continue;
                            }
                            // availability measurement in scheduled downtime are disabled
                            if (!m.isEnabled() && eids.contains(AppdefUtil.newAppdefEntityId(r))) {
                                measMap.put(r, m);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Could not find availability measurements in downtime: " + e.getMessage(), e);
        }

        return measMap;
    }
    
    /**
     * TODO: Can this method be combined with the one that takes an array?
     * 
     * 
     */
    @Transactional(readOnly = true)
    public PageList<HighLowMetricValue> getHistoricalAvailData(Measurement m, long begin, long end, PageControl pc,
                                                               boolean prependUnknowns) {
        List<AvailabilityDataRLE> availInfo = availabilityDataRepository.getHistoricalAvails(m, begin, end, pc.isDescending());
        return getPageList(availInfo, begin, end, m.getInterval(), prependUnknowns);
    }

    /**
     * Fetches historical availability encapsulating the specified time range
     * for each measurement id in mids;
     * @param mids measurement ids
     * @param begin time range start
     * @param end time range end
     * @param interval interval of each time range window
     * @param pc page control
     * @param prependUnknowns determines whether to prepend AVAIL_UNKNOWN if the
     *        corresponding time window is not accounted for in the database.
     *        Since availability is contiguous this will not occur unless the
     *        time range precedes the first availability point.
     * @see org.hyperic.hq.measurement.MeasurementConstants#AVAIL_UNKNOWN
     * 
     */
    @Transactional(readOnly = true)
    public PageList<HighLowMetricValue> getHistoricalAvailData(Integer[] mids, long begin, long end, long interval,
                                                               PageControl pc, boolean prependUnknowns) {
        if (mids.length == 0) {
            return new PageList<HighLowMetricValue>();
        }
        List<AvailabilityDataRLE> availInfo = availabilityDataRepository.getHistoricalAvails(Arrays.asList(mids), begin, end, pc
            .isDescending());
        return getPageList(availInfo, begin, end, interval, prependUnknowns);
    }

    /**
     * Get the list of Raw RLE objects for a resource
     * @return List<AvailabilityDataRLE>
     * 
     */
    @Transactional(readOnly = true)
    public List<AvailabilityDataRLE> getHistoricalAvailData(Integer res, long begin, long end) {
        return availabilityDataRepository.getHistoricalAvails(res, begin, end);
    }

    private Collection<HighLowMetricValue> getDefaultHistoricalAvail(long timestamp) {
        HighLowMetricValue[] rtn = new HighLowMetricValue[DEFAULT_INTERVAL];
        Arrays.fill(rtn, new HighLowMetricValue(AVAIL_UNKNOWN, timestamp));
        return Arrays.asList(rtn);
    }

    private PageList<HighLowMetricValue> getPageList(List<AvailabilityDataRLE> availInfo, long begin, long end,
                                                     long interval, boolean prependUnknowns) {
        PageList<HighLowMetricValue> rtn = new PageList<HighLowMetricValue>();
        begin += interval;
        for (Iterator<AvailabilityDataRLE> it = availInfo.iterator(); it.hasNext();) {
            AvailabilityDataRLE rle = it.next();
            long availStartime = rle.getStartime();
            long availEndtime = rle.getEndtime();
            if (availEndtime < begin) {
                continue;
            }
            LinkedList<AvailabilityDataRLE> queue = new LinkedList<AvailabilityDataRLE>();
            queue.add(rle);
            int i = 0;
            for (long curr = begin; curr <= end; curr += interval) {
                long next = curr + interval;
                next = (next > end) ? end : next;
                long endtime = ((AvailabilityDataRLE) queue.getFirst()).getEndtime();
                while (next > endtime) {
                    // it should not be the case that there are no more
                    // avails in the array, but we need to handle it
                    if (it.hasNext()) {
                        AvailabilityDataRLE tmp = (AvailabilityDataRLE) it.next();
                        queue.addFirst(tmp);
                        endtime = tmp.getEndtime();
                    } else {
                        endtime = availEndtime;
                        int measId = rle.getMeasurement().getId().intValue();
                        String msg = "Measurement, " + measId + ", for interval " + begin + " - " + end +
                                     " did not return a value for range " + curr + " - " + (curr + interval);
                        _log.warn(msg);
                    }
                }
                endtime = availEndtime;
                while (curr > endtime) {
                    queue.removeLast();
                    // this should not happen unless the above !it.hasNext()
                    // else condition is true
                    if (queue.size() == 0) {
                        rle = new AvailabilityDataRLE(rle.getMeasurement(), rle.getEndtime(), next, AVAIL_UNKNOWN);
                        queue.addLast(rle);
                    }
                    rle = (AvailabilityDataRLE) queue.getLast();
                    availStartime = rle.getStartime();
                    availEndtime = rle.getEndtime();
                    endtime = availEndtime;
                }
                HighLowMetricValue val;
                if (curr >= availStartime) {
                    val = getMetricValue(queue, curr);
                } else if (prependUnknowns) {
                    val = new HighLowMetricValue(AVAIL_UNKNOWN, curr);
                    val.incrementCount();
                } else {
                    i++;
                    continue;
                }
                if (rtn.size() <= i) {
                    rtn.add(round(val));
                } else {
                    updateMetricValue(val, (HighLowMetricValue) rtn.get(i));
                }
                i++;
            }
        }
        if (rtn.size() == 0) {
            rtn.addAll(getDefaultHistoricalAvail(end));
        }
        return rtn;
    }

    private HighLowMetricValue round(HighLowMetricValue val) {
        final BigDecimal b = new BigDecimal(val.getValue(), new MathContext(10));
        val.setValue(b.doubleValue());
        return val;
    }

    private HighLowMetricValue updateMetricValue(HighLowMetricValue newVal, HighLowMetricValue oldVal) {
        if (newVal.getHighValue() == AVAIL_UNKNOWN || newVal.getHighValue() > oldVal.getHighValue()) {
            oldVal.setHighValue(newVal.getHighValue());
        }
        if (newVal.getLowValue() == AVAIL_UNKNOWN || newVal.getLowValue() < oldVal.getLowValue()) {
            oldVal.setLowValue(newVal.getLowValue());
        }
        int count = oldVal.getCount();
        if (oldVal.getValue() == AVAIL_UNKNOWN) {
            double value = newVal.getValue();
            oldVal.setValue(value);
            oldVal.setCount(1);
        } else if (newVal.getValue() == AVAIL_UNKNOWN) {
            return oldVal;
        } else {
            double value = ((newVal.getValue() + (oldVal.getValue() * count))) / (count + 1);
            oldVal.setValue(value);
            oldVal.incrementCount();
            round(oldVal);
        }
        return oldVal;
    }

    private HighLowMetricValue getMetricValue(List<AvailabilityDataRLE> avails, long timestamp) {
        if (avails.size() == 1) {
            AvailabilityDataRLE rle = avails.get(0);
            return new HighLowMetricValue(rle.getAvailVal(), timestamp);
        }
        double value = 0;
        for (AvailabilityDataRLE rle : avails) {
            double availVal = rle.getAvailVal();
            value += availVal;
        }
        value = value / avails.size();
        HighLowMetricValue val = new HighLowMetricValue(value, timestamp);
        val.incrementCount();
        return val;
    }

    /**
     * @return {@link Map} of {@link Measurement} to {@link double[]}. Array is
     *         comprised of 5 elements: [IND_MIN] [IND_AVG] [IND_MAX]
     *         [IND_CFG_COUNT] [IND_LAST_TIME]
     * 
     */
    @Transactional(readOnly = true)
    public Map<Integer, double[]> getAggregateData(Integer[] mids, long begin, long end) {
        List<Object[]> avails = availabilityDataRepository.findAggregateAvailability(Arrays.asList(mids), begin, end);
        return getAggData(avails, false);
    }

    /**
     * @return {@link Map} of {@link MeasurementTemplate.getId} to {@link
     *         double[]}. Array is comprised of 5 elements: [IND_MIN] [IND_AVG]
     *         [IND_MAX] [IND_CFG_COUNT] [IND_LAST_TIME]
     * 
     */
    @Transactional(readOnly = true)
    public Map<Integer, double[]> getAggregateDataByTemplate(Integer[] mids, long begin, long end) {
        List<Object[]> avails = availabilityDataRepository.findAggregateAvailability(Arrays.asList(mids), begin, end);
        return getAggData(avails, true);
    }

    private Map<Integer, double[]> getAggData(List<Object[]> avails, boolean useTidKey) {
        Map<Integer, double[]> rtn = new HashMap<Integer, double[]>();
        if (avails.size() == 0) {
            // Nothing to do, return an empty Map.
            return rtn;
        }
        for (Object[] objs : avails) {
            double[] data;
            Integer key = null;
            if (useTidKey) {
                if (objs[0] instanceof Measurement) {
                    key = ((Measurement) objs[0]).getTemplate().getId();
                } else {
                    key = (Integer) objs[0];
                }
            } else {
                key = ((Measurement) objs[0]).getId();
            }
            if (null == (data = (double[]) rtn.get(key))) {
                data = new double[IND_TOTAL_TIME + 1];
                data[IND_MIN] = MeasurementConstants.AVAIL_UP;
                data[IND_MAX] = MeasurementConstants.AVAIL_PAUSED;
                rtn.put(key, data);
            }

            data[IND_MIN] = Math.min(data[IND_MIN], ((Double) objs[1]).doubleValue());
            data[IND_MAX] = Math.max(data[IND_MAX], ((Double) objs[3]).doubleValue());

            // Expect data to be sorted by end time, so that the last value
            // returned is the final count and the last value
            data[IND_CFG_COUNT] += (objs[4] == null) ? 0 : ((java.lang.Number)objs[4]).doubleValue();
            data[IND_LAST_TIME] = ((Double) objs[2]).doubleValue();

            data[IND_UP_TIME] += ((Double) objs[5]).doubleValue();
            data[IND_TOTAL_TIME] += ((Double) objs[6]).doubleValue();
        }

        // Now calculate the average value
        for (double[] data : rtn.values()) {
            data[IND_AVG] += data[IND_UP_TIME] / data[IND_TOTAL_TIME];
        }
        return rtn;
    }

    /**
     * @param resources Collection may be of type {@link Resource},
     *        {@link AppdefEntityId}, {@link AppdefEntityValue},
     *        {@link AppdefResourceValue} or {@link Integer}
     * @param measCache Map<Integer, List> optional arg (may be null) to supply
     *       measurement id(s) of ResourceIds. Integer => Resource.getId().  If a
     *       measurement is not specified in the measCache parameter it will be added
     *       to the map
     * @return Map<Integer, MetricValue> Integer => Measurement.getId()
     * 
     */
    @Transactional(readOnly = true)
    public Map<Integer, MetricValue> getLastAvail(Collection<? extends Object> resources,
                                                  Map<Integer, List<Measurement>> measCache) {
        final Set<Integer> midsToGet = new HashSet<Integer>(resources.size());
        final List<Resource> resToGet = new ArrayList<Resource>(resources.size());
        for (Object o : resources) {
            Resource resource = null;
            if (o instanceof AppdefEntityValue) {
                AppdefEntityValue rv = (AppdefEntityValue) o;
                AppdefEntityID aeid = rv.getID();
                resource = resourceManager.findResource(aeid);
            } else if (o instanceof AppdefEntityID) {
                AppdefEntityID aeid = (AppdefEntityID) o;
                resource = resourceManager.findResource(aeid);
            } else if (o instanceof Resource) {
                resource = (Resource) o;
            } else if (o instanceof AppdefResourceValue) {
                AppdefResourceValue res = (AppdefResourceValue) o;
                AppdefEntityID aeid = res.getEntityId();
                resource = resourceManager.findResource(aeid);
            } else {
                resource = resourceManager.findResourceById((Integer) o);
            }
            List<Measurement> measurements = null;
            if (resource == null || resource.isInAsyncDeleteState()) {
                continue;
            }
           
            if (measCache != null) {
                measurements = measCache.get(resource.getId());
            }
            if (measurements == null || measurements.size() == 0) {
                resToGet.add(resource);
                continue;
            }
            for (Measurement m : measurements) {
                // populate the Map if value doesn't exist
                if (measCache != null) {
                    List<Measurement> measids =  measCache.get(m.getResource());
                    if (measids == null) {
                        measids = new ArrayList<Measurement>();
                        measids.add(m);
                        measCache.put(m.getResource(), measids);
                    }
                   
                }
                midsToGet.add(m.getId());
            }
        }
        if (!resToGet.isEmpty()) {
            final Collection<List<Measurement>> measIds = measurementManager.getAvailMeasurements(resToGet).values();
            for (List<Measurement> measurementList : measIds) {
                for (Measurement m : measurementList) {
                    midsToGet.add(m.getId());
                }
            }
        }
        return getLastAvail((Integer[]) midsToGet.toArray(new Integer[0]));
    }

    /**
     * 
     */
    @Transactional(readOnly = true)
    public MetricValue getLastAvail(Measurement m) {
        Map<Integer, MetricValue> map = getLastAvail(new Integer[] { m.getId() });
        MetricValue mv = (MetricValue) map.get(m.getId());
        if (mv == null) {
            return new MetricValue(AVAIL_UNKNOWN, System.currentTimeMillis());
        } else {
            return mv;
        }
    }

    /**
     * Only unique measurement ids should be passed in. Duplicate measurement
     * ids will be filtered out from the returned Map if present.
     * 
     * @return {@link Map} of {@link Integer} to {@link MetricValue} Integer is
     *         the measurementId
     * 
     */
    @Transactional(readOnly = true)
    public Map<Integer, MetricValue> getLastAvail(Integer[] mids) {
        if (mids.length == 0) {
            return Collections.emptyMap();
        }
        // Don't modify callers array
        final List<Integer> midList = Collections.unmodifiableList(Arrays.asList(mids));
        final Map<Integer, MetricValue> rtn = new HashMap<Integer, MetricValue>(midList.size());
        final List<AvailabilityDataRLE> list = availabilityDataRepository.findLastByMeasurements(midList);
        for (AvailabilityDataRLE avail : list) {
            final Integer mid = avail.getMeasurement().getId();
            final AvailabilityMetricValue mVal = new AvailabilityMetricValue(avail.getAvailVal(), avail.getStartime(),
                avail.getApproxEndtime());
            rtn.put(mid, mVal);
        }
        // fill in missing measurements
        final long now = TimingVoodoo.roundDownTime(System.currentTimeMillis(), MeasurementConstants.MINUTE);
        for (Integer mid : midList) {
            if (!rtn.containsKey(mid)) {
                final MetricValue mVal = new MetricValue(AVAIL_UNKNOWN, now);
                rtn.put(mid, mVal);
            }
        }
        return rtn;
    }

    /**
     * @param includes List<Integer> of mids. If includes is null then all
     *        unavail entities will be returned.
     * 
     */
    @Transactional(readOnly = true)
    public List<DownMetricValue> getUnavailEntities(List<Integer> includes) {
        List<DownMetricValue> rtn;
        if (includes != null) {
            rtn = new ArrayList<DownMetricValue>(includes.size());
        } else {
            rtn = new ArrayList<DownMetricValue>();
        }
        List<AvailabilityDataRLE> unavails = availabilityDataRepository.getDownMeasurements(includes);
        for (AvailabilityDataRLE rle : unavails) {
            Measurement meas = rle.getMeasurement();
            long timestamp = rle.getStartime();
            Integer mid = meas.getId();
            MetricValue val = new MetricValue(AVAIL_DOWN, timestamp);
            rtn.add(new DownMetricValue(AppdefUtil.newAppdefEntityId(meas.getResource()), mid, val));
        }
        return rtn;
    }

    /**
     * Add a single Availablility Data point.
     * @mid The Measurement id
     * @mval The MetricValue to store.
     * 
     */
    public void addData(Integer mid, MetricValue mval) {
        List<DataPoint> l = new ArrayList<DataPoint>(1);
        l.add(new DataPoint(mid, mval));
        addData(l);
    }

    /**
     * Process Availability data. The default behavior is to send the data
     * points to the event handlers.
     * 
     * @param availPoints List of DataPoints
     * 
     * 
     */
    public void addData(List<DataPoint> availPoints) {
        addData(availPoints, true);
    }

    /**
     * Process Availability data.
     * 
     * @param availPoints List of DataPoints
     * @param sendData Indicates whether to send the data to event handlers. The
     *        default behavior is true. If false, the calling method should call
     *        sendDataToEventHandlers directly afterwards.
     * 
     * 
     */
    public void addData(List<DataPoint> availPoints, boolean sendData) {
        if (availPoints == null || availPoints.size() == 0) {
            return;
        }
        List<DataPoint> updateList = new ArrayList<DataPoint>(availPoints.size());
        List<DataPoint> outOfOrderAvail = new ArrayList<DataPoint>(availPoints.size());
        Map<DataPoint, AvailabilityDataRLE> createMap = new HashMap<DataPoint, AvailabilityDataRLE>();
        Map<DataPoint, AvailabilityDataRLE> removeMap = new HashMap<DataPoint, AvailabilityDataRLE>();
        Map<Integer, StringBuilder> state = null;
        Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails = Collections.emptyMap();
        synchronized (availabilityCache) {
            try {
                availabilityCache.beginTran();
                updateCache(availPoints, updateList, outOfOrderAvail);
                currAvails = createCurrAvails(outOfOrderAvail, updateList);
                state = captureCurrAvailState(currAvails);
                updateStates(updateList, currAvails, createMap, removeMap);
                updateOutOfOrderState(outOfOrderAvail, currAvails, createMap, removeMap);
                flushCreateAndRemoves(createMap, removeMap);
                logErrorInfo(state, availPoints, currAvails);
                availabilityCache.commitTran();
            } catch (Throwable e) {
                logErrorInfo(state, availPoints, currAvails);
                _log.error(e.getMessage(), e);
                availabilityCache.rollbackTran();
                throw new SystemException(e);
            }
        }
        
        concurrentStatsCollector.addStat(availPoints.size(), AVAIL_MANAGER_METRICS_INSERTED);
        
        if (sendData) {
            sendDataToEventHandlers(availPoints);
        }
    }

    private void flushCreateAndRemoves(Map<DataPoint, AvailabilityDataRLE> createMap,
                                       Map<DataPoint, AvailabilityDataRLE> removeMap) {
        final StopWatch watch = new StopWatch();
        final boolean debug = _log.isDebugEnabled();
              
        if (debug) watch.markTimeBegin("remove");
        for (Map.Entry<DataPoint, AvailabilityDataRLE> entry : removeMap.entrySet()) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE) entry.getValue();
            // if we call remove() on an object which is already in the session
            // hibernate will throw NonUniqueObjectExceptions
            AvailabilityDataRLE tmp = availabilityDataRepository.findOne(rle.getAvailabilityDataId());
            if (tmp != null) {
                availabilityDataRepository.delete(tmp);
            } else {
                availabilityDataRepository.delete(rle);
            }
        }
        if (debug) {
            watch.markTimeEnd("remove");
            watch.markTimeBegin("flush");
        }
        // addData() could be overwriting RLE data points (i.e. from 0.0 to 1.0)
        // with the same ID. If this is the scenario, then we must run
        // flush() in order to ensure that these old objects are not in the
        // session when the equivalent create() on the same ID is run,
        // thus avoiding NonUniqueObjectExceptions
        availabilityDataRepository.flush();
        if (debug) {
            watch.markTimeEnd("flush");
            watch.markTimeBegin("create");
        }
        
        for (Map.Entry<DataPoint, AvailabilityDataRLE> entry : createMap.entrySet()) {
            AvailabilityDataRLE rle = (AvailabilityDataRLE) entry.getValue();
            AvailabilityDataId id = new AvailabilityDataId();
            id.setMeasurement(rle.getMeasurement());
            id.setStartime(rle.getStartime());
            AvailabilityDataRLE availData = new AvailabilityDataRLE(rle.getMeasurement(), rle.getStartime(), 
                rle.getEndtime(), rle.getAvailVal());
            availabilityDataRepository.save(availData);
        }
        if (debug) {
            watch.markTimeEnd("create");
            _log.debug("AvailabilityInserter flushCreateAndRemoves: " + watch
                + ", points {remove=" + removeMap.size()
                + ", create=" + createMap.size()
                + "}");
        }  
    }

    private void logErrorInfo(final Map<Integer, StringBuilder> oldState, final List<DataPoint> availPoints,
                              Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
        if (!_traceLog.isDebugEnabled()) {
            return;
        }
        Integer mid;
        Map<Integer, StringBuilder> currState = captureCurrAvailState(currAvails);
        if (null != (mid = isAvailDataRLEValid(currAvails))) {
            logAvailState(oldState, mid);
            logStates(availPoints, mid);
            logAvailState(currState, mid);
        } else {
            _traceLog.debug("RLE Data is valid");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, TreeSet<AvailabilityDataRLE>> createCurrAvails(final List<DataPoint> outOfOrderAvail,
                                                                        final List<DataPoint> updateList) {
        Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails =  null;
        final StopWatch watch = new StopWatch();
        try {
            if (outOfOrderAvail.size() == 0 && updateList.size() == 0) {
                currAvails = Collections.EMPTY_MAP;
            }
            long now = TimingVoodoo.roundDownTime(System.currentTimeMillis(), 60000);
            HashSet<Integer> mids = getMidsWithinAllowedDataWindow(updateList, now);
            mids.addAll(getMidsWithinAllowedDataWindow(outOfOrderAvail, now));
            if (mids.size() <= 0) {
                currAvails = Collections.EMPTY_MAP;
    
            }
            currAvails = availabilityDataRepository.getHistoricalAvailMap(new ArrayList<Integer>(mids), now - MAX_DATA_BACKLOG_TIME, false);
            return currAvails;
        } finally {
            if (_log.isDebugEnabled()) {
                _log.debug("AvailabilityInserter setCurrAvails: " + watch
                    + ", size=" + currAvails.size());
            }
        }
    }

    private HashSet<Integer> getMidsWithinAllowedDataWindow(final List<DataPoint> states, final long now) {
        HashSet<Integer> mids = new HashSet<Integer>();
        int i = 0;
        for (Iterator<DataPoint> it = states.iterator(); it.hasNext(); i++) {
            DataPoint pt = it.next();
            long timestamp = pt.getTimestamp();
            // only allow data for the last MAX_DATA_BACKLOG_TIME ms
            // this way we don't have to bring too much into memory which could
            // severely impact performance
            if ((now - timestamp) > MAX_DATA_BACKLOG_TIME) {
                it.remove();
                long days = (now - timestamp) / MeasurementConstants.DAY;
                _log.warn(" Avail measurement came in " + days + " days " + " late, dropping: timestamp=" + timestamp +
                          " measId=" + pt.getMetricId() + " value=" + pt.getMetricValue());
                continue;
            }
            Integer mId = pt.getMetricId();
            if (!mids.contains(mId)) {
                mids.add(mId);
            }
        }
        return mids;
    }

    private void updateDup(DataPoint state, AvailabilityDataRLE dup,
                           Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                           Map<DataPoint, AvailabilityDataRLE> createMap, Map<DataPoint, AvailabilityDataRLE> removeMap)
        throws BadAvailStateException {
        if (dup.getAvailVal() == state.getValue()) {
            // nothing to do
        } else if (dup.getAvailVal() != AVAIL_DOWN) {
            String msg = "New DataPoint and current DB value for " + "MeasurementId " + state.getMetricId() +
                         " / timestamp " + state.getTimestamp() + " have conflicting states.  " +
                         "Since a non-zero rle value cannot be overridden, no update." + "\ncurrent rle value -> " +
                         dup +
                         // ask Juilet Sierra why (js) is here
                         ":(js):\npoint trying to override current rle -> " + state;
            throw new BadAvailStateException(msg);
        } else {
            Measurement meas = dup.getMeasurement();
            long newStartime = dup.getStartime() + meas.getInterval();
            insertPointOnBoundry(dup, newStartime, state, currAvails, createMap, removeMap);
        }
    }

    /**
     * sets avail's startime to newStartime and prepends a new avail obj from
     * avail.getStartime() to newStartime with a value of state.getValue() Used
     * specifically for a point which collides with a RLE on its startime
     */
    private void insertPointOnBoundry(AvailabilityDataRLE avail, long newStartime, DataPoint pt,
                                      Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                      Map<DataPoint, AvailabilityDataRLE> createMap,
                                      Map<DataPoint, AvailabilityDataRLE> removeMap) throws BadAvailStateException {
        if (newStartime <= avail.getStartime()) {
            return;
        }
        Measurement meas = avail.getMeasurement();
        if (avail.getEndtime() == MAX_AVAIL_TIMESTAMP) {
          
            DataPoint tmp = availabilityCache.get(pt.getMetricId());
            if (tmp == null || pt.getTimestamp() >= tmp.getTimestamp()) {
                updateAvailVal(avail, pt.getValue(), currAvails, createMap, removeMap);
            } else {
                prependState(pt, avail, currAvails, createMap, removeMap);
            }
        } else if (newStartime < avail.getEndtime()) {
            prependState(pt, avail, currAvails, createMap, removeMap);
        } else if (newStartime > avail.getEndtime()) {
            removeAvail(avail, currAvails, createMap, removeMap);
        } else if (newStartime == avail.getEndtime()) {
            AvailabilityDataRLE after = findAvailAfter(pt, currAvails);
            if (after == null) {
                throw new BadAvailStateException("Availability measurement_id=" + pt.getMetricId() +
                                                 " does not have a availability point after timestamp " +
                                                 pt.getTimestamp());
            }
            if (after.getAvailVal() == pt.getValue()) {
                // resolve by removing the before obj, if it exists,
                // and sliding back the start time of after obj
                AvailabilityDataRLE before = findAvailBefore(pt, currAvails);
                if (before == null) {
                    after = updateStartime(after, avail.getStartime(), currAvails, createMap, removeMap);
                } else if (before.getAvailVal() == after.getAvailVal()) {
                    removeAvail(avail, currAvails, createMap, removeMap);
                    removeAvail(before, currAvails, createMap, removeMap);
                    after = updateStartime(after, before.getStartime(), currAvails, createMap, removeMap);
                }
            } else {
                // newStartime == avail.getEndtime() &&
                // newStartime == after.getStartime() &&
                // newStartime < after.getEndtime() &&
                // pt.getValue() != after.getAvailVal()
                // therefore, need to push back startTime and set the value
                long interval = meas.getInterval();
                if ((after.getStartime() + interval) < after.getEndtime()) {
                    prependState(pt, after, currAvails, createMap, removeMap);
                } else {
                    DataPoint afterPt = new DataPoint(meas.getId().intValue(), after.getAvailVal(), after.getStartime());
                    AvailabilityDataRLE afterAfter = findAvailAfter(afterPt, currAvails);
                    if (afterAfter.getAvailVal() == pt.getValue()) {
                        removeAvail(after, currAvails, createMap, removeMap);
                        afterAfter = updateStartime(afterAfter, pt.getTimestamp(), currAvails, createMap, removeMap);
                    } else {
                        updateAvailVal(after, pt.getValue(), currAvails, createMap, removeMap);
                    }
                }
            }
        }
    }

    private AvailabilityDataRLE findAvail(DataPoint state, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
        Integer mId = state.getMetricId();
        Collection<AvailabilityDataRLE> rles = currAvails.get(mId);
        long start = state.getTimestamp();
        for (AvailabilityDataRLE rle : rles) {
            if (rle.getStartime() == start) {
                return rle;
            }
        }
        return null;
    }

    private AvailabilityDataRLE findAvailAfter(DataPoint state, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
        final Integer mId = state.getMetricId();
        final TreeSet<AvailabilityDataRLE> rles = currAvails.get(mId);
        final long start = state.getTimestamp();
        final AvailabilityDataRLE tmp = new AvailabilityDataRLE();
        // tailSet is inclusive so we need to add 1 to start
        tmp.setStartime(start + 1);
        final SortedSet<AvailabilityDataRLE> set = rles.tailSet(tmp);
        if (set.size() == 0) {
            return null;
        }
        return (AvailabilityDataRLE) set.first();
    }

    private AvailabilityDataRLE findAvailBefore(DataPoint state, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
        Integer mId = state.getMetricId();
        TreeSet<AvailabilityDataRLE> rles = currAvails.get(mId);
        long start = state.getTimestamp();
        AvailabilityDataRLE tmp = new AvailabilityDataRLE();
        // headSet is inclusive so we need to subtract 1 from start
        tmp.setStartime(start - 1);
        SortedSet<AvailabilityDataRLE> set = rles.headSet(tmp);
        if (set.size() == 0) {
            return null;
        }
        return set.last();
    }

    private void merge(DataPoint state, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                       Map<DataPoint, AvailabilityDataRLE> createMap, Map<DataPoint, AvailabilityDataRLE> removeMap)
        throws BadAvailStateException {
        AvailabilityDataRLE dup = findAvail(state, currAvails);
        if (dup != null) {
            updateDup(state, dup, currAvails, createMap, removeMap);
            return;
        }
        AvailabilityDataRLE before = findAvailBefore(state, currAvails);
        AvailabilityDataRLE after = findAvailAfter(state, currAvails);
        if (before == null && after == null) {
            // this shouldn't happen here
            Measurement meas = getMeasurement(state.getMetricId());
            create(meas, state.getTimestamp(), state.getValue(), currAvails, createMap);
        } else if (before == null) {
            if (after.getAvailVal() != state.getValue()) {
                prependState(state, after, currAvails, createMap, removeMap);
            } else {
                after = updateStartime(after, state.getTimestamp(), currAvails, createMap, removeMap);
            }
        } else if (after == null) {
            // this shouldn't happen here
            updateState(state, currAvails, createMap, removeMap);
        } else {
            insertAvail(before, after, state, currAvails, createMap, removeMap);
        }
    }

    private void insertAvail(AvailabilityDataRLE before, AvailabilityDataRLE after, DataPoint state,
                             Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                             Map<DataPoint, AvailabilityDataRLE> createMap,
                             Map<DataPoint, AvailabilityDataRLE> removeMap) {
        if (state.getValue() != after.getAvailVal() && state.getValue() != before.getAvailVal()) {
            Measurement meas = getMeasurement(state.getMetricId());
            long pivotTime = state.getTimestamp() + meas.getInterval();
            create(meas, state.getTimestamp(), pivotTime, state.getValue(), currAvails, createMap);
            updateEndtime(before, state.getTimestamp());
            after = updateStartime(after, pivotTime, currAvails, createMap, removeMap);
        } else if (state.getValue() == after.getAvailVal() && state.getValue() != before.getAvailVal()) {
            updateEndtime(before, state.getTimestamp());
            after = updateStartime(after, state.getTimestamp(), currAvails, createMap, removeMap);
        } else if (state.getValue() != after.getAvailVal() && state.getValue() == before.getAvailVal()) {
            // this is fine
        } else if (state.getValue() == after.getAvailVal() && state.getValue() == before.getAvailVal()) {
            // this should never happen or else there is something wrong
            // in the code
            String msg = "AvailabilityData [" + before + "] and [" + after +
                         "] have the same values.  This should not be the case.  " + "Cleaning up";
            _log.warn(msg);
            updateEndtime(before, after.getEndtime());
            removeAvail(after, currAvails, createMap, removeMap);
        }
    }

    private boolean prependState(DataPoint state, AvailabilityDataRLE avail,
                                 Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                 Map<DataPoint, AvailabilityDataRLE> createMap,
                                 Map<DataPoint, AvailabilityDataRLE> removeMap) {
        AvailabilityDataRLE before = findAvailBefore(state, currAvails);
        Measurement meas = avail.getMeasurement();
        if (before != null && before.getAvailVal() == state.getValue()) {
            long newStart = state.getTimestamp() + meas.getInterval();
            updateEndtime(before, newStart);
            avail = updateStartime(avail, newStart, currAvails, createMap, removeMap);
        } else {
            long newStart = state.getTimestamp() + meas.getInterval();
            long endtime = newStart;
            avail = updateStartime(avail, newStart, currAvails, createMap, removeMap);
            create(avail.getMeasurement(), state.getTimestamp(), endtime, state.getValue(), currAvails, createMap);
        }
        return true;
    }

    private void updateAvailVal(AvailabilityDataRLE avail, double val,
                                Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                Map<DataPoint, AvailabilityDataRLE> createMap,
                                Map<DataPoint, AvailabilityDataRLE> removeMap) {
        Measurement meas = avail.getMeasurement();
        DataPoint state = new DataPoint(meas.getId().intValue(), val, avail.getStartime());
        AvailabilityDataRLE before = findAvailBefore(state, currAvails);
        if (before == null || before.getAvailVal() != val) {
            avail.setAvailVal(val);
        } else {
            removeAvail(before, currAvails, createMap, removeMap);
            avail = updateStartime(avail, before.getStartime(), currAvails, createMap, removeMap);
            avail.setAvailVal(val);
        }
    }

    private void updateEndtime(AvailabilityDataRLE avail, long endtime) {
        avail.setEndtime(endtime);
    }

    private AvailabilityDataRLE updateStartime(AvailabilityDataRLE avail, long start,
                                               Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                               Map<DataPoint, AvailabilityDataRLE> createMap,
                                               Map<DataPoint, AvailabilityDataRLE> removeMap) {
        // this should not be the case here, but want to make sure and
        // avoid HibernateUniqueKeyExceptions :(
        AvailabilityDataRLE tmp;
        Measurement meas = avail.getMeasurement();
        Integer mId = meas.getId();
        DataPoint tmpState = new DataPoint(mId.intValue(), avail.getAvailVal(), start);
        if (null != (tmp = findAvail(tmpState, currAvails))) {
            removeAvail(tmp, currAvails, createMap, removeMap);
        }
        removeAvail(avail, currAvails, createMap, removeMap);
        return create(meas, start, avail.getEndtime(), avail.getAvailVal(), currAvails, createMap);
    }

    private void removeAvail(AvailabilityDataRLE avail, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                             Map<DataPoint, AvailabilityDataRLE> createMap,
                             Map<DataPoint, AvailabilityDataRLE> removeMap) {
        long start = avail.getStartime();
        Integer mId = avail.getMeasurement().getId();
        TreeSet<AvailabilityDataRLE> rles = currAvails.get(mId);
        if (rles.remove(avail)) {
            DataPoint key = new DataPoint(mId.intValue(), avail.getAvailVal(), start);
            createMap.remove(key);
            removeMap.put(key, avail);
        }
    }

    private AvailabilityDataRLE getLastAvail(DataPoint state, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
        Integer mId = state.getMetricId();
        TreeSet<AvailabilityDataRLE> rles = currAvails.get(mId);
        if (rles.size() == 0) {
            return null;
        }
        return rles.last();
    }

    private AvailabilityDataRLE create(Measurement meas, long start, long end, double val,
                                       Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                       Map<DataPoint, AvailabilityDataRLE> createMap) {
        AvailabilityDataRLE rtn = _createAvail(meas, start, end, val);
        createMap.put(new DataPoint(meas.getId().intValue(), val, start), rtn);
        Integer mId = meas.getId();
        Collection<AvailabilityDataRLE> rles = currAvails.get(mId);
        rles.add(rtn);
        return rtn;
    }

    private AvailabilityDataRLE _createAvail(Measurement meas, long start, long end, double val) {
        AvailabilityDataRLE rtn = new AvailabilityDataRLE();
        rtn.setMeasurement(meas);
        rtn.setStartime(start);
        rtn.setEndtime(end);
        rtn.setAvailVal(val);
        return rtn;
    }

    private AvailabilityDataRLE create(Measurement meas, long start, double val,
                                       Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                       Map<DataPoint, AvailabilityDataRLE> createMap) {
        AvailabilityDataRLE rtn = _createAvail(meas, start, MAX_AVAIL_TIMESTAMP, val);
        createMap.put(new DataPoint(meas.getId().intValue(), val, start), rtn);
        Integer mId = meas.getId();
        Collection<AvailabilityDataRLE> rles = currAvails.get(mId);
        // I am assuming that this will be cleaned up by the caller where it
        // will update the rle before rtn if one exists
        rles.add(rtn);
        return rtn;
    }

    private boolean updateState(DataPoint state, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                Map<DataPoint, AvailabilityDataRLE> createMap,
                                Map<DataPoint, AvailabilityDataRLE> removeMap) throws BadAvailStateException {
        AvailabilityDataRLE avail = getLastAvail(state, currAvails);
        final boolean debug = _log.isDebugEnabled();
        long begin = -1;
        if (avail == null) {
            Measurement meas = getMeasurement(state.getMetricId());
            create(meas, state.getTimestamp(), state.getValue(), currAvails, createMap);
            return true;
        } else if (state.getTimestamp() < avail.getStartime()) {
            if (debug) {
                begin = System.currentTimeMillis();
            }
            merge(state, currAvails, createMap, removeMap);
            if (debug) {
                long now = System.currentTimeMillis();
                _log.debug("updateState.merge() -> " + (now - begin) + " ms");
            }
            return false;
        } else if (state.getTimestamp() == avail.getStartime() && state.getValue() != avail.getAvailVal()) {
            if (debug) {
                begin = System.currentTimeMillis();
            }
            updateDup(state, avail, currAvails, createMap, removeMap);
            if (debug) {
                long now = System.currentTimeMillis();
                _log.debug("updateState.updateDup() -> " + (now - begin) + " ms");
            }
            return true;
        } else if (state.getValue() == avail.getAvailVal()) {
            if (debug) {
                _log.debug("no update state == avail " + state + " == " + avail);
            }
            return true;
        }
        if (debug) {
            _log.debug("updating endtime on avail -> " + avail + ", updating to state -> " + state);
        }
        updateEndtime(avail, state.getTimestamp());
        create(avail.getMeasurement(), state.getTimestamp(), state.getValue(), currAvails, createMap);
        return true;
    }

    private void updateStates(List<DataPoint> states, Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                              Map<DataPoint, AvailabilityDataRLE> createMap,
                              Map<DataPoint, AvailabilityDataRLE> removeMap) {
       
        if (states.size() == 0) {
            return;
        }
        // as a performance optimization, fetch all the last avails
        // at once, rather than one at a time in updateState()
        final StopWatch watch = new StopWatch();
        final boolean debug = _log.isDebugEnabled();
        int numUpdates = 0;
        for (DataPoint state : states) {
            try {
                // need to check again since there could be multiple
                // states with the same id in the list
                DataPoint currState = availabilityCache.get(state.getMetricId());
                if (currState != null && currState.getValue() == state.getValue()) {
                    continue;
                }
                boolean updateCache = updateState(state, currAvails, createMap, removeMap);
                if (debug) {
                    _log.debug("state " + state + " was updated, cache updated: " + updateCache);
                }
                if (updateCache) {
                    availabilityCache.put(state.getMetricId(), state);
                    numUpdates++;
                }
            } catch (BadAvailStateException e) {
                _log.warn(e.getMessage());
            }
        }
        if (debug) {
            _log.debug("AvailabilityInserter updateStates: " + watch
                + ", points {total=" + states.size()
                + ", updateCache=" + numUpdates
                + "}");
        }
    }

    private void updateOutOfOrderState(List<DataPoint> outOfOrderAvail,
                                       Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails,
                                       Map<DataPoint, AvailabilityDataRLE> createMap,
                                       Map<DataPoint, AvailabilityDataRLE> removeMap) {
        if (outOfOrderAvail.size() == 0) {
            return;
        }
        final StopWatch watch = new StopWatch();
        int numBadAvailState = 0;
        for (DataPoint state : outOfOrderAvail) {
            try {
                // do not update the cache here, the timestamp is out of order
                merge(state, currAvails, createMap, removeMap);
            } catch (BadAvailStateException e) {
                numBadAvailState++;
                _log.warn(e.getMessage());
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug("AvailabilityInserter updateOutOfOrderState: " + watch
                + ", points {total=" + outOfOrderAvail.size()
                + ", badAvailState=" + numBadAvailState
                + "}");
        }
    }

    private void updateCache(List<DataPoint> availPoints, List<DataPoint> updateList, List<DataPoint> outOfOrderAvail) {
        if (availPoints.size() == 0) {
            return;
        }
      
        final StopWatch watch = new StopWatch();
        final boolean debug = _log.isDebugEnabled();
        for (DataPoint pt : availPoints) {
            int id = pt.getMetricId().intValue();
            MetricValue mval = pt.getMetricValue();
            double val = mval.getValue();
            long timestamp = mval.getTimestamp();
            DataPoint newState = new DataPoint(id, val, timestamp);
            DataPoint oldState = availabilityCache.get(new Integer(id));
            // we do not want to update the state if it changes
            // instead change it when the db is changed in order
            // to ensure the state of memory to db
            // ONLY update memory state here if there is no change
            if (oldState != null && timestamp < oldState.getTimestamp()) {
                outOfOrderAvail.add(newState);
            } else if (oldState == null || oldState.getValue() == AVAIL_NULL || oldState.getValue() != val) {
                updateList.add(newState);
                if (debug) {
                    String msg = "value of state[" + newState + "] differs from" + " current value[" +
                                 ((oldState != null) ? oldState.toString() : "old state does not exist") + "]";
                    _log.debug(msg);
                }
            } else {
                availabilityCache.put(new Integer(id), newState);
            }
        }
        if (debug) {
            _log.debug("AvailabilityInserter updateCache: " + watch
                + ", points {total=" + availPoints.size()
                + ", outOfOrder=" + outOfOrderAvail.size()
                + ", updateToDb=" + updateList.size()
                + ", updateCacheTimestamp="
                + (availPoints.size() - outOfOrderAvail.size() - updateList.size())
                + "}");
        }
    }

    private void sendDataToEventHandlers(List<DataPoint> data) {
        final StopWatch watch = new StopWatch();
        final boolean debug = _log.isDebugEnabled();
        int maxCapacity = data.size();
        ArrayList<MeasurementEvent> events = new ArrayList<MeasurementEvent>(maxCapacity);
        Map<Integer, MeasurementEvent> downEvents = new HashMap<Integer, MeasurementEvent>(maxCapacity);
        List<MeasurementZevent> zevents = new ArrayList<MeasurementZevent>(maxCapacity);
        boolean allEventsInteresting = Boolean.getBoolean(ALL_EVENTS_INTERESTING_PROP);
        if (debug) watch.markTimeBegin("isTriggerInterested");
        for (DataPoint dp : data) {
            Integer metricId = dp.getMetricId();
            MetricValue val = dp.getMetricValue();
            MeasurementEvent event = new MeasurementEvent(metricId, val);
            if (registeredTriggers.isTriggerInterested(event) || allEventsInteresting) {
                measurementManager.buildMeasurementEvent(event);
                if (event.getValue().getValue() == AVAIL_DOWN) {
                    Resource r = resourceManager.findResourceById(event.getResource());
                    if (r != null && !r.isInAsyncDeleteState()) {
                        downEvents.put(r.getId(), event);
                    }
                } else {
                    events.add(event);
                }
            }
            zevents.add(new MeasurementZevent(metricId.intValue(), val));
        }
        if (debug) watch.markTimeEnd("isTriggerInterested");
        if (!downEvents.isEmpty()) {
            if (debug) watch.markTimeBegin("suppressMeasurementEvents");
            // Determine whether the measurement events can
            // be suppressed as part of hierarchical alerting
            PermissionManagerFactory.getInstance().getHierarchicalAlertingManager().suppressMeasurementEvents(
                downEvents, true);
            if (debug) watch.markTimeEnd("suppressMeasurementEvents");
            if (!downEvents.isEmpty()) {
                events.addAll(downEvents.values());
            }
        }

        if (!events.isEmpty()) {
            messenger.publishMessage(MessagePublisher.EVENTS_TOPIC, events);
        }

        if (!zevents.isEmpty()) {
            ZeventManager.getInstance().enqueueEventsAfterCommit(zevents);
        }
        if (debug) {
            _log.debug("AvailabilityInserter sendDataToEventHandlers: " + watch
                + ", points {total=" + maxCapacity
                + ", downEvents=" + downEvents.size()
                + ", eventsToPublish=" + events.size()
                + ", zeventsToEnqueue=" + zevents.size()
                + "}");
        }
    }

    /**
     * This method should only be called by the AvailabilityCheckService and is
     * used to filter availability data points based on hierarchical alerting
     * rules.
     * 
     * 
     */
    public void sendDataToEventHandlers(Map<Integer, DataPoint> data) {
        int maxCapacity = data.size();
        Map<Integer, MeasurementEvent> events = new HashMap<Integer, MeasurementEvent>(maxCapacity);
        List<MeasurementZevent> zevents = new ArrayList<MeasurementZevent>(maxCapacity);
        boolean allEventsInteresting = Boolean.getBoolean(ALL_EVENTS_INTERESTING_PROP);
        for (Integer resourceIdKey : data.keySet()) {
            DataPoint dp = data.get(resourceIdKey);
            Integer metricId = dp.getMetricId();
            MetricValue val = dp.getMetricValue();
            MeasurementEvent event = new MeasurementEvent(metricId, val);
            if (registeredTriggers.isTriggerInterested(event) || allEventsInteresting) {
                measurementManager.buildMeasurementEvent(event);
                events.put(resourceIdKey, event);
            }
            zevents.add(new MeasurementZevent(metricId.intValue(), val));
        }
        if (!events.isEmpty()) {
            // Determine whether the measurement events can
            // be suppressed as part of hierarchical alerting
            PermissionManagerFactory.getInstance().getHierarchicalAlertingManager().suppressMeasurementEvents(events,
                false);
            messagePublisher.publishMessage(MessagePublisher.EVENTS_TOPIC, new ArrayList<MeasurementEvent>(events
                .values()));
        }
        if (!zevents.isEmpty()) {
            ZeventManager.getInstance().enqueueEventsAfterCommit(zevents);
        }
    }

    private Measurement getMeasurement(Integer mId) {
        return measurementManager.getMeasurement(mId);
    }

    private Map<Integer, StringBuilder> captureCurrAvailState(Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
        if (!_traceLog.isDebugEnabled()) {
            return null;
        }
        Map<Integer, StringBuilder> rtn = new HashMap<Integer, StringBuilder>();
        for (Map.Entry<Integer, TreeSet<AvailabilityDataRLE>> entry : currAvails.entrySet()) {
            Integer mid = entry.getKey();
            Collection<AvailabilityDataRLE> rles = entry.getValue();
            StringBuilder buf = new StringBuilder("\n");
            for (AvailabilityDataRLE rle : rles) {
                buf.append(mid).append(" | ").append(rle.getStartime()).append(" | ").append(rle.getEndtime()).append(
                    " | ").append(rle.getAvailVal()).append("\n");
            }
            rtn.put(mid, buf);
        }
        return rtn;
    }

    private void logAvailState(Map<Integer, StringBuilder> availState, Integer mid) {
        StringBuilder buf = (StringBuilder) availState.get(mid);
        _traceLog.debug(buf.toString());
    }

    private void logStates(List<DataPoint> states, Integer mid) {
        StringBuilder log = new StringBuilder("\n");
        for (DataPoint pt : states) {
            if (!pt.getMetricId().equals(mid)) {
                continue;
            }
            log.append(pt.getMetricId()).append(" | ").append(pt.getTimestamp()).append(" | ").append(
                pt.getMetricValue()).append("\n");
        }
        _traceLog.debug(log.toString());
    }

    private Integer isAvailDataRLEValid(Map<Integer, TreeSet<AvailabilityDataRLE>> currAvails) {
    
        synchronized (availabilityCache) {
            for (Map.Entry<Integer, TreeSet<AvailabilityDataRLE>> entry : currAvails.entrySet()) {
                Integer mId = entry.getKey();
                Collection<AvailabilityDataRLE> rles = entry.getValue();
                if (!isAvailDataRLEValid(mId, availabilityCache.get(mId), rles)) {
                    return mId;
                }
            }
        }
        return null;
    }

    private boolean isAvailDataRLEValid(Integer measId, DataPoint lastPt, Collection<AvailabilityDataRLE> avails) {
        AvailabilityDataRLE last = null;
        Set<Long> endtimes = new HashSet<Long>();
        for (AvailabilityDataRLE avail : avails) {
            Long endtime = new Long(avail.getEndtime());
            if (endtimes.contains(endtime)) {
                _log.error("list for MID=" + measId + " contains two or more of the same endtime=" + endtime);
                return false;
            }
            endtimes.add(endtime);
            if (last == null) {
                last = avail;
                continue;
            }
            if (last.getAvailVal() == avail.getAvailVal()) {
                _log.error("consecutive availpoints have the same value, " +
                    "first={" + last + "}, last={" + avail + "}");
                return false;
            } else if (last.getEndtime() != avail.getStartime()) {
                _log.error("there are gaps in the availability table" +
                   "first={" + last + "}, last={" + avail + "}");
                return false;
            } else if (last.getStartime() > avail.getStartime()) {
                _log.error("startime availability is out of order" +
                   "first={" + last + "}, last={" + avail + "}");
                return false;
            } else if (last.getEndtime() > avail.getEndtime()) {
                _log.error("endtime availability is out of order" +
                   "first={" + last + "}, last={" + avail + "}");
                return false;
            }
            last = avail;
        }
       
        if (((DataPoint) availabilityCache.get(measId)).getValue() != lastPt.getValue()) {
            _log.error("last avail data point does not match cache");
            return false;
        }
        return true;
    }
}
