# Android TV Code Review

## Goal

Review all of my Android TV implementation and evaluate whether it follows the project's existing standards, architecture, code conventions, TV UI/UX patterns, and Android best practices.

The review should focus not only on whether the app works, but also whether the implementation fits naturally into the existing codebase and provides a good experience with a D-pad/remote control on a large screen.

---

## Context

This is part of an **existing Android TV application**.

The project already has established:

- Code structure and package conventions
- Architectural pattern (e.g. MVVM, MVI, Clean Architecture)
- Naming conventions
- Reusable composables/views, presenters, adapters, and utilities
- Shared base classes (base Fragment/Activity/ViewModel, etc.)
- Dependency injection setup (e.g. Hilt, Koin, Dagger)
- Navigation approach (Navigation Component, Compose Navigation, custom router)
- UI toolkit (Leanback, Compose for TV, or Views with Material)
- Networking and data layer conventions (Retrofit, Ktor, Room, DataStore)
- Coroutines/Flow vs RxJava usage
- Error, loading, and empty-state patterns
- Focus-handling and D-pad navigation conventions
- Theming and design tokens (typography sized for 10-foot viewing, spacing, colors)
- Logging, analytics, and crash-reporting patterns
- Testing conventions

Before evaluating my implementation, inspect the existing codebase and understand how similar screens, features, and components are currently implemented.

**Prefer existing project conventions over introducing new patterns unless the existing approach has a clear problem.**

---

## What I Need

Perform a thorough code, architecture, and TV UX review of my changes.

Evaluate:

- Correctness
- Maintainability
- Architecture
- Consistency with the existing project
- Focus and D-pad navigation
- TV UI/UX consistency
- Reusability
- Lifecycle and memory safety
- Performance on low-end TV hardware
- Security
- Reliability
- Potential bugs
- Edge cases
- Testability

Do not review the code in isolation.

Compare my implementation with similar screens, components, and features already present in the project and identify deviations from established patterns.

---

## Review Criteria

### 1. Code Quality

Evaluate whether:

- The code is clean, readable, and maintainable
- Naming conventions are clear and consistent with the rest of the project
- Classes, composables, and functions have appropriate responsibilities
- Functions and composables are not unnecessarily large or complex
- Control flow is easy to understand
- Business logic is separated from UI logic
- Magic values or unexplained constants are avoided
- Hard-coded strings, dimensions, and colors are avoided where project resources exist
- Comments are useful and not compensating for unclear code
- Dead code, unused imports, unused resources, or unnecessary abstractions exist
- Nullable types are handled safely (no unnecessary `!!`)
- Kotlin idioms are used appropriately (or Java conventions, if the project is Java)

Identify code that could be simplified.

---

### 2. Project Structure & Architecture

Check whether the implementation follows the existing project architecture.

Review:

- Package and module placement
- Feature module boundaries (if multi-module)
- Separation of concerns (UI / ViewModel / UseCase / Repository / DataSource)
- Fragment/Activity/Composable responsibilities
- ViewModel responsibilities and state exposure
- Repository and data-source responsibilities
- Domain model vs DTO vs UI model boundaries
- Dependency direction (UI must not depend on data-layer internals)
- Dependency injection wiring and scopes
- Shared utility and resource placement
- Use of the project's base classes

Look for business logic placed in the wrong layer.

For example:

- Fragments or composables should not perform network or database calls directly
- ViewModels should not hold references to Views, Context, or Activities (unless the project explicitly allows AndroidViewModel usage)
- Repository logic should not be duplicated inside feature ViewModels
- Playback logic should not be scattered across multiple screens

---

### 3. Consistency With Existing Patterns

Compare the implementation with similar existing features.

Check whether:

- Similar screens are structured similarly
- Presenters, adapters, and row/grid setups follow existing conventions
- Data loading follows existing ViewModel/Flow/Coroutine patterns
- Navigation follows existing routing conventions
- Error handling and logging follow existing patterns
- Dependency injection follows existing module setup
- Theming and styling use existing theme tokens
- Resource naming (layouts, drawables, strings, dimens) follows project conventions
- Analytics and tracking events use existing helpers

Flag unnecessary introduction of new patterns, libraries, helpers, or abstractions when an existing solution already exists.

---

### 4. Reusability & Duplication

Check whether:

- Existing components, adapters, presenters, or utilities could have been reused
- Similar card, row, grid, or list logic is duplicated
- Existing repository methods or use cases already cover the need
- Common validation, formatting, or mapping logic is duplicated
- Constants, dimensions, or styles already exist in resources
- New abstractions are actually necessary

Identify duplicated logic that should be extracted or reused.

Also flag over-engineering where abstraction adds complexity without meaningful benefit.

---

### 5. TV Focus & D-pad Navigation

This is one of the most important areas for Android TV. Review carefully.

Check:

- All interactive elements are focusable and reachable with the D-pad
- Initial focus is set to the most logical element on screen entry
- Focus returns to the correct element after dialogs, back navigation, or data refresh
- Focus does not get lost when lists update or items are removed
- Focus order follows the visual layout (up/down/left/right behave predictably)
- No focus traps, unless intentional and with a clear exit
- `focusable`, `isFocusable`, `descendantFocusability`, `nextFocus*` attributes are used correctly
- Focus state is visually clear (highlight, scale, border, or elevation change)
- Focus restoration on process or configuration change where relevant
- Key events (`onKeyDown`, `onKeyUp`, `onPreviewKeyEvent`) are handled correctly and consumed only when appropriate
- Long-press and repeated key events behave as expected
- Remote media keys (play, pause, fast-forward, rewind) are handled where relevant
- Back button behavior is correct (exits, closes overlays, or navigates back predictably)
- Menu and options keys follow the project's existing behavior

Flag screens where a user could get stuck or where focus visibly jumps unexpectedly.

---

### 6. TV UI/UX Behavior

Evaluate the experience on a television, not just the code.

Check:

- Whether the primary action is obvious from across the room
- Whether visual hierarchy is clear at a distance
- Whether content rows and grids are predictable
- Whether feedback is provided after user actions
- Whether loading states prevent confusing interactions
- Whether empty states explain what the user can do next
- Whether errors are actionable and not technical
- Whether destructive actions require confirmation
- Whether a long press or accidental double press causes duplicate actions
- Whether the UI is usable without touch assumptions
- Whether pointer/touch-only interactions are avoided (Android TV does not guarantee touch)
- Whether text and icons are readable at 10-foot viewing distance
- Whether the screen does not depend on gestures or swipes

Identify confusing flows or unnecessary friction.

---

### 7. Display Adaptation & Overscan

Review behavior across TV screen configurations.

Check:

- Content respects safe margins (overscan / title-safe area)
- Layouts adapt to common resolutions (720p, 1080p, 4K)
- Density-independent units (`dp`, `sp`) are used rather than `px`
- Text sizes are appropriate for TV viewing distance
- Images are sized appropriately and do not waste memory
- Layouts handle long titles, long descriptions, and localized text without clipping
- Landscape-only orientation is respected where intended
- Aspect ratio handling for posters, thumbnails, and banners is consistent
- Behavior on different TV form factors (e.g. Android TV box vs. Google TV vs. Fire TV, if applicable) is considered where the project has known differences

Compare behavior with existing screens in the project.

---

### 8. Accessibility

Review accessibility for TV.

Check:

- `contentDescription` set for meaningful images and icon-only controls
- `contentDescription` null for decorative images
- Focus order and focus visibility work with TalkBack
- Text contrast meets guidelines against backgrounds and focus states
- Captions and subtitle support where media is involved
- Text scaling does not break layouts
- Reduced-motion or animation considerations where relevant
- Heading and semantic structure is preserved in Compose (`semantics`, `heading()`) or in Views (`accessibilityHeading`)
- Dialogs announce their purpose

Avoid unnecessary accessibility overrides when the default behavior is correct.

---

### 9. State Management & Lifecycle

Review state and lifecycle handling carefully.

Check:

- Whether state is actually necessary
- Whether derived values are stored unnecessarily
- Single source of truth for screen state
- Use of `StateFlow` / `SharedFlow` / `LiveData` consistent with the project
- Correct use of `collectAsStateWithLifecycle` or `repeatOnLifecycle` where applicable
- State survival across configuration changes and process death (`SavedStateHandle` where needed)
- Stale state after returning to the screen
- Coroutine scopes tied to the correct lifecycle (`viewModelScope`, `lifecycleScope`, `rememberCoroutineScope`)
- No work continuing after the screen is destroyed
- Listeners, callbacks, and receivers are unregistered
- Views, Contexts, or Activities are not leaked through static or long-lived references
- Adapter/ViewHolder references and `Glide`/`Coil` requests cleared when views are recycled
- Player instances released in the correct lifecycle callback

Flag complex effects that exist only to keep multiple pieces of state in sync.

---

### 10. Coroutines, Threading & Side Effects

Check:

- Long-running work runs off the main thread
- Dispatchers are chosen appropriately (`Dispatchers.IO`, `Default`, `Main`)
- Exceptions are handled in coroutines (`CoroutineExceptionHandler` or try/catch where appropriate)
- `CancellationException` is not swallowed
- Structured concurrency is respected (no `GlobalScope` usage without strong reason)
- Race conditions between multiple requests are handled (e.g. `collectLatest`, `flatMapLatest`, job cancellation)
- Debounce or throttle is used for search input or rapid key events where needed
- Retry logic is bounded and safe to repeat
- Compose effects (`LaunchedEffect`, `DisposableEffect`) have correct keys and cleanup

---

### 11. Data Fetching & Networking

Review all network and data-source integration.

Check:

- Use of the existing API client and interceptors
- Request construction and parameters
- Pagination and paging library usage (Paging 3 or custom)
- Caching strategy and cache invalidation
- Offline behavior and fallback data
- Timeout configuration appropriate for TV network conditions
- Error mapping to user-facing states
- Response parsing and null-safety of DTOs
- Handling of unexpected or partial API responses
- Duplicate requests on focus change, scroll, or screen re-entry
- Image loading pipeline (Glide / Coil / Picasso) uses project configuration and caching
- Sensitive data not logged from network interceptors

Verify that API usage matches similar features already in the project.

---

### 12. Media Playback (if applicable)

If the app plays video, audio, or streams, review:

- Player setup and reuse (ExoPlayer / Media3 / other)
- Player release on pause, stop, and destroy
- Player state restoration (position, track selection) after pause or background
- Audio focus handling
- Background playback and notification behavior
- Buffering, error, and retry states
- DRM handling and license request security
- Subtitle and audio track selection UX with the remote
- Playback continuity during network changes
- Playback controls reachable with D-pad and media keys
- Resource usage on low-memory TV devices (buffer size, codec instances)

---

### 13. Loading, Error & Empty States

Check whether all meaningful states are handled.

Review:

- Initial loading
- Background refresh
- Skeletons, shimmers, or progress indicators consistent with the project
- Empty results
- No-network state
- Permission-denied or login-required state
- Not-found state
- Recoverable errors with a retry action
- Fatal errors
- Partial failures

Ensure the UI does not:

- Show stale data as current without indication
- Flash incorrect empty states before loading completes
- Allow repeated actions while a request is in progress
- Fail silently
- Display raw exception messages to users

Compare with existing project state patterns.

---

### 14. Navigation & Routing

Review navigation behavior.

Check:

- Route definitions and naming
- Arguments and their types (safe args / typed routes)
- Back stack behavior (`popUpTo`, `launchSingleTop`, task affinity)
- Deep link handling and validation
- Behavior when the app is launched from the Android TV home screen channels or recommendations
- Behavior when launched from an Intent with invalid or missing extras
- Restoration of selected item and scroll position after returning
- Navigation after create, update, or delete actions
- Navigation that bypasses the project's routing abstraction

---

### 15. Authentication & Authorization

Review access control.

Check:

- Login state checks before protected screens
- Token storage location (EncryptedSharedPreferences, Keystore-backed storage, or project standard)
- Token refresh flow and handling of expired sessions
- Sign-out clears sensitive data and cached content
- Profile or account switching behavior, if applicable
- Parental controls or content restrictions, if applicable
- Permission-denied handling

**Client-side checks are not a security boundary.** Protected backend operations must still be enforced server-side. Flag cases where the UI implies stronger security than actually exists.

---

### 16. Android Security

Look for security risks including:

- Exported components without proper permissions (`android:exported`)
- Unprotected `BroadcastReceiver`, `ContentProvider`, or `Service` components
- Intent injection or unvalidated Intent extras
- Deep link parameters used without validation
- `WebView` with JavaScript enabled and untrusted content, or `addJavascriptInterface` misuse
- Cleartext traffic allowed (`usesCleartextTraffic`, network security config)
- Certificate pinning or TLS configuration issues, where the project requires them
- Secrets, API keys, or tokens hard-coded in source, `BuildConfig`, or resources
- Sensitive data written to logs (`Log.d`, Timber, network logs)
- Sensitive data stored in plain SharedPreferences or external storage
- Backup configuration exposing sensitive data (`allowBackup`, `fullBackupContent`)
- Debuggable flags or debug-only code paths in release builds
- Insecure use of `PendingIntent` (mutability flags)
- Untrusted URLs opened without validation
- Third-party SDK permissions beyond what the feature needs

Prioritize security findings clearly.

---

### 17. Performance on TV Hardware

Android TV devices often have limited RAM, slower CPUs, and weaker GPUs than phones. Review:

- Main thread work during scrolling, focus changes, and screen transitions
- Overdraw from stacked backgrounds, shadows, or blur effects
- Large or unscaled bitmaps loaded into memory
- Image loading size constraints relative to view size
- RecyclerView / Leanback row configuration (prefetch, item count, view recycling)
- Compose recomposition triggered by unstable parameters or unnecessary state reads
- Lazy list keys and content types
- Large composable trees or views not needed on screen
- Expensive operations during `onDraw`, `onLayout`, or composition
- Startup cost (Application `onCreate`, initialization of SDKs)
- Repeated network calls on focus change
- Memory leaks that cause gradual slowdown
- Unnecessary animations running on low-end devices

Do not suggest optimization without a reasonable justification or evidence from the codebase.

---

### 18. Android Manifest, Resources & Platform Requirements

Check configuration correctness for Android TV.

Review:

- `<uses-feature android:name="android.software.leanback" android:required="true|false">` is set correctly
- `<uses-feature android:name="android.hardware.touchscreen" android:required="false">` is set so the app is not filtered out on TV
- `LEANBACK_LAUNCHER` category present on the launcher activity
- TV banner (`android:banner`) provided and sized correctly
- `android:screenOrientation` and `configChanges` set intentionally
- Permissions requested are necessary and documented
- Unused permissions and features removed
- `minSdkVersion` / `targetSdkVersion` consistent with project and Play Store requirements
- Resource qualifiers (`-tv`, `-large`, `-land`) follow project conventions
- Drawables and icons use the expected TV-specific sizes
- Strings are externalized and localized

---

### 19. Build Configuration & Dependencies

Review Gradle and dependency changes.

Check:

- Dependency versions match the project's version catalog or existing pins
- No new dependency added when an existing library already solves the problem
- Dependency scope is correct (`implementation` vs `api` vs `debugImplementation`)
- Unused dependencies removed
- ProGuard / R8 rules added where needed and not overly broad
- Build variants and flavors used consistently
- Secrets not committed to Gradle files or `local.properties` tracked in git
- Library licenses compatible with the project

Do not introduce a new library when the project already has an established solution unless there is a strong reason.

---

### 20. Rendering & UI Correctness

Actively inspect rendering behavior.

Check:

- Conditional rendering and visibility (`View.GONE` vs `INVISIBLE`, `if` in Compose)
- List item keys and stable IDs
- Empty lists and null values
- Zero values hidden accidentally by truthy/falsy checks
- Incorrect fallback images or text
- Loading-to-loaded transitions
- Error-to-retry transitions
- Items reused by RecyclerView or Leanback presenters with stale state
- Focus state not carried over to the wrong item after recycling
- Configuration changes (rotation, density, dark mode) handled correctly
- Dialog and popup behavior on dismissal and focus return

Look for visual bugs that only appear in specific states.

---

### 21. Type Safety & Null Safety

Review:

- Incorrect or overly broad types
- Unsafe casts (`as` without checks, Java raw types)
- Non-null assertions (`!!`) without justification
- Platform types (Java interop) not checked at boundaries
- Nullable API responses and DTO fields handled properly
- Sealed classes or enums used to model states where appropriate
- Duplicated model definitions across layers
- Models that allow invalid states

Prefer existing project typing conventions.

---

### 22. Internationalization & Formatting

If the project supports localization, review:

- Hard-coded user-facing strings
- Use of string resources and plurals
- Date, time, number, and duration formatting
- Runtime locale and language changes handled correctly
- Text expansion for long translations
- RTL layout compatibility where relevant

Use existing formatting utilities.

---

### 23. Edge Cases & Potential Bugs

Actively search for edge cases instead of reviewing only the happy path.

Consider:

- Null or missing values
- Empty lists and empty strings
- Very long titles and descriptions
- Rapid D-pad key presses
- Holding a D-pad key down for a long time
- Duplicate clicks or selections
- Slow or failed network requests
- Out-of-order responses
- Navigation during an in-flight request
- Screen rotation or configuration change during loading
- App backgrounded during playback or loading
- Process death and restoration
- Session expiration while browsing
- Deleted or stale content
- Unexpected API enum values
- Device without network connectivity
- Screensaver or sleep timeout during playback
- Low-memory situations
- Device language or region changes

Point out concrete failure scenarios where possible.

---

### 24. Maintainability

Consider how easy the implementation will be to modify later.

Check:

- Coupling between screens, ViewModels, and repositories
- Hidden dependencies and hard-to-test static access
- Excessive branching or `when` blocks
- Complex composables or fragments
- Repeated business or UI rules
- Hard-coded behavior that should be configuration or remote-driven
- Unclear data flow
- Duplicate state
- Premature generic components
- Over-engineering

Suggest refactoring only when it provides a clear maintainability benefit.

---

### 25. Tests & Testability

Review whether the implementation is easy to test and whether important scenarios require coverage.

Identify missing or important test cases such as:

- ViewModel state transitions (loading, success, error, empty)
- Repository behavior with mocked data sources
- Mapping from DTO to UI model
- Focus behavior and key event handling (instrumented or Compose UI tests)
- Navigation and back-stack behavior
- Deep link parsing and validation
- Playback state restoration
- Authorization and expired-session handling
- Network error and retry behavior
- Configuration change and process death restoration
- Accessibility labels for key elements

However, **do not run the test suite, builds, or emulator/device tests**.

Review existing test files and test patterns statically if useful.

---

## Severity Levels

Classify findings using the following severity levels:

### Critical

Issues that may cause:

- Security vulnerabilities (exported components, token leaks, cleartext traffic with sensitive data)
- Sensitive-data exposure
- Broken authentication assumptions
- Crashes on launch or in a core flow
- Completely broken playback or primary user flow
- Play Store rejection or TV device filtering errors (wrong `uses-feature` configuration)

These should block merging.

### High

Issues likely to cause:

- Focus traps or lost focus on core screens
- Incorrect user-visible behavior
- Memory leaks that degrade performance over a session
- Broken back-stack or navigation in important flows
- Significant race conditions
- Data loss or inconsistent state
- Serious performance problems on TV hardware
- Player resource leaks

These should normally be fixed before merging.

### Medium

Issues involving:

- Maintainability and architecture inconsistencies
- Design-system or theme violations
- Missing edge-case handling
- Moderate accessibility problems
- Moderate performance concerns
- Significant duplication
- Inconsistent TV UX patterns
- Overscan or text-size issues on common resolutions

These should be addressed when practical.

### Low

Issues involving:

- Minor cleanup
- Naming
- Readability
- Small consistency improvements
- Minor visual inconsistencies

These should not block merging unless they violate an important project convention.

---

## Review Approach

Before reporting an issue:

1. Inspect relevant existing implementations.
2. Determine what convention the project currently follows.
3. Compare my implementation against that convention.
4. Verify whether an existing component, presenter, utility, style, or pattern already solves the same problem.
5. Explain why the difference matters.
6. Provide a concrete recommendation.

Avoid generic recommendations that are not grounded in this codebase.

For every important issue, reference the relevant:

- File
- Class / composable / fragment / ViewModel / function
- Manifest entry, Gradle file, or resource when relevant
- Code section or line when possible
- Existing similar implementation when comparison is useful

Explain the actual impact rather than only stating a best-practice rule.

For TV UX issues, explain:

- What focus, navigation, or visual behavior is inconsistent
- What existing project pattern should be followed
- How the issue affects a user sitting on the couch with a remote

---

## Output Format

### 1. Summary

Provide an overall assessment:

**Overall Quality:** `Good` / `Needs Improvement` / `Not Aligned`

Include a short explanation of:

- General implementation quality
- Alignment with project architecture and conventions
- Focus and D-pad navigation quality
- Main UX risks
- Main technical risks (crashes, leaks, playback, performance)
- Whether there are merge-blocking issues

---

### 2. Key Issues

List the most important findings first.

Use this format:

```text
[Severity] Short issue title

Location:
<file / class / composable / function / manifest entry>

Problem:
<what is wrong>

Why it matters:
<actual impact on correctness, TV UX, focus, memory, performance, security, etc.>

Existing project pattern:
<similar implementation/component/pattern to follow, when applicable>

Recommendation:
<specific improvement>
```

Prioritize real correctness, security, focus navigation, lifecycle, and architecture issues over minor style preferences.

---

### 3. Detailed Feedback

Group detailed findings by category:

- Code Quality
- Architecture / Project Structure
- Consistency
- Reusability
- TV Focus & Navigation
- TV UI/UX
- Display Adaptation & Overscan
- Accessibility
- State Management & Lifecycle
- Coroutines & Threading
- Data Fetching & Networking
- Media Playback
- Loading / Error / Empty States
- Navigation & Routing
- Authentication / Authorization
- Android Security
- Performance
- Manifest & Platform Requirements
- Build & Dependencies
- Rendering Correctness
- Type & Null Safety
- Internationalization
- Edge Cases
- Maintainability
- Tests / Testability

Skip categories where there are no meaningful findings.

---

### 4. Positive Findings

Also mention things that are implemented well, especially:

- Good reuse of existing components and presenters
- Correct architecture and layer separation
- Good focus handling and predictable D-pad navigation
- Proper lifecycle and resource cleanup
- Good error and loading handling
- Correct manifest and TV feature configuration
- Clear code organization

Do not invent positive feedback just to fill this section.

---

### 5. Actionable Suggestions

Provide a prioritized checklist.

Example:

```text
Must Fix
- [ ] Fix focus loss after returning from the details screen in ...
- [ ] Release ExoPlayer in onStop in ...
- [ ] Set android:exported="false" on ... unless it must be public

Should Fix
- [ ] Reuse existing <PresenterName> instead of duplicating ...
- [ ] Align loading/error state with ...
- [ ] Move network call from ... into the repository

Nice to Have
- [ ] Extract repeated row setup into ...
- [ ] Rename ...
- [ ] Simplify ...
```

---

## Important Review Rules

- Do not modify code unless I explicitly ask you to.
- Do not assume new code is correct simply because it compiles.
- Do not focus only on formatting or style.
- Prioritize correctness, focus navigation, lifecycle safety, security, architecture, and maintainability.
- Verify existing project conventions before recommending a different approach.
- Verify whether an existing component, presenter, utility, or pattern already solves the problem before suggesting a new abstraction.
- Avoid suggesting unnecessary abstractions or over-engineering.
- Do not introduce a new library when the project already has an established solution unless there is a strong reason.
- Distinguish actual bugs from optional improvements.
- Distinguish design-system violations from subjective visual preferences.
- Explain the impact of each significant issue.
- Prefer concrete, project-specific feedback over generic Android advice.
- Do not recommend memoization or performance optimization without evidence that it is useful.
- Do not treat client-side checks as a security boundary.
- Do not recommend replacing an existing project convention solely because another pattern is more popular.
- If something cannot be determined confidently from the codebase, state that explicitly instead of assuming.

---

## Input

```text
@app/
```

Replace `@app/` with the relevant module, package, feature, branch, diff, or files to review.

---

## Notes

**Do not run the test suite.**

In particular, do not run commands such as:

```bash
./gradlew test
./gradlew connectedAndroidTest
./gradlew connectedCheck
./gradlew lint
./gradlew assembleRelease
./gradlew build
```

Also do not run heavy build, lint, benchmark, emulator, or device commands that could cause high CPU or memory usage on my machine unless I explicitly request them.

Static inspection of source code, manifests, Gradle files, resources, existing tests, and project structure is allowed.

If verification would normally require running tests, builds, emulators, or a physical TV device, mention what should be verified instead of executing those commands.
</document_content></document>

<document index="1" media_type="text/plain"><document_content># Frontend Code Review

## Goal

Review all of my frontend implementation and evaluate whether it follows the project's existing standards, architecture, code conventions, design system, and best practices.

The review should focus not only on whether the implementation works, but also whether it fits naturally into the existing codebase and user experience.

---

## Context

This is part of an **existing frontend application**.

The project already has established:

- Code structure and folder conventions
- Architectural patterns
- Naming conventions
- Reusable components
- Shared hooks, helpers, utilities, and abstractions
- UI/UX design system
- Typography, spacing, colors, and layout conventions
- Form and validation patterns
- State-management patterns
- API/data-fetching patterns
- Routing/navigation conventions
- Error/loading/empty-state conventions
- Authentication and authorization patterns
- Accessibility conventions
- Responsive design patterns
- Testing conventions

Before evaluating my implementation, inspect the existing codebase and understand how similar pages, components, flows, and features are currently implemented.

**Prefer existing project conventions over introducing new patterns unless the existing approach has a clear problem.**

---

## What I Need

Perform a thorough code, architecture, and UI/UX review of my changes.

Evaluate:

- Correctness
- Maintainability
- Component architecture
- Consistency with the existing project
- Reusability
- UI/UX consistency
- Accessibility
- Responsiveness
- State and data flow
- Security
- Performance
- Reliability
- Potential bugs
- Edge cases
- Testability

Do not review the code in isolation.

Compare my implementation with similar pages/components/features already present in the project and identify deviations from established patterns.

---

## Review Criteria

### 1. Code Quality

Evaluate whether:

- The code is clean, readable, and maintainable
- Naming conventions are clear and consistent
- Components, hooks, and functions have appropriate responsibilities
- Functions and components are not unnecessarily large or complex
- Control flow is easy to understand
- Conditional rendering is clear
- Business/UI logic is structured properly
- Magic values or unexplained constants are avoided
- Comments are useful and not compensating for unclear code
- Dead code, unused imports, or unnecessary abstractions exist
- Implementation details are unnecessarily repeated
- Complex expressions should be extracted for readability

Identify code that could be simplified.

---

### 2. Project Structure & Frontend Architecture

Check whether the implementation follows the existing project architecture.

Review:

- Folder and module placement
- Page/screen placement
- Component placement
- Feature/module boundaries
- Separation of concerns
- Container/presentational responsibilities, where applicable
- Hook responsibilities
- Service/API-layer responsibilities
- State-management boundaries
- Shared utility placement
- Shared type placement
- Route organization
- Dependency direction

Look for logic placed in the wrong layer.

For example:

- Pages should not contain large amounts of reusable UI logic if the project normally extracts components
- Reusable components should not contain feature-specific business logic
- API calls should follow the project's existing data-access pattern
- Shared logic should not be buried inside one page when it is already reusable elsewhere
- Components should not become unnecessarily coupled to unrelated application concerns

---

### 3. Consistency With Existing Patterns

Compare the implementation with similar existing features.

Check whether:

- Similar pages are structured similarly
- Similar components use the same patterns
- Forms follow existing conventions
- Tables/lists/cards/modals follow existing implementations
- Data fetching follows existing project patterns
- Loading states follow existing conventions
- Error states follow existing conventions
- Empty states follow existing conventions
- Navigation follows existing conventions
- Authentication/authorization checks are implemented consistently
- Toasts/notifications/feedback use existing utilities
- Icons come from the existing icon system
- Dates, numbers, currency, and localization follow project conventions

Flag unnecessary introduction of new patterns, libraries, helpers, hooks, or abstractions when an existing solution already exists.

---

### 4. Component Design & Reusability

Review component boundaries carefully.

Check whether:

- Existing components could have been reused
- Similar UI already exists elsewhere
- Components have a clear single responsibility
- Components are overly large
- Components are overly fragmented
- Props are clear and minimal
- Prop drilling is excessive
- Component APIs are consistent with similar existing components
- Generic components are actually reusable
- Feature-specific components are not prematurely generalized
- Rendering logic is duplicated
- Shared layout patterns are duplicated
- Existing hooks/utilities are being reimplemented

Identify duplicated logic or UI that should be extracted or reused.

Also flag over-engineering where abstraction adds complexity without meaningful benefit.

---

### 5. Design System & Visual Consistency

Verify that the implementation follows the project's existing design system.

Review:

- Colors
- Typography
- Font sizes
- Font weights
- Line heights
- Spacing
- Margins
- Padding
- Border radius
- Borders
- Shadows
- Icons
- Button variants
- Inputs
- Selects
- Checkboxes
- Radio buttons
- Modals/dialogs
- Cards
- Tables
- Tooltips
- Badges
- Tabs
- Alerts
- Empty states
- Loading states

Check whether:

- Existing design tokens are used instead of hard-coded values
- Existing UI primitives/components are reused
- New styling is visually consistent with the rest of the application
- Similar states look and behave consistently
- Custom styles duplicate functionality already provided by the design system

Flag visual implementations that work technically but do not fit the existing product.

---

### 6. UI/UX Behavior

Evaluate the user experience, not just the code.

Check:

- Whether the primary user action is clear
- Whether visual hierarchy is appropriate
- Whether controls are placed where users would expect them
- Whether interactions are predictable
- Whether feedback is provided after user actions
- Whether destructive actions require appropriate confirmation
- Whether disabled states are understandable
- Whether loading states prevent confusing interactions
- Whether empty states explain what users can do next
- Whether errors are actionable
- Whether success feedback is appropriate
- Whether forms preserve or reset data correctly
- Whether accidental duplicate submissions are prevented
- Whether navigation after actions is intuitive
- Whether modals/drawers behave consistently
- Whether keyboard/mouse/touch interactions feel correct

Identify confusing flows or unnecessary friction.

---

### 7. Responsive Design

Review behavior across screen sizes.

Check:

- Mobile layout
- Tablet layout
- Desktop layout
- Very narrow screens
- Very wide screens
- Long text
- Large tables
- Overflow
- Horizontal scrolling
- Fixed/sticky elements
- Modal/dialog sizing
- Drawer behavior
- Form layout
- Navigation behavior
- Touch target sizes

Look for:

- Hard-coded dimensions that break responsiveness
- Content clipping
- Overlapping elements
- Layout shifts
- Hidden actions on small screens
- Inappropriate breakpoint usage
- Different breakpoints than the rest of the project without justification

Compare responsive behavior with similar existing screens.

---

### 8. Accessibility

Review accessibility carefully.

Check:

- Semantic HTML
- Heading hierarchy
- Labels for inputs
- Accessible names for icon-only controls
- Keyboard navigation
- Focus states
- Focus order
- Focus trapping in dialogs
- Escape behavior for dialogs/menus where appropriate
- ARIA usage
- Screen-reader behavior
- Color contrast
- Disabled-state semantics
- Error-message association with fields
- Required-field indication
- Form instructions
- Table semantics
- Button vs link semantics
- Alternative text for meaningful images
- Decorative image handling
- Reduced-motion considerations where relevant

Avoid unnecessary ARIA when native HTML already provides correct semantics.

Flag interactions that work with a mouse but are inaccessible by keyboard or assistive technology.

---

### 9. Forms & Validation

For forms, review:

- Field naming
- Initial values
- Controlled/uncontrolled patterns
- Validation location
- Client-side validation
- Server-side validation handling
- Validation schemas
- Required vs optional fields
- Error display
- Field-level errors
- Form-level errors
- Submission state
- Double submission prevention
- Reset behavior
- Dirty-state handling
- Unsaved-change handling
- Data normalization
- Trimming
- Numeric parsing
- Date parsing
- File validation
- Invalid enum values
- Empty string handling
- Null/undefined handling

Check whether form behavior matches similar forms in the project.

Do not rely on frontend validation as a security boundary.

---

### 10. State Management

Review state usage carefully.

Check:

- Whether state is actually necessary
- Whether derived values are stored unnecessarily
- Local vs global state decisions
- Server state vs client state
- State synchronization
- Duplicate sources of truth
- Stale state
- Initialization
- Reset behavior
- State persistence
- URL state where appropriate
- Shared state boundaries
- Context usage
- Store usage
- Reducer usage

Look for state that could be derived instead of synchronized manually.

Flag complex effects that exist only to keep multiple pieces of state in sync.

---

### 11. Hooks & Side Effects

If the project uses React or a similar hook-based framework, review:

- Hook dependency arrays
- Missing dependencies
- Unnecessary dependencies
- Infinite-loop risks
- Stale closures
- Cleanup functions
- Event listener cleanup
- Timer cleanup
- Subscription cleanup
- Abort/cancellation handling
- Effects that should be event handlers instead
- Effects doing too many unrelated things
- Custom-hook responsibilities
- Hook naming and reuse

Check whether side effects are deterministic and whether they can run multiple times safely.

---

### 12. Data Fetching & API Integration

Review all frontend/backend interactions.

Check:

- Existing API-client usage
- Query/mutation conventions
- Request construction
- Query parameters
- Pagination
- Filtering
- Sorting
- Cache usage
- Cache invalidation
- Refetch behavior
- Loading states
- Error states
- Retry behavior
- Request cancellation
- Race conditions
- Stale responses
- Optimistic updates
- Rollback behavior
- Duplicate requests
- Response normalization
- Unexpected API data
- Partial API responses

Verify that API usage matches similar features already in the project.

Flag direct API usage when the project has an established service/client abstraction.

---

### 13. Loading, Error & Empty States

Check whether all meaningful states are handled.

Review:

- Initial loading
- Background loading
- Mutation loading
- Skeletons/spinners
- Empty results
- No-search-results state
- Permission-denied state
- Not-found state
- Recoverable errors
- Fatal errors
- Partial failures
- Retry behavior
- Offline/network failures where relevant

Ensure the UI does not:

- Show stale data as current
- Flash incorrect empty states before loading completes
- Leave buttons interactive during unsafe mutations
- Fail silently
- Display raw technical errors to users

Compare with existing project state patterns.

---

### 14. Routing & Navigation

Review routing behavior.

Check:

- Route definitions
- Route naming
- Nested routes
- Parameters
- Query parameters
- Route guards
- Authentication requirements
- Authorization requirements
- Redirect behavior
- Back-button behavior
- Deep linking
- Refresh behavior
- Invalid route parameters
- Preservation of filters/search state
- Navigation after create/update/delete actions

Check for broken links and navigation that bypasses existing routing abstractions.

---

### 15. Authentication & Authorization

Review frontend access control carefully.

Check:

- Route protection
- Component/action visibility
- Permission checks
- Role checks
- Feature flags
- Resource-level access assumptions
- Tenant/account boundaries
- Unauthorized-state handling
- Session-expiration behavior

Remember:

**Frontend authorization is not a security boundary.**

Frontend permission checks should improve UX, but protected backend operations must still be secured server-side.

Flag cases where the UI implies stronger security than actually exists.

---

### 16. Frontend Security

Look for security risks including:

- Cross-site scripting (XSS)
- Unsafe HTML rendering
- `dangerouslySetInnerHTML` or framework equivalents
- Untrusted URL rendering
- Unsafe redirects
- Open redirects
- URL injection
- DOM injection
- Sensitive information stored in insecure browser storage
- Secrets embedded in frontend code
- Tokens exposed unnecessarily
- Sensitive data in logs
- Sensitive data in analytics events
- Insecure iframe usage
- Missing `rel="noopener noreferrer"` where relevant
- Unsafe file handling
- Unvalidated external links
- Client-side trust of user-controlled values

Check whether untrusted data reaches HTML, URLs, scripts, or privileged actions without appropriate handling.

Prioritize security findings clearly.

---

### 17. Performance

Identify meaningful performance problems.

Review:

- Unnecessary re-renders
- Expensive computations during render
- Repeated calculations
- Large component trees
- Large bundle additions
- Unnecessary dependencies
- Poor memoization decisions
- Excessive memoization
- Large list rendering
- Missing virtualization where justified
- Unoptimized images
- Layout shifts
- Repeated network requests
- Waterfall requests
- Blocking sequential requests
- Unnecessary refetching
- Heavy synchronous work
- Expensive event handlers
- Debounce/throttle needs
- Lazy loading
- Code splitting

For React-like frameworks, also consider:

- Unstable object/function props
- Incorrect keys
- Index keys where ordering can change
- Expensive context updates
- Incorrect memoization dependencies

Do not suggest optimization without a reasonable justification.

---

### 18. Rendering Correctness

Actively inspect rendering behavior.

Check:

- Conditional rendering
- List keys
- Empty arrays
- Null values
- Undefined values
- Zero values accidentally hidden by truthy/falsy checks
- Incorrect fallback rendering
- Loading-to-loaded transitions
- Error-to-retry transitions
- Hydration issues if SSR is used
- Server/client rendering differences
- Portals
- Modal mounting/unmounting
- Component remounting caused by unstable keys

Look for visual bugs that only appear in specific states.

---

### 19. Type Safety

If the project uses TypeScript or another typed language, review:

- Incorrect types
- Overly broad types
- Excessive use of `any`
- Unsafe assertions
- Unsafe casts
- Non-null assertions
- Missing nullability handling
- Incorrect API response typing
- Duplicated type definitions
- Props that allow invalid states
- Union types that could better represent states
- Optional fields that should be required
- Required fields that should be optional
- Event handler typing
- Generic component typing

Prefer existing project typing conventions.

Do not create duplicate frontend models when shared/generated API types already exist unless there is a clear reason.

---

### 20. Styling Implementation

Review styling approach.

Check:

- Whether the project styling system is followed
- CSS module / Tailwind / styled-component / CSS-in-JS conventions
- Design token usage
- Repeated style definitions
- Specificity problems
- Global style leakage
- Inline styles
- Hard-coded pixel values
- Hard-coded colors
- `!important`
- Unnecessary custom CSS
- Responsive utility consistency
- Conditional class complexity

Flag styling that bypasses the project's design system without justification.

---

### 21. Internationalization & Formatting

If the project supports localization, review:

- Hard-coded user-facing strings
- Translation-key usage
- Pluralization
- Date formatting
- Time formatting
- Number formatting
- Currency formatting
- Time-zone assumptions
- Locale-aware sorting
- Text expansion
- RTL compatibility where relevant

Use existing formatting and localization utilities.

---

### 22. Edge Cases & Potential Bugs

Actively search for edge cases instead of reviewing only the happy path.

Consider:

- Null or missing values
- Empty arrays
- Empty strings
- Very long strings
- Large numbers
- Zero values
- Negative values
- Duplicate items
- Duplicate clicks
- Slow network
- Failed requests
- Out-of-order responses
- Navigation during requests
- Session expiration
- Permission changes
- Stale data
- Deleted resources
- Partial API data
- Unexpected enum values
- Rapid user interaction
- Component unmounting during async operations
- Browser refresh
- Deep linking
- Mobile viewport issues

Point out concrete failure scenarios where possible.

---

### 23. Browser & Device Compatibility

Where relevant, check:

- Browser APIs used
- Feature support
- Polyfill assumptions
- Mobile browser behavior
- Safari-specific issues
- Touch interactions
- Hover-only interactions
- Clipboard APIs
- File APIs
- Date parsing behavior
- Storage APIs
- Viewport units
- Sticky positioning
- Autofill behavior

Do not speculate broadly; focus on APIs and CSS patterns actually used by the implementation.

---

### 24. Maintainability

Consider how easy the implementation will be to modify later.

Check:

- Coupling between components
- Hidden dependencies
- Excessive branching
- Complex components
- Complex hooks
- Repeated business/UI rules
- Hard-coded behavior
- Unclear data flow
- Duplicate state
- Poor abstraction boundaries
- Unnecessary abstraction
- Premature generic components
- Over-engineering

Suggest refactoring only when it provides a clear maintainability benefit.

---

### 25. Tests & Testability

Review whether the implementation is easy to test and whether important scenarios appear to require test coverage.

Identify missing or important test cases such as:

- Basic rendering
- Happy path
- Loading state
- Error state
- Empty state
- Validation failures
- Authorization/permission behavior
- User interactions
- Form submission
- Double submission
- API failure
- Retry behavior
- Boundary values
- Responsive behavior where relevant
- Keyboard interaction
- Accessibility behavior
- Routing/navigation
- Stale/late API responses

However, **do not run the test suite**.

Review existing test files and test patterns statically if useful.

---

## Severity Levels

Classify findings using the following severity levels:

### Critical

Issues that may cause:

- Serious security vulnerabilities
- Sensitive-data exposure
- Broken authentication/authorization assumptions
- Major production failures
- Completely broken core user flows

These should block merging.

### High

Issues likely to cause:

- Incorrect user-visible behavior
- Broken important workflows
- Data loss caused by frontend behavior
- Serious accessibility failures on core flows
- Significant race conditions
- Major responsiveness/usability problems
- Severe performance problems

These should normally be fixed before merging.

### Medium

Issues involving:

- Maintainability
- Architecture inconsistencies
- Design-system violations
- Missing edge-case handling
- Moderate accessibility problems
- Moderate performance concerns
- Significant duplication
- Inconsistent UX

These should be addressed when practical.

### Low

Issues involving:

- Minor cleanup
- Naming
- Readability
- Small consistency improvements
- Minor visual inconsistencies

These should not block merging unless they violate an important project convention.

---

## Review Approach

Before reporting an issue:

1. Inspect relevant existing implementations.
2. Determine what convention the project currently follows.
3. Compare my implementation against that convention.
4. Verify whether an existing component, hook, utility, style, or pattern already solves the same problem.
5. Explain why the difference matters.
6. Provide a concrete recommendation.

Avoid generic recommendations that are not grounded in this codebase.

For every important issue, reference the relevant:

- File
- Component/hook/function/module
- Code section or line when possible
- Existing similar implementation when comparison is useful

Explain the actual impact rather than only stating a best-practice rule.

For UI/UX issues, explain:

- What behavior or visual treatment is inconsistent
- What existing project pattern should be followed
- How the inconsistency affects users

---

## Output Format

### 1. Summary

Provide an overall assessment:

**Overall Quality:** `Good` / `Needs Improvement` / `Not Aligned`

Include a short explanation of:

- General implementation quality
- Alignment with project conventions
- Alignment with the design system
- Main UX/accessibility risks
- Main technical risks
- Whether there are merge-blocking issues

---

### 2. Key Issues

List the most important findings first.

Use this format:

```text
[Severity] Short issue title

Location:
<file / component / hook / function>

Problem:
<what is wrong>

Why it matters:
<actual impact on correctness, users, maintainability, performance, accessibility, etc.>

Existing project pattern:
<similar implementation/component/pattern to follow, when applicable>

Recommendation:
<specific improvement>
```

Prioritize real correctness, UX, accessibility, architecture, security, and maintainability issues over minor style preferences.

---

### 3. Detailed Feedback

Group detailed findings by category:

- Code Quality
- Architecture / Project Structure
- Consistency
- Components / Reusability
- Design System
- UI/UX
- Responsive Design
- Accessibility
- Forms / Validation
- State Management
- Hooks / Side Effects
- Data Fetching / API Integration
- Loading / Error / Empty States
- Routing / Navigation
- Authentication / Authorization
- Security
- Performance
- Rendering Correctness
- Type Safety
- Styling
- Internationalization
- Edge Cases
- Browser Compatibility
- Maintainability
- Tests / Testability

Skip categories where there are no meaningful findings.

---

### 4. Positive Findings

Also mention things that are implemented well, especially:

- Good reuse of existing components
- Correct architecture
- Good design-system usage
- Clear component boundaries
- Good state/data flow
- Strong accessibility
- Good responsive behavior
- Good error/loading handling
- Clear code organization

Do not invent positive feedback just to fill this section.

---

### 5. Actionable Suggestions

Provide a prioritized checklist.

Example:

```text
Must Fix
- [ ] Fix inaccessible keyboard interaction in ...
- [ ] Prevent duplicate form submission in ...
- [ ] Replace unsafe HTML rendering in ...

Should Fix
- [ ] Reuse existing <ComponentName> instead of duplicating ...
- [ ] Align loading/error state with ...
- [ ] Simplify state synchronization in ...

Nice to Have
- [ ] Extract repeated UI logic ...
- [ ] Rename ...
- [ ] Simplify ...
```

---

## Important Review Rules

- Do not modify code unless I explicitly ask you to.
- Do not assume new code is correct simply because it compiles or renders.
- Do not focus only on formatting or style.
- Prioritize correctness, user experience, accessibility, security, architecture, and maintainability.
- Verify existing project conventions before recommending a different approach.
- Verify whether an existing component/hook/utility already solves the problem before suggesting a new abstraction.
- Avoid suggesting unnecessary abstractions or over-engineering.
- Do not introduce a new library when the project already has an established solution unless there is a strong reason.
- Distinguish actual bugs from optional improvements.
- Distinguish design-system violations from subjective visual preferences.
- Explain the impact of each significant issue.
- Prefer concrete, project-specific feedback over generic best-practice advice.
- Do not recommend memoization or performance optimization without evidence that it is useful.
- Do not treat frontend authorization as sufficient security.
- Do not recommend replacing an existing project convention solely because another pattern is more popular.
- If something cannot be determined confidently from the codebase, state that explicitly instead of assuming.

---

## Input

```text
@frontend/
```

Replace `@frontend/` with the relevant frontend project, folder, feature, branch, diff, or files to review.

---

## Notes

**Do not run the test suite.**

In particular, do not run commands such as:

```bash
npm run test
npm test
yarn test
pnpm test
```

Also do not run heavy build, lint, type-check, benchmark, or browser/E2E commands that could cause high CPU or memory usage on my machine unless I explicitly request them.

Examples include:

```bash
npm run build
npm run lint
npm run typecheck
npm run e2e
npx playwright test
npx cypress run
```

Static inspection of source code, configuration, existing tests, styles, components, project structure, and package usage is allowed.

If verification would normally require running tests, builds, browsers, or benchmarks, mention what should be verified instead of executing those commands.
</document_content></document>
