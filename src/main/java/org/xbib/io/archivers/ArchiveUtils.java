
package org.xbib.io.archivers;

import java.io.UnsupportedEncodingException;

/**
 * Generic Archive utilities
 */
public class ArchiveUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ArchiveUtils() {
    }

    /**
     * Generates a string containing the name, isDirectory setting and size of an entry.
     * <p/>
     * For example:<br/>
     * <tt>-    2000 main.c</tt><br/>
     * <tt>d     100 testfiles</tt><br/>
     *
     * @return the representation of the entry
     */
    public static String toString(ArchiveEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.isDirectory() ? 'd' : '-');// c.f. "ls -l" output
        String size = Long.toString((entry.getEntrySize()));
        sb.append(' ');
        // Pad output to 7 places, leading spaces
        for (int i = 7; i > size.length(); i--) {
            sb.append(' ');
        }
        sb.append(size);
        sb.append(' ').append(entry.getName());
        return sb.toString();
    }

    /**
     * Check if buffer contents matches Ascii String.
     *
     * @param expected
     * @param buffer
     * @param offset
     * @param length
     * @return {@code true} if buffer is the same as the expected string
     */
    public static boolean matchAsciiBuffer(
            String expected, byte[] buffer, int offset, int length) {
        byte[] buffer1;
        try {
            buffer1 = expected.getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // Should not happen
        }
        return isEqual(buffer1, 0, buffer1.length, buffer, offset, length, false);
    }

    /**
     * Convert a string to Ascii bytes.
     * Used for comparing "magic" strings which need to be independent of the default Locale.
     *
     * @param inputString
     * @return the bytes
     */
    public static byte[] toAsciiBytes(String inputString) {
        try {
            return inputString.getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // Should never happen
        }
    }

    /**
     * Convert an input byte array to a String using the ASCII character set.
     *
     * @param inputBytes
     * @return the bytes, interpreted as an Ascii string
     */
    public static String toAsciiString(final byte[] inputBytes) {
        try {
            return new String(inputBytes, "ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // Should never happen
        }
    }

    /**
     * Convert an input byte array to a String using the ASCII character set.
     *
     * @param inputBytes input byte array
     * @param offset     offset within array
     * @param length     length of array
     * @return the bytes, interpreted as an Ascii string
     */
    public static String toAsciiString(final byte[] inputBytes, int offset, int length) {
        try {
            return new String(inputBytes, offset, length, "ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // Should never happen
        }
    }

    /**
     * Compare byte buffers, optionally ignoring trailing nulls
     *
     * @param buffer1
     * @param offset1
     * @param length1
     * @param buffer2
     * @param offset2
     * @param length2
     * @param ignoreTrailingNulls
     * @return {@code true} if buffer1 and buffer2 have same contents, having regard to trailing nulls
     */
    public static boolean isEqual(
            final byte[] buffer1, final int offset1, final int length1,
            final byte[] buffer2, final int offset2, final int length2,
            boolean ignoreTrailingNulls) {
        int minLen = length1 < length2 ? length1 : length2;
        for (int i = 0; i < minLen; i++) {
            if (buffer1[offset1 + i] != buffer2[offset2 + i]) {
                return false;
            }
        }
        if (length1 == length2) {
            return true;
        }
        if (ignoreTrailingNulls) {
            if (length1 > length2) {
                for (int i = length2; i < length1; i++) {
                    if (buffer1[offset1 + i] != 0) {
                        return false;
                    }
                }
            } else {
                for (int i = length1; i < length2; i++) {
                    if (buffer2[offset2 + i] != 0) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

}
