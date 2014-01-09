package org.xbib.io.archivers.tar;

import org.xbib.io.archivers.ArchiveSession;

public class TarSession extends ArchiveSession {

    protected String getSuffix() {
        return "tar";
    }

}
