package org.xbib.io.archive.tar;

import org.xbib.io.archive.ArchiveEntry;
import org.xbib.io.archive.ArchiveUtils;
import org.xbib.io.archive.entry.ArchiveEntryEncoding;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;

/**
 * This class represents an entry in a Tar archive.
 */
public class TarArchiveInputEntry implements TarConstants, ArchiveEntry {

    /**
     * Maximum length of a user's name in the tar file
     */
    private static final int MAX_NAMELEN = 31;

    /**
     * Default permissions bits for directories
     */
    private static final int DEFAULT_DIR_MODE = 040755;

    /**
     * Default permissions bits for files
     */
    private static final int DEFAULT_FILE_MODE = 0100644;

    /**
     * Convert millis to seconds
     */
    private static final int MILLIS_PER_SECOND = 1000;

    /**
     * The entry's name.
     */
    private String name;

    /**
     * The entry's permission mode.
     */
    private int mode;

    /**
     * The entry's user id.
     */
    private int userId;

    /**
     * The entry's group id.
     */
    private int groupId;

    /**
     * The entry's size.
     */
    private long size;

    /**
     * The entry's modification time.
     */
    private long modTime;

    /**
     * The entry's link flag.
     */
    private byte linkFlag;

    /**
     * The entry's link name.
     */
    private String linkName;

    /**
     * The version of the format
     */
    private String version;

    /**
     * The entry's user name.
     */
    private String userName;

    /**
     * The entry's group name.
     */
    private String groupName;

    /**
     * The entry's major device number.
     */
    private int devMajor;

    /**
     * The entry's minor device number.
     */
    private int devMinor;

    /**
     * If an extension sparse header follows.
     */
    private boolean isExtended;

    /**
     * The entry's real size in case of a sparse file.
     */
    private long realSize;

    private boolean isDir;

    /**
     * Construct an empty entry and prepares the header values.
     */
    public TarArchiveInputEntry() {
        this.version = VERSION_POSIX;
        this.name = "";
        this.linkName = "";
        this.linkFlag = LF_GNUTYPE_LONGNAME;
        String user = System.getProperty("user.name", "");
        if (user.length() > MAX_NAMELEN) {
            user = user.substring(0, MAX_NAMELEN);
        }
        this.userName = user;
        this.groupName = "";
        this.userId = 0;
        this.groupId = 0;
        this.mode = DEFAULT_FILE_MODE;
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * @param name the entry name
     */
    public TarArchiveInputEntry(String name) {
        this(name, false);
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * @param name                   the entry name
     * @param preserveLeadingSlashes whether to allow leading slashes
     *                               in the name.
     */
    public TarArchiveInputEntry(String name, boolean preserveLeadingSlashes) {
        this();
        name = ArchiveUtils.normalizeFileName(name, preserveLeadingSlashes);
        this.name = name;
        boolean isDir = name.endsWith("/");
        this.mode = isDir ? DEFAULT_DIR_MODE : DEFAULT_FILE_MODE;
        this.linkFlag = isDir ? LF_DIR : LF_NORMAL;
        this.devMajor = 0;
        this.devMinor = 0;
        this.userId = 0;
        this.groupId = 0;
        this.size = 0;
        this.modTime = (new Date()).getTime() / MILLIS_PER_SECOND;
        this.linkName = "";
        this.userName = "";
        this.groupName = "";
    }

    /**
     * Construct an entry with a name and a link flag.
     *
     * @param name     the entry name
     * @param linkFlag the entry link flag.
     */
    public TarArchiveInputEntry(String name, byte linkFlag) {
        this(name);
        this.linkFlag = linkFlag;
        if (linkFlag == LF_GNUTYPE_LONGNAME) {
            version = VERSION_GNU_SPACE;
        }
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @param encoding  encoding to use for file names
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     */
    public TarArchiveInputEntry(byte[] headerBuf, ArchiveEntryEncoding encoding) throws IOException {
        this();
        parseTarHeader(headerBuf, encoding);
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    public boolean equals(TarArchiveInputEntry it) {
        return getName().equals(it.getName());
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    @Override
    public boolean equals(Object it) {
        return !(it == null || getClass() != it.getClass()) && equals((TarArchiveInputEntry) it);
    }

    /**
     * Hashcodes are based on entry names.
     *
     * @return the entry hashcode
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Get this entry's name.
     *
     * @return This entry's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Set this entry's name.
     *
     * @param name This entry's new name.
     */
    public TarArchiveInputEntry setName(String name) {
        this.name = ArchiveUtils.normalizeFileName(name, false);
        this.isDir = name.endsWith("/");
        this.mode = isDir ? DEFAULT_DIR_MODE : DEFAULT_FILE_MODE;
        this.linkFlag = isDir ? LF_DIR : LF_NORMAL;
        return this;
    }

    /**
     * Set this entry's modification time
     *
     * @param date This entry's new modification time
     */
    public TarArchiveInputEntry setLastModified(Date date) {
        modTime = date.getTime() / MILLIS_PER_SECOND;
        return this;
    }

    public Date getLastModified() {
        return new Date(modTime * MILLIS_PER_SECOND);
    }

    @Override
    public boolean isDirectory() {
        return isDir;
    }

    /**
     * Set this entry's file size.
     *
     * @param size This entry's new file size.
     * @throws IllegalArgumentException if the size is &lt; 0.
     */
    public TarArchiveInputEntry setEntrySize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("size is out of range: " + size);
        }
        this.size = size;
        return this;
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getEntrySize() {
        return size;
    }

    /**
     * Set the mode for this entry
     *
     * @param mode the mode for this entry
     */
    public void setMode(int mode) {
        this.mode = mode;
    }

    /**
     * Get this entry's link name.
     *
     * @return This entry's link name.
     */
    public String getLinkName() {
        return linkName;
    }

    /**
     * Set this entry's link name.
     *
     * @param link the link name to use.
     */
    public void setLinkName(String link) {
        this.linkName = link;
    }

    /**
     * Get this entry's user id.
     *
     * @return This entry's user id.
     */
    public int getUserId() {
        return userId;
    }

    /**
     * Set this entry's user id.
     *
     * @param userId This entry's new user id.
     */
    public void setUserId(int userId) {
        this.userId = userId;
    }

    /**
     * Get this entry's group id.
     *
     * @return This entry's group id.
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * Set this entry's group id.
     *
     * @param groupId This entry's new group id.
     */
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    /**
     * Get this entry's user name.
     *
     * @return This entry's user name.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Set this entry's user name.
     *
     * @param userName This entry's new user name.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Get this entry's group name.
     *
     * @return This entry's group name.
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Set this entry's group name.
     *
     * @param groupName This entry's new group name.
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     * Get this entry's mode.
     *
     * @return This entry's mode.
     */
    public int getMode() {
        return mode;
    }


    /**
     * Get this entry's major device number.
     *
     * @return This entry's major device number.
     */
    public int getDevMajor() {
        return devMajor;
    }

    /**
     * Set this entry's major device number.
     *
     * @param devNo This entry's major device number.
     * @throws IllegalArgumentException if the devNo is &lt; 0.
     */
    public void setDevMajor(int devNo) {
        if (devNo < 0) {
            throw new IllegalArgumentException("Major device number is out of "
                    + "range: " + devNo);
        }
        this.devMajor = devNo;
    }

    /**
     * Get this entry's minor device number.
     *
     * @return This entry's minor device number.
     */
    public int getDevMinor() {
        return devMinor;
    }

    /**
     * Set this entry's minor device number.
     *
     * @param devNo This entry's minor device number.
     * @throws IllegalArgumentException if the devNo is &lt; 0.
     */
    public void setDevMinor(int devNo) {
        if (devNo < 0) {
            throw new IllegalArgumentException("Minor device number is out of " + "range: " + devNo);
        }
        this.devMinor = devNo;
    }

    /**
     * Indicates in case of a sparse file if an extension sparse header
     * follows.
     *
     * @return true if an extension sparse header follows.
     */
    public boolean isExtended() {
        return isExtended;
    }

    /**
     * Get this entry's real file size in case of a sparse file.
     *
     * @return This entry's real file size.
     */
    public long getRealSize() {
        return realSize;
    }

    /**
     * Indicate if this entry is a GNU sparse block
     *
     * @return true if this is a sparse extension provided by GNU tar
     */
    public boolean isGNUSparse() {
        return linkFlag == LF_GNUTYPE_SPARSE;
    }

    /**
     * Indicate if this entry is a GNU long name block
     *
     * @return true if this is a long name extension provided by GNU tar
     */
    public boolean isGNULongNameEntry() {
        return linkFlag == LF_GNUTYPE_LONGNAME && GNU_LONGLINK.equals(name);
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     */
    public boolean isPaxHeader() {
        return linkFlag == LF_PAX_EXTENDED_HEADER_LC || linkFlag == LF_PAX_EXTENDED_HEADER_UC;
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     */
    public boolean isGlobalPaxHeader() {
        return linkFlag == LF_PAX_GLOBAL_EXTENDED_HEADER;
    }

    /**
     * Check if this is a symbolic link entry.
     */
    public boolean isSymbolicLink() {
        return linkFlag == LF_SYMLINK;
    }

    /**
     * Check if this is a link entry.
     */
    public boolean isLink() {
        return linkFlag == LF_LINK;
    }

    /**
     * Check if this is a character device entry.
     */
    public boolean isCharacterDevice() {
        return linkFlag == LF_CHR;
    }

    /**
     * Check if this is a block device entry.
     */
    public boolean isBlockDevice() {
        return linkFlag == LF_BLK;
    }

    /**
     * Check if this is a FIFO (pipe) entry.
     */
    public boolean isFIFO() {
        return linkFlag == LF_FIFO;
    }

    /**
     * Parse an entry's header information from a header buffer.
     *
     * @param header   The tar entry header buffer to get information from.
     * @param encoding encoding to use for file names
     * @throws IllegalArgumentException if any of the numeric fields
     *                                  have an invalid format
     */
    public void parseTarHeader(byte[] header, ArchiveEntryEncoding encoding)
            throws IOException {
        parseTarHeader(header, encoding, false);
    }

    private void parseTarHeader(byte[] header, ArchiveEntryEncoding encoding, final boolean oldStyle)
            throws IOException {
        int offset = 0;
        int type = evaluateType(header);
        name = parseFileName(header);
        offset += NAMELEN;
        mode = (int) parseOctalOrBinary(header, offset, MODELEN);
        offset += MODELEN;
        userId = (int) parseOctalOrBinary(header, offset, UIDLEN);
        offset += UIDLEN;
        groupId = (int) parseOctalOrBinary(header, offset, GIDLEN);
        offset += GIDLEN;
        if (type == GNU_FORMAT) {
            size = getSize(header, offset, SIZELEN);
        } else {
            size = parseOctalOrBinary(header, offset, SIZELEN);
        }
        offset += SIZELEN;
        modTime = parseOctalOrBinary(header, offset, MODTIMELEN);
        offset += MODTIMELEN;
        offset += CHKSUMLEN;
        linkFlag = header[offset++];
        linkName = oldStyle ? parseName(header, offset, NAMELEN) : parseName(header, offset, NAMELEN, encoding);
        offset += NAMELEN;
        switch (type) {
            case UNIX_FORMAT: {
                offset += ATIMELEN_GNU;
                offset += CTIMELEN_GNU;
                offset += OFFSETLEN_GNU;
                offset += LONGNAMESLEN_GNU;
                offset += PAD2LEN_GNU;
                offset += SPARSELEN_GNU;
                isExtended = parseBoolean(header, offset);
                offset += ISEXTENDEDLEN_GNU;
                realSize = parseOctal(header, offset, REALSIZELEN_GNU);
                offset += REALSIZELEN_GNU;
                break;
            }
            case POSIX_FORMAT: {
                parseName(header, offset, MAGICLEN); // magic
                offset += MAGICLEN;
                version = parseName(header, offset, VERSIONLEN);
                offset += VERSIONLEN;
                userName = oldStyle ? parseName(header, offset, UNAMELEN) : parseName(header, offset, UNAMELEN, encoding);
                offset += UNAMELEN;
                groupName = oldStyle ? parseName(header, offset, GNAMELEN) : parseName(header, offset, GNAMELEN, encoding);
                offset += GNAMELEN;
                devMajor = (int) parseOctalOrBinary(header, offset, DEVLEN);
                offset += DEVLEN;
                devMinor = (int) parseOctalOrBinary(header, offset, DEVLEN);
                offset += DEVLEN;
            }
        }
    }

    /**
     * Evaluate an entry's header format from a header buffer.
     *
     * @param header The tar entry header buffer to evaluate the format for.
     * @return format type
     */
    private int evaluateType(byte[] header) {
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_UNIX, header, MAGIC_OFFSET, MAGICLEN)) {
            return UNIX_FORMAT;
        }
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_POSIX, header, MAGIC_OFFSET, MAGICLEN)) {
            return POSIX_FORMAT;
        }
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_GNU, header, MAGIC_OFFSET, MAGICLEN)) {
            return GNU_FORMAT;
        }
        return 0;
    }

    /**
     * Parse an octal string from a buffer.
     * <p>Leading spaces are ignored.
     * The buffer must contain a trailing space or NUL,
     * and may contain an additional trailing space or NUL.</p>
     * <p>The input buffer is allowed to contain all NULs,
     * in which case the method returns 0L
     * (this allows for missing fields).</p>
     * <p>To work-around some tar implementations that insert a
     * leading NUL this method returns 0 if it detects a leading NUL.</p>
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse - must be at least 2 bytes.
     * @return The long value of the octal string.
     * @throws IllegalArgumentException if the trailing space/NUL is missing or if a invalid byte is detected.
     */
    private long parseOctal(final byte[] buffer, final int offset, final int length) {
        long result = 0;
        int end = offset + length;
        int start = offset;
        if (length < 2) {
            throw new IllegalArgumentException("Length " + length + " must be at least 2");
        }
        if (buffer[start] == 0) {
            return 0L;
        }
        while (start < end) {
            if (buffer[start] == ' ') {
                start++;
            } else {
                break;
            }
        }
        byte trailer;
        trailer = buffer[end - 1];
        if (trailer == 0 || trailer == ' ') {
            end--;
        } else {
            throw new IllegalArgumentException(exceptionMessage(buffer, offset, length, end - 1, trailer));
        }
        trailer = buffer[end - 1];
        if (trailer == 0 || trailer == ' ') {
            end--;
        }
        for (; start < end; start++) {
            final byte currentByte = buffer[start];
            if (currentByte < '0' || currentByte > '7') {
                throw new IllegalArgumentException(
                        exceptionMessage(buffer, offset, length, start, currentByte));
            }
            result = (result << 3) + (currentByte - '0'); // convert from ASCII
        }

        return result;
    }

    /**
     * Compute the value contained in a byte buffer.  If the most
     * significant bit of the first byte in the buffer is set, this
     * bit is ignored and the rest of the buffer is interpreted as a
     * binary number.  Otherwise, the buffer is interpreted as an
     * octal number as per the parseOctal function above.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse.
     * @return The long value of the octal or binary string.
     * @throws IllegalArgumentException if the trailing space/NUL is
     *                                  missing or an invalid byte is detected in an octal number, or
     *                                  if a binary number would exceed the size of a signed long
     *                                  64-bit integer.
     */
    private long parseOctalOrBinary(final byte[] buffer, final int offset, final int length) {
        if ((buffer[offset] & 0x80) == 0) {
            return parseOctal(buffer, offset, length);
        }
        final boolean negative = buffer[offset] == (byte) 0xff;
        if (length < 9) {
            return parseBinaryLong(buffer, offset, length, negative);
        }
        return parseBinaryBigInteger(buffer, offset, length, negative);
    }

    private long parseBinaryLong(final byte[] buffer, final int offset, final int length, final boolean negative) {
        if (length >= 9) {
            throw new IllegalArgumentException("At offset " + offset + ", "
                    + length + " byte binary number"
                    + " exceeds maximum signed long"
                    + " value");
        }
        long val = 0;
        for (int i = 1; i < length; i++) {
            val = (val << 8) + (buffer[offset + i] & 0xff);
        }
        if (negative) {
            // 2's complement
            val--;
            val ^= ((long) Math.pow(2, (length - 1) * 8) - 1);
        }
        return negative ? -val : val;
    }

    private long parseBinaryBigInteger(final byte[] buffer, final int offset, final int length, final boolean negative) {
        byte[] remainder = new byte[length - 1];
        System.arraycopy(buffer, offset + 1, remainder, 0, length - 1);
        BigInteger val = new BigInteger(remainder);
        if (negative) {
            // 2's complement
            val = val.add(BigInteger.valueOf(-1)).not();
        }
        if (val.bitLength() > 63) {
            throw new IllegalArgumentException("At offset " + offset + ", "
                    + length + " byte binary number"
                    + " exceeds maximum signed long"
                    + " value");
        }
        return negative ? -val.longValue() : val.longValue();
    }

    /**
     * Parse a boolean byte from a buffer.
     * Leading spaces and NUL are ignored.
     * The buffer may contain trailing spaces or NULs.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @return The boolean value of the bytes.
     * @throws IllegalArgumentException if an invalid byte is detected.
     */
    private boolean parseBoolean(final byte[] buffer, final int offset) {
        return buffer[offset] == 1;
    }

    private String exceptionMessage(byte[] buffer, final int offset, final int length, int current, final byte currentByte) {
        String string = new String(buffer, offset, length); // TODO default charset?
        string = string.replaceAll("\0", "{NUL}"); // Replace NULs to allow string to be printed
        return "Invalid byte " + currentByte + " at offset " + (current - offset) + " in '" + string + "' len=" + length;
    }

    /**
     * Parse an entry name from a buffer.
     * Parsing stops when a NUL is found
     * or the buffer length is reached.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse.
     * @return The entry name.
     */
    private String parseName(byte[] buffer, final int offset, final int length) {
        try {
            return parseName(buffer, offset, length, ArchiveUtils.DEFAULT_ENCODING);
        } catch (IOException ex) {
            try {
                return parseName(buffer, offset, length, ArchiveUtils.FALLBACK_ENCODING);
            } catch (IOException ex2) {
                // impossible
                throw new RuntimeException(ex2);
            }
        }
    }

    /**
     * Parse an entry name from a buffer.
     * Parsing stops when a NUL is found
     * or the buffer length is reached.
     *
     * @param buffer   The buffer from which to parse.
     * @param offset   The offset into the buffer from which to parse.
     * @param length   The maximum number of bytes to parse.
     * @param encoding name of the encoding to use for file names
     * @return The entry name.
     */
    private String parseName(byte[] buffer, final int offset, final int length, final ArchiveEntryEncoding encoding) throws IOException {
        int len = length;
        for (; len > 0; len--) {
            if (buffer[offset + len - 1] != 0) {
                break;
            }
        }
        if (len > 0) {
            byte[] b = new byte[len];
            System.arraycopy(buffer, offset, b, 0, len);
            return encoding.decode(b);
        }
        return "";
    }

    private long getSize(byte[] header, int offset, int length) {
        long test = parseOctal(header, offset, length);
        if (test <= 0 && header[offset] == (byte) 128) {
            byte[] last = new byte[length];
            System.arraycopy(header, offset, last, 0, length);
            last[0] = (byte) 0;
            long rSize = new BigInteger(last).longValue();
            last = null;
            return rSize;
        }
        return test;
    }

    private String parseFileName(byte[] header) {
        StringBuilder result = new StringBuilder(256);
        // If header[345] is not equal to zero, then it is the "prefix"
        // that 'ustar' defines. It must be prepended to the "normal"
        // name field. We are responsible for the separating '/'.
        if (header[345] != 0) {
            for (int i = 345; i < 500 && header[i] != 0; ++i) {
                result.append((char) header[i]);
            }
            result.append("/");
        }
        for (int i = 0; i < 100 && header[i] != 0; ++i) {
            result.append((char) header[i]);
        }
        return result.toString();
    }
}

