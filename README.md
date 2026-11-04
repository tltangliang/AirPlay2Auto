# AirPlay2Auto · 车载 AirPlay 2 接收器

把安卓车机变成 AirPlay 2 接收端：iPhone / iPad / Mac 可通过「屏幕镜像」或 AirPlay 把画面、视频、音乐投到车机上。

基于 [android-airplay-server](https://github.com/jqssun/android-airplay-server)（UxPlay 内核，GPLv3），热点控制思路参考 [DiPlay](https://github.com/shihabal3amri/DiPlay)（GPLv3）。

## 车机版新增功能

**兼容性**
- Android 6.0 及以上（API 23+）
- 同时内置 arm64-v8a、armeabi-v7a、x86、x86_64 四种架构，覆盖绝大多数车机（含 32 位系统和 Intel 方案车机）
- 横屏宽屏车机自动改用侧边导航栏，界面全中文
- 独立包名 `io.github.carairplay`，可与原版共存

**车机热点（「车机」页）**
- 设置热点名称、密码、2.4G/5G 频段，一键开启 / 关闭热点
- 按系统版本自动选择方式：
  - Android 6–7：直接设置并开启热点
  - Android 8–10：通过系统接口开启已保存的热点
  - Android 11+：通过 TetheringManager 开启；已 Root 的车机可用 `cmd wifi start-softap` 按自定义名称/密码开启
  - 以上都不可用时：临时热点（系统随机名称/密码），或跳转系统热点设置
- 需要授予「修改系统设置」权限
- Android 8.0 以上修改热点 **名称/密码** 需要系统权限（应用装为系统应用）或 Root；否则使用系统里已保存的热点

**自动启动 / 保活**
- 开机、快速启动、常见车机 ACC 上电广播自动启动，可设置启动延迟（等待 Wi-Fi 就绪）
- 「假关机/休眠」车机：亮屏、解锁、ACC 广播时自动恢复
- 可填写自定义 ACC 广播
- 开机自动打开界面并退回后台（需「显示在其他应用上层」权限）
- 启动时自动开启热点
- 前台服务 + 定时检查，被系统清理后自动拉起
- 热点 / Wi-Fi 变化后自动重新广播 AirPlay，避免手机搜不到
- 一键申请忽略电池优化

## 使用

1. 安装 APK，打开应用，进入「车机」页
2. 授予「修改系统设置」，设置热点名称和密码，点「保存并开启热点」（或手动在系统里开热点）
3. iPhone 连接车机热点（或与车机连接同一个 Wi-Fi）
4. iPhone 控制中心 →「屏幕镜像」→ 选择「车载AirPlay」

## 编译

```bash
git clone --recursive https://github.com/tltangliang/AirPlay2Auto.git
cd AirPlay2Auto
./gradlew assembleRelease
```

需要 JDK 21、Android SDK 37、NDK 27.0.12077973。推送到 `main` 后 GitHub Actions 会自动编译，APK 在 Actions 的 Artifacts 中下载；推送 `v*` 标签会自动发布 Release。

如需固定签名（便于覆盖升级），在仓库 Secrets 中配置 `STORE`（keystore 的 base64）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

## 说明

- 不支持 DRM 内容（如 Apple TV App 的影片）
- 热点控制依赖车机固件，不同车机效果可能不同
- 许可证：GPLv3
