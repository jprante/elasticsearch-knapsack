package org.xbib.io.archive.tar;

import org.xbib.io.archive.ArchiveEntry;
import org.xbib.io.archive.ArchiveUtils;
import org.xbib.io.archive.entry.ArchiveEntryEncoding;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;

/**
 * This class represents an entry in a Tar archive for output
 */
public class TarArchiveOutputEntry implements TarConstants, ArchiveEntry {

    private static final int BYTE_MASK = 255;

    /**
     * Maximum length of a user's name in the tar file
     */
    public static final int MAX_NAMELEN = 31;

    /**
     * Default permissions bits for directories
     */
    public static final int DEFAULT_DIR_MODE = 040755;

    /**
     * Default permissions bits for files
     */
    public static final int DEFAULT_FILE_MODE = 0100644;

    /**
     * Convert millis to seconds
     */
    public static final int MILLIS_PER_SECOND = 1000;

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
     * The entry's magic tag.
     */
    private String magic;
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

    /**
     * The entry's file reference
     */
    private File file;

    /**
     * Construct an empty entry and prepares the header values.
     */
    public TarArchiveOutputEntry() {
        this.magic = MAGIC_POSIX;
        this.version = VERSION_POSIX;
        this.name = "";
        this.linkName = "";
        String user = System.getProperty("user.name", "");
        if (user.length() > MAX_NAMELEN) {
            user = user.substring(0, MAX_NAMELEN);
        }
        this.userId = 0;
        this.groupId = 0;
        this.userName = user;
        this.groupName = "";
        this.file = null;
        this.mode = DEFAULT_FILE_MODE;
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * @param name the entry name
     */
    public TarArchiveOutputEntry(String name) {
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
    public TarArchiveOutputEntry(String name, boolean preserveLeadingSlashes) {
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
    public TarArchiveOutputEntry(String name, byte linkFlag) {
        this(name);
        this.linkFlag = linkFlag;
        if (linkFlag == LF_GNUTYPE_LONGNAME) {
            magic = MAGIC_GNU;
            version = VERSION_GNU_SPACE;
        }
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     * The name is set from the normalized file path.
     *
     * @param file The file that the entry represents.
     */
    public TarArchiveOutputEntry(File file) {
        this(file, ArchiveUtils.normalizeFileName(file.getPath(), false));
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     *
     * @param file     The file that the entry represents.
     * @param fileName the name to be used for the entry.
     */
    public TarArchiveOutputEntry(File file, String fileName) {
        this();
        this.file = file;
        this.linkName = "";
        if (file.isDirectory()) {
            this.mode = DEFAULT_DIR_MODE;
            this.linkFlag = LF_DIR;

            int nameLength = fileName.length();
            if (nameLength == 0 || fileName.charAt(nameLength - 1) != '/') {
                this.name = fileName + "/";
            } else {
                this.name = fileName;
            }
            this.size = 0;
        } else {
            this.mode = DEFAULT_FILE_MODE;
            this.linkFlag = LF_NORMAL;
            this.size = file.length();
            this.name = fileName;
        }
        this.modTime = file.lastModified() / MILLIS_PER_SECOND;
        this.devMajor = 0;
        this.devMinor = 0;
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    public boolean equals(TarArchiveOutputEntry it) {
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
        return !(it == null || getClass() != it.getClass()) && equals((TarArchiveOutputEntry) it);
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
     * Determine if the given entry is a descendant of this entry.
     * Descendancy is determined by the name of the descendant
     * starting with this entry's name.
     *
     * @param desc Entry to be checked as a descendent of this.
     * @return True if entry is a descendant of this.
     */
    public boolean isDescendent(TarArchiveOutputEntry desc) {
        return desc.getName().startsWith(getName());
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
    public TarArchiveOutputEntry setName(String name) {
        this.name = ArchiveUtils.normalizeFileName(name, false);
        boolean isDir = name.endsWith("/");
        this.mode = isDir ? DEFAULT_DIR_MODE : DEFAULT_FILE_MODE;
        this.linkFlag = isDir ? LF_DIR : LF_NORMAL;
        return this;
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
     * Convenience method to set this entry's group and user ids.
     *
     * @param userId  This entry's new user id.
     * @param groupId This entry's new group id.
     */
    public void setIds(int userId, int groupId) {
        setUserId(userId);
        setGroupId(groupId);
    }

    /**
     * Convenience method to set this entry's group and user names.
     *
     * @param userName  This entry's new user name.
     * @param groupName This entry's new group name.
     */
    public void setNames(String userName, String groupName) {
        setUserName(userName);
        setGroupName(groupName);
    }

    /**
     * Set this entry's modification time. The parameter passed
     * to this method is in "Java time".
     *
     * @param date This entry's new modification time.
     */
    public TarArchiveOutputEntry setLastModified(Date date) {
        modTime = date.getTime() / MILLIS_PER_SECOND;
        return this;
    }

    public Date getLastModified() {
        return new Date(modTime * MILLIS_PER_SECOND);
    }

    /**
     * Get this entry's file.
     *
     * @return This entry's file.
     */
    public File getFile() {
        return file;
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
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getEntrySize() {
        return size;
    }

    /**
     * Set this entry's file size.
     *
     * @param size This entry's new file size.
     * @throws IllegalArgumentException if the size is &lt; 0.
     */
    public TarArchiveOutputEntry setEntrySize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size is out of range: " + size);
        }
        this.size = size;
        return this;
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
            throw new IllegalArgumentException("Major device number is out of " + "range: " + devNo);
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
            throw new IllegalArgumentException("Minor device number is out of "
                    + "range: " + devNo);
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
        return linkFlag == LF_GNUTYPE_LONGNAME
                && name.equals(GNU_LONGLINK);
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     */
    public boolean isPaxHeader() {
        return linkFlag == LF_PAX_EXTENDED_HEADER_LC
                || linkFlag == LF_PAX_EXTENDED_HEADER_UC;
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
     * Return whether or not this entry represents a directory.
     *
     * @return True if this entry is a directory.
     */
    public boolean isDirectory() {
        if (file != null) {
            return file.isDirectory();
        }
        return linkFlag == LF_DIR || getName().endsWith("/");
    }

    /**
     * Check if this is a "normal file"
     */
    public boolean isFile() {
        if (file != null) {
            return file.isFile();
        }
        return linkFlag == LF_OLDNORM || linkFlag == LF_NORMAL || !getName().endsWith("/");
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
     * If this entry represents a file, and the file is a directory, return
     * an array of TarEntries for this entry's children.
     *
     * @return An array of TarEntry's for this entry's children.
     */
    public TarArchiveOutputEntry[] getDirectoryEntries() {
        if (file == null || !file.isDirectory()) {
            return new TarArchiveOutputEntry[0];
        }
        String[] list = file.list();
        TarArchiveOutputEntry[] result = new TarArchiveOutputEntry[list.length];
        for (int i = 0; i < list.length; ++i) {
            result[i] = new TarArchiveOutputEntry(new File(file, list[i]));
        }
        return result;
    }

    /**
     * Write an entry's header information to a header buffer.
     *
     * @param outbuf   The tar entry header buffer to fill in.
     * @param encoding encoding to use when writing the file name.
     * @param starMode whether to use the star/GNU tar/BSD tar
     *                 extension for numeric fields if their value doesn't fit in the
     *                 maximum size of standard tar archives
     */
    public void writeEntryHeader(byte[] outbuf, ArchiveEntryEncoding encoding, boolean starMode) throws IOException {
        int offset = 0;
        offset = ArchiveUtils.formatNameBytes(name, outbuf, offset, NAMELEN, encoding);
        offset = writeEntryHeaderField(mode, outbuf, offset, MODELEN, starMode);
        offset = writeEntryHeaderField(userId, outbuf, offset, UIDLEN, starMode);
        offset = writeEntryHeaderField(groupId, outbuf, offset, GIDLEN, starMode);
        offset = writeEntryHeaderField(size, outbuf, offset, SIZELEN, starMode);
        offset = writeEntryHeaderField(modTime, outbuf, offset, MODTIMELEN, starMode);
        int csOffset = offset;
        for (int c = 0; c < CHKSUMLEN; ++c) {
            outbuf[offset++] = (byte) ' ';
        }
        outbuf[offset++] = linkFlag;
        offset = ArchiveUtils.formatNameBytes(linkName, outbuf, offset, NAMELEN, encoding);
        offset = ArchiveUtils.formatNameBytes(magic, outbuf, offset, MAGICLEN);
        offset = ArchiveUtils.formatNameBytes(version, outbuf, offset, VERSIONLEN);
        offset = ArchiveUtils.formatNameBytes(userName, outbuf, offset, UNAMELEN, encoding);
        offset = ArchiveUtils.formatNameBytes(groupName, outbuf, offset, GNAMELEN, encoding);
        offset = writeEntryHeaderField(devMajor, outbuf, offset, DEVLEN, starMode);
        offset = writeEntryHeaderField(devMinor, outbuf, offset, DEVLEN, starMode);
        while (offset < outbuf.length) {
            outbuf[offset++] = 0;
        }
        long chk = computeCheckSum(outbuf);
        formatCheckSumOctalBytes(chk, outbuf, csOffset, CHKSUMLEN);
    }

    private int writeEntryHeaderField(long value, byte[] outbuf, int offset, int length, boolean starMode) {
        if (!starMode && (value < 0
                || value >= (1l << (3 * (length - 1))))) {
            // value doesn't fit into field when written as octal
            // number, will be written to PAX header or causes an
            // error
            return formatLongOctalBytes(0, outbuf, offset, length);
        }
        return formatLongOctalOrBinaryBytes(value, outbuf, offset, length);
    }

    /**
     * Fill buffer with unsigned octal number, padded with leading zeroes.
     *
     * @param value  number to convert to octal - treated as unsigned
     * @param buffer destination buffer
     * @param offset starting offset in buffer
     * @param length length of buffer to fill
     * @throws IllegalArgumentException if the value will not fit in the buffer
     */
    private void formatUnsignedOctalString(final long value, byte[] buffer, final int offset, final int length) {
        int remaining = length;
        remaining--;
        if (value == 0) {
            buffer[offset + remaining--] = (byte) '0';
        } else {
            long val = value;
            for (; remaining >= 0 && val != 0; --remaining) {
                buffer[offset + remaining] = (byte) ((byte) '0' + (byte) (val & 7));
                val = val >>> 3;
            }
            if (val != 0) {
                throw new IllegalArgumentException(value + "=" + Long.toOctalString(value) + " will not fit in octal number buffer of length " + length);
            }
        }

        for (; remaining >= 0; --remaining) { // leading zeros
            buffer[offset + remaining] = (byte) '0';
        }
    }

    /**
     * Write an octal long integer into a buffer.
     * <p/>
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by a space.
     *
     * @param value  The value to write as octal
     * @param buf    The destinationbuffer.
     * @param offset The starting offset into the buffer.
     * @param length The length of the buffer
     * @return The updated offset
     * @throws IllegalArgumentException if the value (and trailer) will not fit in the buffer
     */
    private int formatLongOctalBytes(final long value, byte[] buf, final int offset, final int length) {
        int idx = length - 1; // For space
        formatUnsignedOctalString(value, buf, offset, idx);
        buf[offset + idx] = (byte) ' '; // Trailing space
        return offset + length;
    }

    /**
     * Write an long integer into a buffer as an octal string if this
     * will fit, or as a binary number otherwise.
     * <p/>
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by a space.
     *
     * @param value  The value to write into the buffer.
     * @param buf    The destination buffer.
     * @param offset The starting offset into the buffer.
     * @param length The length of the buffer.
     * @return The updated offset.
     * @throws IllegalArgumentException if the value (and trailer)
     *                                  will not fit in the buffer.
     */
    private int formatLongOctalOrBinaryBytes(final long value, byte[] buf, final int offset, final int length) {
        // Check whether we are dealing with UID/GID or SIZE field
        final long maxAsOctalChar = length == TarConstants.UIDLEN ? TarConstants.MAXID : TarConstants.MAXSIZE;
        final boolean negative = value < 0;
        if (!negative && value <= maxAsOctalChar) { // OK to store as octal chars
            return formatLongOctalBytes(value, buf, offset, length);
        }
        if (length < 9) {
            formatLongBinary(value, buf, offset, length, negative);
        }
        formatBigIntegerBinary(value, buf, offset, length, negative);
        buf[offset] = (byte) (negative ? 0xff : 0x80);
        return offset + length;
    }

    private void formatLongBinary(final long value, byte[] buf, final int offset, final int length, final boolean negative) {
        final int bits = (length - 1) * 8;
        final long max = 1l << bits;
        long val = Math.abs(value);
        if (val >= max) {
            throw new IllegalArgumentException("Value " + value +
                    " is too large for " + length + " byte field.");
        }
        if (negative) {
            val ^= max - 1;
            val |= 0xff << bits;
            val++;
        }
        for (int i = offset + length - 1; i >= offset; i--) {
            buf[i] = (byte) val;
            val >>= 8;
        }
    }

    private void formatBigIntegerBinary(final long value, byte[] buf,
                                               final int offset,
                                               final int length,
                                               final boolean negative) {
        BigInteger val = BigInteger.valueOf(value);
        final byte[] b = val.toByteArray();
        final int len = b.length;
        final int off = offset + length - len;
        System.arraycopy(b, 0, buf, off, len);
        final byte fill = (byte) (negative ? 0xff : 0);
        for (int i = offset + 1; i < off; i++) {
            buf[i] = fill;
        }
    }

    /**
     * Writes an octal value into a buffer.
     * <p/>
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by NUL and then space.
     *
     * @param value  The value to convert
     * @param buf    The destination buffer
     * @param offset The starting offset into the buffer.
     * @param length The size of the buffer.
     * @return The updated value of offset, i.e. offset+length
     * @throws IllegalArgumentException if the value (and trailer) will not fit in the buffer
     */
    private int formatCheckSumOctalBytes(final long value, byte[] buf, final int offset, final int length) {
        int idx = length - 2;
        formatUnsignedOctalString(value, buf, offset, idx);
        buf[offset + idx++] = 0;
        buf[offset + idx] = (byte) ' ';
        return offset + length;
    }

    /**
     * Compute the checksum of a tar entry header.
     *
     * @param buf The tar entry's header buffer.
     * @return The computed checksum.
     */
    private long computeCheckSum(final byte[] buf) {
        long sum = 0;
        for (byte aBuf : buf) {
            sum += BYTE_MASK & aBuf;
        }
        return sum;
    }

}

