package eu._4fh.guildsync.helper;

import java.sql.Timestamp;
import java.time.Instant;

import org.dmfs.rfc5545.DateTime;

public class DateHelper {
	private DateHelper() {
		// Empty private constructor
	}

	public static java.sql.Timestamp dateTimeToSqlDate(DateTime dt) {
		return new java.sql.Timestamp(dt.getTimestamp());
	}

	public static Instant getNow() {
		return Instant.now();
	}

	public static Timestamp instantToSqlDate(final Instant instant) {
		return Timestamp.from(instant);
	}

	public static Instant sqlDateToInstant(final Timestamp timestamp) {
		return timestamp.toInstant();
	}
}
