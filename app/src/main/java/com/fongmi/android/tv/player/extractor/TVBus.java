package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Core;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;
import com.tvbus.engine.Listener;
import com.tvbus.engine.TVCore;

import java.io.File;

public class TVBus implements Source.Extractor, Listener {

    private TVCore tvcore;
    private String hls;
    private Core core;

    @Override
    public boolean match(String scheme, String host) {
        return "tvbus".equals(scheme);
    }

    private void init(Core core) {
        App.get().setHook(core.getHook());
        tvcore = new TVCore(getPath(core.getSo())).listener(this);
        tvcore.auth(core.getAuth()).name(core.getName()).pass(core.getPass());
        tvcore.domain(core.getDomain()).broker(core.getBroker()).serv(0).play(8902).mode(1);
        tvcore.init();
    }

    private String getPath(String url) {
        try {
            File file = new File(Path.so(), Uri.parse(url).getLastPathSegment());
            if (file.length() < 10240) Path.write(file, OkHttp.newCall(url).execute().body().bytes());
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String fetch(String url) throws Exception {
        if (core != null && !core.equals(LiveConfig.get().getHome().getCore())) change();
        if (tvcore == null) init(core = LiveConfig.get().getHome().getCore());
        App.get().setHook(null);
        tvcore.start(url);
        onWait();
        onCheck();
        return hls;
    }

    private void onCheck() throws Exception {
        if (hls.startsWith("-")) throw new ExtractException(ResUtil.getString(R.string.error_play_code, hls));
    }

    private void onWait() throws InterruptedException {
        synchronized (this) {
            wait();
        }
    }

    private void onNotify() {
        synchronized (this) {
            notify();
        }
    }

    private void change() {
        Setting.putBootLive(true);
        App.post(() -> System.exit(0), 250);
    }

    @Override
    public void stop() {
        if (tvcore != null) tvcore.stop();
        if (hls != null) hls = null;
    }

    @Override
    public void exit() {
        if (tvcore != null) tvcore.quit();
        tvcore = null;
    }

    @Override
    public void onPrepared(String result) {
        JsonObject json = App.gson().fromJson(result, JsonObject.class);
        if (json.get("hls") == null) return;
        hls = json.get("hls").getAsString();
        onNotify();
    }

    @Override
    public void onStop(String result) {
        JsonObject json = App.gson().fromJson(result, JsonObject.class);
        hls = json.get("errno").getAsString();
        if (hls.startsWith("-")) onNotify();
    }

    @Override
    public void onInited(String result) {
    }

    @Override
    public void onStart(String result) {
    }

    @Override
    public void onInfo(String result) {
    }

    @Override
    public void onQuit(String result) {
    }
}
