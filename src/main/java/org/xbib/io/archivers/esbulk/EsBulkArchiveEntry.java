
package org.xbib.io.archivers.esbulk;

import org.xbib.io.archivers.ArchiveEntry;

import java.util.Date;

public class EsBulkArchiveEntry implements ArchiveEntry {

    private String name;

    private long size;

    private Date lastmodified;

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
        this.lastmodified = lastmodified;
        return this;
    }

    @Override
    public Date getLastModified() {
        return lastmodified;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }
}
