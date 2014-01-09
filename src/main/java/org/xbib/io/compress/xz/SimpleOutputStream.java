
package org.xbib.io.compress.xz;

import org.xbib.io.compress.xz.simple.SimpleFilter;

import java.io.IOException;

class SimpleOutputStream extends FinishableOutputStream {
    private static final int TMPBUF_SIZE = 4096;

    private FinishableOutputStream out;
    private final SimpleFilter simpleFilter;

    private final byte[] tmpbuf = new byte[TMPBUF_SIZE];
    private int pos = 0;
    private int unfiltered = 0;

    private IOException exception = null;
    private boolean finished = false;

    static int getMemoryUsage() {
        return 1 + TMPBUF_SIZE / 1024;
    }

    SimpleOutputStream(FinishableOutputStream out,
                       SimpleFilter simpleFilter) {
        if (out == null) {
            throw new NullPointerException();
        }

        this.out = out;
        this.simpleFilter = simpleFilter;
    }

    public void write(int b) throws IOException {
        byte[] buf = new byte[1];
        buf[0] = (byte) b;
        write(buf, 0, 1);
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
            throw new IndexOutOfBoundsException();
        }

        if (exception != null) {
            throw exception;
        }

        if (finished) {
            throw new XZIOException("Stream finished or closed");
        }

        while (len > 0) {
            // Copy more unfiltered data into tmpbuf.
            int copySize = Math.min(len, TMPBUF_SIZE - (pos + unfiltered));
            System.arraycopy(buf, off, tmpbuf, pos + unfiltered, copySize);
            off += copySize;
            len -= copySize;
            unfiltered += copySize;

            // Filter the data in tmpbuf.
            int filtered = simpleFilter.code(tmpbuf, pos, unfiltered);
            assert filtered <= unfiltered;
            unfiltered -= filtered;

            // Write out the filtered data.
            try {
                out.write(tmpbuf, pos, filtered);
            } catch (IOException e) {
                exception = e;
                throw e;
            }

            pos += filtered;

            // If end of tmpbuf was reached, move the pending unfiltered
            // data to the beginning of the buffer so that more data can
            // be copied into tmpbuf on the next loop iteration.
            if (pos + unfiltered == TMPBUF_SIZE) {
                System.arraycopy(tmpbuf, pos, tmpbuf, 0, unfiltered);
                pos = 0;
            }
        }
    }

    private void writePending() throws IOException {
        assert !finished;

        if (exception != null) {
            throw exception;
        }

        try {
            out.write(tmpbuf, pos, unfiltered);
        } catch (IOException e) {
            exception = e;
            throw e;
        }

        finished = true;
    }

    public void flush() throws IOException {
        throw new UnsupportedOptionsException("Flushing is not supported");
    }

    public void finish() throws IOException {
        if (!finished) {
            // If it fails, don't call out.finish().
            writePending();

            try {
                out.finish();
            } catch (IOException e) {
                exception = e;
                throw e;
            }
        }
    }

    public void close() throws IOException {
        if (out != null) {
            if (!finished) {
                // out.close() must be called even if writePending() fails.
                // writePending() saves the possible exception so we can
                // ignore exceptions here.
                try {
                    writePending();
                } catch (IOException e) {
                }
            }

            try {
                out.close();
            } catch (IOException e) {
                // If there is an earlier exception, the exception
                // from out.close() is lost.
                if (exception == null) {
                    exception = e;
                }
            }

            out = null;
        }

        if (exception != null) {
            throw exception;
        }
    }
}
