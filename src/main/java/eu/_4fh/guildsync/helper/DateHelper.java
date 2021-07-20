package eu._4fh.guildsync.helper;

import java.util.Calendar;

import org.dmfs.rfc5545.DateTime;

public class DateHelper {
	private DateHelper() {
		// Empty private constructor
	}

	public static java.sql.Timestamp dateTimeToSqlDate(DateTime dt) {
		return new java.sql.Timestamp(dt.getTimestamp());
	}

	public static java.sql.Timestamp calendarToSqlDate(Calendar cal) {
		return new java.sql.Timestamp(cal.getTimeInMillis());
	}

	public static Calendar sqlDateToCalendar(java.sql.Timestamp date) {
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(date.getTime());
		return cal;
	}

	public static Calendar getNow() {
		final Calendar today = Calendar.getInstance();
		return today;
	}
}
