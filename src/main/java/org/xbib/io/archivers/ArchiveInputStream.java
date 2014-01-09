
package org.xbib.io.archivers;

import java.io.IOException;
import java.io.InputStream;

/**
 * Archive input streams <b>MUST</b> override the
 * {@link #read(byte[], int, int)} - or {@link #read()} -
 * method so that reading from the stream generates EOF for the end of
 * data in each entry as well as at the end of the file proper.
 * <p/>
 * The {@link #getNextEntry()} method is used to reset the input stream
 * ready for reading the data from the next entry.
 * <p/>
 * The input stream classes must also implement a method with the signature:
 * <pre>
 * public static boolean matches(byte[] signature, int length)
 * </pre>
 * which is used by the {@link ArchiveFactory} to autodetect
 * the archive type from the first few bytes of a stream.
 */
public abstract class ArchiveInputStream extends InputStream {

    private byte[] SINGLE = new byte[1];
    private static final int BYTE_MASK = 0xFF;

    /**
     * holds the number of bytes read in this stream
     */
    private long bytesRead = 0;

    /**
     * Returns the next Archive Entry in this Stream.
     *
     * @return the next entry,
     * or {@code null} if there are no more entries
     * @throws java.io.IOException if the next entry could not be read
     */
    public abstract ArchiveEntry getNextEntry() throws IOException;

    /**
     * Reads a byte of data. This method will block until enough input is
     * available.
     * <p/>
     * Simply calls the {@link #read(byte[], int, int)} method.
     * <p/>
     * MUST be overridden if the {@link #read(byte[], int, int)} method
     * is not overridden; may be overridden otherwise.
     *
     * @return the byte read, or -1 if end of input is reached
     * @throws java.io.IOException if an I/O error has occurred
     */
    @Override
    public int read() throws IOException {
        int num = read(SINGLE, 0, 1);
        return num == -1 ? -1 : SINGLE[0] & BYTE_MASK;
    }

    /**
     * Increments the counter of already read bytes.
     * Doesn't increment if the EOF has been hit (read == -1)
     *
     * @param read the number of bytes read
     */
    protected void count(int read) {
        count((long) read);
    }

    /**
     * Increments the counter of already read bytes.
     * Doesn't increment if the EOF has been hit (read == -1)
     *
     * @param read the number of bytes read
     */
    protected void count(long read) {
        if (read != -1) {
            bytesRead = bytesRead + read;
        }
    }

    /**
     * Decrements the counter of already read bytes.
     *
     * @param pushedBack the number of bytes pushed back.
     */
    protected void pushedBackBytes(long pushedBack) {
        bytesRead -= pushedBack;
    }

    /**
     * Returns the current number of bytes read from this stream.
     *
     * @return the number of read bytes
     */
    public long getBytesRead() {
        return bytesRead;
    }

}
