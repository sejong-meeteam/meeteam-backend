package com.wardk.meeteam_backend.domain.project.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import com.wardk.meeteam_backend.web.project.dto.request.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import static com.wardk.meeteam_backend.domain.recruitment.entity.QRecruitmentState.*;
import static com.wardk.meeteam_backend.domain.member.entity.QMember.*;
import static com.wardk.meeteam_backend.domain.project.entity.QProject.*;

public class ProjectRepositoryImpl extends Querydsl4RepositorySupport implements ProjectRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ProjectRepositoryImpl(JPAQueryFactory queryFactory) {
        super(Project.class);
        this.queryFactory = queryFactory;
    }

    /**
     * @deprecated Slice를 반환하는 {@link #searchWithSlice} 사용 권장.
     */
    @Deprecated
    public Page<Project> findAllSlicedForSearchAtCondition(ProjectSearchCondition condition, Pageable pageable) {
        validatePublicSort(pageable);

        Page<Project> projects = applyPagination(pageable, qf ->
            qf.select(project)
                .from(project)
                .join(project.creator, member).fetchJoin()
                .where(
                    publiclyVisible(),
                    platformCategoryEq(condition.getPlatformCategory()),
                    recruitmentEq(condition.getRecruitment()),
                    jobFieldCodeEq(condition.getJobFieldCode()),
                    meetingModeMatches(condition.getMeetingMode()),
                    projectCategoryEq(condition.getProjectCategory()),
                    keywordContains(condition.getKeyword())
                )
        );

        return projects;
    }

    @Override
    public Slice<Project> searchWithSlice(ProjectSearchCondition condition, Pageable pageable) {
        validatePublicSort(pageable);
        return applySlicing(pageable, qf ->
            qf.select(project)
                .from(project)
                .join(project.creator, member).fetchJoin()
                .where(
                    publiclyVisible(),
                    platformCategoryEq(condition.getPlatformCategory()),
                    recruitmentEq(condition.getRecruitment()),
                    jobFieldCodeEq(condition.getJobFieldCode()),
                    meetingModeMatches(condition.getMeetingMode()),
                    projectCategoryEq(condition.getProjectCategory()),
                    keywordContains(condition.getKeyword())
                )
        );
    }

    @Override
    public Page<Project> findProjectsFromMainPageCondition(CategoryCondition condition, Pageable pageable) {
        validatePublicSort(pageable);
        return applyPagination(pageable,qf ->
                qf.select(project)
                        .from(project)
                        .join(project.creator, member).fetchJoin()
                        .where(
                                publiclyVisible(),
                                projectCategoryEq(condition.getProjectCategory())
                        ));
    }


    private BooleanExpression jobFieldCodeEq(JobFieldCode jobFieldCode) {
        if (jobFieldCode == null) return null;

        return JPAExpressions
                .selectOne()
                .from(recruitmentState)
                .where(
                        recruitmentState.project.eq(project),
                        recruitmentState.jobPosition.jobField.code.eq(jobFieldCode)
                )
                .exists();
    }



    private BooleanExpression platformCategoryEq(PlatformCategory platformCategory) {
        return (platformCategory == null) ? null : project.platformCategory.eq(platformCategory);
    }

    private BooleanExpression recruitmentEq(Recruitment recruitment) {
        return (recruitment == null) ? null : project.recruitmentStatus.eq(recruitment);
    }


    private BooleanExpression meetingModeMatches(MeetingMode meetingMode) {
        return (meetingMode == null) ? null : project.collaboration.meetingMode.in(meetingMode.searchMatches());
    }

    private BooleanExpression projectCategoryEq(ProjectCategory projectCategory) {
        return (projectCategory == null) ? null : project.projectCategory.eq(projectCategory);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return project.name.containsIgnoreCase(keyword)
            .or(project.creator.namePrivate.isFalse().and(project.creator.realName.containsIgnoreCase(keyword)));
    }

    private void validatePublicSort(Pageable pageable) {
        if (pageable.getSort().stream().anyMatch(order -> order.getProperty()
                .toLowerCase(java.util.Locale.ROOT).contains("realname"))) {
            throw new com.wardk.meeteam_backend.global.exception.CustomException(
                    com.wardk.meeteam_backend.global.response.ErrorCode.INVALID_REQUEST);
        }
    }

    private BooleanExpression publiclyVisible() {
        return project.visibility.eq(ProjectVisibility.VISIBLE);
    }


}
