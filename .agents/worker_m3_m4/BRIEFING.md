# BRIEFING — 2026-07-25T11:04:47+07:00

## Mission
Implement Milestones M3 (Moderation, Content Filtering & Age Rating) and M4 (Copyright & Violation Management) for Novel-Plus.

## 🔒 My Identity
- Archetype: Implementer / QA / Specialist
- Roles: implementer, qa, specialist
- Working directory: d:\Project\novel-plus\.agents\worker_m3_m4
- Original parent: eecd2f86-2a79-442d-9131-1d0eb6169593
- Milestone: M3 & M4

## 🔒 Key Constraints
- CODE_ONLY mode, no external HTTP calls.
- DO NOT CHEAT, no hardcoded test results, no dummy implementations.
- Follow minimal change principle and project conventions.

## Current Parent
- Conversation ID: eecd2f86-2a79-442d-9131-1d0eb6169593
- Updated: 2026-07-25T11:04:47+07:00

## Task Summary
- **What to build**:
  1. DB migration file `doc/sql/20260725_moderation_copyright.sql`
  2. Fix comment leak in `BookCommentMapper.xml`
  3. Build `SensitiveWordFilter`, `SimHashUtil`, `ContentHashUtil` in `novel-common`
  4. Age restriction checks in `novel-front`
  5. Moderation queues for novels, chapters, comments, images in `novel-front` & `novel-admin`
  6. Chapter version history snapshotting in `BookServiceImpl`
  7. Copyright report intake, counter-appeal workflow, ownership proof storage in `novel-front` & `novel-admin`
  8. Comprehensive unit & integration tests for all R3/R4 features
  9. Run `mvn clean test` to ensure 100% pass
- **Success criteria**: All code compiles cleanly, all tests pass, genuine implementation with proper DB, entity, mapper, service, controller, and utility classes.

## Change Tracker
- **Files modified**: none yet
- **Build status**: not built yet
- **Pending issues**: TBD

## Quality Status
- **Build/test result**: TBD
- **Lint status**: TBD
- **Tests added/modified**: TBD

## Loaded Skills
- None
