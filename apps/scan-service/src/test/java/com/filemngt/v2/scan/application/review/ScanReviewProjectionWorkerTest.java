package com.filemngt.v2.scan.application.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionTaskStore.Task;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ScanReviewProjectionWorkerTest {
    @Test
    void recordsFailureWhenRebuildThrows() {
        var transactions = mock(ScanReviewProjectionTransactions.class);
        var task = new Task(UUID.randomUUID(), UUID.randomUUID(), "root", 2, 1);
        when(transactions.claim(anyString(), any())).thenReturn(Optional.of(task));
        doThrow(new IllegalStateException("projection failed"))
                .when(transactions)
                .rebuild(any(Task.class), anyString(), any());
        var worker = new ScanReviewProjectionWorker(transactions);

        worker.projectNextRoot();

        verify(transactions).recordFailure(any(Task.class), anyString(), any(), any(IllegalStateException.class));
    }

    @Test
    void doesNothingWhenNoTaskIsDue() {
        var transactions = mock(ScanReviewProjectionTransactions.class);
        when(transactions.claim(anyString(), any())).thenReturn(Optional.empty());
        var worker = new ScanReviewProjectionWorker(transactions);

        worker.projectNextRoot();

        verify(transactions, never()).rebuild(any(), anyString(), any());
    }
}
