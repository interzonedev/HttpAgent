package com.interzonedev.httpagent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;
import javax.servlet.http.Cookie;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

import com.interzonedev.httpcore.HttpException;
import com.interzonedev.httpcore.Method;
import com.interzonedev.httpcore.Request;
import com.interzonedev.httpcore.Response;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.ListenableFuture;

public class NingRequestService implements RequestService {

    private static final Logger log = (Logger) LoggerFactory.getLogger(NingRequestService.class);

    private final AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

    @PreDestroy
    public void destroy() {
        if (!asyncHttpClient.isClosed()) {
            asyncHttpClient.close();
        }
    }

    @Override
    public Response doSynchronousRequest(Request request) throws HttpException {
        try {
            log.debug("doSynchronousRequest: Start - request = " + request);
            Future<Response> responseFuture = doRequest(request);
            Response response = responseFuture.get();
            log.debug("doSynchronousRequest: Returning response = " + response);
            return response;
        } catch (Exception e) {
            String errorMessage = "Error performing synchronous HTTP request";
            log.error("doSynchronousRequest: " + errorMessage, e);
            throw new HttpException(errorMessage, e);
        } finally {
            log.debug("doSynchronousRequest: End");
        }
    }

    @Override
    public Future<Response> doRequest(final Request request) throws HttpException {
        try {
            log.debug("doRequest: Start - request = " + request);

            BoundRequestBuilder requestBuilder = getRequestBuilderFromRequest(request);

            final ListenableFuture<Response> responseFuture = requestBuilder
                    .execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(com.ning.http.client.Response ningResponse) throws Exception {
                            return transformResponse(request, ningResponse);
                        }

                        @Override
                        public void onThrowable(Throwable t) {
                            log.error("onThrowable: Error making request", t);
                        }
                    });

            log.debug("doRequest: Got response future");

            return responseFuture;
        } catch (Exception e) {
            String errorMessage = "Error performing asynchronous HTTP request";
            log.error("doRequest: " + errorMessage, e);
            throw new HttpException(errorMessage, e);
        } finally {
            log.debug("doRequest: End");
        }
    }

    /**
     * Assemble the {@link BoundRequestBuilder} instance that represents the HTTP request from the {@link Request} value
     * object.
     * 
     * @param request The {@link Request} value object that contains the components of the {@link BoundRequestBuilder}
     *            to assemble.
     * 
     * @return Returns an {@link BoundRequestBuilder} instance that represents the HTTP request from the {@link Request}
     *         value object.
     */
    private BoundRequestBuilder getRequestBuilderFromRequest(Request request) throws UnsupportedEncodingException {
        String url = request.getUrl();

        Method method = request.getMethod();

        BoundRequestBuilder requestBuilder = null;

        switch (method) {
            case GET:
                requestBuilder = asyncHttpClient.prepareGet(url);
                break;
            case POST:
                requestBuilder = asyncHttpClient.preparePost(url);
                break;
            case PUT:
                requestBuilder = asyncHttpClient.preparePut(url);
                break;
            case DELETE:
                requestBuilder = asyncHttpClient.prepareDelete(url);
                break;
            case OPTIONS:
                requestBuilder = asyncHttpClient.prepareOptions(url);
                break;
            case HEAD:
                requestBuilder = asyncHttpClient.prepareHead(url);
                break;
            case CONNECT:
                requestBuilder = asyncHttpClient.prepareConnect(url);
                break;
            default:
                throw new RuntimeException("Unsupported request method: " + method);
        }

        addRequestParametersToRequestBuilder(requestBuilder, request);

        addRequestHeadersToRequestBuilder(requestBuilder, request);

        return requestBuilder;
    }

    private void addRequestParametersToRequestBuilder(BoundRequestBuilder requestBuilder, Request request) {
        Method method = request.getMethod();
        Map<String, List<String>> parameters = request.getParameters();
        for (String parameterName : parameters.keySet()) {
            List<String> parameterValues = parameters.get(parameterName);
            for (String parameterValue : parameterValues) {
                switch (method) {
                    case POST:
                    case PUT:
                        requestBuilder.addFormParam(parameterName, parameterValue.toString());
                        break;
                    default:
                        requestBuilder.addQueryParam(parameterName, parameterValue.toString());
                }
            }
        }
    }

    private void addRequestHeadersToRequestBuilder(BoundRequestBuilder requestBuilder, Request request) {
        Map<String, List<String>> headers = request.getHeaders();
        for (String headerName : headers.keySet()) {
            List<String> headerValues = headers.get(headerName);
            for (String headerValue : headerValues) {
                requestBuilder.addHeader(headerName, headerValue);
            }
        }
    }

    /**
     * For all request {@link Method}s except for {@link Method#POST} and {@link Method#PUT}, transforms the parameters
     * in the specified {@link Request} into a query string and appends it to the URL taken from the specified
     * {@link Request}.
     * 
     * @param request The {@link Request} value object that contains the parameters to add to the URL.
     * 
     * @return Returns the URL taken from the specified {@link Request} with a query string appended if there are
     *         request parameters and the request {@link Method} is not {@link Method#POST} or {@link Method#PUT}.
     */
    @SuppressWarnings("unused")
    @Deprecated
    private String getUrlWithQueryString(Request request) {
        StringBuilder url = new StringBuilder(request.getUrl());

        Method method = request.getMethod();

        if (Method.POST.equals(method) || Method.PUT.equals(method)) {
            return url.toString();
        }

        StringBuilder queryString = new StringBuilder();

        Map<String, List<String>> parameters = request.getParameters();
        try {
            for (String parameterName : parameters.keySet()) {
                String encodedParameterName = URLEncoder.encode(parameterName, "utf-8");
                List<String> parameterValues = parameters.get(parameterName);
                for (String parameterValue : parameterValues) {
                    String encodedParameterValue = URLEncoder.encode(parameterValue, "utf-8");
                    queryString.append(encodedParameterName).append("=").append(encodedParameterValue).append("&");
                }
            }
        } catch (UnsupportedEncodingException uee) {
            String errorMessage = "Unable to URL encode query string parameters in UTF-8";
            log.error(errorMessage, uee);
            throw new RuntimeException(errorMessage, uee);
        }

        int queryStringLength = queryString.length();
        if (queryStringLength > 0) {
            queryString.delete(queryStringLength - 2, queryStringLength - 1);

            if (-1 == url.indexOf("?")) {
                queryString.insert(0, "?");
            } else {
                queryString.insert(0, "&");
            }

            url.append(queryString);
        }

        return url.toString();
    }

    private Response transformResponse(Request request, com.ning.http.client.Response ningResponse) throws IOException {
        int statusCode = ningResponse.getStatusCode();

        String contentType = ningResponse.getContentType();

        String responseContent = ningResponse.getResponseBody();

        Map<String, List<String>> responseHeaders = ningResponse.getHeaders();

        // TODO - Check headers first.
        long contentLength = responseContent.length();

        Map<String, Cookie> cookies = getCookiesFromResponse(ningResponse);

        // TODO - Get locale from headers
        Locale locale = null;

        return Response.newBuilder().setRequest(request).setStatus(statusCode).setContentType(contentType)
                .setContentLength(contentLength).setHeaders(responseHeaders).setCookies(cookies)
                .setContent(responseContent).setLocale(locale).build();
    }

    private Map<String, Cookie> getCookiesFromResponse(com.ning.http.client.Response ningResponse) {
        Map<String, Cookie> cookies = new HashMap<String, Cookie>();

        List<com.ning.http.client.cookie.Cookie> ningCookies = ningResponse.getCookies();

        for (com.ning.http.client.cookie.Cookie ningCookie : ningCookies) {
            String cookieName = ningCookie.getName();
            String cookieValue = ningCookie.getValue();

            Cookie cookie = new Cookie(cookieName, cookieValue);

            cookies.put(cookieName, cookie);
        }

        return cookies;
    }
}
