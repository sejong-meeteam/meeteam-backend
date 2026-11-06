package com.wardk.meeteam_backend.domain.admin.repository;

import com.wardk.meeteam_backend.domain.admin.entity.AdminAuditLog;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminAuditWriter {
    private final EntityManager em;
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AdminAuditLog log) { em.persist(log); }
}
