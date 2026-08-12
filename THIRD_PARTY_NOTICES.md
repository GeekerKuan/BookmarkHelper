# Open-source notices

This Android application combines code under compatible licenses:

- The original BookmarkHelper project by ChengLiang/viceyy remains available
  under Apache License 2.0. See `LICENSES/Apache-2.0.txt`.
- The Compose presentation layer is adapted from
  [KernelSU Style UI Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit)
  at commit `c90d322c8c6d9d8f591490c5fdb39ff9f6b5210e`, under GNU GPL v3.0.
  See `LICENSES/GPL-3.0.txt`.
- The dual-style application structure, dashboard information hierarchy, and
  floating-navigation layout were adapted with reference to
  [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) at commit
  `35467545b2826e3acfc88699755981a889956b1a`, under GNU GPL v3.0.
- KernelSU Style UI Kit acknowledges KernelSU Manager, Miuix, Jetpack Compose,
  and Material Design as its UI foundation and related ecosystem.
- The selected Material Rounded icon source files used by the SukiSU-derived
  settings rows are copied from AndroidX 1.7.8 under Apache License 2.0. Each
  copied file retains its Android Open Source Project copyright header.
- The liquid-glass backdrop, lens, vibrancy, and navigation-bar effects are
  adapted through the UI Kit from
  [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
  and the `compose-miuix-ui` liquid-glass example, under Apache License 2.0.
  See `LICENSES/Apache-2.0.txt`.
- The optional experimental hook entry compiles against
  [libxposed/api](https://github.com/libxposed/api) 101.0.1 under Apache
  License 2.0. The API is supplied by a compatible framework at runtime and
  is not bundled into the application APK.

Because GPL-covered UI code is combined with the application, distributions
of this combined version must comply with GPL-3.0 and provide the corresponding
source. This notice does not relicense the original Apache-2.0 files in
isolation.
