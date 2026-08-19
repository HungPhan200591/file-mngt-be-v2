package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.operation.CatalogOperationLaneHash;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogOperationStageStoreTest {
    @ParameterizedTest
    @CsvSource({"JOKE:VIDEO:CODE-001, 50", "USE:ALBUM:ALBUM-001, 16", "JOKE:VIDEO:CODE-999, 31"})
    void stableLaneMatchesPostgreSqlMd5GoldenVectors(String subjectKey, int expectedLane) {
        assertThat(CatalogOperationLaneHash.stableLane(subjectKey)).isEqualTo(expectedLane);
    }
}
