package eu._4fh.guildsync.service.requests;

import javax.annotation.Nonnull;

import org.dmfs.httpessentials.converters.PlainStringHeaderConverter;
import org.dmfs.httpessentials.decoration.Decoration;
import org.dmfs.httpessentials.headers.BasicSingletonHeaderType;
import org.dmfs.httpessentials.headers.HeaderType;
import org.dmfs.httpessentials.headers.Headers;
import org.dmfs.httpessentials.headers.UpdatedHeaders;

public class TokenHeaderDecorator implements Decoration<Headers> {
	private final HeaderType<String> authHeaderType = new BasicSingletonHeaderType<String>("Authorization",
			new PlainStringHeaderConverter());

	private final @Nonnull String token;

	public TokenHeaderDecorator(final @Nonnull String token) {
		this.token = token;
	}

	@Override
	public Headers decorated(Headers original) {
		String headerValue = "Bearer " + token;
		return new UpdatedHeaders(original, authHeaderType.entity(headerValue));
	}
}
