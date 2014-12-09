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
import java.io.InputStream;

/**
 * Archive input streams <b>MUST</b> override the
 * {@link #read(byte[], int, int)} - or {@link #read()} -
 * method so that reading from the stream generates EOF for the end of
 * data in each entry as well as at the end of the file proper.
 * The {@link #getNextEntry()} method is used to reset the input stream
 * ready for reading the data from the next entry.
 */
public abstract class ArchiveInputStream<E extends ArchiveEntry> extends InputStream {

    private BytesProgressWatcher watcher;

    public ArchiveInputStream setWatcher(BytesProgressWatcher watcher) {
        this.watcher = watcher;
        return this;
    }

    public BytesProgressWatcher getWatcher() {
        return watcher;
    }

    /**
     * Returns the next Archive Entry in this Stream.
     *
     * @return the next entry,
     * or {@code null} if there are no more entries
     * @throws java.io.IOException if the next entry could not be read
     */
    public abstract E getNextEntry() throws IOException;

    /**
     * Reads a byte of data. This method will block until enough input is
     * available.
     * <p>
     * Simply calls the {@link #read(byte[], int, int)} method.
     * <p>
     * MUST be overridden if the {@link #read(byte[], int, int)} method
     * is not overridden; may be overridden otherwise.
     *
     * @return the byte read, or -1 if end of input is reached
     * @throws java.io.IOException if an I/O error has occurred
     */
    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int num = read(b, 0, 1);
        return num == -1 ? -1 : b[0] & 0xFF;
    }

}
