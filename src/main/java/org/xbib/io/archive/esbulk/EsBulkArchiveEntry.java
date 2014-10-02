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

import org.xbib.io.archive.ArchiveEntry;

import java.util.Date;

public class EsBulkArchiveEntry implements ArchiveEntry {

    private String name;

    private long size;

    public EsBulkArchiveEntry() {
    }

    @Override
    public ArchiveEntry setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ArchiveEntry setEntrySize(long size) {
        this.size = size;
        return this;
    }

    @Override
    public long getEntrySize() {
        return size;
    }

    @Override
    public ArchiveEntry setLastModified(Date lastmodified) {
        return this;
    }

    @Override
    public Date getLastModified() {
        return null;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

}
