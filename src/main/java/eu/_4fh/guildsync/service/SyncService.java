package eu._4fh.guildsync.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.ws.rs.BadRequestException;

import org.dmfs.httpessentials.client.HttpRequestExecutor;
import org.dmfs.httpessentials.decoration.HeaderDecorated;
import org.dmfs.httpessentials.exceptions.ProtocolError;
import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.dmfs.httpessentials.exceptions.RedirectionException;
import org.dmfs.httpessentials.exceptions.ServerErrorException;
import org.dmfs.httpessentials.exceptions.UnexpectedStatusException;
import org.dmfs.httpessentials.httpurlconnection.HttpUrlConnectionExecutor;
import org.dmfs.oauth2.client.OAuth2AccessToken;
import org.dmfs.oauth2.client.http.decorators.BearerAuthenticatedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu._4fh.guildsync.config.Config;
import eu._4fh.guildsync.data.Account;
import eu._4fh.guildsync.data.BNetProfileInfo;
import eu._4fh.guildsync.data.BNetProfileWowCharacter;
import eu._4fh.guildsync.data.WowCharacter;
import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.db.Transaction;
import eu._4fh.guildsync.helper.DateHelper;
import eu._4fh.guildsync.service.requests.BNetCheckTokenRequest;
import eu._4fh.guildsync.service.requests.BNetGuildMembersRequest;
import eu._4fh.guildsync.service.requests.BNetProfileInfoRequest;
import eu._4fh.guildsync.service.requests.BNetProfileWowCharactersRequest;
import eu._4fh.guildsync.service.requests.TokenHeaderDecorator;

@ParametersAreNonnullByDefault
public class SyncService {
	private static final Logger log = LoggerFactory.getLogger(SyncService.class);
	private static final @Nonnull Config config = Config.getInstance();

	private final @Nonnull DbWrapper db;
	private final @Nonnull List<BNetProfileWowCharacter> bnetGuildCharacters;

	public SyncService() {
		db = new DbWrapper();
		try {
			bnetGuildCharacters = Collections.unmodifiableList(
					new ArrayList<>(new HttpUrlConnectionExecutor().execute(config.uriBNetGuildCharacters(),
							new BearerAuthenticatedRequest<>(new BNetGuildMembersRequest(), config.token()))));
		} catch (IOException | ProtocolError | ProtocolException e) {
			throw new RuntimeException(e);
		}
	}

	private @Nonnull List<BNetProfileWowCharacter> removeNonGuildCharacters(
			final List<BNetProfileWowCharacter> otherCharacters) {
		final LinkedList<BNetProfileWowCharacter> result = new LinkedList<>(otherCharacters);

		for (final ListIterator<BNetProfileWowCharacter> it = result.listIterator(); it.hasNext();) {
			final BNetProfileWowCharacter character = it.next();
			final Optional<BNetProfileWowCharacter> bnetGuildCharacter = bnetGuildCharacters.stream()
					.filter(guildChar -> guildChar.equals(character)).findAny();
			if (bnetGuildCharacter.isEmpty()) {
				it.remove();
			} else if (character.getGuildRank() != bnetGuildCharacter.get().getGuildRank()) {
				it.remove();
				it.add(BNetProfileWowCharacter.copyAndMergeRanks(character, bnetGuildCharacter.get()));
			}
		}

		return result;
	}

	private long addAccount(@Nonnull String remoteSystem, @Nonnull long remoteId, OAuth2AccessToken token,
			long battleNetId) {
		long newAccountId = db.accountAdd(battleNetId, token);
		db.remoteAccountAdd(newAccountId, remoteSystem, remoteId);

		return newAccountId;
	}

	private void updateAccount(long accountId, @Nonnull String remoteSystem, long remoteId, OAuth2AccessToken token,
			long battleNetId) {
		db.accountUpdateById(accountId, battleNetId, token);

		if (db.remoteAccountIdGetByAccountId(accountId, remoteSystem) == null) {
			db.remoteAccountAdd(accountId, remoteSystem, remoteId);
		}
	}

	private boolean addCharacters(final long accountId, final List<BNetProfileWowCharacter> characters) {
		boolean hasNewCharacter = false;
		for (BNetProfileWowCharacter character : characters) {
			final WowCharacter newCharacter = new WowCharacter(character.getName(), character.getServer(),
					character.getGuildRank(), DateHelper.getNow());
			if (!db.characterExists(accountId, newCharacter)) {
				log.info("Add character " + newCharacter.getName() + "-" + newCharacter.getServer() + " for "
						+ accountId);
				db.characterAdd(accountId, newCharacter);
				hasNewCharacter = true;
			}
		}
		return hasNewCharacter;
	}

	public long addOrUpdateAccount(final @Nonnull OAuth2AccessToken token, final @Nonnull String remoteSystem,
			final @Nonnull long remoteId) {
		HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
		try (final Transaction trans = Transaction.getTransaction()) {
			final Set<String> scopes = executor.execute(config.uriBNetCheckToken(),
					new BNetCheckTokenRequest(token.accessToken()));
			if (!scopes.contains(config.oAuth2ScopeAsString())) {
				throw new BadRequestException("You need to authorize access to your wow profile. "
						+ "Please revoke all access at "
						+ "<a target=\"_blank\" href=\"https://account.blizzard.com/connections#authorized-applications\">https://account.blizzard.com/connections#authorized-applications</a> "
						+ "and try again.");
			}

			final BNetProfileInfo info = executor.execute(config.uriBNetAccountInfo(),
					new BearerAuthenticatedRequest<>(new BNetProfileInfoRequest(), token));
			List<BNetProfileWowCharacter> characters = executor.execute(config.uriBNetAccountCharacters(),
					new BearerAuthenticatedRequest<>(new BNetProfileWowCharactersRequest(), token));

			Long accountId = null;
			accountId = db.accountIdGetByBattleNetId(info.getId());
			if (accountId == null) {
				log.debug("Account not found by id, search by remote account " + remoteSystem + ":" + remoteId);
				accountId = db.accountIdGetByRemoteAccount(remoteSystem, remoteId);
			}
			if (accountId == null) {
				log.debug("Account not found by id remote system, search by characters");
				for (BNetProfileWowCharacter character : characters) {
					accountId = db.accountIdGetByCharacter(character.getName(), character.getServer());
					if (accountId != null) {
						log.debug("Account found by character " + character.getName() + "-" + character.getServer());
						break;
					}
				}
			}
			log.debug("Account-ID: " + String.valueOf(accountId));

			characters = removeNonGuildCharacters(characters);

			if (accountId == null) {
				accountId = addAccount(remoteSystem, remoteId, token, info.getId());
			} else {
				updateAccount(accountId, remoteSystem, remoteId, token, info.getId());
			}
			if (!characters.isEmpty()) {
				addCharacters(accountId, characters);
			}

			trans.commit();
			return accountId;
		} catch (IOException | ProtocolError | ProtocolException | SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public String updateAndDeleteAccounts() {
		StringBuilder result = new StringBuilder();
		try (final Transaction trans = Transaction.getTransaction()) {
			result.append(updateAccountsFromTokens());
			result.append(updateAccountsFromGuildList());
			log.info("Deleted " + db.accountsDeleteWhenUnused(DateHelper.getNow()) + " unused accounts");
			trans.commit();
		} catch (SQLException e) {
			log.error("Cant refresh chars for guild", e);
			throw new RuntimeException(e);
		}

		return result.toString();
	}

	private String updateAccountsFromGuildList() {
		StringBuilder result = new StringBuilder();
		try {
			log.info("Updating characters from guild list");
			final Calendar onlyDeleteCharactersAddedBefore = DateHelper.getNow();
			onlyDeleteCharactersAddedBefore.add(Calendar.DATE, config.afterCharacterAddDontDeleteForDays() * -1);
			// For old Battle.net API
			/*
			List<BNetProfileWowCharacter> bnetGuildCharacters = executor
					.execute(config.uriBNetGuildCharacters(guildName, guildServer), new BNetGuildMembersRequest());
			*/
			// For new Battle.net API
			List<WowCharacter> toDelCharacters = new LinkedList<>(db.charactersGetAll());
			for (ListIterator<WowCharacter> it = toDelCharacters.listIterator(); it.hasNext();) {
				final @Nonnull WowCharacter character = it.next();
				final Long accountId = db.accountIdGetByCharacter(character.getName(), character.getServer());
				if (accountId == null) {
					throw new IllegalStateException(
							"Cant find account-id for character " + character.getName() + "-" + character.getServer());
				}

				final @CheckForNull BNetProfileWowCharacter matchingBnetCharacter = bnetGuildCharacters.stream()
						.filter(bnetCharacter -> character.getName().equalsIgnoreCase(bnetCharacter.getName())
								&& character.getServer().equalsIgnoreCase(bnetCharacter.getServer()))
						.findFirst().orElse(null);
				if (character.getAddedDate().before(onlyDeleteCharactersAddedBefore) && matchingBnetCharacter == null) {
					log.info("Delete no longer existing character " + character.getName() + "-" + character.getServer()
							+ " from account " + accountId);
					db.characterDelete(accountId, character);
				} else if (matchingBnetCharacter != null
						&& !Objects.equals(character.getRank(), matchingBnetCharacter.getGuildRank())) {
					final int newRank = matchingBnetCharacter.getGuildRank();
					db.characterUpdateRank(character, newRank);
				}
			}
		} catch (Throwable t) {
			log.error("Cant update characters from guild list: ", t);
			result.append("Cant update characters from guild list: ").append(t.getMessage()).append('\n');
		}
		return result.toString();
	}

	private String updateAccountsFromTokens() {
		StringBuilder result = new StringBuilder();
		List<Account> toUpdateAccounts = db.accountsGetWithTokenValidUntil(DateHelper.getNow());
		for (Account acc : toUpdateAccounts) {
			@CheckForNull
			Throwable exception;
			try {
				int numTries = 0;
				do {
					numTries++;
					exception = null;
					try {
						updateCharactersForAccount(acc);
					} catch (ServerErrorException e) {
						log.warn("Cant update characters for " + acc.getAccountId() + ". Retry "
								+ Boolean.toString(numTries < config.bnetNumRetries()), e);
						exception = e;
					}
				} while (exception != null && numTries < config.bnetNumRetries());
			} catch (Throwable t) {
				exception = t;
			}
			if (exception != null) {
				log.error("Cant update characters for " + acc.getAccountId() + ": ", exception);
				result.append("Cant update characters for ").append(acc.getAccountId()).append(": ")
						.append(exception.getClass().getSimpleName()).append(' ').append(exception.getMessage())
						.append('\n');

			}
		}
		return result.toString();
	}

	private void updateCharactersForAccount(final @Nonnull Account acc)
			throws RedirectionException, UnexpectedStatusException, IOException, ProtocolError, ProtocolException {
		log.info("Update characters for " + acc.getAccountId() + " with " + acc.getToken());
		{
			HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
			List<BNetProfileWowCharacter> bnetCharacters = executor.execute(config.uriBNetAccountCharacters(),
					new HeaderDecorated<>(new BNetProfileWowCharactersRequest(),
							new TokenHeaderDecorator(acc.getToken())));
			bnetCharacters = removeNonGuildCharacters(bnetCharacters);
			bnetCharacters = Collections.unmodifiableList(bnetCharacters);
			addCharacters(acc.getAccountId(), bnetCharacters);
		}
	}

	public List<Long> getAllRemoteIdsByRemoteSystem(final String remoteSystemName) {
		try (final Transaction trans = Transaction.getTransaction()) {
			return db.accountsGetByRemoteSystem(remoteSystemName);
		}
	}

	public boolean isOfficer(final String remoteSystemName, final Long remoteId) {
		try (final Transaction trans = Transaction.getTransaction()) {
			final Integer rank = db.accountGetMinRankByRemoteSystemId(remoteSystemName, remoteId);
			if (rank == null) {
				return false;
			}
			if (rank <= config.officerMaxRank()) {
				return true;
			} else {
				return false;
			}
		}
	}
}
