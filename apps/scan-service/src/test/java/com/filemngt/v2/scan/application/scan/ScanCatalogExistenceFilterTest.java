package com.filemngt.v2.scan.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogExistenceClient;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanCatalogExistenceFilterTest {

    @Test
    void retainsExactAssetForOverwriteRerun() {
        var catalog = mock(CatalogExistenceClient.class);
        var evidenceCodec = mock(ScanEvidenceCodec.class);
        var filter = new ScanCatalogExistenceFilter(catalog, evidenceCodec);
        UUID proposalId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        var proposal = proposal(proposalId);
        var chunk = new ScanChunk();
        chunk.addProposal(proposal);
        when(catalog.classify(any(), any()))
                .thenReturn(Map.of(
                        proposalId,
                        new CatalogExistenceClient.Result(
                                proposalId,
                                CatalogExistenceClient.Classification.EXACT_ASSET_EXISTS,
                                subjectId,
                                assetId,
                                null)));
        when(evidenceCodec.withCatalogExistence(any(), any(), any(), any(), any()))
                .thenReturn("{\"catalogExistence\":\"EXACT_ASSET_EXISTS\"}");

        int skipped = filter.filter(context(true), chunk);

        assertThat(skipped).isZero();
        assertThat(chunk.proposals()).hasSize(1);
        assertThat(chunk.proposals().getFirst().evidence()).contains("EXACT_ASSET_EXISTS");
    }

    @Test
    void skipsExactAssetForNormalScan() {
        var catalog = mock(CatalogExistenceClient.class);
        var filter = new ScanCatalogExistenceFilter(catalog, mock(ScanEvidenceCodec.class));
        UUID proposalId = UUID.randomUUID();
        var chunk = new ScanChunk();
        chunk.addProposal(proposal(proposalId));
        when(catalog.classify(any(), any()))
                .thenReturn(Map.of(
                        proposalId,
                        new CatalogExistenceClient.Result(
                                proposalId,
                                CatalogExistenceClient.Classification.EXACT_ASSET_EXISTS,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                null)));

        int skipped = filter.filter(context(false), chunk);

        assertThat(skipped).isEqualTo(1);
        assertThat(chunk.proposals()).isEmpty();
    }

    private ScanExecutionContext context(boolean overwriteExisting) {
        var root = new ScanProperties.Root("fixture", "unused", ScanProfile.JOKE_VIDEO);
        var snapshot = new ScanRegistrySnapshot(1L, "JOKE", List.of(), List.of());
        return new ScanExecutionContext(UUID.randomUUID(), "worker", root, snapshot, overwriteExisting);
    }

    private ScanProposalEntity proposal(UUID id) {
        return new ScanProposalEntity(
                id,
                UUID.randomUUID(),
                "A - [START-169] (BEST).mp4",
                ScanProfile.JOKE_VIDEO,
                "VIDEO",
                "JOKE:START-169:_",
                "A - [START-169]",
                "PRIMARY_VIDEO",
                "{}");
    }
}
