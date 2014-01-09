
package org.xbib.io.archivers.zip;

/**
 * Simple placeholder for all those extra fields we don't want to deal
 * with.
 * <p/>
 * <p>Assumes local file data and central directory entries are
 * identical - unless told the opposite.</p>
 */
public class UnrecognizedExtraField implements ZipExtraField {

    /**
     * The Header-ID.
     */
    private ZipShort headerId;

    /**
     * Set the header id.
     *
     * @param headerId the header id to use
     */
    public void setHeaderId(ZipShort headerId) {
        this.headerId = headerId;
    }

    /**
     * Get the header id.
     *
     * @return the header id
     */
    public ZipShort getHeaderId() {
        return headerId;
    }

    /**
     * Extra field data in local file data - without
     * Header-ID or length specifier.
     */
    private byte[] localData;

    /**
     * Set the extra field data in the local file data -
     * without Header-ID or length specifier.
     *
     * @param data the field data to use
     */
    public void setLocalFileDataData(byte[] data) {
        localData = ZipUtil.copy(data);
    }

    /**
     * Get the length of the local data.
     *
     * @return the length of the local data
     */
    public ZipShort getLocalFileDataLength() {
        return new ZipShort(localData.length);
    }

    /**
     * Get the local data.
     *
     * @return the local data
     */
    public byte[] getLocalFileDataData() {
        return ZipUtil.copy(localData);
    }

    /**
     * Extra field data in central directory - without
     * Header-ID or length specifier.
     */
    private byte[] centralData;

    /**
     * Set the extra field data in central directory.
     *
     * @param data the data to use
     */
    public void setCentralDirectoryData(byte[] data) {
        centralData = ZipUtil.copy(data);
    }

    /**
     * Get the central data length.
     * If there is no central data, get the local file data length.
     *
     * @return the central data length
     */
    public ZipShort getCentralDirectoryLength() {
        if (centralData != null) {
            return new ZipShort(centralData.length);
        }
        return getLocalFileDataLength();
    }

    /**
     * Get the central data.
     *
     * @return the central data if present, else return the local file data
     */
    public byte[] getCentralDirectoryData() {
        if (centralData != null) {
            return ZipUtil.copy(centralData);
        }
        return getLocalFileDataData();
    }

    /**
     * @param data   the array of bytes.
     * @param offset the source location in the data array.
     * @param length the number of bytes to use in the data array.
     * @see ZipExtraField#parseFromLocalFileData(byte[], int, int)
     */
    public void parseFromLocalFileData(byte[] data, int offset, int length) {
        byte[] tmp = new byte[length];
        System.arraycopy(data, offset, tmp, 0, length);
        setLocalFileDataData(tmp);
    }

    /**
     * @param data   the array of bytes.
     * @param offset the source location in the data array.
     * @param length the number of bytes to use in the data array.
     * @see ZipExtraField#parseFromCentralDirectoryData(byte[], int, int)
     */
    public void parseFromCentralDirectoryData(byte[] data, int offset,
                                              int length) {
        byte[] tmp = new byte[length];
        System.arraycopy(data, offset, tmp, 0, length);
        setCentralDirectoryData(tmp);
        if (localData == null) {
            setLocalFileDataData(tmp);
        }
    }

}
