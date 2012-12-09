/*
 * Licensed to Jörg Prante and xbib under one or more contributor 
 * license agreements. See the NOTICE.txt file distributed with this work
 * for additional information regarding copyright ownership.
 *
 * Copyright (C) 2012 Jörg Prante and xbib
 * 
 * This program is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU Affero General Public License as published 
 * by the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see http://www.gnu.org/licenses 
 * or write to the Free Software Foundation, Inc., 51 Franklin Street, 
 * Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * The interactive user interfaces in modified source and object code 
 * versions of this program must display Appropriate Legal Notices, 
 * as required under Section 5 of the GNU Affero General Public License.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public 
 * License, these Appropriate Legal Notices must retain the display of the 
 * "Powered by xbib" logo. If the display of the logo is not reasonably 
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by xbib".
 */
package org.xbib.io.tar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import org.xbib.io.ObjectPacket;
import org.xbib.io.Packet;
import org.xbib.io.Session;
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
                if (scheme.startsWith("targz")) {
                    createFileOutputStream(".tar.gz");
                    this.out = new TarOutputStream(codecFactory.getCodec("gz").encode(fout));
                    out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.startsWith("tarbz2")) {
                    createFileOutputStream(".tar.bz2");
                    // bzip2 is a memory hog
                    this.out = new TarOutputStream(codecFactory.getCodec("bz2").encode(fout));
                    out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.startsWith("tarxz")) {
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
