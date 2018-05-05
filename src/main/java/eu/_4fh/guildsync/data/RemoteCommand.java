package eu._4fh.guildsync.data;

import java.util.Objects;

import javax.xml.bind.annotation.XmlRootElement;

import edu.umd.cs.findbugs.annotations.NonNull;

@XmlRootElement
public class RemoteCommand {
	public static enum Commands {
		ACC_UPDATE;
	}

	private long id;
	private long remoteAccountId;
	private @NonNull Commands command;

	public RemoteCommand(long id, long remoteAccountId, @NonNull Commands command) {
		Objects.requireNonNull(command);
		this.id = id;
		this.remoteAccountId = remoteAccountId;
		this.command = command;
	}

	public long getId() {
		return id;
	}

	public long getRemoteAccountId() {
		return remoteAccountId;
	}

	public @NonNull Commands getCommand() {
		return command;
	}

	@Override
	public String toString() {
		return String.valueOf(id) + " -> " + command.toString() + " for " + String.valueOf(remoteAccountId);
	}
}
