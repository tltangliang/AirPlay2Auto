# AirPlay2Auto · 车载 AirPlay 2 接收器

[中文](#中文) · [English](#english)

把安卓车机变成 AirPlay 2 接收端。 · Turn an Android car head unit into an AirPlay 2 receiver.

基于 / Based on [android-airplay-server](https://github.com/jqssun/android-airplay-server)（UxPlay，GPLv3）；热点控制思路参考 / hotspot approach informed by [DiPlay](https://github.com/shihabal3amri/DiPlay)（GPLv3）。

**下载 / Download：** [Releases](https://github.com/tltangliang/AirPlay2Auto/releases/latest)

---

## 中文

iPhone / iPad / Mac 可以通过「屏幕镜像」或 AirPlay，把画面、视频、音乐投到车机上。

### 功能

**兼容性**
- 支持 Android 6.0 及以上（API 23+）
- 内置 arm64-v8a、armeabi-v7a、x86、x86_64 四种架构，覆盖绝大多数车机，包括 32 位系统和 Intel 方案车机
- 横屏宽屏车机自动改用侧边导航栏，界面为中文
- 独立包名 `io.github.carairplay`，可与原版共存

**车机热点（「车机」页）**
- 设置热点名称、密码、2.4G/5G 频段，一键开启 / 关闭热点
- 按系统版本自动选择开启方式：
  - Android 6–7：直接设置并开启热点
  - Android 8–10：通过系统接口开启已保存的热点
  - Android 11+：通过 TetheringManager 开启；已 Root 的车机可用 `cmd wifi start-softap` 按自定义名称和密码开启
  - 以上都不可用时：开启临时热点（系统随机名称和密码），或跳转到系统热点设置
- 需要授予「修改系统设置」权限
- Android 8.0 以上修改热点**名称和密码**，需要系统权限（应用装为系统应用）或 Root；否则使用系统里已保存的热点

**自动启动和保活**
- 开机、快速启动、常见车机 ACC 上电广播都会自动启动，可设置启动延迟，等待 Wi-Fi 就绪
- 适配「假关机 / 休眠」车机：亮屏、解锁或收到 ACC 广播时自动恢复
- 可填写自定义 ACC 广播
- 开机后自动打开界面并退回后台（需要「显示在其他应用上层」权限）
- 启动时自动开启热点
- 前台服务加定时检查，被系统清理后自动拉起
- 热点或 Wi-Fi 变化后自动重新广播 AirPlay，避免手机搜不到
- 一键申请忽略电池优化

### 使用

1. 安装 APK 并打开应用，进入「车机」页
2. 授予「修改系统设置」权限，设置热点名称和密码，点「保存并开启热点」（也可以在系统里手动开热点）
3. iPhone 连接车机热点，或与车机连接同一个 Wi-Fi
4. 在 iPhone 控制中心点「屏幕镜像」，选择「车载AirPlay」

### 编译

```bash
git clone --recursive https://github.com/tltangliang/AirPlay2Auto.git
cd AirPlay2Auto
./gradlew assembleRelease
```

需要 JDK 21、Android SDK 37、NDK 27.0.12077973。

推送到 `main` 后，GitHub Actions 会自动编译，APK 在 Actions 的 Artifacts 中下载。推送 `v*` 标签会自动发布 Release：如果仓库里有已测试的 `release/CarAirPlay-<版本>.apk`，就发布这个文件，否则发布 CI 编译的版本。

如需 CI 使用固定签名（便于覆盖升级），在仓库 Secrets 中配置 `STORE`（keystore 的 base64）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

### 说明

- 不支持 DRM 内容，例如 Apple TV App 里的影片
- 热点控制依赖车机固件，不同车机效果可能不同
- 许可证：GPLv3

---

## English

Cast screen mirroring, video and music from an iPhone, iPad or Mac to the head unit with Screen Mirroring or AirPlay.

### Features

**Compatibility**
- Android 6.0 and later (API 23+)
- Ships arm64-v8a, armeabi-v7a, x86 and x86_64, covering most head units, including 32-bit systems and Intel-based units
- Wide landscape screens get a side navigation rail; UI in Chinese and English
- Separate package name `io.github.carairplay`, so it can be installed next to the original app

**Car hotspot ("Car" tab)**
- Set the hotspot SSID, password and 2.4/5 GHz band, and turn the hotspot on or off with one tap
- The method is chosen by Android version:
  - Android 6–7: configures and starts the hotspot directly
  - Android 8–10: starts the saved hotspot through the system tethering interface
  - Android 11+: starts it through TetheringManager; on rooted units, `cmd wifi start-softap` starts it with your own SSID and password
  - If none of these works: a local-only hotspot (random SSID and password from the system), or a shortcut to the system hotspot settings
- Requires the "Modify system settings" permission
- On Android 8.0 and later, changing the hotspot **SSID and password** needs system rights (installed as a system app) or root; otherwise the hotspot saved in the system is used

**Autostart and keep-alive**
- Starts on boot, quick boot and common head-unit ACC-on broadcasts, with a configurable delay so Wi-Fi is ready first
- For units that sleep instead of shutting down: recovers on screen on, unlock or ACC broadcasts
- Custom ACC broadcast actions can be added
- Can open the app at boot and then send it to the background (needs "Display over other apps")
- Can turn the hotspot on when it starts
- Foreground service plus a periodic check that restarts the receiver if the system kills it
- Re-advertises AirPlay when the hotspot or Wi-Fi changes, so the phone can still find it
- One-tap request to ignore battery optimization

### Usage

1. Install the APK, open the app and go to the "Car" tab
2. Grant "Modify system settings", set the hotspot name and password, and tap "Save and start hotspot" (or turn the hotspot on in system settings)
3. Connect the iPhone to the head unit's hotspot, or put both on the same Wi-Fi
4. On the iPhone, open Control Center, tap Screen Mirroring and choose "车载AirPlay"

### Build

```bash
git clone --recursive https://github.com/tltangliang/AirPlay2Auto.git
cd AirPlay2Auto
./gradlew assembleRelease
```

Requires JDK 21, Android SDK 37 and NDK 27.0.12077973.

Every push to `main` is built by GitHub Actions, and the APK can be downloaded from the run's Artifacts. Pushing a `v*` tag publishes a Release: it uses the tested `release/CarAirPlay-<version>.apk` from the repository when one exists, otherwise the CI build.

To have CI sign with a fixed key (so updates install over the old version), add the repository secrets `STORE` (base64 of the keystore), `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

### Notes

- DRM content (for example films in the Apple TV app) is not supported
- Hotspot control depends on the head unit's firmware and may behave differently between units
- License: GPLv3
