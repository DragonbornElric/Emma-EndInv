package com.emma.endinv.folia;

import com.emma.endinv.platform.ILoaderProvider;
import org.bukkit.Bukkit;

import java.nio.file.Path;

public final class FoliaLoaderProvider implements ILoaderProvider {

    private static Path configDir;

    static void setConfigDir(Path path) {
        configDir = path;
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return Bukkit.getPluginManager().getPlugin(modId) != null;
    }

    @Override
    public Path getConfigDir() {
        return configDir;
    }
}
