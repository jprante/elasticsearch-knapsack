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
package org.xbib.io.tar;

/**
 * This interface contains all the definitions used in the package.
 *
 * @author <a href="mailto:time@ice.com">Timothy Gerard Endres</a>
 * @author <a href="mailto:stefano@apache.org">Stefano Mazzocchi</a>
 */
interface TarConstants {
    /**
     * Flag to indicate that an error should be generated if an attempt
     * is made to write an entry that exceeds the 100 char POSIX limit.
     */
    int LONGFILE_ERROR = 0;
    /**
     * Flag to indicate that entry name should be truncated if an
     * attempt is made to write an entry that exceeds the 100 char POSIX
     * limit.
     */
    int LONGFILE_TRUNCATE = 1;
    /**
     * Flag to indicate that entry name should be formatted according
     * to GNU tar extension if an attempt is made to write an entry that
     * exceeds the 100 char POSIX limit. Note that this makes the jar
     * unreadable by non-GNU tar commands.
     */
    int LONGFILE_GNU = 2;
    /** the default record size */
    int DEFAULT_RECORDSIZE = 512;
    
    /** the default block size */
    int DEFAULT_BLOCKSIZE = 512 * 20;
    
    int FILE_NAME_SIZE = 100;
    /**
     * The name of the GNU tar entry which contains a long name.
     */
    String GNU_LONGLINK = "././@LongLink";
    /**
     * The magic tag representing a GNU tar archive.
     */
    String GNU_TMAGIC = "ustar  ";
    /**
     * The magic tag representing a POSIX tar archive.
     */
    String TMAGIC = "ustar";
    /**
     * Normal file type.
     */
    byte LF_NORMAL = (byte) '0';
    /**
     * Link file type.
     */
    byte LF_LINK = (byte) '1';
    /**
     * Symbolic link file type.
     */
    byte LF_SYMLINK = (byte) '2';
    /**
     * Character device file type.
     */
    byte LF_CHAR = (byte) '3';
    /**
     * Block device file type.
     */
    byte LF_BLOCK = (byte) '4';
    /**
     * Directory file type.
     */
    byte LF_DIR = (byte) '5';
    /**
     * FIFO (pipe) file type.
     */
    byte LF_FIFO = (byte) '6';
    /**
     * Contiguous file type.
     */
    byte LF_CONTIGUOUS = (byte) '7';
    /**
     * Identifies the next file on the tape as having a long name.
     */
    byte LF_GNUTYPE_LONGNAME = (byte) 'L';
    /**
     * The length of the checksum field in a header buffer.
     */
    int CHKSUMLEN = 8;
    /**
     * The length of the devices field in a header buffer.
     */
    int DEVLEN = 8;
    /**
     * The length of the group id field in a header buffer.
     */
    int GIDLEN = 8;
    /**
     * The length of the group name field in a header buffer.
     */
    int GNAMELEN = 32;
    /**
     * The length of the magic field in a header buffer.
     */
    int MAGICLEN = 8;
    /**
     * The length of the mode field in a header buffer.
     */
    int MODELEN = 8;
    /**
     * The length of the modification time field in a header buffer.
     */
    int MODTIMELEN = 12;
    /**
     * The length of the size field in a header buffer.
     */
    int SIZELEN = 12;
    /**
     * The length of the user id field in a header buffer.
     */
    int UIDLEN = 8;
    /**
     * The length of the user name field in a header buffer.
     */
    int UNAMELEN = 32;
}
