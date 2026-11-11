# 19th-team2-server

디프만 19기 2팀 서버

## 🌳 브랜치 전략

| 브랜치 | 용도 | 머지 대상 |
|---|---|---|
| `main` | 운영(prod) 배포 | - |
| `develop` | 개발(dev) 통합 | `main` |
| `feature/{이슈번호}-{설명}` | 기능 개발 · 리팩터링 · 환경 설정 | `develop` |
| `hotfix/{이슈번호}-{설명}` | 버그 수정 | `develop` |
| `release/vX.Y.Z` | 운영 배포 준비 | `main` |

```
feature/12-kakao-login ─┐
hotfix/15-token-expire ─┼─▶ develop ─▶ release/v1.0.0 ─▶ main
```

- 작업 흐름은 항상 **이슈 → 브랜치 → PR** 순서입니다.
- 브랜치는 최신 `develop` 에서 땁니다. 작업 중 `develop` 이 바뀌면 rebase 로 따라갑니다.
- `main` · `develop` 에 직접 push 하지 않고 PR 로만 머지합니다.
- PR 은 **Squash and merge** 로 머지합니다.
- 운영 배포는 `develop` 에서 `release/vX.Y.Z` 를 따서 `main` 으로 PR 을 올립니다.

## ✍️ 커밋 · PR 제목

```
Type: [도메인] 내용
```

| Type | 용도 | 라벨 |
|---|---|---|
| `Feat` | 기능 개발 | 🚀 feat |
| `Fix` | 버그 수정 | 🐛 bug |
| `Refactor` | 리팩터링 | 🔨 refactor |
| `Chore` | 빌드 · 환경 설정 · 배포 | 🌐 chore |
| `Docs` | 문서 | 📝 docs |
| `Test` | 테스트 | - |

예시
- `Feat: [회원] 카카오 로그인`
- `Fix: [인증] 토큰 만료 시 재발급 실패 수정`
- `Release v1.0.0` (release PR)

내용은 명사형으로 끝냅니다. 커밋 제목에 이슈 번호는 붙이지 않고, PR 본문의 관련 이슈 칸에 적습니다.
