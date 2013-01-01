package com.interzonedev.httpagent;

import java.io.IOException;

/**
 * Interface for performing HTTP requests and returning responses using the {@link Request} and {@link Response} value
 * objects.
 * 
 * @author mark@interzonedev.com
 */
public interface RequestService {

	/**
	 * Performs an HTTP request using the url, method, headers and parameters in the specified {@link Request} value
	 * object.
	 * 
	 * @param request
	 *            The {@link Request} value object that contains the components of the HTTP request to be made.
	 * 
	 * @return Returns a {@link Response} value object that contains the components, including status and body, of the
	 *         HTTP response to the HTTP request performed by this method.
	 * 
	 * @throws IOException
	 *             Thrown if there is an error performing the HTTP request.
	 */
	public Response doRequest(Request request) throws IOException;

}
