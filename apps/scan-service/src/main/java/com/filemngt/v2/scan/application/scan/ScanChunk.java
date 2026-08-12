package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.ArrayList;
import java.util.List;

/** State mutable của một chunk đang chờ classify, analyze và commit. */
final class ScanChunk {
    private final List<ScanInventoryItem> changedInventoryItems = new ArrayList<>();
    private final List<ScanProposalEntity> proposals = new ArrayList<>();
    private final List<ScanIssueEntity> issues = new ArrayList<>();

    void addProposal(ScanProposalEntity proposal) {
        proposals.add(proposal);
    }

    void addChangedInventory(ScanInventoryItem item) {
        changedInventoryItems.add(item);
    }

    void addIssue(ScanIssueEntity issue) {
        issues.add(issue);
    }

    void replaceProposals(List<ScanProposalEntity> filtered) {
        proposals.clear();
        proposals.addAll(filtered);
    }

    List<ScanInventoryItem> changedInventoryItems() {
        return changedInventoryItems;
    }

    List<ScanProposalEntity> proposals() {
        return proposals;
    }

    List<ScanIssueEntity> issues() {
        return issues;
    }
}
