---
name: commit
description: 현재 변경사항을 분석하고 자동으로 커밋합니다.
allowed-tools:
  - Bash
  - Read
  - Glob
---

# Commit 스킬

현재 변경사항을 분석하여 커밋 메시지를 자동 생성하고 커밋합니다.

## 수행 단계

1. `git status`로 변경된 파일 확인 (untracked 포함)
2. `git diff`로 staged/unstaged 변경 내용 확인
3. `git log --oneline -5`로 최근 커밋 스타일 확인
4. 변경 내용을 분석하여 적절한 커밋 메시지 작성
5. 사용자에게 커밋 메시지 확인
6. 파일 staging 후 커밋 수행

## 커밋 메시지 규칙

- 한글로 작성
- prefix 사용: feat, fix, refactor, chore, docs, test
- 형식: `prefix: 변경 요약`
- 예시: `feat: 팀원 검색 AND 조건 필터링 추가`

## 주의사항

- `.env`, `credentials` 등 민감한 파일은 커밋 제외
- 변경사항 없으면 "커밋할 변경사항이 없습니다" 안내