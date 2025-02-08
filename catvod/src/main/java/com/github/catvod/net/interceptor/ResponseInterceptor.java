package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URI;
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

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(checkUser(chain));
        String encoding = response.header(HttpHeaders.CONTENT_ENCODING);
        if ("deflate".equals(encoding)) return deflate(response);
        return response;
    }

    private Request checkUser(Chain chain) {
        Request request = chain.request();
        URI uri = request.url().uri();
        if (uri.getUserInfo() == null) return request;
        return request.newBuilder().header(HttpHeaders.AUTHORIZATION, Util.basic(uri.getUserInfo())).build();
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
