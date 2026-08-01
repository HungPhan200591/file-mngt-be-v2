package com.filemngt.v2.query.application;

import java.util.Optional;
import java.util.UUID;

public interface QueryDetailCache {
    Optional<QuerySubjectDetail> get(UUID subjectId);

    void put(QuerySubjectDetail detail);

    void evict(UUID subjectId);
}
