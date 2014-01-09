
package org.xbib.io.archivers.zip;

/**
 * Constants from stat.h on Unix systems.
 */
public interface UnixStat {
    /**
     * Bits used for permissions (and sticky bit)
     */
    int PERM_MASK = 07777;
    /**
     * Indicates symbolic links.
     */
    int LINK_FLAG = 0120000;
    /**
     * Indicates plain files.
     */
    int FILE_FLAG = 0100000;
    /**
     * Indicates directories.
     */
    int DIR_FLAG = 040000;

}
