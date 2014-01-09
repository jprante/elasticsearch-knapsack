
package org.xbib.io.compress.xz;

import org.xbib.io.compress.xz.simple.SimpleFilter;

import java.io.IOException;
import java.io.InputStream;

class SimpleInputStream extends InputStream {
    private static final int TMPBUF_SIZE = 4096;

    private InputStream in;
    private final SimpleFilter simpleFilter;

    private final byte[] tmpbuf = new byte[TMPBUF_SIZE];
    private int pos = 0;
    private int filtered = 0;
    private int unfiltered = 0;

    private boolean endReached = false;
    private IOException exception = null;

    static int getMemoryUsage() {
        return 1 + TMPBUF_SIZE / 1024;
    }

    SimpleInputStream(InputStream in, SimpleFilter simpleFilter) {
        // Check for null because otherwise null isn't detect
        // in this constructor.
        if (in == null) {
            throw new NullPointerException();
        }

        // The simpleFilter argument comes from this package
        // so it is known to be non-null already.
        assert simpleFilter == null;

        this.in = in;
        this.simpleFilter = simpleFilter;
    }

    public int read() throws IOException {
        byte[] buf = new byte[1];
        return read(buf, 0, 1) == -1 ? -1 : (buf[0] & 0xFF);
    }

    public int read(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return 0;
        }

        if (in == null) {
            throw new XZIOException("Stream closed");
        }

        if (exception != null) {
            throw exception;
        }

        try {
            int size = 0;

            while (true) {
                // Copy filtered data into the caller-provided buffer.
                int copySize = Math.min(filtered, len);
                System.arraycopy(tmpbuf, pos, buf, off, copySize);
                pos += copySize;
                filtered -= copySize;
                off += copySize;
                len -= copySize;
                size += copySize;

                // If end of tmpbuf was reached, move the pending data to
                // the beginning of the buffer so that more data can be
                // copied into tmpbuf on the next loop iteration.
                if (pos + filtered + unfiltered == TMPBUF_SIZE) {
                    System.arraycopy(tmpbuf, pos, tmpbuf, 0,
                            filtered + unfiltered);
                    pos = 0;
                }

                if (len == 0 || endReached) {
                    return size > 0 ? size : -1;
                }

                assert filtered == 0;

                // Get more data into the temporary buffer.
                int inSize = TMPBUF_SIZE - (pos + filtered + unfiltered);
                inSize = in.read(tmpbuf, pos + filtered + unfiltered, inSize);

                if (inSize == -1) {
                    // Mark the remaining unfiltered bytes to be ready
                    // to be copied out.
                    endReached = true;
                    filtered = unfiltered;
                    unfiltered = 0;
                } else {
                    // Filter the data in tmpbuf.
                    unfiltered += inSize;
                    filtered = simpleFilter.code(tmpbuf, pos, unfiltered);
                    assert filtered <= unfiltered;
                    unfiltered -= filtered;
                }
            }
        } catch (IOException e) {
            exception = e;
            throw e;
        }
    }

    public int available() throws IOException {
        if (in == null) {
            throw new XZIOException("Stream closed");
        }

        if (exception != null) {
            throw exception;
        }

        return filtered;
    }

    public void close() throws IOException {
        if (in != null) {
            try {
                in.close();
            } finally {
                in = null;
            }
        }
    }
}
