package com.filemngt.v2.query.adapter.out.persistence;

import com.filemngt.v2.query.domain.MediaAssetRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "query_media_asset")
public class QueryAssetEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id")
    private QuerySubjectEntity subject;

    @Enumerated(EnumType.STRING)
    private MediaAssetRole role;

    private String relativePath;

    protected QueryAssetEntity() {}

    public QueryAssetEntity(UUID id, MediaAssetRole role, String path) {
        this.id = id;
        this.role = role;
        relativePath = path;
    }

    void assignSubject(QuerySubjectEntity value) {
        subject = value;
    }

    void update(MediaAssetRole nextRole, String nextPath) {
        role = nextRole;
        relativePath = nextPath;
    }

    public UUID id() {
        return id;
    }

    public MediaAssetRole role() {
        return role;
    }

    public String relativePath() {
        return relativePath;
    }
}
