# Tiandili Foundation And Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development and dev-tdd to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Android 8.0+ application shell whose first working slice displays a Gregorian month grid, verified lunar summaries, and a selected-date almanac detail view.

**Architecture:** Use one Android application module with package boundaries for domain, data adapters, and Compose UI. The domain owns all calendar models and the `AlmanacEngine` contract; `LunarJavaAlmanacEngine` is the only type allowed to import lunar-java. Compose observes immutable UI state produced by `CalendarViewModel`.

**Tech Stack:** JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.0, compile/target SDK 36, min SDK 26, Jetpack Compose BOM 2025.10.01, Material 3, Navigation Compose, lunar-java 1.7.7, JUnit 4, Truth, kotlinx-coroutines-test.

---

## File Map

- `scripts/bootstrap-android-toolchain.sh`: downloads the pinned local JDK, Android command-line tools, SDK 36, Build Tools, and Gradle distribution.
- `settings.gradle.kts`: plugin and dependency repositories plus the `app` module.
- `build.gradle.kts`: root plugin declarations.
- `gradle/libs.versions.toml`: exact dependency versions and aliases.
- `gradle.properties`: AndroidX, Kotlin, and Gradle runtime settings.
- `app/build.gradle.kts`: Android application, Compose, and test configuration.
- `app/src/main/AndroidManifest.xml`: single activity, backup disabled, app metadata.
- `app/src/main/res/xml/backup_rules.xml`: Android 11 and lower backup exclusion.
- `app/src/main/res/xml/data_extraction_rules.xml`: Android 12+ cloud and device-transfer exclusions.
- `app/src/main/res/values/strings.xml`: application and accessibility strings.
- `app/src/main/kotlin/com/tiandili/calendar/domain/almanac/AlmanacModels.kt`: app-owned calendar and almanac value types.
- `app/src/main/kotlin/com/tiandili/calendar/domain/almanac/AlmanacEngine.kt`: calendar provider boundary.
- `app/src/main/kotlin/com/tiandili/calendar/data/almanac/LunarJavaAlmanacEngine.kt`: lunar-java mapping implementation.
- `app/src/main/kotlin/com/tiandili/calendar/feature/calendar/MonthGrid.kt`: pure Kotlin six-week month layout.
- `app/src/main/kotlin/com/tiandili/calendar/feature/calendar/CalendarUiState.kt`: immutable screen state.
- `app/src/main/kotlin/com/tiandili/calendar/feature/calendar/CalendarViewModel.kt`: month/date state transitions and background almanac loading.
- `app/src/main/kotlin/com/tiandili/calendar/feature/calendar/CalendarScreen.kt`: month grid and selected-day detail.
- `app/src/main/kotlin/com/tiandili/calendar/ui/theme/Color.kt`: jade, mineral blue, cinnabar, gold, and neutral colors.
- `app/src/main/kotlin/com/tiandili/calendar/ui/theme/Theme.kt`: light/dark Material 3 schemes.
- `app/src/main/kotlin/com/tiandili/calendar/ui/theme/Type.kt`: compact calendar typography.
- `app/src/main/kotlin/com/tiandili/calendar/navigation/TiandiliNavGraph.kt`: four top-level destinations, with unfinished destinations represented by honest empty states.
- `app/src/main/kotlin/com/tiandili/calendar/MainActivity.kt`: Compose host.
- `app/src/test/kotlin/com/tiandili/calendar/data/almanac/LunarJavaAlmanacEngineTest.kt`: fixed-date adapter contract.
- `app/src/test/kotlin/com/tiandili/calendar/feature/calendar/MonthGridTest.kt`: Monday/Sunday-first layout and range behavior.
- `app/src/test/kotlin/com/tiandili/calendar/feature/calendar/CalendarViewModelTest.kt`: selected-date and month transition behavior.
- `app/src/androidTest/kotlin/com/tiandili/calendar/feature/calendar/CalendarScreenTest.kt`: core semantics and date selection.

## Task 1: Reproducible Build Shell

- [x] **Step 1: Add the bootstrap script and pinned Gradle configuration**

The script must install only under `.toolchains/` and `.android-sdk/` in the workspace. It must not modify shell profiles or global package managers.

- [x] **Step 2: Run the bootstrap**

Run:

```bash
bash scripts/bootstrap-android-toolchain.sh
```

Expected: JDK 17, Gradle 8.13, SDK platform 36, platform-tools, and Build Tools 36.0.0 are present locally.

- [x] **Step 3: Generate and validate the Gradle wrapper**

Run:

```bash
JAVA_HOME="$PWD/.toolchains/jdk-17/Contents/Home" \
  "$PWD/.toolchains/gradle-8.13/bin/gradle" wrapper --gradle-version 8.13
```

Then run:

```bash
JAVA_HOME="$PWD/.toolchains/jdk-17/Contents/Home" \
ANDROID_HOME="$PWD/.android-sdk" \
  ./gradlew help
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Almanac Domain Contract

- [x] **Step 1: Write the failing fixed-date test**

Create `LunarJavaAlmanacEngineTest.kt` with this first behavior:

```kotlin
@Test
fun `maps 2025 spring festival to the first day of the first lunar month`() {
    val day = engine.day(LocalDate.of(2025, 1, 29), ZoneId.of("Asia/Shanghai"))

    assertThat(day.lunarMonthText).isEqualTo("正月")
    assertThat(day.lunarDayText).isEqualTo("初一")
    assertThat(day.zodiac).isEqualTo("蛇")
    assertThat(day.festivals).contains("春节")
}
```

- [x] **Step 2: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests "com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngineTest"
```

Expected: compilation failure because `LunarJavaAlmanacEngine` and app-owned models do not exist.

- [x] **Step 3: Add the smallest app-owned models and engine implementation**

The public contract is:

```kotlin
interface AlmanacEngine {
    fun day(date: LocalDate, zoneId: ZoneId): AlmanacDay
    fun month(yearMonth: YearMonth, zoneId: ZoneId): List<AlmanacDaySummary>
}
```

`AlmanacDay` must contain only mapped strings and Java time values; no lunar-java type may escape the data adapter package.

- [x] **Step 4: Verify GREEN**

Run the same narrow test.

Expected: `1 test completed, 1 passed`.

- [x] **Step 5: Add a month-summary test**

Assert that February 2024 returns 29 ordered summaries from `2024-02-01` through `2024-02-29`, and that `2024-02-10` maps to `初一`.

- [x] **Step 6: Verify RED then GREEN**

First run must fail because `month()` is not implemented. Implement only ordered per-day mapping, then rerun until the test passes.

## Task 3: Pure Month Grid

- [x] **Step 1: Write the failing Monday-first layout test**

```kotlin
@Test
fun `january 2025 Monday-first grid starts on December 30 and has 42 cells`() {
    val cells = buildMonthGrid(YearMonth.of(2025, 1), DayOfWeek.MONDAY)

    assertThat(cells).hasSize(42)
    assertThat(cells.first().date).isEqualTo(LocalDate.of(2024, 12, 30))
    assertThat(cells.last().date).isEqualTo(LocalDate.of(2025, 2, 9))
}
```

- [x] **Step 2: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests "com.tiandili.calendar.feature.calendar.MonthGridTest"
```

Expected: compilation failure because `buildMonthGrid` is missing.

- [x] **Step 3: Implement the minimal 42-cell grid**

Each `MonthCell` contains a date and `isInDisplayedMonth`. Reject displayed months outside January 1901 through December 2099.

- [x] **Step 4: Verify GREEN**

Run the same test class and expect all tests to pass.

- [ ] **Step 5: Add Sunday-first and boundary tests**

Prove that the first cell changes with the configured week start and that out-of-range months fail with a user-safe domain exception.

## Task 4: Calendar ViewModel

- [x] **Step 1: Write the failing initial-state test**

Given an injected `Clock` fixed at `2025-01-29T02:00:00Z` and `Asia/Shanghai`, assert:

```kotlin
assertThat(state.displayedMonth).isEqualTo(YearMonth.of(2025, 1))
assertThat(state.selectedDate).isEqualTo(LocalDate.of(2025, 1, 29))
assertThat(state.cells).hasSize(42)
```

- [x] **Step 2: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests "com.tiandili.calendar.feature.calendar.CalendarViewModelTest"
```

Expected: compilation failure because `CalendarViewModel` is missing.

- [x] **Step 3: Implement immutable state and transitions**

Support `selectDate`, `previousMonth`, `nextMonth`, and `returnToToday`. Keep weather and schedules out of this first state model.

- [x] **Step 4: Verify GREEN**

Run the same test class and expect all tests to pass.

- [x] **Step 5: Add transition tests**

Prove that selecting an adjacent-month cell changes both the selected date and displayed month, and that range boundaries disable further navigation.

## Task 5: Compose Application Slice

- [x] **Step 1: Write the failing UI semantics test**

Launch `CalendarScreen` with January 2025 state and assert nodes with text `天地历`, `2025 年 1 月`, `29`, `正月初一`, and `春节` are displayed.

- [x] **Step 2: Verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tiandili.calendar.feature.calendar.CalendarScreenTest
```

Expected: test compilation failure because `CalendarScreen` is missing. If no emulator is available, record that infrastructure blocker after confirming `compileDebugAndroidTestKotlin` fails for the missing production type.

- [x] **Step 3: Implement the theme, activity, navigation, and calendar screen**

The first screen must:

- show a compact app bar with month navigation and a return-to-today action;
- render a stable seven-column by six-row calendar;
- distinguish today, selected date, adjacent-month dates, and dates with labels using shape, border, text, or icons rather than color alone;
- show the selected date's lunar label, ganzhi, zodiac, festival/solar term, and available auspicious/inauspicious summaries;
- keep all text inside bounded cells at 200% font scale;
- provide four bottom destinations: 月历, 日程, 天气, 设置.

- [x] **Step 4: Compile and rerun the UI test**

Run:

```bash
./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`, with all JVM tests passing.

- [ ] **Step 5: Run on an API 36 emulator when available**

Install the debug APK, launch `com.tiandili.calendar/.MainActivity`, capture phone and tablet-width screenshots, and inspect for clipping, overlap, blank surfaces, and navigation failures.

## Task 6: Slice Verification

- [x] **Step 1: Run focused checks**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: all tasks succeed without lint errors.

- [x] **Step 2: Inspect the APK and manifest**

Verify:

- package is `com.tiandili.calendar`;
- min SDK is 26 and target SDK is 36;
- `android:allowBackup` is false;
- cloud backup and device transfer are excluded;
- no ad, analytics, account, or system-calendar permissions are present.

- [x] **Step 3: Record follow-up boundaries**

The slice is complete only when the debug APK launches to a usable month calendar and the fixed-date contract tests pass. Room schedules, reminders, weather, widgets, and import/export remain separate implementation plans and must not be represented as complete.
