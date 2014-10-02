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
package org.xbib.io.archive.entry;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A fallback ZipEncoding, which uses a java.io means to encode names.
 * <p/>
 * <p>This implementation is not suitable for encodings other than
 * utf-8, because java.io encodes unmappable character as question
 * marks leading to unreadable ZIP entries on some operating
 * systems.</p>
 * <p/>
 * <p>Furthermore this implementation is unable to tell whether a
 * given name can be safely encoded or not.</p>
 * <p/>
 * <p>This implementation acts as a last resort implementation, when
 * neither {@link Simple8BitArchiveEntryEncoding} nor {@link NioArchiveEntryEncoding} is
 * available.</p>
 */
class FallbackArchiveEntryEncoding implements ArchiveEntryEncoding {
    private final String charset;

    /**
     * Construct a fallback zip encoding, which uses the platform's
     * default charset.
     */
    public FallbackArchiveEntryEncoding() {
        this.charset = null;
    }

    /**
     * Construct a fallback zip encoding, which uses the given charset.
     *
     * @param charset The name of the charset or {@code null} for
     *                the platform's default character set.
     */
    public FallbackArchiveEntryEncoding(String charset) {
        this.charset = charset;
    }

    public boolean canEncode(String name) {
        return true;
    }

    public ByteBuffer encode(String name) throws IOException {
        if (this.charset == null) { // i.e. use default charset, see no-args constructor
            return ByteBuffer.wrap(name.getBytes());
        } else {
            return ByteBuffer.wrap(name.getBytes(this.charset));
        }
    }

    public String decode(byte[] data) throws IOException {
        if (this.charset == null) { // i.e. use default charset, see no-args constructor
            return new String(data);
        } else {
            return new String(data, this.charset);
        }
    }
}
