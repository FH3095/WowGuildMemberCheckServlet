package eu._4fh.guildsync.service.requests;

import java.io.IOException;

import org.dmfs.httpessentials.HttpMethod;
import org.dmfs.httpessentials.HttpStatus;
import org.dmfs.httpessentials.client.HttpRequest;
import org.dmfs.httpessentials.client.HttpRequestEntity;
import org.dmfs.httpessentials.client.HttpResponse;
import org.dmfs.httpessentials.client.HttpResponseHandler;
import org.dmfs.httpessentials.entities.EmptyHttpRequestEntity;
import org.dmfs.httpessentials.exceptions.ProtocolError;
import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.dmfs.httpessentials.headers.EmptyHeaders;
import org.dmfs.httpessentials.headers.Headers;
import org.dmfs.httpessentials.responsehandlers.FailResponseHandler;
import org.dmfs.httpessentials.responsehandlers.StringResponseHandler;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class AbstractBNetRequest<T> implements HttpRequest<T> {

	@Override
	public HttpMethod method() {
		return HttpMethod.GET;
	}

	@Override
	public Headers headers() {
		return EmptyHeaders.INSTANCE;
	}

	@Override
	public HttpRequestEntity requestEntity() {
		return EmptyHttpRequestEntity.INSTANCE;
	}

	protected abstract T convertJsonToObject(final JSONObject obj);

	@Override
	public HttpResponseHandler<T> responseHandler(HttpResponse response)
			throws IOException, ProtocolError, ProtocolException {
		if (!HttpStatus.OK.equals(response.status())) {
			return FailResponseHandler.getInstance();
		}

		String responseString = new StringResponseHandler("UTF-8").handleResponse(response);
		try {
			JSONObject obj = new JSONObject(responseString);
			return new HttpResponseHandler<T>() {

				@Override
				public T handleResponse(HttpResponse response) throws IOException, ProtocolError, ProtocolException {
					return convertJsonToObject(obj);
				}
			};
		} catch (JSONException e) {
			throw new ProtocolException(String.format("Can't decode JSON response %s", responseString), e);
		}
	}
}
