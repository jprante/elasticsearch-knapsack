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

import org.xbib.io.Packet;
import org.xbib.io.StringPacket;
import org.xbib.io.archive.entry.ArchiveEntryEncoding;
import org.xbib.io.archive.entry.ArchiveEntryEncodingHelper;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.StringTokenizer;

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
     * Check if buffer contents matches ascii String.
     *
     * @param expected the expected string
     * @param buffer the buffer
     * @param offset the offset
     * @param length the length
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
     * @param ignoreTrailingNulls ignore trailing null if true
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

    public final static String[] keys = new String[]{
            "index", "type", "id", "field"
    };

    private final static String EMPTY = "null";

    /**
     * Encode archive entry name. Ensure there is no '/' File.separator at the end of the name, otherwise 'tar' will
     * recognize it as directory entry.
     *
     * @param packet the packet
     * @return teh entry name
     */
    public static String encodeArchiveEntryName(StringPacket packet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                sb.append(File.separator);
            }
            Object o = packet.meta().get(keys[i]);
            if (o == null) {
                o = EMPTY; // writing "null" here avoids File.separator at the end of the name
            }
            sb.append(encode(o.toString(), UTF8));
        }
        return sb.toString();
    }

    /**
     * Decode archive entry name
     *
     * @param packet           the packet
     * @param archiveEntryName the entry name
     */
    public static void decodeArchiveEntryName(Packet packet, String archiveEntryName) {
        String[] components = split(archiveEntryName, File.separator);
        for (int i = 0; i < components.length; i++) {
            packet.meta(keys[i], decode(components[i], UTF8));
        }
    }

    /**
     * Split "str" into tokens by delimiters and optionally remove white spaces
     * from the splitted tokens.
     */
    private static String[] split(String str, String delims) {
        StringTokenizer tokenizer = new StringTokenizer(str, delims);
        int n = tokenizer.countTokens();
        String[] list = new String[n];
        for (int i = 0; i < n; i++) {
            list[i] = tokenizer.nextToken();
        }
        return list;
    }

    /**
     * Decodes an octet according to RFC 2396. According to this spec,
     * any characters outside the range 0x20 - 0x7E must be escaped because
     * they are not printable characters, except for any characters in the
     * fragment identifier. This method will translate any escaped characters
     * back to the original.
     *
     * @param s        the URI to decode
     * @param encoding the encoding to decode into
     * @return The decoded URI
     */
    public static String decode(String s, Charset encoding) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean fragment = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '+':
                    sb.append(' ');
                    break;
                case '#':
                    sb.append(ch);
                    fragment = true;
                    break;
                case '%':
                    if (!fragment) {
                        // fast hex decode
                        sb.append((char) ((Character.digit(s.charAt(++i), 16) << 4)
                                | Character.digit(s.charAt(++i), 16)));
                    } else {
                        sb.append(ch);
                    }
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return new String(sb.toString().getBytes(LATIN1), encoding);
    }

    /**
     * <p>Escape a string into URI syntax</p>
     * <p>This function applies the URI escaping rules defined in
     * section 2 of [RFC 2396], as amended by [RFC 2732], to the string
     * supplied as the first argument, which typically represents all or part
     * of a URI, URI reference or IRI. The effect of the function is to
     * replace any special character in the string by an escape sequence of
     * the form %xx%yy..., where xxyy... is the hexadecimal representation of
     * the octets used to represent the character in US-ASCII for characters
     * in the ASCII repertoire, and a different character encoding for
     * non-ASCII characters.</p>
     * <p>If the second argument is true, all characters are escaped
     * other than lower case letters a-z, upper case letters A-Z, digits 0-9,
     * and the characters referred to in [RFC 2396] as "marks": specifically,
     * "-" | "_" | "." | "!" | "~" | "" | "'" | "(" | ")". The "%" character
     * itself is escaped only if it is not followed by two hexadecimal digits
     * (that is, 0-9, a-f, and A-F).</p>
     * <p>[RFC 2396] does not define whether escaped URIs should use
     * lower case or upper case for hexadecimal digits. To ensure that escaped
     * URIs can be compared using string comparison functions, this function
     * must always use the upper-case letters A-F.</p>
     * <p>The character encoding used as the basis for determining the
     * octets depends on the setting of the second argument.</p>
     *
     * @param s        the String to convert
     * @param encoding The encoding to use for unsafe characters
     * @return The converted String
     */
    public static String encode(String s, Charset encoding) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        int length = s.length();
        int start = 0;
        int i = 0;
        StringBuilder result = new StringBuilder(length);
        while (true) {
            while ((i < length) && isSafe(s.charAt(i))) {
                i++;
            }
            // Safe character can just be added
            result.append(s.substring(start, i));
            // Are we done?
            if (i >= length) {
                return result.toString();
            } else if (s.charAt(i) == ' ') {
                result.append('+'); // Replace space char with plus symbol.
                i++;
            } else {
                // Get all unsafe characters
                start = i;
                char c;
                while ((i < length) && ((c = s.charAt(i)) != ' ') && !isSafe(c)) {
                    i++;
                }
                // Convert them to %XY encoded strings
                String unsafe = s.substring(start, i);
                byte[] bytes = unsafe.getBytes(encoding);
                for (byte aByte : bytes) {
                    result.append('%');
                    result.append(hex.charAt(((int) aByte & 0xf0) >> 4));
                    result.append(hex.charAt((int) aByte & 0x0f));
                }
            }
            start = i;
        }
    }

    /**
     * Returns true if the given char is
     * either a uppercase or lowercase letter from 'a' till 'z', or a digit
     * froim '0' till '9', or one of the characters '-', '_', '.' or ''. Such
     * 'safe' character don't have to be url encoded.
     *
     * @param c the character
     * @return true or false
     */
    private static boolean isSafe(char c) {
        return (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'))
                || ((c >= '0') && (c <= '9')) || (c == '-') || (c == '_') || (c == '.') || (c == '*'));
    }

    private static final String hex = "0123456789ABCDEF";

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private static final Charset UTF8 = Charset.forName("UTF-8");

}
