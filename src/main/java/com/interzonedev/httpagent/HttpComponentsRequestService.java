package com.interzonedev.httpagent;

import com.interzonedev.httpcore.HttpException;
import com.interzonedev.httpcore.Method;
import com.interzonedev.httpcore.Request;
import com.interzonedev.httpcore.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpComponentsRequestService implements RequestService {

    private static final Logger log = LoggerFactory.getLogger(HttpComponentsRequestService.class);

    private boolean initialized = false;

    private int maxTotalHttpConnections;

    private int defaultMaxHttpConnectionsPerRoute;

    private int coreThreadPoolSize;

    private int maximumThreadPoolSize;

    private CloseableHttpClient httpClient;

    private ExecutorService threadPoolExecutor;

    public HttpComponentsRequestService(int maxTotalHttpConnections, int defaultMaxHttpConnectionsPerRoute,
            int coreThreadPoolSize, int maximumThreadPoolSize) {
        this.maxTotalHttpConnections = maxTotalHttpConnections;
        this.defaultMaxHttpConnectionsPerRoute = defaultMaxHttpConnectionsPerRoute;
        this.coreThreadPoolSize = coreThreadPoolSize;
        this.maximumThreadPoolSize = maximumThreadPoolSize;
    }

    @PostConstruct
    public void init() {
        PoolingHttpClientConnectionManager httpClientConnectionManager = new PoolingHttpClientConnectionManager();
        httpClientConnectionManager.setMaxTotal(maxTotalHttpConnections);
        httpClientConnectionManager.setDefaultMaxPerRoute(defaultMaxHttpConnectionsPerRoute);

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().setConnectionManager(
                httpClientConnectionManager);

        httpClient = httpClientBuilder.build();

        threadPoolExecutor = new ThreadPoolExecutor(coreThreadPoolSize, maximumThreadPoolSize, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());

        initialized = true;
    }

    @PreDestroy
    public void destroy() {
        if (null != httpClient) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error("destroy: Error closing HTTP client", e);
            }
        }

        if (null != threadPoolExecutor) {
            threadPoolExecutor.shutdown();
        }
    }

    private class CallableRequest implements Callable<Response> {
        private final Request request;

        private CallableRequest(Request request) {
            this.request = request;
        }

        @Override
        public Response call() throws Exception {
            try {
                log.debug("call: Start request = " + request);

                if (!initialized) {
                    String errorMessage = "HttpComponentsRequestService not initialized";
                    log.error(errorMessage);
                    throw new IllegalStateException(errorMessage);
                }

                // Assemble the HTTP request from the request value object.
                HttpRequestBase httpRequestBase = getHttpRequestBaseFromRequest(request);

                log.debug("call: Sending HTTP request");

                // Send the HTTP request.
                HttpResponse httpResponse = httpClient.execute(httpRequestBase);

                log.debug("call: Received HTTP response");

                // Assemble the response value object from the HTTP response.
                Response response = transformResponse(request, httpResponse);

                log.debug("call: Assembled response = " + response);

                return response;
            } finally {
                log.debug("call: End");
            }
        }
    }

    @Override
    public Response doSynchronousRequest(Request request) throws HttpException {
        try {
            log.debug("doSynchronousRequest: Start - request = " + request);

            CallableRequest callableRequest = new CallableRequest(request);
            Response response = callableRequest.call();
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
    public Future<Response> doRequest(Request request) throws HttpException {
        try {
            log.debug("doRequest: Starting request - " + request);

            CallableRequest callableRequest = new CallableRequest(request);
            Future<Response> responseFuture = threadPoolExecutor.submit(callableRequest);
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
     * Assemble the {@link HttpRequestBase} instance that represents the HTTP request from the {@link Request} value
     * object.
     * 
     * @param request The {@link Request} value object that contains the components of the {@link HttpRequestBase} to
     *            assemble.
     * 
     * @return Returns an {@link HttpRequestBase} instance that represents the HTTP request from the {@link Request}
     *         value object.
     */
    private HttpRequestBase getHttpRequestBaseFromRequest(Request request) {
        Method method = request.getMethod();

        List<NameValuePair> requestNameValuePairs = getNameValuePairsFromRequestParameters(request.getParameters());

        String url = addQueryStringToUrl(method, request.getUrl(), requestNameValuePairs);

        HttpRequestBase httpRequestBase = getRawHttpRequestBaseFromMethod(method, url);

        addRequestHeadersToHttpRequestBase(httpRequestBase, request.getHeaders());

        addRequestParametersToRequestBody(httpRequestBase, method, requestNameValuePairs);

        return httpRequestBase;
    }

    /**
     * Turns the specified map of request parameters into a list of {@link NameValuePair}s. Request parameters with
     * multiple values result in corresponding multiple {@link NameValuePair} instances.
     * 
     * @param requestParameters A map of request parameters name/value pairs.
     * 
     * @return Returns a list of {@link NameValuePair}s that correspond to the request parameter name/value pairs.
     */
    private List<NameValuePair> getNameValuePairsFromRequestParameters(Map<String, List<String>> requestParameters) {

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

        if ((null != requestParameters) && !requestParameters.isEmpty()) {
            for (String parameterName : requestParameters.keySet()) {
                List<String> parameterValues = requestParameters.get(parameterName);
                for (String parameterValue : parameterValues) {
                    nameValuePairs.add(new BasicNameValuePair(parameterName, parameterValue));
                }
            }
        }

        return nameValuePairs;
    }

    /**
     * For all request {@link Method}s except for {@link Method#POST} and {@link Method#PUT}, transforms the specified
     * request parameter name/value pairs into a query string and appends it to the specified url.
     * 
     * @param method The {@link Method} of the HTTP request.
     * @param url The url of the HTTP request.
     * @param requestNameValuePairs A list of {@link NameValuePair}s that correspond to the request parameter name/value
     *            pairs.
     * 
     * @return Returns the specified url with a query string appended if there are request parameter name/value pairs
     *         and the request {@link Method} is not {@link Method#POST} or {@link Method#PUT}.
     */
    private String addQueryStringToUrl(Method method, String url, List<NameValuePair> requestNameValuePairs) {
        String alteredUrl = url;

        switch (method) {
            case POST:
            case PUT:
                break;
            default:
                String queryString = URLEncodedUtils.format(requestNameValuePairs, "utf-8");
                if (!alteredUrl.contains("?")) {
                    alteredUrl += "?";
                } else if (StringUtils.isNotBlank(queryString)) {
                    alteredUrl += "&";
                }
                alteredUrl += queryString;
        }

        return alteredUrl;
    }

    /**
     * Gets an instance of {@link HttpRequestBase} according to the specified {@link Method} initialized with the
     * specified url. The request headers and body are not set.
     * 
     * @param method The {@link Method} of the HTTP request.
     * @param url The url of the HTTP request.
     * 
     * @return Returns an instance of {@link HttpRequestBase} according to the specified {@link Method} initialized with
     *         the specified url.
     */
    private HttpRequestBase getRawHttpRequestBaseFromMethod(Method method, String url) {
        switch (method) {
            case GET:
                return new HttpGet(url);
            case POST:
                return new HttpPost(url);
            case PUT:
                return new HttpPut(url);
            case DELETE:
                return new HttpDelete(url);
            case OPTIONS:
                return new HttpOptions(url);
            case HEAD:
                return new HttpHead(url);
            case TRACE:
                return new HttpTrace(url);
            default:
                throw new RuntimeException("Unsupported request method: " + method);
        }
    }

    /**
     * Sets the specfied request headers on the specified {@link HttpRequestBase}.
     * 
     * @param httpRequestBase The {@link HttpRequestBase} that represents the HTTP request.
     * @param requestHeaders A map of request header name/value pairs.
     */
    private void addRequestHeadersToHttpRequestBase(HttpRequestBase httpRequestBase,
            Map<String, List<String>> requestHeaders) {
        if ((null != requestHeaders) && !requestHeaders.isEmpty()) {
            for (String requestHeaderName : requestHeaders.keySet()) {
                List<String> requestHeaderValues = requestHeaders.get(requestHeaderName);
                for (String requestHeaderValue : requestHeaderValues) {
                    httpRequestBase.addHeader(requestHeaderName, requestHeaderValue);
                }
            }
        }
    }

    /**
     * For {@link Method#POST} and {@link Method#PUT} sets the request parameter name/value pairs in the body of the
     * specified {@link HttpRequestBase}.
     * 
     * @param httpRequestBase The {@link HttpRequestBase} that represents the HTTP request.
     * @param method The {@link Method} of the HTTP request.
     * @param requestNameValuePairs A list of {@link NameValuePair}s that correspond to the request parameter name/value
     *            pairs.
     */
    private void addRequestParametersToRequestBody(HttpRequestBase httpRequestBase, Method method,
            List<NameValuePair> requestNameValuePairs) {
        if (!requestNameValuePairs.isEmpty()) {
            switch (method) {
                case POST:
                case PUT:
                    UrlEncodedFormEntity requestBodyEntity;
                    try {
                        requestBodyEntity = new UrlEncodedFormEntity(requestNameValuePairs, "utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        String errorMessage = "Error creating request body";
                        log.error("doRequest: " + errorMessage, uee);
                        throw new RuntimeException(errorMessage, uee);
                    }
                    ((HttpEntityEnclosingRequestBase) httpRequestBase).setEntity(requestBodyEntity);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Assembles a {@link Response} value object from the specified {@link HttpResponse}.
     * 
     * @param request The {@link Request} value object that represents the originiating HTTP request.
     * @param httpResponse The {@link HttpResponse} that represents the response to transform into a {@link Response}.
     * 
     * @return Returns a {@link Response} value object assembled from the components of the specified
     *         {@link HttpResponse}.
     * 
     * @throws ParseException Thrown if there was an error turning the response body into a string.
     * @throws IOException Thrown if there was an error turning the response body into a string.
     */
    private Response transformResponse(Request request, HttpResponse httpResponse) throws ParseException, IOException {
        HttpEntity responseEntity = httpResponse.getEntity();

        int statusCode = httpResponse.getStatusLine().getStatusCode();

        String contentType = null;
        Header contentTypeHeader = responseEntity.getContentType();
        if (null != contentTypeHeader) {
            contentType = responseEntity.getContentType().getValue();
        }

        long contentLength = responseEntity.getContentLength();

        Map<String, Cookie> cookies = getCookiesFromResponse(httpResponse);

        Map<String, List<String>> responseHeaders = getResponseHeaders(httpResponse);

        String responseContent = EntityUtils.toString(responseEntity);

        Locale locale = httpResponse.getLocale();

        return Response.newBuilder().setRequest(request).setStatus(statusCode).setContentType(contentType)
                .setContentLength(contentLength).setHeaders(responseHeaders).setCookies(cookies)
                .setContent(responseContent).setLocale(locale).build();
    }

    /**
     * Gets the headers from the specified {@link HttpResponse} and turns them into a map of header names to lists of
     * header values.
     * 
     * @param httpResponse The {@link HttpResponse} that represents the response from which to get the headers.
     * 
     * @return Returns a map of header names to lists of header values.
     */
    private Map<String, List<String>> getResponseHeaders(HttpResponse httpResponse) {
        Map<String, List<String>> responseHeaders = new HashMap<String, List<String>>();

        Header[] responseHeaderValues = httpResponse.getAllHeaders();
        for (Header responseHeaderValue : responseHeaderValues) {
            String reponseHeaderName = responseHeaderValue.getName();
            String reponseHeaderValue = responseHeaderValue.getValue();

            List<String> headerValues = responseHeaders.get(reponseHeaderName);
            if (null == headerValues) {
                headerValues = new ArrayList<String>();
                responseHeaders.put(reponseHeaderName, headerValues);
            }
            headerValues.add(reponseHeaderValue);
        }

        return responseHeaders;
    }

    /**
     * Gets a map of cookie names to {@link Cookie} instances by parsing the "Set-Cookie" headers in the specified
     * {@link HttpResponse}.
     * 
     * @param httpResponse The {@link HttpResponse} that represents the response from which to get the headers.
     * 
     * @return Returns a map of cookie names to {@link Cookie} instances by parsing the "Set-Cookie" headers in the
     *         specified {@link HttpResponse}.
     */
    private Map<String, Cookie> getCookiesFromResponse(HttpResponse httpResponse) {
        Map<String, Cookie> cookies = new HashMap<String, Cookie>();

        Header[] cookieHeaders = httpResponse.getHeaders("Set-Cookie");

        if ((null == cookieHeaders) || (0 == cookieHeaders.length)) {
            return cookies;
        }

        for (Header cookieHeader : cookieHeaders) {

            HeaderElement[] cookieHeaderElements = cookieHeader.getElements();
            for (HeaderElement cookieHeaderElement : cookieHeaderElements) {

                String cookieName = cookieHeaderElement.getName();
                String cookieValue = cookieHeaderElement.getValue();

                if (StringUtils.isNotBlank(cookieName)) {
                    try {
                        Cookie cookie = new Cookie(cookieName, cookieValue);

                        // TODO - Parse through cookie parameters for path, age, domain, etc. and set them on the
                        // cookie.
                        @SuppressWarnings("unused")
                        NameValuePair[] cookieParameters = cookieHeaderElement.getParameters();

                        cookies.put(cookieName, cookie);
                    } catch (Throwable t) {
                        String warnMessage = "Error creating cookie with name " + cookieName;
                        log.warn("getCookiesFromResponse: " + warnMessage, t);
                    }
                }

            }

        }

        return cookies;
    }
}
