package com.github.catvod.crawler.diagnostics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Redaction shared by every collector and repeated at the export boundary. */
public final class DiagnosticRedactor {
    public static final String MASK = "[REDACTED]";
    private static final int MAX_INPUT = 32768;
    private static final Pattern URL = Pattern.compile("(?i)(?<![a-z0-9+.-])[a-z][a-z0-9+.-]{0,15}://[^\\s<>\\\"']+");
    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i)password|passwd|pwd|authorization|cookie|token|secret|api[ _-]?key|access[ _-]?key|signature|credential|session[ _-]?id|auth|jwt");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([a-fA-F0-9]{4})");
    private static final Pattern AUTH = Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[a-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[a-zA-Z0-9_-]{8,}\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\b");

    private DiagnosticRedactor() {}

    public static String redact(String input) {
        if (input == null) return "";
        String value = input.length() > MAX_INPUT ? input.substring(0, MAX_INPUT) + " [truncated]" : input;
        Matcher urls = URL.matcher(value);
        StringBuffer result = new StringBuffer();
        while (urls.find()) urls.appendReplacement(result, Matcher.quoteReplacement(redactUrl(urls.group())));
        urls.appendTail(result);
        value = result.toString();
        result = new StringBuffer();
        int copied = 0;
        for (int i = 0; i < value.length();) {
            int start = i;
            char c = value.charAt(i);
            boolean quoted = c == '\"' || c == '\'';
            String key;
            if (quoted) {
                i = quotedEnd(value, i);
                key = value.substring(start + 1, Math.max(start + 1, i - 1));
            } else if (isKeyChar(c)) {
                while (i < value.length() && isKeyChar(value.charAt(i))) i++;
                key = value.substring(start, i);
            } else { i++; continue; }
            int delimiter = i;
            while (delimiter < value.length() && Character.isWhitespace(value.charAt(delimiter))) delimiter++;
            if (delimiter >= value.length() || (value.charAt(delimiter) != ':' && value.charAt(delimiter) != '=')) {
                // A quoted log message may itself contain password=... rather than being a JSON key.
                if (quoted) i = start + 1;
                continue;
            }
            key = decodeKey(key);
            if (!SENSITIVE_KEY.matcher(key).find()) continue;
            int body = delimiter + 1;
            while (body < value.length() && Character.isWhitespace(value.charAt(body))) body++;
            result.append(value, copied, body).append(MASK);
            copied = valueEnd(value, body, key);
            i = copied;
        }
        result.append(value, copied, value.length());
        return JWT.matcher(AUTH.matcher(result).replaceAll(MASK)).replaceAll(MASK);
    }

    private static boolean isKeyChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '%';
    }

    private static String decodeKey(String key) {
        Matcher escapes = UNICODE_ESCAPE.matcher(key);
        StringBuffer result = new StringBuffer();
        while (escapes.find()) escapes.appendReplacement(result, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(escapes.group(1), 16))));
        escapes.appendTail(result);
        return result.toString();
    }

    private static String redactUrl(String url) {
        int scheme = url.indexOf("://") + 3;
        int end = url.length();
        for (char delimiter : new char[]{'/', '?', '#'}) {
            int pos = url.indexOf(delimiter, scheme);
            if (pos >= 0) end = Math.min(end, pos);
        }
        int at = url.lastIndexOf('@', end);
        if (at >= scheme) url = url.substring(0, scheme) + MASK + "@" + url.substring(at + 1);
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int cut = query < 0 ? fragment : fragment < 0 ? query : Math.min(query, fragment);
        if (cut >= 0) url = url.substring(0, cut) + url.charAt(cut) + MASK;
        // Credentials are also commonly passed as named path segments.
        return url.replaceAll("(?i)(/(?:token|secret|password|auth|apikey)/)[^/]+", "$1" + MASK);
    }

    private static int valueEnd(String text, int start, String key) {
        if (start >= text.length()) return start;
        char first = text.charAt(start);
        if (first == '\"' || first == '\'') return quotedEnd(text, start);
        if (first == '{' || first == '[') {
            int depth = 0;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\"' || c == '\'') { i = quotedEnd(text, i) - 1; continue; }
                if (c == '{' || c == '[') depth++;
                if ((c == '}' || c == ']') && --depth == 0) return i + 1;
            }
            return text.length();
        }
        String lowerKey = key.toLowerCase(java.util.Locale.ROOT);
        boolean header = lowerKey.contains("authorization") || lowerKey.contains("cookie");
        int i = start;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                if (header) {
                    int next = i + 1;
                    if (c == '\r' && next < text.length() && text.charAt(next) == '\n') next++;
                    if (next < text.length() && (text.charAt(next) == ' ' || text.charAt(next) == '\t'
                            || text.substring(start, i).trim().matches("(?i)Bearer|Basic"))) {
                        i = next - 1;
                        continue;
                    }
                }
                break;
            }
            if (!header && (Character.isWhitespace(c) || ",;&}]".indexOf(c) >= 0)) break;
        }
        return i;
    }

    private static int quotedEnd(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            if (text.charAt(i) == '\\') i++;
            else if (text.charAt(i) == quote) return i + 1;
        }
        return text.length();
    }
}
