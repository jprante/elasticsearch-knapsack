
package org.xbib.io.compress.xz;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counts the number of bytes read from an input stream.
 */
class CountingInputStream extends FilterInputStream {
    private long size = 0;

    public CountingInputStream(InputStream in) {
        super(in);
    }

    public int read() throws IOException {
        int ret = in.read();
        if (ret != -1 && size >= 0) {
            ++size;
        }

        return ret;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int ret = in.read(b, off, len);
        if (ret > 0 && size >= 0) {
            size += ret;
        }

        return ret;
    }

    public long getSize() {
        return size;
    }
}
