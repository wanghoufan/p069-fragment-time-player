# P1 修复复核 — P069 碎片时间播放器（0.1.2 / versionCode 3）

- 复核对象：`CODE_REVIEW-2026-11-11.md` 第三节 P1-1～P1-4 的修复结果
- 复核基线：工作区当前源码与资源（修复代理交付 0.1.2，声称 SHA-256 `9e7be169…efdb2`、单测 21/21）
- Reviewer：P1 修复复核代理（只读）
- 方式：**仅静态只读复核**。未构建、未安装、未真机、未联网、未执行 git、未修改源码/资源/Gradle/Manifest/docs/plan。
- 结论：**P1-1～P1-4 全部 CLOSED（关闭）**；未发现新增 P0/P1；另有 1 条 P2、4 条 P3 残余建议，均不阻断进入真机复测。

## 一、逐条结论

| 编号 | 结论 | 一句话依据 |
|---|---|---|
| P1-1 启动代际竞态 | **CLOSED** | `cancelRequested` 已删除；以 `sessionId` 三代际令牌校验；`cancelPreparing()` 先 `Job.cancel()`；`CancellationException` 仅在仍属本会话时清理；`beginRunning` 只能由当前会话进入（单次） |
| P1-2 队列含不可播课程 | **CLOSED** | 队列只取 `status == READY`；空队列走 `failStart(timer_error_no_courses)` 回到 IDLE 并提供重试/仅倒计时/去导入出口；无音轨 READY 视频仅在探测时加 note，仍入队可播 |
| P1-3 到点收尾被暂停打断 | **CLOSED** | `completeSession` 整体 `NonCancellable`；`completionLock` 在最外层 finally 复位；`pause()` 在 `alertPosted` 后直接返回；`TimerService` 用独立 `alarmScope` 且 `onDestroy` 在 `alarmInFlight` 时不取消它 |
| P1-4 导入部分失败覆盖旧状态 | **CLOSED** | `collectMediaFiles` 返回成功标志；partial 时 `missingRows` 为空且已有课程保留旧 status/type/hasAudio；ProbeFailure 区分 Security/IO/FORMAT；新文件失败仍 UNSUPPORTED；结果文案含“部分目录未读取”；`updateAll` 是 `@Update`，不会删除未见课程 |

## 二、P1-1 证据（启动代际竞态）

1. **全局 `cancelRequested` 已移除**：对 `app/src` 全文检索 `cancelRequested` 无任何命中（`rg` 结果为空）。
2. **代际令牌校验**：`timer/TimerController.kt:257-262`
   ```kotlin
   private fun isPreparing(sessionId: String): Boolean {
       val snapshot = _state.value
       return currentSessionId == sessionId &&
           snapshot.sessionId == sessionId &&
           snapshot.phase == TimerPhase.PREPARING
   }
   ```
   三重条件同时成立才算“仍属本次启动”，任何一次新 start / cancelStart 改变其中任一项都会使旧协程失效。
3. **挂起点后校验**：
   - presets 读取后：`TimerController.kt:100-105`
   - 课程读取后：`TimerController.kt:123-129`
   - `playQueue` 返回后：`TimerController.kt:154-157`（失败分支前还有 `146-164`）
   - 写 `courseTitle` 前：`TimerController.kt:166-169`
   - 进入 `beginRunning` 前：`TimerController.kt:270-276` → `278-295`
4. **`cancelPreparing` 取消 Job**：`ui/MainViewModel.kt:58-63`
   ```kotlin
   fun cancelPreparing() {
       startJob?.cancel()
       startJob = null
       graph.timer.cancelStart()
   }
   ```
   `TimerController.cancelStart()`（`TimerController.kt:183-186`）仍保持同步清理，但真正的挂起协程由 ViewModel 取消；即便未来有人绕过 ViewModel 直接调 `cancelStart()`，代际令牌也会阻止旧协程继续改状态。
5. **`CancellationException` 清理且不误伤新会话**：`TimerController.kt:174-180`
   ```kotlin
   } catch (cancelled: CancellationException) {
       if (currentSessionId == sessionId) {
           withContext(NonCancellable) { cancelPreparingSession() }
       }
       throw cancelled
   }
   ```
   只有当 `currentSessionId` 仍是旧会话时才清理；新 start 已写入新 sessionId 时不清理。校验与清理之间无挂起点（`Main.immediate` + `NonCancellable` 同为当前调度器），主线程上不可交错。
6. **不可能双 beginRunning / 双闹钟**：
   - `beginRunningIfPreparing` 是唯一的进入点（`TimerController.kt:270-276`），先同步校验再同步 `beginRunning`，中间无挂起点，主线程下不可能两次通过。
   - 即使历史遗留的两次调度同时到达，`scheduleAlarm` 使用同一 `PendingIntent` 请求码 + `FLAG_UPDATE_CURRENT`（`TimerNotifications.kt:163-178, 202-219`），只会留下一条闹钟，且 `TimerAlarmReceiver.kt:24-27` 会用 sessionId 与存储快照再校验一次。

**残余（非阻断，P3）**：`TimerController.kt:135-140` 的 `checkpoints.get()` 之后没有立即再校验一次；且 `runCatching` 包裹了 presets/courses/checkpoints 三处挂起调用（`100/123/135`），会吞掉 `CancellationException`。实际影响被两层机制兜住：其一，旧协程下一次进入 `playback.playQueue` 时 `awaitController`/`withContext` 会因 Job 已取消立即抛 `CancellationException`（`PlaybackEngine.kt:185-186, 190`），不会真正开始播放；其二，后置代际校验会阻止写状态。建议后续补一处 `if (!isPreparing(sessionId)) { abandonStartIfOwned(sessionId); return }` 并把 `runCatching` 换成 `try/catch` 只吞非取消异常。

## 三、P1-2 证据（队列只含 READY）

1. **过滤条件**：`timer/TimerController.kt:123-125`
   ```kotlin
   val playableCourses = runCatching { courses.all() }
       .getOrDefault(emptyList())
       .filter { it.status == CourseStatus.READY }
   ```
   与课程页判定一致：`ui/screens/CoursesScreen.kt:125-139`（`if (course.status == CourseStatus.READY)` 才允许播放 / 从头播放）。
2. **空队列恢复出口**：`TimerController.kt:126-133` → `failStart()`（`311-330`）写 `phase = IDLE`、`sessionId = null`、`error = timer_error_no_courses`，并保留 presetId 供重试。UI 侧 `ui/screens/TimerScreen.kt:263-269` 用 `StartErrorCard` 提供“重试 / 仅倒计时”，同页 `CurrentCourseCard`（`302-310`）带“去导入”按钮，`315-345` 是错误卡片的动作区。取消/失败不会把用户困在 PREPARING。
3. **无音轨 READY 视频仍可播**：`importer/MediaProbe.kt:85-97` 对 `type == VIDEO && !hasAudio` 返回 `ok = true` 并只加 note；`importer/CourseImporterImpl.kt:185-187` 中 `probe.ok -> CourseStatus.READY` 优先，故状态为 READY、`hasAudio = false`。`playback/PlaybackEngine.kt:176-230` 不对 status/hasAudio 做二次过滤，只按传入列表建 `MediaItem`，因此仍会播放画面（静音）。
4. **UNSUPPORTED / MISSING 不入队**：由上面的 READY 过滤直接保证；`MISSING` 与 `UNSUPPORTED` 在 `CoursesScreen.kt:350-405` 仍显示重新关联/查看原因入口。

**残余（P3）**：`timer_error_no_courses` 的文案是“还没有导入课程，可以先只用倒计时”（`values/strings_timer.xml:13`，英文同义 `values-en/strings_timer.xml:13`）。现在“导入了课程但全部不可播”也会走到这条文案，措辞略不准确（恢复入口本身是完整的）。建议后续拆成“没有可播放课程”。

## 四、P1-3 证据（到点收尾不可被暂停打断）

1. **`completeSession` 整体 `NonCancellable`**：`timer/TimerController.kt:362-371`
   ```kotlin
   if (!completionLock.compareAndSet(false, true)) return
   try {
       withContext(NonCancellable) { completeSession(completed) }
   } finally {
       completionLock.set(false)
   }
   ```
   记录会话、状态、提醒、取消闹钟、持久化全部在 `completeSession`（`373-435`）内完成。
2. **`completionLock` 复位在最外层**：`try/finally` 包住唯一主体，锁在 `finally` 复位；并发第二个收尾直接返回，由第一个完成最终状态（不会双记）。
3. **`pause()` 在 `alertPosted` 后拒绝进入 PAUSED**：`TimerController.kt:188-201`
   ```kotlin
   if (_state.value.phase != TimerPhase.RUNNING) return
   if (TimerNotifications.isAlertPosted(appContext)) return
   ```
   `alertPosted` 由闹钟接收器在启动服务前原子占位（`timer/TimerAlarmReceiver.kt:27`，`TimerNotifications.claimAlert` `74-78`），因此闹钟路径一旦开始，暂停按钮不再改写状态、不再取消闹钟、不再 `stopTimerService`。
4. **`TimerService.alarmScope` 收尾不受 `onDestroy` 取消**：`timer/TimerService.kt:29-34`
   ```kotlin
   private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
   private val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
   private var alarmInFlight = false
   ```
   到点收尾跑在 `alarmScope`（`56-69`，内部再用 `withContext(NonCancellable)`），`onDestroy`（`85-93`）只在 `!alarmInFlight` 时才取消 `alarmScope`；收尾完成后由自身的 `finally` 回收作用域。
5. **到点瞬间暂停的四种时序均能闭环**：
   - 闹钟先到（claim 已发生）：`pause()` 直接返回，收尾照常 → 记一次会话、`FINISHED`、取消闹钟（`419-433`）。
   - ticker 先到零：`restartTicker` 先把 `tickerJob = null` 再调 `finishInternal`（`351-356`），`pause()` 的 `tickerJob?.cancel()` 无从打断；`completeSession` 已持锁且 NonCancellable，`pause()` 即便在 `saveCheckpointNow`/`sessions.record` 的挂起处插入，也只能短暂写 PAUSED，最终由收尾写成 FINISHED。
   - 收尾过程中 `stopTimerService()` → `onDestroy`：`alarmInFlight` 为 true（或收尾跑在 Controller 自己的 ticker 上），不影响收尾。
   - 暂停发生在到点前几毫秒且用户不再恢复：属正常暂停语义（闹钟已取消、不记账），不是本缺陷。
6. **NonCancellable 不会卡死**：`completeSession` 内部只做 Room suspend 调用、`SharedPreferences.commit()` 与通知操作，无阻塞主线程的同步等待、无嵌套锁竞争；`completionLock` 是原子标志类，不是阻塞锁，第二次收尾直接返回而不会等待。

**残余（非阻断，P3）**：`pause()` 在 ticker 已到零但 `claimAlert` 尚未执行的极窄窗口内仍可能先写一次 PAUSED；该写法会被 NonCancellable 收尾覆盖为 FINISHED，静态推演无数据损失。另 `handleAlarm` 仍接受 PAUSED（`TimerController.kt:238-242`），原审查 P2-4（暂停瞬间记满时长）保持原样，属既有 Backlog，不是本轮引入。

## 五、P1-4 证据（导入部分失败不覆盖旧状态）

1. **`collectMediaFiles` 返回成功标志且逐层传播**：`importer/CourseImporterImpl.kt:261-294`
   - `dir.listFiles()` 抛异常 → `return false`（`263-267`），不再 `emptyArray()` 吞掉；
   - 子目录失败由 `if (!collectMediaFiles(...)) complete = false` 向上汇总（`272-275`），最终 `return complete`（`293`）。
2. **scan 侧取 partial**：`CourseImporterImpl.kt:101-115`；根为空且 partial 时给 `data_err_folder_unreadable` 而不是“无媒体”（`103-113`）。
3. **partial 跳过缺失标记**：`CourseImporterImpl.kt:212-219`
   ```kotlin
   val missingRows = if (partial) emptyList() else existing.filter { it.id !in usedIds }
   ```
   且 partial 时结果文案追加“部分目录未读取……”（`246-248`）。
4. **partial / 临时不可读不降级旧状态**：`CourseImporterImpl.kt:178-193`
   ```kotlin
   val keepStatus = match != null && !probe.ok &&
       (partial || probe.failure == ProbeFailure.IO || probe.failure == ProbeFailure.PERMISSION)
   ...
   status = when {
       probe.ok -> CourseStatus.READY
       match == null -> CourseStatus.UNSUPPORTED
       keepStatus -> base.status
       else -> CourseStatus.UNSUPPORTED
   }
   ```
   `type/hasAudio` 在 `keepStatus` 时保留旧值（`183-184`），`durationMs` 仅在 probe 取到正值时才覆盖（`182`），`note` 仍更新为本次原因（`192`）。
5. **ProbeFailure 分类**：`importer/MediaProbe.kt:17` 定义 `NONE/IO/PERMISSION/FORMAT`；映射为 `SecurityException → PERMISSION`（`98-106`）、`IOException → IO`（`107-115`）、`IllegalArgumentException → FORMAT`（`116-124`）、`UnsupportedOperationException → FORMAT`（`125-133`）、其它异常 → `IO`（`134-142`）；无轨道 / 纯音频无音轨 → `FORMAT`（`65-84`）。
6. **新文件失败仍 UNSUPPORTED**：`match == null -> CourseStatus.UNSUPPORTED`（`187`），随后 `insert`（`195-198`）。
7. **中英字符串齐全**：`values/strings_data.xml:14` 与 `values-en/strings_data.xml:14` 均有 `data_import_partial_part`；两文件 key 各 21 条，`diff` 完全一致；`data_probe_no_permission/unreadable/no_track/video_no_audio/audio_silent` 两侧均存在。
8. **“缺失不删除”未被破坏**：`data/db/Daos.kt:70-71` 的 `updateAll` 是 `@Update`（只更新已存在行，不删除），`data/repo/CourseRepositoryImpl.kt:26-29` 仅在列表非空时调用。partial 扫描中未出现的旧课程既不进入 `rows`、也不会被 `updateAll` 删除，数据库里保持原状态；完整扫描时才由 `missingRows` 显式写 `MISSING`（仍是标记而非物理删除）。

**残余（P2，不阻断）**：`MediaProbe.kt:134-142` 把“其它异常”一律归为 `IO`。实际设备上 `MediaMetadataRetriever.setDataSource` 对损坏/不支持的容器常抛 `RuntimeException`（非 `IOException`/`IllegalArgumentException`），会被判成 `IO` 从而对已有课程执行 `keepStatus`，原本 READY 的坏文件继续保留 READY；P1-2 的 READY 过滤不会排除它，计时启动时仍可能落到 `startSignal` 超时并回到 IDLE+错误+重试（有恢复出口、无数据损失）。建议下一轮把 `setDataSource failed`/`status = 0x…` 这类 `RuntimeException` 归入 `FORMAT`，或仅在 `partial` 时保留 IO 状态。

## 六、回归检查

| 检查项 | 结论 | 依据 |
|---|---|---|
| READY 过滤后“课程页可播、计时不播” | **未发现** | 课程页与计时页同为 `CourseStatus.READY`；`PlaybackEngine.playQueue` 不再做额外过滤 |
| 无音轨视频被误判为不可播 | **未发现** | probe 对 VIDEO 无音轨返回 ok=true + note，状态 READY |
| 取消逻辑清掉新会话 | **未发现** | 清理前 `currentSessionId == sessionId` 判定；新 start 已换 id 时不清理；`cancelStart()` 仅在 PREPARING 生效 |
| NonCancellable 导致卡死/主线程阻塞 | **未发现** | 收尾内无阻塞等待；`completionLock` 非阻塞；第二个收尾直接返回 |
| 中英字符串缺失 | **未发现** | `strings_data.xml` key 数 21=21 且完全对齐；新增文案中英均存在 |
| 导入后课程被物理删除 | **未发现** | `@Update` 不删除；partial 不写 missing；完整扫描只写 MISSING 标记 |
| 其他 UI/播放路径被改动 | **未发现** | 只读复核范围内未见播放器/课程页/设置页逻辑改动痕迹 |

## 七、新发现（均非 P0/P1）

- **P2-新-1**：`MediaProbe` 通用异常归类为 `IO`，可能让损坏的既有 READY 课程继续留在可播队列（详见 §五残余）。影响：启动失败一次并回到 IDLE+错误+重试，有恢复出口，不丢数据。
- **P3-新-1**：`checkpoints.get()` 后缺一次即时代际校验，且三处 `runCatching` 会吞 `CancellationException`（实际被后续 `withContext` 取消与代际校验兜住）。
- **P3-新-2**：`timer_error_no_courses` 文案在“有课程但全不可播”时提示“还没有导入课程”，与事实不符。
- **P3-新-3**：partial 扫描只为本次收集到的行重算 `sortIndex`，未读到的旧行保留旧序号，可能出现序号并列/排序轻微错位（仅展示顺序，不影响播放与数据）。
- **P3-新-4**：`TimerService.alarmScope` 在收尾 `finally` 中自取消；若同一 Service 实例在销毁前再次收到 `ACTION_ALARM`，新 `launch` 会落进已取消作用域而不执行（现实中同会话已被 `cancelAlarm` 防住，且接收器已先发提醒、ticker 到零会自愈）。

## 八、静态复核边界（不得据此判任何 PASS）

- 未执行构建，未复验 0.1.2 的 SHA-256 `9e7be169…efdb2` 与 21/21 单测；上述结论只针对**当前工作区源码文本**。
- 未安装、未真机、未做 OPPO PLG110 / Redmi Note 11 5G 双机验证；锁屏、省电冻结、精确闹钟拒绝、来电/耳机断连、SAF 第三方 provider、真实 MP4/MKV/M4A 解码等全部未验证。
- 未做进程强杀/划掉任务/低内存下的真实竞态验证；P1-1/P1-3 的时序结论均由静态推演得出。
- 未执行 git，无法核对本轮改动是否只含这 4 条修复、是否存在未提交/未构建的残留。
- 新增的 P1 修复**仍无单测覆盖**：`app/src/test` 中无 `TimerController`、`TimerService`、`CourseImporterImpl`、`MediaProbe` 相关用例（原审查 §七第 1、2 项缺口保持）。建议后续用可注入的 `DocumentFile`/`MediaProbe` 抽口补“parts 失败不覆盖”“代际取消”两组回归。

## 九、建议

1. **四个 P1 可判闭环，建议进入真机复测**；真机重点覆盖：PREPARING 6s 窗口内连点“取消→立即重开”、到点瞬间点暂停（前台/锁屏）、断开某个子目录后的重扫提示、无音轨 MP4、全部课程 UNSUPPORTED 时的空队列恢复。
2. 真机通过前不要把本报告当作 AC01/AC03/AC06 的验收证据；本报告只证明“代码层面已按审查建议改到位”。
3. 下一轮排期：P2-新-1（probe 分类）与 P3-新-1/2 各一行级修正，再按原 Backlog P2-1/2/3/11/12 继续。