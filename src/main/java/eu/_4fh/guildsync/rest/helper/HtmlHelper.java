package eu._4fh.guildsync.rest.helper;

import java.net.URI;

import javax.annotation.Nonnull;

public class HtmlHelper {
	private HtmlHelper() {
	}

	public static @Nonnull String getHtmlDoctype() {
		return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n";
	}

	public static @Nonnull String encodeLinkForHref(final @Nonnull URI uri) {
		return uri.toASCIIString().replace("&", "&amp;");
	}
}
