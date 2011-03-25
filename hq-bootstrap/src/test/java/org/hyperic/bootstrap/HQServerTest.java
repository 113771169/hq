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

package org.hyperic.bootstrap;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.easymock.EasyMock;
import org.hyperic.hq.common.shared.HQConstants;
import org.hyperic.sigar.OperatingSystem;
import org.hyperic.sigar.SigarException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit test of {@link HQServer}
 * @author jhickey
 * 
 */
public class HQServerTest {

    private HQServer server;
    private ServerConfigurator serverConfigurator;
    private ProcessManager processManager;
    private EngineController engineController;
    private EmbeddedDatabaseController embeddedDatabaseController;
    private String serverHome = "/Applications/HQ5/server-5.0.0";
    private String engineHome = "/Applications/HQ5/server-5.0.0/hq-engine";
    private OperatingSystem osInfo;
    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;

    @Before
    public void setUp() {
        this.serverConfigurator = EasyMock.createMock(ServerConfigurator.class);
        this.processManager = EasyMock.createMock(ProcessManager.class);
        this.engineController = EasyMock.createMock(EngineController.class);
        this.embeddedDatabaseController = EasyMock.createMock(EmbeddedDatabaseController.class);
        this.dataSource = EasyMock.createMock(DataSource.class);
        this.connection = EasyMock.createMock(Connection.class);
        this.statement = EasyMock.createMock(Statement.class);
        this.resultSet = EasyMock.createMock(ResultSet.class);
        this.osInfo = org.easymock.classextension.EasyMock.createMock(OperatingSystem.class);
        this.server = new HQServer(serverHome, engineHome, processManager,
            embeddedDatabaseController, serverConfigurator, engineController, osInfo, dataSource);
    }

    @Test
    public void testGetJavaOptsSunJava64() {
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        expectedOpts.add("-d64");
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("SunOS");
        org.easymock.classextension.EasyMock.expect(osInfo.getArch()).andReturn(
            "64-bit something or other");
        replay();
        List<String> javaOpts = server.getJavaOpts();
        verify();
        assertEquals(expectedOpts, javaOpts);
    }

    @Test
    public void testStart() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(true);
        //TODO re-enable if we add dbupgrade back to bootstrap?
//        EasyMock.expect(
//            processManager.executeProcess(EasyMock
//                .aryEq(new String[] { System.getProperty("java.home") + "/bin/java",
//                                     "-cp",
//                                     serverHome + "/lib/ant-launcher-1.7.1.jar",
//                                     "-Dserver.home=" + serverHome,
//                                     "-Dant.home=" + serverHome,
//                                     "-Dtomcat.home=" + engineHome + "/hq-server",
//                                     "-Dlog4j.configuration=" +
//                                         new File(serverHome + "/conf/log4j.xml").toURI().toURL()
//                                             .toString(),
//                                     "org.apache.tools.ant.launch.Launcher",
//                                     "-q",
//                                     "-lib",
//                                     serverHome + "/lib",
//                                     "-listener",
//                                     "org.apache.tools.ant.listener.Log4jListener",
//                                     "-buildfile",
//                                     serverHome + "/data/db-upgrade.xml",
//                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
//                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
//        EasyMock.expect(dataSource.getConnection()).andReturn(connection);
//        EasyMock.expect(connection.createStatement()).andReturn(statement);
//        EasyMock.expect(
//            statement.executeQuery("select propvalue from EAM_CONFIG_PROPS " + "WHERE propkey = '" +
//                                   HQConstants.SchemaVersion + "'")).andReturn(resultSet);
//        EasyMock.expect(resultSet.next()).andReturn(true);
//        EasyMock.expect(resultSet.getString("propvalue")).andReturn("3.1.88");
//        connection.close();
//        resultSet.close();
//        statement.close();
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        testProps.put("server.webapp.port", "7080");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("Mac OS X");
        EasyMock.expect(engineController.start(expectedOpts)).andReturn(0);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartServerAlreadyRunning() throws SigarException {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(true);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartServerUnableToTellIfRunning() throws SigarException {
        EasyMock.expect(engineController.isEngineRunning()).andThrow(new SigarException());
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartErrorConfiguring() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expectLastCall().andThrow(new NullPointerException());
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(false);
        //TODO re-enable if we add dbupgrade back to bootstrap?
//        EasyMock.expect(
//            processManager.executeProcess(EasyMock
//                .aryEq(new String[] { System.getProperty("java.home") + "/bin/java",
//                                     "-cp",
//                                     serverHome + "/lib/ant-launcher-1.7.1.jar",
//                                     "-Dserver.home=" + serverHome,
//                                     "-Dant.home=" + serverHome,
//                                     "-Dtomcat.home=" + engineHome + "/hq-server",
//                                     "-Dlog4j.configuration=" +
//                                         new File(serverHome + "/conf/log4j.xml").toURI().toURL()
//                                             .toString(),
//                                     "org.apache.tools.ant.launch.Launcher",
//                                     "-q",
//                                     "-lib",
//                                     serverHome + "/lib",
//                                     "-listener",
//                                     "org.apache.tools.ant.listener.Log4jListener",
//                                     "-buildfile",
//                                     serverHome + "/data/db-upgrade.xml",
//                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
//                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
//        EasyMock.expect(dataSource.getConnection()).andReturn(connection);
//        EasyMock.expect(connection.createStatement()).andReturn(statement);
//        EasyMock.expect(
//            statement.executeQuery("select propvalue from EAM_CONFIG_PROPS " + "WHERE propkey = '" +
//                                   HQConstants.SchemaVersion + "'")).andReturn(resultSet);
//        EasyMock.expect(resultSet.next()).andReturn(true);
//        EasyMock.expect(resultSet.getString("propvalue")).andReturn("3.1.88");
//        connection.close();
//        resultSet.close();
//        statement.close();
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        testProps.put("server.webapp.port", "7080");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("Mac OS X");
        EasyMock.expect(engineController.start(expectedOpts)).andReturn(0);
        replay();
        server.start();
        verify();
    }

    @Ignore("Re-enable if we add dbupgrade back to bootstrap?")
    @Test
    public void testStartErrorVerifyingSchema() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(true);
        EasyMock.expect(
            processManager.executeProcess(EasyMock
                .aryEq(new String[] { System.getProperty("java.home") + "/bin/java",
                                     "-cp",
                                     serverHome + "/lib/ant-launcher-1.7.1.jar",
                                     "-Dserver.home=" + serverHome,
                                     "-Dant.home=" + serverHome,
                                     "-Dtomcat.home=" + engineHome + "/hq-server",
                                     "-Dlog4j.configuration=" +
                                         new File(serverHome + "/conf/log4j.xml").toURI().toURL()
                                             .toString(),
                                     "org.apache.tools.ant.launch.Launcher",
                                     "-q",
                                     "-lib",
                                     serverHome + "/lib",
                                     "-listener",
                                     "org.apache.tools.ant.listener.Log4jListener",
                                     "-buildfile",
                                     serverHome + "/data/db-upgrade.xml",
                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
        EasyMock.expect(dataSource.getConnection()).andThrow(new SQLException());
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        testProps.put("server.webapp.port", "7080");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("Mac OS X");
        EasyMock.expect(engineController.start(expectedOpts)).andReturn(0);
        replay();
        server.start();
        verify();
    }

    @Test
    @Ignore("Re-enable if we add dbupgrade back to bootstrap?")
    public void testStartInvalidDBSchema() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(true);
        EasyMock.expect(
            processManager.executeProcess(EasyMock
                .aryEq(new String[] { System.getProperty("java.home") + "/bin/java",
                                     "-cp",
                                     serverHome + "/lib/ant-launcher-1.7.1.jar",
                                     "-Dserver.home=" + serverHome,
                                     "-Dant.home=" + serverHome,
                                     "-Dtomcat.home=" + engineHome + "/hq-server",
                                     "-Dlog4j.configuration=" +
                                         new File(serverHome + "/conf/log4j.xml").toURI().toURL()
                                             .toString(),
                                     "org.apache.tools.ant.launch.Launcher",
                                     "-q",
                                     "-lib",
                                     serverHome + "/lib",
                                     "-listener",
                                     "org.apache.tools.ant.listener.Log4jListener",
                                     "-buildfile",
                                     serverHome + "/data/db-upgrade.xml",
                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
        EasyMock.expect(dataSource.getConnection()).andReturn(connection);
        EasyMock.expect(connection.createStatement()).andReturn(statement);
        EasyMock.expect(
            statement.executeQuery("select propvalue from EAM_CONFIG_PROPS " + "WHERE propkey = '" +
                                   HQConstants.SchemaVersion + "'")).andReturn(resultSet);
        EasyMock.expect(resultSet.next()).andReturn(true);
        EasyMock.expect(resultSet.getString("propvalue")).andReturn(
            HQConstants.SCHEMA_MOD_IN_PROGRESS);
        connection.close();
        resultSet.close();
        statement.close();
        replay();
        server.start();
        verify();
    }

    @Ignore("Re-enable if we add dbupgrade back to bootstrap?")
    @Test
    public void testStartNoDBResultsWithSchemaCheck() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(true);
        EasyMock.expect(
            processManager.executeProcess(EasyMock
                .aryEq(new String[] { System.getProperty("java.home") + "/bin/java",
                                     "-cp",
                                     serverHome + "/lib/ant-launcher-1.7.1.jar",
                                     "-Dserver.home=" + serverHome,
                                     "-Dant.home=" + serverHome,
                                     "-Dtomcat.home=" + engineHome + "/hq-server",
                                     "-Dlog4j.configuration=" +
                                         new File(serverHome + "/conf/log4j.xml").toURI().toURL()
                                             .toString(),
                                     "org.apache.tools.ant.launch.Launcher",
                                     "-q",
                                     "-lib",
                                     serverHome + "/lib",
                                     "-listener",
                                     "org.apache.tools.ant.listener.Log4jListener",
                                     "-buildfile",
                                     serverHome + "/data/db-upgrade.xml",
                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
        EasyMock.expect(dataSource.getConnection()).andReturn(connection);
        EasyMock.expect(connection.createStatement()).andReturn(statement);
        EasyMock.expect(
            statement.executeQuery("select propvalue from EAM_CONFIG_PROPS " + "WHERE propkey = '" +
                                   HQConstants.SchemaVersion + "'")).andReturn(resultSet);
        EasyMock.expect(resultSet.next()).andReturn(false);
        connection.close();
        resultSet.close();
        statement.close();
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        testProps.put("server.webapp.port", "7080");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("Mac OS X");
        EasyMock.expect(engineController.start(expectedOpts)).andReturn(0);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartErrorStartingDB() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andThrow(
            new NullPointerException());
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartStartDBFailed() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(false);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStop() throws Exception {
        EasyMock.expect(engineController.stop()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.stopBuiltInDB()).andReturn(true);
        replay();
        server.stop();
        verify();
    }

    @Test
    public void testStopErrorStoppingEngine() throws Exception {
        EasyMock.expect(engineController.stop()).andThrow(new SigarException());
        replay();
        server.stop();
        verify();
    }

    @Test
    public void testStopErrorStoppingBuiltInDB() throws Exception {
        EasyMock.expect(engineController.stop()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.stopBuiltInDB()).andThrow(new SigarException());
        replay();
        server.stop();
        verify();
    }

    private void replay() {
        EasyMock.replay(engineController, serverConfigurator, processManager,
            embeddedDatabaseController, dataSource, connection, statement, resultSet);
        org.easymock.classextension.EasyMock.replay(osInfo);
    }

    private void verify() {
        EasyMock.verify(engineController, serverConfigurator, processManager,
            embeddedDatabaseController, dataSource, connection, statement, resultSet);
        org.easymock.classextension.EasyMock.verify(osInfo);
    }

}
