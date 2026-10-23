# 회원 기술(skills) 응답 구조 변경 안내

> 대상 API: **유저 상세조회**(`GET /api/v1/members/{memberId}`), **내 정보조회**(`GET /api/v1/members/me`)
> 변경 요지: 기술 목록을 **직군별 그룹(`groupedSkills`)** 에서 **전체 기술 평면 리스트(`skills`)** 로 변경.
> 기준 커밋: `3865b451663788792d7e78c736282f8b9ff105f1`. 현재 DTO의 평면 기술 응답 구현을 정적으로 확인했습니다. 테스트 실행·운영 반영·과거 버전과의 diff 검증은 수행하지 않았습니다.
> 프론트엔드가 과거 groupedSkills를 사용한다면 변경 대응이 필요합니다. 아래 Before는 과거 구조를 설명하는 예시이며 기준 커밋의 현재 응답이 아닙니다.
> 모든 JSON 예시는 SuccessResponse의 result 내부만 발췌합니다.

---

## 왜 바뀌었나

이 문서의 기존 변경 배경은 직군별 그룹핑으로 직군에 매핑되지 않은 기술이 누락될 수 있었다는 설명입니다. 과거 구현·장애 재현은 이번 기준 커밋 점검 범위에서 검증하지 않았습니다.

변경 후에는 **회원이 보유한 모든 기술**을 직군과 무관하게 평면 리스트로 내려줍니다. (정렬: `displayOrder` 순)

---

## 1. 유저 상세조회 — `GET /api/v1/members/{memberId}`

### Before
```jsonc
{
  "memberId": 5,
  "name": "홍길동",
  // ...
  "groupedSkills": [
    {
      "jobFieldName": "프론트",
      "jobPositionName": "웹 프론트엔드",
      "techStacks": ["React.js", "Zustand"]
    },
    {
      "jobFieldName": "백엔드",
      "jobPositionName": "서버",
      "techStacks": ["Spring Boot"]
    }
  ]
}
```

### After
```jsonc
{
  "memberId": 5,
  "name": "홍길동",
  // ...
  "skills": ["React.js", "Zustand", "Spring Boot", "SwiftUI"]
}
```

### 변경 안내 (Before는 과거 구조 설명용)
| 구분 | 필드 | 타입 | 설명 |
|---|---|---|---|
| ❌ 제거 | `groupedSkills` | `List<{jobFieldName, jobPositionName, techStacks}>` | 직군별 그룹(직군 종속 기술만) |
| ✅ 추가 | `skills` | `List<String>` | 회원이 보유한 **전체 기술명** (직군 무관, displayOrder 순) |

> 현재 상세 DTO의 `jobPositions`는 **직무 이름 목록**입니다. `representativePosition`은 첫 직무 코드의 영문명이고 `participatedProjects`는 참여 프로젝트 카드입니다. 다른 필드의 과거 대비 불변 여부는 검증하지 않았습니다.

---

## 2. 내 정보조회 — `GET /api/v1/members/me`

### Before
```jsonc
{
  "memberId": 5,
  "name": "홍길동",
  // ...
  "groupedSkills": [
    { "jobFieldName": "프론트", "jobPositionName": "웹 프론트엔드", "techStacks": ["React.js"] }
  ],
  "skills": ["React.js", "Spring Boot"]   // 과거 구조 설명용 예시
}
```

### After
```jsonc
{
  "memberId": 5,
  "name": "홍길동",
  // ...
  "skills": ["React.js", "Spring Boot", "SwiftUI"]   // displayOrder 순으로 정렬
}
```

### 변경 필드
| 구분 | 필드 | 타입 | 설명 |
|---|---|---|---|
| ❌ 제거 | `groupedSkills` | `List<{jobFieldName, jobPositionName, techStacks}>` | 직군별 그룹 (중복이라 제거) |
| 🔄 유지·보강 | `skills` | `List<String>` | 현재 DTO에서 **displayOrder 순으로 정렬**해 반환하는 전체 기술 리스트 |

> 기준 커밋의 내 정보 DTO에는 `skills`만 있으며 `groupedSkills`는 없습니다. 상세 응답과 달리 내 정보의 `representativePosition`은 한글 직무명, `representativePositionEn`은 영문명이고 참여 프로젝트는 `projectCards`입니다.

---

## 프론트 반영 체크리스트

- [ ] 유저 상세조회: `groupedSkills` 참조 코드를 `skills`(`string[]`)로 교체
- [ ] 내 정보조회: `groupedSkills` 참조 코드를 `skills`(`string[]`)로 교체
- [ ] 기술을 직군별로 묶어 보여주던 UI가 있다면, 평면 리스트 표시로 변경
- [ ] (참고) 두 API 모두 기술 정렬 기준은 `displayOrder`

---

## 참고: 서버 변경 사항
- `web/member/controller/MemberController.java:42-47,77-85`: 두 v1 조회 경로와 응답 DTO 확인.
- `web/member/dto/response/MemberDetailResponse.java:65-66,94-97`: 전체 memberTechStacks를 displayOrder 오름차순으로 정렬하고 기술 이름으로 변환.
- `web/member/dto/response/MemberProfileResponse.java:43-44,76-79`: 같은 평면 리스트·정렬 구현.
- 기준 커밋에 `GroupedSkillResponse` 클래스는 없지만 삭제 시점·과거 변경일은 이번 점검으로 입증하지 않습니다.
- 위 Java 경로의 루트는 `src/main/java/com/wardk/meeteam_backend/`입니다.
- 기술 이름 예시는 회원 저장 데이터의 설명입니다. `Spring`은 현재 job seed에 없으므로 신규 선택 옵션 예시로는 `Spring Boot`를 사용합니다.