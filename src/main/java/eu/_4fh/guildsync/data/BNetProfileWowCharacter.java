package eu._4fh.guildsync.data;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class BNetProfileWowCharacter {
	private final @Nonnull String name;
	private final @Nonnull String server;
	private final @Nonnull int guildRank;

	public BNetProfileWowCharacter(final @Nonnull String name, final @Nonnull String server, final int guildRank) {
		this.name = name;
		this.server = server;
		if (guildRank < 0) {
			throw new IllegalArgumentException("GuildRank below 0");
		}
		this.guildRank = Math.min(guildRank, Short.MAX_VALUE);
	}

	public @Nonnull String getName() {
		return name;
	}

	public @Nonnull String getServer() {
		return server;
	}

	public int getGuildRank() {
		return guildRank;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, server);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof BNetProfileWowCharacter)) {
			return false;
		}
		BNetProfileWowCharacter other = (BNetProfileWowCharacter) obj;
		return Objects.equals(name, other.name) && Objects.equals(server, other.server);
	}

	@Override
	public String toString() {
		return "BNetProfileWowCharacter [name=" + name + ", server=" + server + "]";
	}
}
