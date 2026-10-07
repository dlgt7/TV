package com.fongmi.android.tv.drive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

final class DriveLink {

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"'，。；（）【】]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD = Pattern.compile("(?:提取码|访问码|密码|passcode|password)\\s*[:：=]?\\s*([A-Za-z0-9]{4,8})(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    final String provider;
    final String id;
    final String password;
    final String url;

    private DriveLink(String provider, String id, String password, String url) {
        this.provider = provider;
        this.id = id;
        this.password = password;
        this.url = url;
    }

    static DriveLink parse(String text) {
        if (text == null || text.length() > 4096) return null;
        Matcher matcher = URL.matcher(text);
        if (!matcher.find()) return null;
        String prose = text.substring(0, matcher.start()) + " " + text.substring(matcher.end());
        String raw = matcher.group().replaceAll("[),.;]+$", "");
        // Multiple links are ambiguous: never send one link's extraction code to another.
        if (matcher.find()) return null;
        HttpUrl parsed = HttpUrl.parse(raw);
        if (parsed == null || !parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.port() != (parsed.isHttps() ? 443 : 80)) return null;
        String host = parsed.host();
        String provider;
        String canonicalHost;
        switch (host) {
            case "pan.quark.cn": provider = "夸克"; canonicalHost = host; break;
            case "www.alipan.com": case "alipan.com": case "www.aliyundrive.com": case "aliyundrive.com":
                provider = "阿里云盘"; canonicalHost = "www.alipan.com"; break;
            case "115.com": case "www.115.com": case "115cdn.com": case "www.115cdn.com":
                provider = "115"; canonicalHost = "115cdn.com"; break;
            default: return null;
        }
        Matcher share = Pattern.compile("^/s/([A-Za-z0-9]{4,64})/?$").matcher(parsed.encodedPath());
        if (!share.matches()) return null;
        String id = share.group(1);
        String password = "";
        for (String key : new String[]{"password", "pwd", "passcode", "receive_code"}) {
            String candidate = parsed.queryParameter(key);
            if (candidate != null && candidate.matches("[A-Za-z0-9]{4,8}")) { password = candidate; break; }
        }
        if (password.isEmpty()) {
            Matcher code = PASSWORD.matcher(prose);
            if (code.find()) password = code.group(1);
        }
        return new DriveLink(provider, id, password, "https://" + canonicalHost + "/s/" + id);
    }
}
