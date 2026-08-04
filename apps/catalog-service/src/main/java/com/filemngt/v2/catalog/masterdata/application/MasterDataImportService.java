package com.filemngt.v2.catalog.masterdata.application;

import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.ActressEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.ActressRepository;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.MasterDataImportEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.MasterDataImportRepository;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioCodeEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioCodeRepository;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioRepository;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.TagEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.TagRepository;
import com.filemngt.v2.catalog.masterdata.application.dto.ImportReportView;
import com.filemngt.v2.catalog.masterdata.application.dto.ImportReportView.ImportConflictItem;
import com.filemngt.v2.catalog.masterdata.application.exception.ImportConflictException;
import com.filemngt.v2.catalog.masterdata.domain.MasterDataNormalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý import Studio, Tag và Actress từ JSON.
 * dry-run=true: chỉ validate và trả report, không ghi DB.
 * dry-run=false: atomic apply khi zero conflict.
 */
@Service
public class MasterDataImportService {

    private final StudioRepository studioRepository;
    private final StudioCodeRepository codeRepository;
    private final TagRepository tagRepository;
    private final ActressRepository actressRepository;
    private final MasterDataImportRepository importRepository;
    private final MasterDataVersionService versionService;

    public MasterDataImportService(
            StudioRepository studioRepository,
            StudioCodeRepository codeRepository,
            TagRepository tagRepository,
            ActressRepository actressRepository,
            MasterDataImportRepository importRepository,
            MasterDataVersionService versionService) {
        this.studioRepository = studioRepository;
        this.codeRepository = codeRepository;
        this.tagRepository = tagRepository;
        this.actressRepository = actressRepository;
        this.importRepository = importRepository;
        this.versionService = versionService;
    }

    /** Payload Studio: map region → list of rows. */
    public record StudioImportPayload(Map<String, List<StudioImportRow>> regions) {}

    public record StudioImportRow(String studio, List<String> code) {}

    /** Payload Tag: danh sách tên tag hoặc JSON array. */
    public record TagImportPayload(List<String> tags) {}

    /** Payload Actress: map region → list of actress names. */
    public record ActressImportPayload(Map<String, List<String>> regions) {}

    @Transactional
    public ImportReportView importStudios(StudioImportPayload payload, boolean dryRun) {
        int totalInput = 0;
        int created = 0;
        int merged = 0;
        List<ImportConflictItem> conflicts = new ArrayList<>();

        // ── Pass 1: collect all mutations and detect conflicts ─────────────────
        // Map: region → (normalized_code → studio display_names intending to own it)
        Map<String, Map<String, List<String>>> codeOwners = new LinkedHashMap<>();

        // Planned mutations (not yet written to DB)
        record PlannedCode(String region, String normalizedCode, String rawCode, String studioNormalizedName) {}
        List<PlannedCode> planned = new ArrayList<>();
        // Planned studios to upsert: region+normalizedName → displayName
        Map<String, String> plannedStudios = new LinkedHashMap<>();

        for (var entry : payload.regions().entrySet()) {
            String region = entry.getKey().toUpperCase();
            if (!region.equals("JOKE") && !region.equals("USE")) continue;
            for (var row : entry.getValue()) {
                totalInput++;
                String studioNorm = MasterDataNormalizer.normalizeName(row.studio());
                plannedStudios.put(region + "\0" + studioNorm, row.studio());
                for (String rawCode : row.code()) {
                    String codeNorm = MasterDataNormalizer.normalizeCode(rawCode);
                    planned.add(new PlannedCode(region, codeNorm, rawCode, studioNorm));
                    codeOwners
                            .computeIfAbsent(region, r -> new LinkedHashMap<>())
                            .computeIfAbsent(codeNorm, c -> new ArrayList<>())
                            .add(row.studio());
                }
            }
        }

        // Detect conflicts: same (region, normalized_code) claimed by multiple studios in payload
        for (var regionEntry : codeOwners.entrySet()) {
            String region = regionEntry.getKey();
            for (var codeEntry : regionEntry.getValue().entrySet()) {
                List<String> owners = codeEntry.getValue().stream().distinct().toList();
                if (owners.size() > 1) {
                    conflicts.add(new ImportConflictItem(region, codeEntry.getKey(), owners));
                    continue;
                }
                // Check existing DB: same code owned by a different studio
                var existing = codeRepository.findByRegionAndNormalizedCode(region, codeEntry.getKey());
                if (existing.isPresent()) {
                    String existingStudioNorm = existing.get().studio().normalizedName();
                    String payloadStudioNorm = MasterDataNormalizer.normalizeName(owners.getFirst());
                    if (!existingStudioNorm.equals(payloadStudioNorm)) {
                        conflicts.add(new ImportConflictItem(
                                region,
                                codeEntry.getKey(),
                                List.of(existing.get().studio().displayName(), owners.getFirst())));
                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
            if (!dryRun) {
                // Record audit then throw 409
                saveAudit("STUDIO", true, totalInput, 0, 0, conflicts.size(), "CONFLICT", null);
                throw new ImportConflictException(conflicts);
            }
            return new ImportReportView(true, totalInput, 0, 0, conflicts.size(), conflicts);
        }

        if (dryRun) {
            // Estimate counts without writing
            int dryCreated = 0;
            int dryMerged = 0;
            for (var e : plannedStudios.entrySet()) {
                String[] parts = e.getKey().split("\0", 2);
                boolean exists = studioRepository.existsByRegionAndNormalizedName(parts[0], parts[1]);
                if (exists) dryMerged++;
                else dryCreated++;
            }
            return new ImportReportView(true, totalInput, dryCreated, dryMerged, 0, List.of());
        }

        // ── Pass 2: apply (already in @Transactional) ─────────────────────────
        var now = Instant.now();
        for (var e : plannedStudios.entrySet()) {
            String[] parts = e.getKey().split("\0", 2);
            String region = parts[0];
            String normalizedName = parts[1];
            var studioOpt = studioRepository.findByRegionAndNormalizedName(region, normalizedName);
            if (studioOpt.isEmpty()) {
                studioRepository.save(new StudioEntity(UUID.randomUUID(), region, e.getValue(), normalizedName, now));
                created++;
            } else {
                merged++;
            }
        }

        // Add codes (idempotent: skip exact duplicates)
        for (var p : planned) {
            var studioEntity = studioRepository
                    .findByRegionAndNormalizedName(p.region(), p.studioNormalizedName())
                    .orElseThrow();
            var existingCode = codeRepository.findByRegionAndNormalizedCode(p.region(), p.normalizedCode());
            if (existingCode.isEmpty()) {
                codeRepository.save(
                        new StudioCodeEntity(UUID.randomUUID(), studioEntity, p.rawCode(), p.normalizedCode(), now));
            }
            // same code → same studio is no-op (idempotent)
        }

        boolean anyMutation = created > 0
                || planned.stream().anyMatch(p -> codeRepository
                        .findByRegionAndNormalizedCode(p.region(), p.normalizedCode())
                        .isEmpty());
        if (created > 0 || merged > 0) {
            versionService.bumpVersion();
        }
        saveAudit("STUDIO", false, totalInput, created, merged, 0, "OK", null);
        return new ImportReportView(false, totalInput, created, merged, 0, List.of());
    }

    @Transactional
    public ImportReportView importTags(TagImportPayload payload, boolean dryRun) {
        if (payload == null || payload.tags() == null) {
            return new ImportReportView(dryRun, 0, 0, 0, 0, List.of());
        }
        int totalInput = payload.tags().size();
        int created = 0;
        int merged = 0;

        for (String rawTag : payload.tags()) {
            if (rawTag == null || rawTag.isBlank()) continue;
            String normalized = MasterDataNormalizer.normalizeName(rawTag);
            boolean exists = tagRepository.existsByNormalizedName(normalized);
            if (exists) {
                merged++;
            } else {
                created++;
                if (!dryRun) {
                    tagRepository.save(new TagEntity(UUID.randomUUID(), rawTag.trim(), normalized, Instant.now()));
                }
            }
        }

        if (!dryRun && created > 0) {
            versionService.bumpVersion();
        }
        saveAudit("TAG", dryRun, totalInput, created, merged, 0, "OK", null);
        return new ImportReportView(dryRun, totalInput, created, merged, 0, List.of());
    }

    @Transactional
    public ImportReportView importActresses(ActressImportPayload payload, boolean dryRun) {
        if (payload == null || payload.regions() == null) {
            return new ImportReportView(dryRun, 0, 0, 0, 0, List.of());
        }
        int totalInput = 0;
        int created = 0;
        int merged = 0;

        for (var entry : payload.regions().entrySet()) {
            String region = entry.getKey().toUpperCase();
            if (!region.equals("JOKE") && !region.equals("USE")) continue;
            for (String rawName : entry.getValue()) {
                if (rawName == null || rawName.isBlank()) continue;
                totalInput++;
                String normalized = MasterDataNormalizer.normalizeName(rawName);
                boolean exists = actressRepository.existsByRegionAndNormalizedName(region, normalized);
                if (exists) {
                    merged++;
                } else {
                    created++;
                    if (!dryRun) {
                        actressRepository.save(new ActressEntity(
                                UUID.randomUUID(), region, rawName.trim(), normalized, Instant.now()));
                    }
                }
            }
        }

        if (!dryRun && created > 0) {
            versionService.bumpVersion();
        }
        saveAudit("ACTRESS", dryRun, totalInput, created, merged, 0, "OK", null);
        return new ImportReportView(dryRun, totalInput, created, merged, 0, List.of());
    }

    private void saveAudit(
            String type,
            boolean dryRun,
            int total,
            int created,
            int merged,
            int conflict,
            String status,
            String errorDetail) {
        importRepository.save(new MasterDataImportEntity(
                UUID.randomUUID(), type, dryRun, status, total, created, merged, conflict, errorDetail, Instant.now()));
    }
}
