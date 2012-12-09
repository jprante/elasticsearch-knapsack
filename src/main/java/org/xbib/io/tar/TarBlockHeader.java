/*
 * Licensed to Jörg Prante and xbib under one or more contributor 
 * license agreements. See the NOTICE.txt file distributed with this work
 * for additional information regarding copyright ownership.
 *
 * Copyright (C) 2012 Jörg Prante and xbib
 * 
 * This program is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU Affero General Public License as published 
 * by the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see http://www.gnu.org/licenses 
 * or write to the Free Software Foundation, Inc., 51 Franklin Street, 
 * Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * The interactive user interfaces in modified source and object code 
 * versions of this program must display Appropriate Legal Notices, 
 * as required under Section 5 of the GNU Affero General Public License.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public 
 * License, these Appropriate Legal Notices must retain the display of the 
 * "Powered by xbib" logo. If the display of the logo is not reasonably 
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by xbib".
 */
package org.xbib.io.tar;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

/**
 * A TAR header
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarBlockHeader extends TarRecord implements TarConstants {

    public byte[] checksum = new byte[8];
    public byte[] devMajor = new byte[8];
    public byte[] devMinor = new byte[8];
    public byte[] fileName = new byte[FILE_NAME_SIZE];
    public byte[] fileSize = new byte[12];
    public byte[] gid = new byte[8];
    public byte[] groupName = new byte[32];
    public byte[] linkName = new byte[FILE_NAME_SIZE];
    public byte[] magic = new byte[8];
    public byte[] mode = new byte[8];
    public byte[] modificationTime = new byte[12];
    public byte[] uid = new byte[8];
    public byte[] userName = new byte[32];
    private boolean eofPadding = false;
    public byte linkFlag;

    /**
     * Creates a new TarBlockHeader object.
     *
     * @param data
     *
     * @throws IOException
     */
    public TarBlockHeader(byte[] data) throws IOException {
        super(data);
        populateVariables();
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

    private void populateVariables() throws IOException {
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
