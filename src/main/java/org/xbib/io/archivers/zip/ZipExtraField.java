
package org.xbib.io.archivers.zip;

import java.util.zip.ZipException;

/**
 * General format of extra field data. <p>
 * <p/>
 * Extra fields usually appear twice per file, once in the local file data and
 * once in the central directory. Usually they are the same, but they don't have
 * to be. {@link java.util.zip.ZipOutputStream java.util.zip.ZipOutputStream}
 * will only use the local file data in both places.</p>
 */
public interface ZipExtraField {
    /**
     * The Header-ID.
     *
     * @return The HeaderId value
     */
    ZipShort getHeaderId();

    /**
     * Length of the extra field in the local file data - without Header-ID or
     * length specifier.
     *
     * @return The LocalFileDataLength value
     */
    ZipShort getLocalFileDataLength();

    /**
     * Length of the extra field in the central directory - without Header-ID or
     * length specifier.
     *
     * @return The CentralDirectoryLength value
     */
    ZipShort getCentralDirectoryLength();

    /**
     * The actual data to put into local file data - without Header-ID or length
     * specifier.
     *
     * @return The LocalFileDataData value
     */
    byte[] getLocalFileDataData();

    /**
     * The actual data to put into central directory - without Header-ID or
     * length specifier.
     *
     * @return The CentralDirectoryData value
     */
    byte[] getCentralDirectoryData();

    /**
     * Populate data from this array as if it was in local file data.
     *
     * @param buffer the buffer to read data from
     * @param offset offset into buffer to read data
     * @param length the length of data
     * @throws java.util.zip.ZipException on error
     */
    void parseFromLocalFileData(byte[] buffer, int offset, int length)
            throws ZipException;

    /**
     * Populate data from this array as if it was in central directory data.
     *
     * @param buffer the buffer to read data from
     * @param offset offset into buffer to read data
     * @param length the length of data
     * @throws java.util.zip.ZipException on error
     */
    void parseFromCentralDirectoryData(byte[] buffer, int offset, int length)
            throws ZipException;
}
