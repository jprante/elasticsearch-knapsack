
package org.xbib.io.compress;

import org.xbib.io.StreamCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipStreamCodec implements StreamCodec<GZIPInputStream, GZIPOutputStream> {

    @Override
    public String getName() {
        return "gz";
    }

    @Override
    public GZIPInputStream decode(InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }

    @Override
    public GZIPInputStream decode(InputStream in, int bufsize) throws IOException {
        return new GZIPInputStream(in, bufsize);
    }

    @Override
    public GZIPOutputStream encode(OutputStream out) throws IOException {
        return new GZIPOutputStream(out);
    }

    @Override
    public GZIPOutputStream encode(OutputStream out, int bufsize) throws IOException {
        return new GZIPOutputStream(out, bufsize);
    }
}
