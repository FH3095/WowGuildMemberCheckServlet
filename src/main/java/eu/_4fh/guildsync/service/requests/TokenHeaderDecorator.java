package eu._4fh.guildsync.service.requests;

import org.dmfs.httpessentials.converters.PlainStringHeaderConverter;
import org.dmfs.httpessentials.decoration.Decoration;
import org.dmfs.httpessentials.headers.BasicSingletonHeaderType;
import org.dmfs.httpessentials.headers.HeaderType;
import org.dmfs.httpessentials.headers.Headers;
import org.dmfs.httpessentials.headers.UpdatedHeaders;

import edu.umd.cs.findbugs.annotations.NonNull;

public class TokenHeaderDecorator implements Decoration<Headers> {
	private final HeaderType<String> authHeaderType = new BasicSingletonHeaderType<String>("Authorization",
			new PlainStringHeaderConverter());

	private final @NonNull String token;

	public TokenHeaderDecorator(final @NonNull String token) {
		this.token = token;
	}

	@Override
	public Headers decorated(Headers original) {
		String headerValue = "Bearer " + token;
		return new UpdatedHeaders(original, authHeaderType.entity(headerValue));
	}
}
