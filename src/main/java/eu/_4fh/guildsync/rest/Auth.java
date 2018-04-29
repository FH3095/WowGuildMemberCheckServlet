package eu._4fh.guildsync.rest;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.dmfs.httpessentials.client.HttpRequestExecutor;
import org.dmfs.httpessentials.exceptions.ProtocolError;
import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.dmfs.httpessentials.httpurlconnection.HttpUrlConnectionExecutor;
import org.dmfs.oauth2.client.OAuth2AccessToken;
import org.dmfs.oauth2.client.OAuth2InteractiveGrant;
import org.dmfs.oauth2.client.OAuth2InteractiveGrant.OAuth2GrantState;
import org.dmfs.oauth2.client.grants.AuthorizationCodeGrant;
import org.dmfs.rfc3986.encoding.Precoded;
import org.dmfs.rfc3986.uris.LazyUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.guildsync.config.Config;
import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.rest.helper.HtmlHelper;
import eu._4fh.guildsync.rest.helper.RequiredParam;
import eu._4fh.guildsync.service.SyncService;

@Path("auth")
public class Auth {
	private static final class AuthInformations implements Serializable {
		private static final long serialVersionUID = 2781228208547364912L;
		public static final String sessionKey = "authInformations";

		public final @NonNull long guildId;
		public final @NonNull String remoteSystemName;
		public final @NonNull long remoteAccountId;
		public final @NonNull URI redirectTo;

		public AuthInformations(final @NonNull long guildId, final @NonNull String remoteSystemName,
				final @NonNull long remoteAccountId, final @NonNull URI redirectTo) {
			this.guildId = guildId;
			this.remoteSystemName = remoteSystemName;
			this.remoteAccountId = remoteAccountId;
			this.redirectTo = redirectTo;
		}
	}

	private static final Logger log = LoggerFactory.getLogger(Auth.class);
	private static final Config config = Config.getInstance();

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@GET
	@Path("start")
	@Produces(MediaType.TEXT_HTML)
	public Response start(final @QueryParam("guildId") @RequiredParam Long guildId,
			final @QueryParam("systemName") @RequiredParam String remoteSystemName,
			final @QueryParam("remoteAccountId") @RequiredParam Long remoteId,
			final @QueryParam("redirectTo") @RequiredParam String redirectTo,
			final @QueryParam("mac") @RequiredParam String macIn) throws URISyntaxException {
		if (!config.allowedRemoteSystems().contains(remoteSystemName)) {
			throw new BadRequestException("Not allowed remote system: " + remoteSystemName);
		}

		String macKey = DbWrapper.guildGetMacKeyById(guildId);
		if (macKey == null) {
			throw new BadRequestException("Unknown guild id " + guildId);
		}

		try {
			String toMacValues = String.valueOf(guildId) + remoteSystemName + String.valueOf(remoteId);
			SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(macKey), "HmacSHA256");
			Mac localMac = Mac.getInstance("HmacSHA256");
			localMac.init(key);
			byte[] localMacResult = localMac.doFinal(toMacValues.getBytes(StandardCharsets.UTF_8));
			byte[] inMacResult = Base64.getDecoder().decode(macIn);
			if (!Arrays.equals(inMacResult, localMacResult)) {
				throw new BadRequestException("Invalid MAC");
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}

		HttpSession session = request.getSession(true);
		session.setMaxInactiveInterval(900);
		try {
			session.setAttribute(AuthInformations.sessionKey,
					new AuthInformations(guildId, remoteSystemName, remoteId, new URI(redirectTo)));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		OAuth2InteractiveGrant grant = new AuthorizationCodeGrant(config.oAuth2Client(), config.oAuth2Scope());

		URI authorizationUrl = grant.authorizationUrl();
		session.setAttribute("grantState", grant.state());

		// Redirect as html. It seems there are multiple browsers out there that at least had problems with HTTP-Redirects with cookies
		// https://bugs.webkit.org/show_bug.cgi?id=3512
		// https://bugs.chromium.org/p/chromium/issues/detail?id=150066
		String result = HtmlHelper.getHtmlDoctype() + "<html><head>\n" + "<title>Redirect</title>\n"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\">\n"
				+ "<meta http-equiv=\"refresh\" content=\"5; URL=" + HtmlHelper.encodeLinkForHref(authorizationUrl)
				+ "\">\n" + "</head>\n\n" + "<body>\n" + "<p>Wait one moment please.</p>\n" + "</body></html>\n";
		return Response.ok(result).build();
	}

	@GET
	@Path("finish")
	@Produces(MediaType.TEXT_HTML)
	public Response finish() {
		try {
			HttpSession session = request.getSession(false);
			if (session == null || session.getAttribute("grantState") == null
					|| !(session.getAttribute("grantState") instanceof OAuth2GrantState)
					|| session.getAttribute(AuthInformations.sessionKey) == null
					|| !(session.getAttribute(AuthInformations.sessionKey) instanceof AuthInformations)) {
				throw new ForbiddenException("Cant find your session, please try again");
			}

			AuthInformations authInformations = (AuthInformations) session.getAttribute(AuthInformations.sessionKey);
			OAuth2GrantState grantState = (OAuth2GrantState) session.getAttribute("grantState");

			if (uriInfo.getQueryParameters().containsKey("error")) {
				String errorMsg = uriInfo.getQueryParameters().getFirst("error");
				String errorDesc = uriInfo.getQueryParameters().getFirst("error_description");
				log.info("Cant finish auth for " + authInformations.remoteAccountId + "@"
						+ authInformations.remoteSystemName + ": " + errorMsg + ": " + errorDesc);
				UriBuilder builder = UriBuilder.fromUri(authInformations.redirectTo);
				builder.replaceQueryParam("error", errorMsg);
				builder.replaceQueryParam("errorDescription", errorDesc);
				return Response.seeOther(builder.build()).build();
			}

			HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
			OAuth2InteractiveGrant grant = grantState.grant(config.oAuth2Client());
			OAuth2AccessToken token = grant
					.withRedirect(new LazyUri(new Precoded(
							request.getRequestURL().append('?').append(request.getQueryString()).toString())))
					.accessToken(executor);

			log.debug("Auth finished for " + authInformations.remoteAccountId + "@" + authInformations.remoteSystemName
					+ ". Token: " + token.accessToken() + " valid until " + token.expirationDate().toString() + " for "
					+ token.scope().toString() + " as " + token.tokenType() + " ; Redirecting to "
					+ authInformations.redirectTo);
			new SyncService(authInformations.guildId).addOrUpdateAccount(token, authInformations.remoteSystemName,
					authInformations.remoteAccountId);
			return Response.seeOther(authInformations.redirectTo).build();
		} catch (ProtocolError | ProtocolException | IOException e) {
			throw new RuntimeException(e);
		}
	}
}
