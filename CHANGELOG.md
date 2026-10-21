## [1.1.0](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.1...v1.1.0) (2026-10-21)

### 🐛 Bug Fixes

* access coroutine label only from helper declared in its own class ([61311b4](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/61311b4159500c1d296889e4fdc4eb16aeeb05d7))
* arm parallel startup before remote config can complete synchronously ([84c6ef3](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/84c6ef3db4f7f5c02b0397ac64498e22bc62e7a4))
* avoid external-label crash in startup animation hooks ([4b39eb0](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4b39eb0013a456bd151c03862ec07d3f34769dd9))
* avoid verifier conflict in startup grace trace using zero-argument helper ([f3adc58](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/f3adc5845d7b6637b1e9df0301b4de4e71472aa3))
* correct int-to-float opcode in early home label Smali ([12ade22](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/12ade22e92cd3b1c001f3dfff9698b9f3bd22ddc))
* correct Smali int-to-float and reserve locals apart from parameter registers ([0fdef80](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/0fdef8059d34e6463ab1b8e8875e39788353a817))
* eliminate optional 1-second home prefetch grace on fast startup ([1f94c9b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1f94c9b479af4fb9c522e673eeb9b7eb5b9d8bb9))
* escape interceptor Chain nested-class descriptor in transport milestone ([19ed544](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/19ed5440d68a13fc7be53e3a4511099fb1661cee))
* escape nested Android layout-class names in Kotlin smali strings ([148bdac](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/148bdacb66f88c56bbede4af1fddc1082eb9d5cc))
* escape nested OkHttp Chain descriptor in Kotlin Smali source ([450438c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/450438c570191c04a973d08a1b16bda85344f2af))
* escape remaining ViewGroup nested parameter descriptor ([bccf508](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/bccf50862675d5c89ba9554738cb4f4956ebec5f))
* interpolate OkHttp Chain method descriptor using existing type constant ([982e446](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/982e446b0286ad5f0c30eac189b6edbb897b1a54))
* invoke private early-home helper with invoke-direct (ART VerifyError) ([c98cfac](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/c98cfac3d9c5749a5a10c3e779e099aa38a9830d))
* per-fragment home frame trace; instrument SDK and CDN request/callback timing ([11f49f6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/11f49f62c87caa433fc2513286a0d22eab3ebd06))
* preserve branch targets in splash grace method ([13b3864](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/13b3864f8917cef4895b9392ead9d1ddd5aa8934))
* preserve clock-sync bytecode indices before prepending trace ([ae20233](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/ae2023320aa1371ee17575191d0d3d5e5cbfd75a))
* preserve target registers in startup milestone hooks ([42e3e85](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/42e3e857a9dfd990ee4b61ca4b8dc7fd95faec9e))
* remove duplicated malformed Smali source trailing after patch closure ([a2a0998](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/a2a09983ef425caadd6469d0dfd9a0005ae94d96))
* remove noninteractive early-home splash skeleton overlay ([0ea2078](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/0ea20785674a6c6fb6e60e35d513043b265b5aba))
* restore Smali template and correctly escape nested OkHttp Chain name ([b75e879](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/b75e879a77387da9f457164cebf205100b61f531))
* trace skipped startup grace and serialize release publishing ([9835ace](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/9835acef6bfea6d911afdd3fbde34d09bb8fc343))
* **tver:** restore AUTO quality and reset per-video selection state ([7abbdd6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7abbdd6ca60478a30bc7032d70f590f791fc2aaf))
* **tver:** show resolution picker while tracks load and refresh options ([4a3d949](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4a3d949d3a01c0e12b1749f59fc148020815e9db))

### ✨ New Features

* collect TVer startup milestones without sensitive data ([3aca7cd](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/3aca7cdaa507bd5abf80fb50bfacc455f562d5b9))
* distinguish user/init connection setup from server response waiting ([5c399eb](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5c399eb73e6991f7cebb9c1fc451d1235835c5fa))
* document optimized fast startup with home landing wait bypass ([d58600f](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/d58600f0f4bc79eb58771798067149b34582134f))
* experimental early home skeleton over splash while boot continues ([2592391](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2592391d2d4150c61dc9f037a19ac08725c67128))
* instrument OkHttp DNS TCP TLS milestones for slow user/init connection ([817ce88](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/817ce88995aa41b0e504f63cf6120e0e8bdad19c))
* instrument user/init HTTP attempt latency and status without exposing payloads ([7b2b576](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7b2b576f5f0a76c00d58af44765afbd5fa130a09))
* measure SDK 24-hour salt refresh HTTP latency in startup ([97e4c74](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/97e4c7482329a6383aca512b928cae5d100efe48))
* measure setting.json HTTP latency and home tab milestones without shifting branch offsets ([0d43d41](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/0d43d41317a846ec812a50e0c44b702030582a36))
* opt-in native Firebase Remote Config 5-minute cache to reduce real warm-start latency ([ca27427](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/ca274279f5d3a9d367d4f868d4ccc3a4691bee26))
* optional nonblocking SDK clock sync to bypass startup retries ([32ad35a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/32ad35a64f7b2d57b662649ae8bea36266391f09))
* optional parallel Remote Config + TVerApp init with SDK startup barrier ([7975bab](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7975bab4ea5c218ebb3a8ddbe9cd9bbc0febb956))
* optional startup animation skip in Morphe settings; restore star chart ([928bd65](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/928bd65da4bb820fb416d5b699430e18d7fd7162))
* show startup timeline and copy it from Morphe settings ([6f9f931](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/6f9f931a9987f8817c01308ed7c4dadffa6fcc1d))
* time remote config gate and first home frame commit ([c58dfb4](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/c58dfb4a12e9883fafe6f0b160ff562686121c75))
* trace actual HTTP 503 retry sleeps to distinguish user-init latency ([34d2f5c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/34d2f5ca34ba0e7a270a92535d541cff4632633e))
* trace SDK browser profile creation request behind slow startup ([1913ab3](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1913ab346413b72877fd35ca7d0e35d8fe08243f))
* trace SDK server-time sync attempts, HTTP latency and retry sleeps ([7e0fb3d](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7e0fb3dc79f47c5b3ce745780aaf0db28429a347))
* trace second SDK CDN setting.json request behind cold startup delays ([3dd1efc](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/3dd1efcae4c3793504f6bfbedbd15de9bdaf6036))
* trace TVer SDK executor, profile setup and callback dispatch to isolate slow init ([4904d79](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4904d797e4e600fce4e3eb7a0bbd777079a347e3))
* trace user init OkHttp terminal call failures ([9e8567d](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/9e8567d842351ac19ce8815d9aa9ced344ef1972))
* trace user/init coroutine and home SDK wait without changing startup gates ([a58f008](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/a58f008a165f2141b593ed299605f92d4d97878e))
* **tver:** add adaptive rendition quality picker runtime bridge ([6fe235d](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/6fe235dd313671cc1dadcf19a5c996e521cf929d))
* **tver:** hook Compose quality button and lift VOD 1080p phone cap ([dadfb38](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/dadfb383bebc44aea6e6cd5791a13abc1b0b4eb6))

## [1.1.0-dev.32](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.31...v1.1.0-dev.32) (2026-10-21)

### 🐛 Bug Fixes

* **tver:** show resolution picker while tracks load and refresh options ([4a3d949](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4a3d949d3a01c0e12b1749f59fc148020815e9db))

## [1.1.0-dev.31](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.30...v1.1.0-dev.31) (2026-10-21)

### 🐛 Bug Fixes

* **tver:** restore AUTO quality and reset per-video selection state ([7abbdd6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7abbdd6ca60478a30bc7032d70f590f791fc2aaf))

## [1.1.0-dev.30](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.29...v1.1.0-dev.30) (2026-10-21)

### ✨ New Features

* **tver:** add adaptive rendition quality picker runtime bridge ([6fe235d](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/6fe235dd313671cc1dadcf19a5c996e521cf929d))
* **tver:** hook Compose quality button and lift VOD 1080p phone cap ([dadfb38](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/dadfb383bebc44aea6e6cd5791a13abc1b0b4eb6))

## [1.1.0-dev.29](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.28...v1.1.0-dev.29) (2026-10-21)

### ✨ New Features

* trace user init OkHttp terminal call failures ([9e8567d](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/9e8567d842351ac19ce8815d9aa9ced344ef1972))

## [1.1.0-dev.28](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.27...v1.1.0-dev.28) (2026-10-21)

### ✨ New Features

* optional nonblocking SDK clock sync to bypass startup retries ([32ad35a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/32ad35a64f7b2d57b662649ae8bea36266391f09))

## [1.1.0-dev.27](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.26...v1.1.0-dev.27) (2026-10-20)

### 🐛 Bug Fixes

* preserve clock-sync bytecode indices before prepending trace ([ae20233](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/ae2023320aa1371ee17575191d0d3d5e5cbfd75a))

### ✨ New Features

* trace SDK server-time sync attempts, HTTP latency and retry sleeps ([7e0fb3d](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7e0fb3dc79f47c5b3ce745780aaf0db28429a347))

## [1.1.0-dev.26](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.25...v1.1.0-dev.26) (2026-10-20)

### ✨ New Features

* instrument OkHttp DNS TCP TLS milestones for slow user/init connection ([817ce88](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/817ce88995aa41b0e504f63cf6120e0e8bdad19c))

## [1.1.0-dev.25](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.24...v1.1.0-dev.25) (2026-10-20)

### ✨ New Features

* measure SDK 24-hour salt refresh HTTP latency in startup ([97e4c74](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/97e4c7482329a6383aca512b928cae5d100efe48))

## [1.1.0-dev.24](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.23...v1.1.0-dev.24) (2026-10-20)

### 🐛 Bug Fixes

* escape interceptor Chain nested-class descriptor in transport milestone ([19ed544](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/19ed5440d68a13fc7be53e3a4511099fb1661cee))
* interpolate OkHttp Chain method descriptor using existing type constant ([982e446](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/982e446b0286ad5f0c30eac189b6edbb897b1a54))
* remove duplicated malformed Smali source trailing after patch closure ([a2a0998](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/a2a09983ef425caadd6469d0dfd9a0005ae94d96))

### ✨ New Features

* distinguish user/init connection setup from server response waiting ([5c399eb](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5c399eb73e6991f7cebb9c1fc451d1235835c5fa))

## [1.1.0-dev.23](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.22...v1.1.0-dev.23) (2026-10-20)

### ✨ New Features

* trace second SDK CDN setting.json request behind cold startup delays ([3dd1efc](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/3dd1efcae4c3793504f6bfbedbd15de9bdaf6036))

## [1.1.0-dev.22](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.21...v1.1.0-dev.22) (2026-10-20)

### ✨ New Features

* trace SDK browser profile creation request behind slow startup ([1913ab3](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1913ab346413b72877fd35ca7d0e35d8fe08243f))

## [1.1.0-dev.21](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.20...v1.1.0-dev.21) (2026-10-20)

### 🐛 Bug Fixes

* escape nested OkHttp Chain descriptor in Kotlin Smali source ([450438c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/450438c570191c04a973d08a1b16bda85344f2af))
* restore Smali template and correctly escape nested OkHttp Chain name ([b75e879](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/b75e879a77387da9f457164cebf205100b61f531))

### ✨ New Features

* instrument user/init HTTP attempt latency and status without exposing payloads ([7b2b576](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7b2b576f5f0a76c00d58af44765afbd5fa130a09))

## [1.1.0-dev.20](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.19...v1.1.0-dev.20) (2026-10-20)

### ✨ New Features

* trace actual HTTP 503 retry sleeps to distinguish user-init latency ([34d2f5c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/34d2f5ca34ba0e7a270a92535d541cff4632633e))

## [1.1.0-dev.19](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.18...v1.1.0-dev.19) (2026-10-20)

### ✨ New Features

* trace TVer SDK executor, profile setup and callback dispatch to isolate slow init ([4904d79](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4904d797e4e600fce4e3eb7a0bbd777079a347e3))

## [1.1.0-dev.18](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.17...v1.1.0-dev.18) (2026-10-20)

### 🐛 Bug Fixes

* access coroutine label only from helper declared in its own class ([61311b4](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/61311b4159500c1d296889e4fdc4eb16aeeb05d7))

### ✨ New Features

* trace user/init coroutine and home SDK wait without changing startup gates ([a58f008](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/a58f008a165f2141b593ed299605f92d4d97878e))

## [1.1.0-dev.17](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.16...v1.1.0-dev.17) (2026-10-20)

### 🐛 Bug Fixes

* remove noninteractive early-home splash skeleton overlay ([0ea2078](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/0ea20785674a6c6fb6e60e35d513043b265b5aba))

## [1.1.0-dev.16](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.15...v1.1.0-dev.16) (2026-10-20)

### 🐛 Bug Fixes

* arm parallel startup before remote config can complete synchronously ([84c6ef3](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/84c6ef3db4f7f5c02b0397ac64498e22bc62e7a4))

### ✨ New Features

* optional parallel Remote Config + TVerApp init with SDK startup barrier ([7975bab](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7975bab4ea5c218ebb3a8ddbe9cd9bbc0febb956))

## [1.1.0-dev.15](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.14...v1.1.0-dev.15) (2026-10-20)

### 🐛 Bug Fixes

* per-fragment home frame trace; instrument SDK and CDN request/callback timing ([11f49f6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/11f49f62c87caa433fc2513286a0d22eab3ebd06))

## [1.1.0-dev.14](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.13...v1.1.0-dev.14) (2026-10-20)

### ✨ New Features

* opt-in native Firebase Remote Config 5-minute cache to reduce real warm-start latency ([ca27427](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/ca274279f5d3a9d367d4f868d4ccc3a4691bee26))

## [1.1.0-dev.13](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.12...v1.1.0-dev.13) (2026-10-20)

### 🐛 Bug Fixes

* invoke private early-home helper with invoke-direct (ART VerifyError) ([c98cfac](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/c98cfac3d9c5749a5a10c3e779e099aa38a9830d))

## [1.1.0-dev.12](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.11...v1.1.0-dev.12) (2026-10-20)

### 🐛 Bug Fixes

* correct int-to-float opcode in early home label Smali ([12ade22](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/12ade22e92cd3b1c001f3dfff9698b9f3bd22ddc))

## [1.1.0-dev.11](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.10...v1.1.0-dev.11) (2026-10-20)

### 🐛 Bug Fixes

* correct Smali int-to-float and reserve locals apart from parameter registers ([0fdef80](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/0fdef8059d34e6463ab1b8e8875e39788353a817))

## [1.1.0-dev.10](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.9...v1.1.0-dev.10) (2026-10-20)

### 🐛 Bug Fixes

* escape nested Android layout-class names in Kotlin smali strings ([148bdac](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/148bdacb66f88c56bbede4af1fddc1082eb9d5cc))
* escape remaining ViewGroup nested parameter descriptor ([bccf508](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/bccf50862675d5c89ba9554738cb4f4956ebec5f))

### ✨ New Features

* experimental early home skeleton over splash while boot continues ([2592391](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2592391d2d4150c61dc9f037a19ac08725c67128))

## [1.1.0-dev.9](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.8...v1.1.0-dev.9) (2026-10-20)

### ✨ New Features

* time remote config gate and first home frame commit ([c58dfb4](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/c58dfb4a12e9883fafe6f0b160ff562686121c75))

## [1.1.0-dev.8](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.7...v1.1.0-dev.8) (2026-10-20)

### ✨ New Features

* measure setting.json HTTP latency and home tab milestones without shifting branch offsets ([0d43d41](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/0d43d41317a846ec812a50e0c44b702030582a36))

## [1.1.0-dev.7](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.6...v1.1.0-dev.7) (2026-10-20)

### 🐛 Bug Fixes

* preserve branch targets in splash grace method ([13b3864](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/13b3864f8917cef4895b9392ead9d1ddd5aa8934))

## [1.1.0-dev.6](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.5...v1.1.0-dev.6) (2026-10-20)

### 🐛 Bug Fixes

* avoid verifier conflict in startup grace trace using zero-argument helper ([f3adc58](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/f3adc5845d7b6637b1e9df0301b4de4e71472aa3))
* preserve target registers in startup milestone hooks ([42e3e85](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/42e3e857a9dfd990ee4b61ca4b8dc7fd95faec9e))

## [1.1.0-dev.5](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.4...v1.1.0-dev.5) (2026-10-20)

### ✨ New Features

* show startup timeline and copy it from Morphe settings ([6f9f931](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/6f9f931a9987f8817c01308ed7c4dadffa6fcc1d))

## [1.1.0-dev.4](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.3...v1.1.0-dev.4) (2026-10-20)

### ✨ New Features

* collect TVer startup milestones without sensitive data ([3aca7cd](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/3aca7cdaa507bd5abf80fb50bfacc455f562d5b9))

## [1.1.0-dev.3](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-10-19)

### 🐛 Bug Fixes

* eliminate optional 1-second home prefetch grace on fast startup ([1f94c9b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1f94c9b479af4fb9c522e673eeb9b7eb5b9d8bb9))
* trace skipped startup grace and serialize release publishing ([9835ace](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/9835acef6bfea6d911afdd3fbde34d09bb8fc343))

### ✨ New Features

* document optimized fast startup with home landing wait bypass ([d58600f](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/d58600f0f4bc79eb58771798067149b34582134f))

## [1.1.0-dev.3](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-10-19)

### 🐛 Bug Fixes

* eliminate optional 1-second home prefetch grace on fast startup ([1f94c9b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1f94c9b479af4fb9c522e673eeb9b7eb5b9d8bb9))
* trace skipped startup grace and serialize release publishing ([9835ace](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/9835acef6bfea6d911afdd3fbde34d09bb8fc343))

### ✨ New Features

* document optimized fast startup with home landing wait bypass ([d58600f](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/d58600f0f4bc79eb58771798067149b34582134f))

## [1.1.0-dev.3](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-10-19)

### 🐛 Bug Fixes

* eliminate optional 1-second home prefetch grace on fast startup ([1f94c9b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1f94c9b479af4fb9c522e673eeb9b7eb5b9d8bb9))
* trace skipped startup grace and serialize release publishing ([9835ace](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/9835acef6bfea6d911afdd3fbde34d09bb8fc343))

## [1.1.0-dev.3](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-10-19)

### 🐛 Bug Fixes

* eliminate optional 1-second home prefetch grace on fast startup ([1f94c9b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1f94c9b479af4fb9c522e673eeb9b7eb5b9d8bb9))

## [1.1.0-dev.2](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.1.0-dev.1...v1.1.0-dev.2) (2026-10-19)

### 🐛 Bug Fixes

* avoid external-label crash in startup animation hooks ([4b39eb0](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4b39eb0013a456bd151c03862ec07d3f34769dd9))

## [1.1.0-dev.1](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.1...v1.1.0-dev.1) (2026-10-19)

### ✨ New Features

* optional startup animation skip in Morphe settings; restore star chart ([928bd65](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/928bd65da4bb820fb416d5b699430e18d7fd7162))

## [1.0.1](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0...v1.0.1) (2026-10-19)

### 🐛 Bug Fixes

* remove template patch and repair localized README listings ([29ef3a6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/29ef3a66a3c56960540df3c35a42e8bce145d823))

## [1.0.1-dev.1](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0...v1.0.1-dev.1) (2026-10-19)

### 🐛 Bug Fixes

* remove template patch and repair localized README listings ([29ef3a6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/29ef3a66a3c56960540df3c35a42e8bce145d823))

## 1.0.0 (2026-10-19)

### 🐛 Bug Fixes

* add on-screen manual PiP diagnostic button and logcat events ([5792f86](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5792f865ddc00f2dad881cf60ccaca8805420f0d))
* allow episode PiP during buffering before audio starts ([b40d764](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/b40d764879aa34793f2c958182dad6c231d272ff))
* allow Home PiP audio fallback when activity Intent is absent ([5ed811a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5ed811a9734ad405fbcfe4e7689d3774e9c170a5))
* allow legacy Home fallback when automatic PiP request fails ([29f3b8c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/29f3b8cb72c11c45c4b7565545612dafb6123ec1))
* allow PiP as soon as episode navigation begins ([1bf838b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1bf838be1e8426df95b5b26aa9fedaf768cfc6ec))
* block home preview PiP when main navigation tabs are visible ([2462c11](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2462c11de0b0f35b2111b31fd20123a9518f110e))
* call valid enterPictureInPictureMode(PictureInPictureParams) overload ([502768a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/502768a9df05d53dcb6b9b7cccbb20550cde84b4))
* compare actual dex opcode enum when locating PiP return instructions ([2ee0005](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2ee0005e469d2f9f19c0c26723b320571e56a9cb))
* correct Kotlin interpolation syntax in PiP diagnostic overlay ([1114059](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/111405948a6f2b4850de1043c4ce1f6fca3cf6cf))
* detect visible episode playback host instead of home preview audio ([70e8361](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/70e8361b06c9c93dcf7498eb2e2e35b0ef1f2117))
* enable Android 12 automatic PiP during TVer video playback ([8b17cef](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/8b17cef351ed8ba192cb4bec1a45d1e33e8caf83))
* escape Kotlin dollar signs in generated debug PiP smali ([5c4a413](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5c4a413a757ef444d0823b3129e7d2d07bdf27a2))
* escape nested PiP builder class name in Kotlin smali strings ([7f15aac](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7f15aac8fcf7eaa5d171161f426dbd4d449555b4))
* escape PiP Builder class name in replay auto-enter polling ([38c672a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/38c672a49f692da818d0cb96fc21a5bdb5e2eecf))
* handle Android system PiP request callback before Home leave fallback ([93644d5](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/93644d596252762b07eba876c1a0d79de0c7d7e4))
* handle PiP player lifecycle returns without assuming terminal instruction ([157c052](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/157c0520c67e355004c0eecae0058dc5d2881183))
* import mutable field conversion for PiP phase tracker ([3a8c979](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/3a8c97972d9b4d5ff79cecfdda2b1d52138284ed))
* log video fragment lifecycle and Activity leave hints for PiP diagnosis ([7d785a7](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7d785a7c1201aa231f4ae4000b12e18c4f2aa6c2))
* pre-enable automatic PiP while replay audio is active ([8c130c5](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/8c130c5fecaa891df5dc1d07b9489b18911de21c))
* preserve Activity register in Home PiP fallback logging ([2cd87e4](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2cd87e49b0229b57a63f82873e3702b502617ad0))
* read AndroidManifest activity names using Morphe XML attribute conventions ([459b9b6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/459b9b6e2d824917aaa8a003f81742f9107f54f6))
* rebuild Home PiP audio fallback without corrupted duplicate code ([03de4d7](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/03de4d7730f23ed5dc7dd4d713307e06c35d41ea))
* repair truncated PiP builder descriptor in automatic Home fallback ([cfaf286](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/cfaf286252d935170a584fcfdeb2d98ee0f22a2d))
* restore interrupted dev.10 channel note to unblock dev.11 release ([46c0080](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/46c00802382203ee7d434354982d4ea40e6e7320))
* skip automatic PiP for explicitly paused episode playback ([ccb00c2](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/ccb00c2657211e2a160d91a755b285236c833c51))
* try automatic PiP on Home when media audio is active ([c184c10](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/c184c10ed201b239c839cb36df939c4bd01c0278))

### ✨ New Features

* add persistent PiP TEST visibility switch under Morphe settings ([4629ee9](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4629ee93da507eecc660cd37a451be01ea9123cb))
* publish experimental TVer 7.2.1 PiP and ad patches ([947334b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/947334b7f8b1f591993477fa8ac17539ff8ec61c))

## [1.0.0-dev.18](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.17...v1.0.0-dev.18) (2026-10-19)

### ✨ New Features

* add persistent PiP TEST visibility switch under Morphe settings ([4629ee9](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/4629ee93da507eecc660cd37a451be01ea9123cb))

## [1.0.0-dev.17](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.16...v1.0.0-dev.17) (2026-10-19)

### 🐛 Bug Fixes

* allow PiP as soon as episode navigation begins ([1bf838b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/1bf838be1e8426df95b5b26aa9fedaf768cfc6ec))

## [1.0.0-dev.16](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.15...v1.0.0-dev.16) (2026-10-19)

### 🐛 Bug Fixes

* import mutable field conversion for PiP phase tracker ([3a8c979](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/3a8c97972d9b4d5ff79cecfdda2b1d52138284ed))
* skip automatic PiP for explicitly paused episode playback ([ccb00c2](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/ccb00c2657211e2a160d91a755b285236c833c51))

## [1.0.0-dev.15](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.14...v1.0.0-dev.15) (2026-10-19)

### 🐛 Bug Fixes

* allow episode PiP during buffering before audio starts ([b40d764](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/b40d764879aa34793f2c958182dad6c231d272ff))

## [1.0.0-dev.14](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.13...v1.0.0-dev.14) (2026-10-19)

### 🐛 Bug Fixes

* detect visible episode playback host instead of home preview audio ([70e8361](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/70e8361b06c9c93dcf7498eb2e2e35b0ef1f2117))

## [1.0.0-dev.13](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.12...v1.0.0-dev.13) (2026-10-19)

### 🐛 Bug Fixes

* block home preview PiP when main navigation tabs are visible ([2462c11](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2462c11de0b0f35b2111b31fd20123a9518f110e))

## [1.0.0-dev.12](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.11...v1.0.0-dev.12) (2026-10-19)

### 🐛 Bug Fixes

* escape PiP Builder class name in replay auto-enter polling ([38c672a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/38c672a49f692da818d0cb96fc21a5bdb5e2eecf))
* pre-enable automatic PiP while replay audio is active ([8c130c5](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/8c130c5fecaa891df5dc1d07b9489b18911de21c))

## [1.0.0-dev.11](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.10...v1.0.0-dev.11) (2026-10-18)

### 🐛 Bug Fixes

* allow Home PiP audio fallback when activity Intent is absent ([5ed811a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5ed811a9734ad405fbcfe4e7689d3774e9c170a5))
* restore interrupted dev.10 channel note to unblock dev.11 release ([46c0080](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/46c00802382203ee7d434354982d4ea40e6e7320))

## [1.0.0-dev.10](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.9...v1.0.0-dev.10) (2026-10-18)

### 🐛 Bug Fixes

* allow Home PiP audio fallback when activity Intent is absent ([5ed811a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5ed811a9734ad405fbcfe4e7689d3774e9c170a5))
* allow legacy Home fallback when automatic PiP request fails ([29f3b8c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/29f3b8cb72c11c45c4b7565545612dafb6123ec1))

## [1.0.0-dev.10](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.9...v1.0.0-dev.10) (2026-10-18)

### 🐛 Bug Fixes

* allow legacy Home fallback when automatic PiP request fails ([29f3b8c](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/29f3b8cb72c11c45c4b7565545612dafb6123ec1))

## [1.0.0-dev.9](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.8...v1.0.0-dev.9) (2026-10-18)

### 🐛 Bug Fixes

* handle Android system PiP request callback before Home leave fallback ([93644d5](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/93644d596252762b07eba876c1a0d79de0c7d7e4))

## [1.0.0-dev.8](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.7...v1.0.0-dev.8) (2026-10-18)

### 🐛 Bug Fixes

* preserve Activity register in Home PiP fallback logging ([2cd87e4](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2cd87e49b0229b57a63f82873e3702b502617ad0))
* rebuild Home PiP audio fallback without corrupted duplicate code ([03de4d7](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/03de4d7730f23ed5dc7dd4d713307e06c35d41ea))
* repair truncated PiP builder descriptor in automatic Home fallback ([cfaf286](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/cfaf286252d935170a584fcfdeb2d98ee0f22a2d))
* try automatic PiP on Home when media audio is active ([c184c10](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/c184c10ed201b239c839cb36df939c4bd01c0278))

## [1.0.0-dev.7](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.6...v1.0.0-dev.7) (2026-10-18)

### 🐛 Bug Fixes

* call valid enterPictureInPictureMode(PictureInPictureParams) overload ([502768a](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/502768a9df05d53dcb6b9b7cccbb20550cde84b4))

## [1.0.0-dev.6](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.5...v1.0.0-dev.6) (2026-10-18)

### 🐛 Bug Fixes

* add on-screen manual PiP diagnostic button and logcat events ([5792f86](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5792f865ddc00f2dad881cf60ccaca8805420f0d))
* correct Kotlin interpolation syntax in PiP diagnostic overlay ([1114059](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/111405948a6f2b4850de1043c4ce1f6fca3cf6cf))
* escape Kotlin dollar signs in generated debug PiP smali ([5c4a413](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/5c4a413a757ef444d0823b3129e7d2d07bdf27a2))
* log video fragment lifecycle and Activity leave hints for PiP diagnosis ([7d785a7](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7d785a7c1201aa231f4ae4000b12e18c4f2aa6c2))

## [1.0.0-dev.5](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.4...v1.0.0-dev.5) (2026-10-18)

### 🐛 Bug Fixes

* enable Android 12 automatic PiP during TVer video playback ([8b17cef](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/8b17cef351ed8ba192cb4bec1a45d1e33e8caf83))
* escape nested PiP builder class name in Kotlin smali strings ([7f15aac](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/7f15aac8fcf7eaa5d171161f426dbd4d449555b4))

## [1.0.0-dev.4](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.3...v1.0.0-dev.4) (2026-10-18)

### 🐛 Bug Fixes

* compare actual dex opcode enum when locating PiP return instructions ([2ee0005](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/2ee0005e469d2f9f19c0c26723b320571e56a9cb))

## [1.0.0-dev.3](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.2...v1.0.0-dev.3) (2026-10-18)

### 🐛 Bug Fixes

* handle PiP player lifecycle returns without assuming terminal instruction ([157c052](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/157c0520c67e355004c0eecae0058dc5d2881183))

## [1.0.0-dev.2](https://github.com/yuubinnkyoku/morphe-tver-patch/compare/v1.0.0-dev.1...v1.0.0-dev.2) (2026-10-18)

### 🐛 Bug Fixes

* read AndroidManifest activity names using Morphe XML attribute conventions ([459b9b6](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/459b9b6e2d824917aaa8a003f81742f9107f54f6))

## 1.0.0-dev.1 (2026-10-18)

### ✨ New Features

* publish experimental TVer 7.2.1 PiP and ad patches ([947334b](https://github.com/yuubinnkyoku/morphe-tver-patch/commit/947334b7f8b1f591993477fa8ac17539ff8ec61c))
