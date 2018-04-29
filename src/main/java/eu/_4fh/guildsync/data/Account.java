package eu._4fh.guildsync.data;

import edu.umd.cs.findbugs.annotations.NonNull;

public class Account {
	private final @NonNull long accountId;
	private final @NonNull String token;

	public Account(final @NonNull long accountId, final @NonNull String token) {
		this.accountId = accountId;
		this.token = token;
	}

	public @NonNull long getAccountId() {
		return accountId;
	}

	public @NonNull String getToken() {
		return token;
	}
}
