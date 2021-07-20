package eu._4fh.guildsync.rest;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
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

import eu._4fh.guildsync.config.Config;
import eu._4fh.guildsync.helper.MacCalculator;
import eu._4fh.guildsync.rest.helper.HtmlHelper;
import eu._4fh.guildsync.rest.helper.RequiredParam;
import eu._4fh.guildsync.service.SyncService;

@Path("auth")
public class Auth {
	private static final class AuthInformations implements Serializable {
		private static final long serialVersionUID = 2781228208547364912L;
		public static final String sessionKey = "authInformations";

		public final @Nonnull String remoteSystemName;
		public final @Nonnull long remoteAccountId;
		public final @Nonnull URI redirectTo;
		public final @Nonnull OAuth2GrantState grantState;

		public AuthInformations(final @Nonnull String remoteSystemName, final @Nonnull long remoteAccountId,
				final @Nonnull URI redirectTo, final @Nonnull OAuth2GrantState grantState) {
			this.remoteSystemName = remoteSystemName;
			this.remoteAccountId = remoteAccountId;
			this.redirectTo = redirectTo;
			this.grantState = grantState;
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
	public Response start(final @QueryParam("systemName") @RequiredParam String remoteSystemName,
			final @QueryParam("remoteId") @RequiredParam Long remoteId,
			final @QueryParam("redirectTo") @RequiredParam String redirectTo,
			final @QueryParam("mac") @RequiredParam String macIn) throws URISyntaxException {

		MacCalculator.testMac(macIn, remoteSystemName, String.valueOf(remoteId), redirectTo);

		OAuth2InteractiveGrant grant = new AuthorizationCodeGrant(config.oAuth2Client(), config.oAuth2Scope());

		HttpSession session = request.getSession(true);
		session.setMaxInactiveInterval(900);
		try {
			session.setAttribute(AuthInformations.sessionKey,
					new AuthInformations(remoteSystemName, remoteId, new URI(redirectTo), grant.state()));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		URI authorizationUrl = grant.authorizationUrl();
		// Redirect as html. It seems there are multiple browsers out there that at least had problems with HTTP-Redirects with cookies
		// https://bugs.webkit.org/show_bug.cgi?id=3512
		// https://bugs.chromium.org/p/chromium/issues/detail?id=150066
		String result = HtmlHelper.getHtmlDoctype() + "<html><head>\n" + "<title>Redirect</title>\n"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\">\n"
				+ "<meta http-equiv=\"refresh\" content=\"3; URL=" + HtmlHelper.encodeLinkForHref(authorizationUrl)
				+ "\">\n" + "</head>\n\n" + "<body>\n" + "<p>Wait one moment please.</p>\n" + "</body></html>\n";
		return Response.ok(result).build();
	}

	@GET
	@Path("finish")
	@Produces(MediaType.TEXT_HTML)
	public Response finish() {
		try {
			HttpSession session = request.getSession(false);
			if (session == null || session.getAttribute(AuthInformations.sessionKey) == null
					|| !(session.getAttribute(AuthInformations.sessionKey) instanceof AuthInformations)) {
				throw new ForbiddenException("Cant find your session, please try again");
			}

			AuthInformations authInformations = (AuthInformations) session.getAttribute(AuthInformations.sessionKey);
			OAuth2GrantState grantState = authInformations.grantState;

			if (uriInfo.getQueryParameters().containsKey("error")) {
				String errorMsg = uriInfo.getQueryParameters().getFirst("error");
				String errorDesc = uriInfo.getQueryParameters().getFirst("error_description");
				log.info("Cant finish auth for " + authInformations.remoteAccountId + "@"
						+ authInformations.remoteSystemName + ": " + errorMsg + ": " + errorDesc);
				UriBuilder builder = UriBuilder.fromUri(authInformations.redirectTo);
				builder.replaceQueryParam("error", errorMsg);
				builder.replaceQueryParam("errorDescription", errorDesc);
				builder.replaceQueryParam("remoteId", authInformations.remoteAccountId);
				return Response.seeOther(builder.build()).build();
			}

			HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
			OAuth2InteractiveGrant grant = grantState.grant(config.oAuth2Client());
			OAuth2AccessToken token = grant
					.withRedirect(new LazyUri(new Precoded(
							request.getRequestURL().append('?').append(request.getQueryString()).toString())))
					.accessToken(executor);

			log.info("Auth finished for " + authInformations.remoteAccountId + "@" + authInformations.remoteSystemName
					+ ". Token: " + token.accessToken() + " valid until " + token.expirationDate().toString() + " for "
					+ token.scope().toString() + " as " + token.tokenType() + " with refresh "
					+ Boolean.toString(token.hasRefreshToken()) + " ; Redirecting to " + authInformations.redirectTo);
			new SyncService().addOrUpdateAccount(token, authInformations.remoteSystemName,
					authInformations.remoteAccountId);
			UriBuilder builder = UriBuilder.fromUri(authInformations.redirectTo);
			builder.replaceQueryParam("remoteId", authInformations.remoteAccountId);
			return Response.seeOther(builder.build()).build();
		} catch (ProtocolError | ProtocolException | IOException e) {
			throw new RuntimeException(e);
		}
	}
}
