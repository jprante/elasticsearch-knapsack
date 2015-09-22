package org.xbib.elasticsearch.support.client;

import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class ConfigHelper {

    private Settings.Builder settingsBuilder;

    private Settings settings;

    private Map<String, String> mappings = new HashMap<>();

    public ConfigHelper reset() {
        settingsBuilder = Settings.settingsBuilder();
        settings = null;
        mappings = new HashMap<>();
        return this;
    }

    public ConfigHelper settings(Settings settings) {
        this.settings = settings;
        return this;
    }

    public ConfigHelper setting(String key, String value) {
        if (settingsBuilder == null) {
            settingsBuilder = Settings.settingsBuilder();
        }
        settingsBuilder.put(key, value);
        return this;
    }

    public ConfigHelper setting(String key, Boolean value) {
        if (settingsBuilder == null) {
            settingsBuilder = Settings.settingsBuilder();
        }
        settingsBuilder.put(key, value);
        return this;
    }

    public ConfigHelper setting(String key, Integer value) {
        if (settingsBuilder == null) {
            settingsBuilder = Settings.settingsBuilder();
        }
        settingsBuilder.put(key, value);
        return this;
    }

    public ConfigHelper setting(InputStream in) throws IOException {
        settingsBuilder = Settings.settingsBuilder().loadFromStream(".json", in);
        return this;
    }

    public Settings.Builder settingsBuilder() {
        return settingsBuilder != null ? settingsBuilder : Settings.settingsBuilder();
    }

    public Settings settings() {
        if (settings != null) {
            return settings;
        }
        if (settingsBuilder == null) {
            settingsBuilder = Settings.settingsBuilder();
        }
        return settingsBuilder.build();
    }

    public ConfigHelper mapping(String type, String mapping) throws IOException {
        mappings.put(type, mapping);
        return this;
    }

    public ConfigHelper mapping(String type, InputStream in) throws IOException {
        if (type == null) {
            return this;
        }
        StringWriter sw = new StringWriter();
        Streams.copy(new InputStreamReader(in), sw);
        mappings.put(type, sw.toString());
        return this;
    }

    public Map<String, String> mappings() {
        return mappings.isEmpty() ? null : mappings;
    }

}
