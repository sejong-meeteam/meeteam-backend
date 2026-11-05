package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.MemberJobPosition;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.admin.AdminResponses;
import com.wardk.meeteam_backend.web.admin.AdminResponses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Administrator queries include hidden/deleted records; public repositories enforce visibility separately. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminQueryService {
    private final AdminQueryRepository queries;
    private final AdminResponseMapper mapper;

    public Page<MemberSummary> members(AdminQueryOptions options, String status, String role, String jobFieldCode) {
        var result = queries.members(options, status, role, jobFieldCode);
        Map<Long, JobPosition> positions = new HashMap<>();
        queries.positions(result.content().stream().map(r -> r.member().getId()).toList())
                .forEach(j -> positions.putIfAbsent(j.getMember().getId(), j.getJobPosition()));
        return Page.of(result.content().stream().map(r -> mapper.member(r, positions.get(r.member().getId()), true)).toList(),
                options.page(), options.size(), result.total());
    }
    public MemberDetail member(long id) {
        var row = queries.member(id).orElseThrow(AdminQueryService::notFound);
        Member m = row.member();
        List<MemberJobPosition> positions = queries.positions(List.of(id));
        var summary = mapper.member(row, positions.isEmpty() ? null : positions.get(0).getJobPosition(), false);
        return new MemberDetail(summary, m.getBirth(), m.getGender() == null ? null : m.getGender().name(),
                nullable(m.getGithubUrl()), nullable(m.getBlogUrl()), nullable(m.getIntroduction()),
                positions.stream().map(j -> j.getJobPosition().getName()).toList(), queries.skills(id), m.isSuspended() ? m.getSuspendReason() : null,
                queries.memberProjects(id).stream().map(p -> new MemberProject(p.getId(), p.getName(), p.getCategoryCode(),
                        p.getCreator() != null && id == p.getCreator().getId(), p.getRecruitmentStatus().name(),
                        AdminResponseMapper.visibility(p), p.getCreatedAt())).toList(),
                queries.memberApplications(id).stream().map(a -> new MemberApplication(a.getId(), a.getProject().getId(),
                        a.getProject().getName(), a.getJobPosition().getName(), a.getStatus().name(), a.getCreatedAt())).toList());
    }
    public Page<ProjectSummary> projects(AdminQueryOptions options, String category, String recruitmentStatus, String visibility) {
        var result = queries.projects(options, category, recruitmentStatus, visibility);
        return Page.of(result.content().stream().map(mapper::project).toList(), options.page(), options.size(), result.total());
    }
    public ProjectDetail project(long id) {
        var row = queries.project(id).orElseThrow(AdminQueryService::notFound);
        Project p = row.project();
        Map<Long, AdminResponses.ProjectMember> members = new LinkedHashMap<>();
        var associations = queries.projectMembers(id);
        Member leader = p.getCreator();
        if (leader != null) {
            String leaderPosition = associations.stream().filter(pm -> leader.getId().equals(pm.getMember().getId()))
                    .map(pm -> pm.getJobPosition() == null ? null : pm.getJobPosition().getName()).filter(Objects::nonNull).findFirst().orElse(null);
            members.put(leader.getId(), mapper.projectMember(leader, leaderPosition, true));
        }
        for (var pm : associations) members.putIfAbsent(pm.getMember().getId(), mapper.projectMember(pm.getMember(),
                pm.getJobPosition() == null ? null : pm.getJobPosition().getName(), false));
        var recruitments = queries.recruitments(id).stream().map(r -> new ProjectRecruitment(r.getJobField().getName(),
                r.getJobPosition().getName(), r.getRecruitmentCount(), r.getCurrentCount(), r.isClosed(),
                r.getRecruitmentTechStacks().stream().sorted(Comparator.comparing(t -> t.getTechStack().getName()))
                        .map(t -> t.getTechStack().getName()).toList())).toList();
        return new ProjectDetail(mapper.project(row), p.getDescription(), nullable(p.getGithubRepositoryUrl()),
                nullable(p.getCommunicationChannelUrl()), List.copyOf(members.values()), recruitments, queries.applicationStats(id));
    }
    private static String nullable(String s) { return s == null || s.isEmpty() ? null : s; }
    private static CustomException notFound() { return new CustomException(ErrorCode.ADMIN_TARGET_NOT_FOUND); }
}
