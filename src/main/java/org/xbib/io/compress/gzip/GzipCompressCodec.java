/*
 * Copyright (C) 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.io.compress.gzip;

import org.xbib.io.compress.CompressCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressCodec implements CompressCodec<GZIPInputStream, GZIPOutputStream> {

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
