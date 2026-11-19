package com.wardk.meeteam_backend.domain.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지원 전에 확인하는 협업 방식 정보. 모든 값은 선택 항목입니다.
 * Querydsl 5 APT가 record 임베디드 타입을 처리하지 못해 클래스로 둡니다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCollaboration {

    public static final ProjectCollaboration EMPTY = new ProjectCollaboration(null, null, null);

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_mode", length = 20)
    private MeetingMode meetingMode;

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(name = "meeting_note", length = 100)
    private String meetingNote;

    public ProjectCollaboration(MeetingMode meetingMode, Integer weeklyHours, String meetingNote) {
        this.meetingMode = meetingMode;
        this.weeklyHours = weeklyHours;
        this.meetingNote = meetingNote == null || meetingNote.isBlank() ? null : meetingNote;
    }
}
