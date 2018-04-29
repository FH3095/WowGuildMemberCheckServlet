package eu._4fh.guildsync.data;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BNetProfileWowCharacter {
	private final @NonNull String name;
	private final @NonNull String server;
	private final @CheckForNull String guildName;
	private final @CheckForNull String guildServer;

	public BNetProfileWowCharacter(final @NonNull String name, final @NonNull String server,
			final @CheckForNull String guildName, final @CheckForNull String guildServer) {
		this.name = name;
		this.server = server;
		this.guildName = guildName;
		this.guildServer = guildServer;
	}

	public @NonNull String getName() {
		return name;
	}

	public @NonNull String getServer() {
		return server;
	}

	public @CheckForNull String getGuildName() {
		return guildName;
	}

	public @CheckForNull String getGuildServer() {
		return guildServer;
	}
}
