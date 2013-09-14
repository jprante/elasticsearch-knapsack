
package org.xbib.io;

public class ObjectPacket implements Packet<Object> {
    
    private String name;
    private String number;
    private String link;
    private Object object;

    public ObjectPacket() {
    }
    
    public ObjectPacket(String name, String number, String link) {
        setName(name);
        setNumber(number);
        setLink(link);
    }

    public ObjectPacket(String name, String number, String link, Object object) {
        this(name, number, link);
        setPacket(object);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getLink() {
        return link;
    }

    public void setPacket(Object object) {
        this.object = object;
    }
    
    public Object getPacket() {
        return object;
    }

    @Override
    public String toString() {
        return object.toString();
    }
    
    
}
