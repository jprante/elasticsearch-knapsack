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
package org.xbib.io.archive;

import java.util.Date;

/**
 * Represents an entry of an archive.
 */
public interface ArchiveEntry {

    /**
     * Special value indicating that the size is unknown
     */
    long SIZE_UNKNOWN = -1;

    ArchiveEntry setName(String name);

    /**
     * The name of the entry in the archive. May refer to a file or directory or other item
     */
    String getName();

    /**
     * Set the (uncompressed) entry size in bytes
     */
    ArchiveEntry setEntrySize(long size);

    /**
     * The (uncompressed) size of the entry. May be -1 (SIZE_UNKNOWN) if the size is unknown
     */
    long getEntrySize();

    /**
     * Sets the date of the last modification of this entry.
     *
     * @param date the date
     * @return this archive entry
     */
    ArchiveEntry setLastModified(Date date);

    /**
     * The last modified date of the entry.
     */
    Date getLastModified();

    /**
     * True if the entry refers to a directory
     */
    boolean isDirectory();

}
