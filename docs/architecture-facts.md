# 架构与构建事实｜069 碎片时间播放器

> 常驻事实位：只放「改代码前必须知道」的稳定事实。踩坑进根 `agent.md`，风险进 `docs/risks.md`，现势进 `docs/handoff/HANDOFF.md`。
> 最后核对：2026-11-11（Note 11 主验收、AC09 闭环与 0.1.9 最终构建后）。上限 150 行 / 10,240 字节。

## 1. 这是什么

- 一句话定位：面向个人英语学习者的「碎片时间播放器」——刷牙 2 分钟、滴眼药水 3 分钟等场景一键从上次位置播放本地英语音视频，同时倒计时；到点停媒体、保存进度、只提醒一次。
- 交付形态：原生 Android APK（只做 Android；iOS 未做，后续需单独适配）。
- 部署位置：侧载 APK，包名 `com.wanghoufan.fragmentplayer`；debug 签名，未上架、未登记发版台账。
- 当前版本：**0.1.9 / versionCode 10 为最终构建，已装 Redmi Note 11 5G**（相对 0.1.8 只清理调试日志与无用组件，未改行为；AC09 由 0.1.8 继承）。历史：0.1.8 / vc9 为 AC09 根级横幅修复版（真机首屏可见）；0.1.3–0.1.7 为 AC09 失败/诊断尝试；0.1.2 / vc3 为 Note 11 本轮主要验收基线；0.1.1 / vc2 已构建未安装；0.1.0 / vc1 已装 OPPO PLG110 完成首轮验收。
- 真实课程媒体（2026-11-11 新事实）：用户指定 `/Users/zzymima0000/Documents/1第一章：家庭与朋友`，其中仅 1 个完整视频 `5.Bonus Time - No legs or arms.mp4`（54.149s，1080x2344，H.264+AAC，49,677,154 B）；同目录 5 个 `.baiduyun.p.downloading`（约 678M/616M/614M/842M/494M）是未下完临时文件，不可播放、不计课程。该视频已推送手机 `/sdcard/Movies/FragmentLearning/`，增量扫描后共 8 节，列表第 6 项显示 00:54。
- V1 完全离线：不依赖 Mac、Wi-Fi/5G、浏览器、账号或云服务。

## 2. 技术栈与目录

| 项 | 值 |
|---|---|
| 语言 / 框架 | Kotlin 2.2.20；Jetpack Compose（BOM 2025.09.00，Material 3）；AGP 8.13.0；Gradle 8.14.3；KSP 2.2.20-2.0.2 |
| 媒体 / 存储 | Media3 ExoPlayer + MediaSessionService 1.11.1；Room 2.7.2；DataStore Preferences 1.1.7；DocumentFile 1.1.0 |
| SDK / JVM | compileSdk = targetSdk = 36；minSdk 26；JDK 17（JVM target 17） |
| 构建命令 | `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon` |
| 启动命令 | 无后端；真机 `adb shell am start -n com.wanghoufan.fragmentplayer/.MainActivity` |
| 测试命令 | `./gradlew :app:testDebugUnitTest --no-daemon`（JVM 单测 21 条） |
| 数据库 | Room 本地库（无云库、无网络） |
| APK（当前最终构建 0.1.9） | `app/build/outputs/apk/debug/app-debug.apk`；versionCode 10 / versionName 0.1.9；16,513,928 B；SHA-256 `46d2a1cc4dafca834ecc69b42e18bf81b356a93c864aa996da8d05ed2a5503d7`；debug 签名；**已安装 Redmi Note 11 5G** |
| APK（历史 0.1.8 / AC09 修复版） | versionCode 9；SHA-256 `ba29bdcf9d07c3e6e78f6b04b58971c312b8d322aa684d7a339e4050c94a1f44`；真机验证中断提示首屏可见；0.1.9 继承其行为 |
| APK（历史 0.1.2 / Note 11 验收基线） | versionCode 3；16,513,928 B；SHA-256 `9e7be169e9067bd588d1b7682951bf01957bd0d6c82cababfd3ea4118c7efdb2`；注意与 0.1.9 字节数相同但哈希不同，认包以 SHA 为准 |
| APK（历史 0.1.0 / OPPO 首轮） | versionCode 1；16,984,755 B；SHA-256 `450d3587bc3877b404c861e4ff7083fb9bab4bf238bb13d7e17cc47e9050e17f`；已装 OPPO PLG110 |
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
| `app/src/test/` | BackupCodec 5、Fingerprint 4、NaturalSort 5、TimerEngine 7（共 21） |
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

- 本地自测：`./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`（0.1.0、0.1.1、0.1.2、0.1.8、0.1.9 均 21/21 通过；0.1.9 同命令构建 vc10）。
- 独立审查：`docs/review/CODE_REVIEW-2026-11-11.md`（有条件通过，0 P0 / 4 P1 / 21 P2+P3）；P1 静态复核 `docs/review/CODE_REVIEW-P1-VERIFY-2026-11-11.md`（P1-1～P1-4 全 CLOSED，无新增 P0/P1；未构建、未真机）。
- 记账后与收工前：`node scripts/model/check-ledger.mjs docs/model`，须 `LEDGER-OK`。
- 真机基线：Redmi Note 11 5G（21091116AC，Android 11 / SDK 30，媒体音量 8，通知可用，精确闹钟在 Android 11 无需权限）；OPPO PLG110（Android 17 / SDK 37）为历史首轮基线，当前仍装 0.1.0。
- 已知不稳定 / 未验证：真实课程仅 1 个完整视频（其余 5 个为百度网盘未下完临时文件）；AC08 仅 6/10（前台/后台/锁屏各 2 轮，非每状态 10 轮）；锁屏通知画面可见性未截到；备份 JSON 经系统 UI 导入未测；AC02/05/07/10 部分或未测；勿扰与 OEM 省电未测；debug 签名不可发布；AC12 未签收。
- 设备踩坑：MIUI DocumentsUI 文件夹卡片对 `input tap` 无响应，须用 UiAutomator 无障碍 ACTION_CLICK（`PickFolderTest` 已完成目录选择）；测试 APK 用完须卸载，避免与主 App 双进程干扰。