package com.emma.endinv.folia;

import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.options.config.ComplexConfigEntryImpl;
import com.emma.endinv.options.config.ConfigEntryImpl;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/** Wires {@link ServerConfigs} entries to Bukkit's {@code config.yml}. */
public final class FoliaConfigLoader {

    private static JavaPlugin pluginRef;

    private FoliaConfigLoader() {}

    public static void load(JavaPlugin plugin) {
        pluginRef = plugin;
        FileConfiguration cfg = plugin.getConfig();
        Logger log = plugin.getLogger();
        boolean dirty = false;
        for (ConfigEntryImpl<?> entry : ServerConfigs.getConfigs()) {
            if (entry instanceof ComplexConfigEntryImpl<?> complex) {
                dirty |= wireComplex(cfg, complex, complex.key());
            } else {
                dirty |= wireSimple(cfg, entry, entry.key());
            }
        }
        if (dirty) plugin.saveConfig();
        log.info("Loaded config.yml (%d entries)".formatted(ServerConfigs.getConfigs().size()));
    }

    @SuppressWarnings("unchecked")
    private static boolean wireSimple(FileConfiguration cfg, ConfigEntryImpl<?> entry, String path) {
        boolean wrote = false;
        if (!cfg.contains(path)) {
            cfg.set(path, toYaml(entry.defaultValue()));
            setComments(cfg, path, entry.comments());
            wrote = true;
        }
        if (entry instanceof ConfigEntryImpl.BooleanEntry b) {
            b.initialize(
                    () -> cfg.getBoolean(path, b.defaultValue()),
                    v -> { cfg.set(path, v); save(cfg); });
        } else if (entry instanceof ConfigEntryImpl.EnumEntry<?> e) {
            wireEnum(cfg, path, e);
        } else if (entry instanceof ConfigEntryImpl.IntEntry i) {
            i.initialize(
                    () -> cfg.getInt(path, i.defaultValue()),
                    v -> { cfg.set(path, v); save(cfg); });
        } else if (entry instanceof ConfigEntryImpl.DoubleEntry d) {
            d.initialize(
                    () -> cfg.getDouble(path, d.defaultValue()),
                    v -> { cfg.set(path, v); save(cfg); });
        } else if (entry instanceof ConfigEntryImpl.StringEntry s) {
            s.initialize(
                    () -> cfg.getString(path, s.defaultValue()),
                    v -> { cfg.set(path, v); save(cfg); });
        } else if (entry instanceof ConfigEntryImpl.ListEntry<?> l) {
            wireList(cfg, path, l);
        }
        return wrote;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wireEnum(FileConfiguration cfg, String path, ConfigEntryImpl.EnumEntry e) {
        Enum<?> def = (Enum<?>) e.defaultValue();
        e.initialize(
                () -> {
                    String name = cfg.getString(path, def.name());
                    try { return Enum.valueOf(def.getDeclaringClass(), name); }
                    catch (IllegalArgumentException ex) { return def; }
                },
                v -> { cfg.set(path, ((Enum<?>) v).name()); save(cfg); });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wireList(FileConfiguration cfg, String path, ConfigEntryImpl.ListEntry l) {
        l.initialize(
                () -> cfg.getList(path, (List) l.defaultValue()),
                v -> { cfg.set(path, v); save(cfg); });
    }

    private static boolean wireComplex(FileConfiguration cfg, ComplexConfigEntryImpl<?> complex, String prefix) {
        boolean wrote = false;
        for (ConfigEntryImpl<?> field : complex.fields()) {
            String childPath = prefix + "." + field.key();
            if (field instanceof ComplexConfigEntryImpl<?> nested) {
                wrote |= wireComplex(cfg, nested, childPath);
            } else {
                wrote |= wireSimple(cfg, field, childPath);
            }
        }
        complex.setInitialized();
        return wrote;
    }

    private static Object toYaml(Object value) {
        if (value instanceof Enum<?> e) return e.name();
        return value;
    }

    private static void setComments(FileConfiguration cfg, String path, String[] comments) {
        if (comments != null && comments.length > 0) cfg.setComments(path, List.of(comments));
    }

    private static void save(FileConfiguration cfg) {
        if (pluginRef != null) pluginRef.saveConfig();
    }
}
