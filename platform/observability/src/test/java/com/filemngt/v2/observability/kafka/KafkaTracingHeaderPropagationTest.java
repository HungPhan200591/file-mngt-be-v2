package com.filemngt.v2.observability.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.observability.CorrelationId;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class KafkaTracingHeaderPropagationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void injectsCorrelationAndCurrentTraceparentIntoProducerRecord() {
        MDC.put(CorrelationId.MDC_KEY, "test-corr-id-123");
        var record = new ProducerRecord<String, String>("test.topic", "key", "payload");

        try (var ignored = remoteParentScope()) {
            KafkaTracingHeaderPropagation.injectTracingHeaders(record);
        }

        var corrHeader = record.headers().lastHeader(CorrelationId.HEADER);
        assertThat(corrHeader).isNotNull();
        assertThat(new String(corrHeader.value(), StandardCharsets.UTF_8)).isEqualTo("test-corr-id-123");

        var tpHeader = record.headers().lastHeader(KafkaTracingHeaderPropagation.TRACEPARENT_HEADER);
        assertThat(tpHeader).isNotNull();
        String tpStr = new String(tpHeader.value(), StandardCharsets.UTF_8);
        assertThat(tpStr).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    void extractsHeadersAndSetsMdcAndCleansUpWithAutoCloseable() throws Exception {
        var headers = new RecordHeaders();
        headers.add(CorrelationId.HEADER, "kafka-corr-999".getBytes(StandardCharsets.UTF_8));
        headers.add(
                KafkaTracingHeaderPropagation.TRACEPARENT_HEADER,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8));
        var consumerRecord = new ConsumerRecord<String, String>("test.topic", 0, 0L, "key", "payload");
        headers.forEach(h -> consumerRecord.headers().add(h));

        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(consumerRecord)) {
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("kafka-corr-999");
            assertThat(MDC.get(KafkaTracingHeaderPropagation.TRACE_ID_MDC_KEY))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        }

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaTracingHeaderPropagation.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void fallbackGeneratesCorrelationIdWhenHeadersMissing() throws Exception {
        var consumerRecord = new ConsumerRecord<String, String>("test.topic", 0, 0L, "key", "payload");

        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(consumerRecord)) {
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isNotBlank();
        }

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void capturesAndRestoresOutboxTraceContextWithoutLeakingMdc() {
        MDC.put(CorrelationId.MDC_KEY, "request-correlation");

        KafkaTracingHeaderPropagation.OutboxTraceContext traceContext;
        try (var ignored = remoteParentScope()) {
            traceContext = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
        }
        MDC.clear();

        try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                traceContext.correlationId(), traceContext.traceparent())) {
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("request-correlation");
            assertThat(MDC.get(KafkaTracingHeaderPropagation.TRACE_ID_MDC_KEY))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(traceContext.traceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        }

        assertThat(MDC.getCopyOfContextMap()).isEmpty();
    }

    private Scope remoteParentScope() {
        SpanContext spanContext = SpanContext.createFromRemoteParent(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        return Context.current().with(Span.wrap(spanContext)).makeCurrent();
    }
}
