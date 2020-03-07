package eu._4fh.guildsync.helper;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.ws.rs.BadRequestException;

import eu._4fh.guildsync.config.Config;

public class MacCalculator {
	private MacCalculator() {
	}

	public static void testMac(final @Nonnull String macInStr, final String... macValues) {
		try {
			final byte[] inMac = Base64.getDecoder().decode(macInStr);

			final Mac localMac = Mac.getInstance(Config.getInstance().macAlgorithm());
			localMac.init(Config.getInstance().macKey());
			for (final String value : macValues) {
				localMac.update(value.getBytes(StandardCharsets.UTF_8));
			}
			final byte[] localMacResult = localMac.doFinal();

			if (!Arrays.equals(inMac, localMacResult)) {
				throw new BadRequestException("Invalid MAC");
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}
}
