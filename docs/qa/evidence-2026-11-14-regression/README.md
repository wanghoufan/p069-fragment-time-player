# P069 回归检查 V1.1 真机证据｜2026-11-14

> 本轮为「回归检查体系 V1.1」首次试运行。设备：Redmi Note 12 Pro（22101316C，Android 14 / SDK 34，MIUI/HyperOS）。被测包：`0.2.8(21)`，SHA-256 `779c8433bce45045c07d266dfa20d90bf8a46132047c446286a40be41b01814c`，17,926,541 B，debug 签名，构建时间 2026-11-13 13:26。
> 设备绑定：`android-device-target-lock-local-zh`。**实测纠偏**：profile `codearts-069` 当时并未绑定本机 22101316C；锁记录为 label「Redmi Note 12T Pro」、model `23054RA19C`（实为 Note 11T Pro+），且未 release。详见 `logs/device-lock-state-2026-11-14.txt`；该轮真机存在绕过锁绑定操作的问题。设备序列号不入库。
> 接手轮补充：`android15-recheck/`（Note 11T Pro+ / Android 15）+ `logs/device-inventory-2026-11-14.txt`。

## 1. 证据清单

### 截图（screens/）

| 文件 | 证明什么 | 结论 |
|---|---|---|
| `01-launch.png` | 冷启动首屏：两个场景卡、当前课程空态、导入入口、仅倒计时、三 Tab | PASS |
| `02-start-no-course.png` | 无课程点「开始」的错误卡片：说明原因 + 重试 + 仅倒计时 | PASS（有恢复出口） |
| `07-finished.png` | 2 分钟到点完成页：本次 2 分钟、知道了、再来一轮 | PASS |
| `16-finish-dialog.png` | 提前结束只弹一个「记入历史？」对话框，无二次确认 | PASS |
| `19-lockscreen-alert.png` | 锁屏首屏：只有其他 App 媒体卡与 USB 通知，未见 P069 到点通知 | PARTIAL |
| `20-notif-shade.png` | 下拉通知栏：「时间到了 / 「刷牙」02:00 已到」，12:16 | PASS（通知已发且可见） |
| `21-after-lock-finish.png` | 解锁回 App：计时完成页；会话 id=20 已写库 | PASS |
| `22-interrupted.png` | 计时中 force-stop 后重启：根级横幅「上次计时被中断…已进行 00:07」 | PASS |
| `27-theme-light.png` | 设置页切浅色后立即生效 | PASS |
| `28-theme-light-after-restart.png` | 浅色强停重启后保持 | PASS |
| `34-courses-empty.png` | 课程页 0 节空态：选择文件夹、说明、无假课程 | PASS |
| `39-after-add3.png` | 新增场景「QA Test」出现在首页 | PASS |
| `46-shortcut-coldstart.png` | 快捷方式 Intent 冷启动直达「滴眼药水」计时（无课程自动仅倒计时） | PASS |
| `53-theme-restored.png` | 主题恢复「跟随系统」且重启后深色 | PASS |

### 控件树（xml/）

`02`、`07`、`17`、`20`、`22`、`33`、`39`、`44`、`45`、`46` 的 `uiautomator dump` 原文，用于复核文案与坐标；计时页 dump 在每秒刷新时多次返回 `could not get idle state`，按 Skill 阶段 0.8 判为不可用，改用截图 + dumpsys 交叉，未据此下结论。

### 日志（logs/）

| 文件 | 内容 |
|---|---|
| `test.log` | `./gradlew :app:testDebugUnitTest --rerun-tasks --no-daemon`，BUILD SUCCESSFUL（未打印用例数）；用例数由 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 复核为 14 类 76 条、0 失败 0 错误 0 跳过，见 `logs/unit-test-summary.txt` |
| `build.log` | `:app:testDebugUnitTest :app:assembleDebug --no-daemon`，BUILD SUCCESSFUL |
| `rebuild-clean.log` | 临时移出唯一未跟踪配置文件后，从 HEAD `8915d0dd` 执行 `assembleDebug --rerun-tasks`，BUILD SUCCESSFUL；产物 SHA=`76b6b449…`（本文件不含 shasum 行，哈希以 `logs/` 与 `.local/qa-regression-2026-11-14/*.apk` 实测为准） |
| `backup-20261114.json` | 设置页「导出备份」产物：version=1、presets=2、courses=0、sessions=21、settings.theme=LIGHT（导出时） |

## 2. 真机实测轮次

| 轮次 | 操作 | 证据 | 结果 |
|---|---|---|---|
| R1 前台 2 分钟 | 首页「仅倒计时」→ 等待到点 | `07`、通知 6901、`USAGE_ALARM` 12:10:43.316、session id=18 | PASS，started→ended 120.194s |
| R2 手动结束 | 滴眼药水 3 分钟 → 暂停 → 继续 → 提前结束 → 记入 | `16`、session id=19（listened=70, completed=0） | PASS |
| R3 锁屏 2 分钟 | 开始 → 熄屏 → 等待到点 → 唤醒 | `19`、`20`、通知 6901、`USAGE_ALARM` 12:16:24.359、session id=20 | 到点 PASS；锁屏首屏可见性 PARTIAL |
| R4 后台 2 分钟 | 开始 → Home 键 → 等待到点 | 通知 6901、session id=21 | PASS，120.087s |
| R5 强停恢复 | 开始 7 秒 → force-stop → 重启 | `22`、`timer_session.xml` 为空、会话数不变 | PASS |
| R6 主题持久化 | 跟随系统 → 浅色 → 强停重启 → 恢复跟随系统 | `27`、`28`、`53`、`settings.preferences_pb` theme=LIGHT/SYSTEM | PASS |
| R7 备份往返 | 导出到 Download → 解析 → 导入同文件 → 回读 | `33`、`backup-20261114.json`、恢复前快照 | PASS（2 场景 / 0 课程 / 21 会话不变） |
| R8 场景 CRUD | 新增 QA Test → 改名 QA Edit → 删除 | `39`、`44`、`45`、presets 恢复 2 条 | PASS |
| R9 快捷启动 | force-stop → `am start` 带 `START_PRESET`+presetId=2 | `46`、通知 6900、alarm 3 分钟 | PASS（无课程自动仅倒计时） |

## 3. 未测 / 阻塞

- 真实课程导入、MP3/视频播放、断点续播、自动下一节、无音轨提示、锁屏有声播放：接手轮逐台只读核查，四台在连设备 `/sdcard`（maxdepth 3）均未找到 mp3/mp4；Note 11T Pro 的 App 库 courses=0、sessions=0。真实素材位置需用户指定；未用合成素材冒充。
- 首次安装：本机已装 0.2.8，卸载重装会丢 21 条历史；未执行。
- 跨机型复测：接手轮只在 Note 11T Pro+（Android 15）补首屏/安全区/返回键；OPPO 被 `codex` profile 占用；Note 11T Pro 仍装 0.2.7(18)，未覆盖升级。
- AC08 每状态 10 轮：本轮只做前台/锁屏/后台各 1 轮，不能折算为 10 轮。
- 勿扰、OEM 省电、精确闹钟授权后复测、系统真实撤回 SAF 授权：未测。
- Note 11T Pro+ 通知权限（允许通知/锁屏/发声/振动）四项全关，不能用于到点提醒正向验收；未代改系统设置。

## 4. 清场

- 删除 `/sdcard/ui.xml` 与 `/sdcard/Download/fragment-time-backup-20261114.json`（本轮测试产物）。
- `stay_on_while_plugged_in` 已复原为 `0`；`screen_off_timeout` 原值 `600000` 未改。
- `reverse` 为空；`forward` 中 OPPO 的两条映射非本轮创建，未动。
- 设备无 `.test` 包；应用无活动会话；主题恢复跟随系统；新增测试场景已删除。
- 设备锁 `codearts-069` **未释放**：实测仍绑定 model `23054RA19C`（label 错写「Redmi Note 12T Pro」）。下次使用前按 `status → resolve → adb -s`；需要归还时由用户或后续会话执行 `release --profile codearts-069`，并先按真实型号重绑标签。
- 测试新增的 6 条历史保留在应用库中（id=18–21 实测，另 2 条手动结束未记入/未入表），未删用户数据。