package com.fongmi.quickjs.utils;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.Assert.*;

public class AsyncTest {

    @Test
    public void serializesOrdinaryDataObjectOnItsOwningThread() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        JSObject value = object("{\"list\":[{\"vod_id\":\"1\"}]}", releases);
        assertEquals("{\"list\":[{\"vod_id\":\"1\"}]}", Async.runJson(spider(function(args -> value)), "home").get());
        assertEquals(1, releases.get());
    }

    @Test
    public void retainsLegacyStringsAndRawProxyObjects() throws Exception {
        assertEquals("legacy-json", Async.runJson(spider(function(args -> "legacy-json")), "home").get());
        AtomicInteger releases = new AtomicInteger();
        JSObject proxy = object("[200,\"text/plain\",\"body\"]", releases);
        assertSame(proxy, Async.run(spider(function(args -> proxy)), "proxy").get());
        assertEquals(0, releases.get());
        assertEquals(Boolean.TRUE, Async.run(spider(function(args -> true)), "sniffer").get());
    }

    @Test
    public void waitsForPromiseAndSerializesItsResolvedObject() throws Exception {
        AtomicReference<JSCallFunction> success = new AtomicReference<>();
        AtomicReference<JSCallFunction> failure = new AtomicReference<>();
        JSObject promise = promise(success, failure);
        CompletableFuture<Object> result = Async.runJson(spider(function(args -> promise)), "category");
        assertFalse(result.isDone());
        AtomicInteger releases = new AtomicInteger();
        success.get().call(object("{\"page\":2,\"list\":[]}", releases));
        assertEquals("{\"page\":2,\"list\":[]}", result.get());
        assertEquals(1, releases.get());
    }

    @Test
    public void exposesPromiseRejectionInsteadOfReturningEmptyData() {
        AtomicReference<JSCallFunction> success = new AtomicReference<>();
        AtomicReference<JSCallFunction> failure = new AtomicReference<>();
        CompletableFuture<Object> result = Async.runJson(spider(function(args -> promise(success, failure))), "search");
        failure.get().call("HTTP 403");
        ExecutionException error = assertThrows(ExecutionException.class, result::get);
        assertEquals("HTTP 403", error.getCause().getMessage());
    }

    private JSObject spider(JSFunction function) {
        return fake(JSObject.class, (name, args) -> name.equals("getJSFunction") ? function : null);
    }

    private JSObject object(String json, AtomicInteger releases) {
        return fake(JSObject.class, (name, args) -> {
            if (name.equals("stringify")) return json;
            if (name.equals("release")) releases.incrementAndGet();
            return null;
        });
    }

    private JSObject promise(AtomicReference<JSCallFunction> success, AtomicReference<JSCallFunction> failure) {
        return fake(JSObject.class, (name, args) -> {
            if (!name.equals("getJSFunction")) return null;
            AtomicReference<JSCallFunction> target = args[0].equals("then") ? success : failure;
            return function(values -> { target.set((JSCallFunction) values[0]); return null; });
        });
    }

    private JSFunction function(Function<Object[], Object> function) {
        return fake(JSFunction.class, (name, args) -> {
            if (name.equals("call") || name.equals("callVoid")) return function.apply((Object[]) args[0]);
            return null;
        });
    }

    private interface Invocation {
        Object call(String name, Object[] args);
    }

    private <T> T fake(Class<T> type, Invocation invocation) {
        Thread owner = Thread.currentThread();
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            assertSame("QuickJS value used on another thread", owner, Thread.currentThread());
            return invocation.call(method.getName(), args);
        }));
    }
}
