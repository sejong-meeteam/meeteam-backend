package com.wardk.meeteam_backend.web.project.dto.response;

import lombok.Data;
import org.springframework.data.domain.Page;

@Data
public class PageConditionResponse<T> {

    private int totalCount;

    private Page<T> content;


    public PageConditionResponse(Page<T> content) {
        this.totalCount = content.getNumberOfElements();
        this.content = content;
    }
}
