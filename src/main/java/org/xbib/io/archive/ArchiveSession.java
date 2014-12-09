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

import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.xbib.io.BytesProgressWatcher;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.compress.CompressCodecService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A basic archive session
 */
public abstract class ArchiveSession<I extends ArchiveInputStream, O extends ArchiveOutputStream> implements Session<ArchivePacket> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(ArchiveSession.class.getSimpleName());

    private final static CompressCodecService codecService = CompressCodecService.getInstance();

    private final static ArchiveService archiveService = ArchiveService.getInstance();

    private boolean isOpen;

    private EnumSet<Mode> mode;

    private File file;

    private Path path;

    private I in;

    private O out;

    private BytesProgressWatcher watcher;

    private long packetCounter;

    private AtomicLong archiveCounter = new AtomicLong();

    private boolean uriEncoded;

    protected ArchiveSession(BytesProgressWatcher watcher) {
        this.watcher = watcher;
        this.packetCounter = 0L;
    }

    public BytesProgressWatcher getWatcher() {
        if (watcher == null) {
            watcher = new BytesProgressWatcher(0L);
        }
        return watcher;
    }

    public abstract String getName();

    @Override
    public synchronized void open(EnumSet<Mode> mode, Path path, File file) throws IOException {
        if (isOpen) {
            return;
        }
        this.mode = mode;
        this.file = file;
        this.path = path;
        if (mode.contains(Mode.URI_ENCODED)) {
            uriEncoded = true;
        }
        if (mode.contains(Mode.READ)) {
            this.in = createArchiveInputStream();
            this.isOpen = this.in != null;
            if (!isOpen) {
                throw new FileNotFoundException("can't open for input, check existence or access rights: " + file.getAbsolutePath());
            }
        } else if (mode.contains(Mode.WRITE)) {
            this.out = createArchiveOutputStream(false);
            this.isOpen = this.out != null;
            if (!isOpen) {
                throw new FileNotFoundException("can't open for output, check existence or access rights: " + file.getAbsolutePath());
            }
        } else if (mode.contains(Mode.OVERWRITE)) {
            this.out = createArchiveOutputStream(true);
            this.isOpen = this.out != null;
            if (!isOpen) {
                throw new FileNotFoundException("can't open for output, check existence or access rights: " + file.getAbsolutePath());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private I createArchiveInputStream() throws IOException {
        I archiveIn;
        FileInputStream in;
        if (file.isFile() && file.canRead()) {
            in = new FileInputStream(file);
        } else {
            throw new FileNotFoundException("can't open for input, check existence or access rights: " + path);
        }
        String pathStr = path.toString();
        Set<String> streamCodecs = CompressCodecService.getCodecs();
        for (String codec : streamCodecs) {
            if (pathStr.endsWith("." + codec)) {
                archiveIn = (I) archiveService.getCodec(getName()).createArchiveInputStream(codecService.getCodec(codec).decode(in));
                archiveIn.setWatcher(watcher);
                return archiveIn;
            }
        }
        archiveIn = (I) archiveService.getCodec(getName()).createArchiveInputStream(in);
        archiveIn.setWatcher(watcher);
        return archiveIn;
    }

    @SuppressWarnings("unchecked")
    private O createArchiveOutputStream(boolean overwrite) throws IOException {
        O archiveOut;
        FileOutputStream out;
        if (!file.exists() || file.length() == 0 || overwrite) {
            out = new FileOutputStream(file);
        } else {
            throw new FileNotFoundException("can't open for output, check existence or access rights: " + file.getAbsolutePath());
        }
        String pathStr = path.toString();
        Set<String> streamCodecs = CompressCodecService.getCodecs();
        for (String codec : streamCodecs) {
            if (pathStr.endsWith("." + codec)) {
                archiveOut = (O) archiveService.getCodec(getName()).createArchiveOutputStream(codecService.getCodec(codec).encode(out));
                archiveOut.setWatcher(watcher);
                return archiveOut;
            }
        }
        archiveOut = (O) archiveService.getCodec(getName()).createArchiveOutputStream(out);
        archiveOut.setWatcher(watcher);
        return archiveOut;
    }

    @Override
    public ArchivePacket newPacket() {
        return new ArchivePacket();
    }

    @Override
    public synchronized ArchivePacket read() throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (in == null) {
            throw new IOException("no input stream found");
        }
        ArchiveEntry entry = in.getNextEntry();
        if (entry == null) {
            return null;
        }
        ArchivePacket packet = newPacket();
        String name = entry.getName();
        packet.meta("name", name);
        decodeArchiveEntryName(packet, name);
        int size = (int) entry.getEntrySize();
        if (size >= 0) {
            byte[] b = new byte[size]; // naive but fast, heap may explode
            int num = in.read(b, 0, size); // fill byte array from stream
            packet.payload(new String(b));
        } else {
            // slow copy, unknown size (zip deflate method)
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Streams.copy(in, b);
            packet.payload(new String(b.toByteArray()));
        }
        packetCounter++;
        return packet;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void write(ArchivePacket packet) throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (out == null) {
            throw new IOException("no output stream found");
        }
        if (packet == null || packet.payload() == null) {
            throw new IOException("no payload to write for entry");
        }
        byte[] buf = packet.payload().toString().getBytes();
        String name = encodeArchiveEntryName(packet);
        ArchiveEntry entry = out.newArchiveEntry();
        entry.setName(name);
        entry.setLastModified(new Date());
        entry.setEntrySize(buf.length);
        out.putArchiveEntry(entry);
        out.write(buf);
        out.closeArchiveEntry();
        packetCounter++;
        if (watcher.getBytesToTransfer() != 0 && watcher.getBytesTransferred() > watcher.getBytesToTransfer()) {
            logger.debug("bytes watcher: transferred = {}, rate {}",
                    watcher.getBytesTransferred(), watcher.getRecentByteRatePerSecond());
            switchToNextArchive();
            watcher.resetWatcher();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isOpen) {
            return;
        }
        if (out != null) {
            out.close();
        }
        if (in != null) {
            in.close();
        }
        this.isOpen = false;
    }

    @Override
    public long getPacketCounter() {
        return packetCounter;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Switches to next archive stream if a certain byte limit was set
     *
     * @throws IOException
     */
    private void switchToNextArchive() throws IOException {
        close();
        String filename = file.getName();
        String prefix = Long.toString(archiveCounter.get()) + ".";
        if (filename.startsWith(prefix)) {
            filename = filename.substring(prefix.length());
        }
        filename = archiveCounter.incrementAndGet() + "." + filename;
        this.file = new File(file.getParent() + File.separator + filename);
        this.path = file.toPath();
        open(mode, path, file);
    }

    private final static String[] keys = new String[]{
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
    private String encodeArchiveEntryName(ArchivePacket packet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                sb.append(File.separator);
            }
            Object o = packet.meta().get(keys[i]);
            if (o == null) {
                o = EMPTY; // writing "null" here avoids File.separator at the end of the name
            }
            if (uriEncoded) {
                sb.append(encode(o.toString(), UTF8));
            } else {
                sb.append(o.toString());
            }
        }
        return sb.toString();
    }

    /**
     * Decode archive entyr name
     *
     * @param packet           the packet
     * @param archiveEntryName the entry name
     */
    private void decodeArchiveEntryName(Packet packet, String archiveEntryName) {
        String[] components = split(archiveEntryName, File.separator);
        for (int i = 0; i < components.length; i++) {
            if (uriEncoded) {
                components[i] = decode(components[i] != null ? components[i] : "", UTF8);
            }
            packet.meta(keys[i], components[i]);
        }
    }

    /**
     * Split "str" into tokens by delimiters and optionally remove white spaces
     * from the splitted tokens.
     */
    private String[] split(String str, String delims) {
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
    private String decode(String s, Charset encoding) {
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
    private String encode(String s, Charset encoding) {
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
    private boolean isSafe(char c) {
        return (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'))
                || ((c >= '0') && (c <= '9')) || (c == '-') || (c == '_') || (c == '.') || (c == '*'));
    }

    private static final String hex = "0123456789ABCDEF";

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private static final Charset UTF8 = Charset.forName("UTF-8");

}
