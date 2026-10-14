---
name: push
description: 현재 브랜치를 push하고 GitHub PR을 생성합니다.
allowed-tools:
  - Bash
  - Read
  - Glob
---

# Push & PR 스킬

현재 브랜치를 push하고 GitHub PR을 생성합니다.

## 수행 단계

1. `git status`로 커밋되지 않은 변경사항 확인
   - 있으면 먼저 커밋할지 사용자에게 질문
2. `git branch --show-current`로 현재 브랜치 확인
3. `git log main..HEAD --oneline`로 PR에 포함될 커밋 확인
4. `git diff main...HEAD`로 전체 변경사항 분석
5. 원격에 브랜치 push (`git push -u origin <branch>`)
6. `gh pr create`로 PR 생성

## PR Template 형식

`.github/pull_request_template.md` 파일을 참고하여 작성:

```markdown
## 📝 변경 사항

- [변경1]
- [변경2]
- [변경3]

---

## 🔥 많이 투자한 부분

[이 PR에서 가장 공들인 부분 설명]

---

## 👀 리뷰에 신경 써줬으면 하는 부분

- [리뷰 포인트1]
- [리뷰 포인트2]
```

## 주의사항

- main/master 브랜치에서는 PR 생성 불가 (경고)
- 이미 PR이 존재하면 링크 안내
- PR 생성 전 사용자에게 제목/내용 확인