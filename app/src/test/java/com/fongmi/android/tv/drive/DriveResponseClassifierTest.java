package com.fongmi.android.tv.drive;

import static com.fongmi.android.tv.drive.DriveCheckResult.Status.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class DriveResponseClassifierTest {
    @Test public void quarkUsesExplicitProviderCodesAndToken() {
        assertEquals(OK, DriveResponseClassifier.classify("夸克", 200, "{\"code\":0,\"data\":{\"stoken\":\"fixture\"}}"));
        assertEquals(LOCKED, DriveResponseClassifier.classify("夸克", 200, "{\"code\":41008}"));
        for (int code : new int[]{41004, 41010, 41011}) assertEquals(BAD, DriveResponseClassifier.classify("夸克", 200, "{\"code\":" + code + "}"));
        assertEquals(UNCERTAIN, DriveResponseClassifier.classify("夸克", 200, "{\"code\":500,\"message\":\"参数错误，分享不存在？\"}"));
        assertEquals(UNCERTAIN, DriveResponseClassifier.classify("夸克", 200, "{\"data\":{\"stoken\":\"fixture\"}}"));
    }

    @Test public void aliDistinguishesPasswordAndExplicitRevocationFromGenericForbidden() {
        assertEquals(OK, DriveResponseClassifier.classify("阿里云盘", 200, "{\"share_token\":\"fixture\"}"));
        assertEquals(BAD, DriveResponseClassifier.classify("阿里云盘", 404, "{\"code\":\"NotFound.ShareLink\"}"));
        assertEquals(LOCKED, DriveResponseClassifier.classify("阿里云盘", 400, "{\"code\":\"ShareLinkPwdInvalid\"}"));
        for (String code : new String[]{"Forbidden", "Forbidden.ShareLink", "ShareLink.RateLimit", "AccessTokenInvalid", "ShareLink.Unknown"}) {
            assertEquals(UNCERTAIN, DriveResponseClassifier.classify("阿里云盘", 400, "{\"code\":\"" + code + "\"}"));
        }
    }

    @Test public void drive115ExpiredStateWinsOverLeftoverFileList() {
        assertEquals(BAD, DriveResponseClassifier.classify("115", 200, "{\"state\":true,\"errno\":0,\"data\":{\"share_state\":7,\"list\":[{}]}}"));
        assertEquals(OK, DriveResponseClassifier.classify("115", 200, "{\"state\":true,\"data\":{\"share_state\":1}}"));
        assertEquals(LOCKED, DriveResponseClassifier.classify("115", 200, "{\"state\":false,\"error\":\"提取码错误\"}"));
        assertEquals(UNCERTAIN, DriveResponseClassifier.classify("115", 200, "{\"state\":false,\"error\":\"请登录后重试\"}"));
        assertEquals(UNCERTAIN, DriveResponseClassifier.classify("115", 200, "{\"state\":true,\"data\":{\"list\":[]}}"));
        assertEquals(UNCERTAIN, DriveResponseClassifier.classify("115", 200, "{\"state\":true,\"data\":{\"shareinfo\":{\"forbid_reason\":\"账号异常\"},\"list\":[{}]}}"));
    }

    @Test public void malformedAuthRateLimitAndServerErrorsStayUncertain() {
        for (String provider : new String[]{"夸克", "阿里云盘", "115"}) {
            for (String body : new String[]{"", "<html>风控</html>", "[]", "null", "{}", "{\"code\":{}}"})
                assertEquals(UNCERTAIN, DriveResponseClassifier.classify(provider, 200, body));
            for (int code : new int[]{301, 401, 403, 429, 500, 503})
                assertEquals(UNCERTAIN, DriveResponseClassifier.classify(provider, code, "{\"code\":41004,\"state\":false,\"error\":\"分享已取消\"}"));
        }
    }
}
