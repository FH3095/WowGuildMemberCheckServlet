package eu._4fh.guildsync.rest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import eu._4fh.guildsync.helper.MacCalculator;
import eu._4fh.guildsync.rest.helper.RequiredParam;
import eu._4fh.guildsync.service.SyncService;

@Path("accounts")
public class Accounts {
	@GET
	@Path("remoteIdsByRemoteSystem")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Long> getAllRemoteIdsByRemoteSystem(
			final @RequiredParam @QueryParam("systemName") String remoteSystemName,
			final @RequiredParam @QueryParam("mac") String mac) {
		MacCalculator.testMac(mac, remoteSystemName);
		return new SyncService().getAllRemoteIdsByRemoteSystem(remoteSystemName);
	}
}
