package eu._4fh.guildsync.service.requests;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import eu._4fh.guildsync.data.BNetProfileWowCharacter;

public class BNetProfileWowCharactersRequest extends AbstractBNetRequest<List<BNetProfileWowCharacter>> {

	@Override
	protected List<BNetProfileWowCharacter> convertJsonToObject(JSONObject arrayObj) {
		List<BNetProfileWowCharacter> result = new ArrayList<>();
		JSONArray array = arrayObj.getJSONArray("characters");
		for (int i = 0; i < array.length(); ++i) {
			JSONObject obj = array.getJSONObject(i);
			BNetProfileWowCharacter character = new BNetProfileWowCharacter(obj.getString("name"),
					obj.getString("realm"), obj.optString("guild"), obj.optString("guildRealm"));
			result.add(character);
		}

		return result;
	}
}
