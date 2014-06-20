package com.interzonedev.httpagent;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;

public class Response {

    private final Request request;

    private final int status;

    private final String contentType;

    private final long contentLength;

    private final Map<String, List<String>> headers;

    private final Map<String, Cookie> cookies;

    private final String content;

    private final Locale locale;

    public Response(Request request, int status, String contentType, long contentLength,
            Map<String, List<String>> headers, Map<String, Cookie> cookies, String content, Locale locale) {

        this.request = request;
        this.status = status;
        this.contentType = contentType;
        this.contentLength = contentLength;

        if (null == headers) {
            this.headers = Collections.emptyMap();
        } else {
            this.headers = Collections.unmodifiableMap(headers);
        }

        if (null == cookies) {
            this.cookies = Collections.emptyMap();
        } else {
            this.cookies = Collections.unmodifiableMap(cookies);
        }

        this.content = content;
        this.locale = locale;

    }

    public Request getRequest() {
        return request;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, Cookie> getCookies() {
        return cookies;
    }

    public String getContent() {
        return content;
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getStatus()).append(" from ").append(getRequest());

        return sb.toString();
    }
}
