package com.interzonedev.httpagent;

import java.util.concurrent.Future;

import com.interzonedev.httpcore.HttpException;
import com.interzonedev.httpcore.Request;
import com.interzonedev.httpcore.Response;

/**
 * Interface for performing HTTP requests and returning responses using the {@link Request} and {@link Response} value
 * objects.
 */
public interface RequestService {

    /**
     * Performs a synchronous HTTP request using the url, method, headers and parameters in the specified
     * {@link Request} value object.
     * 
     * @param request The {@link Request} value object that contains the components of the HTTP request to be made.
     * 
     * @return Returns a {@link Response} value object that contains the components, including status and body, of the
     *         HTTP response to the HTTP request performed by this method.
     * 
     * @throws HttpException Thrown if there is an error performing the HTTP request.
     */
    Response doSynchronousRequest(Request request) throws HttpException;

    /**
     * Performs an asynchronous HTTP request using the url, method, headers and parameters in the specified
     * {@link Request} value object.
     * 
     * @param request The {@link Request} value object that contains the components of the HTTP request to be made.
     * 
     * @return Returns a {@link Future} that wraps a {@link Response} value object that contains the components,
     *         including status and body, of the HTTP response to the HTTP request performed by this method.
     * 
     * @throws HttpException Thrown if there is an error performing the HTTP request.
     */
    Future<Response> doRequest(Request request) throws HttpException;

}
