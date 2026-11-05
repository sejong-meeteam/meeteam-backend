package com.wardk.meeteam_backend.web.github.integration;

import com.wardk.meeteam_backend.domain.github.activity.GithubActivityQuery;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectListResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Slice;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.*;

/** Enrich after cached domain reads, in one batch, so activity badges do not become stale in page caches. */
@RestControllerAdvice @RequiredArgsConstructor
@ConditionalOnProperty(prefix="integration.github.app",name="enabled",havingValue="true")
public class GithubProjectCardAdvice implements ResponseBodyAdvice<Object> {
    private final GithubActivityQuery activity;
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        var cards=new ArrayList<Target>();collect(body,cards);
        if(cards.isEmpty())return body;
        var ids=cards.stream().map(Target::id).filter(Objects::nonNull).distinct().toList();
        var summaries=activity.summaries(ids);
        for(var card:cards){var summary=summaries.get(card.id());card.setter().accept(summary==null?null:
                new ProjectCardResponse.GithubActivity(summary.activityLevel(),summary.lastPushedAt()));}
        return body;
    }
    private record Target(Long id,java.util.function.Consumer<ProjectCardResponse.GithubActivity> setter) {}
    private static void collect(Object value,List<Target> cards){
        if(value instanceof SuccessResponse<?> success)collect(success.getResult(),cards);
        else if(value instanceof Slice<?> slice)for(Object item:slice.getContent())collect(item,cards);
        else if(value instanceof List<?> list)for(Object item:list)collect(item,cards);
        else if(value instanceof ProjectCardResponse card)cards.add(new Target(card.getProjectId(),card::setGithubActivity));
        else if(value instanceof ProjectListResponse card)cards.add(new Target(card.getProjectId(),card::setGithubActivity));
        else if(value instanceof ProjectSearchResponse card)cards.add(new Target(card.getProjectId(),card::setGithubActivity));
        else if(value instanceof MemberProfileResponse profile&&profile.getProjectCards()!=null)collect(profile.getProjectCards(),cards);
    }
}
