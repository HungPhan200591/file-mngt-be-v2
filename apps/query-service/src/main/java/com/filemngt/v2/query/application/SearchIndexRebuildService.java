package com.filemngt.v2.query.application;

import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.adapter.out.search.ElasticsearchSearchAdapter;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexRebuildService {
    private final QuerySubjectRepository subjects;
    private final ElasticsearchSearchAdapter search;
    private final SearchIndexCoordinator coordinator;
    private final int batchSize;

    public SearchIndexRebuildService(
            QuerySubjectRepository subjects,
            ElasticsearchSearchAdapter search,
            SearchIndexCoordinator coordinator,
            @Value("${query.search.rebuild-batch-size:500}") int batchSize) {
        this.subjects = subjects;
        this.search = search;
        this.coordinator = coordinator;
        this.batchSize = batchSize;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RebuildResult rebuild() throws IOException {
        coordinator.lock();
        try {
            return rebuildSnapshot();
        } finally {
            coordinator.unlock();
        }
    }

    private RebuildResult rebuildSnapshot() throws IOException {
        var candidate = search.createCandidateIndex();
        var indexedCount = 0;
        try {
            for (var pageNumber = 0; ; pageNumber++) {
                var page = subjects.findAll(PageRequest.of(pageNumber, batchSize, Sort.by("id")));
                search.indexAll(candidate, page.getContent());
                indexedCount += page.getNumberOfElements();
                if (!page.hasNext()) break;
            }
            search.activate(candidate);
            return new RebuildResult(candidate, indexedCount);
        } catch (IOException | RuntimeException exception) {
            cleanupCandidate(candidate, exception);
            throw exception;
        }
    }

    private void cleanupCandidate(String candidate, Exception failure) {
        try {
            search.deleteIndex(candidate);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    public record RebuildResult(String index, int indexedCount) {}
}
