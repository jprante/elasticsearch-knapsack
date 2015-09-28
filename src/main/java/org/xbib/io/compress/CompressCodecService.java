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
package org.xbib.io.compress;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;

public class CompressCodecService {

    private final static Map<String, CompressCodec> codecs = new WeakHashMap<String, CompressCodec>();

    private final static CompressCodecService instance = new CompressCodecService();

    private CompressCodecService() {
        ServiceLoader<CompressCodec> loader = ServiceLoader.load(CompressCodec.class, getClass().getClassLoader());
        for (CompressCodec codec : loader) {
            if (!codecs.containsKey(codec.getName())) {
                codecs.put(codec.getName(), codec);
            }
        }
    }

    public static CompressCodecService getInstance() {
        return instance;
    }

    public CompressCodec getCodec(String suffix) {
        if (codecs.containsKey(suffix)) {
            return codecs.get(suffix);
        }
        throw new IllegalArgumentException("Stream codec for " + suffix + " not found in " + codecs);
    }

    public static Set<String> getCodecs() {
        return codecs.keySet();
    }
}
