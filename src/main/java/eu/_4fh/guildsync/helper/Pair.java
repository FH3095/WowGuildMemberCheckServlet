package eu._4fh.guildsync.helper;

public class Pair<T1, T2> {
	private final T1 v1;
	private final T2 v2;

	public Pair(final T1 v1, final T2 v2) {
		this.v1 = v1;
		this.v2 = v2;
	}

	public T1 getValue1() {
		return v1;
	}

	public T2 getValue2() {
		return v2;
	}
}
