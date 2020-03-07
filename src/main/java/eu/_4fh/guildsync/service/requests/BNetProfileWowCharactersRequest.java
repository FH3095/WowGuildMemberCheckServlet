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
		final JSONArray wowAccounts = arrayObj.getJSONArray("wow_accounts");
		for (int i = 0; i < wowAccounts.length(); ++i) {
			final JSONArray characters = wowAccounts.getJSONObject(i).getJSONArray("characters");
			for (int j = 0; j < characters.length(); ++j) {
				final JSONObject obj = characters.getJSONObject(j);
				final BNetProfileWowCharacter character = new BNetProfileWowCharacter(obj.getString("name"),
						obj.getJSONObject("realm").getString("name"), Integer.MAX_VALUE);
				result.add(character);
			}
		}

		return result;
	}
}
