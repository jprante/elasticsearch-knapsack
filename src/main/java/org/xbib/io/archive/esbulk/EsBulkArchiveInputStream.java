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

import org.xbib.io.archive.ArchiveInputStream;
import org.xbib.io.archive.ArchiveUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EsBulkArchiveInputStream extends ArchiveInputStream<EsBulkArchiveEntry> {

    private final static Pattern indexPattern = Pattern.compile("\"_index\"\\s*:\\s*\"(.*?)\"");
    private final static Pattern typePattern = Pattern.compile("\"_type\"\\s*:\\s*\"(.*?)\"");
    private final static Pattern idPattern = Pattern.compile("\"_id\"\\s*:\\s*\"(.*?)\"");

    private final BufferedReader reader;

    private ByteArrayInputStream in;

    public EsBulkArchiveInputStream(InputStream in) throws UnsupportedEncodingException {
        this.reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    }

    @Override
    public EsBulkArchiveEntry getNextEntry() throws IOException {
        String meta = reader.readLine();
        if (meta == null) {
            return null;
        }
        String data = reader.readLine();
        if (data == null) {
            return null;
        }
        EsBulkArchiveEntry entry = new EsBulkArchiveEntry();
        StringBuilder sb = new StringBuilder();
        Matcher m = indexPattern.matcher(meta);
        if (m.find()) {
            sb.append(ArchiveUtils.encode(m.group(1), Charset.forName("UTF-8")));
        } else {
            throw new IOException("no _index found");
        }
        m = typePattern.matcher(meta);
        if (m.find()) {
            sb.append('/').append(ArchiveUtils.encode(m.group(1), Charset.forName("UTF-8")));
        } else {
            throw new IOException("no _type found");
        }
        m = idPattern.matcher(meta);
        if (m.find()) {
            sb.append('/').append(ArchiveUtils.encode(m.group(1), Charset.forName("UTF-8")));
        } else {
            throw new IOException("no _id found");
        }
        entry.setName(sb.toString());
        byte[] b = data.getBytes("UTF-8");
        entry.setEntrySize(b.length);
        this.in = new ByteArrayInputStream(b);
        return entry;
    }

    @Override
    public int read(byte[] buffer, int start, int length) throws IOException {
        return in.read(buffer, start, length);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public long skip(long value) throws IOException {
        throw new UnsupportedOperationException();
    }
}
