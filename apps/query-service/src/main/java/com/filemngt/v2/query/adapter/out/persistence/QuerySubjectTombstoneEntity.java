package com.filemngt.v2.query.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_subject_tombstone")
public class QuerySubjectTombstoneEntity {
    @Id
    private UUID subjectId;

    private long subjectVersion;
    private Instant deletedAt;

    protected QuerySubjectTombstoneEntity() {}

    public QuerySubjectTombstoneEntity(UUID subjectId, long subjectVersion, Instant deletedAt) {
        this.subjectId = subjectId;
        this.subjectVersion = subjectVersion;
        this.deletedAt = deletedAt;
    }

    public long subjectVersion() {
        return subjectVersion;
    }
}
