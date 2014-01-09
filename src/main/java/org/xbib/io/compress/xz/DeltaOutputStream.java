
package org.xbib.io.compress.xz;

import org.xbib.io.compress.xz.delta.DeltaEncoder;

import java.io.IOException;


class DeltaOutputStream extends FinishableOutputStream {
    private static final int TMPBUF_SIZE = 4096;

    private FinishableOutputStream out;
    private final DeltaEncoder delta;
    private final byte[] tmpbuf = new byte[TMPBUF_SIZE];

    private boolean finished = false;
    private IOException exception = null;

    static int getMemoryUsage() {
        return 1 + TMPBUF_SIZE / 1024;
    }

    DeltaOutputStream(FinishableOutputStream out, DeltaOptions options) {
        this.out = out;
        delta = new DeltaEncoder(options.getDistance());
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
            throw new XZIOException("Stream finished");
        }

        try {
            while (len > TMPBUF_SIZE) {
                delta.encode(buf, off, TMPBUF_SIZE, tmpbuf);
                out.write(tmpbuf);
                off += TMPBUF_SIZE;
                len -= TMPBUF_SIZE;
            }

            delta.encode(buf, off, len, tmpbuf);
            out.write(tmpbuf, 0, len);
        } catch (IOException e) {
            exception = e;
            throw e;
        }
    }

    public void flush() throws IOException {
        if (exception != null) {
            throw exception;
        }

        if (finished) {
            throw new XZIOException("Stream finished or closed");
        }

        try {
            out.flush();
        } catch (IOException e) {
            exception = e;
            throw e;
        }
    }

    public void finish() throws IOException {
        if (!finished) {
            if (exception != null) {
                throw exception;
            }

            try {
                out.finish();
            } catch (IOException e) {
                exception = e;
                throw e;
            }

            finished = true;
        }
    }

    public void close() throws IOException {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
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
