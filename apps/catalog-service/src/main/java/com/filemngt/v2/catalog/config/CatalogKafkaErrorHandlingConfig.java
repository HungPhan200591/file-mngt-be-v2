package com.filemngt.v2.catalog.config;

import com.filemngt.v2.catalog.adapter.in.event.CatalogInputContractException;
import java.time.Duration;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class CatalogKafkaErrorHandlingConfig {

    private static final long INITIAL_BACK_OFF_MILLIS = 250;
    private static final long MAXIMUM_BACK_OFF_MILLIS = 2_000;
    private static final long BASE_BACK_OFF_JITTER_MILLIS = 100;
    private static final long RETRY_ATTEMPTS = 3;

    @Bean
    DeadLetterPublishingRecoverer catalogDeadLetterPublishingRecoverer(KafkaTemplate<Object, Object> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setVerifyPartition(true);
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(5));
        return recoverer;
    }

    @Bean
    CommonErrorHandler catalogKafkaErrorHandler(
            DeadLetterPublishingRecoverer recoverer, CatalogKafkaRetryMetricsListener retryMetrics) {
        var handler = new DefaultErrorHandler(recoverer, retryBackOff());
        handler.defaultFalse(true);
        handler.addNotRetryableExceptions(CatalogInputContractException.class);
        handler.addRetryableExceptions(
                TransientDataAccessException.class,
                RecoverableDataAccessException.class,
                DataAccessResourceFailureException.class,
                KafkaException.class);
        handler.setCommitRecovered(false);
        handler.setResetStateOnRecoveryFailure(true);
        handler.setRetryListeners(retryMetrics);
        return handler;
    }

    static ExponentialBackOff retryBackOff() {
        var backOff = new ExponentialBackOff(INITIAL_BACK_OFF_MILLIS, 2.0);
        backOff.setJitter(BASE_BACK_OFF_JITTER_MILLIS);
        backOff.setMaxInterval(MAXIMUM_BACK_OFF_MILLIS);
        backOff.setMaxAttempts(RETRY_ATTEMPTS);
        return backOff;
    }
}
