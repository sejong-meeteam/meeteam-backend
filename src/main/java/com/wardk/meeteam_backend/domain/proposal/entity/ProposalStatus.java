package com.wardk.meeteam_backend.domain.proposal.entity;

/** Only PENDING exists this round. APPLIED, EXPIRED and WITHDRAWN arrive with the accept/expire flow. */
public enum ProposalStatus {
    PENDING
}
