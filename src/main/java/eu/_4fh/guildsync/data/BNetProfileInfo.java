package eu._4fh.guildsync.data;

import edu.umd.cs.findbugs.annotations.NonNull;

public class BNetProfileInfo {
	private final long id;
	private final @NonNull String battleTag;

	public BNetProfileInfo(final long id, final @NonNull String battleTag) {
		this.id = id;
		this.battleTag = battleTag;
	}

	public long getId() {
		return id;
	}

	public @NonNull String getBattleTag() {
		return battleTag;
	}
}
