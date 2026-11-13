# 069 碎片时间播放器｜2026-11-11 真机验收证据索引

> 本文件覆盖 2026-11-11 全天的两段真机验收：**① 凌晨 OPPO PLG110 首轮 0.1.0 验收**（原始记录保留在 §8）；**② 白天 Redmi Note 11 5G 主验收**（0.1.2 基线 → AC09 修复链 0.1.3–0.1.8 → 最终 0.1.9）；**③ 下午全量缺口补测**（AC02/05/06/07 与 AC08 加轮，见 §4.6/§4.7）；**④ 14:50 开启系统发声权限后锁屏复测**（锁屏提醒可见且有闹钟音，见 §4.4/§4.7）。
> 截图与日志在 `build/qa/`、`build/codearts/`（`build/` 不入库），本文只写相对路径。
> 状态口径：**PASS**＝有证据且判据满足；**PARTIAL**＝只覆盖判据的一部分；**FAIL**＝有证据证明不满足；**未测**＝没有执行，**未测 ≠ 通过**。
> 不把 P068／P040／P051／P067 的历史 PASS 当作本项目 PASS。

## 1. 环境

| 项 | 值 |
|---|---|
| 主机 | Mac；JDK 17.0.20.1；`ANDROID_HOME=~/Library/Android/sdk` |
| 主验收真机 | Redmi Note 11 5G（21091116AC），Android 11 / SDK 30（序列号不记录） |
| 历史验收真机 | OPPO PLG110，Android 17 / SDK 37（见 §8） |
| 媒体音量 | 8（Note 11 本轮） |
| 通知 | 已授权可用；振动权限与锁屏通知权限本来就开；**发声权限**此前关闭，14:50 在 MIUI「设置 → 碎片时间 通知」中手动打开；精确闹钟在 Android 11 无需单独权限 |
| 网络 | 本轮不联网；App 无 `INTERNET` 权限，媒体为手机本地 SAF 文件 |
| 包名 | `com.wanghoufan.fragmentplayer`；debug 签名 |

## 2. 构建与单测

- 命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`
- 结果：`BUILD SUCCESSFUL`；单测 **21/21 通过**（BackupCodec 5、Fingerprint 4、NaturalSort 5、TimerEngine 7）。
- 设备内 `BackupRoundTripTest`：导出 / peek / 导入往返 **PASS**。

### 2.1 本轮版本链（以 SHA-256 认包）

| 版本 / vc | SHA-256 | 状态与作用 |
|---|---|---|
| **0.1.9 / vc10** | `46d2a1cc4dafca834ecc69b42e18bf81b356a93c864aa996da8d05ed2a5503d7` | 白天轮最终产物（当前 0.2.1 见 §14）；16,513,928 B；已装 Note 11 5G；相对 0.1.8 仅清理调试日志与无用组件，不改行为 |
| 0.1.8 / vc9 | `ba29bdcf9d07c3e6e78f6b04b58971c312b8d322aa684d7a339e4050c94a1f44` | **AC09 根级横幅修复版**；真机验证中断提示首屏可见 |
| 0.1.7 / vc8 | — | AC09 失败/诊断尝试：提示只挂计时页或首帧/窗口未显示 |
| 0.1.6 / vc7 | `6a5cf38c…e180` | AC09 失败尝试 |
| 0.1.5 / vc6 | `30f08fda…355a` | AC09 失败尝试 |
| 0.1.4 / vc5 | `a43dcc1e…d858` | AC09 失败尝试 |
| 0.1.3 / vc4 | `29cab50e…aec4` | AC09 失败尝试 |
| **0.1.2 / vc3** | `9e7be169e9067bd588d1b7682951bf01957bd0d6c82cababfd3ea4118c7efdb2` | **Note 11 本轮主要验收基线**；16,513,928 B |
| 0.1.1 / vc2 | `13bcfd820e7cdfaeb3ccd14b9a564f2a54d55ca501d07978c44763a77a7f966d` | 历史：四项缺陷修复构建（未安装） |
| 0.1.0 / vc1 | `450d3587bc3877b404c861e4ff7083fb9bab4bf238bb13d7e17cc47e9050e17f` | 历史：OPPO PLG110 首轮验收；当前仍装于 OPPO |

> 0.1.2 与 0.1.9 字节数同为 16,513,928 B 但哈希不同，**认包一律以 SHA-256 为准**，不得以大小判同包。
> 「0.1.x / vcx」与 Note 11 真机的对应关系只代表本轮实际安装过的包；未在表内标注的文件产物不代表已验证。

## 3. 真实课程媒体（新事实）

- 用户指定目录：`/Users/zzymima0000/Documents/1第一章：家庭与朋友`
- 其中只有 **1 个完整视频** `5.Bonus Time - No legs or arms.mp4`：
  - 时长 **54.149s**；分辨率 **1080x2344**；编码 **H.264 + AAC**；大小 **49,677,154 B**。
- 同目录另 5 个 `.baiduyun.p.downloading`（约 **678M / 616M / 614M / 842M / 494M**）是百度网盘**未下完的临时文件**，不可播放，**不得计为课程**。
- 已把完整视频推送到手机 `/sdcard/Movies/FragmentLearning/`；App「更新课程目录」增量扫描后共 **8 节**，真实视频在列表**第 6 项**，显示 **00:54**；原 7 个样本仍在。
- **下午全量缺口补测后仍为 8 节**：把该视频改名 `.mp4.bak` → 「更新课程目录」显示「媒体缺失」→ 改回原名 → 「更新课程目录」同一 id **8** 恢复 **READY**，总数仍 **8**、无重复行（DB 已核对，见 §4.6）。
- 该视频实播：`media_session` state=PLAYING、位置推进；灭屏 10 秒位置 **6.2s→30.2s**，音频 `usage=USAGE_MEDIA` 持续。
- 结论：Note 11 上**真实完整视频只有 1 个**（8 节中 7 节为合成样本），真实多节课程验收在 Note 11 未做；Note 12T Pro 的 13 节真实课程 MP3 见 §11。

## 4. Redmi Note 11 5G 验收（有证据项）

### 4.1 导入（AC-01 / AC-06 相关）

| 项 | 状态 | 说明 |
|---|---|---|
| SAF 目录导入 8 节、自然排序正确 | PASS | 样本 7 节 + 真实视频 1 节 |
| 坏 MP3 / 损坏 MP4 提示 | PASS | 标「格式不支持」 |
| 新增真实视频增量加入 | PASS | 「更新课程目录」后 8 节，旧记录与断点保留 |
| 真实 5–10 节课程 | 未测（Note 11）／PASS（Note 12T Pro 13 节 MP3） | Note 11 只有 1 个完整视频，见 §3；Note 12T Pro 13 节见 §11 |
| 失效媒体标记与恢复（改名 `.mp4.bak`） | PASS | 该行显示「媒体缺失」、进度 85% 保留；改回原名重扫后同一 id 8 恢复 READY、总数 8、无重复（§4.6） |
| SAF 授权撤回后重新授权 | 未测 | 未执行目录撤回/授权失效重授权 |

### 4.2 无音轨提示（AC-04）

| 项 | 状态 | 说明 |
|---|---|---|
| 课程页「无音轨」标签 | PASS | 0.1.1 修复已在 0.1.2 基线真机可见 |
| 点播 Snackbar | PASS | 提示无音轨 |
| 计时页顶部提示条 | PASS | 文案「这个视频没有音轨，锁屏后听不到声音」 |

### 4.3 计时 / 播放（AC-01 / AC-02 / AC-05 部分）

| 项 | 状态 | 证据 / 说明 |
|---|---|---|
| 真实视频有声播放 | PASS | `media_session` PLAYING，位置推进 |
| 暂停计时剩余冻结 | PASS | `remainingMs=95550` 4 秒不变 |
| 继续计时恢复 | PASS | 恢复 RUNNING，媒体 PLAYING |
| 仅暂停播放器（计时继续） | PASS | 媒体 PAUSED，倒计时 **23.9s→20.9s** 继续 |
| 点「下一课」倒计时不重置 | PASS | 单次观察 `118989→114898ms` |
| 提前结束按实际用时保存 | PASS | 显示「本次 1 分钟，已按实际用时保存」 |
| AC-02 多场景连续断点 | PASS | 刷牙开始→提前结束→首页当前课程 10-（100%）→再启动滴眼药水→计时页当前课程仍 10-、剩余 02:57；两个场景共享同一队列/断点（§4.6） |
| AC-05 切课不重置计时 | PASS | 滴眼药水计时中 remaining 164787ms → 点「上一课」3 秒后 160989ms，媒体 PLAYING（active item 4）；拖动进度与播完自动下一节未单独复测（§4.6） |

### 4.4 到点提醒（AC-03 / AC-08）

| 项 | 状态 | 证据 / 说明 |
|---|---|---|
| 前台 2 分钟到点 | PASS（单轮） | 单次通知 **6901**，importance=4，category=alarm，flags=0x10（无 SILENT）；channel `timer_alert` 使用 `alarm_alert`/`USAGE_ALARM`；音频播放器 `usage=USAGE_NOTIFICATION`；震动 Waveform `0/500/300/500/300/500`，uid=10328，包名 `com.wanghoufan.fragmentplayer` |
| 后台 10 秒到点 | PASS（单轮） | 12:26:08 震动 |
| 锁屏 10 秒到点 | PASS（单轮） | 灭屏 Asleep；12:26:55 震动、通知 6901 |
| **下午补测 14:36:57 前台 Test10** | PASS（单轮） | RUNNING→FINISHED；单次通知 **6901**，importance=4，category=alarm，flags=0x10（无 SILENT）；震动 Waveform `0/500/300/500/300/500`，uid=10328，包名 `com.wanghoufan.fragmentplayer` |
| **下午补测 14:37:22 后台 Test10** | PASS（单轮） | 按 HOME 退后台后到点；通知与震动同上（6901 / 0/500/300/500/300/500，uid=10328） |
| **下午补测 14:37:50 锁屏 Test10** | PASS（单轮） | 灭屏后 `dumpsys` Asleep 状态到点；通知与震动同上（6901 / 0/500/300/500/300/500，uid=10328） |
| **下午补测 14:39 锁屏（亮屏截图）** | PASS（单轮，铃声与震动）／画面**发声权限关闭时未显示** | 该轮发声权限仍为关闭；到点铃声与震动均出现，锁屏亮屏截图 `build/qa/100-lockscreen-notification.png` 只见媒体通知与 USB 通知，6901 画面不可见。**该状态已由 14:50 打开系统发声权限解决**，不作为最终 FAIL 结论 |
| **下午复测 14:50 锁屏 Test10（发声权限已开）** | **PASS（单轮，画面可见 + 闹钟音）** | 10 秒计时后锁屏到点：通知 **6901** importance=4、category=alarm、flags=0x10（无 SILENT）；`dumpsys audio` 出现 `MediaPlayer usage=USAGE_ALARM state:started`（`build/qa/103-lock-sound.txt`）；锁屏亮屏截图 `build/qa/104-lock-alert-visible.png` **清楚显示「时间到了 / 「Test10」00:10 已到」**，同时有媒体通知 |
| 前台/后台/锁屏各 10 轮矩阵（AC-08） | **PARTIAL（8/10）** | 上午 3 轮（各 1）＋下午 14:36:57/14:37:22/14:37:50 各 1（共 7）＋14:50 锁屏 1＝**共 8 轮**（前台 2 / 后台 2 / 锁屏 4）；**不是每状态 10 轮**，按「总计」口径记 8/10；**不得写全通过** |
| 每轮只发一次 6901 / 完成后 phase=FINISHED / 无残留 timer session | PASS（8 轮均如此） | 8 轮到点各只发一次通知 6901，importance=4、category=alarm、flags=0x10（无 SILENT），完成后 phase=FINISHED，无残留 timer session |
| 锁屏通知「画面可见性」 | **PASS（前提：系统发声权限开启）** | 14:50 在 MIUI「设置 → 碎片时间 通知」打开发声权限后，锁屏亮屏截图 `build/qa/104-lock-alert-visible.png` 可见「时间到了 / 「Test10」00:10 已到」；同时 `MediaPlayer usage=USAGE_ALARM state:started`。**关闭发声权限时画面不出现**（14:39 轮 `build/qa/100`），即该可见性依赖系统「发声权限 / 锁屏通知」开启，不是通知未发出 |

### 4.5 主题 / 字号 / 备份（AC-10）

| 项 | 状态 | 说明 |
|---|---|---|
| 深色 + 特大（1.5）切换 | PASS | 切换成功 |
| 强停重启后保持 | PASS | 深色与 1.5 保持 |
| 计时页大号倒计时未裁切 | PASS | 本机实测无裁切 |
| JSON 备份导出（SAF） | PASS | `fragment-time-backup-20261111.json`，**8,535 B**，含 `version` / `settings`（DARK、fontScale 1.5）/ `folderUri` / `presets` / `courses` |
| 设备内导出/peek/导入往返 | PASS | `BackupRoundTripTest` PASS |
| **通过系统 UI 选择 JSON 导入** | **未测** | 只测了设备内测试入口，不得写成 UI 已通过；MIUI DocumentsUI 的文件卡/文件夹卡对 `input tap` 不响应（按钮/滑动正常），需无障碍 `ACTION_CLICK`，本轮未再引入测试包 |
| 场景编辑 / 删除（AC-10） | 未测 | 未逐项验收 |

### 4.6 全量缺口验收（AC-02 / AC-05 / AC-06 / AC-07）

> 时间：2026-11-11 下午；机型 Redmi Note 11 5G；版本 0.1.9 / vc10；数据库课程 **8 节**（7 样本 + 真实视频 `5.Bonus Time - No legs or arms.mp4`，DB id **8**）。

| AC | 状态 | 判据与实测证据 |
|---|---|---|
| AC-02 多场景共用断点 | **PASS** | 刷牙开始→提前结束→首页「当前课程 10-（100%）」→再启动滴眼药水→计时页当前课程仍「10-」、剩余 **02:57**；证明两个场景共享同一队列/断点，不因切场景回到第一条 |
| AC-05 切课不重置计时 | **PASS** | 滴眼药水计时中 `remaining=164787ms`，点「上一课」后 3 秒 `remaining=160989ms`，媒体 **PLAYING**（active item 4）；倒计时连续推进、未重置 |
| AC-06 失效媒体标记与恢复 | **PASS** | 把 `5.Bonus Time - No legs or arms.mp4` 改名 `.mp4.bak` → 「更新课程目录」→ 该行显示「**媒体缺失**」、进度 **85%** 保留；改回原名 → 「更新课程目录」→ 同一 id **8** 恢复 **READY**，总数仍 **8**、无重复行（DB 已核对） |
| AC-07 离线完成一轮 | **PASS** | `airplane_mode_on=1` → 强停冷启动 → 课程列表可见 → 播放真实视频：`media_session` **PLAYING**、`AudioTrack usage=USAGE_MEDIA state:started`；随后已关闭飞行模式（`airplane_mode_on=0`） |

- 本轮过程说明：期间另一 App（`com.stretchroutine.v1`）曾在前台，导致一次「更新课程目录」点击未落到本 App；已纠正，最终数据以 DB 为准（8 节、Bonus 为 id 8 READY）。
- 未覆盖但**不属于上述 PASS 判据**的项：SAF 授权撤回后的重新授权流程、AC-05 拖动进度与播完自动下一节，均仍**未测**（见 §10）。

### 4.7 AC08 累计 8/10 与锁屏提醒可见（发声权限已开）

- 累计 **8 轮**：前台 **2**、后台 **2**、锁屏 **4**（含 14:39 与 14:50 两次锁屏亮屏截图）。
- 每轮共同事实：通知 **6901 单次**、`importance=4`、`category=alarm`、`flags=0x10`（无 SILENT）；应用震动波形均出现。
- **14:39 轮（发声权限关闭）**：锁屏亮屏截图 `build/qa/100-lockscreen-notification.png` 只见媒体通知与 USB 通知，6901 画面不可见。这是系统「发声权限」关闭状态下的表现，不作为最终 FAIL 结论。
- **14:50 轮（发声权限开启，结论性一轮）**：在 MIUI「设置 → 碎片时间 通知」把**发声权限**从关闭打开后，Test10 10 秒计时锁屏到点——通知 6901 importance=4/category=alarm/flags=0x10 无 SILENT；`dumpsys audio` 出现 `MediaPlayer usage=USAGE_ALARM state:started`（`build/qa/103-lock-sound.txt`）；锁屏亮屏截图 `build/qa/104-lock-alert-visible.png` **清楚显示「时间到了 / 「Test10」00:10 已到」**，同时有媒体通知。
- 判定：**锁屏到点提醒可见且有闹钟音（PASS，前提＝系统发声权限与锁屏通知权限开启）**；AC08 整体仍记 **PARTIAL（8/10）**——未达每状态 10 轮，**不得写全通过**。
- 仍缺：每状态 10 轮未达；勿扰 / OEM 省电分级未测。

## 5. AC09 修复链（中断提示首屏可见）

| 版本 | 结果 | 现象 |
|---|---|---|
| 0.1.3 / vc4 | FAIL | 提示只在计时页挂载时出现，首屏不可见 |
| 0.1.4 / vc5 | FAIL | 同上，首帧/窗口未显示 |
| 0.1.5 / vc6 | FAIL | 同上 |
| 0.1.6 / vc7 | FAIL | 同上 |
| 0.1.7 / vc8 | FAIL（诊断轮） | 提示只挂计时页或首帧/窗口未显示 |
| **0.1.8 / vc9** | **PASS** | 改为**根级内联横幅**；强停重启首屏可见 |
| 0.1.9 / vc10 | PASS（继承） | 只清理调试日志与无用组件，未改行为 |

0.1.8/0.1.9 真机验收事实：
- 强停重启后**首屏**出现根级横幅：「上次计时被中断 / 上次计时被系统中断，中断前已进行 00:04」。
- 点「知道了」清 prefs；**再次强停重启不重复**提示。
- 结论：AC-09 的「杀进程/强停后中断提示」判据 **PASS**；来电、耳机拔出、系统重启后断点恢复仍属未测项，见 §9。

## 6. 最终产物 0.1.9

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- versionCode **10** / versionName **0.1.9**；大小 **16,513,928 字节**
- SHA-256 **`46d2a1cc4dafca834ecc69b42e18bf81b356a93c864aa996da8d05ed2a5503d7`**（本轮收工前只读复核一致）
- 已安装到 Redmi Note 11 5G；单测 21/21。
- 变更范围：相对 0.1.8 只清理调试日志与无用组件，**未改行为**；AC09 结论由 0.1.8 直接继承。

## 7. 测试辅助与踩坑

- MIUI DocumentsUI 的**文件夹卡片**对 `input tap` 无响应（按钮、滑动正常）；改用 UiAutomator 无障碍 `ACTION_CLICK` 的 `PickFolderTest` 完成目录选择。
- 测试 APK 用完已**卸载**，避免与主 App 双进程干扰。
- 设备侧备份 JSON 落盘路径与大小以实际文件为准（8,535 B）；截图/日志均在 `build/qa/`，不入库。

## 8. 历史：OPPO PLG110 首轮 0.1.0 验收（原始记录保留）

> 验收时用户睡眠：media 流 speaker 音量 0，App 内铃声与震动关闭；**未改系统闹钟音量（12）**。测试后手机进入安全锁屏。以下状态只对 0.1.0 有效，不代表后续版本。

| 项 | 状态 | 证据 |
|---|---|---|
| OPPO 安装引导并安装成功 | PASS | `02-install-guide.png`、`03-install-confirm.png` |
| 首屏两场景卡 + 空课程引导 | PASS | `04-home.png`、`05-courses-empty.png` |
| SAF 选择 `Movies/FragmentLearning` | PASS | `06-saf-picker.png`、`07-saf-browse.png` |
| 导入 7 节并自然排序 1,2,3,4,5,6,10 | PASS | `08-courses-imported.png`、`12-courses-scrolled.png` |
| 坏 MP3 与损坏 MP4 标「格式不支持」 | PASS | `09-mp3-reason.png`（`3-测试音频.mp3`，ffprobe `Header missing`） |
| 追加 `6-测试音调.mp3` 增量更新 | PASS | `10-courses-rescan.png`、`11-rescan2.png` |
| 音频 / 视频播放 | PASS | `13-audio-playing.png`；`media_session state=PLAYING` |
| 媒体流静音 | PASS | `dumpsys audio`：media 流音量 0 |
| 锁屏后台继续播放 | PASS（仅位置推进） | `35-lockscreen.png`＋`dumpsys`：0→8957ms，`mWakefulness=Dozing` |
| 锁屏通知可见性 | 未测 | 仅 `vis=PUBLIC`，截图被系统保护全黑 |
| 无音轨提示 | FAIL（0.1.0） | 0.1.1/0.1.2 已修，见 §4.2 |
| 深色冷启动白闪 | FAIL（0.1.0） | 0.1.2 补 `values-night`，Note 11 已随基线复测 |
| PREPARING 取消 / 启动失败残音 | 未测（0.1.0） | 0.1.2 已修，Note 11 基线未单独复现该时序 |
| 2 分钟场景启动后播放 | PASS | `22-timer-running.png`、`26-timer-active.png` |
| 暂停 / 继续计时 | PASS | `30-paused.png`、`31-paused-later.png`、`32-resumed.png` |
| 仅暂停播放器（计时继续） | PASS | `33-media-paused-timer-running.png` |
| 到点收尾与唯一提醒 | PASS（单轮） | `25-timer-finished.png`；通知 6901 一次，`flags=AUTO_CANCEL\|SILENT`，媒体 STOPPED |
| 提前结束按实际用时保存 | PASS | `34-early-finish.png` |
| 通知权限 / 精确闹钟 | PASS | `16~19-*.png`；OPPO 默认忽略通知，已手动打开 |
| 首屏小屏/大字体无截断 | 未测 | OPPO 轮未做矩阵 |
| 主题三态 / 字体四档 / JSON 备份 | 未测 | OPPO 轮未执行（Note 11 轮已部分补，见 §4.5） |
| AC-02 / AC-05 / AC-07 / AC-08 / AC-09 / AC-10 | 未测 | OPPO 轮均未执行 |

## 9. 四层报告（不合并为一个 PASS）

1. **代码完成**：0.1.9 已构建（vc10，16,513,928 B，SHA `46d2a1cc…03d7`，单测 21/21）。
2. **包已安装**：0.1.9 已装 Redmi Note 11 5G；OPPO PLG110 仍为 0.1.0。
3. **真机链路**：**PARTIAL**——Note 11 已完成导入、无音轨提示、真实视频播放、计时暂停/继续/提前结束、AC02 跨场景断点、AC05 切课不重置、AC06 失效标记与恢复、AC07 飞行模式离线播放、**8 轮到点**（前台 2/后台 2/锁屏 4）、主题/字号、备份导出、AC09 首屏横幅；缺口：AC08 仅 8/10（非每状态 10 轮）、备份导入 UI 未测、AC10 场景编辑/删除未逐项。锁屏提醒在系统发声权限开启后**可见且有闹钟音**（14:50，`build/qa/104-lock-alert-visible.png`），此项不再记为缺口。
4. **用户签收**：**未签收**（首次发布尚未发生）。

## 10. 未完成清单（未测 ≠ 通过）

- **AC08**：现 **8/10**（前台 2/后台 2/锁屏 4），**不是每状态 10 轮**；锁屏提醒可见性已解决——14:50 开启系统发声权限后，截图 `build/qa/104-lock-alert-visible.png` 可见到点提醒且有 `USAGE_ALARM` 闹钟音（14:39 `build/qa/100` 对应发声权限关闭状态，已不构成 FAIL 结论）；勿扰/OEM 省电未测。
- **真实课程**：Note 11 只有 1 个真实完整视频（其余 5 个为百度网盘未下完临时文件）；Note 12T Pro 13 节真实 MP3 见 §11。
- **备份导入系统 UI** 未测（仅设备内测试入口往返 PASS）；MIUI DocumentsUI 文件卡需无障碍 `ACTION_CLICK`。
- **SAF 授权撤回后的重新授权** 未测（AC-06 的失效标记/恢复已由改名校验 PASS）。
- **AC-05 拖动进度、播完自动下一节** 未单独复测；已 PASS 部分为切课/上一课不重置计时。
- **AC10** 场景编辑/删除未逐项；主题/字号已部分通过。
- **AC11** OPPO 仍为 0.1.0，最终 0.1.9 只在 Note 11 验证。
- **AC12** 用户最终签收未发生。

## 11. 真实素材全量转 MP3 导入轮（2026-11-11，Note 12T Pro）

- 素材来源（只读）：`/Users/zzymima0000/Documents/1第一章：家庭与朋友`，13 个文件（11 mp4 + 2 mov），共 8.8 GB，全部 h264 + aac；按用户要求只导入音频，不导入视频。
- 转码：`ffmpeg -vn -c:a libmp3lame -q:a 2`，输出 `.local/media/第一章家庭与朋友/`（`.local/` 已 gitignore），13 个 MP3 共 87 MB；逐个 ffprobe 核对，**13/13 时长与源完全一致**，44.1 kHz 立体声，码率 84–111 kbps。
- 推送：Note 12T Pro（23054RA19C / Android 15 / 24 GB 空闲）`/sdcard/Movies/第一章家庭与朋友/`，13 个文件 91,559,234 字节；手机端与本地 `md5sum *.mp3` 集合哈希一致（`1392fd4f…dcac`）。
- 版本：先装 0.1.8/vc9（SHA `ba29bdcf…1f44`）完成导入；随后覆盖安装 0.1.9/vc10（SHA `46d2a1cc…03d7`），数据保留。

| 判据 | 结果 | 证据 |
|---|---|---|
| 13 节全部导入、无遗漏 | PASS | `media-mp3-2026-11-11/01-课程页-13节.png`（共 13 节 + 1–6 节） |
| 自然排序 1–13，含缺分隔点文件名 | PASS | `02-课程页-7到13节.png`：7、8朋友聚会（上）、9、10、11、12、13 |
| 增量重扫无重复 | PASS | 重扫后仍「共 13 节」，无重复条目 |
| 旧进度不丢 | PASS | 播放 00:41 后提前结束 → 已学到 13% → 重扫仍 13% → 覆盖安装 0.1.9 后仍 13%（`03-重扫后-13节-进度13%.png`） |
| 真实媒体可播 | PASS | 计时页播放 `1.家庭时光（上）.mp3`，位置 00:41 / 08:26，PlaybackService 前台 |
| 失效重授权 | 未测 | 本轮未撤销 SAF 授权 |

- 未覆盖：AC-02 跨场景断点链（已由 §4.6 在 Note 11 补 PASS）、AC08 8/10 仍未达每状态 10 轮；本轮只证导入/排序/去重/进度四条。锁屏提醒可见性已由 14:50 发声权限轮解决（§4.7）。

## 12. 测试素材清理记录（2026-11-11 15:15）

- 范围：仅 Redmi Note 11 5G（WGBMXWDIAIFMPFCQ）与 OPPO PLG110（3B15CW00KND00000）；Note 12T Pro（UKCESWB67PUO7LPB）只读核对，未执行任何写操作。
- 前置清单（`adb -s <serial> shell 'ls -la /sdcard/Movies; ls -la /sdcard/Music'`）：
  - Note 11：`/sdcard/Movies` 下仅 `FragmentLearning`（75M，内含 `1-第一章-测试视频.mp4`、`2-第二章-测试视频.mp4`、`3-测试音频.mp3`、`4-无音轨视频.mp4`、`5-损坏文件.mp4`、`6-测试音调.mp3`、`10-第十章-测试视频.mp4`、真实视频副本 `5.Bonus Time - No legs or arms.mp4` 与 `fragment-time-backup-20261111.json`）；`/sdcard/Music` 仅系统 `.thumbnails`。
  - OPPO：15:26 已补清理（目录 + 应用私有数据）；原记录为离线未清理，现更正。
- 删除：`adb -s WGBMXWDIAIFMPFCQ shell 'rm -rf /sdcard/Movies/FragmentLearning'`（明确路径，未使用根目录通配）→ exit 0。
- 目录验证：删除后重列 `/sdcard/Movies` 与 `/sdcard/Music`，两处均只剩 `.thumbnails`，`FragmentLearning` 已不存在。
- 应用数据：`adb -s WGBMXWDIAIFMPFCQ shell 'pm clear com.wanghoufan.fragmentplayer'` → 输出 `Success`。
- OPPO 补清理（2026-11-11 15:26）：删除 `/sdcard/Movies/FragmentLearning`（含 `1-第一章-测试视频.mp4`、`10-第十章-测试视频.mp4`、`2-第二章-测试视频.mp4`、`3-测试音频.mp3` 等）；`run-as com.wanghoufan.fragmentplayer` 删除 `databases`、`files`、`shared_prefs`、`cache`、`code_cache`，应用私有目录已空。
- 回读验证：
  - `run-as com.wanghoufan.fragmentplayer ls -la .` → `total 43` 仅 `.`/`..`；`files`、`databases`、`shared_prefs` 均返回 `No such file or directory`（无数据库、无偏好文件）。
  - `ls -la /sdcard/Android/data/com.wanghoufan.fragmentplayer` → 空目录（无外部数据残留）。
  - `dumpsys package com.wanghoufan.fragmentplayer` → `versionName=0.1.9`、`versionCode=10`，说明仅清数据、应用仍安装。
- 保留项核对（只读）：Note 12T Pro `/sdcard/Movies/第一章家庭与朋友/` 13 个 MP3 仍在，合计 91,559,234 字节（87M），未改动。
- 结论：Note 11 测试课程、测试记录与合成素材已清空；OPPO 已于 15:26 补清理（目录 + 应用私有数据）；真实 13 节 MP3 完整保留。清理后验收证据仍以本文既有章节为准，素材层面的「合成样本」结论不得再当作真实课程结论。

## 13. 0.2.0 结束流程简化真机验收（2026-11-11，Note 12T Pro）

- 版本：0.2.0 / vc11，APK 16,514,240 B，SHA-256 `944131fc2bf9105908bb4fe0484c9b5d7f32f3e3ffc87f57336de23be3f15233`，覆盖安装至 Redmi Note 12T Pro（UKCESWB67PUO7LPB）；单测 25/25 通过（新增 4 条结束流程测试）。
- 行为改动：点「提前结束」立即停止播放并结算，不再出现「提前结束这一轮？」二次确认；只出现一个对话框「记入历史？」，按钮「记入 / 不记入」；两种选择都直接回首页（IDLE），没有结果页停留。自然倒计时到点的完成路径未改。
- 素材：Note 12T Pro `/sdcard/Movies/第一章家庭与朋友/` 真实 13 节。

| 核对项 | 命令 / 操作 | 结果 |
|---|---|---|
| 版本与单测 | 覆盖安装 0.2.0 APK 后 `adb -s UKCESWB67PUO7LPB shell dumpsys package com.wanghoufan.fragmentplayer` 读版本；`./gradlew test` | versionName=0.2.0、versionCode=11；APK 16,514,240 B、SHA-256 `944131fc…5233`；单测 25/25（新增 4 条结束流程测试）；覆盖安装，数据保留 |
| 提前结束只出一个对话框 | 真实 13 节课播放中暂停 → 点「提前结束」 | 无「提前结束这一轮？」二次确认，仅「记入历史？」一个对话框（`endflow-020/01-点结束只出一个确认框-记入历史.png`） |
| 选「记入」 | 点「记入」→ 读 `timer_sessions` 与课程进度 | 回 IDLE；`timer_sessions` 新增 1 行（id=2、presetName=刷牙、listenedSec=49、completed=0）；课程进度 13%→24% |
| 选「不记入」 | 再跑一轮 → 点「提前结束」→ 点「不记入」→ 读 `timer_sessions`、`checkpoints` | 回 IDLE；`timer_sessions` 仍 2 行（未新增）；`checkpoints.positionMs=123999`（回滚到本轮开始前位置）；进度保持 24%；课程仍「共 13 节」 |

- 未覆盖：自然倒计时到点完成路径未复测（本次改动未涉及）；Note 11 与 OPPO 尚未升级 0.2.0。

## 14. 0.2.1 返回键双击退出（2026-11-11 晚，Note 12T Pro）

- 版本：0.2.1 / vc12，APK 16,514,712 B，SHA-256 `2ace3efd6bc83f81c401fe2c6ce899576c3b719a14be627f605de5ae73996c17`，覆盖安装至 Redmi Note 12T Pro（UKCESWB67PUO7LPB）；单测 **29/29** 通过（新增 4 条 ExitBackGuard 用例）。
- 缺陷：此前首页按一次返回立即退回桌面，无法留在应用内。
- 修复：新增 `ExitBackGuard` ＋ Compose `BackHandler`；第一次返回提示「再按一次退出」（英文 `Press back again to exit`）并留在应用内，2 秒内再按一次才 `finish` 回桌面，超 2 秒重新计时。
- 素材：Note 12T Pro `/sdcard/Movies/第一章家庭与朋友/` 真实 13 节（进度 26% 跨覆盖安装保留）。

| 核对项 | 修复前（0.2.0） | 修复后（0.2.1） | 命令 / 操作 | 结果 | 截图 / 路径 |
|---|---|---|---|---|---|
| 版本与单测 | — | 0.2.1 / vc12 | 覆盖安装 APK 后 `adb -s UKCESWB67PUO7LPB shell dumpsys package com.wanghoufan.fragmentplayer`；`./gradlew test` | versionName=0.2.1、versionCode=12；APK 16,514,712 B、SHA-256 `2ace3efd…6c17`；单测 29/29（新增 4 条 ExitBackGuard） | 本仓库 APK 产物；台账 SHA 认包 |
| 第一次返回 | 直接退回桌面 | 提示「再按一次退出」并留在应用内 | 首页按一次返回 → `adb -s UKCESWB67PUO7LPB shell dumpsys activity activities \| grep -i topResumedActivity` | 仍为 `com.wanghoufan.fragmentplayer/.MainActivity`；屏幕出现「再按一次退出」提示 | `docs/qa/evidence-2026-11-11/back-guard-021/01-首次返回-提示再按一次退出.png` |
| 2 秒内第二次返回 | 不适用（首次即退出） | `finish` 回桌面 | 提示出现后 0.5 秒内再按一次返回 → 同上 `dumpsys` | topResumedActivity 变为 `com.miui.home/.launcher.Launcher` | 以 `dumpsys` 输出为准，无截图 |
| 超 2 秒再次返回 | 不适用 | 重新计时，仍提示并留在应用内 | 提示后等待 >2 秒再按返回（本轮未执行） | **未测**（按实现逻辑成立，无真机回执，未测 ≠ 通过） | — |

- 设备现状（本轮收尾）：Note 12T Pro 0.2.1 / vc12；Note 11 5G 0.1.9 / vc10；OPPO PLG110 0.1.0；三台均已清空测试数据，真实 13 节 MP3 与进度 26% 保留在 Note 12T Pro。
- 未覆盖：Note 11 与 OPPO 尚未升级 0.2.1；「超 2 秒重新计时」未做真机复测。

## 15. 0.2.2 语言设置真机验收（2026-11-11 深夜，OPPO PLG110）

- 版本：0.2.2 / vc13，APK **18,710,453 B（17.84 MiB）**，SHA-256 `3b7a2ebba0d0c833a674a7d28b1f86052db607b4cae38385a3ad4ac1f012423f`；单测 **34/34** 通过（新增语言映射 1 例、备份兼容 3 例）。
- 功能：设置页新增「语言」区块，三选一「跟随系统 / 简体中文 / English」，默认跟随系统；偏好持久化，切换经 `AppCompatDelegate` + `locales_config` 重建 Activity 立即生效。
- 设备：OPPO PLG110，**系统语言为英文**；课程保持**共 13 节**，素材文件夹不变。

| 核对项 | 操作 | 结果 | 截图 / 路径 |
|---|---|---|---|
| 英文 → 简体中文 | 英文界面点「简体中文」 | **PASS**：全界面（计时/课程/设置、备份提示、版本号 0.2.2）变中文 | `docs/qa/evidence-2026-11-11/language-022/01-OPPO-设置-语言三选一-中文界面.png` |
| 中文 → 跟随系统 | 点「跟随系统」 | **PASS**：回到英文（系统语言为英文） | 同上目录，本轮结论以界面观察为准 |
| 强停重启持久化 | 强停后冷启动 | **PASS**：仍为中文，偏好已持久化 | 同上目录 |
| 数据保留 | Note 12T Pro 覆盖安装 0.2.2 后读库 | **PASS**：`courses=13`、`timer_sessions=3`、`checkpoints=132,885ms`（26%）不变 | 本仓库只读核对记录，无截图 |

- 回切补充：再点「简体中文」→ 回到中文并保持；英文 UI 文案 `Press back again to exit` 等随语言切换同步。
- 设备现状：三台均 **0.2.2 / vc13**（Note 12T Pro、Note 11 5G、OPPO PLG110）。
- 未覆盖：英文界面下的备份提示与错误文案仅抽查（不逐条）；语言切换后计时进行中的场景未复测。

## 16. 0.2.3 锁屏播放修复（2026-11-11 晚，Note 12T Pro）

- 版本：0.2.3 / vc14，APK **17,510,062 B**，SHA-256 `0d66e9f7310d16daa95acb605ad79fc272e11323bbe98bb7548cb8bd09ed2e2d`；单测 **44/44** 通过（新增 `PlaybackHoldPolicy` 等用例）。
- 缺陷（0.2.2）：锁屏后音频约在**第 95 秒**被系统暂停；倒计时仍走满 180 秒、历史也记为 180 秒，但实际播放停在 `position=230,844 ms`——「记满却听不满」。
- 根因与修复：0.2.3 新增 `playback/PlaybackHoldPolicy.kt` 与播放期 `PARTIAL_WAKE_LOCK`（仅 `playWhenReady` 且解码/出音时持有，暂停与空闲释放）；`PlaybackService` 按策略在「前台状态」与「持锁」之间切换。
- 设备与范围：Redmi Note 12T Pro；素材真实 13 节；用户明确**只跑一轮 3 分钟**锁屏复测，不做每状态 10 轮矩阵。

| 核对项 | 修复前（0.2.2） | 修复后（0.2.3） | 命令 / 操作 | 结果 | 截图 / 路径 |
|---|---|---|---|---|---|
| 版本与单测 | 0.2.2 / vc13 | 0.2.3 / vc14 | 覆盖安装后 `adb -s <serial> shell dumpsys package com.wanghoufan.fragmentplayer`；`./gradlew test` | versionName=0.2.3、versionCode=14；APK 17,510,062 B、SHA-256 `0d66e9f7…2e2d`；单测 44/44（新增 PlaybackHoldPolicy 等） | APK 产物；认包以 SHA-256 为准 |
| 锁屏连续播放 | 约 95 秒后音频被暂停，`position` 停在 230,844 ms | 连续播放 **179,848 ms（≈180 秒）** | 17:18:21 开始计时 → 锁屏 → `dumpsys power` 读 `mWakefulness`；结束后 `dumpsys media_session` 读 state/position | 锁屏全程 `mWakefulness=Asleep`；结束 `media_session` 为 **PAUSED**、`position=410,587 ms`；起点断点 230,739 ms，差 **179,848 ms≈180 秒** | `docs/qa/evidence-2026-11-11/lockscreen-023/01-锁屏三分钟后-计时完成-进度已保存.png` |
| 计时与历史落库 | 历史记 180 秒但实际只播约 95 秒 | 历史 180 秒与实际播放一致 | 结束后读 `timer_sessions` | `timer_sessions` id=5 记 **180 秒完成** | 同上（01） |
| 课程进度 | 26% | **81%** | 结束后回课程页读取 | 课程页显示「共 13 节 / 已学到 81%」 | `docs/qa/evidence-2026-11-11/lockscreen-023/02-锁屏三分钟后的课程进度.png` |

- 设备现状（本轮收尾）：Note 12T Pro **0.2.3 / vc14**；Note 11 5G 与 OPPO PLG110 仍 **0.2.2 / vc13**（OPPO 已交还 043，本轮不再操作）。
- 未覆盖：AC08 每状态 10 轮矩阵未做（用户明确只做本轮一轮 3 分钟）；Note 11 与 OPPO 未升级 0.2.3；勿扰/OEM 省电分级未测。

## 17. 0.2.4 桌面名固定中文与三机推送（2026-11-11 晚）

- 版本：0.2.4 / vc15，APK **17,510,446 B**，SHA-256 `8c72bc89d17162248864df56a501fcc6d42335e3a423e9c11aed61fd582e14c4`；单测 **44/44** 通过。
- 需求：桌面图标名固定为中文「碎片时间」，不随系统语言/App 界面语言变化。
- 改动：新增 `app_label`，仅在默认 `values`（中文）定义；`AndroidManifest.xml` 的 `android:label` 指向 `app_label`。App 内界面语言与通知应用名仍走 `app_name` 的中英资源，两者互不影响。
- 校验方式：`aapt2 dump badging` 读取 `application-label`、`application-label-en` 及各语言 `application-label-*`，全部为「碎片时间」。
- push 范围：渠道 APK 侧载，Redmi Note 12T Pro、Redmi Note 11 5G、OPPO PLG110 三台均覆盖安装到 0.2.4 / vc15，未卸载、未清数据。

| 核对项 | 操作 / 命令 | 结果 | 截图 / 路径 |
|---|---|---|---|
| 版本与单测 | 覆盖安装后 `adb -s <serial> shell dumpsys package com.wanghoufan.fragmentplayer`；`./gradlew test` | versionName=0.2.4、versionCode=15；APK 17,510,446 B、SHA-256 `8c72bc89…14c4`；单测 44/44 | APK 产物；认包以 SHA-256 为准 |
| aapt2 语言标签核对 | `aapt2 dump badging app-debug.apk \| grep application-label` | `application-label` 与 `application-label-en` 等**全部语言均为「碎片时间」** | 命令输出（只读核对） |
| Note 12T Pro 安装 | 覆盖安装 0.2.4 → 读版本、`firstInstallTime`、课程库 | **PASS**：0.2.4 / vc15；`firstInstallTime` 未变；课程 **13 节**、历史 **5 条**，数据未丢 | 本仓库只读核对记录 |
| Note 11 5G 安装 | 同上 | **PASS**：0.2.4 / vc15；`firstInstallTime` 未变；库为空（原有状态），数据未丢 | 本仓库只读核对记录 |
| OPPO PLG110 安装 | 同上 | **PASS**：0.2.4 / vc15；`firstInstallTime` 未变；课程 **13 节**、历史 **1 条**，数据未丢 | 本仓库只读核对记录 |
| OPPO 应用列表显示名（系统英文） | 系统语言为英文，打开应用列表查看 | **PASS**：显示「**碎片时间**」（中文），不随系统英文变为其他名称 | 真机界面观察，无截图 |
| Note 12T Pro 桌面搜索建议 | 桌面下拉搜索输入应用名 | **PASS**：搜索建议显示「**碎片时间**」 | 真机界面观察，无截图 |

- 设备现状（本轮收尾）：三台均 **0.2.4 / vc15**（Note 12T Pro、Note 11 5G、OPPO PLG110）。
- 版本台账：§H 已登记 0.2.4(15) 三机、渠道 APK 侧载，回读 ✓；项目根 `VERSION.md` 已同步。
- 未覆盖：多语言下英文界面内桌面名未另测（aapt2 已证全语言标签一致）；三机均为覆盖安装，未做卸载重装路径。

## 18. 阶段复盘记录（2026-11-11 晚）

### 18.1 落盘踩坑（`agent.md` 新增 5 条）

| 条目 | 一句话结论 |
|---|---|
| Compose 应用一点返回就退出桌面 | 根部无 BackHandler 即走 Activity 默认 finish；需 2 秒窗口 + Toast |
| ExoPlayer 锁屏约 95 秒后音频自停 | 仅 `setWakeMode(WAKE_MODE_LOCAL)` 不够，须按 playWhenReady/state 持 `PARTIAL_WAKE_LOCK` |
| media_session position 快照误判 | 暂停态快照可能滞后；用起止断点毫秒差 + 会话记录 + 界面进度交叉判定 |
| 应用内切中文但桌面图标仍英文 | 应用内语言只影响自身资源；桌面按系统语言读 `android:label`，需独立 `app_label` |
| CodeArts 派工中途停但版本号已改 | 回收必须核对 APK mtime/版本/SHA 与源码改动同时成立 |

### 18.2 机制二门禁（S1，已并入本项目真机验收流程）

`agent.md` 新增「真机验收强制门禁」，三项检查 + 固定回执：

```text
【交互门禁回执】
- 返回键：<首次停留/二次退出，命令与前台包名>
- 结束流程：<确认次数与落点页面>
- 桌面名：<aapt2 名称 + 真机列表显示>
- 结论：<PASS / FAIL（写明哪项）>
```

缺任一项或缺失回执，本次真机验收判未完成，不得报 PASS。

### 18.3 未执行项

- S2：拟把「setWakeMode 不等于锁屏续播、需按状态持锁」补进 `android-fgs-long-audio-local-zh`；按红线未改中央仓库，上收稿待管家处理。
- A1（每日 23:00 多机版本漂移巡检）、A2（每周日 23:00 治理体积与账本体检）：用户本轮未选，不建任务、不登记。
- jcp：未获 commit 授权，未提交、未 push。

### 18.4 体积实测

| 文件 | 体积 | 上限 | 结论 |
|---|---|---|---|
| `agent.md` | 7,320 B | 12,288 B | 通过 |
| `docs/handoff/HANDOFF.md` | 6,829 B / 73 行 | 8,192 B / 100 行 | 通过（§5 由长文压缩为指针句，明细已迁入本节与 §11–§17） |

## 20. 0.2.5 时光流速与到点铃声真机验收（2026-11-12）

| 项 | 命令 / 操作 | 结果 |
|---|---|---|
| 版本 | `adb -s <serial> install -r` 后回读 | 0.2.5 / vc16，18,714,665 B，SHA-256 `5a42c246…421`，单测 60/60 |
| 时光流速 | 设置页选 10×，跑「刷牙 2:00」 | 09:32:19 到点（实际约 12 秒），界面与通知同步触发 |
| 到点铃声 | 到点后 `dumpsys audio` 采样 | MediaPlayer piid 7159 `state:started`、`usage=USAGE_ALARM`、`container uid=10639`（本应用）；通知 id 6901 走 `timer_alert_v2`，importance=4 |
| 不污染数据 | 测试前后拉库比对 | `timer_sessions` 6→6、`checkpoints.positionMs` 19,955→19,955、`courses` 13，无新增 |
| 恢复默认 | 设置页点回 1× | DataStore `time_flow` 值为 1 |

- 截图：`docs/qa/evidence-2026-11-11/timeflow-025/01-时光流速-四档与不计历史说明.png`
- 未覆盖：1× 正常写历史本轮未在真机重跑（单测覆盖）；OPPO 未测试（已交还，用户带出门）。

## 19. 备份恢复与重新关联真机验收（2026-11-11，Note 12T Pro）

- 版本：卸载后重装 **0.2.3 / vc14**（APK 17,510,062 B，SHA-256 `0d66e9f7…2e2d`，同 §16）；`adb uninstall` 后重装，数据全清。
- 素材：真实 13 节 MP3，`/sdcard/Movies/第一章家庭与朋友/`。
- 目标 AC：AC-06（失效可重授权）、AC-10（JSON 备份恢复）。
- 限定：**「SAF 授权失效」由 `adb uninstall` 卸载重装模拟，不是系统真实撤回 SAF 授权；系统真实撤回授权路径未测。**

| 步骤 | 操作 / 命令 | 结果 |
|---|---|---|
| 1. 导出备份（系统 UI） | App 设置页「导出备份」→ 系统保存对话框选「下载」 | **PASS**：生成 `fragment-time-backup-20261111.json`，**12,572 B**；内容含 `settings`（theme / language / fontScale / folderUri）与场景、课程 |
| 2. 清数据 | `adb -s <serial> uninstall com.wanghoufan.fragmentplayer` → 重装 0.2.3 | **PASS**：应用数据全清（课程/历史/进度归零），用于模拟授权失效 |
| 3. 导入备份（系统 UI） | App 设置页「导入备份」→ 系统文件选择器选「下载」里的 `fragment-time-backup-20261111.json` | **PASS**：弹出确认框「恢复这个备份？备份里有场景 2 个、课程 13 个」 |
| 4. 确认恢复 | 点「恢复」 | **PASS**：恢复完成，无报错 |
| 5. 回读数据 | 读本地库（`courses` / `timer_sessions` / `checkpoints`） | **PASS**：`courses=13`、`timer_sessions=3`、`checkpoints.positionMs=132885`（约 **26%** 进度） |
| 6. 重装后课程页 | 打开课程页 | **PASS**：仍显示 13 节与进度；文件夹授权已失效 |
| 7. 重新关联文件夹 | 点「重新关联文件夹」→ 系统选择器选 `/sdcard/Movies/第一章家庭与朋友` → 允许 | **PASS**：无报错，授权恢复 |
| 8. 回读课程与进度 | 读课程列表与进度 | **PASS**：共 **13 节**、当前文件夹路径正确、已学到 **26%** 保留 |

- 本轮结论：AC-06 → **PASS**（含上述两条路径；限定见上）；AC-10 的「真实 13 节备份导出」「经系统 UI 选择 JSON 导入」已由本轮覆盖，AC-10 整体仍 PARTIAL（场景编辑/删除未测）。
- 未覆盖：系统真实撤回 SAF 授权（现为卸载重装模拟）；备份导入在中途取消/坏 JSON 的失败分支；场景新增/编辑/删除。
