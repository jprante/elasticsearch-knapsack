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

import org.xbib.io.archive.entry.ArchiveEntryEncoding;
import org.xbib.io.archive.entry.ArchiveEntryEncodingHelper;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Archive utilities
 */
public class ArchiveUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ArchiveUtils() {
    }

    /**
     * Strips Windows' drive letter as well as any leading slashes,
     * turns path separators into forward slahes.
     */
    public static String normalizeFileName(String fileName, boolean preserveLeadingSlashes) {
        String osname = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (osname.startsWith("windows")) {
            if (fileName.length() > 2) {
                char ch1 = fileName.charAt(0);
                char ch2 = fileName.charAt(1);
                if (ch2 == ':' && ((ch1 >= 'a' && ch1 <= 'z') || (ch1 >= 'A' && ch1 <= 'Z'))) {
                    fileName = fileName.substring(2);
                }
            }
        } else if (osname.contains("netware")) {
            int colon = fileName.indexOf(':');
            if (colon != -1) {
                fileName = fileName.substring(colon + 1);
            }
        }
        fileName = fileName.replace(File.separatorChar, '/');
        // No absolute pathnames. Windows paths can start with "\\NetworkDrive\",  so we loop on starting /'s.
        while (!preserveLeadingSlashes && fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        return fileName;
    }

    public static final ArchiveEntryEncoding DEFAULT_ENCODING = ArchiveEntryEncodingHelper.getEncoding(null);

    public static final ArchiveEntryEncoding FALLBACK_ENCODING = new ArchiveEntryEncoding() {
        public boolean canEncode(String name) {
            return true;
        }

        public ByteBuffer encode(String name) {
            final int length = name.length();
            byte[] buf = new byte[length];
            for (int i = 0; i < length; ++i) {
                buf[i] = (byte) name.charAt(i);
            }
            return ByteBuffer.wrap(buf);
        }

        public String decode(byte[] buffer) {
            final int length = buffer.length;
            StringBuilder result = new StringBuilder(length);
            for (byte b : buffer) {
                if (b == 0) {
                    break;
                }
                result.append((char) (b & 0xFF));
            }
            return result.toString();
        }
    };

    /**
     * Copy a name into a buffer.
     * Copies characters from the name into the buffer
     * starting at the specified offset.
     * If the buffer is longer than the name, the buffer
     * is filled with trailing NULs.
     * If the name is longer than the buffer,
     * the output is truncated.
     *
     * @param name   The header name from which to copy the characters.
     * @param buf    The buffer where the name is to be stored.
     * @param offset The starting offset into the buffer
     * @param length The maximum number of header bytes to copy.
     * @return The updated offset, i.e. offset + length
     */
    public static int formatNameBytes(String name, byte[] buf, final int offset, final int length) {
        try {
            return formatNameBytes(name, buf, offset, length, DEFAULT_ENCODING);
        } catch (IOException ex) {
            try {
                return formatNameBytes(name, buf, offset, length, ArchiveUtils.FALLBACK_ENCODING);
            } catch (IOException ex2) {
                // impossible
                throw new RuntimeException(ex2);
            }
        }
    }

    /**
     * Copy a name into a buffer.
     * Copies characters from the name into the buffer
     * starting at the specified offset.
     * If the buffer is longer than the name, the buffer
     * is filled with trailing NULs.
     * If the name is longer than the buffer,
     * the output is truncated.
     *
     * @param name     The header name from which to copy the characters.
     * @param buf      The buffer where the name is to be stored.
     * @param offset   The starting offset into the buffer
     * @param length   The maximum number of header bytes to copy.
     * @param encoding name of the encoding to use for file names
     * @return The updated offset, i.e. offset + length
     */
    public static int formatNameBytes(String name, byte[] buf, final int offset,
                                      final int length,
                                      final ArchiveEntryEncoding encoding)
            throws IOException {
        int len = name.length();
        ByteBuffer b = encoding.encode(name);
        while (b.limit() > length && len > 0) {
            b = encoding.encode(name.substring(0, --len));
        }
        final int limit = b.limit();
        System.arraycopy(b.array(), b.arrayOffset(), buf, offset, limit);

        // Pad any remaining output bytes with NUL
        for (int i = limit; i < length; ++i) {
            buf[offset + i] = 0;
        }

        return offset + length;
    }


    /**
     * Generates a string containing the name, isDirectory setting and size of an entry.
     * <p>
     * For example:
     * <tt>-    2000 main.c</tt>
     * <tt>d     100 testfiles</tt>
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
    public static boolean matchAsciiBuffer(String expected, byte[] buffer, int offset, int length) {
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
     * @param inputString input string
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
     * @param inputBytes input byet array
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
     * @param buffer1             first buffer
     * @param offset1             offset of first buffer
     * @param length1             length of first buffer
     * @param buffer2             second buffer
     * @param offset2             offset of second buffer
     * @param length2             length of second buffer
     * @param ignoreTrailingNulls
     * @return {@code true} if buffer1 and buffer2 have same contents, having regard to trailing nulls
     */
    public static boolean isEqual(final byte[] buffer1, final int offset1, final int length1,
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
