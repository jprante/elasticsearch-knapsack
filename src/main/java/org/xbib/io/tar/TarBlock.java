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

import java.net.URI;

/**
 * A TAR block consists of a header and and input stream for the data
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarBlock {

    private TarBlockHeader blockHeader;
    private URI uri;
    private byte[] block;

    public TarBlock(URI uri, TarBlockHeader blockHeader, byte[] block) {
        this.uri = uri;
        this.blockHeader = blockHeader;
        this.block = block;
    }

    public byte[] getBlock() {
        return block;
    }

    public TarBlockHeader getHeader() {
        return blockHeader;
    }

    public URI getTarBlockURI() {
        return uri;
    }
}
