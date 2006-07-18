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

package org.hyperic.tools.ant.dbupgrade;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.apache.tools.ant.BuildException;
import org.hyperic.util.jdbc.DBUtil;

public class SST_DropTable extends SchemaSpecTask {

    private String table = null;

    public SST_DropTable () {}

    public void setTable (String t) {
        table = t;
    }

    public void execute () throws BuildException {

        validateAttributes();

        Connection c = getConnection();
        PreparedStatement ps = null;
        String sql = "DROP TABLE " + table;
        try {
            // Check to see if the table exists.  If it's already there,
            // then don't re-add it.
            Connection newC = null;
            boolean foundTable = false;
            try {
                newC = getNewConnection();
                foundTable = DBUtil.checkTableExists(newC, table);
            } finally {
                DBUtil.closeConnection(ctx, newC);
            }
            if ( !foundTable ) {
                log(">>>>> Not dropping table: " + table
                    + " because it does not exist");
                return;
            }

            // Add the column.
            ps = c.prepareStatement(sql);
            log(">>>>> Dropping table " + table);
            ps.executeUpdate();

        } catch ( Exception e ) {
            throw new BuildException("Error dropping table " 
                                     + table + ": " + e, e);
        } finally {
            DBUtil.closeStatement(ctx, ps);
        }
        
    }

    private void validateAttributes () throws BuildException {
        if ( table == null )
            throw new BuildException("SchemaSpec: dropTable: No 'table' attribute specified.");
    }
}
