package eu._4fh.guildsync.rest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import eu._4fh.guildsync.data.WowCharacter;
import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.helper.MacCalculator;
import eu._4fh.guildsync.rest.helper.RequiredParam;
import eu._4fh.guildsync.service.SyncService;

@Path("chars")
public class Chars {
	@GET
	@Path("get")
	@Produces(MediaType.APPLICATION_JSON)
	public List<WowCharacter> getCharacters(@QueryParam("systemName") @RequiredParam String remoteSystem,
			@QueryParam("remoteId") @RequiredParam Long remoteAccountId,
			final @QueryParam("mac") @RequiredParam String macIn) {
		MacCalculator.testMac(macIn, remoteSystem, String.valueOf(remoteAccountId));
		return new DbWrapper().charactersGetByRemoteAccountId(remoteSystem, remoteAccountId);
	}

	@GET
	@Path("refresh")
	@Produces(MediaType.TEXT_PLAIN)
	public String refresh() {
		return new SyncService().updateAndDeleteAccounts();
	}
}
