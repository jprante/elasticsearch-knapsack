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

import org.xbib.io.archive.ArchiveEntry;

import java.util.Date;
import java.util.zip.ZipEntry;

public class ZipArchiveEntry implements ArchiveEntry {

    private ZipEntry entry;

    public ZipArchiveEntry setEntry(ZipEntry entry) {
        this.entry = entry;
        return this;
    }

    public ZipEntry getEntry() {
        return entry;
    }

    @Override
    public ArchiveEntry setName(String name) {
        entry = new ZipEntry(name);
        return this;
    }

    @Override
    public String getName() {
        return entry.getName();
    }

    @Override
    public ArchiveEntry setEntrySize(long size) {
        entry.setSize(size);
        return this;
    }

    @Override
    public long getEntrySize() {
        return entry.getSize();
    }

    @Override
    public ArchiveEntry setLastModified(Date date) {
        entry.setTime(date.getTime());
        return this;
    }

    @Override
    public Date getLastModified() {
        return new Date(entry.getTime());
    }

    @Override
    public boolean isDirectory() {
        return false;
    }
}
