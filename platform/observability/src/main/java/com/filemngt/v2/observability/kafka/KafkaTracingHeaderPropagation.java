package com.filemngt.v2.observability.kafka;

import com.filemngt.v2.observability.CorrelationId;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

public final class KafkaTracingHeaderPropagation {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACE_ID_MDC_KEY = "trace_id";

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");

    @FunctionalInterface
    public interface MdcContext extends AutoCloseable {
        @Override
        void close();
    }

    public record OutboxTraceContext(String correlationId, String traceparent) {}

    private KafkaTracingHeaderPropagation() {}

    public static OutboxTraceContext captureOutboxTraceContext() {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        tagCurrentSpan(correlationId);
        return new OutboxTraceContext(correlationId, currentTraceparent());
    }

    public static MdcContext restoreOutboxTraceContext(String correlationId, String traceparent) {
        String previousCorrelationId = MDC.get(CorrelationId.MDC_KEY);
        String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        String resolvedCorrelationId = correlationId;
        if (resolvedCorrelationId == null || resolvedCorrelationId.isBlank()) {
            resolvedCorrelationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationId.MDC_KEY, resolvedCorrelationId);
        String traceId = traceId(traceparent);
        if (traceId != null) {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }
        Scope scope = restoreTraceparent(traceparent);

        return () -> {
            scope.close();
            restoreMdc(CorrelationId.MDC_KEY, previousCorrelationId);
            restoreMdc(TRACE_ID_MDC_KEY, previousTraceId);
        };
    }

    public static MdcContext restoreOutboxTraceContext(
            String correlationId, String traceparent, Tracer tracer, Propagator propagator) {
        String previousCorrelationId = MDC.get(CorrelationId.MDC_KEY);
        String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        String resolvedCorrelationId = correlationId;
        if (resolvedCorrelationId == null || resolvedCorrelationId.isBlank()) {
            resolvedCorrelationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationId.MDC_KEY, resolvedCorrelationId);
        String traceId = traceId(traceparent);
        if (traceId != null) {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }

        io.micrometer.tracing.Span restoredSpan = restoreMicrometerSpan(traceparent, propagator);
        Tracer.SpanInScope spanScope = restoredSpan == null ? () -> {} : tracer.withSpan(restoredSpan);
        if (restoredSpan != null) {
            restoredSpan.tag("correlation.id", resolvedCorrelationId);
        }

        return () -> {
            spanScope.close();
            if (restoredSpan != null) {
                restoredSpan.end();
            }
            restoreMdc(CorrelationId.MDC_KEY, previousCorrelationId);
            restoreMdc(TRACE_ID_MDC_KEY, previousTraceId);
        };
    }

    public static void injectTracingHeaders(Headers headers) {
        if (headers == null) {
            return;
        }

        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        if (headers.lastHeader(CorrelationId.HEADER) == null) {
            headers.add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }

        String traceparent = currentTraceparent();
        if (traceparent != null && headers.lastHeader(TRACEPARENT_HEADER) == null) {
            headers.add(TRACEPARENT_HEADER, traceparent.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void injectTracingHeaders(ProducerRecord<?, ?> record) {
        if (record != null) {
            injectTracingHeaders(record.headers());
        }
    }

    public static MdcContext extractAndSetMdc(Headers headers) {
        String previousCorrelationId = MDC.get(CorrelationId.MDC_KEY);
        String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        String correlationId = headerValue(headers, CorrelationId.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        tagCurrentSpan(correlationId);
        String traceId = traceId(headerValue(headers, TRACEPARENT_HEADER));
        if (traceId != null) {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }

        return () -> {
            restoreMdc(CorrelationId.MDC_KEY, previousCorrelationId);
            restoreMdc(TRACE_ID_MDC_KEY, previousTraceId);
        };
    }

    public static MdcContext extractAndSetMdc(ConsumerRecord<?, ?> record) {
        return extractAndSetMdc(record == null ? null : record.headers());
    }

    private static String currentTraceparent() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (!spanContext.isValid()) {
            return null;
        }
        return "00-%s-%s-%s"
                .formatted(
                        spanContext.getTraceId(),
                        spanContext.getSpanId(),
                        spanContext.getTraceFlags().asHex());
    }

    private static Scope restoreTraceparent(String traceparent) {
        var matcher = TRACEPARENT_PATTERN.matcher(traceparent == null ? "" : traceparent);
        if (!matcher.matches()) {
            return () -> {};
        }
        SpanContext spanContext = SpanContext.createFromRemoteParent(
                matcher.group(1), matcher.group(2), TraceFlags.fromHex(matcher.group(3), 0), TraceState.getDefault());
        if (!spanContext.isValid()) {
            return () -> {};
        }
        return Context.current().with(Span.wrap(spanContext)).makeCurrent();
    }

    private static io.micrometer.tracing.Span restoreMicrometerSpan(String traceparent, Propagator propagator) {
        if (traceId(traceparent) == null) {
            return null;
        }
        return propagator
                .extract(traceparent, (carrier, headerName) -> TRACEPARENT_HEADER.equals(headerName) ? carrier : null)
                .name("outbox trace context")
                .start();
    }

    private static String headerValue(Headers headers, String headerName) {
        if (headers == null) {
            return null;
        }
        var header = headers.lastHeader(headerName);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String traceId(String traceparent) {
        var matcher = TRACEPARENT_PATTERN.matcher(traceparent == null ? "" : traceparent);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static void restoreMdc(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static void tagCurrentSpan(String correlationId) {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.setAttribute("correlation.id", correlationId);
        }
    }
}
