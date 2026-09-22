package com.fongmi.android.tv.storage;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SmbDiscover {

    private static final int PORT = 445;
    private static final int TIMEOUT_MS = 300;
    private static final int MAX_HOSTS = 254;

    private final CopyOnWriteArrayList<Future<?>> futures = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Listener listener;

    public interface Listener {
        void onProgress(int done, int total);

        void onComplete(List<Host> hosts);
    }

    public static class Host {
        private final String ip;
        private final String name;

        public Host(String ip, String name) {
            this.ip = ip;
            this.name = name == null ? "" : name;
        }

        public String getIp() {
            return ip;
        }

        public String getName() {
            return name;
        }

        public String display() {
            if (TextUtils.isEmpty(name) || name.equals(ip)) return ip;
            return name + " (" + ip + ")";
        }
    }

    public SmbDiscover(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Task.execute(this::run);
    }

    public void stop() {
        running.set(false);
        listener = null;
        futures.forEach(f -> f.cancel(true));
        futures.clear();
    }

    private void run() {
        String local = Util.getIp();
        List<String> targets = buildTargets(local);
        if (targets.isEmpty()) {
            finish(Collections.emptyList());
            return;
        }
        List<Host> found = new CopyOnWriteArrayList<>();
        AtomicInteger done = new AtomicInteger();
        int total = targets.size();
        for (String ip : targets) {
            futures.add(Task.submitLarge(() -> {
                if (!running.get()) return;
                if (isOpen(ip)) found.add(new Host(ip, ""));
                int current = done.incrementAndGet();
                if (current % 16 == 0 || current == total) {
                    App.post(() -> {
                        Listener callback = listener;
                        if (running.get() && callback != null) callback.onProgress(current, total);
                    });
                }
            }));
        }
        for (Future<?> job : futures) {
            try {
                job.get();
            } catch (Exception ignored) {
            }
        }
        futures.clear();
        List<Host> result = new ArrayList<>(found);
        Collections.sort(result, (a, b) -> a.getIp().compareTo(b.getIp()));
        finish(result);
    }

    private void finish(List<Host> hosts) {
        running.set(false);
        App.post(() -> {
            if (listener != null) listener.onComplete(hosts);
        });
    }

    private static boolean isOpen(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, PORT), TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> buildTargets(String local) {
        if (TextUtils.isEmpty(local)) return Collections.emptyList();
        try {
            InetAddress address = InetAddress.getByName(local);
            if (!(address instanceof Inet4Address) || (!address.isSiteLocalAddress() && !address.isLinkLocalAddress())) {
                return Collections.emptyList();
            }
            int prefix = activePrefix(address);
            // Never turn discovery into a broad subnet scanner; larger LANs are limited to the local /24.
            prefix = Math.max(24, Math.min(30, prefix));
            byte[] bytes = address.getAddress();
            int value = ((bytes[0] & 255) << 24) | ((bytes[1] & 255) << 16) | ((bytes[2] & 255) << 8) | (bytes[3] & 255);
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            int network = value & mask;
            int broadcast = network | ~mask;
            List<String> targets = new ArrayList<>();
            for (int candidate = network + 1; candidate < broadcast && targets.size() < MAX_HOSTS; candidate++) {
                if (candidate == value) continue;
                targets.add(String.format(java.util.Locale.US, "%d.%d.%d.%d",
                        candidate >>> 24 & 255, candidate >>> 16 & 255, candidate >>> 8 & 255, candidate & 255));
            }
            return targets;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static int activePrefix(InetAddress local) {
        try {
            ConnectivityManager manager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
            LinkProperties properties = manager == null ? null : manager.getLinkProperties(manager.getActiveNetwork());
            if (properties != null) {
                for (LinkAddress link : properties.getLinkAddresses()) {
                    if (local.equals(link.getAddress())) return link.getPrefixLength();
                }
            }
        } catch (Exception ignored) {
        }
        return 24;
    }
}
