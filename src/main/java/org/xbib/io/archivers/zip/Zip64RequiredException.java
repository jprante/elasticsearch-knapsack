
package org.xbib.io.archivers.zip;

import java.util.zip.ZipException;

/**
 * Exception thrown when attempting to write data that requires Zip64
 * support to an archive and {@link ZipArchiveOutputStream#setUseZip64
 * UseZip64} has been set to {@link Zip64Mode#Never Never}.
 */
public class Zip64RequiredException extends ZipException {

    /**
     * Helper to format "entry too big" messages.
     */
    static String getEntryTooBigMessage(ZipArchiveEntry ze) {
        return ze.getName() + "'s size exceeds the limit of 4GByte.";
    }

    static final String ARCHIVE_TOO_BIG_MESSAGE =
            "archive's size exceeds the limit of 4GByte.";

    static final String TOO_MANY_ENTRIES_MESSAGE =
            "archive contains more than 65535 entries.";

    public Zip64RequiredException(String reason) {
        super(reason);
    }
}
