package eu._4fh.guildsync.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.dmfs.oauth2.client.OAuth2AccessToken;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.guildsync.data.Account;
import eu._4fh.guildsync.data.RemoteCommand;
import eu._4fh.guildsync.data.WowCharacter;
import eu._4fh.guildsync.helper.DateHelper;
import eu._4fh.guildsync.helper.Pair;

/*
CREATE TRIGGER validate_unique_remote_system_and_remote_id_per_guild_on_insert
  BEFORE INSERT ON account_remote_ids
  FOR EACH ROW
BEGIN
	IF (SELECT COUNT(*) FROM account_remote_ids WHERE
      remote_system_name = NEW.remote_system_name AND remote_id = NEW.remote_id AND
      account_id IN (SELECT id FROM accounts WHERE guild_id IN (SELECT guild_id FROM accounts WHERE id = NEW.account_id))) > 0 THEN
        SET @message_text = concat('remote_system_name-remote_id ',NEW.remote_system_name,'-',NEW.remote_id,' not unique for guild of account ',NEW.account_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message_text;
	END IF;
END
 */
public class DbWrapper {
	private final @NonNull String guildIdSubQuery;
	private final @NonNull long guildId;

	public DbWrapper(long guildId) {
		this.guildId = guildId;
		guildIdSubQuery = "SELECT id FROM accounts WHERE guild_id = " + String.valueOf(guildId) + " ";
	}

	private static Transaction getTrans() {
		return Transaction.getTransaction();
	}

	public @NonNull List<RemoteCommand> remoteCommandsGetByRemoteSystemName(@NonNull String remoteSystemName) {
		final String sql = "SELECT rc.id, ari.remote_id, rc.command "
				+ "FROM remote_commands AS rc INNER JOIN account_remote_ids AS ari "
				+ "ON rc.account_id = ari.account_id AND rc.remote_system_name = ari.remote_system_name "
				+ "WHERE rc.remote_system_name = ? AND rc.account_id IN (" + guildIdSubQuery + ") ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystemName);
			ResultSet rs = stmt.executeQuery();
			List<RemoteCommand> remoteCommands = new ArrayList<>();
			while (rs.next()) {
				RemoteCommand command = new RemoteCommand(rs.getLong(1), rs.getLong(2),
						RemoteCommand.Commands.valueOf(rs.getString(3)));
				remoteCommands.add(command);
			}

			return remoteCommands;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void remoteCommandsDeleteByRemoteSystemNameAndId(@NonNull String remoteSystemName, long toId) {
		final String sql = "DELETE FROM remote_commands WHERE remote_system_name = ? AND id <= ? "
				+ "AND account_id IN (" + guildIdSubQuery + ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystemName);
			stmt.setLong(2, toId);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @CheckForNull Long accountIdGetByCharacter(@NonNull String name, @NonNull String server) {
		final String sql = "SELECT account_id FROM characters WHERE lower(name) = lower(?) AND lower(server) = lower(?) AND account_id IN ("
				+ guildIdSubQuery + ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, name);
			stmt.setString(2, server);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}
			return rs.getLong(1);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @CheckForNull Long accountIdGetByBattleNetId(long battleNetId) {
		if (battleNetId < 1) {
			throw new IllegalArgumentException("battleNetId < 1: " + battleNetId);
		}

		final String sql = "SELECT id FROM accounts WHERE battle_net_id = ? AND guild_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, battleNetId);
			stmt.setLong(2, guildId);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}
			return rs.getLong(1);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void accountUpdateById(long accountId, long battleNetId, OAuth2AccessToken token) {
		final String sql = "UPDATE accounts SET battle_net_id = ?, token = ?, token_valid_until = ? WHERE id = ? AND guild_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, battleNetId);
			stmt.setString(2, token.accessToken().toString());
			stmt.setDate(3, DateHelper.dateTimeToSqlDate(token.expirationDate()));
			stmt.setLong(4, accountId);
			stmt.setLong(5, guildId);
			int updatedRows = stmt.executeUpdate();
			if (updatedRows != 1) {
				throw new IllegalStateException(
						"Cant update account for accountId " + accountId + ", db update result was " + updatedRows);
			}
		} catch (SQLException | ProtocolException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean characterExists(final @NonNull long accountId, final @NonNull WowCharacter newCharacter) {
		final String sql = "SELECT 1 FROM characters WHERE name = ? AND server = ? AND account_id = ? AND account_id IN ("
				+ guildIdSubQuery + ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, newCharacter.getName());
			stmt.setString(2, newCharacter.getServer());
			stmt.setLong(3, accountId);
			ResultSet rs = stmt.executeQuery();
			boolean exists = rs.next();
			rs.close();
			return exists;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void characterAdd(final long accountId, final @NonNull WowCharacter newCharacter) {
		final String sql = "INSERT INTO characters(account_id, name, server, rank, added) VALUES (?, ?, ?, ?, ?)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			stmt.setString(2, newCharacter.getName());
			stmt.setString(3, newCharacter.getServer());
			stmt.setInt(4, newCharacter.getRank());
			stmt.setDate(5, DateHelper.calendarToSqlDate(DateHelper.getToday()));
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void characterDelete(final @NonNull long accountId, final @NonNull WowCharacter character) {
		final String sql = "DELETE FROM characters WHERE account_id = ? AND name = ? AND server = ? AND account_id IN ("
				+ guildIdSubQuery + ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			stmt.setString(2, character.getName());
			stmt.setString(3, character.getServer());
			if (stmt.executeUpdate() != 1) {
				throw new RuntimeException("No characters or to many characters deleted");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @NonNull Long accountAdd() {
		final String sql = "INSERT INTO accounts(guild_id) VALUES(?)";
		try (Transaction trans = getTrans();
				PreparedStatement stmt = trans.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			stmt.setLong(1, guildId);
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (!rs.next()) {
				throw new RuntimeException("Didnt generate a new ID???");
			}
			long newId = rs.getLong(1);
			rs.close();
			return newId;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @NonNull Long accountAdd(long battleNetId, OAuth2AccessToken token) {
		long newAccountId = accountAdd();
		accountUpdateById(newAccountId, battleNetId, token);
		return newAccountId;
	}

	public @NonNull Integer charactersGetNumByAccountId(long accountId) {
		final String sql = "SELECT COUNT(*) FROM characters WHERE account_id = ? AND account_id IN (" + guildIdSubQuery
				+ ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return 0;
			}
			int result = rs.getInt(1);
			rs.close();
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void remoteCommandAdd(final @NonNull String remoteSystem, final long accountId,
			final @NonNull RemoteCommand.Commands command) {
		final String sql = "INSERT INTO remote_commands(remote_system_name, account_id, command, command_added) VALUES(?, ?, ?, ?)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystem);
			stmt.setLong(2, accountId);
			stmt.setString(3, command.name());
			stmt.setDate(4, DateHelper.calendarToSqlDate(DateHelper.getToday()));
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @CheckForNull Long remoteAccountIdGetByAccountId(final long accountId,
			final @NonNull String remoteSystemName) {
		final String sql = "SELECT remote_id FROM account_remote_ids WHERE account_id = ? AND remote_system_name = ? AND account_id IN ("
				+ guildIdSubQuery + ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			stmt.setString(2, remoteSystemName);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}
			long result = rs.getLong(1);
			rs.close();
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void remoteAccountAdd(long accountId, @NonNull String remoteSystem, long remoteId) {
		final String sql = "INSERT INTO account_remote_ids(account_id, remote_system_name, remote_id) VALUES (?, ?, ?)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			stmt.setString(2, remoteSystem);
			stmt.setLong(3, remoteId);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	public List<WowCharacter> charactersGetByRemoteAccountId(final @NonNull String remoteSystemName,
			final @NonNull Long remoteAccountId) {
		final String sql = "SELECT name, server, rank, added FROM characters WHERE "
				+ "account_id IN (SELECT account_id FROM account_remote_ids WHERE remote_system_name = ? AND remote_id = ?) AND "
				+ "account_id IN (" + guildIdSubQuery + ") ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystemName);
			stmt.setLong(2, remoteAccountId);
			return Collections.unmodifiableList(characterResultSetToList(stmt.executeQuery()));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public List<WowCharacter> charactersGetByAccountId(long accountId) {
		final String sql = "SELECT name, server, rank, added FROM characters WHERE account_id = ? AND account_id IN ("
				+ guildIdSubQuery + ") ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			return Collections.unmodifiableList(characterResultSetToList(stmt.executeQuery()));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public List<WowCharacter> charactersGetAll() {
		final String sql = "SELECT name, server, rank, added FROM characters WHERE account_id IN (" + guildIdSubQuery
				+ ") ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			return Collections.unmodifiableList(characterResultSetToList(stmt.executeQuery()));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private List<WowCharacter> characterResultSetToList(final @NonNull ResultSet rs) throws SQLException {
		List<WowCharacter> result = new ArrayList<>();
		while (rs.next()) {
			result.add(new WowCharacter(rs.getString(1), rs.getString(2), rs.getInt(3),
					DateHelper.sqlDateToCalendar(rs.getDate(4))));
		}
		rs.close();
		return Collections.unmodifiableList(result);
	}

	public List<Account> accountsGetWithTokenValidUntil(Calendar cal) {
		final String sql = "SELECT id, token FROM accounts "
				+ "WHERE token IS NOT NULL and token_valid_until IS NOT NULL and token_valid_until > ? AND guild_id = ? ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			List<Account> result = new ArrayList<>();
			stmt.setDate(1, DateHelper.calendarToSqlDate(cal));
			stmt.setLong(2, guildId);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				result.add(new Account(rs.getLong(1), rs.getString(2)));
			}
			rs.close();
			return Collections.unmodifiableList(result);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static String guildGetApiKeyById(final @NonNull long guildId) {
		final String sql = "SELECT api_key FROM guilds WHERE id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, guildId);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}
			String apiKey = rs.getString(1);
			rs.close();
			return apiKey;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static String guildGetMacKeyById(Long remoteId) {
		final String sql = "SELECT mac_key FROM guilds WHERE id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, remoteId);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}
			String macKey = rs.getString(1);
			rs.close();
			return macKey;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	public static long guildAdd(String guildName, String guildServer, String apiKey, String macKey) {
		final String sql = "INSERT INTO guilds(name, server, api_key, mac_key) VALUES (?, ?, ?, ?)";
		try (Transaction trans = getTrans();
				PreparedStatement stmt = trans.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			stmt.setString(1, guildName);
			stmt.setString(2, guildServer);
			stmt.setString(3, apiKey);
			stmt.setString(4, macKey);
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (!rs.next()) {
				throw new RuntimeException("Didnt generate a new ID?");
			}
			long newId = rs.getLong(1);
			rs.close();
			return newId;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public int accountsDeleteWhenUnused(Calendar today) {
		final String sql = "DELETE FROM accounts WHERE guild_id = ? AND "
				+ "(token_valid_until < ? OR token_valid_until IS NULL) AND "
				+ "id NOT IN (SELECT account_id FROM remote_commands) AND "
				+ "id NOT IN (SELECT account_id FROM characters)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, guildId);
			stmt.setDate(2, DateHelper.calendarToSqlDate(today));
			return stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	public void guildUpdateLastRefresh(Calendar today) {
		final String sql = "UPDATE guilds SET last_refresh = ? WHERE id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setDate(1, DateHelper.calendarToSqlDate(today));
			stmt.setLong(2, guildId);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public Pair<String, String> guildGetNameAndServer() {
		final String sql = "SELECT name, server FROM guilds WHERE id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, guildId);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				throw new RuntimeException("Unkown guild id " + guildId);
			}
			return new Pair<>(rs.getString(1), rs.getString(2));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public int remoteCommandsDeleteLongUnused(final @NonNull Calendar cal) {
		final String sql = "DELETE FROM remote_commands WHERE command_added < ? AND " + "account_id IN ("
				+ guildIdSubQuery + ")";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setDate(1, DateHelper.calendarToSqlDate(cal));
			return stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @CheckForNull Long accountIdGetByRemoteAccount(final @NonNull String remoteSystemName,
			final @NonNull long remoteAccountId) {
		final String sql = "SELECT account_id FROM account_remote_ids WHERE remote_system_name = ? AND remote_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystemName);
			stmt.setLong(2, remoteAccountId);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}
			return rs.getLong(1);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void characterUpdateRank(final @NonNull WowCharacter character, final @NonNull int newRank) {
		final String sql = "UPDATE characters SET rank = ? WHERE name = ? AND server = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setInt(1, newRank);
			stmt.setString(2, character.getName());
			stmt.setString(3, character.getServer());
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
}
