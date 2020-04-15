package eu._4fh.guildsync.service.requests;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.dmfs.httpessentials.HttpMethod;
import org.dmfs.httpessentials.client.HttpRequestEntity;
import org.dmfs.httpessentials.entities.XWwwFormUrlEncodedEntity;
import org.dmfs.jems.pair.elementary.ValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

public class BNetCheckTokenRequest extends AbstractBNetRequest<Set<String>> {
	private final CharSequence token;

	public BNetCheckTokenRequest(final CharSequence token) {
		super();
		this.token = token;
	}

	@Override
	public HttpMethod method() {
		return HttpMethod.POST;
	}

	@Override
	public HttpRequestEntity requestEntity() {
		return new XWwwFormUrlEncodedEntity(Collections.singleton(new ValuePair<>("token", token)));
	}

	@Override
	protected Set<String> convertJsonToObject(JSONObject obj) {
		final JSONArray scopes = obj.getJSONArray("scope");
		final Set<String> result = new HashSet<>(scopes.length());
		for (int i = 0; i < scopes.length(); ++i) {
			result.add(scopes.getString(i));
		}
		return Collections.unmodifiableSet(result);
	}
}
