# 회원 기술(skills) 응답 구조 변경 안내

> 대상 API: **유저 상세조회**(`GET /api/v1/members/{memberId}`), **내 정보조회**(`GET /api/v1/members/me`)
> 변경 요지: 기술 목록을 **직군별 그룹(`groupedSkills`)** 에서 **전체 기술 평면 리스트(`skills`)** 로 변경.
> ⚠️ **Breaking change** — 프론트엔드 반영 필요.

---

## 왜 바뀌었나

기존에는 기술을 **직군(JobField)에 속한 것만 필터링**해서 직군별로 그룹핑해 내려줬습니다.
그 결과 **직군에 매핑되지 않은 기술은 응답에서 누락**되는 문제가 있었습니다.

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
      "techStacks": ["Spring"]
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
  "skills": ["React.js", "Zustand", "Spring", "SwiftUI"]
}
```

### 변경 필드
| 구분 | 필드 | 타입 | 설명 |
|---|---|---|---|
| ❌ 제거 | `groupedSkills` | `List<{jobFieldName, jobPositionName, techStacks}>` | 직군별 그룹(직군 종속 기술만) |
| ✅ 추가 | `skills` | `List<String>` | 회원이 보유한 **전체 기술명** (직군 무관, displayOrder 순) |

> 그 외 필드(`jobPositions`(직군 목록), `representativePosition`, `participatedProjects` 등)는 **변경 없음**.

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
  "skills": ["React.js", "Spring"]   // 기존에도 존재했음
}
```

### After
```jsonc
{
  "memberId": 5,
  "name": "홍길동",
  // ...
  "skills": ["React.js", "Spring", "SwiftUI"]   // displayOrder 순으로 정렬
}
```

### 변경 필드
| 구분 | 필드 | 타입 | 설명 |
|---|---|---|---|
| ❌ 제거 | `groupedSkills` | `List<{jobFieldName, jobPositionName, techStacks}>` | 직군별 그룹 (중복이라 제거) |
| 🔄 유지·보강 | `skills` | `List<String>` | 기존에도 있던 전체 기술 리스트. 이제 **displayOrder 순으로 정렬**해서 반환 |

> 내 정보조회는 원래 `groupedSkills`와 `skills`를 **둘 다** 내려줬는데, 이제 `skills`만 남깁니다.

---

## 프론트 반영 체크리스트

- [ ] 유저 상세조회: `groupedSkills` 참조 코드를 `skills`(`string[]`)로 교체
- [ ] 내 정보조회: `groupedSkills` 참조 코드를 `skills`(`string[]`)로 교체
- [ ] 기술을 직군별로 묶어 보여주던 UI가 있다면, 평면 리스트 표시로 변경
- [ ] (참고) 두 API 모두 기술 정렬 기준은 `displayOrder`

---

## 참고: 서버 변경 사항
- `MemberDetailResponse` — `groupedSkills` → `skills` (직군 필터 제거)
- `MemberProfileResponse` — `groupedSkills` 제거, `skills` 유지(정렬 추가)
- `GroupedSkillResponse` DTO 클래스 삭제(미사용)
- 변경일: 2026-06-16