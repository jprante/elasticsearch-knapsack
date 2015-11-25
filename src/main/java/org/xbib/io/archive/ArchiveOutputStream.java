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

import org.xbib.io.BytesProgressWatcher;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Archive output stream implementations are expected to override the
 * {@link #write(byte[], int, int)} method to improve performance.
 * They should also override {@link #close()} to ensure that any necessary
 * trailers are added.
 * The normal sequence of calls for working with ArchiveOutputStreams is:
 * + create ArchiveOutputStream object
 * + write header (optional, Zip only)
 * + repeat as needed
 * - putArchiveEntry() (writes entry header)
 * - write() (writes entry data)
 * - closeArchiveEntry() (closes entry)
 * + finish() (ends the addition of entries)
 * + write additional data if format supports it (optional)
 * + close()
 */
public abstract class ArchiveOutputStream<E extends ArchiveEntry> extends OutputStream {

    private BytesProgressWatcher watcher;

    public ArchiveOutputStream setWatcher(BytesProgressWatcher watcher) {
        this.watcher = watcher;
        return this;
    }

    public BytesProgressWatcher getWatcher() {
        return watcher;
    }

    /**
     * Temporary buffer used for the {@link #write(int)} method
     */
    private final byte[] oneByte = new byte[1];

    /**
     * Creates a new archive entry for this archive output stream
     *
     * @return the new archive entry
     * @throws IOException
     */
    public abstract E newArchiveEntry() throws IOException;

    /**
     * Writes the headers for an archive entry to the output stream.
     * The caller must then write the content to the stream and call
     * {@link #closeArchiveEntry()} to complete the process.
     *
     * @param entry describes the entry
     * @throws java.io.IOException
     */
    public abstract void putArchiveEntry(E entry) throws IOException;

    /**
     * Closes the archive entry, writing any trailer information that may
     * be required.
     *
     * @throws java.io.IOException
     */
    public abstract void closeArchiveEntry() throws IOException;

    /**
     * Finishes the addition of entries to this stream, without closing it.
     * Additional data can be written, if the format supports it.
     * <p>
     * The finish() method throws an Exception if the user forgets to close the entry
     * .
     *
     * @throws java.io.IOException
     */
    public abstract void finish() throws IOException;

    /**
     * Writes a byte to the current archive entry.
     * <p>
     * This method simply calls write( byte[], 0, 1 ).
     *
     * @param b The byte to be written.
     * @throws java.io.IOException on error
     */
    @Override
    public void write(int b) throws IOException {
        oneByte[0] = (byte) (b & 0xFF);
        write(oneByte, 0, 1);
    }

}
