/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.plugin.knapsack.io.tar;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

/**
 * A TAR header
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
class TarBlockHeader implements TarConstants {

    protected byte[] checksum = new byte[8];
    protected byte[] devMajor = new byte[8];
    protected byte[] devMinor = new byte[8];
    protected byte[] fileName = new byte[FILE_NAME_SIZE];
    protected byte[] fileSize = new byte[12];
    protected byte[] gid = new byte[8];
    protected byte[] groupName = new byte[32];
    protected byte[] linkName = new byte[FILE_NAME_SIZE];
    protected byte[] magic = new byte[8];
    protected byte[] mode = new byte[8];
    protected byte[] modificationTime = new byte[12];
    protected byte[] uid = new byte[8];
    protected byte[] userName = new byte[32];
    protected byte linkFlag;
    private boolean eofPadding = false;

    /**
     * Creates a new TarBlockHeader object.
     *
     * @param data
     *
     * @throws IOException
     */
    TarBlockHeader(byte[] data) throws IOException {
        populateVariables(data);
    }

    public String getDevMajor() {
        return new String(devMajor).trim();
    }

    public String getDevMinor() {
        return new String(devMinor).trim();
    }

    public File getFile() {
        return new File(getFileName());
    }

    public String getFileName() {
        return new String(fileName).trim();
    }

    public long getFileSize() {
        String sizeString = new String(fileSize).trim();
        return Long.parseLong(sizeString, 8);
    }

    public String getGID() {
        return new String(gid).trim();
    }

    public String getGroupName() {
        return new String(groupName).trim();
    }

    public char getLinkFlag() {
        return (char) linkFlag;
    }

    public String getLinkName() {
        return new String(linkName).trim();
    }

    public String getMagic() {
        return new String(magic).trim();
    }

    public String getMode() {
        return new String(mode).trim();
    }

    public long getModificationTime() {
        String mtimeString = new String(modificationTime).trim();

        return Long.parseLong(mtimeString, 8);
    }

    public String getUID() {
        return new String(uid).trim();
    }

    public String getUserName() {
        return new String(userName).trim();
    }

    public boolean isEOFPadding() {
        return eofPadding;
    }

    private void populateVariables(byte[] data) throws IOException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        DataInputStream dataInputStream = new DataInputStream(byteStream);
        dataInputStream.readFully(fileName);
        if (getFileName().equals("")) {
            eofPadding = true;
            return;
        }
        dataInputStream.readFully(mode);
        dataInputStream.readFully(uid);
        dataInputStream.readFully(gid);
        dataInputStream.readFully(fileSize);
        dataInputStream.readFully(modificationTime);
        dataInputStream.readFully(checksum);
        linkFlag = dataInputStream.readByte();
        dataInputStream.readFully(linkName);
        dataInputStream.readFully(magic);
        dataInputStream.readFully(userName);
        dataInputStream.readFully(groupName);
        dataInputStream.readFully(devMajor);
        dataInputStream.readFully(devMinor);
    }
}
