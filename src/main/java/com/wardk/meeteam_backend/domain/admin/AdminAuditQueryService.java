package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.admin.AdminResponses;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminAuditQueryService {
    private final EntityManager em;
    public AdminResponses.Page<AdminResponses.AuditLog> logs(AdminQueryOptions options, String action, String targetType, String targetId) {
        StringBuilder where = new StringBuilder(" where 1=1");
        Map<String, Object> params = new HashMap<>();
        AuditAction parsedAction = AdminQueryOptions.enumOrNull(AuditAction.class, action);
        if (parsedAction != null) { where.append(" and a.action = :action"); params.put("action", parsedAction); }
        AuditTargetType parsedType = AdminQueryOptions.enumOrNull(AuditTargetType.class, targetType);
        if (parsedType != null) { where.append(" and a.targetType = :type"); params.put("type", parsedType); }
        if (targetId != null && !targetId.isBlank()) {
            long id;
            try { id = Long.parseLong(targetId); }
            catch (NumberFormatException e) { throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST); }
            if (id <= 0) throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST);
            where.append(" and a.targetId = :id"); params.put("id", id);
        }
        var count = em.createQuery("select count(a.id) from AdminAuditLog a" + where, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();
        var query = em.createQuery("select a from AdminAuditLog a" + where + " order by a.createdAt desc, a.id desc", AdminAuditLog.class);
        params.forEach(query::setParameter);
        var result = query.setFirstResult(options.offset()).setMaxResults(options.size()).getResultList().stream()
                .map(a -> new AdminResponses.AuditLog(a.getId(), a.getAdminId(), a.getAdminName(), a.getAction().name(),
                        a.getTargetType().name(), a.getTargetId(), a.getTargetName(), a.getReason(), a.getMetadata(), a.getCreatedAt())).toList();
        return AdminResponses.Page.of(result, options.page(), options.size(), total);
    }
}
