package com.filemngt.v2.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.catalog.adapter.in.event.CatalogInputContractException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.BackOffExecution;

class CatalogKafkaErrorHandlingConfigTest {
    @Test
    void dltSendFailurePropagatesWithoutCommittingSourceOffset() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);
        when(kafka.partitionsFor("media.file.discovered.v2.DLT"))
                .thenReturn(IntStream.range(0, 4)
                        .mapToObj(partition -> new PartitionInfo(
                                "media.file.discovered.v2.DLT",
                                partition,
                                null,
                                new org.apache.kafka.common.Node[0],
                                new org.apache.kafka.common.Node[0]))
                        .toList());
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.<SendResult<Object, Object>>failedFuture(
                        new KafkaException("broker unavailable")));
        var configuration = new CatalogKafkaErrorHandlingConfig();
        var recoverer = configuration.catalogDeadLetterPublishingRecoverer(kafka);
        var handler = configuration.catalogKafkaErrorHandler(recoverer, mock(CatalogKafkaRetryMetricsListener.class));
        var source = new ConsumerRecord<>("media.file.discovered.v2", 3, 42L, "key", "payload");
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        boolean recovered = handler.handleOne(new CatalogInputContractException("poison"), source, consumer, container);

        assertThat(recovered).isFalse();
        verify(consumer, never()).commitSync();
        verify(consumer, never()).commitAsync();
    }

    @Test
    void exponentialBackoffHasJitterAndStopsAfterConfiguredAttemptCap() {
        BackOffExecution execution =
                CatalogKafkaErrorHandlingConfig.retryBackOff().start();

        assertThat(execution.nextBackOff()).isBetween(150L, 350L);
        assertThat(execution.nextBackOff()).isBetween(300L, 700L);
        assertThat(execution.nextBackOff()).isBetween(600L, 1_400L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void operationFactoryUsesObservationWithoutLegacyListenerTimer() {
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> consumers = mock(ConsumerFactory.class);
        CommonErrorHandler errors = mock(CommonErrorHandler.class);

        var factory = new CatalogOperationKafkaConfig().catalogOperationBatchFactory(consumers, errors, 4);

        assertThat(factory.getContainerProperties().isObservationEnabled()).isTrue();
        assertThat(factory.getContainerProperties().isMicrometerEnabled()).isFalse();
    }

    @Test
    void productionDltTopologyKeepsSourcePartitionCardinality() {
        var topic = new CatalogKafkaTopicConfiguration().mediaFileDiscoveredDltTopic(12);

        assertThat(topic.name()).isEqualTo("media.file.discovered.v2.DLT");
        assertThat(topic.numPartitions()).isEqualTo(12);
    }
}
