package com.interzonedev.httpagent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public class Request {

    private final String url;

    private final Method method;

    private final Map<String, List<String>> headers;

    private final Map<String, List<String>> parameters;

    public Request(String url, Method method, Map<String, List<String>> headers, Map<String, List<String>> parameters) {

        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("The url must be set");
        }

        if (null == method) {
            throw new IllegalArgumentException("The method must be set");
        }

        this.url = url;
        this.method = method;

        if (null == headers) {
            this.headers = Collections.emptyMap();
        } else {
            this.headers = Collections.unmodifiableMap(headers);
        }

        if (null == parameters) {
            this.parameters = Collections.emptyMap();
        } else {
            this.parameters = Collections.unmodifiableMap(parameters);
        }

    }

    public String getUrl() {
        return url;
    }

    public Method getMethod() {
        return method;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, List<String>> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getMethod()).append(" ").append(getUrl());

        return sb.toString();
    }

}
