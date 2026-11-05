package com.wardk.meeteam_backend.domain.member.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.repository.Querydsl4RepositorySupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility;

import static com.wardk.meeteam_backend.domain.member.entity.QMember.member;
import static com.wardk.meeteam_backend.domain.member.entity.QMemberJobPosition.memberJobPosition;
import static com.wardk.meeteam_backend.domain.member.entity.QMemberTechStack.memberTechStack;
import static com.wardk.meeteam_backend.domain.projectmember.entity.QProjectMember.projectMember;
import static com.wardk.meeteam_backend.domain.job.entity.QTechStack.techStack;

@Slf4j
public class MemberRepositoryImpl extends Querydsl4RepositorySupport implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public MemberRepositoryImpl(JPAQueryFactory queryFactory) {
        super(Member.class);
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Member> searchMembersV1(String name, Long jobFieldId, List<String> techStackNames, Pageable pageable) {
        // projectCount 정렬이 있는지 확인
        boolean hasProjectCountSort = pageable.getSort().stream()
                .anyMatch(order -> "projectCount".equals(order.getProperty()));

        if (hasProjectCountSort) {
            return searchMembersV1WithProjectCountSort(name, jobFieldId, techStackNames, pageable);
        }

        return applyPagination(pageable, qf ->
                qf.select(member)
                        .from(member)
                        .where(
                                publiclyVisibleMember(),
                                nameContains(name),
                                jobFieldIdEquals(jobFieldId),
                                techStackNamesExist(techStackNames)
                        )
        );
    }

    /**
     * v1 검색 + projectCount 정렬 처리
     */
    private Page<Member> searchMembersV1WithProjectCountSort(String name, Long jobFieldId,
                                                              List<String> techStackNames, Pageable pageable) {
        JPAQuery<Member> query = queryFactory
                .select(member)
                .from(member)
                .where(
                        publiclyVisibleMember(),
                        nameContains(name),
                        jobFieldIdEquals(jobFieldId),
                        techStackNamesExist(techStackNames)
                );

        // 정렬 적용
        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

            if ("projectCount".equals(sortOrder.getProperty())) {
                JPQLQuery<Long> projectCountExpr = JPAExpressions
                        .select(projectMember.count())
                        .from(projectMember)
                        .where(projectMember.member.eq(member),
                                projectMember.project.visibility.eq(ProjectVisibility.VISIBLE));
                query.orderBy(new OrderSpecifier<>(direction, projectCountExpr));
            } else {
                getQuerydsl().applySorting(Sort.by(sortOrder), query);
            }
        }

        List<Member> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        publiclyVisibleMember(),
                        nameContains(name),
                        jobFieldIdEquals(jobFieldId),
                        techStackNamesExist(techStackNames)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression publiclyVisibleMember() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        return member.deletedAt.isNull()
                .and(member.provider.isNull().or(member.provider.ne("local-admin")))
                .and(member.role.ne(UserRole.ADMIN))
                .and(member.suspended.isFalse().or(member.suspendedUntil.loe(now)));
    }

    /**
     * 이름 검색 (부분 일치).
     */
    private BooleanExpression nameContains(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return member.realName.containsIgnoreCase(name);
    }

    /**
     * 직군 ID 조건.
     */
    private BooleanExpression jobFieldIdEquals(Long jobFieldId) {
        if (jobFieldId == null) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(memberJobPosition)
                .join(memberJobPosition.jobPosition)
                .where(
                        memberJobPosition.member.eq(member),
                        memberJobPosition.jobPosition.jobField.id.eq(jobFieldId)
                )
                .exists();
    }

    /**
     * 기술스택 이름 조건 (AND 조건).
     * 요청한 모든 기술스택을 가지고 있어야 매칭.
     */
    private BooleanExpression techStackNamesExist(List<String> techStackNames) {
        if (techStackNames == null || techStackNames.isEmpty()) {
            return null;
        }

        return techStackNames.stream()
                .map(this::hasTechStack)
                .reduce(BooleanExpression::and)
                .orElse(null);
    }

    private BooleanExpression hasTechStack(String techStackName) {
        return JPAExpressions
                .selectOne()
                .from(memberTechStack)
                .join(memberTechStack.techStack, techStack)
                .where(
                        memberTechStack.member.eq(member),
                        techStack.name.eq(techStackName)
                )
                .exists();
    }

    @Override
    public Page<Member> findAllWithProjectCountSort(Pageable pageable) {
        // projectCount 정렬이 있는지 확인
        boolean hasProjectCountSort = pageable.getSort().stream()
                .anyMatch(order -> "projectCount".equals(order.getProperty()));

        if (!hasProjectCountSort) {
            // projectCount 정렬이 없으면 기본 applyPagination 사용
            return applyPagination(pageable, qf ->
                    qf.select(member)
                            .from(member)
                            .where(publiclyVisibleMember())
            );
        }

        // projectCount 정렬 처리
        JPAQuery<Member> query = queryFactory
                .select(member)
                .from(member)
                .where(publiclyVisibleMember());

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

            if ("projectCount".equals(sortOrder.getProperty())) {
                JPQLQuery<Long> projectCountExpr = JPAExpressions
                        .select(projectMember.count())
                        .from(projectMember)
                        .where(projectMember.member.eq(member),
                                projectMember.project.visibility.eq(ProjectVisibility.VISIBLE));
                query.orderBy(new OrderSpecifier<>(direction, projectCountExpr));
            } else {
                getQuerydsl().applySorting(Sort.by(sortOrder), query);
            }
        }

        List<Member> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .where(publiclyVisibleMember());

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
