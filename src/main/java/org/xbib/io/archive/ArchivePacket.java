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
package org.xbib.io.archive;

import org.xbib.io.Packet;

import java.util.HashMap;
import java.util.Map;

/**
 * The information packet for the archive session
 */
public class ArchivePacket implements Packet<Object> {

    /**
     * Meta data for the payload
     */
    private Map<String,Object> meta =  new HashMap<String,Object>();

    /**
     * The payload
     */
    private Object payload;

    public ArchivePacket() {
    }

    @Override
    public ArchivePacket meta(Map<String,Object> meta) {
        this.meta = meta;
        return this;
    }

    @Override
    public ArchivePacket meta(String key, Object value) {
        meta.put(key, value);
        return this;
    }

    @Override
    public Map<String,Object> meta() {
        return meta;
    }

    @Override
    public ArchivePacket payload(Object payload) {
        this.payload = payload;
        return this;
    }

    @Override
    public Object payload() {
        return payload;
    }

}
