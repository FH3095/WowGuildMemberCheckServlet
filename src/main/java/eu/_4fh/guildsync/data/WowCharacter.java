package eu._4fh.guildsync.data;

import java.util.Calendar;
import java.util.Objects;

import javax.xml.bind.annotation.XmlRootElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.guildsync.helper.DateHelper;

@XmlRootElement
public class WowCharacter {
	private @NonNull String name;
	private @NonNull String server;
	private @NonNull Calendar addedDate;

	public WowCharacter(final @NonNull String name, final @NonNull String server, final @NonNull Calendar addedDate) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(server);
		Objects.requireNonNull(addedDate);
		this.name = name;
		this.server = server;
		this.addedDate = (Calendar) addedDate.clone();
	}

	// For JAX-RS
	@SuppressWarnings("unused")
	private WowCharacter() {
		name = "invalid";
		server = "invalid";
		addedDate = DateHelper.getToday();
	}

	public @NonNull String getName() {
		return name;
	}

	public @NonNull String getServer() {
		return server;
	}

	public @NonNull Calendar getAddedDate() {
		return (Calendar) addedDate.clone();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + name.hashCode();
		result = prime * result + server.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof WowCharacter)) {
			return false;
		}

		WowCharacter other = (WowCharacter) obj;
		if (!name.equals(other.name)) {
			return false;
		}
		if (!server.equals(other.server)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return name + "-" + server;
	}
}
