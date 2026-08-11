package com.filemngt.v2.query.application;

import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;

public record QuerySubjectFilter(
        Region region, SubjectType subjectType, String rootKey, String studio, String actress, String tag) {

    public boolean hasMetadataFilter() {
        return rootKey != null || studio != null || actress != null || tag != null;
    }
}
