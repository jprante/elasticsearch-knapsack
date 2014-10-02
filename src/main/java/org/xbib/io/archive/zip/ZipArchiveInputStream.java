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
package org.xbib.io.archive.zip;

import org.xbib.io.archive.ArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipArchiveInputStream extends ArchiveInputStream<ZipArchiveEntry> {

    private final ZipInputStream in;

    public ZipArchiveInputStream(InputStream in) {
        this.in = new ZipInputStream(in);
    }

    @Override
    public ZipArchiveEntry getNextEntry() throws IOException {
        ZipEntry entry = in.getNextEntry();
        return entry != null ? new ZipArchiveEntry().setEntry(entry) : null;
    }

    @Override
    public int read(byte[] buffer, int start, int length) throws IOException {
        return in.read(buffer, start, length);
    }

}
