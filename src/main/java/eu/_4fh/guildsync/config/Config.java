package eu._4fh.guildsync.config;

import java.io.IOException;
import java.net.URI;
import java.security.Key;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TimeZone;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.ws.rs.core.UriBuilder;

import org.dmfs.httpessentials.exceptions.ProtocolError;
import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.dmfs.httpessentials.httpurlconnection.HttpUrlConnectionExecutor;
import org.dmfs.oauth2.client.BasicOAuth2AuthorizationProvider;
import org.dmfs.oauth2.client.BasicOAuth2Client;
import org.dmfs.oauth2.client.BasicOAuth2ClientCredentials;
import org.dmfs.oauth2.client.OAuth2AccessToken;
import org.dmfs.oauth2.client.OAuth2Client;
import org.dmfs.oauth2.client.OAuth2Scope;
import org.dmfs.oauth2.client.grants.ClientCredentialsGrant;
import org.dmfs.oauth2.client.scope.BasicScope;
import org.dmfs.rfc3986.encoding.Precoded;
import org.dmfs.rfc3986.uris.LazyUri;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ParametersAreNonnullByDefault
public class Config {
	private static final Logger log = LoggerFactory.getLogger(Config.class);
	private static final @Nonnull Config instance = new Config();

	private static final String envPrefix = "java:/comp/env/";

	private final @Nonnull DataSource dbDataSource;

	private final @Nonnull OAuth2Client oAuth2Client;
	private @CheckForNull OAuth2AccessToken oAuthToken;
	private final @Nonnull String oAuthScope;

	private final @Nonnull URI uriBNetCheckToken;
	private final @Nonnull URI uriBNetAccountInfo;
	private final @Nonnull URI uriBNetAccountCharacters;
	private final @Nonnull URI uriBNetGuildCharacters;
	private final int numBNetRetries;

	private final @Nonnull byte[] macKeyArray;

	private final int afterCharacterAddDontDeleteForDays;
	private final int officerMaxRank;

	public static @Nonnull Config getInstance() {
		return instance;
	}

	private @Nonnull <T> Optional<T> getContextObjectOrEmpty(String name, final Class<T> type) {
		try {
			name = envPrefix + name;
			javax.naming.Context initContext = new InitialContext();
			final @CheckForNull Object obj = initContext.lookup(name);
			if (obj == null) {
				return Optional.empty();
			}
			if (!type.isInstance(obj)) {
				throw new IllegalStateException("Context-Object " + name + " is not of type " + type.getName() + " but "
						+ obj.getClass().getName());
			}
			return Optional.of(type.cast(obj));
		} catch (NamingException e) {
			throw new IllegalStateException(e);
		}
	}

	private @Nonnull <T> T getContextObject(final String name, final Class<T> type) {
		final Optional<T> obj = getContextObjectOrEmpty(name, type);
		if (obj.isEmpty()) {
			throw new IllegalStateException("Context-Object " + name + " does not exist");
		}
		return obj.get();
	}

	private Config() {
		dbDataSource = getContextObject("jdbc/db", DataSource.class);

		afterCharacterAddDontDeleteForDays = getContextObject("conf/afterCharacterAddDontDeleteForDays", Integer.class);
		officerMaxRank = getContextObject("conf/officerMaxRank", Integer.class);
		numBNetRetries = getContextObject("conf/numRetries", Integer.class);

		macKeyArray = Base64.getDecoder().decode(getContextObject("conf/macKey", String.class));

		oAuthScope = getContextObject("bnet/scope", String.class);

		// https://eu.battle.net/oauth/userinfo
		uriBNetAccountInfo = UriBuilder.fromUri(getContextObject("bnet/uri/oauth/userinfo", String.class)).build();
		// https://eu.battle.net/oauth/check_token
		uriBNetCheckToken = UriBuilder.fromUri(getContextObject("bnet/uri/oauth/checkToken", String.class)).build();
		// To all following URLs should be ?namespace=profile-eu&locale=en_GB appended
		// https://eu.api.blizzard.com/profile/user/wow 
		uriBNetAccountCharacters = UriBuilder.fromUri(getContextObject("bnet/uri/profile/characters", String.class))
				.build();
		// https://eu.api.blizzard.com/data/wow/guild/{realmSlug}/{nameSlug}/roster 
		uriBNetGuildCharacters = UriBuilder.fromUri(getContextObject("bnet/uri/guild/members", String.class)).build();

		// Build OAuth things
		final String bnetApiKey = getContextObject("bnet/ApiKey", String.class);
		final String bnetApiSecret = getContextObject("bnet/ApiSecret", String.class);
		final int bnetDefaultTokenDuration = getContextObject("bnet/DefaultTokenDuration", Integer.class);
		// https://eu.battle.net/oauth/authorize
		final URI uriBNetOAuthAuth = URI.create(getContextObject("bnet/uri/oauth/authorize", String.class));
		// https://eu.battle.net/oauth/token
		final URI uriBNetOAuthToken = URI.create(getContextObject("bnet/uri/oauth/token", String.class));
		final LazyUri bnetOAuthRedirectTarget = new LazyUri(
				new Precoded(getContextObject("bnet/authRedirectTarget", String.class)));
		// Call bnetOAuthRedirectTarget.fragement to force validation of the url
		bnetOAuthRedirectTarget.fragment();

		oAuth2Client = new BasicOAuth2Client(
				new BasicOAuth2AuthorizationProvider(uriBNetOAuthAuth, uriBNetOAuthToken,
						new Duration(1, bnetDefaultTokenDuration,
								0) /* default expiration time in case the server doesn't return any */),
				new BasicOAuth2ClientCredentials(bnetApiKey, bnetApiSecret), bnetOAuthRedirectTarget);
	}

	public DataSource dbDataSource() {
		return dbDataSource;
	}

	public int afterCharacterAddDontDeleteForDays() {
		return afterCharacterAddDontDeleteForDays;
	}

	public int officerMaxRank() {
		return officerMaxRank;
	}

	public int bnetNumRetries() {
		return numBNetRetries;
	}

	public @Nonnull OAuth2Scope oAuth2Scope() {
		return new BasicScope(oAuthScope);
	}

	public @Nonnull String oAuth2ScopeAsString() {
		return oAuthScope;
	}

	public String macAlgorithm() {
		return "HmacSHA256";
	}

	public Key macKey() {
		return new SecretKeySpec(macKeyArray, macAlgorithm());
	}

	public @Nonnull OAuth2Client oAuth2Client() {
		return oAuth2Client;
	}

	public @Nonnull URI uriBNetCheckToken() {
		return uriBNetCheckToken;
	}

	public @Nonnull URI uriBNetAccountInfo() {
		return uriBNetAccountInfo;
	}

	public @Nonnull URI uriBNetAccountCharacters() {
		return uriBNetAccountCharacters;
	}

	public @Nonnull URI uriBNetGuildCharacters() {
		return uriBNetGuildCharacters;
	}

	public synchronized @Nonnull OAuth2AccessToken token() throws ProtocolException, IOException, ProtocolError {
		if (oAuthToken != null) {
			log.debug("Token expiration: Token {} expires {}", oAuthToken.accessToken(),
					oAuthToken.expirationDate().shiftTimeZone(TimeZone.getDefault()).toString());
		}
		if (oAuthToken == null || oAuthToken.expirationDate().before(DateTime.now())) {
			oAuthToken = new ClientCredentialsGrant(oAuth2Client(), oAuth2Scope())
					.accessToken(new HttpUrlConnectionExecutor());
			Objects.requireNonNull(oAuthToken, "Received no new token");
			log.info("Requested new token, got: {} as {} for {} valid until {}", oAuthToken.accessToken(),
					oAuthToken.tokenType(), oAuthToken.scope().toString(), oAuthToken.expirationDate().toString());
		}
		return oAuthToken;
	}
}
