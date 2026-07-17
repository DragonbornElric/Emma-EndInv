package com.emma.endinv.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

public interface ILoaderProvider {

    boolean isClient();

    boolean isModLoaded(String modId);

    Path getConfigDir();

    static ILoaderProvider get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        static final ILoaderProvider INSTANCE = ServiceLoader
                .load(ILoaderProvider.class, ILoaderProvider.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ILoaderProvider service found"));
    }
}
