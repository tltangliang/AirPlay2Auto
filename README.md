# AirPlay2Auto · 车载 AirPlay 2 接收器

[中文](#中文) · [English](#english)

把安卓车机变成 AirPlay 2 接收端。 · Turn an Android car head unit into an AirPlay 2 receiver.

基于 / Based on [android-airplay-server](https://github.com/jqssun/android-airplay-server)（UxPlay，GPLv3）；车机适配思路参考 / car head-unit ideas from [DiPlay](https://github.com/shihabal3amri/DiPlay)（GPLv3）。

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
- 为保证兼容性，热点的名称、密码和开关都在车机**系统设置**中完成，本应用不修改热点
- 「车机」页显示热点状态和当前网络地址，并提供「打开系统热点设置」「Wi-Fi 设置」快捷入口
- 热点开启或 Wi-Fi 切换后，自动在新网络上重新广播 AirPlay，手机即可搜到

**自动启动和保活**
- 开机、快速启动、常见车机 ACC 上电广播都会自动启动，可设置启动延迟，等待 Wi-Fi 就绪
- 适配「假关机 / 休眠」车机：亮屏、解锁或收到 ACC 广播时自动恢复
- 可填写自定义 ACC 广播
- 开机后自动打开界面并退回后台（需要「显示在其他应用上层」权限）
- 前台服务加定时检查，被系统清理后自动拉起
- 热点或 Wi-Fi 变化后自动重新广播 AirPlay，避免手机搜不到
- 一键申请忽略电池优化

**系统权限和原生设置（「车机」页）**
- 「系统权限」列表实时显示每项权限是否已开启，一键跳到对应的系统页面：附近设备 / 本地网络（Android 17+）、通知、显示在其他应用上层、忽略电池优化、自启动 / 后台运行、应用详情
- 运行时权限会先弹出系统授权框，被拒绝后自动跳到系统设置
- 「安卓原生设置」快捷入口：设置主页、WLAN、热点、蓝牙、显示、声音、应用管理、悬浮窗管理、电池优化名单、语言、日期时间、开发者选项、关于设备
- 优先打开安卓原生设置（`com.android.settings`），适合桌面隐藏了原生设置的车机；车机没有原生设置时自动改用系统默认的设置页面

**全屏显示**
- 屏幕镜像：点画面或全屏按钮进入全屏，可选「适应 / 拉伸 / 裁剪」，宽屏车机去黑边，选择会被记住
- 音乐：全屏播放页（大封面、歌曲信息、播放控制、音量），横屏 / 竖屏自适应
- 视频：AirPlay 视频始终全屏播放，支持适应 / 拉伸 / 裁剪 / 100%
- 可设置镜像连接时自动全屏、音乐播放时自动全屏
- 画中画小窗默认关闭：很多车机系统无法操作画中画，会留下点不动的黑框；车机支持时可在「车机」页开启

### 使用

1. 安装 APK 并打开应用，进入「车机」页，在「系统权限」里把各项权限打开
2. 点「打开系统热点设置」，在系统中设置并开启车机热点
3. iPhone 连接车机热点，或与车机连接同一个 Wi-Fi
4. 在 iPhone 控制中心点「屏幕镜像」，选择「车载AirPlay」（设备名称可在「设置」里修改）

### 编译

```bash
git clone --recursive https://github.com/tltangliang/AirPlay2Auto.git
cd AirPlay2Auto
./gradlew assembleRelease
```

需要 JDK 21、Android SDK 37、NDK 27.0.12077973。

推送到 `main` 后，GitHub Actions 会自动编译，APK 在 Actions 的 Artifacts 中下载。要发布新版本，把测试过的 APK 以 `release/AirPlay2Auto-<版本>.apk` 提交到 `main`（可附带 `release/notes-<版本>.md` 作为发布说明），Actions 会自动创建 `v<版本>` 标签并发布 Release。

如需 CI 使用固定签名（便于覆盖升级），在仓库 Secrets 中配置 `STORE`（keystore 的 base64）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

### 说明

- 不支持 DRM 内容，例如 Apple TV App 里的影片
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
- For compatibility, the hotspot name, password and on/off switch are handled in the head unit's **system settings**; the app does not change the hotspot
- The "Car" tab shows the hotspot state and current network addresses, with shortcuts to the system hotspot and Wi-Fi settings
- When the hotspot comes up or Wi-Fi changes, AirPlay is re-advertised on the new network so the phone can find it

**Autostart and keep-alive**
- Starts on boot, quick boot and common head-unit ACC-on broadcasts, with a configurable delay so Wi-Fi is ready first
- For units that sleep instead of shutting down: recovers on screen on, unlock or ACC broadcasts
- Custom ACC broadcast actions can be added
- Can open the app at boot and then send it to the background (needs "Display over other apps")
- Foreground service plus a periodic check that restarts the receiver if the system kills it
- Re-advertises AirPlay when the hotspot or Wi-Fi changes, so the phone can still find it
- One-tap request to ignore battery optimization

**System permissions and native settings ("Car" tab)**
- The "System permissions" list shows each permission's live state and opens the matching system page: nearby devices / local network (Android 17+), notifications, display over other apps, battery optimization, autostart / background running, and app info
- Runtime permissions show the system prompt first and open system settings if refused
- "Native Android settings" shortcuts: Settings home, Wi-Fi, hotspot, Bluetooth, display, sound, apps, overlay permissions, battery optimization list, language, date and time, developer options, about device
- Opens the native Android settings (`com.android.settings`) first, which helps on head units whose launcher hides them; falls back to the system default settings screen when a unit has no native settings

**Full screen**
- Screen mirroring: tap the picture or the fullscreen button; choose fit / stretch / crop to remove black bars on wide car displays (remembered)
- Music: full-screen player with large cover art, track info, playback controls and volume, adapting to landscape and portrait
- Video: AirPlay video always plays full screen, with fit / stretch / crop / 100%
- Optional automatic full screen when mirroring starts or music plays
- Picture-in-picture is off by default: many head-unit systems can't interact with the PiP window and leave an unclickable black box; turn it on in the "Car" tab if your unit supports it

### Usage

1. Install the APK, open the app, go to the "Car" tab and turn on the permissions under "System permissions"
2. Tap "Open system hotspot settings" and set up and turn on the hotspot in the system
3. Connect the iPhone to the head unit's hotspot, or put both on the same Wi-Fi
4. On the iPhone, open Control Center, tap Screen Mirroring and choose "车载AirPlay" (the device name can be changed in Settings)

### Build

```bash
git clone --recursive https://github.com/tltangliang/AirPlay2Auto.git
cd AirPlay2Auto
./gradlew assembleRelease
```

Requires JDK 21, Android SDK 37 and NDK 27.0.12077973.

Every push to `main` is built by GitHub Actions, and the APK can be downloaded from the run's Artifacts. To publish a version, commit the tested APK to `main` as `release/AirPlay2Auto-<version>.apk` (optionally with `release/notes-<version>.md` as release notes); Actions creates the `v<version>` tag and publishes the Release.

To have CI sign with a fixed key (so updates install over the old version), add the repository secrets `STORE` (base64 of the keystore), `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

### Notes

- DRM content (for example films in the Apple TV app) is not supported
- License: GPLv3
