package com.emma.endinv.options.config;

import com.emma.endinv.ModInfo;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Config file paths ({@code <mod_id>-<suffix>}). The first time a file is missing but one exists
 * under the upstream mod id ({@code endless_inventory-<suffix>}), it is carried over, with
 * {@code endless_inventory:} ids (menus, pages) rewritten to the new namespace. The old file is
 * kept as {@code .migrated}.
 */
public final class ConfigFiles {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfigFiles() {}

    public static Path resolve(Path dir, String suffix) {
        Path file = dir.resolve(ModInfo.MOD_ID + "-" + suffix);
        Path legacy = dir.resolve(ModInfo.LEGACY_ID + "-" + suffix);
        if (!Files.exists(file) && Files.exists(legacy)) {
            try {
                String text = Files.readString(legacy, StandardCharsets.UTF_8)
                        .replace(ModInfo.LEGACY_ID + ":", ModInfo.MOD_ID + ":");
                Files.writeString(file, text, StandardCharsets.UTF_8);
                Files.move(legacy, legacy.resolveSibling(legacy.getFileName() + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Migrated config {} -> {}", legacy.getFileName(), file.getFileName());
            } catch (IOException e) {
                LOGGER.warn("Could not migrate config {}; using defaults", legacy, e);
            }
        }
        return file;
    }
}
