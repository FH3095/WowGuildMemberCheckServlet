package eu._4fh.guildsync.data;

import javax.annotation.Nonnull;

public class BNetProfileInfo {
	private final long id;
	private final @Nonnull String battleTag;

	public BNetProfileInfo(final long id, final @Nonnull String battleTag) {
		this.id = id;
		this.battleTag = battleTag;
	}

	public long getId() {
		return id;
	}

	public @Nonnull String getBattleTag() {
		return battleTag;
	}
}
