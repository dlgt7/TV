package com.fongmi.android.tv.drive;

import static org.junit.Assert.*;
import org.junit.Test;

public class DriveLinkTest {
    @Test public void extractsOnePastedShareWithoutReturningCredentials() {
        DriveLink link = DriveLink.parse("我分享了文件 https://115.com/s/abcd1234?password=H7k2&token=secret 提取码：H7k2");
        assertEquals("115", link.provider);
        assertEquals("H7k2", link.password);
        assertEquals("https://115cdn.com/s/abcd1234", link.url);
        assertEquals("z7Q2", DriveLink.parse("https://pan.quark.cn/s/abcd1234 提取码：z7Q2").password);
        assertEquals("https://www.alipan.com/s/abcd1234", DriveLink.parse("https://www.aliyundrive.com/s/abcd1234").url);
    }

    @Test public void rejectsAmbiguousOrUntrustedDestinations() {
        for (String text : new String[]{
                "https://pan.quark.cn.evil.test/s/abcd1234", "http://127.0.0.1/s/abcd1234",
                "https://user:password@pan.quark.cn/s/abcd1234", "https://pan.quark.cn:444/s/abcd1234",
                "https://pan.quark.cn/s/abcd1234/other", "https://pan.quark.cn/s/abc%2f1234",
                "https://pan.quark.cn/s/abcd1234 https://115.com/s/efgh5678 提取码:1234"}) {
            assertNull(text, DriveLink.parse(text));
        }
    }

    @Test public void rejectsOverlongInputAndDoesNotTreatTokenAsExtractionCode() {
        assertNull(DriveLink.parse(new String(new char[4097])));
        DriveLink link = DriveLink.parse("https://pan.quark.cn/s/abcd1234?token=passwordsecret#access_token=private");
        assertEquals("", link.password);
        assertFalse(link.url.contains("private"));
    }
}
