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

import javax.ws.rs.core.UriBuilder;

import org.dmfs.oauth2.client.BasicOAuth2AuthorizationProvider;
import org.dmfs.oauth2.client.BasicOAuth2Client;
import org.dmfs.oauth2.client.BasicOAuth2ClientCredentials;
import org.dmfs.oauth2.client.OAuth2Client;
import org.dmfs.oauth2.client.OAuth2Scope;
import org.dmfs.oauth2.client.scope.BasicScope;
import org.dmfs.rfc3986.encoding.Precoded;
import org.dmfs.rfc3986.uris.LazyUri;
import org.dmfs.rfc5545.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;

public class Config {
	private static final Logger log = LoggerFactory.getLogger(Config.class);
	private static final @NonNull Config instance = new Config();

	// Values from properties-File
	private final @NonNull String bnetApiHost;
	private final @NonNull String bnetApiLocale;
	private final @NonNull int bnetNumRetries;

	private final @NonNull String dbUrl;
	private final @NonNull String dbUser;
	private final @NonNull String dbPassword;

	// Constructed Values
	private final @NonNull OAuth2Client oAuth2Client;

	private final @NonNull List<String> allowedRemoteSystems;

	private final @NonNull URI uriBNetAccountInfo;
	private final @NonNull URI uriBNetAccountCharacters;
	private final @NonNull UriBuilder uriBNetGuildCharactersPattern;

	public static @NonNull Config getInstance() {
		return instance;
	}

	private static final String readProp(final @NonNull Properties props, final @NonNull String key) {
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

		bnetApiHost = readProp(props, "BNet.ApiHost"); // "eu.api.battle.net";
		bnetApiLocale = readProp(props, "BNet.ApiLocale"); // "en_GB";
		bnetNumRetries = Integer.parseInt(readProp(props, "BNet.Retries"));
		if (bnetNumRetries < 1) {
			throw new RuntimeException("BNet.Retries must be greater than 0.");
		}
		final String bnetApiKey = readProp(props, "BNet.ApiKey"); // "hhhxv25rr3aemezs6a7ezydhthscsqqz";

		final URI bnetApiBaseUrl = URI.create("https://" + bnetApiHost);
		uriBNetAccountInfo = UriBuilder.fromUri(bnetApiBaseUrl).scheme("https").path("account/user").build();
		uriBNetAccountCharacters = UriBuilder.fromUri(bnetApiBaseUrl).scheme("https").path("wow/user/characters")
				.build();
		uriBNetGuildCharactersPattern = UriBuilder.fromUri(bnetApiBaseUrl).scheme("https")
				.path("wow/guild/{guildServer}/{guildName}").queryParam("fields", "members")
				.queryParam("locale", bnetApiLocale).queryParam("apikey", bnetApiKey);

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

	public @NonNull OAuth2Client oAuth2Client() {
		return oAuth2Client;
	}

	public @NonNull OAuth2Scope oAuth2Scope() {
		return new BasicScope("wow.profile");
	}

	public @NonNull String dbUrl() {
		return dbUrl;
	}

	public @NonNull String dbUser() {
		return dbUser;
	}

	public @NonNull String dbPassword() {
		return dbPassword;
	}

	public @NonNull List<String> allowedRemoteSystems() {
		return allowedRemoteSystems;
	}

	public @NonNull URI uriBNetAccountInfo() {
		return uriBNetAccountInfo;
	}

	public @NonNull URI uriBNetAccountCharacters() {
		return uriBNetAccountCharacters;
	}

	public @NonNull URI uriBNetGuildCharacters(final @NonNull String guildName, final @NonNull String guildServer) {
		return uriBNetGuildCharactersPattern.build(guildServer, guildName);
	}

	public @NonNull int bnetNumRetries() {
		return bnetNumRetries;
	}
}
