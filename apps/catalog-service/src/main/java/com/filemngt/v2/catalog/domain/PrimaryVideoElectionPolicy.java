package com.filemngt.v2.catalog.domain;

/** Policy bầu primary ổn định: video không tag ưu tiên hơn video có tag. */
public final class PrimaryVideoElectionPolicy {
    private PrimaryVideoElectionPolicy() {}

    public static boolean outranks(boolean candidateHasTags, boolean currentHasTags) {
        return !candidateHasTags && currentHasTags;
    }

    public static int priority(boolean hasTags) {
        return hasTags ? 0 : 1;
    }
}
