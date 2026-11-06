package com.wardk.meeteam_backend.domain.admin.service;

import com.wardk.meeteam_backend.domain.admin.dto.AdminQueryOptions;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.repository.AdminDashboardRepository;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminDashboardService {
    private final AdminDashboardRepository totals;
    private final AdminQueryRepository queries;
    private final AdminResponseMapper mapper;
    private final AdminAuditQueryService audit;
    private final Clock clock;
    public AdminDashboardService(AdminDashboardRepository totals, AdminQueryRepository queries,
                                 AdminResponseMapper mapper, AdminAuditQueryService audit,
                                 @Qualifier("adminClock") Clock clock) {
        this.totals = totals; this.queries = queries; this.mapper = mapper; this.audit = audit; this.clock = clock;
    }
    public AdminResponses.Dashboard dashboard() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
        LocalDateTime start = now.toLocalDate().atStartOfDay();
        var members = totals.members(now, start, start.plusDays(1));
        var projects = totals.projects();
        var stats = new AdminResponses.DashboardStats(members.total(), members.today(), members.suspended(),
                projects.total(), projects.recruiting(), projects.hidden(), totals.pendingApplications());
        var recent = queries.recentMembers();
        Map<Long, JobPosition> positions = new HashMap<>();
        queries.positions(recent.stream().map(row -> row.member().getId()).toList())
                .forEach(position -> positions.putIfAbsent(position.getMember().getId(), position.getJobPosition()));
        return new AdminResponses.Dashboard(stats,
                recent.stream().map(row -> mapper.member(row, positions.get(row.member().getId()), true, now)).toList(),
                queries.recentProjects().stream().map(mapper::project).toList(),
                audit.logs(AdminQueryOptions.of("0", "5", null, null), null, null, null).content());
    }
}
