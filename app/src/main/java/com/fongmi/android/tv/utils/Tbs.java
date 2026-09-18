package com.fongmi.android.tv.utils;

import android.os.Build;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Path;
import com.orhanobut.logger.Logger;
import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsCommonCode;
import com.tencent.smtt.sdk.TbsDownloader;
import com.tencent.smtt.sdk.TbsListener;

import java.io.File;
import java.util.HashMap;

public class Tbs {

    private static final String TAG = Tbs.class.getSimpleName();

    public interface Callback {

        void onX5Success();

        void onX5Error();

        void onX5Cancel();
    }

    private static boolean isCpu64Bit() {
        for (String abi : Build.SUPPORTED_ABIS) if (abi.contains("64")) return true;
        return false;
    }

    private static String getUrl() {
        File file = new File(Path.tv(), "x5.tbs.apk");
        if (file.exists()) return Server.get().getAddress("/file/TV/x5.tbs.apk");
        return Server.get().getAddress("/x5.tbs.apk");
    }

    public static String url() {
        return getUrl();
    }

    public static File file() {
        return Path.cache("x5.tbs.apk");
    }

    public static void remove() {
        File file = file();
        if (file.exists()) file.delete();
    }

    private static void tbsInit() {
        HashMap<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_PRIVATE_CLASSLOADER, true);
        QbSdk.initTbsSettings(map);
        TbsDownloader.stopDownload();
        QbSdk.PreInitCallback callback = new QbSdk.PreInitCallback() {
            @Override
            public void onViewInitFinished(boolean finished) {
                if (finished) Notify.show(R.string.x5webview_enabled);
            }

            @Override
            public void onCoreInitFinished() {
            }
        };
        QbSdk.initX5Environment(App.get(), callback);
    }

    public static void init() {
        if (Setting.getParseWebView() == 0) return;
        if (QbSdk.isTbsCoreInited()) return;
        App.post(Tbs::tbsInit);
    }

    public static void install(Callback callback) {
        if (QbSdk.canLoadX5(App.get())) return;
        HashMap<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_PRIVATE_CLASSLOADER, true);
        QbSdk.initTbsSettings(map);
        TbsListener listener = new TbsListener() {
            @Override
            public void onDownloadFinish(int stateCode) {
                Logger.t(TAG).d("onDownloadFinish:" + stateCode);
            }

            @Override
            public void onInstallFinish(int stateCode) {
                Logger.t(TAG).d("onInstallFinish:" + stateCode);
                if (stateCode == TbsCommonCode.INSTALL_SUCCESS) callback.onX5Success();
                else callback.onX5Error();
            }

            @Override
            public void onDownloadProgress(int progress) {
                Logger.t(TAG).d("onDownloadProgress:" + progress);
            }
        };
        QbSdk.setTbsListener(listener);
        int version = isCpu64Bit() ? 46279 : 46914;
        QbSdk.reset(App.get());
        QbSdk.installLocalTbsCore(App.get(), version, file().getAbsolutePath());
    }
}