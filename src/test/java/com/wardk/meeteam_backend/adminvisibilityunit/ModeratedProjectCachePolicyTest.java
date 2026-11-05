package com.wardk.meeteam_backend.adminvisibilityunit;

import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.ProjectQueryService;
import com.wardk.meeteam_backend.domain.project.service.ProjectQueryServiceImpl;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ModeratedProjectCachePolicyTest.Config.class)
class ModeratedProjectCachePolicyTest {
    @Configuration @EnableCaching
    static class Config {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager("mainPageProjects"); }
        @Bean ProjectRepository projects() { return mock(ProjectRepository.class); }
        @Bean ProjectQueryService service(ProjectRepository projects) {
            return new ProjectQueryServiceImpl(projects, mock(ProjectMemberRepository.class),
                    mock(ProjectLikeRepository.class), mock(RecruitmentStateRepository.class));
        }
    }
    @Autowired ProjectQueryService service;
    @Autowired ProjectRepository projects;
    @Autowired CacheManager caches;

    @Test void evenAnExistingAnonymousMainPageCacheCannotBypassCurrentDatabaseVisibility() {
        var paging = PageRequest.of(0, 20);
        var condition = new CategoryCondition(null);
        caches.getCache("mainPageProjects").put("page_0_size_20_sort_UNSORTED_category_all",
                new PageImpl<>(List.of(mock(ProjectCardResponse.class)), paging, 1));
        when(projects.findProjectsFromMainPageCondition(condition, paging)).thenReturn(Page.empty(paging));
        assertThat(service.searchForMainPage(condition, paging, null).getContent()).isEmpty();
        assertThat(service.searchForMainPage(condition, paging, null).getContent()).isEmpty();
        verify(projects, times(2)).findProjectsFromMainPageCondition(condition, paging);
    }
}
