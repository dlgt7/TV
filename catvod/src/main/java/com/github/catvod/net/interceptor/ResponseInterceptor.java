package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

public class ResponseInterceptor implements Interceptor {

    private final Map<String, String> redirect;

    public ResponseInterceptor() {
        this.redirect = new HashMap<>();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(checkUser(request));
        if ("deflate".equals(response.header(HttpHeaders.CONTENT_ENCODING))) return deflate(response);
        if (response.code() == 302) redirect.put(response.header(HttpHeaders.LOCATION), request.url().toString());
        if (response.code() == 406 && redirect.containsKey(request.url().toString())) return redirect(request, response);
        return response;
    }

    private Request checkUser(Request request) {
        URI uri = request.url().uri();
        if (uri.getUserInfo() == null) return request;
        return request.newBuilder().header(HttpHeaders.AUTHORIZATION, Util.basic(uri.getUserInfo())).build();
    }

    private Response redirect(Request request, Response response) {
        return new Response.Builder().request(request).protocol(response.protocol()).code(302).message("Found").header(HttpHeaders.LOCATION, redirect.get(request.url().toString())).build();
    }

    private Response deflate(Response response) {
        InflaterInputStream is = new InflaterInputStream(response.body().byteStream(), new Inflater(true));
        return response.newBuilder().headers(response.headers()).body(new ResponseBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return response.body().contentType();
            }

            @Override
            public long contentLength() {
                return response.body().contentLength();
            }

            @NonNull
            @Override
            public BufferedSource source() {
                return Okio.buffer(Okio.source(is));
            }
        }).build();
    }
}
