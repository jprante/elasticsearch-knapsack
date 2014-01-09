
package org.xbib.io.archivers.zip;

import java.nio.charset.Charset;
import java.util.zip.CRC32;
import java.util.zip.ZipException;

/**
 * A common base class for Unicode extra information extra fields.
 */
public abstract class AbstractUnicodeExtraField implements ZipExtraField {

    private long nameCRC32;

    private byte[] unicodeName;

    private byte[] data;

    protected AbstractUnicodeExtraField() {
    }

    /**
     * Assemble as unicode extension from the name/comment and
     * encoding of the orginal zip entry.
     *
     * @param text  The file name or comment.
     * @param bytes The encoded of the filename or comment in the zip
     *              file.
     * @param off   The offset of the encoded filename or comment in
     *              <code>bytes</code>.
     * @param len   The length of the encoded filename or commentin
     *              <code>bytes</code>.
     */
    protected AbstractUnicodeExtraField(String text, byte[] bytes, int off, int len) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes, off, len);
        nameCRC32 = crc32.getValue();

        unicodeName = text.getBytes(Charset.forName("UTF-8"));
    }

    /**
     * Assemble as unicode extension from the name/comment and
     * encoding of the orginal zip entry.
     *
     * @param text  The file name or comment.
     * @param bytes The encoded of the filename or comment in the zip
     *              file.
     */
    protected AbstractUnicodeExtraField(String text, byte[] bytes) {
        this(text, bytes, 0, bytes.length);
    }

    private void assembleData() {
        if (unicodeName == null) {
            return;
        }

        data = new byte[5 + unicodeName.length];
        // version 1
        data[0] = 0x01;
        System.arraycopy(ZipLong.getBytes(nameCRC32), 0, data, 1, 4);
        System.arraycopy(unicodeName, 0, data, 5, unicodeName.length);
    }

    /**
     * @return The CRC32 checksum of the filename or comment as
     * encoded in the central directory of the zip file.
     */
    public long getNameCRC32() {
        return nameCRC32;
    }

    /**
     * @param nameCRC32 The CRC32 checksum of the filename as encoded
     *                  in the central directory of the zip file to set.
     */
    public void setNameCRC32(long nameCRC32) {
        this.nameCRC32 = nameCRC32;
        data = null;
    }

    /**
     * @return The utf-8 encoded name.
     */
    public byte[] getUnicodeName() {
        byte[] b = null;
        if (unicodeName != null) {
            b = new byte[unicodeName.length];
            System.arraycopy(unicodeName, 0, b, 0, b.length);
        }
        return b;
    }

    /**
     * @param unicodeName The utf-8 encoded name to set.
     */
    public void setUnicodeName(byte[] unicodeName) {
        if (unicodeName != null) {
            this.unicodeName = new byte[unicodeName.length];
            System.arraycopy(unicodeName, 0, this.unicodeName, 0,
                    unicodeName.length);
        } else {
            this.unicodeName = null;
        }
        data = null;
    }

    public byte[] getCentralDirectoryData() {
        if (data == null) {
            this.assembleData();
        }
        byte[] b = null;
        if (data != null) {
            b = new byte[data.length];
            System.arraycopy(data, 0, b, 0, b.length);
        }
        return b;
    }

    public ZipShort getCentralDirectoryLength() {
        if (data == null) {
            assembleData();
        }
        return new ZipShort(data.length);
    }

    public byte[] getLocalFileDataData() {
        return getCentralDirectoryData();
    }

    public ZipShort getLocalFileDataLength() {
        return getCentralDirectoryLength();
    }

    public void parseFromLocalFileData(byte[] buffer, int offset, int length)
            throws ZipException {

        if (length < 5) {
            throw new ZipException("UniCode path extra data must have at least 5 bytes.");
        }

        int version = buffer[offset];

        if (version != 0x01) {
            throw new ZipException("Unsupported version [" + version
                    + "] for UniCode path extra data.");
        }

        nameCRC32 = ZipLong.getValue(buffer, offset + 1);
        unicodeName = new byte[length - 5];
        System.arraycopy(buffer, offset + 5, unicodeName, 0, length - 5);
        data = null;
    }

    /**
     * Doesn't do anything special since this class always uses the
     * same data in central directory and local file data.
     */
    public void parseFromCentralDirectoryData(byte[] buffer, int offset,
                                              int length)
            throws ZipException {
        parseFromLocalFileData(buffer, offset, length);
    }
}
