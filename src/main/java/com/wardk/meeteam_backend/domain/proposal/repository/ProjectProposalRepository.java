package com.wardk.meeteam_backend.domain.proposal.repository;

import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.domain.proposal.entity.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProjectProposalRepository extends JpaRepository<ProjectProposal, Long> {

    boolean existsByProjectIdAndReceiverIdAndStatus(Long projectId, Long receiverId, ProposalStatus status);

    long countByProposerIdAndCreatedAtAfter(Long proposerId, LocalDateTime since);

    @Query("""
            select p.project.id from ProjectProposal p
            where p.receiver.id = :receiverId and p.status = :status and p.project.id in :projectIds
            """)
    List<Long> findProjectIdsByReceiverAndStatus(@Param("receiverId") Long receiverId,
                                                 @Param("status") ProposalStatus status,
                                                 @Param("projectIds") Collection<Long> projectIds);
}
