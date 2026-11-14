# Tiandili Schedules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development and dev-tdd to implement this plan task-by-task.

**Goal:** Add local schedule CRUD, controlled recurrence, persisted reminder jobs, and a usable schedule list/editor without depending on Android's system calendar.

**Architecture:** Keep recurrence and validation in pure Kotlin. Room owns persisted series, rules, exceptions, and reminder outbox rows; repositories map entities to app-owned domain models. UI consumes immutable state and never reads Room entities directly.

**Tech Stack:** Kotlin, Java Time, Room, KSP, StateFlow, Compose Material 3, JUnit, Truth, Room in-memory tests.

---

## Task 1: Recurrence Domain

**Files:**
- Create: `app/src/main/kotlin/com/tiandili/calendar/domain/schedule/RecurrenceRule.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/domain/schedule/OccurrenceGenerator.kt`
- Test: `app/src/test/kotlin/com/tiandili/calendar/domain/schedule/OccurrenceGeneratorTest.kt`

- [ ] Write a failing test proving a monthly series starting on January 31 occurs on February 28, March 31, and April 30.
- [ ] Run the single test and confirm failure is caused by missing recurrence types.
- [ ] Implement `RecurrenceFrequency`, `RecurrenceRule`, and date-only generation sufficient for monthly clamping.
- [ ] Run the single test and confirm it passes.
- [ ] Add failing tests for daily interval, selected weekdays, yearly February 29 clamping, until date, and count.
- [ ] Implement only the behavior driven by each test, rerunning the narrow test after every change.

## Task 2: Timed Occurrences And DST

**Files:**
- Create: `app/src/main/kotlin/com/tiandili/calendar/domain/schedule/Schedule.kt`
- Extend: `app/src/main/kotlin/com/tiandili/calendar/domain/schedule/OccurrenceGenerator.kt`
- Test: `app/src/test/kotlin/com/tiandili/calendar/domain/schedule/TimedOccurrenceGeneratorTest.kt`

- [ ] Write a failing test proving a nonexistent `02:30` local time in `America/New_York` advances to the next valid instant.
- [ ] Implement `ScheduleTiming.Timed` and zone resolution with `ZoneRules`.
- [ ] Write a failing test proving an ambiguous local time uses the earlier offset.
- [ ] Implement the earlier-offset rule and rerun all recurrence tests.

## Task 3: Schedule Validation

**Files:**
- Create: `app/src/main/kotlin/com/tiandili/calendar/domain/schedule/ScheduleValidator.kt`
- Test: `app/src/test/kotlin/com/tiandili/calendar/domain/schedule/ScheduleValidatorTest.kt`

- [ ] Prove blank and over-80-character titles are rejected.
- [ ] Prove timed end before start and all-day end on/before start are rejected.
- [ ] Prove notes over 1000 characters are rejected.
- [ ] Return field-specific validation errors without Android resource dependencies.

## Task 4: Room Schema And Repository

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/tiandili/calendar/data/local/TiandiliDatabase.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/data/schedule/ScheduleEntities.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/data/schedule/ScheduleDao.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/data/schedule/RoomScheduleRepository.kt`
- Test: `app/src/androidTest/kotlin/com/tiandili/calendar/data/schedule/ScheduleDaoTest.kt`

- [ ] Add pinned Room and KSP dependencies.
- [ ] Write a failing in-memory DAO test proving a series, rule, and pending reminder job are saved atomically.
- [ ] Implement the schema with explicit foreign keys and indexes.
- [ ] Write failing tests for update, soft delete, exceptions, and date-window queries.
- [ ] Export Room schema JSON and forbid destructive migration.

## Task 5: Schedule List And Editor

**Files:**
- Create: `app/src/main/kotlin/com/tiandili/calendar/feature/schedule/ScheduleListViewModel.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/feature/schedule/ScheduleListScreen.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/feature/schedule/ScheduleEditorViewModel.kt`
- Create: `app/src/main/kotlin/com/tiandili/calendar/feature/schedule/ScheduleEditorScreen.kt`
- Modify: `app/src/main/kotlin/com/tiandili/calendar/TiandiliApp.kt`
- Test: `app/src/androidTest/kotlin/com/tiandili/calendar/feature/schedule/ScheduleFlowTest.kt`

- [ ] Write a failing Compose test for empty state, add action, required title error, successful save, and immediate list display.
- [ ] Implement the minimum list and form controls for title, type, timing, recurrence, reminder, and note.
- [ ] Add failing tests for edit and delete confirmation.
- [ ] Connect date markers back to the calendar state.

## Verification

Run:

```bash
scripts/gradlew-local.sh testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Expected: recurrence, validation, DAO, and Compose flow tests pass; lint and debug assembly succeed.
