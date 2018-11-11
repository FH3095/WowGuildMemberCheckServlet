package eu._4fh.guildsync.helper;

import java.util.Calendar;

import org.dmfs.rfc5545.DateTime;

public class DateHelper {
	private DateHelper() {
		// Empty private constructor
	}

	public static java.sql.Date dateTimeToSqlDate(DateTime dt) {
		return new java.sql.Date(dt.toAllDay().getTimestamp());
	}

	public static java.sql.Date calendarToSqlDate(Calendar cal) {
		return new java.sql.Date(cal.getTimeInMillis());
	}

	public static Calendar sqlDateToCalendar(java.sql.Date date) {
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(date.getTime());
		return cal;
	}

	public static Calendar getToday() {
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		return today;
	}
}
