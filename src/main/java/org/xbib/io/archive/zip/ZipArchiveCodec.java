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
package org.xbib.io.archive.zip;

import org.xbib.io.BytesProgressWatcher;
import org.xbib.io.archive.ArchiveCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ZipArchiveCodec implements ArchiveCodec<ZipSession, ZipArchiveInputStream, ZipArchiveOutputStream> {

    protected final static String NAME = "zip";

    public String getName() {
        return NAME;
    }

    @Override
    public ZipSession newSession(BytesProgressWatcher watcher) {
        return new ZipSession(watcher);
    }

    @Override
    public ZipArchiveInputStream createArchiveInputStream(InputStream in) throws IOException {
        return new ZipArchiveInputStream(in);
    }

    @Override
    public ZipArchiveOutputStream createArchiveOutputStream(OutputStream out) {
        return new ZipArchiveOutputStream(out);
    }

}