package eu._4fh.guildsync.rest;

import java.util.List;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import eu._4fh.guildsync.config.Config;
import eu._4fh.guildsync.data.WowCharacter;
import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.rest.helper.RequiredParam;
import eu._4fh.guildsync.service.SyncService;

@Path("{guildId}/chars")
public class Chars {
	@PathParam("guildId")
	Long guildId;

	@POST
	@Path("add")
	@Consumes(MediaType.APPLICATION_JSON)
	public void add(@QueryParam("systemName") @RequiredParam String remoteSystem,
			@QueryParam("remoteAccountId") @RequiredParam Long remoteAccountId, WowCharacter wowCharacter) {
		if (wowCharacter == null) {
			throw new BadRequestException("Missing character data");
		}
		if (!Config.getInstance().allowedRemoteSystems().contains(remoteSystem)) {
			throw new BadRequestException("Not allowed remote system: " + remoteSystem);
		}
		new SyncService(guildId).addOrUpdateAccount(remoteSystem, remoteAccountId, wowCharacter);
	}

	@GET
	@Path("get")
	@Produces(MediaType.APPLICATION_JSON)
	public List<WowCharacter> getCharacters(@QueryParam("systemName") @RequiredParam String remoteSystem,
			@QueryParam("remoteAccountId") @RequiredParam Long remoteAccountId) {
		return new DbWrapper(guildId).charactersGetByRemoteAccountId(remoteSystem, remoteAccountId);
	}

	@GET
	@Path("refresh")
	@Produces(MediaType.TEXT_PLAIN)
	public String refresh() {
		return new SyncService(guildId).updateAndDeleteAccounts();
	}
}
