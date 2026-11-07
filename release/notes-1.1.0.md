## AirPlay2Auto 1.1.0

已在实车测试通过。可直接覆盖安装 1.0.0（签名相同，设置保留）。 · Tested on a real head unit. Installs over 1.0.0 (same signature, settings kept).

**中文**
- 应用改名为 AirPlay2Auto，手机上显示的默认设备名也是 AirPlay2Auto
- 默认只作为 AirPlay 音乐接收器；屏幕镜像和 AirPlay 视频默认关闭，可在「车机 → 接收功能」开启
- 音乐默认 2 秒缓冲，等得到丢失数据包的重传，避免断流（可选 0.5–2 秒）；接收时保持 Wi-Fi 高性能模式
- 由车机控制音量：忽略手机端音量调节
- 界面按车机尺寸自动放大（可手动 100%–350%），自动避让车机状态栏和底部快捷栏，可手动调整
- 音乐页：横屏大封面、大号歌曲信息和播放按钮，去掉音量条；支持全屏播放页
- 修复：离开应用后出现无法点击的黑色画中画小窗、无法回到应用（画中画默认关闭）
- 热点改为只在系统设置中操作，去掉相关敏感权限，提升兼容性
- 新增「系统权限」入口（实时显示状态）和「安卓原生设置」快捷入口

**English**
- Renamed to AirPlay2Auto; the default device name on the phone is AirPlay2Auto too
- Music receiver only by default; screen mirroring and AirPlay video are off and can be turned on under "Car → Receiver features"
- 2-second music buffer by default, long enough for lost packets to be resent instead of the audio cutting out (0.5–2 s selectable); Wi-Fi kept in high-performance mode while receiving
- The head unit controls volume: the phone's volume changes are ignored
- UI scaled up automatically for car screens (100%–350% manual), keeps clear of the head unit's status bar and dock, adjustable
- Music view: large cover art, large track info and playback buttons, no volume sliders; full-screen player
- Fixed: leaving the app left an unclickable black picture-in-picture box and the app could not be reopened (PiP now off by default)
- Hotspot is handled in system settings only; the related sensitive permissions are removed for better compatibility
- New "System permissions" section with live status, and shortcuts into native Android settings

下载 `AirPlay2Auto-1.1.0.apk` 安装；`.sha256` 用于校验。 · Download `AirPlay2Auto-1.1.0.apk` and install; use the `.sha256` file to verify.
