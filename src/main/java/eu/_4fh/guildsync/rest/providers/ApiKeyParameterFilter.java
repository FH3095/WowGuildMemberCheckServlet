package eu._4fh.guildsync.rest.providers;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import eu._4fh.guildsync.db.DbWrapper;

@Provider
public class ApiKeyParameterFilter implements ContainerRequestFilter {
	public static final String guildIdPathParam = "guildId";
	public static final String guildApiKey = "apiKey";
	private static final Map<Long, String> guildApiKeyCache = new ConcurrentHashMap<>();

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		String guildIdStr = requestContext.getUriInfo().getPathParameters().getFirst(guildIdPathParam);
		if (guildIdStr == null) {
			return;
		}

		long guildId = Long.parseLong(guildIdStr);
		String apiKey = guildApiKeyCache.get(guildId);
		if (apiKey == null) {
			apiKey = DbWrapper.guildGetApiKeyById(guildId);
			if (apiKey == null) {
				throw new BadRequestException("Guild-ID " + guildId + " is unknown.");
			}
			guildApiKeyCache.putIfAbsent(guildId, apiKey);
		}
		String providedKey = requestContext.getUriInfo().getQueryParameters().getFirst(guildApiKey);
		if (!apiKey.equals(providedKey)) {
			throw new BadRequestException("Invalid guild apiKey");
		}
	}
}
