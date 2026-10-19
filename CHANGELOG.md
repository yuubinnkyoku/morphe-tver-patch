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
