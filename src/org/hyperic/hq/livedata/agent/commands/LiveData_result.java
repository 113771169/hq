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

package org.hyperic.hq.livedata.agent.commands;

import org.hyperic.hq.agent.AgentRemoteValue;
import org.hyperic.hq.agent.AgentAssertionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

public class LiveData_result extends AgentRemoteValue {

    private static final Log _log = LogFactory.getLog(LiveData_result.class);

    private static final String PARAM_RESULT = "result";

    public void setValue(String key, String val) {
        throw new AgentAssertionException("This should never be called");
    }

    public LiveData_result() {
        super();
    }

    public LiveData_result(AgentRemoteValue val)
        throws AgentRemoteException
    {
        String resultAlreadyCompressed = val.getValue(PARAM_RESULT);
        // Only used from the LiveDataClient, where the result value has
        // already been compressed.
        setResultCompressed(resultAlreadyCompressed);
    }

    public void setResultCompressed(String alreadyCompressed) {
        super.setValue(PARAM_RESULT, alreadyCompressed);
    }

    public void setResult(String result)
        throws IOException
    {
        String compressed = compress(result);
        _log.debug("Compressed " + result.length() + " bytes to " +
                   compressed.length() + " bytes.");

        super.setValue(PARAM_RESULT, compressed);
    }

    public String getResult()
        throws IOException, DataFormatException
    {
        String compressed = super.getValue(PARAM_RESULT);
        _log.debug("Decompressing " + compressed.length() + " bytes");
        return decompress(compressed);
    }

    private String compress(String s)
        throws IOException
    {
        Deflater compressor = new Deflater();
        compressor.setInput(s.getBytes());
        compressor.finish();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int total = 0;
        byte[] buf = new byte[1024];
        while (!compressor.finished()) {
            int count = compressor.deflate(buf);
            total += count;
            bos.write(buf, 0, count);
        }

        bos.close();

        byte[] compressedData = bos.toByteArray();
        return new String(compressedData, 0, total);
    }

    public static String decompress(String s)
        throws IOException, DataFormatException
    {
        Inflater decompressor = new Inflater();
        decompressor.setInput(s.getBytes());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int total = 0;
        byte[] buf = new byte[1024];
        while (!decompressor.finished()) {
            int count = decompressor.inflate(buf);
            total += count;
            bos.write(buf, 0, count);
        }

        bos.close();

        byte[] decompressedData = bos.toByteArray();
        return new String(decompressedData, 0, total);
    }
}
