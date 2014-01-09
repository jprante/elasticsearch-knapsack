
package org.xbib.io;

public class ObjectPacket implements Packet<Object> {

    private String name;

    private Object object;

    public ObjectPacket() {
    }

    public ObjectPacket name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return name;
    }

    public ObjectPacket packet(Object object) {
        this.object = object;
        return this;
    }

    public Object packet() {
        return object;
    }

    @Override
    public String toString() {
        return object.toString();
    }

}
