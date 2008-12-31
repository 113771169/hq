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

package org.hyperic.hq.appdef.server.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.ejb.CreateException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hibernate.Util;
import org.hyperic.hq.appdef.server.session.Cprop;
import org.hyperic.hq.appdef.server.session.CpropKey;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityValue;
import org.hyperic.hq.appdef.shared.AppdefResourceTypeValue;
import org.hyperic.hq.appdef.shared.CPropChangeEvent;
import org.hyperic.hq.appdef.shared.CPropKeyExistsException;
import org.hyperic.hq.appdef.shared.CPropKeyNotFoundException;
import org.hyperic.hq.appdef.shared.CPropManagerLocal;
import org.hyperic.hq.appdef.shared.CPropManagerUtil;
import org.hyperic.hq.appdef.server.session.AppdefResourceType;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.autoinventory.server.session.AgentReportStatusDAO;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.common.util.Messenger;
import org.hyperic.hq.dao.AIServerDAO;
import org.hyperic.hq.appdef.server.session.CpropDAO;
import org.hyperic.hq.appdef.server.session.CpropKeyDAO;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.product.TypeInfo;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.EncodingException;
import org.hyperic.util.jdbc.DBUtil;

/**
 * @ejb:bean name="CPropManager"
 *      jndi-name="ejb/appdef/CPropManager"
 *      local-jndi-name="LocalCPropManager"
 *      view-type="local"
 *      type="Stateless"
 * @ejb:util generate="physical"
 * @ejb:transaction type="Required"
 */
public class CPropManagerEJBImpl
    extends AppdefSessionUtil
    implements SessionBean
{
    private final int    CHUNKSIZE      = 1000; // Max size for each row
    private final String CPROP_TABLE    = "EAM_CPROP";
    private final String CPROPKEY_TABLE = "EAM_CPROP_KEY";
    
    private Log log = 
        LogFactory.getLog(CPropManagerEJBImpl.class.getName());

    private Messenger sender = new Messenger();
    
    /**
     * Get all the keys associated with an appdef resource type.
     * 
     * @param appdefType   One of AppdefEntityConstants.APPDEF_TYPE_*
     * @param appdefTypeId The ID of the appdef resource type
     *
     * @return a List of CPropKeyValue objects
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public List getKeys(int appdefType, int appdefTypeId){
        return getCPropKeyDAO().findByAppdefType(appdefType, appdefTypeId);
    }

    /**
     * find appdef resource type
     * @ejb:interface-method
     */
    public AppdefResourceType findResourceType(TypeInfo info)
    {
        return super.findResourceType(info);
    }

    /**
     * find Cprop by key to a resource type based on a TypeInfo object.
     * @ejb:interface-method
     */
    public CpropKey findByKey(AppdefResourceType appdefType, String key)
    {
        int type = appdefType.getAppdefType();
        int instanceId = appdefType.getId().intValue();

        return getCPropKeyDAO().findByKey(type, instanceId, key);
    }

    /**
     * Add a key to a resource type based on a TypeInfo object.
     *
     * @throw AppdefEntityNotFoundException if the appdef resource type
     *        that the key references could not be found
     * @throw CPropKeyExistsException if the key already exists
     * @ejb:interface-method
     */
    public void addKey(AppdefResourceType appdefType,
                       String key, String description)
    {
        int type = appdefType.getAppdefType();
        int instanceId = appdefType.getId().intValue();

        getCPropKeyDAO().create(type, instanceId, key, description);
    }

    /**
     * Add a key to a resource type.  The key's 'appdefType' and
     * 'appdefTypeId' fields are used to locate the resource -- if
     * that resource does not exist, an AppdefEntityNotFoundException
     * will be thrown.
     *
     * @param key Key to create
     * @throw AppdefEntityNotFoundException if the appdef resource type
     *        that the key references could not be found
     * @throw CPropKeyExistsException if the key already exists
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void addKey(CpropKey key)
        throws AppdefEntityNotFoundException, CPropKeyExistsException
    {
        AppdefResourceType recValue;
        CpropKeyDAO cpHome;
        CpropKey cpKey;

        // Insure that the resource type exists
        recValue = this.findResourceType(key.getAppdefType(),
                                         key.getAppdefTypeId());
        cpHome = getCPropKeyDAO();

        cpKey = cpHome.findByKey(key.getAppdefType(),
                                 key.getAppdefTypeId(), key.getKey());

        if(cpKey != null){
            throw new CPropKeyExistsException("Key, '" + key.getKey() + "', " +
               "already exists for " +
               AppdefEntityConstants.typeToString(recValue.getAppdefType()) +
               " type, '" + recValue.getName() + "'");
        }

        cpHome.create(key.getAppdefType(), key.getAppdefTypeId(),
                      key.getKey(), key.getDescription());
    }

    /**
     * Remove a key from a resource type.
     *
     * @param appdefType   One of AppdefEntityConstants.APPDEF_TYPE_*
     * @param appdefTypeId The ID of the resource type
     * @param key          Key to remove
     *
     * @throw CPropKeyNotFoundException if the CPropKey could not be found
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void deleteKey(int appdefType, int appdefTypeId, String key)
        throws CPropKeyNotFoundException
    {
        CpropKeyDAO cpHome;
        CpropKey cpKey;

        cpHome = getCPropKeyDAO();

        cpKey = cpHome.findByKey(appdefType, appdefTypeId, key);
        if (cpKey == null) {
            throw new CPropKeyNotFoundException("Key, '" + key + "', does not"+
                             " exist for " +
                             AppdefEntityConstants.typeToString(appdefType) +
                             " " + appdefTypeId);
        }
        // cascade on delete to remove Cprop as well
        cpHome.remove(cpKey);
    }

    /**
     * Set (or delete) a custom property for a resource.  If the 
     * property already exists, it will be overwritten.
     *
     * @param aID Appdef entity id to set the value for
     * @param typeId Resource type id
     * @param key  Key to associate the value with
     * @param val  Value to assicate with the key.  If the value is null,
     *             then the value will simply be removed.
     *
     * @throw CPropKeyNotFoundException if the key has not been created
     *        for the resource's associated type
     * @throw AppdefEntityNotFoundException if id for 'aVal' specifies
     *        a resource which does not exist
     * XXX: scottmf, we should move this over to hql at some point rather than
     * trying to manage the transaction via jdbc within this container
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void setValue(AppdefEntityID aID, int typeId, String key, 
                         String val)
        throws CPropKeyNotFoundException, AppdefEntityNotFoundException,
               PermissionException
    {
        Statement stmt = null;
        PreparedStatement pstmt = null;
        CpropKey propKey;
        Connection conn = null;
        ResultSet rs = null;
        StringBuilder sql;
        
        propKey = getKey(aID, typeId, key);

        try {
            Integer pk = propKey.getId();
            final int keyId = pk.intValue();

            conn = Util.getConnection();
            stmt = conn.createStatement();
                                    
            // no need to grab the for update since we are in a transaction
            // and therefore automatically get a shared lock
            sql = new StringBuilder()
                .append("SELECT PROPVALUE FROM ").append(CPROP_TABLE)
                .append(" WHERE KEYID=").append(keyId)
                .append(" AND APPDEF_ID=").append(aID.getID());
            rs = stmt.executeQuery(sql.toString());
            String oldval = null;
            if (rs.next()) {
                // vals are the same, no update
                if ((oldval = rs.getString(1)).equals(val)) {
                    return;
                }
            }

            DBUtil.closeStatement(this, stmt);
            if (oldval != null) {
                stmt = conn.createStatement();
                sql = new StringBuilder()
                    .append("DELETE FROM ").append(CPROP_TABLE)
                    .append(" WHERE KEYID=").append(keyId)
                    .append(" AND APPDEF_ID=").append(aID.getID());
                stmt.executeUpdate(sql.toString());
            }
            
            // Optionally add new values
            if (val != null){
                String[] chunks = chunk(val, CHUNKSIZE);
                sql = new StringBuilder()
                    .append("INSERT INTO ").append(CPROP_TABLE);

                Cprop nprop = new Cprop();
                sql.append(" (id,keyid,appdef_id,value_idx,PROPVALUE) VALUES (")
                   .append(Util.generateId("org.hyperic.hq.appdef.server.session.Cprop", nprop))
                   .append(", ?, ?, ?, ?)");
                
                pstmt = conn.prepareStatement(sql.toString());
                pstmt.setInt(1, keyId);
                pstmt.setInt(2, aID.getID());
                for(int i=0; i<chunks.length; i++){
                    pstmt.setInt(3, i);
                    pstmt.setString(4, chunks[i]);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            if (log.isDebugEnabled())
                log.debug("Entity " + aID.getAppdefKey() + " " + key +
                          " changed from " + oldval + " to " + val);
            
            // Send cprop value changed event
            CPropChangeEvent event = new CPropChangeEvent(aID, key, oldval, 
                                                          val);
            
            // Now publish the event
            sender.publishMessage(EventConstants.EVENTS_TOPIC, event);
        } catch(SQLException exc){
            log.error("Unable to update CPropKey values: " + 
                      exc.getMessage(), exc);
            throw new SystemException(exc);
        } finally {
            DBUtil.closeResultSet(this, rs);
            DBUtil.closeStatement(this, stmt);
            DBUtil.closeStatement(this, pstmt);
            // XXX scottmf, this is probably not the right thing to do since
            // it will commit the transaction which is already container
            // managed
            Util.endConnection();
        }
    }

    /**
     * Get a custom property for a resource.  
     *
     * @param aVal Appdef entity to get the value for
     * @param key  Key of the value to get
     *
     * @return The value associated with 'key' if found, else null
     *
     * @throw CPropKeyNotFoundException if the key for the associated
     *        resource is not found
     * @throw AppdefEntityNotFoundException if the passed entity is
     *        not found
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public String getValue(AppdefEntityValue aVal, String key)
        throws CPropKeyNotFoundException, AppdefEntityNotFoundException,
               PermissionException
    {
        CpropKey propKey;
        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;
        AppdefEntityID aID = aVal.getID();
        AppdefResourceType recType = aVal.getAppdefResourceType();
        int typeId  = recType.getId().intValue();
        
        propKey = this.getKey(aID, typeId, key);
        try {
            Integer pk = propKey.getId();
            final int keyId = pk.intValue();
            StringBuffer buf = new StringBuffer();
            boolean didSomething;

            conn = Util.getConnection();
            stmt = conn.prepareStatement("SELECT PROPVALUE FROM " + 
                                         CPROP_TABLE +
                                         " WHERE KEYID=? AND APPDEF_ID=? " +
                                         "ORDER BY VALUE_IDX");
            stmt.setInt(1, keyId);
            stmt.setInt(2, aID.getID());
            rs = stmt.executeQuery();
            
            didSomething = false;
            while(rs.next()){
                didSomething = true;
                buf.append(rs.getString(1));
            }

            if(didSomething)
                return buf.toString();
            else
                return null;
        } catch(SQLException exc){
            this.log.error("Unable to get CPropKey values: " +
                           exc.getMessage(), exc);
            throw new SystemException(exc);
        } finally {
            DBUtil.closeResultSet(this, rs);
            DBUtil.closeStatement(this, stmt);
            Util.endConnection();
        }
    }

    private Properties getEntries(AppdefEntityID aID, String column) {
        PreparedStatement stmt = null;
        Connection conn = null;
        Properties res = new Properties();
        ResultSet rs = null;

        try {
            StringBuffer buf;
            String lastKey;

            conn = Util.getConnection();
            stmt = conn.prepareStatement("SELECT A." + column +
                                         ", B.propvalue FROM " + 
                                         CPROPKEY_TABLE + " A, " +
                                         CPROP_TABLE + " B WHERE " +
                                         "B.keyid=A.id AND A.appdef_type=? " +
                                         "AND B.appdef_id=? " + 
                                         "ORDER BY B.value_idx");
            stmt.setInt(1, aID.getType());
            stmt.setInt(2, aID.getID());
            rs = stmt.executeQuery();

            lastKey = null;
            buf     = null;
            while(rs.next()){
                String keyName = rs.getString(1);
                String valChunk = rs.getString(2);
                
                if(lastKey == null || lastKey.equals(keyName) == false){
                    if(lastKey != null){
                        res.setProperty(lastKey, buf.toString());
                    }

                    buf     = new StringBuffer();
                    lastKey = keyName;
                }
                
                buf.append(valChunk);
            }

            // Have one at the end to add
            if(buf != null && buf.length() != 0){
                res.setProperty(lastKey, buf.toString());
            }
        } catch(SQLException exc){
            log.error("Unable to get CPropKey values: " +
                      exc.getMessage(), exc);
            throw new SystemException(exc);
        } finally {
            DBUtil.closeResultSet(this, rs);
            DBUtil.closeStatement(this, stmt);
            Util.endConnection();
        }

        return res;
    }
    
    /**
     * Get a map which holds the keys & their associated values
     * for an appdef entity.
     *
     * @param aID Appdef entity id to get the custom properties for
     *
     * @return The properties stored for a specific entity ID. 
     *         An empty Properties object will be returned if there are
     *         no custom properties defined for the resource
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Properties getEntries(AppdefEntityID aID)
        throws PermissionException, AppdefEntityNotFoundException
    {
        return getEntries(aID, "propkey");
    }

    /**
     * Get a map which holds the descriptions & their associated values
     * for an appdef entity.
     *
     * @param aID Appdef entity id to get the custom properties for
     *
     * @return The properties stored for a specific entity ID
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public Properties getDescEntries(AppdefEntityID aID)
        throws PermissionException, AppdefEntityNotFoundException
    {
        return getEntries(aID, "description");
    }

    /**
     * Set custom properties for a resource.  If the 
     * property already exists, it will be overwritten.
     *
     * @param aID Appdef entity id to set the value for
     * @param typeId Resource type id
     * @param data Encoded ConfigResponse
     *
     * @ejb:interface-method
     */
    public void setConfigResponse(AppdefEntityID aID, int typeId, byte[] data)
        throws PermissionException, AppdefEntityNotFoundException 
    {
        if (data == null) {
            return;
        }

        ConfigResponse cprops;
        try {
            cprops = ConfigResponse.decode(data);
        } catch (EncodingException e) {
            throw new SystemException(e);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("cprops=" + cprops);
            log.debug("aID=" + aID.toString() + ", typeId=" + typeId);
        }

        for (Iterator it=cprops.getKeys().iterator(); it.hasNext();) {
            String key = (String)it.next();
            String val = cprops.getValue(key);
            try {
                setValue(aID, typeId, key, val);
            } catch (CPropKeyNotFoundException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Remove custom properties for a given resource.
     *
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void deleteValues(int appdefType, int id) {
        PreparedStatement stmt = null;
        Connection conn = null;

        try {
            conn = Util.getConnection();
            stmt = conn.prepareStatement("DELETE FROM " + CPROP_TABLE +
                                         " WHERE keyid IN " +
                                         "(SELECT id FROM " + CPROPKEY_TABLE +
                                         " WHERE appdef_type = ?) " +
                                         "AND appdef_id = ?");
            stmt.setInt(1, appdefType);
            stmt.setInt(2, id);
                                         
            stmt.executeUpdate();
        } catch(SQLException exc){
            log.error("Unable to delete CProp values: " +
                      exc.getMessage(), exc);
            throw new SystemException(exc);
        } finally {
            DBUtil.closeStatement(this, stmt);
            Util.endConnection();
        }
    }
    
    /**
     * Get all Cprops values with specified key name, irregardless of type
     * @ejb:interface-method
     */
    public List getCPropValues(AppdefResourceTypeValue appdefType, String key,
                               boolean asc) {
        int type = appdefType.getAppdefType();
        int instanceId = appdefType.getId().intValue();

        CpropKey pkey = getCPropKeyDAO().findByKey(type, instanceId, key);
        
        CpropDAO dao = new CpropDAO(DAOFactory.getDAOFactory()); 
        return dao.findByKeyName(pkey, asc);
    }

    private CpropKeyDAO getCPropKeyDAO(){
        return DAOFactory.getDAOFactory().getCpropKeyDAO();
    }

    private CpropKey getKey(AppdefEntityID aID, int typeId, String key)
        throws CPropKeyNotFoundException, AppdefEntityNotFoundException,
               PermissionException
    {
        CpropKey res;

        res = getCPropKeyDAO().findByKey(aID.getType(), typeId, key);

        if(res == null){
            String msg = "Key, '" + key + "', does " +
                "not exist for aID=" + aID + ", typeId=" + typeId;
            throw new CPropKeyNotFoundException(msg);
        }
        return res;
    }

    public static CPropManagerLocal getOne() {
        try {
            return CPropManagerUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException();
        }
    }

    public void ejbCreate() throws CreateException {}
    public void ejbRemove() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void setSessionContext(SessionContext ctx) {}

    protected AgentTypeDAO getAgentTypeDAO() {
        return new AgentTypeDAO(DAOFactory.getDAOFactory());
    }

    protected AgentReportStatusDAO getAgentReportStatusDAO() {
        return new AgentReportStatusDAO(DAOFactory.getDAOFactory());
    }

    protected AIServerDAO getAIServerDAO() {
        return new AIServerDAO(DAOFactory.getDAOFactory());
    }

    /**
     * Split a string into a list of same sized chunks, and 
     * a chunk of potentially different size at the end, 
     * which contains the remainder.
     *
     * e.g. chunk("11223", 2) -> { "11", "22", "3" }
     *
     * @param src       String to chunk
     * @param chunkSize The max size of any chunk
     *
     * @return an array containing the chunked string
     */
    private static String[] chunk(String src, int chunkSize){
        String[] res;
        int strLen, nAlloc;
    
        if(chunkSize <= 0){
            throw new IllegalArgumentException("chunkSize must be >= 1");
        }
    
        strLen = src.length();
        nAlloc = strLen / chunkSize;
        if((strLen % chunkSize) != 0)
            nAlloc++;
    
        res = new String[nAlloc];
        for(int i=0; i<nAlloc; i++){
            int begIdx, endIdx;
    
            begIdx = i * chunkSize;
            endIdx = (i + 1) * chunkSize;
            if(endIdx > strLen)
                endIdx = strLen;
    
            res[i] = src.substring(begIdx, endIdx);
        }
        return res;
    }
}
