package org.xbib.io.compress;

import java.io.IOException;

/**
 * Interface used by {@link Uncompressor} implementations: receives
 * uncompressed data and processes it appropriately.
 *
 * @since 0.9.4
 */
public interface DataHandler
{
    /**
     * Method called with uncompressed data as it becomes available.
     */
    public void handleData(byte[] buffer, int offset, int len) throws IOException;

    /**
     * Method called after last call to {@link #handleData}, for successful
     * operation, if and when caller is informed about end of content
     * Note that if an exception thrown by {@link #handleData} has caused processing
     * to be aborted, this method might not get called.
     * Implementation may choose to free resources, flush state, or perform
     * validation at this point.
     */
    public void allDataHandled() throws IOException;
}
