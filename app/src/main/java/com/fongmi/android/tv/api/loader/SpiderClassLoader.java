package com.fongmi.android.tv.api.loader;

import dalvik.system.DexClassLoader;

/** Preserve helpers bundled by old plugins when the host adds SDK helpers of the same name. */
final class SpiderClassLoader extends DexClassLoader {
    private static final String[] PLUGIN_HELPERS = {
            "com.github.catvod.bean.Result", "com.github.catvod.bean.Vod",
            "com.github.catvod.bean.Class", "com.github.catvod.bean.Filter",
            "com.github.catvod.bean.Sub", "com.github.catvod.bean.Danmaku",
            "com.github.catvod.utils.Crypto"
    };

    SpiderClassLoader(String dex, String optimized, String libraries, ClassLoader parent) {
        super(dex, optimized, libraries, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!isPluginHelper(name)) return super.loadClass(name, resolve);
        // Android's public ClassLoader API does not expose the JDK per-name lock.
        synchronized (this) {
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                try { result = findClass(name); }
                catch (ClassNotFoundException absent) { result = super.loadClass(name, false); }
            }
            if (resolve) resolveClass(result);
            return result;
        }
    }

    private static boolean isPluginHelper(String name) {
        for (String helper : PLUGIN_HELPERS)
            if (name.equals(helper) || name.startsWith(helper + "$")) return true;
        return false;
    }
}
