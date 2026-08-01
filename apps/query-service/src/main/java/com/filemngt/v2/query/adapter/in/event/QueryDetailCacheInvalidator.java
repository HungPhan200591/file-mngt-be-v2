package com.filemngt.v2.query.adapter.in.event;

import com.filemngt.v2.query.application.QueryDetailCache;
import com.filemngt.v2.query.application.QuerySubjectProjectionChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QueryDetailCacheInvalidator {
    private final QueryDetailCache cache;

    public QueryDetailCacheInvalidator(QueryDetailCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(QuerySubjectProjectionChanged event) {
        cache.evict(event.subjectId());
    }
}
