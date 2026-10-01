# WeChat UI Probe v0.1

Android 原生只读技术验证应用，用于测试当前 Android + 微信版本暴露的 Accessibility Tree、文本和屏幕坐标。Kotlin + Jetpack Compose；最低 Android 8.0（API 26），编译 / 目标 API 37。候选解析只使用文本、bounds 和父子关系，不依赖微信 View ID。

## 构建与下载

本项目使用 GitHub Actions 云端构建，本机无需安装 JDK、Android SDK 或 Gradle。代码推送 / PR / 手动运行工作流都会执行 `build test lint`。

1. 打开仓库的 **Actions → Android v0.1 checks and APK**。
2. 选择成功的运行，在 **Artifacts** 下载 `WeChatUIProbe-v0.1-debug`。
3. 解压后安装 `app-debug.apk`。APK 带调试签名，供技术验证使用。
4. `verification-reports` 包含单测和 lint 报告；失败运行也尝试上传报告。

云端使用 JDK 17、Gradle 9.8.0、AGP 9.1.1 内置 Kotlin、Compose compiler 2.2.10、Compose BOM 2026.09.00，版本固定。Gradle 分发包带 SHA-256 校验，采用 [Android 官方 AGP 兼容表](https://developer.android.com/build/releases/agp-9-1-0-release-notes) 配置的最低版本要求，并通过本项目完整构建验证。

如已有本机 Android 环境，也可以运行 `./gradlew build test lint`，Windows 使用 `gradlew.bat` 或 `scripts/verify.ps1 -Full`。本项目不安装本机工具链，也不提交本机 SDK 路径。

构建后的 APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。完整构建生成的 release APK 未配置发布签名，不用于此次安装验收。

### 0.1.1 真机诊断修复

修复切换“聊天候选 / 完整节点”时，Compose 延迟读取前一列表却使用当前筛选状态而触发 `No value for order` 崩溃。每行现在保存自己的固定 key 和显示文本，不再在延迟布局回调里读取筛选状态。

如果系统只返回 `className=null`、`visibleToUser=false`、`childCount=0` 的空窗口节点，结果会标为不完整并提示未读到 UI 树，不会把它解释为“聊天没有消息”。Android 13+ 仅在用户主动扫描时清理本服务节点缓存；Android 14+ 的日志补充根节点是否被标记为敏感数据，仍不输出聊天文本。

Android 14+ 可对非辅助工具隐藏敏感无障碍节点（[Android 官方说明](https://developer.android.com/reference/androidx/core/view/accessibility/AccessibilityNodeInfoCompat#setAccessibilityDataSensitive(boolean))）。本应用用于技术验证，保留 `isAccessibilityTool=false`；保护限制下的读取失败也是验证结果。只凭一个空节点不能确认具体限制来源。

### 0.1.2 窗口选择诊断

启用 `flagRetrieveInteractiveWindows`，只在手动扫描时读取当前窗口列表，优先选择拥有输入焦点的应用窗口，无法取得时退回 `rootInActiveWindow`。选中的根节点仍必须属于微信才能遍历；不读取后台微信窗口、事件 source 或其他应用的文本。日志只记录窗口数量、是否找到焦点窗口和根节点结构，便于排除点击悬浮窗造成的活动窗口偏差。

云端缓存本项目的测试调试签名，供后续 APK 原位更新。此签名仅用于技术验证，不用于正式发布。较早未缓存签名的 APK 仍可能需要卸载 Probe 后安装；卸载会删除 Probe 的扫描记录，不影响微信数据。

## 工程结构

```text
app/src/main/java/dev/wechat/uiprobe/
├── MainActivity.kt
├── accessibility/
│   ├── WeChatAccessibilityService.kt
│   ├── AccessibilityStateRepository.kt
│   ├── NodeTreeScanner.kt
│   └── NodeSnapshot.kt
├── parser/
│   ├── WeChatChatParser.kt
│   └── ChatMessageCandidate.kt
├── export/
│   ├── ProbeExporter.kt
│   ├── ProbeStorage.kt
│   └── ExportModels.kt
├── overlay/FloatingProbeService.kt
├── ui/
│   ├── ProbeScreen.kt
│   └── ProbeResultScreen.kt
└── util/RectData.kt
app/src/test/java/dev/wechat/uiprobe/
├── parser/WeChatChatParserTest.kt
└── export/ProbeExporterTest.kt
.github/workflows/android.yml
```

## 真机测试步骤

1. 安装调试 APK，打开 WeChat UI Probe。
2. 点击「打开无障碍设置」，在已下载 / 已安装的服务中开启 **WeChat UI Probe**。
3. 回到应用，确认「Accessibility 已开启 · 已连接」。Android 13+ 的侧载应用可能需要用户在系统应用详情中允许受限制设置；应用不会替你操作。
4. 点击「开启悬浮窗权限」，由用户授予显示在其他应用上层权限。返回应用再点击「显示扫描悬浮按钮」。
5. **手动**打开微信任意聊天页，使微信保持前台。准备左侧、右侧、时间信息以及多条相同文字。
6. 点击悬浮「扫描」一次。按住可拖动，× 关闭；拖动和关闭不会扫描。
7. 手动返回 Probe，核对扫描时间、节点数、文本节点数和候选数。
8. 点击「查看最近一次结果」，分别查看「聊天候选」和「完整节点」。完整节点保留原始 text / contentDescription 和所有布尔属性；parentIndex 表示父节点，index 为从 0 开始的先序遍历编号。
9. 点击「分享 TXT」 / 「分享 JSON」，通过系统分享面板选择接收应用，检查 `wechat_probe_yyyyMMdd_HHmmss_SSS.txt/json`。
10. 确認每个节点的 boundsInScreen；候选按 Y 从上到下排序，相近 Y 按树顺序排列。父子节点文字相同且高度重合时保留更深节点；不同位置的相同消息应保留。
11. 在非微信页面点击悬浮扫描，必须提示非微信，并保持最近一次导出。微信未暴露文本时应导出可见树而不编造消息；没有活动 root 时应明确报错。
12. 不主动扫描时等待、手动切换聊天页，最近扫描时间 / 导出内容应不变。确认没有自动点击、输入、滚动或发送消息。
13. 点击「清除最近一次结果」，确认结果与分享按钮不可用。关闭 / 重启应用后仅能恢复最近一次导出；清除后无可恢复结果。
14. 在系统关闭无障碍服务，悬浮按钮应移除；用 Logcat 的 `WeChatUIProbe` 标签只应看到状态、包名、root 是否存在、数量、耗时和异常类型，不应出现聊天原文。

主界面「获取当前微信 UI」同样是即时扫描入口，但本 App 在前台时活动窗口通常是 Probe，扫描会拒绝该窗口。悬浮按钮解决微信保持前台的触发问题。它由无障碍服务持有窗口，没有额外后台服务或通知。

## 扫描与隐私边界

- 窗口事件仅取 `event.packageName`，不读事件文字 / source / 节点树。为识别「非微信」离开事件，事件监听没有固定 packageNames 过滤；只在主动扫描且活动 root 包名为 `com.tencent.mm` 时遍历节点。
- 没有 `INTERNET`、联系人、通知监听、存储、相机或麦克风权限；没有网络依赖、AI、Hook、Root、OCR 或截图。仅声明可选的 `SYSTEM_ALERT_WINDOW`，无障碍服务受系统 `BIND_ACCESSIBILITY_SERVICE` 权限保护。
- 没有微信节点动作或手势调用。程序只操作本应用窗口和导出文件。
- 原文短暂存在于扫描 / 结果查看所需内存中，长期状态只保存数量与文件名，不保留 AccessibilityNodeInfo 对象。原文只写入用户主动生成的 App 私有导出文件；新成功扫描替换旧快照，启动清理未完成导出。
- 导出位于内部 `files/probe_exports/`，只有 TXT 与 JSON 和不含原文的元信息。可主动清除。应用禁用云备份及设备迁移备份，私有目录不进入系统备份。
- FileProvider 只暴露导出子目录；分享仅通过用户点击的 Android Share Sheet，给予接收应用临时读权限。用户已分享出去的副本由接收应用管理。
- 导出文件可能包含当前页面敏感文字，请在系统分享面板确认接收方。

## 已知限制

- 微信 / Android / 厂商可能不暴露节点或文本，实际可读取性必须在目标真机确认。云端编译、单测和 lint 不等于真机验收。
- 当前页面是否真正是聊天页不能可靠确认。存在可见滚动容器时优先使用其子树；没有滚动容器时使用可见文本，可能包含导航标题等。原始树始终可核对。
- `centerX < width × 0.45` 推测 OTHER，`> width × 0.55` 推测 SELF，其余 CENTER；宽度无效返回 UNKNOWN。CENTER 不能证明是时间 / 系统消息；长气泡可能跨过缓冲区，导致方向误判。
- 屏幕坐标采用当前显示器方向下的全屏尺寸。多窗口、平板、折叠屏、横屏及非全屏微信的方向判断可能失准。
- 扫描只是当前活动窗口、当前暴露的节点，不跨页面、不自动滚动。树实时变化，缺失子节点会输出警告。为避免卡死，保护上限为 15,000 节点 / 深度 128 / 遍历 4 秒；达到上限时 `complete=false` 并明确说明不完整。
- TXT 用于阅读，JSON 是完整结构化来源，保留空值、多行与 Unicode 原文；一次扫描视图不是事务性的微信快照。
- 再次成功扫描或清除结果后，已打开的分享面板可能无法继续读取旧文件。扫描 / 导出失败保留最近一次有效结果。
- 调试签名是 CI 环境生成的；不同干净构建的签名可能不同，更新安装冲突时需先卸载旧调试包。

## v0.1 单测范围

位置缓冲区与边界、Y 排序与相近行的树序、父子去重、相同文字不同位置保留、空白文本 / 仅描述节点过滤、非微信过滤、输入框 / 不可见节点过滤、滚动容器结构及异常父链；JSON 原文与属性保真、TXT 树和候选输出、最近一次文件保留、清除、部分扫描标记、路径约束与中断导出清理。

## 仅记录，不实现的 TODO

群聊发送者、引用消息、图片 / 语音 / 表情识别、OCR / VLM / AI 分析、联系人上下文、摘要、自动上滚、跨页读取、回复生成、自动填写或发送。不开展 v0.2。
