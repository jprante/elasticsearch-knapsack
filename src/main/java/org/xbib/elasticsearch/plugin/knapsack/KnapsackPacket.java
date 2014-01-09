
package org.xbib.elasticsearch.plugin.knapsack;

import org.xbib.io.Packet;
import org.xbib.io.URIUtil;

import java.io.File;

public class KnapsackPacket implements Packet<String> {

    private String name;

    private String packet;

    public KnapsackPacket(String index, String type, String id, String field, String packet) {
        this.name = encodeName(new String[]{index, type, id, field});
        this.packet = packet;
    }

    @Override
    public KnapsackPacket name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public KnapsackPacket packet(String packet) {
        this.packet = packet;
        return this;
    }

    @Override
    public String packet() {
        return packet;
    }

    @Override
    public String toString() {
        return packet;
    }

    public static String encodeName(String[] components) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                sb.append(File.separator);
            }
            sb.append(URIUtil.encode(components[i] != null ? components[i] : "", URIUtil.UTF8));
        }
        return sb.toString();
    }

    public static String[] decodeName(String component) {
        String[] components = component.split(File.separator);
        for (int i = 0; i < components.length; i++) {
            components[i] = URIUtil.decode(components[i] != null ? components[i] : "", URIUtil.UTF8);
        }
        return components;
    }
}
