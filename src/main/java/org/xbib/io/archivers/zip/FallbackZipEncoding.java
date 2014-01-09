
package org.xbib.io.archivers.zip;

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
 * neither {@link Simple8BitZipEncoding} nor {@link NioZipEncoding} is
 * available.</p>
 */
class FallbackZipEncoding implements ZipEncoding {
    private final String charset;

    /**
     * Construct a fallback zip encoding, which uses the platform's
     * default charset.
     */
    public FallbackZipEncoding() {
        this.charset = null;
    }

    /**
     * Construct a fallback zip encoding, which uses the given charset.
     *
     * @param charset The name of the charset or {@code null} for
     *                the platform's default character set.
     */
    public FallbackZipEncoding(String charset) {
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

    /**
     * @see ZipEncoding#decode(byte[])
     */
    public String decode(byte[] data) throws IOException {
        if (this.charset == null) { // i.e. use default charset, see no-args constructor
            return new String(data);
        } else {
            return new String(data, this.charset);
        }
    }
}
