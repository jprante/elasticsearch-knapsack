
package org.elasticsearch.node;

import org.elasticsearch.Version;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.plugins.Plugin;

import java.util.Collection;

public class MockNode extends Node {

    public MockNode(Environment tmpEnv, Collection<Class<? extends Plugin>> classpathPlugins) {
        super(tmpEnv, Version.CURRENT, classpathPlugins);
    }

}
