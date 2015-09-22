package org.xbib.io.archive.cpio;

import org.xbib.io.BytesProgressWatcher;
import org.xbib.io.archive.ArchiveSession;

/**
 * Cpio Session
 */
public class CpioSession extends ArchiveSession<CpioArchiveInputStream, CpioArchiveOutputStream> {

    protected CpioSession(BytesProgressWatcher watcher) {
        super(watcher);
    }

    @Override
    protected String getName() {
        return CpioArchiveCodec.NAME;
    }

}
