package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ScanFileInventorySetWriterTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void classifiesRootWithExistingInventoryAsWarm() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("joke-root")))
                .thenReturn(true);
        var writer = new ScanFileInventorySetWriter(jdbcTemplate);

        assertThat(writer.hasInventoryForRoot("joke-root")).isTrue();
    }

    @Test
    void classifiesRootWithoutInventoryAsCold() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("joke-root")))
                .thenReturn(false);
        var writer = new ScanFileInventorySetWriter(jdbcTemplate);

        assertThat(writer.hasInventoryForRoot("joke-root")).isFalse();
    }

    @Test
    void insertsColdChunkWithoutUpdateOrAntiJoin() {
        UUID runId = UUID.fromString("019fe011-2278-7c46-9008-19a8c90ed5e4");
        when(jdbcTemplate.update(anyString(), eq(runId), eq("a"), eq("z"))).thenReturn(3);
        var writer = new ScanFileInventorySetWriter(jdbcTemplate);

        assertThat(writer.insertCold(runId, "a", "z")).isEqualTo(3);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(runId), eq("a"), eq("z"));
        assertThat(sql.getValue()).contains("INSERT INTO scan_file_inventory");
        assertThat(sql.getValue()).doesNotContain("UPDATE", "NOT EXISTS");
    }

    @Test
    void insertsOnlyMissingInventoryRowsWithHashAntiJoin() {
        UUID runId = UUID.fromString("019fe011-2278-7c46-9008-19a8c90ed5e4");
        when(jdbcTemplate.update(anyString(), eq(runId), eq("a"), eq("z"))).thenReturn(2);
        var writer = new ScanFileInventorySetWriter(jdbcTemplate);

        assertThat(writer.upsertChanged(runId, "a", "z").inserted()).isEqualTo(2);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), eq(runId), eq("a"), eq("z"));
        assertThat(sql.getAllValues().get(1)).contains("LEFT JOIN scan_file_inventory", "inventory.root_key IS NULL");
        assertThat(sql.getAllValues().get(1)).doesNotContain("NOT EXISTS");
    }
}
