package eu._4fh.guildsync.rest;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import eu._4fh.guildsync.data.RemoteCommand;
import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.db.Transaction;
import eu._4fh.guildsync.rest.helper.RequiredParam;

@Path("{guildId}/changes")
public class Changes {
	@PathParam("guildId")
	Long guildId;

	@GET
	@Path("get")
	@Produces(MediaType.APPLICATION_JSON)
	public List<RemoteCommand> getChanges(@QueryParam("systemName") @RequiredParam String remoteSystemName) {
		return Collections
				.unmodifiableList(new DbWrapper(guildId).remoteCommandsGetByRemoteSystemName(remoteSystemName));
	}

	@GET
	@Path("reset")
	public void resetChanges(@QueryParam("systemName") @RequiredParam String remoteSystem,
			@QueryParam("lastId") @RequiredParam Long lastId) {
		try (Transaction transaction = Transaction.getTransaction()) {
			new DbWrapper(guildId).remoteCommandsDeleteByRemoteSystemNameAndId(remoteSystem, lastId);
			transaction.commit();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
}
