
package org.xbib.io.archivers.zip;

/**
 * Info-ZIP Unicode Path Extra Field (0x7075):
 * <p/>
 * <p>Stores the UTF-8 version of the file name field as stored in the
 * local header and central directory header.</p>
 * <p/>
 * <pre>
 *         Value         Size        Description
 *         -----         ----        -----------
 * (UPath) 0x7075        Short       tag for this extra block type ("up")
 *         TSize         Short       total data size for this block
 *         Version       1 byte      version of this extra field, currently 1
 *         NameCRC32     4 bytes     File Name Field CRC32 Checksum
 *         UnicodeName   Variable    UTF-8 version of the entry File Name
 * </pre>
 */
public class UnicodePathExtraField extends AbstractUnicodeExtraField {

    public static final ZipShort UPATH_ID = new ZipShort(0x7075);

    public UnicodePathExtraField() {
    }

    /**
     * Assemble as unicode path extension from the name given as
     * text as well as the encoded bytes actually written to the archive.
     *
     * @param text  The file name
     * @param bytes the bytes actually written to the archive
     * @param off   The offset of the encoded filename in <code>bytes</code>.
     * @param len   The length of the encoded filename or comment in
     *              <code>bytes</code>.
     */
    public UnicodePathExtraField(String text, byte[] bytes, int off, int len) {
        super(text, bytes, off, len);
    }

    /**
     * Assemble as unicode path extension from the name given as
     * text as well as the encoded bytes actually written to the archive.
     *
     * @param name  The file name
     * @param bytes the bytes actually written to the archive
     */
    public UnicodePathExtraField(String name, byte[] bytes) {
        super(name, bytes);
    }

    public ZipShort getHeaderId() {
        return UPATH_ID;
    }
}
