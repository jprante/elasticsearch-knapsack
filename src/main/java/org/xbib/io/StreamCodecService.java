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
package org.xbib.io;

import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

public class StreamCodecService {

    private final static Map<String, StreamCodec> codecs = new WeakHashMap();
    private final static StreamCodecService instance = new StreamCodecService();

    private StreamCodecService() {
        ServiceLoader<StreamCodec> loader = ServiceLoader.load(StreamCodec.class);
        Iterator<StreamCodec> iterator = loader.iterator();
        while (iterator.hasNext()) {
            StreamCodec codec = iterator.next();
            if (!codecs.containsKey(codec.getName())) {
                codecs.put(codec.getName(), codec);
            }
        }
    }

    public static StreamCodecService getInstance() {
        return instance;
    }

    public StreamCodec getCodec(String suffix) {
        if (codecs.containsKey(suffix)) {
            return codecs.get(suffix);
        }
        throw new IllegalArgumentException("Stream codec for " + suffix + " not found in " + codecs);
    }
}
