/*
 * Copyright 2002,2004 The Apache Software Foundation.
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
package org.xbib.io.tar;

import java.io.File;
import java.util.Date;
import java.util.Locale;

/**
 * This class represents an entry in a Tar archive. It consists of the
 * entry's header, as well as the entry's File. Entries can be instantiated in
 * one of three ways, depending on how they are to be used.<p>TarEntries
 * that are created from the header bytes read from an archive are
 * instantiated with the TarEntry( byte[] ) constructor. These entries will be
 * used when extracting from or listing the contents of an archive. These
 * entries have their header filled in using the header bytes. They also set
 * the File to null, since they reference an archive entry not a file.</p>
 *  <p>TarEntries that are created from Files that are to be written into
 * an archive are instantiated with the TarEntry( File ) constructor. These
 * entries have their header filled in using the File's information. They also
 * keep a reference to the File for convenience when writing entries.</p>
 *  <p>Finally, TarEntries can be constructed from nothing but a name. This
 * allows the programmer to construct the entry by hand, for instance when
 * only an InputStream is available for writing to the archive, and the header
 * information is constructed from other information. In this case the header
 * fields are set to defaults and the File is set to null.</p>
 *  <p>The C structure for a Tar Entry's header is:
 * <pre>struct header {char name[NAMSIZ];
 * char mode[8];char uid[8];char gid[8];char size[12];char mtime[12];char chksum[8];char linkflag;
 * char linkname[NAMSIZ];char magic[8];char uname[TUNMLEN];char gname[TGNMLEN];char devmajor[8];
 * char devminor[8];} header;</pre></p>
 *
 * @author <a href="mailto:time@ice.com">Timothy Gerard Endres</a>
 * @author <a href="mailto:stefano@apache.org">Stefano Mazzocchi</a>
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 *
 * @see TarInputStream
 * @see TarOutputStream
 */
public class TarEntry {

    /** The length of the name field in a header buffer. */
    public static final int NAMELEN = 100;

    /** The entry's minor device number. */
    private File file;

    /** The entry's user name. */
    private StringBuilder groupName;

    /** The entry's link flag. */
    private StringBuilder linkName;

    /** The entry's link name. */
    private StringBuilder magic;

    /** name */
    private StringBuilder name;

    /** The entry's magic tag. */
    private StringBuilder userName;

    /** The entry's checksum. */
    private byte linkFlag;

    /** The entry's group name. */
    private int devMajor;

    /** The entry's major device number. */
    private int devMinor;

    /** The entry's user id. */
    private int groupID;

    /** The entry's name. */
    private int mode;

    /** The entry's permission mode. */
    private int userID;

    /** The entry's size. */
    private long modTime;

    /** The entry's group id. */
    private long size;

    /**
     * Construct an entry with only a name. This allows the programmer to
     * construct the entry's header "by hand". File is set to null.
     *
     * @param name the name of the entry
     */
    public TarEntry(String name) {
        this();

        final boolean isDir = name.endsWith("/");

        this.name = new StringBuilder(name);
        this.mode = isDir ? 040755 : 0100644; // octal
        this.linkFlag = isDir ? TarConstants.LF_DIR : TarConstants.LF_NORMAL;
        this.modTime = (new Date()).getTime() / 1000;
        this.linkName = new StringBuilder("");
        this.userName = new StringBuilder("");
        this.groupName = new StringBuilder("");
    }

    /**
     * Construct an entry with a name an a link flag.
     *
     * @param name Description of Parameter
     * @param linkFlag Description of Parameter
     */
    public TarEntry(String name, byte linkFlag) {
        this(name);
        this.linkFlag = linkFlag;
    }

    public TarEntry(String name, String linkName, byte linkFlag) {
        this(name, linkFlag);
        this.linkName = new StringBuilder(linkName);
    }

    /**
     * Construct an entry for a file. File is set to file, and the header is
     * constructed from information from the file.
     *
     * @param file The file that the entry represents.
     */
    public TarEntry(File file) {
        this();
        this.file = file;
        String name = file.getPath();
        // Strip off drive letters!
        final String osName = System.getProperty("os.name").toLowerCase(Locale.US);

        if (-1 != osName.indexOf("netware")) {
            if (name.length() > 2) {
                final char ch1 = name.charAt(0);
                final char ch2 = name.charAt(1);

                if ((ch2 == ':') && (((ch1 >= 'a') && (ch1 <= 'z')) || ((ch1 >= 'A') && (ch1 <= 'Z')))) {
                    name = name.substring(2);
                }
            }
        } else if (-1 != osName.indexOf("netware")) {
            final int colon = name.indexOf(':');

            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        name = name.replace(File.separatorChar, '/');

        // No absolute pathnames
        // Windows (and Posix?) paths can start with "\\NetworkDrive\",
        // so we loop on starting /'s.
        while (name.startsWith("/")) {
            name = name.substring(1);
        }

        linkName = new StringBuilder("");
        this.name = new StringBuilder(name);

        if (file.isDirectory()) {
            mode = 040755; // octal
            linkFlag = TarConstants.LF_DIR;

            if (name.charAt(name.length() - 1) != '/') {
                this.name.append("/");
            }
        } else {
            mode = 0100644; // octal
            linkFlag = TarConstants.LF_NORMAL;
        }

        size = file.length();
        modTime = file.lastModified() / 1000;
        devMajor = 0;
        devMinor = 0;
    }

    /**
     * Construct an entry from an archive's header bytes. File is set to null.
     *
     * @param header The header bytes from a tar archive entry.
     */
    public TarEntry(final byte[] header) {
        this();
        parseTarHeader(header);
    }

    /**
     * Construct an empty entry and prepares the header values.
     */
    private TarEntry() {
        magic = new StringBuilder(TarConstants.TMAGIC);
        name = new StringBuilder();
        linkName = new StringBuilder();

        String user = System.getProperty("user.name", "");

        if (user.length() > 31) {
            user = user.substring(0, 31);
        }

        userName = new StringBuilder(user);
        groupName = new StringBuilder("");
    }

    /**
     * If this entry represents a file, and the file is a directory,
     * return an array of TarEntries for this entry's children.
     *
     * @return An array of TarEntry's for this entry's children.
     */
    public TarEntry[] getDirectoryEntries() {
        if ((null == file) || !file.isDirectory()) {
            return new TarEntry[0];
        }

        final String[] list = file.list();
        final TarEntry[] result = new TarEntry[list.length];

        for (int i = 0; i < list.length; ++i) {
            result[i] = new TarEntry(new File(file, list[i]));
        }

        return result;
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
     * Get this entry's group id.
     *
     * @return This entry's group id.
     */
    public int getGroupID() {
        return groupID;
    }

    /**
     * Get this entry's group name.
     *
     * @return This entry's group name.
     */
    public String getGroupName() {
        return groupName.toString();
    }

    /**
     * Set this entry's modification time.
     *
     * @return The ModTime value
     */
    public Date getModTime() {
        return new Date(modTime * 1000);
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
     * Get this entry's name.
     *
     * @return This entry's name.
     */
    public String getName() {
        return name.toString();
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getSize() {
        return size;
    }

    /**
     * Get this entry's user id.
     *
     * @return This entry's user id.
     */
    public int getUserID() {
        return userID;
    }

    /**
     * Get this entry's user name.
     *
     * @return This entry's user name.
     */
    public String getUserName() {
        return userName.toString();
    }

    /**
     * Determine if the given entry is a descendant of this entry.
     * Descendancy is determined by the name of the descendant starting with
     * this entry's name.
     *
     * @param desc Entry to be checked as a descendent of
     *
     * @return True if entry is a descendant of
     */
    public boolean isDescendent(TarEntry desc) {
        return desc.getName().startsWith(getName());
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

        if (linkFlag == TarConstants.LF_DIR) {
            return true;
        }

        if (getName().endsWith("/")) {
            return true;
        }

        return false;
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param other Entry to be checked for equality.
     *
     * @return True if the entries are equal.
     */
    public boolean isEqualTo(TarEntry other) {
        return getName().equals(other.getName());
    }

    /**
     * Indicate if this entry is a GNU long name block
     *
     * @return true if this is a long name extension provided by GNU tar
     */
    public boolean isGNULongNameEntry() {
        return (linkFlag == TarConstants.LF_GNUTYPE_LONGNAME) && name.toString().equals(TarConstants.GNU_LONGLINK);
    }

    /**
     * Parse an entry's header information from a header buffer.
     *
     * @param header The tar entry header buffer to get information from.
     */
    private void parseTarHeader(byte[] header) {
        int offset = 0;

        name = parseName(header, offset, NAMELEN);
        offset += NAMELEN;
        mode = (int)parseOctal(header, offset, TarConstants.MODELEN);
        offset += TarConstants.MODELEN;
        userID = (int)parseOctal(header, offset, TarConstants.UIDLEN);
        offset += TarConstants.UIDLEN;
        groupID = (int)parseOctal(header, offset, TarConstants.GIDLEN);
        offset += TarConstants.GIDLEN;
        size = parseOctal(header, offset, TarConstants.SIZELEN);
        offset += TarConstants.SIZELEN;
        modTime = parseOctal(header, offset, TarConstants.MODTIMELEN);
        offset += TarConstants.MODTIMELEN;
        //checkSum = (int)parseOctal(header, offset, TarConstants.CHKSUMLEN);
        offset += TarConstants.CHKSUMLEN;
        linkFlag = header[offset++];
        linkName = parseName(header, offset, NAMELEN);
        offset += NAMELEN;
        magic = parseName(header, offset, TarConstants.MAGICLEN);
        offset += TarConstants.MAGICLEN;
        userName = parseName(header, offset, TarConstants.UNAMELEN);
        offset += TarConstants.UNAMELEN;
        groupName = parseName(header, offset, TarConstants.GNAMELEN);
        offset += TarConstants.GNAMELEN;
        devMajor = (int)parseOctal(header, offset, TarConstants.DEVLEN);
        offset += TarConstants.DEVLEN;
        devMinor = (int)parseOctal(header, offset, TarConstants.DEVLEN);
    }

    /**
     * Set this entry's group id.
     *
     * @param groupId This entry's new group id.
     */
    public void setGroupID(int groupId) {
        groupID = groupId;
    }

    /**
     * Set this entry's group name.
     *
     * @param groupName This entry's new group name.
     */
    public void setGroupName(String groupName) {
        this.groupName = new StringBuilder(groupName);
    }

    /**
     * Set this entry's modification time. The parameter passed to this
     * method is in "Java time".
     *
     * @param time This entry's new modification time.
     */
    public void setModTime(long time) {
        modTime = time / 1000;
    }

    /**
     * Set this entry's modification time.
     *
     * @param time This entry's new modification time.
     */
    public void setModTime(Date time) {
        modTime = time.getTime() / 1000;
    }

    /**
     * Set the mode for this entry
     *
     * @param mode The new Mode value
     */
    public void setMode(int mode) {
        this.mode = mode;
    }

    /**
     * Set this entry's name.
     *
     * @param name This entry's new name.
     */
    public void setName(String name) {
        this.name = new StringBuilder(name);
    }

    /**
     * Set this entry's file size.
     *
     * @param size This entry's new file size.
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Set this entry's user id.
     *
     * @param userId This entry's new user id.
     */
    public void setUserID(int userId) {
        userID = userId;
    }

    /**
     * Set this entry's user name.
     *
     * @param userName This entry's new user name.
     */
    public void setUserName(String userName) {
        this.userName = new StringBuilder(userName);
    }

    /**
     * Write an entry's header information to a header buffer.
     *
     * @param buffer The tar entry header buffer to fill in.
     */
    public void writeEntryHeader(byte[] buffer) {
        int offset = 0;

        offset = getNameBytes(name, buffer, offset, NAMELEN);
        offset = getOctalBytes(mode, buffer, offset, TarConstants.MODELEN);
        offset = getOctalBytes(userID, buffer, offset, TarConstants.UIDLEN);
        offset = getOctalBytes(groupID, buffer, offset, TarConstants.GIDLEN);
        offset = getLongOctalBytes(size, buffer, offset, TarConstants.SIZELEN);
        offset = getLongOctalBytes(modTime, buffer, offset, TarConstants.MODTIMELEN);

        final int checkSumOffset = offset;

        for (int i = 0; i < TarConstants.CHKSUMLEN; ++i) {
            buffer[offset++] = (byte) ' ';
        }

        buffer[offset++] = linkFlag;
        offset = getNameBytes(linkName, buffer, offset, NAMELEN);
        offset = getNameBytes(magic, buffer, offset, TarConstants.MAGICLEN);
        offset = getNameBytes(userName, buffer, offset, TarConstants.UNAMELEN);
        offset = getNameBytes(groupName, buffer, offset, TarConstants.GNAMELEN);
        offset = getOctalBytes(devMajor, buffer, offset, TarConstants.DEVLEN);
        offset = getOctalBytes(devMinor, buffer, offset, TarConstants.DEVLEN);

        while (offset < buffer.length) {
            buffer[offset++] = 0;
        }

        getCheckSumOctalBytes(computeCheckSum(buffer), buffer, checkSumOffset, TarConstants.CHKSUMLEN);
    }

    /**
     * Compute the checksum of a tar entry header.
     *
     * @param buffer The tar entry's header buffer.
     *
     * @return The computed checksum.
     */
    private long computeCheckSum(byte[] buffer) {
        long sum = 0;

        for (int i = 0; i < buffer.length; ++i) {
            sum += (255 & buffer[i]);
        }

        return sum;
    }

    /**
     * Parse the checksum octal integer from a header buffer.
     *
     * @param value Description of Parameter
     * @param buf Description of Parameter
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     *
     * @return The integer value of the entry's checksum.
     */
    private int getCheckSumOctalBytes(long value, byte[] buf, int offset, int length) {
        getOctalBytes(value, buf, offset, length);

        buf[(offset + length) - 1] = (byte) ' ';
        buf[(offset + length) - 2] = 0;

        return offset + length;
    }

    /**
     * Parse an octal long integer from a header buffer.
     *
     * @param value Description of Parameter
     * @param buf Description of Parameter
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     *
     * @return The long value of the octal bytes.
     */
    private int getLongOctalBytes(long value, byte[] buf, int offset, int length) {
        byte[] temp = new byte[length + 1];

        getOctalBytes(value, temp, 0, length + 1);
        System.arraycopy(temp, 0, buf, offset, length);

        return offset + length;
    }

    /**
     * Determine the number of bytes in an entry name.
     *
     * @param name Description of Parameter
     * @param buffer Description of Parameter
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     *
     * @return The number of bytes in a header's entry name.
     */
    private int getNameBytes(StringBuilder name, byte[] buffer, int offset, int length) {
        int i;

        for (i = 0; (i < length) && (i < name.length()); ++i) {
            buffer[offset + i] = (byte) name.charAt(i);
        }

        for (; i < length; ++i) {
            buffer[offset + i] = 0;
        }

        return offset + length;
    }

    /**
     * Parse an octal integer from a header buffer.
     *
     * @param value
     * @param buffer 
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     *
     * @return The integer value of the octal bytes.
     */
    private int getOctalBytes(long value, byte[] buffer, int offset, int length) {
        int idx = length - 1;

        buffer[offset + idx] = 0;
        --idx;
        buffer[offset + idx] = (byte) ' ';
        --idx;

        if (value == 0) {
            buffer[offset + idx] = (byte) '0';
            --idx;
        } else {
            long val = value;

            while ((idx >= 0) && (val > 0)) {
                buffer[offset + idx] = (byte) ((byte) '0' + (byte) (val & 7));
                val = val >> 3;
                idx--;
            }
        }

        while (idx >= 0) {
            buffer[offset + idx] = (byte) ' ';
            idx--;
        }

        return offset + length;
    }

    /**
     * Parse an entry name from a header buffer.
     *
     * @param header The header buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     *
     * @return The header's entry name.
     */
    private StringBuilder parseName(byte[] header, int offset, int length) {
        StringBuilder result = new StringBuilder(length);
        int end = offset + length;

        for (int i = offset; i < end; ++i) {
            if (header[i] == 0) {
                break;
            }

            result.append((char) header[i]);
        }

        return result;
    }

    /**
     * Parse an octal string from a header buffer. This is used for the
     * file permission mode value.
     *
     * @param header The header buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     *
     * @return The long value of the octal string.
     */
    private long parseOctal(byte[] header, int offset, int length) {
        long result = 0;
        boolean stillPadding = true;
        int end = offset + length;

        for (int i = offset; i < end; ++i) {
            if (header[i] == 0) {
                break;
            }

            if ((header[i] == (byte) ' ') || (header[i] == '0')) {
                if (stillPadding) {
                    continue;
                }

                if (header[i] == (byte) ' ') {
                    break;
                }
            }

            stillPadding = false;
            result = (result << 3) + (header[i] - '0');
        }

        return result;
    }

}
