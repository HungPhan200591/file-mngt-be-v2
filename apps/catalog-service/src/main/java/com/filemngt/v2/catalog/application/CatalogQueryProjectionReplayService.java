package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogQueryProjectionReplayService {
    private static final int MAX_BATCH_SIZE = 500;

    private final MediaSubjectRepository subjects;
    private final CatalogSubjectOutboxService outbox;

    public CatalogQueryProjectionReplayService(MediaSubjectRepository subjects, CatalogSubjectOutboxService outbox) {
        this.subjects = subjects;
        this.outbox = outbox;
    }

    @Transactional
    public int enqueueAll(int requestedBatchSize) {
        var batchSize = Math.min(Math.max(requestedBatchSize, 1), MAX_BATCH_SIZE);
        var page = 0;
        var enqueued = 0;
        while (true) {
            var subjectsPage = subjects.findAll(PageRequest.of(page, batchSize));
            subjectsPage.forEach(outbox::enqueue);
            enqueued += subjectsPage.getNumberOfElements();
            if (!subjectsPage.hasNext()) return enqueued;
            page++;
        }
    }
}
