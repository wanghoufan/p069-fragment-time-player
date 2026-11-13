# CODE REVIEW — P069 碎片时间播放器（0.1.1 源码基线）

- Task: T069-07 之后的独立只读代码审查（计时正确性 / 播放入口 / 导入 / 数据备份 / 权限安全 / UI 状态 / 测试覆盖）
- Commit: 未执行 git（按本轮约束）；审查基线＝工作区当前源码与资源
- Reviewer: code-reviewer（独立只读审查代理）
- Result: **有条件通过**

> 结论含义：核心架构方向正确，未发现 P0 级阻断缺陷；但存在 4 条 P1 需在 AC02/AC03/AC05/AC06/AC09 全面验收前修复，且本轮 0.1.1 未真机复测，本文不构成任何 PASS。
> 审查方式：静态只读审阅。未运行构建、未运行真机、未联网、未修改 app 源码/资源/Gradle/Manifest/docs/plan。

## 一、审查结论与依据

**通过项（有代码证据）**

- 计时时间源正确：`TimerEngine` 全程用 `SystemClock.elapsedRealtime()`，UI 刷新频率不参与计算（TimerEngine.kt:3,11,51-63）；UI 侧 `TimerScreen` 的 `remainingMs` 只做展示（TimerScreen.kt:602-604）。
- 暂停冻结/恢复重排正确：`pause()` 取剩余并冻结，`resume()` 用单调时钟重算 deadline（TimerEngine.kt:39-49），且暂停时撤销闹钟、恢复时重排（TimerController.kt:163,178）。
- 到点双通道去重成立：`claimAlert` 共享 `alertPosted` 占位，锁 + `commit()`，ticker 与闹钟两条路径都经它唯一入口（TimerNotifications.kt:74-78；TimerController.kt:370-384；TimerAlarmReceiver.kt:27）；`sessionId` 三道校验（TimerAlarmReceiver.kt:24-26；TimerController.kt:199-205；TimerService.kt:46-55）；`completionLock` 防并发收尾（TimerController.kt:53,301-303,365-367）。
- 冷启动/重启不假装在播：`BootReceiver` 与 `bootstrapFromStorage` 均取消闹钟、清会话、写真实经过时间提示（BootReceiver.kt:17-41；TimerController.kt:450-512），并在到点临界区走统一收尾（TimerController.kt:478-498）。
- 一键启动遵守“媒体真正播放后再计时”：`playQueue` 以 `onIsPlayingChanged(true)` 作为启动信号，6s 超时失败并暂停（PlaybackEngine.kt:136-143,200-229；TimerController.kt:124-145）。
- 计时与媒体严格分离：暂停计时才暂停媒体并撤销闹钟；播放器单独暂停不影响倒计时（TimerScreen.kt:783-788；TimerController.kt:162）。
- 无 INTERNET 权限；导出组件中仅 `PlaybackService` 与 `MainActivity` exported=true，`TimerService`/`TimerAlarmReceiver` 为 false；TimerService 用 `specialUse` 且已声明 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`（AndroidManifest.xml:4-11,32-52）。
- 两类通知分渠道，提醒渠道 IMPORTANCE_HIGH + `USAGE_ALARM` + 锁屏 PUBLIC，常驻通知 IMPORTANCE_LOW 静默（TimerNotifications.kt:40-67），与 P068 R14 修复思路一致。
- 导入并发互斥、SAF 持久授权、自然排序、缺失标记不物理删除、备份导入走 `withTransaction` 且覆盖前落盘快照、JSON 不含媒体字节（CourseImporterImpl.kt:30,57-79,150-208；BackupManager.kt:53-73,118-126；BackupCodec.kt:65-74）。
- 中英字符串 key 完全对齐（values 与 values-en 各 7 文件、数量一致，无单侧缺失）。
- 现有 21 个单测（TimerEngine 7 / NaturalSort 5 / Fingerprint 4 / BackupCodec 5）覆盖了纯逻辑主干。

**判为“有条件通过”而非“通过”的原因**：4 条 P1 均为条件触发的竞态或判定不一致，正常单次操作路径可通，但会破坏“唯一会话 / 到点记账 / 旧数据不被错误覆盖”这三条 AC01/AC03/AC06 的硬约束。

## 二、P0 问题

无。未发现必现的编译级阻断、数据不可逆丢失、secrets 泄露或 `rm` 类不可逆操作。

## 三、P1 问题（4 条，需在全面验收前修复）

### P1-1 启动代际竞态：取消后立即重开，旧启动流程可覆盖新会话

- 位置：TimerController.kt:73-75、126-141、148-152；MainViewModel.kt:53-61
- 现象：`cancelRequested` 是全局布尔，`start()` 第一行 `cancelRequested.set(false)`，`cancelStart()` 只置位并清 UI，**不取消正在挂起的 `start` 协程**（`cancelPreparing` 只把 `startJob` 置 null，未 `cancel()`）。旧协程在 `playQueue`（最长 6s）返回后读到被新 start 重置的 `false`，继续走 `beginRunning(targetSec_旧)`，且 `currentSessionId` 已被新 start 改写。
- 影响：AC01“禁止同一时刻多个活动会话”。可能出现两套 `beginRunning` 叠加（重复 `scheduleAlarm`、重复 `startForegroundService`）、或最终计时使用旧场景时长而队列是新场景；取消按钮在 6s 窗口内实际不生效。
- 建议改法：以本次 `sessionId` 作为代际令牌，在 `playQueue` 返回后及各挂起点后统一断言 `currentSessionId == mySessionId && _state.value.sessionId == mySessionId && phase == PREPARING`，不满足即 `cancelPreparingSession()` 后返回；`cancelPreparing()` 改为 `startJob?.cancel()` 并让 `start()` 捕获 `CancellationException` 清理；`cancelRequested` 退化为局部变量或删除。

### P1-2 不支持课程仍被自动加入播放队列，与课程页判定矛盾

- 位置：TimerController.kt:105-107；CoursesScreen.kt:120-139；CourseImporterImpl.kt:176-182
- 现象：首页“开始”只过滤 `status != MISSING`，`UNSUPPORTED` 课程照常进入队列并被选中；课程页却要求 `status == READY` 才允许点击播放。
- 影响：导入时已被判“文件无法播放”（probe 失败 / 纯音频无音轨）的课程，会在计时启动时被优先选中，直接落入 `startSignal` 超时或 `onPlayerError`，AC01 的“坏文件有恢复出口”退化为必然失败；同一份数据在两处给出互相矛盾的可播判定。
- 建议改法：`playableCourses` 改为 `filter { it.status == CourseStatus.READY }`；对 `READY && !hasAudio && type == VIDEO` 单独处理（保留播放画面但在 PREPARING 前给“只能看画面，无法听”的二次确认）；若全被过滤空，则复用 `timer_error_no_courses` 的恢复入口。

### P1-3 到点瞬间点“暂停计时”会取消到点收尾，提醒已响但会话未记账、状态停在 PAUSED

- 位置：TimerController.kt:154-165、301-368；TimerService.kt:52-60、75-80
- 现象：`TimerService` 用 `scope.launch { timer.handleAlarm(sessionId) }` 跑收尾，`onDestroy` 直接 `scope.cancel()`。若用户恰在闹钟分发后、`finishInternal` 完成前点“暂停计时”，`pause()` 会 `stopTimerService()` 杀掉该协程；`finishInternal` 只有 `try/finally`（复位锁），没有 `CancellationException` 处理，`sessions.record`/`engine.finish`/`cancelAlarm` 可能半途中断，而 `alertPosted` 已置 true。
- 影响：AC03“到点暂停媒体、保存、标记完成、提醒一次”。实际表现：铃声响过、媒体已停（`pause()` 调了 `playback.pause()`），但本次会话没落库、引擎仍是 RUNNING/PAUSED；用户点继续后剩余≈0，再次触发到点但 `claimAlert` 已占位不再响铃，形成“无声的第二次到点”。
- 建议改法：`finishInternal` 的记录+状态+取消闹钟段用 `withContext(NonCancellable)` 包裹；`TimerService` 收尾协程同样用 `NonCancellable`，或把“phase 变化即 stopSelf”改为“`alarmInFlight` 期间不取消收尾”；`pause()` 增加 `if (TimerNotifications.isAlertPosted()) return`（到点后不再接受暂停，直接走收尾）。

### P1-4 导入部分读取失败会静默把未见课程判为 MISSING，并覆盖已有 READY 状态

- 位置：CourseImporterImpl.kt:242-271（`collectMediaFiles`）、133-208（状态覆盖与 missing 判定）、84-113（失败返回）
- 现象：子目录 `listFiles()` 异常被 `catch → emptyArray()` 吞掉，函数继续；`collected.isEmpty()` 只在“根为空”时判失败，因此**部分目录读失败仍会走到 `updateAll`**，未出现在 `collected` 的旧课程被统一 `status = MISSING`。同批 `probe` 失败（临时不可读、provider 未就绪、权限抖动）会把原本 READY 的课程改写为 UNSUPPORTED，`durationMs` 还可能被 `base.durationMs` 保住但 `note` 被替换。
- 影响：AC06“扫描失败不覆盖旧数据 / 失效媒体可重新授权 / 旧进度不丢”。断点按 `courseId` 仍在，可重扫恢复，但期间首页与课程页都判定不可播，且用户看到的是“文件找不到”而非“这次扫描部分失败”。
- 建议改法：`collectMediaFiles` 返回成功标志或失败计数；只要存在读取失败，本次扫描结果标记为 partial，**跳过 missing 标记与 status 降级**（保留旧状态），并在 `ImportResult.message` 加入“部分目录未读取”提示；`probe` 失败区分“权限/临时不可读”（保留旧 status，仅更新 note）与“格式不支持”（才降级 UNSUPPORTED）。

## 四、P2 / P3 Backlog（建议排期，不阻塞本轮闭环）

**P2（正确性/体验缺口）**

1. 播放器断开后 UI 命令可能抛异常或静默无效 — PlaybackEngine.kt:115-133、232-266：`onDisconnected` 只置 `controller=null`，但 `playQueue` 内的局部 `mediaController` 与 UI 侧 `onMain { controller?.play() }` 仍可能作用于已释放实例；`playCourse` 在 UI 路径无 try/catch（CoursesSettingsViewModel.kt:75），异常会直接崩溃。建议统一 `withController` 包装并 `runCatching`，断开时先保存断点再提示。
2. 试听铃声离开设置页不会停止 — SettingsScreen.kt:112-115、207-222：4s `postDelayed(stop)` 被 `onDispose` 的 `removeCallbacksAndMessages(null)` 移除，`Ringtone` 引用未保存，可能持续响数十秒。建议持有引用并在 `onDispose` 调用 `stop()`。
3. 播放中遇到坏文件缺少恢复出口 — PlaybackEngine.kt:349-375；TimerScreen.kt:861-868：只显示内联“播放失败”，没有“跳过本节/换下一节/回到课程页”动作。建议在播放器错误区补“下一节”。
4. `handleAlarm` 接受 PAUSED，暂停瞬间可记满时长 — TimerController.kt:199-207、313-317：闹钟若在暂停后仍被分发，会以 `completed=true` 记 `listenedSec=targetSec`。建议用实际 `elapsed`/剩余时间判定 completed。
5. `persist()` 每秒在主线程同步 `commit()` — TimerController.kt:285-289、399-418：可移到 IO 或仅在与 deadline 相关的关键秒写入。
6. 备份写入用 `"wt"` 模式且设置应用非事务 — BackupManager.kt:109-116、128-136：部分 SAF provider 不支持 `"wt"`，可能残留旧内容；`applySettings` 逐项写入且 `SettingsRepositoryImpl.write` 吞 IOException，可能出现“课程已恢复、设置只恢复一半”。建议 `"w"`+truncate；设置应用失败回滚并提示。
7. 备份校验缺关键范围/枚举校验 — BackupCodec.kt:108-135：未校验 `fontScale`（可为 0/超大）、`durationSec`、`targetSec`、`status`/`type`。`fontScale` 直接乘进 `Density`（Theme.kt:103-108），异常值会让 UI 不可用。建议 clamp 到 [1.0,1.5] 并校验枚举。
8. 恢复前快照用户不可达 — BackupManager.kt:118-126：写入 `filesDir` 但无读取/还原入口，也没有提示路径，“恢复点”实际不可用。建议设置页提供还原入口或导出到用户可见位置。
9. 增量扫描逐条 insert 不在事务内、rescan 不补授权 — CourseImporterImpl.kt:184-191、41-50、85-93：扫描中断会留下“新增已入库、旧行未重排”的半成品；`rescan()` 不重新 `takePersistableUriPermission`，授权丢失后可能整批判缺失。建议整批事务写入；rescan 前确保授权。
10. 单断点模型下拖动/切课立即改写“上次继续”位置 — PlaybackEngine.kt:156-166、335-342；CheckpointRepositoryImpl.kt:18-29：每次 `save` 先 `clear()` 再 upsert 一条；自动过渡时先写旧课末尾、随后被新课覆盖，进程恰在此窗口被杀会把断点记到旧课末尾。建议自动过渡只保存旧课、拖动不改持久断点（或用独立的“临时播放位置”）。
11. 大字体下倒计时可能裁切 — TimerScreen.kt:637-642：`displayLarge` 57sp × 用户 1.5 = 85.5sp，`maxLines=1` 且无 overflow/自适应，AC10“大字体无截断”未覆盖。建议真机实测后对倒计时单独做自适应字号（其他文本已被要求不改）。
12. App 内 DARK + 系统浅色时冷启动仍可能白闪 — values-night/themes.xml、values/themes.xml；Theme.kt:95-99：启动主题只跟随系统深色，无法感知 App 内持久化偏好（HANDOFF 所述“深色冷启动白闪”只覆盖系统深色场景）。
13. `allowBackup="true"` 且无 backup rules — AndroidManifest.xml:13-20、res/xml 为空：系统会备份 Room/SP/DataStore，恢复后 `folderUri` 无对应 SAF 授权，且会话状态可能跨设备错位。建议 `false` 或配 `dataExtractionRules` 并对恢复后文件夹做授权校验提示。
14. Room 无迁移基线 — AppDatabase.kt:15-16 与 build.gradle.kts:54-56：`exportSchema=false` 与 `schemaLocation` 矛盾，未产出 schema；无 `Migration`/`fallbackToDestructiveMigration`。下一版改 schema 会直接崩。建议 `exportSchema=true` 并入库 schema。
15. “仅暂停播放器”的实际媒体时长未记账 — Models.kt:36-45、TimerController.kt:301-339：文档模型含 `actualMediaSec`，`TimerUiState.actualMediaMs` 已采集（TimerController.kt:281）但未写入会话记录。属产品确认项，先不擅自改 schema。

**P3（清理/一致性）**

16. 扫描计数重复 — CourseImporterImpl.kt:139-147、220-226：重复文件同时计入 `duplicates` 与 `unsupported`，`ImportResult.unsupported` 含重复数，文案可能自相矛盾。
17. 死代码/未接线 — `PlaybackEngineApi.waitForPlaybackStarted`（PlaybackEngine.kt:272-276）、`TimerEngine.isFinished`（TimerEngine.kt:65-66）、`TimerResult.interrupted`（Models.kt:54）均无生产调用。
18. 备份 JSON 含 `folderUri` 与 `relativePath`（BackupCodec.kt:18,33-35）：用户主动导出可接受，但建议在设置页导出说明中标注“含本机目录信息”。
19. 提示语义细节 — TimerScreen.kt:263-269 启动失败的“仅倒计时”固定使用第一个预设（`fallbackPreset`），与用户刚点的场景不一致；`minutesOf`（TimerScreen.kt:1010-1011）对 0 秒也显示 1 分钟。
20. 无障碍/语义 — TimerScreen.kt:805-819 Slider 与 LinearProgressIndicator 无显式语义标签；`StatusTag` 用 14sp `labelSmall`（CourseSettingsComponents.kt:135-137）承载“缺失/不支持/无音轨”关键状态，建议真机 TalkBack + 200% 字号复核。
21. 通知 chronometer 以墙钟 `when` 计基准（TimerNotifications.kt:141-144），改系统时间会跳变；`formatClock`/备份文件名用 `SimpleDateFormat` 默认时区（BackupManager.kt:119、SettingsScreen.kt:600-603），与“时间戳显式归一化时区”规范不符。

## 五、未覆盖范围（本轮未验证，不得据此判 PASS）

- **真机**：0.1.1 未安装复测（HANDOFF 状态“已构建未装”）；OPPO PLG110 与 Redmi Note 11 5G 均未在本轮复核（AC11 双机证据缺失）。
- **OEM / 系统行为**：锁屏通知可见性、OPPO 省电冻结、勿扰、通知渠道被用户关闭、精确闹钟权限拒绝后的降级路径（AC08 前台/后台/锁屏各 10 轮矩阵未做）。
- **媒体能力**：真实 MP4/MKV/WEBM/MOV、M4A/FLAC/OGG/WAV 的实际解码与音轨识别（`MediaProbe`/ExoPlayer 结论均需真实文件）；4K/长视频、无音轨 MP4。
- **SAF provider 差异**：`ExternalStorageProvider`、`DownloadsProvider`、第三方网盘/FileProvider 的 `lastModified` 稳定性与 `"wt"` 支持度；重授权后 fingerprint 是否稳定（当前 fingerprint 依赖 `relativePath|size|lastModified|displayName`，Fingerprint.kt:6-11）。
- **进程时序**：系统强杀、用户划掉任务、重启、低速内存回收下 `claimAlert`/`completionLock`/`BootReceiver` 的真实竞态；本轮仅做静态推演。
- **音频焦点链路**：来电、耳机拔出、蓝牙断连、其他 App 抢焦点后的暂停与恢复（代码使用 `setHandleAudioBecomingNoisy(true)` + 音频焦点，行为待真机）。
- **备份跨机/跨版本**：真实设备卸载重装、换机恢复、旧版本 JSON 导入。
- **依赖可用性**：Media3 `1.11.1`、Compose BOM `2025.09.00`、targetSdk 36 的实际可解析与构建（本轮按约束未构建验证）。
- **无障碍与视觉**：TalkBack 朗读、系统字体 200%、深色/墨水屏对比度、实机截图签收。

## 六、不建议本轮修改的项（避免范围膨胀）

1. **不重构 TimerEngine 的“单调时钟 + 墙钟 deadline 持久化”双时间源**：这是跨重启恢复的正确做法（TimerEngine.kt:11,35；TimerController.kt:84,171,471），只需按 P1-1/P1-3 做局部修正。
2. **不引入 WorkManager/前台轮询替代 `AlarmManager`**：V1 的闹钟兜底路径已按 P068 R14 修正为高重要性渠道 + 闹钟音频属性 + 占位去重，替换方案会引入电池与 OEM 白名单的新风险。
3. **不把单断点改成“每课历史进度”**：AC02 只要求所有场景共享同一断点，改动涉及 Room schema 与恢复语义，应等 AC02 真机通过后再评估。
4. **不升级 Media3 / Compose BOM / targetSdk**：当前版本需重新做一遍全链路真机回归，本轮收益不明。
5. **不为 P068 R9（合并清单注入 ACCESS_NETWORK_STATE）加 `tools:node="remove"`**：可能影响 Media3 网络能力判断，且项目已确认无 INTERNET，风险已记录在案。
6. **不收紧 `PlaybackService exported="true"`**：Media3 官方模板要求 exported 且带 `MediaSessionService` intent-filter，本应用无敏感数据，收紧要另做 controller 授权改造。
7. **不把 `BootReceiver` 改成 exported="false"**：BOOT_COMPLETED 属受保护广播，改动收益低且需真机验证投递，留到 AC09 专项。
8. **不做全站字体自适应改造**：先按 P2-11 只在倒计时一处做真机验证，避免动 Typography 影响全部页面。
9. **不在本轮补 `actualMediaSec` 字段**：属产品口径新增（Models.kt:36-45），需用户确认后再动 schema 与备份格式。

## 七、测试覆盖评估（现有 21 个单测）

**已覆盖**：TimerEngine 倒计时/暂停冻结/恢复重排/到点判定/提前结束/重开重置（7）；NaturalSort 数值比较与稳定性（5）；Fingerprint 稳定性与敏感性（4）；BackupCodec 往返/版本/空 URI/坏 JSON（5）。

**缺口（按优先级）**

1. **P1 级**：TimerController 的到点收尾与双通道去重（`claimAlert` 只发一次、`sessionId` 不匹配不处理、PAUSED 到点、`completionLock` 并发、P1-3 的取消时序）；start 代际竞态（P1-1）。
2. **P1 级**：导入增量匹配完整语义（URI→指纹匹配、缺失不删、UNSUPPORTED 判定、重复文件、部分读取失败不覆盖、并发互斥）——需把 `DocumentFile` 与 `MediaProbe` 抽成可注入接口后用 Robolectric 或纯逻辑重写测试。
3. **P2 级**：备份导入事务（失败回滚、设置应用顺序、fontScale 越界、恢复后 folderUri 失效）；`CheckpointRepositoryImpl.save` 的“单条断点”语义。
4. **P2 级**：TimerSessionStore 读写/清除/notice（Robolectric 可直接覆盖）。
5. **P3 级**：NaturalSort 空串/纯数字/前导零边界；MediaProbe 扩展名分流与 `hasAudio` 判定（可先抽 `typeOf`/`isMediaFile` 纯函数测试）。

## 八、给下一轮的动作建议（不改代码，仅排序）

1. 先修 P1-1、P1-2、P1-3（计时主链路），再修 P1-4（导入判定），然后才做 AC02/AC03/AC05/AC06/AC09 的真机验收。
2. 修完 0.1.1 四项缺陷（HANDOFF R2）后补一次安装复测，再补一轮到点与锁屏后台，否则全部真机结论仍不适用当前源码。
3. 真机通过后再处理 P2 中的 1/2/3/11/12（播放器错误恢复、试听铃声、大字体、深色启动），避免与主链路修复混在同一轮回归。