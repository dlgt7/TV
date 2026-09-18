package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.LanguageUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

public class Setting {

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 4;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;
    private static final int MIN_QUALITY = 0;
    private static final int MAX_QUALITY = 2;
    private static final int MIN_EPISODE = 0;
    private static final int MAX_EPISODE = 1;
    private static final int MIN_MENU_KEY = 0;
    private static final int MAX_FULLSCREEN_MENU_KEY = 1;
    private static final int MAX_SMALL_WINDOW_BACK_KEY = 1;
    private static final int MAX_HOME_MENU_KEY = 8;
    private static final int MIN_HOME_UI = 0;
    private static final int MAX_HOME_UI = 1;
    private static final int MIN_BACKUP_MODE = 0;
    private static final int MAX_BACKUP_MODE = 1;
    private static final int MIN_CONFIG_CACHE = 0;
    private static final int MAX_CONFIG_CACHE = 2;
    private static final int MIN_PARSE_WEB_VIEW = 0;
    private static final int MAX_PARSE_WEB_VIEW = 1;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static int getWall() {
        return Math.clamp(Prefers.getInt("wall", 1), MIN_WALL, MAX_WALL);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", Math.clamp(wall, MIN_WALL, MAX_WALL));
    }

    public static int getWallType() {
        return Math.clamp(Prefers.getInt("wall_type", 0), MIN_WALL_TYPE, MAX_WALL_TYPE);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", Math.clamp(type, MIN_WALL_TYPE, MAX_WALL_TYPE));
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static int getSiteMode() {
        return Math.clamp(Prefers.getInt("site_mode"), MIN_SITE_MODE, MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.clamp(mode, MIN_SITE_MODE, MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.clamp(Prefers.getInt("sync_mode"), MIN_SYNC_MODE, MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.clamp(mode, MIN_SYNC_MODE, MAX_SYNC_MODE));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static String getProxy() {
        return Prefers.getString("proxy");
    }

    public static void putProxy(String proxy) {
        Prefers.put("proxy", proxy);
    }

    public static int getBackupMode() {
        return Math.clamp(Prefers.getInt("backup_mode", 1), MIN_BACKUP_MODE, MAX_BACKUP_MODE);
    }

    public static void putBackupMode(int mode) {
        Prefers.put("backup_mode", Math.clamp(mode, MIN_BACKUP_MODE, MAX_BACKUP_MODE));
    }

    public static int getDanmuSpeed() {
        return Math.clamp(Prefers.getInt("danmu_speed", 2), 0, 3);
    }

    public static void putDanmuSpeed(int speed) {
        Prefers.put("danmu_speed", Math.clamp(speed, 0, 3));
    }

    public static int getQuality() {
        return Math.clamp(Prefers.getInt("quality", 2), MIN_QUALITY, MAX_QUALITY);
    }

    public static void putQuality(int quality) {
        Prefers.put("quality", Math.clamp(quality, MIN_QUALITY, MAX_QUALITY));
    }

    public static int getEpisode() {
        return Math.clamp(Prefers.getInt("episode"), MIN_EPISODE, MAX_EPISODE);
    }

    public static void putEpisode(int episode) {
        Prefers.put("episode", Math.clamp(episode, MIN_EPISODE, MAX_EPISODE));
    }

    public static boolean isDisplayTime() {
        return Prefers.getBoolean("display_time");
    }

    public static void putDisplayTime(boolean display) {
        Prefers.put("display_time", display);
    }

    public static boolean isDisplaySpeed() {
        return Prefers.getBoolean("display_speed");
    }

    public static void putDisplaySpeed(boolean display) {
        Prefers.put("display_speed", display);
    }

    public static boolean isDisplayDuration() {
        return Prefers.getBoolean("display_duration");
    }

    public static void putDisplayDuration(boolean display) {
        Prefers.put("display_duration", display);
    }

    public static boolean isDisplayMiniProgress() {
        return Prefers.getBoolean("display_mini_progress");
    }

    public static void putDisplayMiniProgress(boolean display) {
        Prefers.put("display_mini_progress", display);
    }

    public static boolean isDisplayVideoTitle() {
        return Prefers.getBoolean("display_video_title");
    }

    public static void putDisplayVideoTitle(boolean display) {
        Prefers.put("display_video_title", display);
    }

    public static int getFullscreenMenuKey() {
        return Math.clamp(Prefers.getInt("fullscreen_menu_key"), MIN_MENU_KEY, MAX_FULLSCREEN_MENU_KEY);
    }

    public static void putFullscreenMenuKey(int key) {
        Prefers.put("fullscreen_menu_key", Math.clamp(key, MIN_MENU_KEY, MAX_FULLSCREEN_MENU_KEY));
    }

    public static int getSmallWindowBackKey() {
        return Math.clamp(Prefers.getInt("small_window_back_key"), MIN_MENU_KEY, MAX_SMALL_WINDOW_BACK_KEY);
    }

    public static void putSmallWindowBackKey(int key) {
        Prefers.put("small_window_back_key", Math.clamp(key, MIN_MENU_KEY, MAX_SMALL_WINDOW_BACK_KEY));
    }

    public static int getHomeMenuKey() {
        return Math.clamp(Prefers.getInt("home_menu_key"), MIN_MENU_KEY, MAX_HOME_MENU_KEY);
    }

    public static void putHomeMenuKey(int key) {
        Prefers.put("home_menu_key", Math.clamp(key, MIN_MENU_KEY, MAX_HOME_MENU_KEY));
    }

    public static boolean isHomeSiteLock() {
        return Prefers.getBoolean("home_site_lock");
    }

    public static void putHomeSiteLock(boolean lock) {
        Prefers.put("home_site_lock", lock);
    }

    public static boolean isAggregatedSearch() {
        return Prefers.getBoolean("aggregated_search");
    }

    public static void putAggregatedSearch(boolean search) {
        Prefers.put("aggregated_search", search);
    }

    public static int getHomeUI() {
        return Math.clamp(Prefers.getInt("home_ui", 1), MIN_HOME_UI, MAX_HOME_UI);
    }

    public static void putHomeUI(int ui) {
        Prefers.put("home_ui", Math.clamp(ui, MIN_HOME_UI, MAX_HOME_UI));
    }

    public static String getHomeButtons(String defaultValue) {
        return Prefers.getString("home_buttons", defaultValue);
    }

    public static void putHomeButtons(String buttons) {
        Prefers.put("home_buttons", buttons);
    }

    public static String getHomeButtonsSorted(String defaultValue) {
        return Prefers.getString("home_buttons_sorted", defaultValue);
    }

    public static void putHomeButtonsSorted(String buttons) {
        Prefers.put("home_buttons_sorted", buttons);
    }

    public static boolean isHomeHistory() {
        return Prefers.getBoolean("home_history", true);
    }

    public static void putHomeHistory(boolean show) {
        Prefers.put("home_history", show);
    }

    public static int getConfigCache() {
        return Math.clamp(Prefers.getInt("config_cache"), MIN_CONFIG_CACHE, MAX_CONFIG_CACHE);
    }

    public static void putConfigCache(int cache) {
        Prefers.put("config_cache", Math.clamp(cache, MIN_CONFIG_CACHE, MAX_CONFIG_CACHE));
    }

    public static int getLanguage() {
        return Prefers.getInt("language", LanguageUtil.locale());
    }

    public static void putLanguage(int language) {
        Prefers.put("language", language);
    }

    public static int getParseWebView() {
        return Math.clamp(Prefers.getInt("parse_web_view"), MIN_PARSE_WEB_VIEW, MAX_PARSE_WEB_VIEW);
    }

    public static void putParseWebView(int webview) {
        Prefers.put("parse_web_view", Math.clamp(webview, MIN_PARSE_WEB_VIEW, MAX_PARSE_WEB_VIEW));
    }

    public static String getThunderCacheDir() {
        return Prefers.getString("thunder_cache_dir", "");
    }

    public static void putThunderCacheDir(String dir) {
        Prefers.put("thunder_cache_dir", dir);
    }

    public static float getThumbnail() {
        return 0.3f * getQuality() + 0.4f;
    }
}
