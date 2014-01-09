
package org.xbib.io.archivers.tar;

import java.io.IOException;

/**
 * This class represents a sparse entry in a Tar archive.
 * <p/>
 * <p/>
 * The C structure for a sparse entry is:
 * <pre>
 * struct posix_header {
 * struct sparse sp[21]; // TarConstants.SPARSELEN_GNU_SPARSE     - offset 0
 * char isextended;      // TarConstants.ISEXTENDEDLEN_GNU_SPARSE - offset 504
 * };
 * </pre>
 * Whereas, "struct sparse" is:
 * <pre>
 * struct sparse {
 * char offset[12];   // offset 0
 * char numbytes[12]; // offset 12
 * };
 * </pre>
 */

public class TarArchiveSparseEntry implements TarConstants {
    /**
     * If an extension sparse header follows.
     */
    private boolean isExtended;

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @throws java.io.IOException on unknown format
     */
    public TarArchiveSparseEntry(byte[] headerBuf) throws IOException {
        int offset = 0;
        offset += SPARSELEN_GNU_SPARSE;
        isExtended = TarUtils.parseBoolean(headerBuf, offset);
    }

    public boolean isExtended() {
        return isExtended;
    }
}
