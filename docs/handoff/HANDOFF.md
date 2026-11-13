# HANDOFF｜交接（暂停/恢复用，先读我）

> 上限 ≤100 行 / ≤8KB。事实进 `docs/architecture-facts.md`，风险进 `docs/risks.md`，踩坑进 `agent.md`，本文件只留指针与现势。

## §0 常驻指针

| 要什么 | 去哪 |
|---|---|
| 开工路由、红线、索引 | `AGENTS.md`（唯一真源） |
| 架构与构建事实 | `docs/architecture-facts.md` |
| 未决风险与待验证项 | `docs/risks.md` |
| 踩坑与审查交付约定 | `agent.md` |
| 经验条目 | `经验一句话.md` |
| 治理细则 | `docs/sop/governance-{scope,execution-modes,dispatch,ledger}.md` |
| 角色卡 | `docs/roles/` |
| 本轮真机验收证据 | `docs/qa/evidence-2026-11-11/README.md` |
| AC 追踪矩阵 | `docs/qa/产品验收追踪矩阵.md` |

## §1 当前进展

- Captured at（2026-11-13 晚，0.2.8 快捷方式真机验收 + 四机推送完成后）：
- PROJECT_PHASE：DEVELOP
- PLAN_VERSION：PRODUCT_PLAN_V1.1（顾问稿在 docs/plan，docs/pm 尚无正式 PRODUCT_PLAN，不计 ORCA Gate）
- PLAN_READINESS_SCORE：（空，未评）
- PLAN_GATE：IN_PROGRESS
- DEV_BASELINE：P069 产品计划 V1.1（用户已确认 Android APK、离线优先、复用旧项目思路）
- CHANGE_REQUEST：NONE
- Stage ID：M18 桌面长按快捷方式（0.2.8）（M17 铃声 0.2.7 已完成）
- 版本实测：**四台均 0.2.8 / vc21**（SHA `779c8433…814c`）：OPPO、12T Pro、12 Pro 覆盖安装（firstInstallTime 未变、DB 前后一致）；Note 11T Pro+ 首次安装（原未装）
- 素材实测：12T Pro 真实 13 节 MP3、5 条历史未动；12 Pro 无课程（仅倒计时路径）
- 剩 P0：①12T Pro 上有课程时的快捷启动+续播 ②首次发布用户签收
- 当前 Task：0.2.8 已构建、真机 8 项 PASS、四机同包；代码未提交
- 执行链/Session：无（CodeArts 直驱，无 resume ID）
- 本轮已完成（0.2.8）：①长按图标出「开始刷牙 / 开始滴眼药水」，读场景列表（≤4），增删改名随场景流刷新；②点一下冷启动直达运行中计时，与 App 内「开始」同链路；③无课程自动仅倒计时；④计时/暂停中再点只回计时页，不重开、不丢 pendingFinish；⑤真机 8 项 PASS（证据 `docs/qa/evidence-2026-11-13-shortcuts/README.md`）；⑥四机推送 ALL_SUCCESS、同 SHA、已登记台账 §H

## §2 下一步

- 下一步（Next Single Action）：**在 Note 12T Pro（已装 0.2.8）长按点「开始刷牙」，核对计时 + 课程续播**。
- 复测后继续：首次发布签收。
- 人要拍板（列出来问，不问不许开工）：
  - A｜12T Pro 续播复测结论是否可作首次发布依据。
  - B｜何时做首次发布签收（需逐条核对 AC 并留结论）。
- permission_request：无。

## §3 未完成

- 0.2.6 改动：TimerEngine/Controller（点开始起算、准备递减、闹钟提前）、TimerNotifications（`setAlarmClock`）、TimerService（运行期 `PARTIAL_WAKE_LOCK`）、TimerScreen（仅倒计时沿用场景、非 1× 标签）。
- 0.2.8 改动：`shortcut/ShortcutStart.kt`（构建/刷新/短标签/图标）、`MainActivity`（仅 `savedState==null` 消费 Intent，`onNewIntent` 接热启动）、`FragmentTimeApp`（presetId StateFlow + 订阅 presets 刷新）、`RootViewModel.startFromShortcut`（仅 IDLE/FINISHED、无 pendingFinish、无课程回退仅倒计时、短震动）、`FragmentTimeRoot` 切 Tab 后消费；3 个矢量图标 + 中英文案 + `ShortcutStartTest`。
- 单测：`ShortcutStartTest` **5/5**（按要求只跑新增类，未跑全量；上次全量 71/71）。
- 证据：`docs/qa/evidence-2026-11-13-shortcuts/`（00–07 截图 + README）；0.2.5–0.2.7 见 `docs/qa/evidence-2026-11-13/README.md`。
- 未测/限定：有课程设备的快捷启动续播（12 Pro 无课程）；场景改名未真机单独跑（与增删同链路）；>3 分钟深度 Doze 未实跑；铃声英文文案与震动触感未逐项实测。
- 登记：0.2.8 已入版本台账 §H 与 `VERSION.md`（commit `756e1715-dirty`）；0.2.6/0.2.7 仍未登记。
- 阶段复盘（2026-11-13 本机 0.2.8）：新增踩坑 3 条入 `agent.md`；无新 skill 沉淀（机制二评级 C）；自动化 A1 待回「A1」；口令建议只在对话给出；jcp 已 commit/push main（`a74c823`）；设备锁 `qoder-p069` 已 release；`agent.md` 压到 12.2KB（项目上限内，skill 10KB 目标未达）。
- 阶段复盘（2026-11-13 早）：S1/S2 已并入 `android-true-device-qa-orchestrator-local-zh` 1.7.0（阶段 0.8/0.9），未分发、未 commit；A1–A3 待回。
- `docs/review/CODE_REVIEW-2026-11-11.md` 的 21 条 P2/P3 backlog 未动；旧证据见 `docs/qa/evidence-2026-11-11/README.md`。
- 已知设备问题：MIUI DocumentsUI 卡片需无障碍 ACTION_CLICK；锁屏提醒需「通知→发声权限」；计时中 `uiautomator dump` 无 idle 态；MIUI 抽屉 dump 有旧缓存。
- 测试工具：「时光流速」默认 1×，非 1× 不写历史；复测「时间不准」前先确认 1×。
- 代码已推送 main：`a74c823`（0.2.6–0.2.8 代码、证据与台账）、`3a61dab`（复盘经验 `经验一句话.md` 入库）；仅 `USER_MODEL_OVERRIDE.md.旧版-2026-11-11` 未跟踪（配置备份，按红线不入库）。

## §4 红线

- 不 push；commit 需明确授权（含分支名）。
- 产品验收未落盘或关键 AC 未测、首次发布未签收，不得报完工。
- 体系更新未同步两包与概览、或门禁未过，不得收工。
- 不删历史（只迁移/归档）；不替用户判定文件无用。

## §5 三日流水

- 2026-11-11 的 6 条流水（0.1.0–0.2.4 验收链）已按体积闸迁出到 `docs/history/HANDOFF-流水-2026-11-11归档.md`，需要追溯时读它。
- 2026-11-12：0.2.5/vc16 新增「时光流速」1×/2×/6×/10×（非 1× 不写历史、不推进进度与断点）与到点 `USAGE_ALARM` 自播铃声；真机 10× 下 2:00 约 12 秒到点；sessions 与断点在测试前后零变化；已推 Note 12T Pro 与 OPPO，Note 11 未升（证据 2026-11-11 §20）。

- 2026-11-13：用户报「时间不准（前后台/锁屏都一样）」。定位并修三类确定性偏差：点开始→起播的准备耗时被排除在倒计时外、启动失败后「仅倒计时」退回第一个场景时长、锁屏到点用不精确闹钟。0.2.6/vc17 复测：前台 3 分钟 +0.163s、锁屏 3 分钟 +0.582s、锁屏 10 分钟通知 when +0.136s；单测 64/64（§9–§11）。
- 2026-11-13 傍晚：新增桌面长按快捷方式（0.2.8/vc21），Note 12 Pro 8 项真机 PASS；首版 longLabel 被 MIUI 截断已改为仅短标签，并补「重建不重复启动」防护；测试轮留 1 条 2 分钟刷牙历史（13:15，仅倒计时）。
- 2026-11-13 晚：0.2.8/vc21 推送四台（OPPO、Note 11T Pro+ 首装、Note 12T Pro、Note 12 Pro）全 Success，同 SHA；已登记版本台账 §H 与 VERSION.md（构建 commit `756e1715-dirty`）。
- 2026-11-13 晚：阶段复盘（0.2.8）。踩坑 3 条入 `agent.md`；无新 skill（机制二 C 级不沉淀）；自动化候选 A1（发版即推送+回执）待选；设备锁按复盘规则释放。
- 2026-11-13 下午：用户要求铃声加选项、缩短、可试听。0.2.7/vc18：4 选 1（叮咚/钟声/柔和/系统），内置音 1.2–1.6 秒只响一次、系统音 2.5 秒上限（旧版约 15 秒），点选项即试听、选择持久化并入备份；真机三项内置音 1.41/1.67/1.89 秒、系统音 2.51 秒，关闭响铃到点无铃声但通知仍发，锁屏 3 分钟 +0.178~0.747s；单测 71/71（§12–§13）。推送：Note 12T Pro（0.2.5→0.2.7）、Note 12 Pro、OPPO、Note 11（0.2.4→0.2.7）全覆盖 Success，firstInstallTime 未变、数据未丢。

## §6 接续提示词

先读本文件 §0–§3，按 §2 继续：在 Note 12T Pro 复测快捷启动+课程续播，再拿首次发布签收与 commit 授权。