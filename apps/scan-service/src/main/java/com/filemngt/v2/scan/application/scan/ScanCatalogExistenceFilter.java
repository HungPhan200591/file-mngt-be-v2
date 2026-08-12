package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogExistenceClient;
import com.filemngt.v2.scan.adapter.out.catalog.CatalogExistenceClient.Candidate;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.application.exception.CatalogExistenceUnavailableException;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ScanCatalogExistenceFilter {

    private static final int MAX_BATCH_SIZE = 500;

    private final CatalogExistenceClient catalog;
    private final ScanEvidenceCodec evidenceCodec;

    ScanCatalogExistenceFilter(CatalogExistenceClient catalog, ScanEvidenceCodec evidenceCodec) {
        this.catalog = catalog;
        this.evidenceCodec = evidenceCodec;
    }

    int filter(ScanExecutionContext context, ScanChunk chunk) {
        List<ScanProposalEntity> retained = new ArrayList<>();
        int exact = 0;
        var proposals = chunk.proposals();
        for (int start = 0; start < proposals.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, proposals.size());
            var batch = proposals.subList(start, end);
            var results = catalog.classify(
                    context.runId(),
                    batch.stream().map(p -> candidate(context, p)).toList());
            for (var proposal : batch) {
                var result = requireResult(results, proposal.id());
                if (result.classification() == CatalogExistenceClient.Classification.EXACT_ASSET_EXISTS
                        && !context.overwriteExisting()) {
                    exact++;
                    continue;
                }
                retained.add(withEvidence(proposal, result));
            }
        }
        chunk.replaceProposals(retained);
        return exact;
    }

    private Candidate candidate(ScanExecutionContext context, ScanProposalEntity proposal) {
        if (proposal.assetRole() == null) {
            throw new CatalogExistenceUnavailableException("Scan proposal has no asset role for Catalog lookup");
        }
        return new Candidate(
                proposal.id(),
                context.root().key(),
                proposal.sourceRelativePath(),
                region(proposal.profile()),
                subjectType(proposal.profile()),
                proposal.identityKey(),
                proposal.assetRole());
    }

    private CatalogExistenceClient.Result requireResult(
            Map<UUID, CatalogExistenceClient.Result> results, UUID proposalId) {
        var result = results.get(proposalId);
        if (result == null) {
            throw new CatalogExistenceUnavailableException("Catalog existence response is missing a proposal");
        }
        return result;
    }

    private ScanProposalEntity withEvidence(ScanProposalEntity proposal, CatalogExistenceClient.Result result) {
        String evidence = evidenceCodec.withCatalogExistence(
                proposal.evidence(),
                result.classification().name(),
                text(result.matchedSubjectId()),
                text(result.matchedAssetId()),
                result.conflictCode() == null ? null : result.conflictCode().name());
        return new ScanProposalEntity(
                proposal.id(),
                proposal.scanRunId(),
                proposal.sourceRelativePath(),
                proposal.profile(),
                proposal.candidateType(),
                proposal.identityKey(),
                proposal.displayTitle(),
                proposal.assetRole(),
                evidence);
    }

    private String region(ScanProfile profile) {
        return switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> "JOKE";
            case USE_VIDEO, USE_ASSET, USE_ALBUM -> "USE";
        };
    }

    private String subjectType(ScanProfile profile) {
        return profile == ScanProfile.USE_ALBUM ? "ALBUM" : "VIDEO";
    }

    private String text(UUID value) {
        return value == null ? null : value.toString();
    }
}
