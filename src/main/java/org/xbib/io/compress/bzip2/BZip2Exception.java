package org.xbib.io.compress.bzip2;

import java.io.IOException;

/**
 * Indicates that a data format error was encountered while attempting to decode bzip2 data
 */
public class BZip2Exception extends IOException {

    /**
     * @param reason The exception's reason string
     */
    public BZip2Exception(String reason) {

        super(reason);

    }

}
