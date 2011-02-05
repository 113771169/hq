/**
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 *  "derived work".
 *
 *  Copyright (C) [2010], VMware, Inc.
 *  This file is part of HQ.
 *
 *  HQ is free software; you can redistribute it and/or modify
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
 *
 */

package org.hyperic.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.management.ManagementService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.EntityManagerFactoryImpl;
import org.hibernate.jmx.StatisticsService;
import org.hyperic.hq.common.DiagnosticObject;
import org.hyperic.hq.common.DiagnosticsLogger;
import org.hyperic.util.PrintfFormat;
import org.hyperic.util.StringUtil;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate3.LocalSessionFactoryBean;
import org.springframework.orm.hibernate3.SessionFactoryUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preloads the 2nd level
 * cache for performance optimizations and registers Hibernate and EhCache stat
 * MBeans
 * @author jhickey
 * 
 */
public class CacheInitializingLocalSessionFactoryBean implements FactoryBean<SessionFactory>,
    DisposableBean {

    private List<String> classesForCache;

    private final Log log = LogFactory.getLog(CacheInitializingLocalSessionFactoryBean.class);

    @Autowired
    private LocalContainerEntityManagerFactoryBean entityManagerFactoryBean;

    @Autowired
    private MBeanServer mBeanServer;

    @Autowired
    private DiagnosticsLogger diagnosticsLogger;

    private static final String HIBERNATE_STATS_OBJECT_NAME = "Hibernate:type=statistics,application=hq";

    public CacheInitializingLocalSessionFactoryBean(List<String> classesForCache) {
        super();
        this.classesForCache = classesForCache;
    }

    public void destroy() {
        try {
            mBeanServer.unregisterMBean(new ObjectName(HIBERNATE_STATS_OBJECT_NAME));
        } catch (Exception e) {
            log.warn("Error unregistering Hibernate Stats MBean", e);
        }
    }

    public SessionFactory getObject() throws Exception {
        SessionFactory sessionFactory = ((EntityManagerFactoryImpl)entityManagerFactoryBean.getNativeEntityManagerFactory()).getSessionFactory();
        registerMBeans(sessionFactory);
        initEhCacheDiagnostics();
        preloadCache(sessionFactory);
        return sessionFactory;
    }

    public Class<?> getObjectType() {
        return SessionFactory.class;
    }

    public boolean isSingleton() {
        return true;
    }

    private void registerMBeans(SessionFactory sessionFactory) throws MalformedObjectNameException,
        InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        ObjectName on = new ObjectName(HIBERNATE_STATS_OBJECT_NAME);
        StatisticsService mBean = new StatisticsService();
        mBean.setSessionFactory(sessionFactory);
        mBeanServer.registerMBean(mBean, on);
        ManagementService.registerMBeans(CacheManager.getInstance(), mBeanServer, false, false,
            false, true);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    private void preloadCache(SessionFactory sessionFactory) {
        Session session = SessionFactoryUtils.getSession(sessionFactory, true);
        for (String className : classesForCache) {
            Class<?> clazz;
            className = className.trim();
            if (className.length() == 0 || className.startsWith("#")) {
                continue;
            }
            try {
                clazz = Class.forName(className);
            } catch (Exception e) {
                log.warn("Unable to find preload cache for class [" + className + "]", e);
                continue;
            }
            long start = System.currentTimeMillis();
            Collection<Object> vals = session.createCriteria(clazz).list();
            long end = System.currentTimeMillis();
            log.info("Preloaded " + vals.size() + " [" + clazz.getName() + "] in " + (end - start) +
                     " millis");
            // Evict, to avoid dirty checking everything in the inventory
            for (Object val : vals) {
                session.evict(val);
            }
        }
    }

    private void initEhCacheDiagnostics() {
        // Add ehcache statistics to the diagnostics
        DiagnosticObject cacheDiagnostics = new DiagnosticObject() {
            private PrintfFormat _fmt = new PrintfFormat("%-55s %-6d %-6d %6d");
            private PrintfFormat _hdr = new PrintfFormat("%-55s %-6s %-6s %6s");

            public String getName() {
                return "EhCache Diagnostics";
            }

            public String getShortName() {
                return "ehcacheDiag";
            }

            private List<Cache> getSortedCaches() {
                List<Cache> res = getCaches();

                Collections.sort(res, new Comparator<Cache>() {
                    public int compare(Cache c1, Cache c2) {
                        return c1.getName().compareTo(c2.getName());
                    }
                });
                return res;
            }

            public String getStatus() {
                String separator = System.getProperty("line.separator");
                StringBuffer buf = new StringBuffer(separator);
                Object[] fmtArgs = new Object[5];

                fmtArgs[0] = "Cache";
                fmtArgs[1] = "Size";
                fmtArgs[2] = "Hits";
                fmtArgs[3] = "Misses";
                buf.append(_hdr.sprintf(fmtArgs)).append(separator);
                fmtArgs[0] = "=====";
                fmtArgs[1] = "====";
                fmtArgs[2] = "====";
                fmtArgs[3] = "=====";
                buf.append(_hdr.sprintf(fmtArgs));

                for (Cache cache : getSortedCaches()) {
                    fmtArgs[0] = StringUtil.dotProximate(cache.getName(), 55);
                    fmtArgs[1] = new Integer(cache.getSize());
                    fmtArgs[2] = new Long(cache.getStatistics().getCacheHits());
                    fmtArgs[3] = new Long(cache.getStatistics().getCacheMisses());

                    buf.append(separator).append(_fmt.sprintf(fmtArgs));
                }

                return buf.toString();
            }

            public String getShortStatus() {
                return getStatus();
            }

            public String toString() {
                return "ehcache";
            }
        };
        diagnosticsLogger.addDiagnosticObject(cacheDiagnostics);
    }

    private List<Cache> getCaches() {
        CacheManager cacheManager = CacheManager.getInstance();
        String[] caches = cacheManager.getCacheNames();
        List<Cache> res = new ArrayList<Cache>(caches.length);

        for (int i = 0; i < caches.length; i++) {
            res.add(cacheManager.getCache(caches[i]));
        }
        return res;
    }
}
