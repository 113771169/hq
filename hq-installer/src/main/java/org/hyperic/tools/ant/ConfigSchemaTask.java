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

package org.hyperic.tools.ant;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.sigar.util.Getline;
import org.hyperic.tools.ant.installer.InstallerConfigSchemaProvider;
import org.hyperic.util.config.AutomatedResponseBuilder;
import org.hyperic.util.config.ConfigResponse;
import org.hyperic.util.config.ConfigSchema;
import org.hyperic.util.config.EarlyExitException;
import org.hyperic.util.config.InteractiveResponseBuilder;
import org.hyperic.util.config.InteractiveResponseBuilder_IOHandler;
import org.hyperic.util.config.ReturnStepsException;
import org.hyperic.util.config.SkipConfigException;
import org.hyperic.util.security.KeystoreConfig;
import org.hyperic.util.security.KeystoreManager;

public class ConfigSchemaTask 
    extends Task
    implements InteractiveResponseBuilder_IOHandler {
    
    public void setEncryptedKeys(String encryptedKeys) {
        StringTokenizer st = new StringTokenizer(encryptedKeys,",");
        this.itsEncryptedKeys = new String[st.countTokens()];
        for (int i=0;i<this.itsEncryptedKeys.length;i++) {
            this.itsEncryptedKeys[i] = st.nextToken();
        }
    }

    private String itsClass;
    private String itsInputPrefix;
    private String itsOutputPrefix;
    private String itsInstallDirPropName;
    private File itsFile;
    private File itsLoadFile;
    private File itsCompletionFile;
    private String itsIfDefined;
    private boolean itsReplaceInstallDir = false;
    private String[] itsEncryptedKeys;
    private String itsPBEKey;
    private Project itsProject;
    private InstallerConfigSchemaProvider itsSchemaProvider;
    private ConfigResponse itsResponse;
    private final Getline gl;

    public ConfigSchemaTask () {
        try {
            Sigar.load();
        } catch (SigarException e) {
            e.printStackTrace(); //should never happen
        }
        this.gl = new Getline();
    }
    
    public void setClass ( String c ) {
        itsClass = c;
    }
    
    public void setInputPrefix ( String p ) {
        itsInputPrefix = p;
    }
    
    public void setOutputPrefix ( String p ) {
        itsOutputPrefix = p;
    }
    
    public void setFile ( File f ) {
        itsFile = f;
    }

    public void setLoadFile ( File f ) {
        itsLoadFile = f;
    }

    public void setInstallDirPropName ( String p ) {
        itsInstallDirPropName = p;
    }

    public void setCompletionFile ( File f ) {
        itsCompletionFile = f;
    }

    public void setIfDefined ( String prop ) {
        itsIfDefined = prop;
    }

    public void setReplaceInstallDir( boolean b ) {
        itsReplaceInstallDir = b;
    }
    @Override
	public void execute () throws BuildException {
        
        validateAttributes();
        itsProject = getProject();

        boolean isInteractive = (itsLoadFile == null);

        itsSchemaProvider.setProjectProperties(itsProject.getProperties());
        String installDir = itsProject.getProperty(itsInstallDirPropName);
        itsSchemaProvider.setInstallDir(installDir);

        ConfigSchema schema;
        InteractiveResponseBuilder sb;

        try {
            sb = isInteractive
                ? new InteractiveResponseBuilder(this)
                : new AutomatedResponseBuilder(this, 
                                               this.itsLoadFile,
                                               this.itsIfDefined,
                                               this.itsPBEKey,
                                               this.itsEncryptedKeys);
        } catch (IOException e) {
            throw new BuildException("Error loading properties from file: " 
                                     + this.itsLoadFile.getPath());
        }
        ConfigResponse response = null;
        ConfigResponse mergedResponse = null;

        int i=0;
        try {
            schema = itsSchemaProvider.getSchema(response, i);
        } catch (SkipConfigException e) {
            return;

        } catch (EarlyExitException e) {
            throw new BuildException(e);
        }
        while ( schema != null ) {

            try {
                response = sb.processConfigSchema(schema);
            } catch (SkipConfigException e) {
                return;
                
            } catch ( EarlyExitException e ) {
                throw new BuildException(e);

            } catch ( Exception e ) {
                throw new BuildException("Error getting configuration: "
                                         + e, e);
            }

            if ( mergedResponse == null ) mergedResponse = response;
            else mergedResponse.merge(response, true);

            i++;
            try {
                schema = itsSchemaProvider.getSchema(mergedResponse, i);
            } 
            catch (EarlyExitException e) {
                throw new BuildException(e);
            }
            catch (ReturnStepsException e) {
            	errOutput("---------------------------------------------------------------------------------");
            	errOutput(e.getMessage());
            	errOutput("---------------------------------------------------------------------------------");
            	i = e.getStepToReturnTo();
            	schema = itsSchemaProvider.getSchema(mergedResponse, i);
            }
        }
        
        // ...generate or load our internal keystore...
        // ...store in data dir temporarily and copy it to correct location in ant script...
    	String path = itsFile.getParent(); 
    	String filePath = path + "/hyperic.keystore";
    	String filePassword = "hyperic";
    	KeystoreConfig config = new KeystoreConfig("hq", filePath, filePassword, true);
    	
    	try {
			KeystoreManager.getKeystoreManager().getKeyStore(config);
		} catch (KeyStoreException e) {
			throw new BuildException(e);
		} catch (IOException e) {
			throw new BuildException(e);
		}
        
        if (itsReplaceInstallDir) {
            String newInstallDir
                = mergedResponse.getValue(itsSchemaProvider.getBaseName() 
                                          + ".installdir");
            itsProject.setProperty(itsInstallDirPropName, 
                                   newInstallDir);
        }
        
        // Generate summary text
        String completionText
            = itsSchemaProvider.getCompletionText(mergedResponse);
        if (completionText == null) completionText = "";

        // write to files
        Iterator iter = mergedResponse.getKeys().iterator();
        String key, value;
        Properties props = new Properties();
        while ( iter.hasNext() ) {
            key = (String) iter.next();
            value = mergedResponse.getValue(key);
            props.setProperty(key, value);
        }
        FileOutputStream fos = null;
        FileWriter fw = null;
        try {
            if (itsFile != null) {
                fos = new FileOutputStream(itsFile.getAbsolutePath(), true);
                props.store(fos, "Autogenerated Configuration");
            }
            if (itsCompletionFile != null) {
                fw = new FileWriter(itsCompletionFile);
                fw.write(completionText);
            }
        } catch ( Exception e ) {
            throw new BuildException("Error writing to properties file: " 
                                     + e, e);
        } finally {
            if (fos != null) try { fos.close(); } catch (IOException e) {}
            if (fw != null) try { fw.close(); } catch (IOException e) {}
        }
    }
    
    public void setItsPBEKeyFile(File itsPBEKeyFile) {
        if (itsPBEKeyFile==null) {
            throw new BuildException("itsPBEKeyFile entry defined without a value in the setup-interactive.xml file");
        }
        try (SeekableByteChannel sbc = Files.newByteChannel(file)) {
            ByteBuffer buf = ByteBuffer.allocate(10);

            String encoding = System.getProperty("file.encoding");
            while (sbc.read(buf) > 0) {
                buf.rewind();
                System.out.print(Charset.forName(encoding).decode(buf));
                buf.flip();
            }
        } catch (IOException x) {
        
        
        FileOutputStream fos = null;
        FileWriter fw = null;
        try {
            fos = new FileOutputStream(itsPBEKeyFile.getAbsolutePath(), true);
            props.store(fos, "Autogenerated Configuration");
            if (itsCompletionFile != null) {
                fw = new FileWriter(itsCompletionFile);
                fw.write(completionText);
            }
        } catch ( Exception e ) {
            throw new BuildException("Error writing to properties file: " 
                                     + e, e);
        } finally {
            if (fos != null) try { fos.close(); } catch (IOException e) {}
            if (fw != null) try { fw.close(); } catch (IOException e) {}
        }
        this.itsPBEKey = itsPBEKey;
    }

    public void validateAttributes () throws BuildException {
        if ( itsClass == null ) {
            throw new BuildException("ConfigSchema: No 'class' "
                                     + "attribute specified.");
        }
        try {
            Object o = Class.forName(itsClass).newInstance();
            itsSchemaProvider = (InstallerConfigSchemaProvider) o;

        } catch ( Exception e ) {
            throw new BuildException("ConfigSchema: error instantiating "
                                     + "schema provder class: " 
                                     + itsClass + ":" + e, e);
        }
        if ( itsOutputPrefix == null ) itsOutputPrefix = "";
        if ( itsInputPrefix == null ) itsInputPrefix = "";
        if ( itsFile == null && itsLoadFile == null ) {
            throw new BuildException("ConfigSchema: No 'file' or 'loadFile' "
                                     + "attribute specified.");
        }
        if (itsLoadFile == null && itsIfDefined != null) {
            throw new BuildException("ConfigSchema: Cannot specify 'ifDefined' "
                                     + "attribute without 'loadFile' "
                                     + "attribute.");
        }
    }

    /** @see org.hyperic.util.config.InteractiveResponseBuilder.IOHandler#handleInput */
    public String handleInput ( String prompt ) throws EOFException, IOException {
        itsProject.log(itsInputPrefix + prompt);
        return this.gl.getLine("");
    }

    public String handleHiddenInput ( String prompt ) 
        throws EOFException, IOException 
    {
        // itsProject.log(itsInputPrefix + prompt);
        return Sigar.getPassword(prompt);
    }

    /** @see org.hyperic.util.config.InteractiveResponseBuilder.IOHandler#errOutput */
    public void errOutput ( String msg ) {
        itsProject.log(itsOutputPrefix + msg);
    }

    /** @see org.hyperic.util.config.InteractiveResponseBuilder.IOHandler#isDeveloper */
    public boolean isDeveloper () {
        return false;
    }
}
