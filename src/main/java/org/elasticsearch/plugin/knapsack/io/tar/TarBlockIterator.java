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
class TarBlockIterator implements TarConstants {

    protected final InputStream inputStream;
    protected URI uri;

    TarBlockIterator(URI uri, InputStream in) {
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
                    int numFullFileRecords = (int) fileSize / DEFAULT_RECORDSIZE;
                    int shortRecordSize = (int) fileSize % DEFAULT_RECORDSIZE;
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
        return nextRecord(DEFAULT_RECORDSIZE);
    }

    protected byte[] nextRecord(OutputStream out) throws IOException {
        return nextRecord(out, DEFAULT_RECORDSIZE);
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
            if (recordSize < DEFAULT_RECORDSIZE) {
                inputStream.skip(DEFAULT_RECORDSIZE - recordSize);
            }
            return buf;
        }
    }
}
