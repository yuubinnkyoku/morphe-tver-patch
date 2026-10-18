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
