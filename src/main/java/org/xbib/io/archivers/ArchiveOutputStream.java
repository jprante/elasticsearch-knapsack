
package org.xbib.io.archivers;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Archive output stream implementations are expected to override the
 * {@link #write(byte[], int, int)} method to improve performance.
 * They should also override {@link #close()} to ensure that any necessary
 * trailers are added.
 * <p/>
 * <p>
 * The normal sequence of calls for working with ArchiveOutputStreams is:
 * + create ArchiveOutputStream object
 * + write SFX header (optional, Zip only)
 * + repeat as needed:
 * - putArchiveEntry() (writes entry header)
 * - write() (writes entry data)
 * - closeArchiveEntry() (closes entry)
 * + finish() (ends the addition of entries)
 * + write additional data if format supports it (optional)
 * + close()
 * </p>
 */
public abstract class ArchiveOutputStream extends OutputStream {

    /**
     * Temporary buffer used for the {@link #write(int)} method
     */
    private final byte[] oneByte = new byte[1];


    private static final int BYTE_MASK = 0xFF;

    /**
     * holds the number of bytes written to this stream
     */
    private long bytesWritten = 0;
    // Methods specific to ArchiveOutputStream

    /**
     * Writes the headers for an archive entry to the output stream.
     * The caller must then write the content to the stream and call
     * {@link #closeArchiveEntry()} to complete the process.
     *
     * @param entry describes the entry
     * @throws java.io.IOException
     */
    public abstract void putArchiveEntry(ArchiveEntry entry) throws IOException;

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
     * <p/>
     * The finish() method throws an Exception if the user forgets to close the entry
     * .
     *
     * @throws java.io.IOException
     */
    public abstract void finish() throws IOException;

    /**
     * Writes a byte to the current archive entry.
     * <p/>
     * This method simply calls write( byte[], 0, 1 ).
     * <p/>
     * MUST be overridden if the {@link #write(byte[], int, int)} method
     * is not overridden; may be overridden otherwise.
     *
     * @param b The byte to be written.
     * @throws java.io.IOException on error
     */
    @Override
    public void write(int b) throws IOException {
        oneByte[0] = (byte) (b & BYTE_MASK);
        write(oneByte, 0, 1);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if the EOF has been hit (read == -1)
     *
     * @param written the number of bytes written
     */
    protected void count(int written) {
        count((long) written);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if the EOF has been hit (read == -1)
     *
     * @param written the number of bytes written
     */
    protected void count(long written) {
        if (written != -1) {
            bytesWritten = bytesWritten + written;
        }
    }

    /**
     * Returns the current number of bytes written to this stream.
     *
     * @return the number of written bytes
     */
    public long getBytesWritten() {
        return bytesWritten;
    }

}
