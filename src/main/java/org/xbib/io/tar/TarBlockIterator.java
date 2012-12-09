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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Iterate over a TAR archive and generate blocks for each entry
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarBlockIterator {

    protected final InputStream inputStream;
    protected URI uri;

    public TarBlockIterator(URI uri, InputStream in) {
        this.uri = uri;
        this.inputStream = in;
    }

    public void close() throws IOException {
        synchronized (inputStream) {
            inputStream.close();
        }
    }

    public URI getURI() {
        return uri;
    }

    public TarBlock nextBlock() throws IOException {
        synchronized (inputStream) {
            byte[] headerData = nextRecord();
            if (headerData != null) {
                TarBlockHeader header = new TarBlockHeader(headerData);
                if (!header.isEOFPadding()) {
                    long fileSize = header.getFileSize();
                    int numFullFileRecords = (int) fileSize / TarRecord.RECORD_SIZE;
                    int shortRecordSize = (int) fileSize % TarRecord.RECORD_SIZE;
                    final ByteArrayOutputStream records = new ByteArrayOutputStream();
                    for (int recordCount = 0; recordCount < numFullFileRecords;
                            recordCount++) {
                        nextRecord(records);
                    }
                    if (shortRecordSize > 0) {
                        nextRecord(records, shortRecordSize);
                    }
                    records.close();
                    return new TarBlock(uri, header, records.toByteArray());
                }
            }
            return null;
        }
    }

    protected byte[] nextRecord() throws IOException {
        return nextRecord(TarRecord.RECORD_SIZE);
    }

    protected byte[] nextRecord(OutputStream out) throws IOException {
        return nextRecord(out, TarRecord.RECORD_SIZE);
    }

    protected byte[] nextRecord(int recordSize) throws IOException {
        return nextRecord(null, recordSize);
    }

    protected byte[] nextRecord(OutputStream out, int recordSize) throws IOException {
        synchronized (inputStream) {
            byte[] buf = new byte[recordSize];
            int offset = 0;
            int size = recordSize;
            int count;
            while (offset < recordSize) {
                if ((count = inputStream.read(buf, offset, size)) != -1) {
                    if (out != null) {
                        out.write(buf, offset, count);
                    }
                    offset += count;
                    size -= count;
                }
                if (count == -1) {
                    inputStream.close();
                    return null;
                }
            }
            if (recordSize < TarRecord.RECORD_SIZE) {
                inputStream.skip(TarRecord.RECORD_SIZE - recordSize);
            }
            return buf;
        }
    }
}
