package eu._4fh.guildsync.data;

import java.util.Calendar;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.xml.bind.annotation.XmlRootElement;

import eu._4fh.guildsync.helper.DateHelper;

@XmlRootElement
public class WowCharacter {
	private @Nonnull String name;
	private @Nonnull String server;
	private @Nonnull int rank;
	private @Nonnull Calendar addedDate;

	public WowCharacter(final @Nonnull String name, final @Nonnull String server, final @Nonnull int rank,
			final @Nonnull Calendar addedDate) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(server);
		Objects.requireNonNull(addedDate);
		this.name = name;
		this.server = server;
		this.rank = rank;
		this.addedDate = (Calendar) addedDate.clone();
	}

	// For JAX-RS
	@SuppressWarnings("unused")
	private WowCharacter() {
		name = "invalid";
		server = "invalid";
		rank = Short.MAX_VALUE;
		addedDate = DateHelper.getNow();
	}

	public @Nonnull String getName() {
		return name;
	}

	public @Nonnull String getServer() {
		return server;
	}

	public @Nonnull int getRank() {
		return rank;
	}

	public @Nonnull Calendar getAddedDate() {
		return (Calendar) addedDate.clone();
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
		if (!(obj instanceof WowCharacter)) {
			return false;
		}
		WowCharacter other = (WowCharacter) obj;
		return Objects.equals(name, other.name) && Objects.equals(server, other.server);
	}

	@Override
	public String toString() {
		return name + "-" + server;
	}
}
