package com.wardk.meeteam_backend.integration.project.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.ProjectQueryService;
import com.wardk.meeteam_backend.fixture.MemberFixture;
import com.wardk.meeteam_backend.fixture.ProjectFixture;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchRequest;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSortType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/project-search-data.sql")
@DisplayName("ProjectQueryService 통합 테스트")
class ProjectQueryServiceIntegrationTest {

    // ==================== 상수 (시드 데이터 기반) ====================

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int TOTAL_SEED_PROJECTS = 20;

    // 시드 데이터의 카테고리별 개수
    private static final int CAPSTONE_COUNT = 10;           // ID: 1,2,3,8,9,14,15,16,17,18
    private static final int CREATIVE_SEMESTER_COUNT = 4;   // ID: 4,5,10,12
    private static final int CLUB_COUNT = 6;                // ID: 6,7,11,13,19,20

    // 시드 데이터의 플랫폼별 개수
    private static final int WEB_COUNT = 11;
    private static final int IOS_COUNT = 4;
    private static final int ANDROID_COUNT = 5;

    // 시드 데이터의 모집상태별 개수
    private static final int RECRUITING_COUNT = 16;
    private static final int CLOSED_COUNT = 2;
    private static final int SUSPENDED_COUNT = 2;

    // 키워드 검색 상수
    private static final String KEYWORD_AI = "AI";
    private static final String KEYWORD_BACKEND = "백엔드";
    private static final String KEYWORD_HONG = "홍길동";
    private static final String KEYWORD_NOT_EXIST = "존재하지않는키워드XYZ";

    // ==================== 의존성 ====================

    @Autowired
    private ProjectQueryService projectQueryService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MemberRepository memberRepository;

    // ==================== 테스트 ====================

    @Nested
    @DisplayName("searchV1 메서드는")
    class SearchV1 {

        @Nested
        @DisplayName("키워드 검색 시")
        class WithKeyword {

            @Test
            @DisplayName("프로젝트명에 키워드가 포함된 프로젝트를 반환한다")
            void returns_projects_when_keyword_matches_project_name() {
                // given - 시드 데이터: AI 포함 프로젝트 3개 (ID 1,2,3)

                // when
                Slice<ProjectCardResponse> result = search(requestWithKeyword(KEYWORD_AI));

                // then
                assertThat(result.getContent()).isNotEmpty();
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getProjectName().contains(KEYWORD_AI));
            }

            @Test
            @DisplayName("리더 이름에 키워드가 포함된 프로젝트를 반환한다")
            void returns_projects_when_keyword_matches_leader_name() {
                // given - 시드 데이터: 홍길동(ID 1)이 만든 프로젝트들

                // when
                Slice<ProjectCardResponse> result = search(requestWithKeyword(KEYWORD_HONG));

                // then
                assertThat(result.getContent()).isNotEmpty();
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getCreatorName().equals(KEYWORD_HONG));
            }

            @Test
            @DisplayName("검색 결과가 없으면 빈 Slice를 반환한다")
            void returns_empty_slice_when_no_results() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithKeyword(KEYWORD_NOT_EXIST));

                // then
                assertThat(result.getContent()).isEmpty();
                assertThat(result.hasNext()).isFalse();
            }
        }

        @Nested
        @DisplayName("카테고리 필터 시")
        class WithCategoryFilter {

            @Test
            @DisplayName("CAPSTONE 카테고리의 프로젝트만 반환한다")
            void returns_only_capstone_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithCategory(ProjectCategory.CAPSTONE));

                // then
                assertThat(result.getContent()).hasSize(CAPSTONE_COUNT);
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getCategoryCode().equals(ProjectCategory.CAPSTONE.name()));
            }

            @Test
            @DisplayName("CREATIVE_SEMESTER 카테고리의 프로젝트만 반환한다")
            void returns_only_creative_semester_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithCategory(ProjectCategory.CREATIVE_SEMESTER));

                // then
                assertThat(result.getContent()).hasSize(CREATIVE_SEMESTER_COUNT);
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getCategoryCode().equals(ProjectCategory.CREATIVE_SEMESTER.name()));
            }
        }

        @Nested
        @DisplayName("플랫폼 필터 시")
        class WithPlatformFilter {

            @Test
            @DisplayName("IOS 플랫폼의 프로젝트만 반환한다")
            void returns_only_ios_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithPlatform(PlatformCategory.IOS));

                // then
                assertThat(result.getContent()).hasSize(IOS_COUNT);
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getPlatformName().equals(PlatformCategory.IOS.name()));
            }

            @Test
            @DisplayName("ANDROID 플랫폼의 프로젝트만 반환한다")
            void returns_only_android_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithPlatform(PlatformCategory.ANDROID));

                // then
                assertThat(result.getContent()).hasSize(ANDROID_COUNT);
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getPlatformName().equals(PlatformCategory.ANDROID.name()));
            }
        }

        @Nested
        @DisplayName("모집상태 필터 시")
        class WithRecruitmentFilter {

            @Test
            @DisplayName("RECRUITING 상태의 프로젝트만 반환한다")
            void returns_only_recruiting_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithRecruitment(Recruitment.RECRUITING));

                // then
                assertThat(result.getContent()).hasSize(RECRUITING_COUNT);
            }

            @Test
            @DisplayName("CLOSED 상태의 프로젝트만 반환한다")
            void returns_only_closed_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithRecruitment(Recruitment.CLOSED));

                // then
                assertThat(result.getContent()).hasSize(CLOSED_COUNT);
            }

            @Test
            @DisplayName("SUSPENDED 상태의 프로젝트만 반환한다")
            void returns_only_suspended_projects() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithRecruitment(Recruitment.SUSPENDED));

                // then
                assertThat(result.getContent()).hasSize(SUSPENDED_COUNT);
            }
        }

        @Nested
        @DisplayName("정렬 시")
        class WithSort {

            @Test
            @DisplayName("DEADLINE 정렬 시 마감임박순으로 반환한다")
            void returns_projects_sorted_by_deadline_ascending() {
                // given - 시드 데이터: 긴급 프로젝트(2024-06-10) vs 여유 프로젝트(2025-01-31)

                // when
                Slice<ProjectCardResponse> result = search(requestWithSort(ProjectSortType.DEADLINE));

                // then
                assertThat(result.getContent()).isNotEmpty();

                // 첫 번째가 마감일이 가장 빠른 프로젝트
                ProjectCardResponse first = result.getContent().get(0);
                ProjectCardResponse last = result.getContent().get(result.getContent().size() - 1);
                assertThat(first.getEndDate()).isBefore(last.getEndDate());
            }

            @Test
            @DisplayName("LATEST 정렬 시 최신순으로 반환한다")
            void returns_projects_sorted_by_latest() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithSort(ProjectSortType.LATEST));

                // then
                assertThat(result.getContent()).isNotEmpty();
                // ID가 높을수록 최신 (시드 데이터 기준)
                assertThat(result.getContent().get(0).getProjectId())
                        .isGreaterThan(result.getContent().get(result.getContent().size() - 1).getProjectId());
            }
        }

        @Nested
        @DisplayName("페이징 시")
        class WithPagination {

            @Test
            @DisplayName("페이지 크기만큼 결과를 반환한다")
            void returns_page_size_results() {
                // when
                Slice<ProjectCardResponse> result = search(emptyRequest());

                // then
                assertThat(result.getContent()).hasSize(DEFAULT_PAGE_SIZE);
                assertThat(result.hasNext()).isFalse(); // 20개 중 20개 조회 → 다음 페이지 없음
            }

            @Test
            @DisplayName("커스텀 페이지 크기로 조회한다")
            void returns_custom_page_size_results() {
                // when
                Slice<ProjectCardResponse> result = search(emptyRequest(), pageableOf(0, 5));

                // then
                assertThat(result.getContent()).hasSize(5);
                assertThat(result.hasNext()).isTrue(); // 20개 중 5개 조회 → 다음 페이지 있음
            }

            @Test
            @DisplayName("마지막 페이지에서 hasNext가 false이다")
            void returns_false_has_next_on_last_page() {
                // when - 4번째 페이지 (인덱스 3), 페이지당 5개 → 마지막 페이지
                Slice<ProjectCardResponse> result = search(emptyRequest(), pageableOf(3, 5));

                // then
                assertThat(result.getContent()).hasSize(5);
                assertThat(result.hasNext()).isFalse();
            }
        }

        @Nested
        @DisplayName("복합 필터 시")
        class WithCombinedFilters {

            @Test
            @DisplayName("카테고리와 플랫폼을 동시에 필터링한다")
            void filters_by_category_and_platform() {
                // when - CAPSTONE + WEB
                Slice<ProjectCardResponse> result = search(
                        new ProjectSearchRequest(null, ProjectCategory.CAPSTONE, null, PlatformCategory.WEB, null, null)
                );

                // then
                assertThat(result.getContent()).isNotEmpty();
                assertThat(result.getContent()).allSatisfy(dto -> {
                    assertThat(dto.getCategoryCode()).isEqualTo(ProjectCategory.CAPSTONE.name());
                    assertThat(dto.getPlatformName()).isEqualTo(PlatformCategory.WEB.name());
                });
            }

            @Test
            @DisplayName("키워드와 모집상태를 동시에 필터링한다")
            void filters_by_keyword_and_recruitment() {
                // when - "개발" + RECRUITING
                Slice<ProjectCardResponse> result = search(
                        new ProjectSearchRequest("개발", null, Recruitment.RECRUITING, null, null, null)
                );

                // then
                assertThat(result.getContent()).isNotEmpty();
                assertThat(result.getContent())
                        .allMatch(dto -> dto.getProjectName().contains("개발"));
            }
        }

        @Nested
        @DisplayName("DTO 변환 시")
        class DtoConversion {

            @Test
            @DisplayName("비로그인 사용자는 isLiked가 false이다")
            void is_liked_false_for_anonymous_user() {
                // when
                Slice<ProjectCardResponse> result = search(emptyRequest());

                // then
                assertThat(result.getContent())
                        .allMatch(dto -> !dto.isLiked());
            }

            @Test
            @DisplayName("카테고리 displayName이 올바르게 변환된다")
            void category_display_name_is_converted() {
                // when
                Slice<ProjectCardResponse> result = search(requestWithCategory(ProjectCategory.CAPSTONE));

                // then
                assertThat(result.getContent()).isNotEmpty();
                assertThat(result.getContent().get(0).getCategoryName())
                        .isEqualTo(ProjectCategory.CAPSTONE.getDisplayName());
            }

            @Test
            @DisplayName("필수 필드들이 null이 아니다")
            void required_fields_are_not_null() {
                // when
                Slice<ProjectCardResponse> result = search(emptyRequest());

                // then
                assertThat(result.getContent()).allSatisfy(dto -> {
                    assertThat(dto.getProjectId()).isNotNull();
                    assertThat(dto.getProjectName()).isNotNull();
                    assertThat(dto.getCreatorName()).isNotNull();
                    assertThat(dto.getCategoryCode()).isNotNull();
                    assertThat(dto.getCategoryName()).isNotNull();
                    assertThat(dto.getPlatformName()).isNotNull();
                });
            }
        }
    }

    // ==================== 동적 데이터 테스트 (Fixture 사용) ====================

    @Nested
    @DisplayName("동적 데이터 테스트")
    @Sql(scripts = "/sql/project-search-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
    class DynamicDataTests {

        @Test
        @DisplayName("새로 생성한 프로젝트가 검색된다")
        void searches_newly_created_project() {
            // given
            Member newLeader = memberRepository.save(MemberFixture.withName("신규리더"));
            projectRepository.save(ProjectFixture.withName("완전새로운프로젝트XYZ", newLeader));

            // when
            Slice<ProjectCardResponse> result = search(requestWithKeyword("완전새로운프로젝트XYZ"));

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getProjectName()).isEqualTo("완전새로운프로젝트XYZ");
        }
    }

    // ==================== 헬퍼 메서드 ====================

    private Pageable defaultPageable() {
        return PageRequest.of(DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    private Pageable pageableOf(int page, int size) {
        return PageRequest.of(page, size);
    }

    private ProjectSearchRequest requestWithKeyword(String keyword) {
        return new ProjectSearchRequest(keyword, null, null, null, null, null);
    }

    private ProjectSearchRequest requestWithCategory(ProjectCategory category) {
        return new ProjectSearchRequest(null, category, null, null, null, null);
    }

    private ProjectSearchRequest requestWithRecruitment(Recruitment recruitment) {
        return new ProjectSearchRequest(null, null, recruitment, null, null, null);
    }

    private ProjectSearchRequest requestWithPlatform(PlatformCategory platform) {
        return new ProjectSearchRequest(null, null, null, platform, null, null);
    }

    private ProjectSearchRequest requestWithSort(ProjectSortType sort) {
        return new ProjectSearchRequest(null, null, null, null, null, sort);
    }

    private ProjectSearchRequest emptyRequest() {
        return new ProjectSearchRequest(null, null, null, null, null, null);
    }

    private Slice<ProjectCardResponse> search(ProjectSearchRequest request) {
        return projectQueryService.searchV1(request, defaultPageable(), null);
    }

    private Slice<ProjectCardResponse> search(ProjectSearchRequest request, Pageable pageable) {
        return projectQueryService.searchV1(request, pageable, null);
    }
}
