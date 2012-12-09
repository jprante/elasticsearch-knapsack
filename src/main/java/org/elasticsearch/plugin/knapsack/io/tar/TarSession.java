/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.plugin.knapsack.io.tar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import org.elasticsearch.plugin.knapsack.io.ObjectPacket;
import org.elasticsearch.plugin.knapsack.io.Packet;
import org.elasticsearch.plugin.knapsack.io.Session;
import org.xbib.io.StreamCodecService;

/**
 * Tar Session
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarSession implements Session {

    private final StreamCodecService codecFactory = StreamCodecService.getInstance();
    private final TarEntryReadOperator readOp = new TarEntryReadOperator();
    private final TarEntryWriteOperator writeOp = new TarEntryWriteOperator();
    private boolean isOpen;
    private FileInputStream fin;
    private FileOutputStream fout;
    private TarInputStream in;
    private TarOutputStream out;
    private String scheme;
    private String part;

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public void setName(String name) {
        this.part = name;
    }

    public String getName() {
        return part;
    }

    @Override
    public synchronized void open(Mode mode) throws IOException {
        if (isOpen) {
            return;
        }
        switch (mode) {
            case READ:
                if (scheme.startsWith("targz")) {
                    String s = part + ".tar.gz";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarInputStream(codecFactory.getCodec("gz").decode(fin));
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else if (scheme.startsWith("tarbz2")) {
                    String s = part + ".tar.bz2";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarInputStream(codecFactory.getCodec("bz2").decode(fin));
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else if (scheme.startsWith("tarxz")) {
                    String s = part + ".tar.xz";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarInputStream(codecFactory.getCodec("xz").decode(fin));
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else {
                    String s = part + ".tar";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarInputStream(fin);
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                }
                break;
            case WRITE:
                if (scheme.equals("targz")) {
                    createFileOutputStream(".tar.gz");
                    this.out = new TarOutputStream(codecFactory.getCodec("gz").encode(fout));
                    out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.equals("tarbz2")) {
                    createFileOutputStream(".tar.bz2");
                    this.out = new TarOutputStream(codecFactory.getCodec("bz2").encode(fout));
                    out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.equals("tarxz")) {
                    createFileOutputStream(".tar.xz");
                    this.out = new TarOutputStream(codecFactory.getCodec("xz").encode(fout));
                    out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else {
                    createFileOutputStream(".tar");
                    this.out = new TarOutputStream(fout);
                    out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                }
                break;
        }
    }

    /**
     * Close Tar session
     */
    @Override
    public synchronized void close() throws IOException {
        if (!isOpen) {
            return;
        }
        if (out != null) {
            out.flush();
            out.close();
            out = null;
        }
        if (in != null) {
            in.close();
        }
        this.isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    public TarInputStream getInputStream() {
        return in;
    }

    public TarOutputStream getOutputStream() {
        return out;
    }

    /**
     * Helper method for creating the FileOutputStream. Creates the directory if
     * it doesnot exist.
     *
     * @param s
     * @throws IOException
     */
    private synchronized void createFileOutputStream(String s)
            throws IOException {
        File f = new File(part + s);
        if (!f.getAbsoluteFile().getParentFile().exists()
                && !f.getAbsoluteFile().getParentFile().mkdirs()) {
            throw new RuntimeException(
                    "Could not create directories to store file: " + f);
        }
        this.fout = new FileOutputStream(f);
    }

    @Override
    public Packet read() throws IOException {
        return readOp.read(this);
    }

    @Override
    public void write(Packet packet) throws IOException {
        writeOp.write(this, packet);
    }

    @Override
    public Packet newPacket() {
        return new ObjectPacket();
    }
}
