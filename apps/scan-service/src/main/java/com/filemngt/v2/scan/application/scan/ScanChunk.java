package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.ArrayList;
import java.util.List;

/** State mutable của một chunk đang chờ classify, analyze và commit. */
final class ScanChunk {
    private final int batchSize;
    private final List<ScanInventoryItem> inventoryItems = new ArrayList<>();
    private final List<ScanProposalEntity> proposals = new ArrayList<>();
    private final List<ScanIssueEntity> issues = new ArrayList<>();

    ScanChunk(int batchSize) {
        this.batchSize = batchSize;
    }

    void addInventory(ScanInventoryItem item) {
        inventoryItems.add(item);
    }

    void addProposal(ScanProposalEntity proposal) {
        proposals.add(proposal);
    }

    void addIssue(ScanIssueEntity issue) {
        issues.add(issue);
    }

    boolean shouldFlush() {
        return inventoryItems.size() >= batchSize || proposals.size() + issues.size() >= batchSize;
    }

    boolean hasItems() {
        return !inventoryItems.isEmpty() || !proposals.isEmpty() || !issues.isEmpty();
    }

    List<ScanInventoryItem> inventoryItems() {
        return inventoryItems;
    }

    List<ScanProposalEntity> proposals() {
        return proposals;
    }

    List<ScanIssueEntity> issues() {
        return issues;
    }

    void clear() {
        inventoryItems.clear();
        proposals.clear();
        issues.clear();
    }
}
