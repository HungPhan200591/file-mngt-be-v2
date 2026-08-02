package com.filemngt.v2.observability;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {}

    public static String canonicalOrGenerate(List<String> values) {
        if (values.size() == 1 && VALID_VALUE.matcher(values.getFirst()).matches()) {
            return values.getFirst();
        }
        return UUID.randomUUID().toString();
    }
}
