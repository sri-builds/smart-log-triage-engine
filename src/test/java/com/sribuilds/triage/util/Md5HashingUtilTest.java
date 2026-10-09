package com.sribuilds.triage.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Md5HashingUtilTest {

    private static final String NPE_STACKTRACE =
            "java.lang.NullPointerException: Cannot invoke \"com.verizon.billing.model.Invoice.getAmount()\" "
                    + "because the return value of \"com.verizon.billing.model.Payment.getInvoice()\" is null "
                    + "at com.verizon.billing.BatchJob.execute(BatchJob.java:142)";

    @Test
    void matchesKnownMd5Vector() {
        // Well-known MD5 test vector: "The quick brown fox jumps over the lazy dog"
        assertThat(Md5HashingUtil.hash("The quick brown fox jumps over the lazy dog"))
                .isEqualTo("9e107d9d372bb6826bd81d3542a419d6");
    }

    @Test
    void hashIsDeterministicAndFixedWidth() {
        String first = Md5HashingUtil.hash(NPE_STACKTRACE);
        String second = Md5HashingUtil.hash(NPE_STACKTRACE);

        assertThat(first).isEqualTo(second).hasSize(32);
        assertThat(first).matches("[0-9a-f]{32}");
    }

    @Test
    void hashChangesWithLogContent() {
        String base = Md5HashingUtil.hash(NPE_STACKTRACE);
        String altered = Md5HashingUtil.hash(NPE_STACKTRACE + " [replay]");

        assertThat(base).isNotEqualTo(altered);
    }

    @Test
    void matchesJdkReferenceImplementation() throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(NPE_STACKTRACE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expected = java.util.HexFormat.of().formatHex(digest);

        assertThat(Md5HashingUtil.hash(NPE_STACKTRACE)).isEqualTo(expected);
    }

    @Test
    void rejectsNullAndBlank() {
        assertThatThrownBy(() -> Md5HashingUtil.hash(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Md5HashingUtil.hash("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Md5HashingUtil.hash(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}