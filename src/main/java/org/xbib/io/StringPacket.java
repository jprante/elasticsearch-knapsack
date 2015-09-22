
package org.xbib.io;

import java.util.HashMap;
import java.util.Map;

public class StringPacket implements Packet<String> {

    private Map<String, Object> meta = new HashMap<>();
    private String name;
    private String string;

    public StringPacket() {
    }

    public StringPacket name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return name;
    }

    public StringPacket packet(String string) {
        this.string = string;
        return this;
    }

    @Override
    public Map<String, Object> meta() {
        return meta;
    }

    @Override
    public Packet<String> meta(String key, Object value) {
        meta.put(key, value);
        return this;
    }

    public String payload() {
        return string;
    }

    @Override
    public Packet<String> payload(String packet) {
        this.string = packet;
        return this;
    }

    @Override
    public String toString() {
        return string;
    }


}
