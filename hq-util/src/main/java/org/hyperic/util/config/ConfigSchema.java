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

package org.hyperic.util.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ConfigSchema implements Serializable {

	private static final long serialVersionUID = 8171794117881852319L;

	private ArrayList configOptions;
	
	private static HashMap secrets = new HashMap();

    public ConfigSchema(){
        this.configOptions = new ArrayList();
    }

    /**
     * Construct a ConfigSchema based on the array of ConfigOptions
     * provided.
     * @param options An array of ConfigOptions to populate this
     * schema with.
     */
    public ConfigSchema ( ConfigOption[] options ) {
        this.configOptions = new ArrayList();
        this.configOptions.addAll( Arrays.asList(options) );
    }

    public List<ConfigOption> getOptions(){
        return this.configOptions;
    }

    /**
     * @return Map of getOptions() using ConfigOption.getName() for the keys
     */
    public Map getOptionsMap() {
        //XXX would use this.optionsMap but dont
        //want to break serial uid
        Map opts = new HashMap();
        int size = this.configOptions.size();
        for (int i=0; i<size; i++) {
            ConfigOption option =
                (ConfigOption)this.configOptions.get(i);
            opts.put(option.getName(), option);
        }
        return opts;
    }

    /**
     * @param name ConfigOption.getName() value
     * @return ConfigOption that matches the name param
     */
    public ConfigOption getOption(String name) {
        return (ConfigOption)this.getOptionsMap().get(name);
    }

    public String[] getOptionNames() {
        int size = this.configOptions.size();
        String[] names = new String[size];
        for (int i=0; i<size; i++) {
            ConfigOption option =
                (ConfigOption)this.configOptions.get(i);
            names[i] = option.getName();
        }
        return names;
    }

    public Map getDefaultProperties() {
        HashMap props = new HashMap();
        for (int i=0; i<this.configOptions.size(); i++) {
            ConfigOption opt = (ConfigOption)this.configOptions.get(i);
            String defVal = opt.getDefault();
            if (defVal != null) {
                props.put(opt.getName(), defVal);
            }
        }
        return props;
    }

    public void addOption(ConfigOption option){
        this.configOptions.remove(option);
        this.configOptions.add(option);
    }

    public void addOptions(List options){
        this.configOptions.removeAll(options);
        this.configOptions.addAll(options);
    }
    
    public byte[] encode() throws EncodingException {
    	ObjectOutputStream objectStream = null;
    	byte[] retVal = null;
    	try {
    		final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    		objectStream = new ObjectOutputStream(byteStream);
    	
    		for (Iterator iterator = configOptions.iterator(); iterator.hasNext();) {
    			final ConfigOption configOption = (ConfigOption) iterator.next();
    			objectStream.writeObject(configOption);
    		}
    		objectStream.writeObject(null);
    		objectStream.flush();
    		retVal = byteStream.toByteArray();
    	} catch (IOException exc) {
    		throw new EncodingException(exc.toString());
    	} finally {
    		// ObjectStreams MUST be closed.
    		if (objectStream != null)
    			try {
    				objectStream.close();
    		} catch (Exception ex) {
    		}
    	}
    	return retVal;
    }
    	 
    public static ConfigSchema decode(byte[] data) throws EncodingException,
    	InvalidOptionException, InvalidOptionValueException {
    	try {
    		ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
    		ObjectInputStream objectStream = new ObjectInputStream(byteStream);
    		ConfigSchema schema = new ConfigSchema();
    	
    		// Read attributes
    		while (true) {
    			ConfigOption configOption;
    			if ((configOption = (ConfigOption)objectStream.readObject()) == null) {
    				break;
    			}
    			schema.addOption(configOption);
    		}
    		return schema;
    	} catch (IOException exc) {
    		throw new EncodingException(exc.toString());
    	} catch (ClassNotFoundException exc) {
    		throw new EncodingException(exc.toString());
    	}
    }

    /**
     * Change the default value for a given property within the schema.
     */
    public void setDefault(String prop, String value){
        for (int i=0; i<this.configOptions.size(); i++) {
            ConfigOption opt = (ConfigOption)this.configOptions.get(i);
            if (opt.getName().equals(prop)) {
                opt.setDefault(value);
                break;
            }
        }
    }
    
    public static boolean isSecret(String key) {
        synchronized(secrets) {
            Object obj = secrets.get(key);
            return (obj != null && obj.equals(Boolean.TRUE)) ? true : false;
        }
    }
    
    public static void addSecret(String key) {
        synchronized(secrets) {
            secrets.put(key, Boolean.TRUE);
        }
    }
}
