package eu._4fh.guildsync.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.guildsync.config.Config;
import eu._4fh.guildsync.data.Account;
import eu._4fh.guildsync.data.BNetProfileInfo;
import eu._4fh.guildsync.data.BNetProfileWowCharacter;
import eu._4fh.guildsync.data.RemoteCommand;
import eu._4fh.guildsync.data.RemoteCommand.Commands;
import eu._4fh.guildsync.data.WowCharacter;
import eu._4fh.guildsync.db.DbWrapper;
import eu._4fh.guildsync.db.Transaction;
import eu._4fh.guildsync.helper.DateHelper;
import eu._4fh.guildsync.helper.Pair;
import eu._4fh.guildsync.service.requests.BNetGuildMembersRequest;
import eu._4fh.guildsync.service.requests.BNetProfileInfoRequest;
import eu._4fh.guildsync.service.requests.BNetProfileWowCharactersRequest;
import eu._4fh.guildsync.service.requests.TokenHeaderDecorator;

public class SyncService {
	private static final Logger log = LoggerFactory.getLogger(SyncService.class);
	private static final @NonNull Config config = Config.getInstance();

	private final @NonNull DbWrapper db;
	private final @NonNull long guildId;
	private final String guildName;
	private final String guildServer;

	public SyncService(final @NonNull long guildId) {
		this.guildId = guildId;
		db = new DbWrapper(guildId);
		Pair<String, String> guildNameAndServer = db.guildGetNameAndServer();
		guildName = guildNameAndServer.getValue1();
		guildServer = guildNameAndServer.getValue2();
	}

	private void updateAccount(long accountId, @NonNull String remoteSystem, long remoteId, OAuth2AccessToken token,
			long battleNetId, List<BNetProfileWowCharacter> characters) {
		try (Transaction transaction = Transaction.getTransaction()) {
			db.accountUpdateById(accountId, battleNetId, token);

			boolean addedRemoteAccount = false;
			if (db.remoteAccountIdGetByAccountId(accountId, remoteSystem) == null) {
				db.remoteAccountAdd(accountId, remoteSystem, remoteId);
				addedRemoteAccount = true;
			}

			boolean hasNewCharacter = addCharacters(accountId, characters);
			if (hasNewCharacter) {
				createRemoteCommands(accountId, RemoteCommand.Commands.ACC_UPDATE);
			} else if (addedRemoteAccount) {
				db.remoteCommandAdd(remoteSystem, accountId, RemoteCommand.Commands.ACC_UPDATE);
			}
			transaction.commit();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private long addAccount(@NonNull String remoteSystem, @NonNull long remoteId, OAuth2AccessToken token,
			long battleNetId, List<BNetProfileWowCharacter> characters) {
		try (Transaction transaction = Transaction.getTransaction()) {
			long newAccountId = db.accountAdd(battleNetId, token);

			db.remoteAccountAdd(newAccountId, remoteSystem, remoteId);

			boolean charactersAdded = addCharacters(newAccountId, characters);
			if (charactersAdded) {
				createRemoteCommands(newAccountId, RemoteCommand.Commands.ACC_UPDATE);
			}
			transaction.commit();
			return newAccountId;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean addCharacters(long accountId, List<BNetProfileWowCharacter> characters) {
		boolean hasNewCharacter = false;
		for (BNetProfileWowCharacter character : characters) {
			if (!guildName.equals(character.getGuildName()) || !guildServer.equals(character.getGuildServer())) {
				continue;
			}
			WowCharacter newCharacter = new WowCharacter(character.getName(), character.getServer());
			if (!db.characterExists(accountId, newCharacter)) {
				log.debug("Add character " + newCharacter.getName() + "-" + newCharacter.getServer() + " for "
						+ accountId);
				db.characterAdd(accountId, newCharacter);
				hasNewCharacter = true;
			}
		}
		return hasNewCharacter;
	}

	private void createRemoteCommands(final @NonNull long accountId, final @NonNull Commands command) {
		for (String remoteSystemName : config.allowedRemoteSystems()) {
			Long remoteAccountId = db.remoteAccountIdGetByAccountId(accountId, remoteSystemName);
			if (remoteAccountId == null) {
				continue;
			}
			db.remoteCommandAdd(remoteSystemName, accountId, command);
		}
	}

	public long addOrUpdateAccount(final @NonNull OAuth2AccessToken token, final @NonNull String remoteSystem,
			final @NonNull long remoteId) {
		HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
		try {
			BNetProfileInfo info = executor.execute(config.uriBNetAccountInfo(),
					new BearerAuthenticatedRequest<>(new BNetProfileInfoRequest(), token));
			List<BNetProfileWowCharacter> characters = executor.execute(config.uriBNetAccountCharacters(),
					new BearerAuthenticatedRequest<>(new BNetProfileWowCharactersRequest(), token));

			Long accountId = null;
			accountId = db.accountIdGetByBattleNetId(info.getId());
			if (accountId == null) {
				log.debug("Account not found by id " + info.getId() + ", search by characters");
				for (BNetProfileWowCharacter character : characters) {
					accountId = db.accountIdGetByCharacter(character.getName(), character.getServer());
					if (accountId != null) {
						log.debug("Account found by character " + character.getName() + "-" + character.getServer());
						break;
					}
				}
			}
			log.debug("Account-ID: " + String.valueOf(accountId));

			if (accountId == null) {
				accountId = addAccount(remoteSystem, remoteId, token, info.getId(), characters);
			} else {
				updateAccount(accountId, remoteSystem, remoteId, token, info.getId(), characters);
			}
			return accountId;
		} catch (IOException | ProtocolError | ProtocolException e) {
			throw new RuntimeException(e);
		}
	}

	public long addOrUpdateAccount(final @NonNull String remoteSystemName, final @NonNull long remoteAccountId,
			final @NonNull WowCharacter character) {
		try (Transaction transaction = Transaction.getTransaction()) {
			@CheckForNull
			Long accountId = null;
			final @NonNull boolean accountExists;
			final @NonNull boolean remoteAccountExists;
			final @NonNull boolean charExists;
			{
				final @CheckForNull Long accountIdByChar = db.accountIdGetByCharacter(character.getName(),
						character.getServer());
				final @CheckForNull Long accountIdByRemote = db.accountIdGetByRemoteAccount(remoteSystemName,
						remoteAccountId);
				if (accountIdByChar != null && accountIdByRemote != null
						&& !accountIdByChar.equals(accountIdByRemote)) {
					throw new IllegalArgumentException(
							"Characters " + character.toString() + " already exists and belongs to " + accountIdByChar
									+ " but should be added to " + accountIdByRemote);
				}
				accountId = accountIdByChar != null ? accountIdByChar : accountIdByRemote;
				accountExists = accountId != null;
				remoteAccountExists = accountIdByRemote != null;
				charExists = accountIdByChar != null;
			}

			// Account dont exists -> Create
			if (accountId == null) {
				// Wenn es den Char nicht gibt, gibt es den Account auch nicht -> Beides anlegen
				accountId = db.accountAdd();
				log.info("Added new account " + accountId + " for character " + character.toString() + " to remote "
						+ remoteAccountId + "@" + remoteSystemName);
			}
			if (!remoteAccountExists) {
				// Den Account samt Char gab es schon. Nur die Verknüpfung zur RemoteId hinzufügen.
				db.remoteAccountAdd(accountId, remoteSystemName, remoteAccountId);
				// Der Account samt Char ist nur für ein System neu. Dieses System zum Update zwingen.
				if (charExists) {
					db.remoteCommandAdd(remoteSystemName, accountId, RemoteCommand.Commands.ACC_UPDATE);
				}
				log.info("Added account " + accountId + " with character " + character.toString() + " to remote "
						+ remoteAccountId + "@" + remoteSystemName);
			}
			if (!charExists) {
				db.characterAdd(accountId, character);
				createRemoteCommands(accountId, RemoteCommand.Commands.ACC_UPDATE);
			}

			log.info("Added char. " + (accountExists ? "" : "New ") + "account: " + accountId + " ; "
					+ (remoteAccountExists ? "" : "New ") + "remote account: " + remoteAccountId + "@"
					+ remoteSystemName + " ; " + (charExists ? "" : "New ") + "character: " + character.toString());

			transaction.commit();
			return accountId;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public String updateAndDeleteAccounts() {
		StringBuilder result = new StringBuilder();
		try (Transaction trans = Transaction.getTransaction()) {
			result.append(updateAccountsFromTokens());
			result.append(updateAccountsFromGuildList());
			log.info("Deleted " + db.accountsDeleteWhenUnused(DateHelper.getToday()) + " unused accounts for guild "
					+ guildId);
			db.guildUpdateLastRefresh(DateHelper.getToday());

			Calendar longUnusedDate = DateHelper.getToday();
			longUnusedDate.roll(Calendar.MONTH, -1);
			db.remoteCommandsDeleteLongUnused(longUnusedDate);

			trans.commit();
		} catch (SQLException e) {
			log.error("Cant refresh chars for guild " + guildId, e);
			throw new RuntimeException(e);
		}

		return result.toString();
	}

	private String updateAccountsFromGuildList() {
		StringBuilder result = new StringBuilder();
		try {
			log.info("Updating characters from guild list for " + guildId);
			List<Long> changedAccounts = new ArrayList<>();
			HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
			List<BNetProfileWowCharacter> bnetGuildCharacters = executor
					.execute(config.uriBNetGuildCharacters(guildName, guildServer), new BNetGuildMembersRequest());
			// For new Battle.net API
			/*
			List<BNetProfileWowCharacter> bnetGuildCharacters = executor.execute(
					config.uriBNetGuildCharacters(guildName, guildServer),
					new BearerAuthenticatedRequest<>(new BNetGuildMembersRequest(), config.token()));
			*/
			List<WowCharacter> toDelCharacters = new LinkedList<>(db.charactersGetAll());
			for (ListIterator<WowCharacter> it = toDelCharacters.listIterator(); it.hasNext();) {
				WowCharacter character = it.next();
				if (bnetGuildCharacters.stream()
						.noneMatch(bnetCharacter -> character.getName().equals(bnetCharacter.getName())
								&& character.getServer().equals(bnetCharacter.getServer()))) {
					Long accountId = db.accountIdGetByCharacter(character.getName(), character.getServer());
					if (accountId == null) {
						throw new IllegalStateException("Cant find account-id for character " + character.getName()
								+ "-" + character.getServer());
					}
					log.info("Delete no longer existing character " + character.getName() + "-" + character.getServer()
							+ " from account " + accountId);
					db.characterDelete(accountId, character);
					changedAccounts.add(accountId);
				}
			}
			for (Long changedAccount : changedAccounts) {
				createRemoteCommands(changedAccount, RemoteCommand.Commands.ACC_UPDATE);
			}
		} catch (Throwable t) {
			log.error("Cant update characters from guild list: ", t);
			result.append("Cant update characters from guild list: ").append(t.getMessage()).append('\n');
		}
		return result.toString();
	}

	private String updateAccountsFromTokens() {
		StringBuilder result = new StringBuilder();
		List<Account> toUpdateAccounts = db.accountsGetWithTokenValidUntil(DateHelper.getToday());
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

	private void updateCharactersForAccount(final @NonNull Account acc)
			throws RedirectionException, UnexpectedStatusException, IOException, ProtocolError, ProtocolException {
		boolean charactersAdded = false;
		log.info("Update characters for " + acc.getAccountId() + " with " + acc.getToken());
		{
			HttpRequestExecutor executor = new HttpUrlConnectionExecutor();
			List<BNetProfileWowCharacter> bnetCharacters = executor.execute(config.uriBNetAccountCharacters(),
					new HeaderDecorated<>(new BNetProfileWowCharactersRequest(),
							new TokenHeaderDecorator(acc.getToken())));
			bnetCharacters = Collections.unmodifiableList(bnetCharacters);
			charactersAdded = addCharacters(acc.getAccountId(), bnetCharacters);

			if (charactersAdded) {
				createRemoteCommands(acc.getAccountId(), RemoteCommand.Commands.ACC_UPDATE);
			}
		}
	}
}
