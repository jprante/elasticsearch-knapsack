
package org.xbib.io.archivers.s3;

import org.xbib.io.archivers.ArchiveEntry;

import java.util.Date;

public class S3ArchiveEntry implements ArchiveEntry {

    private String name;

    private long size;

    private Date lastmodified;

    public S3ArchiveEntry() {
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
    public ArchiveEntry setLastModified(Date date) {
        this.lastmodified = date;
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
