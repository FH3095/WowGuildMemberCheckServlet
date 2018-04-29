package eu._4fh.guildsync.rest.helper;

import java.net.URI;

import edu.umd.cs.findbugs.annotations.NonNull;

public class HtmlHelper {
	private HtmlHelper() {
	}

	public static @NonNull String getHtmlDoctype() {
		return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n";
	}

	public static @NonNull String encodeLinkForHref(final @NonNull URI uri) {
		return uri.toASCIIString().replace("&", "&amp;");
	}
}
