/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc.
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

package org.hyperic.hq.grouping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hyperic.hq.appdef.server.session.Service;
import org.hyperic.hq.authz.shared.PermissionManagerFactory;
import org.hyperic.hq.inventory.data.ResourceDao;
import org.hyperic.hq.inventory.domain.Resource;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The CritterTranslator is a simple class useful composing a Hibernate query
 * from a list of critters
 */
@Component
public class CritterTranslator {
    private final Log _log = LogFactory.getLog(CritterTranslator.class);
    public static final String EMPTY_SQL = "";
    
    private ResourceDao resourceDao;
    
    @Autowired
    public CritterTranslator(ResourceDao resourceDao) {
        this.resourceDao = resourceDao;
    }

    public PageList translate(CritterTranslationContext ctx, CritterList cList,
                              PageControl pc)
    {
        PageList rtn = new PageList();
        rtn.ensureCapacity(pc.getPagesize());
        //TODO handle other search params besides ALL and set max results
        //Query query = translate(ctx, cList, false, pc.isDescending())
          //              .setFirstResult(pc.getPageEntityIndex());
        //if (PageControl.SIZE_UNLIMITED != pc.getPagesize()) {
          //  query.setMaxResults(pc.getPagesize());
        //}
        
        //rtn.addAll(query.list());
       // rtn.setTotalSize(
         //   ((Number)translate(ctx, cList, true, false).uniqueResult()).intValue());
        rtn.addAll(resourceDao.find(1, pc.getPagesize()));
        Collections.sort(rtn, new Comparator<Resource>() {
            public int compare(Resource o1,Resource o2) {
                return (o1.getName().compareTo(o2.getName()));
            }
        });
        return rtn;
    }

    public SQLQuery translate(CritterTranslationContext ctx, CritterList cList)
    {
        return translate(ctx, cList, false, false);
    }
    
    private SQLQuery translate(CritterTranslationContext ctx,
                               CritterList cList, boolean issueCount,
                               boolean desc) {
        StringBuilder sql = new StringBuilder();
        Map txContexts = new HashMap(cList.getCritters().size());
        if (issueCount) {
            sql.append("select count(1) from ");
        } else {
            sql.append("select {res.*} from ");
        }
        if (cList.isAll()) {
            sql.append("EAM_RESOURCE res \n");
            sql.append(getSQLConstraints(ctx, cList, txContexts));
            sql.append(PermissionManagerFactory.getInstance()
                .getSQLWhere(ctx.getSubject().getId()));
        } else {
            sql.append(getUnionStmts(ctx, cList, txContexts));
            sql.append(PermissionManagerFactory.getInstance()
                .getSQLWhere(ctx.getSubject().getId()));
            sql.append(") res ");
        }
      
        if (!issueCount) {
            sql.append(" ORDER BY res.name ");
            if (desc)
                sql.append("DESC");
        }
        if (_log.isDebugEnabled()) {
            _log.debug("Created SQL: [" + sql + "]");
        }
        SQLQuery res = ctx.getSession().createSQLQuery(sql.toString());
        if (_log.isDebugEnabled()) {
            _log.debug("Translated into: [" + res.getQueryString() + "]");
        }
        if (!issueCount) {
            res.addEntity("res", Resource.class);
        }
        for (Iterator i = cList.getCritters().iterator(); i.hasNext(); ) {
            Critter c = (Critter)i.next();
            c.bindSqlParams((CritterTranslationContext)txContexts.get(c), res);
        }
        return res;
    }

    private String getUnionStmts(CritterTranslationContext ctx,
                                 CritterList cList,
                                 Map txContexts) {
        StringBuilder sql = new StringBuilder();
        List sysCritters = new ArrayList();
        List critters = new ArrayList();
        for (Iterator i = cList.getCritters().iterator(); i.hasNext();) {
            Critter c = (Critter) i.next();
            if (c.getCritterType().isSystem()) {
                sysCritters.add(c);
            } else {
                critters.add(c);
            }
        }
        CritterList critterList = new CritterList(critters, false);
        if (critterList.getCritters().size() > 0) {
            sql.append("(");
            for (Iterator i = critterList.getCritters().iterator(); i.hasNext();) {
                Critter c = (Critter) i.next();
                List l = new ArrayList(sysCritters);
                l.add(c);
                CritterList list = new CritterList(l, true);
                sql.append("select res.* from EAM_RESOURCE res ").append(
                    getSQLConstraints(ctx, list, txContexts));
                if (i.hasNext()) {
                    sql.append(" UNION ");
                }
            }
            
            //TODO commented this out in favor of translate method appending ") res" after the permission manager SQL (was broken in EE).  If we did
            //have a combo of system and non-system critters in future we'll need this back
            //CritterList sysCritList = new CritterList(sysCritters, false);
            //String sysSQL = getSQLConstraints(ctx, sysCritList, txContexts);
            //sql.append(") res ").append(sysSQL);
        } else {
            CritterList sysCritList = new CritterList(sysCritters, false);
            sql.append("EAM_RESOURCE res ");
            sql.append(getSQLConstraints(ctx, sysCritList, txContexts));
        }
        return sql.toString();
    }

    private String getSQLConstraints(CritterTranslationContext ctx,
                                     CritterList cList,
                                     Map txContexts) {
        StringBuilder sql = new StringBuilder();
        
        int prefixCnt = 0;
        for (Iterator i=cList.getCritters().iterator(); i.hasNext(); ) {
            Critter c = (Critter)i.next();
            String prefix = "x" + (prefixCnt++);
            CritterTranslationContext critterCtx = 
                new CritterTranslationContext(ctx.getSubject(),
                                              ctx.getSession(), 
                                              ctx.getHQDialect(),
                                              prefix);
            txContexts.put(c, critterCtx);
            sql.append(critterCtx.escapeSql(c.getSqlJoins(critterCtx, "res")));
            sql.append(" ");
        }

        List systemCritters = new ArrayList();
        List regularCritters = new ArrayList();
        
        for (Iterator i=cList.getCritters().iterator(); i.hasNext(); ) {
            Critter c = (Critter)i.next();
            
            if (c.getCritterType().isSystem())
                systemCritters.add(c);
            else
                regularCritters.add(c);
        }
        
        boolean whereEnabled = false;

        if (!regularCritters.isEmpty()) {
        
            for (Iterator i=regularCritters.iterator(); i.hasNext(); ) {
                Critter c = (Critter)i.next();
            
                CritterTranslationContext critterCtx = 
                    (CritterTranslationContext)txContexts.get(c);
                String critterSql = c.getSql(critterCtx, "res");
                if (critterSql.equals(EMPTY_SQL)) {
                    continue;
                }
                if (!whereEnabled) {
                    sql.append(" where (");
                    whereEnabled = true;
                } else {
                    sql.append(" and (");
                }
                sql.append(critterCtx.escapeSql(critterSql));
                sql.append(") ");
            }
        }
        
        if (!systemCritters.isEmpty()) {
            for (Iterator i=systemCritters.iterator(); i.hasNext(); ) {
                Critter c = (Critter)i.next();
            
                CritterTranslationContext critterCtx = 
                    (CritterTranslationContext)txContexts.get(c);
                String critterSql = c.getSql(critterCtx, "res");
                if (critterSql.equals(EMPTY_SQL)) {
                    continue;
                }
                if (!whereEnabled) {
                    sql.append(" where (");
                    whereEnabled = true;
                } else {
                    sql.append(" and (");
                }
                sql.append(critterCtx.escapeSql(critterSql));
                sql.append(") ");
            }
        }
        return sql.toString();
    }
}
