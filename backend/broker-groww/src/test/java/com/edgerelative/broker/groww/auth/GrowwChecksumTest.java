package com.edgerelative.broker.groww.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class GrowwChecksumTest {

    @Test
    void checksumIsSha256OfSecretPlusTimestamp() throws Exception {
        String secret = "my-secret";
        String timestamp = "1719830400";
        String expected = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest((secret + timestamp).getBytes(StandardCharsets.UTF_8)));

        assertThat(GrowwAuthenticationClient.checksum(secret, timestamp)).isEqualTo(expected);
    }
}
