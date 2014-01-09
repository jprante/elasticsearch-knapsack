
package org.xbib.io;

/**
 * A packet for transporting data chunks in sessions
 */
public interface Packet<P> {

    String name();

    Packet<P> name(String name);

    P packet();

    Packet<P> packet(P packet);
}
