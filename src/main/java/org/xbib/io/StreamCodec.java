
package org.xbib.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface StreamCodec<I extends InputStream, O extends OutputStream> {

    String getName();

    I decode(InputStream in) throws IOException;

    I decode(InputStream in, int bufsize) throws IOException;

    O encode(OutputStream out) throws IOException;

    O encode(OutputStream out, int bufsize) throws IOException;
}
