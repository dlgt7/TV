package com.github.catvod;

import android.content.Context;
import android.app.Activity;
import java.util.function.Consumer;
import java.util.function.Supplier;

import java.lang.ref.WeakReference;

public class Init {

    private volatile WeakReference<Context> context;
    private volatile Supplier<Activity> activity;
    private volatile Consumer<String> toast;

    private static Init get() {
        return Loader.INSTANCE;
    }

    public static void set(Context context) {
        get().context = new WeakReference<>(context);
    }

    public static Context context() {
        WeakReference<Context> reference = get().context;
        return reference == null ? null : reference.get();
    }

    public static void setActivity(Supplier<Activity> supplier) {
        get().activity = supplier;
    }

    public static Activity activity() {
        Supplier<Activity> supplier = get().activity;
        Activity value = supplier == null ? null : supplier.get();
        return value == null || value.isFinishing() || value.isDestroyed() ? null : value;
    }

    public static void setToast(Consumer<String> consumer) {
        get().toast = consumer;
    }

    public static void toast(String text) {
        Consumer<String> consumer = get().toast;
        if (consumer != null) consumer.accept(text);
    }

    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }
}
