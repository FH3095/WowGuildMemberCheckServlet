package eu._4fh.guildsync.rest;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.db.Transaction;
import eu._4fh.guildsync.rest.helper.RequiredParam;

@Path("guilds")
public class Guilds {
	private static final SecureRandom random;

	static {
		try {
			random = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@GET
	@Path("register")
	@Produces(MediaType.TEXT_HTML)
	public String registerGuild(final @QueryParam("name") @RequiredParam String guildName,
			final @QueryParam("server") @RequiredParam String guildServer) {
		final byte[] macKey = new byte[32]; // https://crypto.stackexchange.com/questions/31473/what-size-should-the-hmac-key-be-with-sha-256
		random.nextBytes(macKey);
		String apiKeyStr = generateRandomString();
		String macKeyStr = Base64.getEncoder().encodeToString(macKey);
		long newId;
		try (Transaction trans = Transaction.getTransaction()) {
			newId = DbWrapper.guildAdd(guildName, guildServer, apiKeyStr, macKeyStr);
			trans.commit();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return "<html><body>ID: " + String.valueOf(newId) + "<br>API-Key: " + apiKeyStr + "<br>Mac-Key: " + macKeyStr
				+ "</body></html>";
	}

	private String generateRandomString() {
		final int targetLength = 32;
		final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder result = new StringBuilder(targetLength);
		while (result.length() < targetLength) {
			result.append(chars.charAt(random.nextInt(chars.length())));
		}
		return result.toString();
	}
}
