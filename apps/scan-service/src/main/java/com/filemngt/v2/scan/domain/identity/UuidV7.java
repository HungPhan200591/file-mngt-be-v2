package com.filemngt.v2.scan.domain.identity;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Sinh UUIDv7 time-ordered cho các identifier được tạo trước khi ghi database. */
public final class UuidV7 {
    private static final long TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL;
    private static final long RANDOM_A_MASK = 0x0FFFL;
    private static final long RANDOM_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final long VERSION_7 = 0x7000L;
    private static final long RFC_4122_VARIANT = 0x8000_0000_0000_0000L;

    private UuidV7() {}

    public static UUID next() {
        var random = ThreadLocalRandom.current();
        long timestamp = System.currentTimeMillis() & TIMESTAMP_MASK;
        long mostSignificantBits = (timestamp << 16) | VERSION_7 | (random.nextLong() & RANDOM_A_MASK);
        long leastSignificantBits = RFC_4122_VARIANT | (random.nextLong() & RANDOM_B_MASK);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
