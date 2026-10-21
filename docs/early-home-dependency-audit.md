# TVer 7.2.1: real home early initialization — dependency audit

Date: 2026-10-20  
Target: `jp.hamitv.hamiand1` / TVer 7.2.1; baseline Morphe patch dev.13.  
Scope: read-only static inspection of the original APK's DEX (JADX). No early `initTabs()` modification has been enabled.

## What is already built before the SDK is ready

`TabMainActivityFragment.onCreateView()` inflates `FragmentTabContainerBinding`, calls `DataRepository.hideMainTabLayoutNow()`, then adds `SplashFragment` to the modal stack on fresh launch.

`TabMainActivityFragment.onViewCreated()` calls `settingsTabs()` *before* SDK completion. `settingsTabs()` constructs the actual native `MainTabView` items for Home, Live, My Page, and Search. The real tab bar thus exists; it is not necessary to draw a fake footer to pre-create it.

However, this does **not** mean the corresponding screens exist or are ready. The navigation destinations are installed later.

## Actual screen creation and readiness

`MainActivity.initTabs()` checks `mTopFragment.getTabsInitialized()` and, if false, invokes `TabMainActivityFragment.initTabs()`.

`TabMainActivityFragment.initTabs()`:
1. Calls `getViewModel().setHasInitTabs(true)`.
2. Creates four `FragmentContainerFragment` bases for `HomeKey`, `LiveTopKey`, `MyPageKey`, `SearchKey`.
3. Calls `initTabFragments(arrayList)`, which delegates to `MultiTabNav3Controller.setBaseIfEmpty()` for each index.
4. Calls `changeActiveTab(selectedPosition)`.
5. Starts `getViewModel().getMyPageBadge()`.

Hence, moving this whole method before configuration and consent readiness would start destination creation/selection and may expose SDK-dependent data calls. It is not just a UI allocation.

## Per-feature findings

- **Home:** `SharedHomePageViewModel` sets up a paging data flow from `GetHomePageUseCase.invoke()` (`cachedIn` on the ViewModel scope). Content collection can start a real API request; a cache-only representation must be distinguished from live data. This audit has not proven the full Home screen is safe before API endpoint initialization.
- **Live:** `LiveTopViewModel` already subscribes to `SdkInitFinishedEvent` and `onCreate()` initializes channels only if `TVerInitializeManager.isInitSdk()` is true and channel tabs are empty. This is an existing gate and a useful model for deferred data initialization. Other live APIs still need review.
- **Search:** `SearchViewModel` constructor launches `AnonymousClass1` in `viewModelScope` immediately; that coroutine calls `fetchTop(false)`. There is no SDK-readiness check around the launch visible in this path. Therefore eager creation/selection of Search can race endpoint/session configuration and requires a dedicated gate or replay/retry path.
- **My Page:** Initialization and authentication dependencies are not yet audited; do not expose authenticated data or treat missing session as logged-out.
- **Remote config:** `SplashViewModel.updateAppRemoteConfig(after)` launches a coroutine awaiting `RemoteConfigInteractor.fetchAndActivate()`, then runs `after` on Main; this blocks the current SDK startup chain. `RemoteConfigInteractor` uses Firebase Remote Config `fetchAndActivate`. Earlier measurements show 200–500ms typically, with network variance elsewhere.
- **TVerApp configuration:** `TVerApp.init()` fetches `https://s.tver.jp/setting.json` and sets API endpoints. In one run its HTTP time was 3.231s. Requests depending on these endpoints cannot simply precede this step.

## Proposed real early-home design

1. Keep SDK, consent, and user initialization intact. Separate **UI_READY**, **CONFIG_READY**, **SDK_READY**, and **USER_READY** into explicit states; a ready flag is never inferred only from a frame commit.
2. Use the **existing** native `settingsTabs()` output and Nav3 hosting UI. Do not show a fabricated `TVer` home skeleton as if it were functional.
3. Gate or defer **every API-dependent operation** in Home/Search/Live/My Page until the specific required state. Search's constructor-started `fetchTop()` is a known gating requirement. Protect playback and user functions until SDK/user consent gates succeed.
4. Build and display the real tab host early only after the request sites are safely gated. Preserve existing selected-tab, deeplink, modal and restoration semantics. Do not invoke `initTabs()` twice; its state flag is set before initialization finishes.
5. On state transitions, resume or replay the deferred fetch *once*; handle failure and retries without permanently blank tabs. Consider cache-first display only for validated, non-sensitive persisted content.
6. Measure both true first frame and first **usable** Home/Search/Live interaction, plus request start, response, and ready timestamps; compare repeated cold/warm boots.
7. Make the risky early mode opt-in with a rollback setting in Morphe Settings, default off initially. First validate bytecode verifier correctness and runtime on a clean APK before changing the default.

## Outstanding work

- Inspect `HomeScreenKt`/navigation destination and `GetHomePageUseCase` to identify the actual first request and whether a cached first page can be reused safely.
- Inspect `SearchScreenKt` and `SearchViewModel.fetchTop()` for SDK/endpoint assumptions and how to replay fetch.
- Audit My Page and global analytics/consent/logging hooks.
- Review `MultiTabNav3Controller` composition and restoration behavior under early base destination setup.
- Only then implement a staged opt-in patch, with DEX/ART verification and startup tests.

## Prior experiments

`dev.13` places an inert, noninteractive home-looking overlay inside Splash; a user's device confirmed it displays early and later transitions to real home. This is **visual masking**, not an actual functional startup-speed improvement. It should not be represented as a finished solution.
