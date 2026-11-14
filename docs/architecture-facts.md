# 架构与构建事实｜069 碎片时间播放器

> 常驻事实位：只放「改代码前必须知道」的稳定事实。踩坑进根 `agent.md`，风险进 `docs/risks.md`，现势进 `docs/handoff/HANDOFF.md`。
> 最后核对：2026-11-14 13:30（V1.1 回归首跑 + 接手轮设备/矩阵纠偏后）。上限 150 行 / 10,240 字节。

## 1. 这是什么

- 一句话定位：面向个人英语学习者的「碎片时间播放器」——刷牙 2 分钟、滴眼药水 3 分钟等场景一键从上次位置播放本地英语音视频，同时倒计时；到点停媒体、保存进度、只提醒一次。
- 交付形态：原生 Android APK（只做 Android；iOS 未做，后续需单独适配）。
- 部署位置：侧载 APK，包名 `com.wanghoufan.fragmentplayer`；debug 签名，未上架；0.2.8(21) 已登记软件台账 §H（登记 commit 为 `756e1715-dirty`，来源不可签收）。
- 当前版本：**0.2.8 / vc21**，三台已装（OPPO PLG110、Note 11T Pro+、Note 12 Pro）；Note 11T Pro（21091116AC）仍为 0.2.7(18)。源码提交 `a74c8235`，构建 HEAD `8915d0dd`。**该包为 dirty 出包（出包时 HEAD 756e1715 不含 0.2.8），干净重建 SHA 不同，当前不可作正式验收包**；详见 `docs/qa/regression-2026-11-14-v1.1.md`。历史：0.2.0–0.2.7 见版本台账 §H；0.1.9/vc10 为 Note 11 最终基线（AC09 由 0.1.8 继承）。
- 真实课程媒体（2026-11-11 新事实）：用户指定 `/Users/zzymima0000/Documents/1第一章：家庭与朋友`，其中仅 1 个完整视频 `5.Bonus Time - No legs or arms.mp4`（54.149s，1080x2344，H.264+AAC，49,677,154 B）；同目录 5 个 `.baiduyun.p.downloading`（约 678M/616M/614M/842M/494M）是未下完临时文件，不可播放、不计课程。该视频已推送手机 `/sdcard/Movies/FragmentLearning/`，增量扫描后共 8 节，列表第 6 项显示 00:54。**2026-11-14 接手轮：四台在连设备 `/sdcard`（maxdepth 3）均未找到 mp3/mp4；Note 11T Pro 的 App 库 courses=0，真实课程链仍 BLOCKED。**
- V1 完全离线：不依赖 Mac、Wi-Fi/5G、浏览器、账号或云服务。

## 2. 技术栈与目录

| 项 | 值 |
|---|---|
| 语言 / 框架 | Kotlin 2.2.20；Jetpack Compose（BOM 2025.09.00，Material 3）；AGP 8.13.0；Gradle 8.14.3；KSP 2.2.20-2.0.2 |
| 媒体 / 存储 | Media3 ExoPlayer + MediaSessionService 1.11.1；Room 2.7.2；DataStore Preferences 1.1.7；DocumentFile 1.1.0 |
| SDK / JVM | compileSdk = targetSdk = 36；minSdk 26；JDK 17（JVM target 17） |
| 构建命令 | `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon` |
| 启动命令 | 无后端；真机 `adb shell am start -n com.wanghoufan.fragmentplayer/.MainActivity` |
| 测试命令 | `./gradlew :app:testDebugUnitTest --no-daemon`（JVM 单测 76 条，0.2.8 起） |
| 数据库 | Room 本地库（无云库、无网络） |
| APK（当前 0.2.8 / vc21） | `app/build/outputs/apk/debug/app-debug.apk`；versionCode 21 / versionName 0.2.8；17,926,541 B；SHA-256 `779c8433bce45045c07d266dfa20d90bf8a46132047c446286a40be41b01814c`；debug 签名；三台已装（Note 11T Pro 仍 0.2.7）；**dirty 来源，不可签收** |
| APK（0.2.8 干净 HEAD 复核包） | `.local/qa-regression-2026-11-14/app-debug-0.2.8-vc21-rebuild-clean-76b6b449.apk`；SHA-256 `76b6b4492a923b96c0637ec5d692a902c1cd17aef680a2f03b201cd39c77f6cd`；从 HEAD `8915d0dd` 重建；同 vc 不同 SHA，仅作复核证据 |
| APK（历史 0.1.9 / vc10） | 16,513,928 B；SHA-256 `46d2a1cc4dafca834ecc69b42e18bf81b356a93c864aa996da8d05ed2a5503d7`；Note 11 最终基线 |
| 插件 | Kotlin Android / Compose / Serialization、KSP、androidx.room schema 导出到 `app/schemas` |

| 路径 | 职责 |
|---|---|
| `app/src/main/java/.../core/` | 领域模型、仓库/导入/播放/计时接口（解耦层） |
| `.../data/db/`、`.../data/repo/` | Room 实体/DAO/仓库实现、映射 |
| `.../data/settings/` | DataStore 设置（主题、字号、震动、铃声、目录 URI） |
| `.../data/backup/` | JSON 备份编码/恢复（BackupCodec、BackupManager） |
| `.../importer/` | SAF 目录扫描、自然排序、文件指纹、媒体探测 |
| `.../playback/` | Media3 `PlaybackService`（MediaSessionService）与播放引擎 |
| `.../timer/` | 计时状态机、前台服务、闹钟/开机接收器、通知与去重 |
| `.../ui/` | Compose 根装配、ViewModel、三页（计时/课程/设置）、主题 |
| `app/src/test/` | 14 个测试类共 76 条：BackupCodec、Fingerprint、NaturalSort、TimeFlowMath、TimeFlowHistory、AlertPolicy、AlertToneResources、PlaybackHoldPolicy、ShortcutStart、ExitBackGuard 等 |
| `docs/qa/evidence-2026-11-11/` | 本轮真机验收索引（截图在 `build/qa/`，不入库） |

## 3. 数据模型与后台组件

- Room 表：
  - `presets(id, name, durationSec, sortOrder)`
  - `courses(id, uri, displayName, relativePath, durationMs, type, hasAudio, sizeBytes, lastModified, fingerprint, status, sortIndex, note)`，`uri`、`fingerprint` 建索引
  - `checkpoints(courseId PK, positionMs, updatedAt)`：按课程一对一，所有场景共享同一队列与断点
  - `timer_sessions(id, presetId, presetName, targetSec, listenedSec, completed, startedAt, endedAt)`
- DataStore：`theme`（SYSTEM/LIGHT/DARK）、`font_scale`（1.0/1.15/1.3/1.5）、`vibration`、`alert_sound`、`folder_uri`；默认 SYSTEM、1.15、开、开。
- 备份：JSON 只含场景、课程元信息、断点与设置，不含媒体。
- 播放后台：`PlaybackService`（`mediaPlayback`，导出，MediaSessionService）；`TimerService`（`specialUse` 前台，仅 RUNNING 时常驻倒计时通知，状态变化才 notify）；`TimerAlarmReceiver`（精确闹钟收尾）；`BootReceiver`（重启后取消遗留闹钟、清理会话并写「被中断」提示，不假装仍在播放）。
- 通知：`timer_alert`（IMPORTANCE_HIGH，闹钟音频属性，锁屏 PUBLIC，ID 6901）与 `timer_running`（LOW，静音，ID 6900）分渠道；`alertPosted` + synchronized 去重，到点只提醒一次；震动由代码按设置执行，渠道自身不震。
- 项目权限：`POST_NOTIFICATIONS`、`VIBRATE`、`WAKE_LOCK`、`FOREGROUND_SERVICE`（+`MEDIA_PLAYBACK`/`SPECIAL_USE`）、`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`。合并清单另有 Media3 注入的 `ACCESS_NETWORK_STATE`（项目未声明 `INTERNET`）。
- 状态机：`IDLE → PREPARING → RUNNING ⇄ PAUSED → FINISHED`；用 `SystemClock.elapsedRealtime()` 的单调时钟算截止时刻，UI 每秒刷新**不是**时间源；暂停撤销闹钟、恢复重排；到点统一「停媒体→存断点→标记会话完成→提醒一次」。
- 导入：`ACTION_OPEN_DOCUMENT_TREE` 持久授权 + `ContentResolver`/`DocumentFile` 扫描；数字自然排序；重复文件跳过并提示；探测失败标「格式不支持」；目录授权失效提示重新选择，不静默错配。
- 断点：播放中约 3–5 秒节流写，暂停/切课/到点/退出立即写；重启恢复到最近落盘位置但不自动开播。

## 4. 参考项目复用边界（只读借鉴，不改旧项目）

- P068：Android 计时、前台服务、提醒与 Room/备份思路；其 R14（计时准但无可察觉铃声）与 R15（锁屏通知可见性未定位）在本项目**重新独立验证**，不复用 PASS。
- P040：本地音视频播放、缓存与播放位置恢复思路。
- P051：课程扫描、自然排序、逐节播放与续播思路。
- P067：紧凑页面、离线本地数据导入导出思路。
- 边界：不修改 P040/P051/P067/P068 源项目；不改 `docs/plan` 产品计划；引用成熟逻辑不等于直接改造旧工程。

## 5. 关键约定

- 分支与提交：不 push；commit 需编排者明确指令。本轮未 commit。
- 配置来源：`local.properties` 的 `sdk.dir` 指向本机 Android SDK（不入库）；无密钥、无账号、无 `.env`。
- 不可动结构：包名 `com.wanghoufan.fragmentplayer`；Room 表与 DataStore 键名变更需迁移；通知渠道 ID 变更需兼容旧安装。
- 媒体纪律：手机侧本地 SAF 读取，不在 App 内二次复制课程；源课程只读，不移动/删除/改名/转码。

## 6. 验证与门禁

- 本地自测：`./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`；2026-11-14 强制重跑 **76/76**（14 类，0 失败）。历史 0.1.x 为 21/21；0.2.3 起 25、0.2.6 起 64、0.2.7 起 71、0.2.8 起 76。
- 独立审查：`docs/review/CODE_REVIEW-2026-11-11.md`（有条件通过，0 P0 / 4 P1 / 21 P2+P3）；P1 静态复核 `docs/review/CODE_REVIEW-P1-VERIFY-2026-11-11.md`（P1-1～P1-4 全 CLOSED，无新增 P0/P1；未构建、未真机）。
- 记账后与收工前：`node scripts/model/check-ledger.mjs docs/model`，须 `LEDGER-OK`。
- 真机基线：**Redmi Note 12 Pro（22101316C，Android 14 / SDK 34，SCHEDULE_EXACT_ALARM=deny）为 2026-11-14 回归机**；同轮接手在 **Redmi Note 11T Pro+（23054RA19C，Android 15 / SDK 35）** 补首屏/安全区；历史 Note 11 5G（Android 11 / SDK 30，精确闹钟无需权限）、OPPO PLG110（Android 17 / SDK 37）、Note 11T Pro（21091116AC，Android 11，装 0.2.7）。
- 已知不稳定 / 未验证：真实课程素材四台在连设备均未找到（Note 11T Pro 库内 courses=0），播放/断点/锁屏有声 BLOCKED；AC08 仅 8/10（本轮再补 1 轮，仍非每状态 10 轮）；锁屏首屏未见通知、下拉可见（Note 11T Pro+ 通知权限四项全关）；精确闹钟未授权；勿扰与 OEM 省电未测；**返回键无页面栈（完成页/设置页/计时中按返回不回上一级，P1 未修）**；debug 签名不可发布；AC12 未签收；现包 dirty 不可签收。
- 设备踩坑：MIUI DocumentsUI 文件夹卡片对 `input tap` 无响应，须用 UiAutomator 无障碍 ACTION_CLICK（`PickFolderTest` 已完成目录选择）；测试 APK 用完须卸载，避免与主 App 双进程干扰。