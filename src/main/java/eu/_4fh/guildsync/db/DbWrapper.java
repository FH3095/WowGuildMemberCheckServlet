package eu._4fh.guildsync.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.WillNotClose;

import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.dmfs.oauth2.client.OAuth2AccessToken;

import eu._4fh.guildsync.data.Account;
import eu._4fh.guildsync.data.WowCharacter;
import eu._4fh.guildsync.helper.DateHelper;

public class DbWrapper {
	public DbWrapper() {
	}

	private static Transaction getTrans() {
		return Transaction.getTransaction();
	}

	public List<Account> accountsGetWithTokenValidUntil(Calendar cal) {
		final String sql = "SELECT id, token FROM accounts "
				+ "WHERE token IS NOT NULL and token_valid_until IS NOT NULL and token_valid_until > ? ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			List<Account> result = new ArrayList<>();
			stmt.setTimestamp(1, DateHelper.calendarToSqlDate(cal));
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					result.add(new Account(rs.getLong(1), rs.getString(2)));
				}
				return Collections.unmodifiableList(result);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public List<Long> accountsGetByRemoteSystem(final @Nonnull String remoteSystemName) {
		final String sql = "SELECT remote_id FROM account_remote_ids WHERE remote_system_name = ? ORDER BY remote_id";
		try (final Transaction trans = getTrans(); final PreparedStatement stmt = trans.prepareStatement(sql)) {
			final List<Long> result = new ArrayList<>();
			stmt.setString(1, remoteSystemName);
			try (final ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					result.add(rs.getLong(1));
				}
				return Collections.unmodifiableList(result);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public int accountsDeleteWhenUnused(Calendar today) {
		final String sql = "DELETE FROM accounts WHERE " + "(token_valid_until < ? OR token_valid_until IS NULL) AND "
				+ "id NOT IN (SELECT account_id FROM characters)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setTimestamp(1, DateHelper.calendarToSqlDate(today));
			return stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	public @CheckForNull Long accountIdGetByCharacter(@Nonnull String name, @Nonnull String server) {
		final String sql = "SELECT account_id FROM characters WHERE lower(name) = lower(?) AND lower(server) = lower(?)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, name);
			stmt.setString(2, server);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return rs.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @CheckForNull Long accountIdGetByBattleNetId(long battleNetId) {
		if (battleNetId < 1) {
			throw new IllegalArgumentException("battleNetId < 1: " + battleNetId);
		}

		final String sql = "SELECT id FROM accounts WHERE battle_net_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, battleNetId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return rs.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @CheckForNull Long accountIdGetByRemoteAccount(final @Nonnull String remoteSystemName,
			final @Nonnull long remoteAccountId) {
		final String sql = "SELECT account_id FROM account_remote_ids WHERE remote_system_name = ? AND remote_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystemName);
			stmt.setLong(2, remoteAccountId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return rs.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @Nonnull Long accountAdd() {
		final String sql = "INSERT INTO accounts() VALUES()";
		try (Transaction trans = getTrans();
				PreparedStatement stmt = trans.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			stmt.executeUpdate();
			try (ResultSet rs = stmt.getGeneratedKeys()) {
				if (!rs.next()) {
					throw new RuntimeException("Didnt generate a new ID???");
				}
				return rs.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @Nonnull Long accountAdd(long battleNetId, OAuth2AccessToken token) {
		long newAccountId = accountAdd();
		accountUpdateById(newAccountId, battleNetId, token);
		return newAccountId;
	}

	public void accountUpdateById(long accountId, long battleNetId, OAuth2AccessToken token) {
		final String sql = "UPDATE accounts SET battle_net_id = ?, token = ?, token_valid_until = ? WHERE id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, battleNetId);
			stmt.setString(2, token.accessToken().toString());
			stmt.setTimestamp(3, DateHelper.dateTimeToSqlDate(token.expirationDate()));
			stmt.setLong(4, accountId);
			int updatedRows = stmt.executeUpdate();
			if (updatedRows != 1) {
				throw new IllegalStateException(
						"Cant update account for accountId " + accountId + ", db update result was " + updatedRows);
			}
		} catch (SQLException | ProtocolException e) {
			throw new RuntimeException(e);
		}
	}

	public List<WowCharacter> charactersGetAll() {
		final String sql = "SELECT name, server, rank, added FROM characters ORDER BY id";
		try (Transaction trans = getTrans();
				PreparedStatement stmt = trans.prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {
			return Collections.unmodifiableList(characterResultSetToList(rs));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean characterExists(final @Nonnull long accountId, final @Nonnull WowCharacter newCharacter) {
		final String sql = "SELECT 1 FROM characters WHERE name = ? AND server = ? AND account_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, newCharacter.getName());
			stmt.setString(2, newCharacter.getServer());
			stmt.setLong(3, accountId);
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public @Nonnull Integer charactersGetNumByAccountId(long accountId) {
		final String sql = "SELECT COUNT(*) FROM characters WHERE account_id = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return 0;
				}
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public List<WowCharacter> charactersGetByRemoteAccountId(final @Nonnull String remoteSystemName,
			final @Nonnull Long remoteAccountId) {
		final String sql = "SELECT name, server, rank, added FROM characters WHERE "
				+ "account_id IN (SELECT account_id FROM account_remote_ids WHERE remote_system_name = ? AND remote_id = ?) "
				+ "ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setString(1, remoteSystemName);
			stmt.setLong(2, remoteAccountId);
			try (ResultSet rs = stmt.executeQuery()) {
				return Collections.unmodifiableList(characterResultSetToList(rs));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public List<WowCharacter> charactersGetByAccountId(long accountId) {
		final String sql = "SELECT name, server, rank, added FROM characters WHERE account_id = ? ORDER BY id";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			try (ResultSet rs = stmt.executeQuery()) {
				return Collections.unmodifiableList(characterResultSetToList(rs));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void characterAdd(final long accountId, final @Nonnull WowCharacter newCharacter) {
		final String sql = "INSERT INTO characters(account_id, name, server, rank, added) VALUES (?, ?, ?, ?, ?)";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			stmt.setString(2, newCharacter.getName());
			stmt.setString(3, newCharacter.getServer());
			stmt.setInt(4, newCharacter.getRank());
			stmt.setTimestamp(5, DateHelper.calendarToSqlDate(DateHelper.getNow()));
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void characterUpdateRank(final @Nonnull WowCharacter character, final @Nonnull int newRank) {
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

	public void characterDelete(final @Nonnull long accountId, final @Nonnull WowCharacter character) {
		final String sql = "DELETE FROM characters WHERE account_id = ? AND name = ? AND server = ?";
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

	public @CheckForNull Long remoteAccountIdGetByAccountId(final long accountId,
			final @Nonnull String remoteSystemName) {
		final String sql = "SELECT remote_id FROM account_remote_ids WHERE account_id = ? AND remote_system_name = ?";
		try (Transaction trans = getTrans(); PreparedStatement stmt = trans.prepareStatement(sql)) {
			stmt.setLong(1, accountId);
			stmt.setString(2, remoteSystemName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return rs.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void remoteAccountAdd(long accountId, @Nonnull String remoteSystem, long remoteId) {
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

	private List<WowCharacter> characterResultSetToList(final @Nonnull @WillNotClose ResultSet rs) throws SQLException {
		List<WowCharacter> result = new ArrayList<>();
		while (rs.next()) {
			result.add(new WowCharacter(rs.getString(1), rs.getString(2), rs.getInt(3),
					DateHelper.sqlDateToCalendar(rs.getTimestamp(4))));
		}
		return Collections.unmodifiableList(result);
	}
}
