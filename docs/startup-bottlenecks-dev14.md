# TVer 7.2.1 startup: dev.14 measured bottlenecks

Measured 2026-10-20; timings are relative to each `MAIN_ON_CREATE`. The user's one OFF and three ON runs are observational, not a randomized controlled benchmark.

| Stage | Cache OFF | Cache ON #1 | Cache ON #2 | Cache ON #3 |
| --- | ---: | ---: | ---: | ---: |
| Remote Config: FETCH_ENTER → GATE_RELEASED | 536 ms | 35 ms | 36 ms | 36 ms |
| `setting.json` HTTP: START → RESPONSE | 210 ms | 209 ms | 224 ms | 218 ms |
| HTTP response → CDN config received | 250 ms | 206 ms | 222 ms | 289 ms |
| CDN config → SDK_INIT_SUCCESS | 542 ms | 487 ms | 307 ms | 503 ms |
| SDK_INIT_SUCCESS → PRIVACY_SETTINGS_SUCCESS | 350 ms | 328 ms | 263 ms | 388 ms |
| PRIVACY_SETTINGS_SUCCESS → HOME_FIRST_FRAME_COMMITTED | 98 ms | 119 ms | 100 ms | 123 ms |
| **First real Home frame (since MAIN_ON_CREATE)** | **2,073 ms** | **1,433 ms** | **1,197 ms** | **1,607 ms** |

The opt-in Firebase 5-minute minimum fetch interval is visibly enabled in all three ON logs and the Remote Config gate decreases from 536 ms to 35–36 ms; this is consistent with Firebase's cache path, although these markers alone do not report whether a network request was performed.

Mean real Home first-frame time on three ON runs: **1,412.3 ms**; difference from single OFF run: **660.7 ms**. This figure includes changing conditions and cannot be attributed entirely to the patch. Mean ON Remote Config interval: **35.7 ms**, saving **500.3 ms** relative to OFF.

## Verified details in original TVer 7.2.1 APK

Inspected `classes6.dex` with JADX 1.5.6:
- `SplashFragment.initializeSdkPlugins()` calls `TVerApp.init(applicationContext, new TVerAppInitOptions("https://s.tver.jp", "7.2.1", ANDROID), callback)`.
- `TVerApp.init$lambda$3()` performs blocking `Call.execute()` to `options.cdnEndpoint + "/setting.json"`.
- A successful JSON response populates *member, platform, service, content, catalog, ads, user, image CDN and static CDN endpoints*. This is not decorative metadata; using a stale or invalid response can break the SDK, and bypassing it without initializing endpoints will break the app.
- The JSON is parsed after the response, and success is posted to the main thread. The existing instrumentation surrounds only the HTTP `Call.execute()`, not all callback processing.

Inspected `classes13.dex`:
- `jp.tver.appsdk.core.CommonKt.defaultHttpClient()` returns `TVerAppHttpClients.INSTANCE.getDefault$appsdk_release()`.
- `TVerAppHttpClients.base` creates an `OkHttpClient.Builder()` with dispatcher and 10-second read/write/connect timeouts; **no OkHttp disk Cache** is configured in this builder.
- Default client adds `PerformanceMonitoringInterceptor` and `UserAgentInterceptor`, with no cache builder invocation visible in its construction path.

## Priority options

1. **Safer bounded endpoint cache (opt-in)**: persist only fully validated successful `setting.json` JSON, with schema and all mandatory keys checked, bound to the exact CDN URL + app version and short TTL. Keep network-fetch path and rollback; do not cache authentication tokens or user sessions. Do not bypass privacy or SDK callbacks. Prove compatibility with server-side cache policy before enabling.
2. **Parallel speculative prefetch (opt-in)**: the URL is a constant in this version, so the HTTP request could start while Firebase Remote Config runs. But with the Remote Config cache enabled, this only overlaps ~36 ms; it would be more useful when Firebase cache is OFF or expired. Avoid duplicate fetch and preserve error semantics.
3. **Investigate CDN config → SDK success (307–503 ms ON)**: this is a bigger remaining stage than the HTTP fetch alone. Instrument SDK agreement, network request boundaries, and SDK initialization substeps before changing order.
4. **True early Home UI**: `settingsTabs()` already constructs the real tab bar before SDK readiness, but `initTabs()` creates navigation destinations and starts data operations. Separate their API requests and safety gates before moving navigation forward. The existing dev.13 skeleton does not shorten first interactive Home time.

Do **not** make remote config or SDK consent/authorization optional, reuse endpoint JSON indefinitely, or claim the fake skeleton is actual Home startup latency improvement.
