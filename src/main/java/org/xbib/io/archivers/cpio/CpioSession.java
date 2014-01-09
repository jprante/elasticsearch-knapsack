
package org.xbib.io.archivers.cpio;

import org.xbib.io.archivers.ArchiveSession;

public class CpioSession extends ArchiveSession {

    protected String getSuffix() {
        return "cpio";
    }
}
