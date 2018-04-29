package eu._4fh.guildsync.service.requests;

import org.json.JSONObject;

import eu._4fh.guildsync.data.BNetProfileInfo;

public class BNetProfileInfoRequest extends AbstractBNetRequest<BNetProfileInfo> {
	@Override
	protected BNetProfileInfo convertJsonToObject(JSONObject obj) {
		return new BNetProfileInfo(obj.getLong("id"), obj.getString("battletag"));
	}
}
