package eu._4fh.guildsync.data;

import javax.annotation.Nonnull;

public class Account {
	private final @Nonnull long accountId;
	private final @Nonnull String token;

	public Account(final @Nonnull long accountId, final @Nonnull String token) {
		this.accountId = accountId;
		this.token = token;
	}

	public @Nonnull long getAccountId() {
		return accountId;
	}

	public @Nonnull String getToken() {
		return token;
	}
}
