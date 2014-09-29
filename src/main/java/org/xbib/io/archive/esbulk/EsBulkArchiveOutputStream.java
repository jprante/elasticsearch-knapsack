/*
 * Copyright (C) 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.io.archive.esbulk;

import org.xbib.io.archive.ArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class EsBulkArchiveOutputStream extends ArchiveOutputStream<EsBulkArchiveEntry> {

    private final OutputStream out;

    public EsBulkArchiveOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public EsBulkArchiveEntry newArchiveEntry() {
        return new EsBulkArchiveEntry();
    }

    @Override
    public void putArchiveEntry(EsBulkArchiveEntry entry) throws IOException {
        String[] s = entry.getName().split("/");
        if (s.length < 3) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"index\":{\"_index\":\"").append(s[0]).append("\",\"_type\":\"").append(s[1]).append("\",\"_id\":\"").append(s[2]).append("\"}\n");
        out.write(sb.toString().getBytes("UTF-8"));
    }

    @Override
    public void write(byte[] buf, int offset, int size) throws IOException {
        out.write(buf, offset, size);
        out.write('\n');
        getWatcher().updateBytesTransferred(size);
    }

    @Override
    public void closeArchiveEntry() throws IOException {
        // do nothing
    }

    @Override
    public void finish() throws IOException {
        out.close();
    }

}
