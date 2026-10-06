package com.fongmi.quickjs.crawler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;

public class ProxyBodyTest {

    private final Spider spider = new Spider("fixture", null);

    @After
    public void closeExecutor() throws Exception {
        Field executor = Spider.class.getDeclaredField("executor");
        executor.setAccessible(true);
        ((ExecutorService) executor.get(spider)).shutdownNow();
    }

    @Test
    public void acceptsSignedAndUnsignedByteArrays() throws Exception {
        JSONArray body = new JSONArray().put(-128).put(-1).put(0).put(127).put(128).put(255);
        assertArrayEquals(new byte[]{-128, -1, 0, 127, -128, -1}, read(body));
        assertArrayEquals(new byte[]{1, 2}, read(new byte[]{1, 2}));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), read("hello"));
    }

    @Test
    public void rejectsValuesWhichCannotRepresentAByte() {
        for (Object value : new Object[]{-129, 256, 1.5, "1", JSONObject.NULL}) {
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read(new JSONArray().put(value)));
            assertTrue(error.getCause() instanceof IllegalArgumentException);
        }
    }

    private byte[] read(Object body) throws Exception {
        Method method = Spider.class.getDeclaredMethod("getStream", Object.class, boolean.class);
        method.setAccessible(true);
        return ((ByteArrayInputStream) method.invoke(spider, body, false)).readAllBytes();
    }
}
