package eu._4fh.guildsync.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Properties;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
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

public class Config {
	private static final Logger log = LoggerFactory.getLogger(Config.class);
	private static final @Nonnull Config instance = new Config();

	// Values from properties-File
	private final @Nonnull int bnetNumRetries;
	private final @Nonnull int afterCharacterAddDontDeleteForDays;

	private final @Nonnull String dbUrl;
	private final @Nonnull String dbUser;
	private final @Nonnull String dbPassword;

	// Constructed Values
	private final @Nonnull OAuth2Client oAuth2Client;
	private @CheckForNull OAuth2AccessToken oAuthToken;

	private final @Nonnull List<String> allowedRemoteSystems;

	private final @Nonnull URI uriBNetAccountInfo;
	private final @Nonnull URI uriBNetAccountCharacters;
	private final @Nonnull UriBuilder uriBNetGuildCharactersPattern;

	public static @Nonnull Config getInstance() {
		return instance;
	}

	private static final String readProp(final @Nonnull Properties props, final @Nonnull String key) {
		return Objects.requireNonNull(props.getProperty(key), "Property-Key missing: " + key).trim();
	}

	private Config() {
		String configFileStr = System.getProperty("eu._4fh.guildsync.config.file", "guildsync.properties");
		Properties props = new Properties();
		File configFile = new File(configFileStr);
		try (FileInputStream fileInputStream = new FileInputStream(configFile)) {
			props.load(fileInputStream);
		} catch (IOException e) {
			log.error("Cant load config from file " + configFile.getAbsolutePath(), e);
			throw new RuntimeException("Cant load config from file " + configFile.getAbsolutePath(), e);
		}

		dbUrl = readProp(props, "DB.Url");
		dbUser = readProp(props, "DB.User");
		dbPassword = readProp(props, "DB.Password");

		final URI bnetApiBaseUrl = URI.create("https://" + readProp(props, "BNet.ApiHost")); // "eu.api.battle.net";
		final URI bnetOAuthBaseUrl = URI.create("https://" + readProp(props, "BNet.OAuthHost"));
		final String bnetApiLocale = readProp(props, "BNet.ApiLocale"); // "en_GB";
		bnetNumRetries = Integer.parseInt(readProp(props, "BNet.Retries"));
		if (bnetNumRetries < 1) {
			throw new RuntimeException("BNet.Retries must be greater than 0.");
		}
		afterCharacterAddDontDeleteForDays = Integer.parseInt(readProp(props, "AfterCharacterAddDontDeleteForDays"));
		if (afterCharacterAddDontDeleteForDays < 1) {
			throw new RuntimeException("AfterCharacterAddDontDeleteForDays must be greater than 0.");
		}

		final String bnetApiKey = readProp(props, "BNet.ApiKey"); // "hhhxv25rr3aemezs6a7ezydhthscsqqz";
		uriBNetAccountInfo = UriBuilder.fromUri(bnetOAuthBaseUrl).scheme("https").path("oauth/userinfo").build();
		uriBNetAccountCharacters = UriBuilder.fromUri(bnetApiBaseUrl).scheme("https").path("wow/user/characters")
				.build();
		uriBNetGuildCharactersPattern = UriBuilder.fromUri(bnetApiBaseUrl).scheme("https")
				.path("wow/guild/{guildServer}/{guildName}").queryParam("fields", "members")
				.queryParam("locale", bnetApiLocale);

		final List<String> remoteSystems = new LinkedList<String>(
				Arrays.asList(readProp(props, "Remote.Systems").split(" ")));
		for (ListIterator<String> it = remoteSystems.listIterator(); it.hasNext();) {
			String str = it.next();
			if (str.trim().isEmpty()) {
				it.remove();
			}
		}
		allowedRemoteSystems = Collections.unmodifiableList(new ArrayList<>(remoteSystems)); // Arrays.asList("Forum", "TS", "Test", "Test2")

		final String bnetApiSecret = readProp(props, "BNet.ApiSecret"); // "bYkfrRNRSuNcDPvuYZ2mCaq9VuqJzW6n"
		final String bnetOAuthAuthUrl = readProp(props, "BNetOAuth.AuthUrl"); // "https://eu.battle.net/oauth/authorize"
		final String bnetOAuthTokenUrl = readProp(props, "BNetOAuth.TokenUrl"); // "https://EU.battle.net/oauth/token"
		final int bnetOAuthDefaultTokenDuration = Integer.parseInt(readProp(props, "BNetOAuth.DefaultTokenDuration")); // 259200 = 3 Tage
		final LazyUri bnetOAuthRedirectTarget = new LazyUri(
				new Precoded(readProp(props, "BNetOAuth.RedirectAfterAuthTo")));
		// Call bnetOAuthRedirectTarget.fragement to force validation of the url
		bnetOAuthRedirectTarget.fragment();

		oAuth2Client = new BasicOAuth2Client(new BasicOAuth2AuthorizationProvider(URI.create(bnetOAuthAuthUrl),
				URI.create(bnetOAuthTokenUrl),
				new Duration(1, 0,
						bnetOAuthDefaultTokenDuration) /* default expiration time in case the server doesn't return any */),
				new BasicOAuth2ClientCredentials(bnetApiKey, bnetApiSecret), bnetOAuthRedirectTarget);
	}

	public @Nonnull OAuth2Client oAuth2Client() {
		return oAuth2Client;
	}

	public @Nonnull OAuth2Scope oAuth2Scope() {
		return new BasicScope("wow.profile");
	}

	public @Nonnull String dbUrl() {
		return dbUrl;
	}

	public @Nonnull String dbUser() {
		return dbUser;
	}

	public @Nonnull String dbPassword() {
		return dbPassword;
	}

	public @Nonnull List<String> allowedRemoteSystems() {
		return allowedRemoteSystems;
	}

	public @Nonnull URI uriBNetAccountInfo() {
		return uriBNetAccountInfo;
	}

	public @Nonnull URI uriBNetAccountCharacters() {
		return uriBNetAccountCharacters;
	}

	public @Nonnull URI uriBNetGuildCharacters(final @Nonnull String guildName, final @Nonnull String guildServer) {
		return uriBNetGuildCharactersPattern.build(guildServer, guildName);
	}

	public @Nonnull OAuth2AccessToken token() throws ProtocolException, IOException, ProtocolError {
		if (oAuthToken != null) {
			log.debug("Token expiration: Token {} expires {}", oAuthToken.accessToken(),
					oAuthToken.expirationDate().toString());
		}
		if (oAuthToken == null || oAuthToken.expirationDate().before(DateTime.now())) {
			oAuthToken = new ClientCredentialsGrant(oAuth2Client(), new BasicScope("scope"))
					.accessToken(new HttpUrlConnectionExecutor());
			Objects.requireNonNull(oAuthToken, "Received no new token");
			log.info("Requested new token, got: {} as {} for {} valid until {}", oAuthToken.accessToken(),
					oAuthToken.tokenType(), oAuthToken.scope().toString(), oAuthToken.expirationDate().toString());
		}
		return oAuthToken;
	}

	public @Nonnull int bnetNumRetries() {
		return bnetNumRetries;
	}

	public final int getAfterCharacterAddDontDeleteForDays() {
		return afterCharacterAddDontDeleteForDays;
	}
}
