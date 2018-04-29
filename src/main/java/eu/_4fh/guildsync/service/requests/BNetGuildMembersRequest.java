package eu._4fh.guildsync.service.requests;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import eu._4fh.guildsync.data.BNetProfileWowCharacter;

public class BNetGuildMembersRequest extends AbstractBNetRequest<List<BNetProfileWowCharacter>> {

	@Override
	protected List<BNetProfileWowCharacter> convertJsonToObject(JSONObject guildObj) {
		List<BNetProfileWowCharacter> result = new ArrayList<>();
		JSONArray array = guildObj.getJSONArray("members");
		for (int i = 0; i < array.length(); ++i) {
			JSONObject obj = array.getJSONObject(i).getJSONObject("character");
			BNetProfileWowCharacter character = new BNetProfileWowCharacter(obj.getString("name"),
					obj.getString("realm"), obj.optString("guild"), obj.optString("guildRealm"));
			result.add(character);
		}

		return result;
	}
}
