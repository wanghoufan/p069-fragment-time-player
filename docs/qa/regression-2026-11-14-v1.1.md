# P069 碎片时间播放器｜回归检查体系 V1.1 首次试运行报告

> 执行日期：2026-11-14（Asia/Shanghai）
> 执行者：CodeArts（项目开发智能体），按 `003-体系管家/检查体系清单` V1.1 执行；15:55 起由 OpenCode 接手（§12）
> 被测版本：**`0.2.10(23)`**（首轮为 `0.2.8(21)`，0.2.9/0.2.10 为同一轮内修复后的候选包）
> 0.2.10 APK SHA-256 `af913c0177bd0d29722465bc9b7ddd1872e8e3cec016839e585bcf1a7f78d368`，构建 commit `83b11ec`
> 设备：Redmi Note 12 Pro（22101316C，Android 14 / SDK 34）；接手轮在 Redmi Note 11T Pro+（23054RA19C，Android 15 / SDK 35）补测
> 证据目录：`docs/qa/evidence-2026-11-14-regression/`（0.2.10 段见 `0.2.10/README.md`）
> 结论：**不通过（P0 未全绿）**。当前状态 `LOCAL_TESTED`，不得写 `READY_FOR_USER_ACCEPTANCE` 或 `ACCEPTED_BY_USER`。
> 接手轮复核（2026-11-14 13:10–13:30）：修正设备与矩阵口径，新增返回键缺陷，门禁探针实测见 §10。
> 0.2.10 修复轮（14:44–16:00）：到点通知进入 Alerting 分区，锁屏首屏仍不可见但已定性为设备/ROM 行为（§12）。

## 1. 执行摘要

本轮不是重新开发，而是用真实项目验证 V1.1 是否能发现问题。共完成 9 轮真机操作、76 条 JVM 单测、一次干净工作区重建、一次备份往返、一次场景 CRUD、一次快捷方式冷启动。

**发现并确认的 P0 级问题（3 类）**：

1. **构建来源错位**：`VERSION.md` 与软件台账把 0.2.8 的构建 commit 记成 `756e1715-dirty`（这是 0.2.5 的功能提交），0.2.8 的功能实际在 `a74c8235`。出包时功能尚未提交，包无法对应功能 commit。
2. **包不可字节复现**：临时移出唯一未跟踪配置备份后，从 HEAD `8915d0dd` 干净重建，产物 SHA 为 `76b6b449…`，与现包 `779c8433…` 不同；同 versionCode 出现两个不同包，任何登记都会被 `E_DUPLICATE_VERSIONCODE` 拒绝。
3. **交接文档反向**：`HANDOFF.md` §1 写「代码未提交」，§3 写「已推送 a74c823」；`architecture-facts.md` 仍停在 0.1.9；验收矩阵仍写 0.2.7/0.2.5。三处与设备实际 0.2.8 不一致。

**本轮未发现应用代码 P0 缺陷**：倒计时、到点提醒、后台/锁屏计时、强停恢复、主题持久化、备份往返、场景 CRUD、快捷启动均通过；暂停/继续实为自动化坐标偏差，非产品缺陷（见 §5.1）。**接手轮补测发现 1 个 P1 代码缺陷：返回键没有页面栈（见 §10.2）。**

**V1.1 体系有效性**：G1（构建来源）、G2（六态对账）、G3（设备锁）三类新增门禁确实抓到了历史复发问题；但 279 项中 132 项对单一 Android 离线 App 为 N/A，P0 适用项仍达 108 项，存在明显重复与颗粒度不均（见 §7）。

## 2. 阶段 A：检查体系适用性验证

完整逐项结果：`docs/qa/regression-matrix-2026-11-14.csv`（279 行，含适用性、状态、理由、证据指针）。

> 下表是**首轮填写结果**，保留作对照；接手轮逐项纠偏后为 PASS 68 / PARTIAL 45 / FAIL 14 / BLOCKED 10 / NOT_RUN 9；0.2.9 修复后最终为 **PASS 80 / PARTIAL 45 / FAIL 2 / BLOCKED 10 / NOT_RUN 9**（适用 146 / N/A 133，见 §11.5）。

| 适用性 | 数量 | 说明 |
|---|---|---|
| 适用 | 147 | G 30 + UX 32 + AND 33 + MEDIA 6 + DATA 10 + OPS 7 + R 33（部分项按 P069 收窄） |
| N/A | 132 | WEB 23、PWA 24、DESK 14、AI 20，以及 AND/MEDIA/DATA/OPS 中相机、录音、云同步、容器、自动化、跨程序转发等无对应能力的项 |
| 合计 | 279 | — |

| 状态 | 数量 | 其中 P0 |
|---|---|---|
| PASS | 77 | 60 |
| PARTIAL | 36 | 25 |
| FAIL | 10 | 10 |
| BLOCKED | 10 | 10 |
| NOT_RUN | 14 | 3 |
| N/A | 132 | — |

### 2.1 找出的重复、冲突与不可执行项

| 类型 | 例子 | 问题 | 建议 |
|---|---|---|---|
| 重复 | WEB-010 与 PWA-003/PWA-022；AND-004 与 AND-032；AND-003 与 R-001/R-003 | 同一「国产浏览器安装」「设备锁/前台」「干净 commit/包哈希」被写 2–3 次 | 保留一条强表述，其他改指针；否则不同轮次容易只跑一条就报「已覆盖」 |
| 冲突 | UX-010 要求「首次浅色」，而项目 `SettingsRepositoryImpl` 默认 `SYSTEM`；产品计划 §3 也写「默认浅色」 | 清单已注明「用户偏好优先」，但 P069 从未有用户对首装默认色的新决定；本轮按现状 SYSTEM 记录，未擅自改默认 | 由用户拍板首装默认色；在此之前清单与项目应统一写「按项目已批准偏好，未批准则 SYSTEM」 |
| 不可执行 | G-008「查询 25 套模板并标采用/借鉴理由」；G-028「Skill 各端可发现/实跑」；R-017「CI 记录」 | 对单人离线 App，模板/CI/多端部署无对应基础设施，逐项做会变成文档表演 | 按形态自动降级为「不适用或抽样」，并提供降级判据 |
| 描述不明确 | R-028「发布与版本登记脚本对 dirty 直接 FAIL」 | 现行 `register-version.mjs` 对 `-dirty` 只提示风险、仍登记；清单假设了一个并不存在的行为 | 要么改脚本（需用户授权），要么把条款改写为「登记后可被检索标红」，不得假设已实现 |
| 判据缺失 | AC08/AND-007「提醒可靠性」 | 清单要求「实测」，但未给每状态轮次、延迟阈值、失败计数口径；项目计划给了 10 轮，清单没引用 | 清单应给出可继承的项目 AC 阈值，不另造一套 |

### 2.2 遗漏的 P069 历史问题

V1.1 未明确覆盖、但本项目真实复发或仍在的风险：

1. **MIUI 锁屏首屏可见性依赖「发声权限 + 锁屏通知」**：清单只在 AND-007 泛写「屏幕锁定下实际行为」，没有把「系统权限前提」与「产品缺陷」分开；本轮锁屏首屏确实没看到通知，下拉才可见。
2. **同 versionCode 同 SHA 的包被覆盖重建后 SHA 变化**：清单区分了「可追溯」与「字节级可重复」，但没有要求保存**原始包副本**；本轮靠人工先备份才保住原包证据。
3. **`-dirty` 构建在出包当时是准确记录，但事后无法对应功能提交**：清单要求「不拿 dirty 包签收」，但未要求出包时同时记录「功能提交 SHA」与「构建 HEAD」两个字段，导致事后只能靠重建推断。
4. **测试新增数据留存在用户库**：清单有「不碰生产数据」，但对单机 App 的「测试会产生真实历史」没有清场口径；本轮保留 6 条测试历史并如实说明。

### 2.3 本项目 P0 矩阵

- 首轮填写：P0 适用 108 项：PASS 60、PARTIAL 25、BLOCKED 10、FAIL 10、NOT_RUN 3。
- 接手轮纠偏后：P0 适用 108 项：PASS 51、PARTIAL 34、BLOCKED 10、FAIL 12、NOT_RUN 1（见 §10.5）。
- 0.2.9 修复后：P0 适用 108 项：PASS 61、PARTIAL 34、BLOCKED 10、FAIL 2、NOT_RUN 1（见 §11.5）。
- 未通过 P0 的集中原因：真实课程/播放链（BLOCKED）、交付证据链（FAIL）、AC08 轮次不足（PARTIAL）。
- 详细逐项见 CSV；本报告只展开会阻断交付的项。

## 3. 阶段 B：当前产品检查

### 3.1 通过项（有本轮证据）

| 检查 | 判据 | 实际 |
|---|---|---|
| 首屏信息架构 | 两个场景 + 当前课程 + 空态出口，无需滚动 | `01-launch.png`：全部首屏可见 |
| 无课程错误路径 | 点开始失败时说明原因并给恢复出口 | `02-start-no-course.png` + xml：原因 + 重试 + 仅倒计时 |
| 倒计时显示 | 大号等宽数字，前后台一致 | `03/04`：01:48→01:28，间隔与系统时间一致 |
| 到点提醒 | 单次通知、高重要性、alarm 类别、无 SILENT | 6901 importance=4 category=alarm flags=0x10 vis=PUBLIC channel=timer_alert_v2 |
| 到点准确 | 与名义时长差 ≤1.5s | 前台 120.194s、锁屏 120.377s、后台 120.087s（名义 120s） |
| 到点入库 | 完成记录不重复、不丢失 | id=18/20/21 各 1 行，completed=1，listened=target |
| 手动结束 | 只一个「记入历史？」对话框，记入/不记入都回首页 | `16`、id=19；不记入轮次会话数不变 |
| 后台/锁屏计时 | 熄屏后仍按单调时钟推进 | 锁屏轮 `mWakefulness=Asleep`，到点准确 |
| 强停恢复 | 不假装仍在播放，显示真实经过时间 | `22`：「已进行 00:07」，无补记 |
| 主题 | 三态、立即生效、重启保持 | LIGHT→SYSTEM，`settings.preferences_pb` 与截图一致 |
| 备份 | 导出可解析；导入有影响提示、恢复前快照 | `backup-20261114.json` 6,496 B；导入后 2/0/21 不变 |
| 场景 CRUD | 增/改/删并刷新首页 | QA Test → QA Edit → 删除，presets 回到 2 条 |
| 快捷方式 | 动态注册、冷启动直达计时 | `dumpsys shortcut` 有 preset-1/2；`46` 直达滴眼药水 |
| 安全区/字号 | 关键文字不落系统栏，正文 ≥15sp、标题 ≥23sp | 首屏/计时/设置截图；Theme.kt 最小 14sp 仅用于非关键标签 |
| 版本一致 | 包内 versionName/Code 与应用「关于」 | APK 0.2.8(21)、设置页「版本 0.2.8」 |

### 3.2 未通过项

| 级别 | 问题 | 证据 | 影响 |
|---|---|---|---|
| P0 | 构建 commit 错位 + dirty 包 | VERSION 记 `756e1715-dirty`；0.2.8 功能在 `a74c8235`；干净重建 SHA 不同 | 不能作为正式验收包 |
| P0 | HANDOFF/架构事实/矩阵版本口径不一致 | HANDOFF §1 vs §3；architecture-facts 0.1.9；矩阵 0.2.7 | 接手者会按错版本推进 |
| P1 | 锁屏首屏看不到到点通知 | `19` 只有其他 App 媒体卡；`20` 下拉可见 | 用户锁屏时可能错过提醒（系统权限前提 + 通知排序） |
| P1 | 精确闹钟未授权时仍依赖「窗口 + 90s」的不精确闹钟 | `SCHEDULE_EXACT_ALARM granted=false`；`dumpsys alarm` 窗口 1m29.9s | 长时 Doze 下可能延迟；当前靠 ticker + PARTIAL_WAKE_LOCK 兜底 |
| P1 | 无真实课程，播放链全部未测 | 本机 0 课程；真实素材在离线设备 | AC-01/04/05/08 的关键部分无法验证 |
| P2 | 仅倒计时完成页仍写「课程进度已保存」 | `07-finished.png` | 文案与实际不符 |
| P2 | 设置写入 IOException 被静默吞掉 | `SettingsRepositoryImpl.kt:61-66` | 极端情况下开关看似成功但未落盘 |
| P2 | 错误卡片出现后首页底部内容贴近底栏 | `02` 中「当前课程」下沿接近导航栏 | 需滚动，不影响主任务 |

## 4. 阶段 C：真机测试结果

设备与门禁回执：

```text
【前置体检回执】
体检是否执行：已执行
逐项结果：①灭屏 通过（原 600000ms，未改；测试期间曾临时 stay_on=3，收尾已复原 0）
②端口 通过（无本地服务；reverse 空，forward 仅他人 OPPO 映射未动）
③服务 通过（纯本地 App，无服务端；不适用）
④反向映射 通过（无本项目映射）
结论：允许开跑

【MIUI 专项门禁回执】
前台包名：com.wanghoufan.fragmentplayer 已核对（每次 input 前查 mCurrentFocus）
测试 APK：未安装
文件选择器：无障碍点击（导出保存对话框直接点「保存」；导入点文件卡成功）
系统通知权限：允许通知=on 锁屏=on（下拉可见） 发声=on（USAGE_ALARM 有声） 振动=on（历史）
媒体完整性：本机完整课程文件 0 个；排除 .downloading/.part 0 个（无课程目录）
结论：可信；锁屏首屏可见性受通知排序/系统展示影响，另记 PARTIAL

【计时准确性回执】
- 起算点：点开始（PREPARING 也按点击时刻递减；起播完成后再进 RUNNING，准备耗时计入）
- 显示口径：向上取整；刷新间隔 250ms
- 后台到点：setAlarmClock（授权时）/ setAndAllowWhileIdle（未授权，本机）；精确闹钟 deny；唤醒锁有（PARTIAL_WAKE_LOCK，剩余+10s）
- 前台轮：T1=12:08:43.195 T2=12:10:43.389 delta=120.194s（名义 120s）
- 锁屏轮：T1=12:14:24.037 T2=12:16:24.414 delta=120.377s（名义 120s；mWakefulness=Asleep）
- 后台轮：T1=12:18:03.791 T2=12:20:03.878 delta=120.087s（名义 120s）
- 倍率：1×
- 结论：PASS（三项均 ≤1.5s）
```

UI 取证：计时页 `uiautomator dump` 多次返回 `could not get idle state`，按阶段 0.8 判不可用；改用截图 + `dumpsys notification/audio/alarm` + 数据库回读交叉，未用旧 XML 下结论。

### 4.1 关键证据链

- 包/设备/版本：`aapt2 dump badging` versionName=0.2.8 versionCode=21；`dumpsys package` lastUpdateTime=2026-11-13 13:40:24；应用内「版本 0.2.8」。
- 单测：`./gradlew :app:testDebugUnitTest --rerun-tasks` → 76/76，`logs/test.log`。
- 到点通知：`dumpsys notification` 6901；`dumpsys audio` 12:10:43.316 / 12:16:24.359 出现 `USAGE_ALARM`。
- 数据库：`timer_sessions` id=18/20/21 completed=1；id=19 listened=70 completed=0。
- 备份：`backup-20261114.json` version=1、presets=2、courses=0、sessions=21。

## 5. 阶段 D：缺陷整改与复测

### 5.1 已修复/已澄清

| 问题 | 处置 | 复测 |
|---|---|---|
| 「暂停计时」点击两次未生效 | 定位为自动化坐标偏差：运行时按钮实际中心在 y≈780，首次按截图目测点 y=630 落到空白区。改用 dump/回读定位后，暂停与继续均生效（`05/06`、`15`） | 复测 PASS；未改代码 |
| 主题切「跟随系统」时误点语言区 | 定位为滚动后坐标复用；按 dump 重定位到主题行后生效 | `53` + `settings.preferences_pb` theme=SYSTEM，PASS |
| 首页错误卡片下布局整体下移 | 属错误态正常占位；点击前必须重新 dump，已在执行纪律中记录 | 不构成产品缺陷 |

### 5.2 未修复（P0/P1，需授权或需素材）

| 问题 | 为什么没修 | 最小下一步 |
|---|---|---|
| 构建来源错位 + dirty 包 | 修它需要 commit（项目红线：commit 需编排者明确指令）并 bump versionCode；本轮无授权 | 用户授权：把唯一未跟踪配置备份归位/加 ignore → bump `versionCode=22` → commit → 构建 → 登记 → 装机复测 |
| HANDOFF/架构事实/矩阵过期 | 接手轮已按实测同步（见 §10），但仍未提交 | 随 vc22 commit 一并入库；push 另行确认 |
| **返回键无页面栈（P1，接手轮新增）** | 需改 `MainActivity`/`FragmentTimeRoot` 的 BackHandler 并补单测；修复必须重新构建，而现 vc21 不可再出同号包 | 并入 vc22：非首页回上一级、计时中给提示、仅 IDLE 首页双击退出；补单测 + 真机复测 |
| 锁屏首屏通知不可见 | 需确认 MIUI 通知「锁屏显示」开关与通知排序；属系统设置前提，未擅改用户设置 | 用户在系统设置确认「锁屏通知 + 发声」后复测；若开关已开仍不显示，再按产品缺陷排查 |
| 精确闹钟未授权 | 需要用户在系统设置授权，不能代操作 | 设置页「去授权」→ 允许后重跑锁屏 3 分钟轮 |
| 真实课程播放链 | 真实素材在用户自用 12T Pro（离线），本机无授权样本 | 接回 12T Pro 或把 13 节 MP3 复制到本机后复测 |
| 仅倒计时完成页文案 | 改字符串需重新构建+登记，且当前包不可签收，先不改以免扩大未追溯变更 | 并入 vc22 修复批次 |
| 设置写入静默失败 | 同上，属 P2，并入 vc22 | 在 `write` 失败时回滚 UI 或提示 |

### 5.3 复测结论

- 修复类（坐标/流程）均已复测通过，无回归。
- 未修复类不冒充通过；相关 AC 在矩阵中标 BLOCKED/FAIL/PARTIAL。

## 6. 交付门禁专项检验（8 条）

| # | 检验点 | 实测 | 结论 |
|---|---|---|---|
| 1 | 构建前源码状态可追溯 | 出包时 HEAD `756e1715` + dirty；0.2.8 功能未提交 | FAIL |
| 2 | 构建提交是否真含本轮功能 | `756e1715` 是 0.2.5 提交；0.2.8 功能在 `a74c8235` | FAIL（R-027 命中） |
| 3 | 正式验收包是否来自确定源码 | 当前包无法对应功能 commit；干净 HEAD 重建 SHA 不同 | FAIL |
| 4 | APK SHA 是否对应验收包 | SHA `779c8433` 与 VERSION 一致，但该包不可由 HEAD 复现同字节 | PARTIAL（R-003 过；R-001 不过） |
| 5 | 本地提交/远端/设备分别记录 | 本地 HEAD `8915d0dd`；远端 origin/main 落后 1（最新 docs 未推）；设备三台 0.2.8(21)、Note 11T Pro 为 0.2.7(18) | 已分列；设备安装态与「四机同包」不符 → PARTIAL（R-026） |
| 6 | HANDOFF/VERSION/构建配置/台账冲突 | 四处口径：VERSION=756e1715-dirty、build.gradle=0.2.8(21)、HANDOFF 自相矛盾、架构事实=0.1.9 | FAIL |
| 7 | 测试记录绑定正确版本 | 本轮证据绑定 0.2.8(21)+SHA+设备+时间；旧 AC 仍引用 0.2.0/0.2.5 | PARTIAL |
| 8 | 未提交/dirty 构建能否被识别并阻断 | 旧脚本只提示不阻断；本轮靠人工重建才发现 | FAIL（R-028 未实现） |

**安全失败场景验证**：

- 临时移出唯一未跟踪文件 → `git status` 为空 → 干净重建 → 得到不同 SHA。证明：同 versionCode 下「原包 ≠ 干净重建包」，若登记会触发 `E_DUPLICATE_VERSIONCODE`（退出码 3），符合「同号不同包必须拒绝」。
- 未改动生产数据、未 push、未发布、未删历史。

## 7. V1.1 检查体系有效性

### 7.1 确实发现问题的检查

| 检查 | 在 P069 的实际命中 | 价值 |
|---|---|---|
| R-027（登记 commit 必须含功能变更） | 命中 0.2.8 的 commit 错位 | 高：单靠「包能装、单测绿」永远发现不了 |
| R-001/R-003（干净源码 + SHA 认包） | 命中 dirty 包与同号不同包 | 高：必须有原始包副本，否则证据被覆盖 |
| R-016/R-026（唯一 HANDOFF + 六态） | 命中 HANDOFF 自相矛盾、架构事实落后 | 高：接手成本直接下降 |
| AND-003/AND-004/AND-032（构建/设备锁/前台） | 设备锁与前台归属有效防止串台 | 高：本轮未误操作他机 |
| AND-007/AND-008/AND-036（提醒/后台/精确闹钟） | 命中「未授权精确闹钟」与锁屏首屏可见性 | 高：把系统前提与产品缺陷分开 |
| UX-032（设置写后读回） | 主题、震动、铃声、字号均有消费点 | 中：未发现死开关 |
| DATA-026（备份可恢复） | 备份往返 + 恢复前快照 | 高：不是「文件存在」 |

### 7.2 重复/低效项

- 同一事实最多出现 3 次（浏览器安装、设备锁、包哈希），执行时容易只跑一条就填多条 PASS。
- 132 项 N/A 占比 47.3%（接手轮纠偏后 133 项 / 47.7%），说明「全类型清单」对单形态项目仍需人工筛；筛的过程本身耗时接近真机测试。
- P0 适用 108 项偏多，其中约 20 项是治理/文档项，和「首次可用交付」关系弱，容易把开发拖进文档工作。

### 7.3 建议修改（仅建议，未改中央文件）

1. **清单增加「项目形态 → 默认 N/A 包」**：Android 离线单机 App 默认排除 WEB/PWA/DESK/AI 与云端 DATA/OPS，减少人工判断。
2. **把 R-027 拆成两个字段**：「出包时 HEAD」与「功能提交 SHA」，允许不同；并要求保存原始包副本（含 SHA 与 mtime）。
3. **R-028 改为可执行前置**：在 `register-version.mjs` 增加 `--require-clean` 与「同号不同包」阻断（已有后者），前者需用户授权后实施。
4. **锁屏提醒条款拆分**：系统权限前提（用户设置）/ 产品行为（通知是否发出）/ 展示排序（锁屏首屏是否可见）三段分别判定，避免把系统设置写成产品 FAIL。
5. **清单与项目 AC 对齐**：AC08 这类「10 轮」阈值应由项目计划提供，清单只引用，不另造数字。

## 8. 最终交付状态

| 态 | 事实 | 证据 |
|---|---|---|
| 本机可测 | 是；9 轮真机 + Android 15 补测 + 0.2.9 真实课程复测 + 0.2.10 通知分区复测 + 79 单测 | 本报告 §4、§10、§11、§12 |
| 源码已提交 | 是：`5cf5842`(0.2.9) + `83b11ec`(0.2.10) + 本轮 docs 提交（含 `VERSION.md`、证据、报告） | `git log` |
| 远端已推送 | **是**：`ecffad2..1a61651` 已推 origin/main，本轮 docs 提交随后同批推 | `git log origin/main` |
| 设备已安装 | **两台 0.2.10(23)**（Note 12 Pro、Note 11T Pro+）；OPPO 0.2.8、Note 11T Pro 0.2.7 | `VERSION.md`、`dumpsys package` |
| 正式已验收 | **否**：AC-08 10 轮/首装等未测，用户未签收 | AC-12 |

**当前允许的表述**：`LOCAL_TESTED`（0.2.9 为待验收候选包）。
**不允许的表述**：`READY_FOR_USER_ACCEPTANCE`、`RELEASED`、`ACCEPTED_BY_USER`。

## 9. 需要用户拍板

A1/B1/E1 已执行：本地 commit（未 push）、0.2.9 干净构建/登记/装机复测、设备锁重绑。0.2.10 修复与复测见 §12。剩余：

1. **push**：是否把 `main` 上的本地提交推到 origin/main（本轮只做 commit，未 push）。
2. **AC-08 与首装**：是否补每状态 10 轮和首装，或明确接受延期并进入签收。
3. **换机复测锁屏可见（新）**：Note 12 Pro 的锁屏不渲染任何第三方通知（含对照通知），需换一台锁屏通知正常的机型，或由用户在本机开启/确认 MIUI「锁屏通知」后再测；否则 AND-007 只能维持 PARTIAL。
4. **通知权限（D1）**：Note 11T Pro+ 允许通知/锁屏/发声/振动全关且勿扰开启；需用户开启后复测到点可见，否则记 BLOCKED。
5. 最后由用户逐条签收 AC-12。

## 10. 接手轮复核（2026-11-14 13:10–13:30）

上一轮在报告正文之外还留下了三处与实测不符的口径；本轮逐项回源修正，并把可复跑的探针结果落盘。

### 10.1 设备与设备锁

| 事实 | 上一轮写法 | 本轮实测 | 证据 |
|---|---|---|---|
| 四机安装版本 | 四台均 0.2.8(21) | 三台 0.2.8(21)；21091116AC（Note 11T Pro）为 **0.2.7(18)** | `logs/device-inventory-2026-11-14.txt` |
| 设备锁绑定 | profile 锁 Redmi Note 12 Pro，收尾已 release | 实际绑定 `model=23054RA19C`（可读型号 Note 11T Pro+），label 误写「Redmi Note 12T Pro」，且**未 release** | `logs/device-lock-state-2026-11-14.txt` |
| 测试机与锁 | 声称一致 | 上一轮实测机为 22101316C（Note 12 Pro），不在锁绑定内 | 同上 |

结论：**V1.1 的 G3 设备锁门禁上一轮实际未通过**，矩阵却标为 N/A/PASS；本轮把 G-013、AND-004、AND-032 改判，并把「锁标签与实际型号不一致」作为 Skill 缺陷单独记录。

### 10.2 新增产品缺陷：返回键没有页面栈

- 复现：Android 15 机「计时完成」页连续按两次 BACK（间隔 >2s），界面均不变，仍停完成页（`android15-recheck/02`、`03`）。
- 代码：`MainActivity.kt:52` 只有一个全局 `BackHandler { exitBackGuard.onBackPressed() }`；`ExitBackGuard` 仅实现 2 秒双击退出，不知道当前 Tab 与计时状态。
- 影响：完成页、设置页、课程页、计时中按返回都不会回上一级，与中央 APP 规范「非首页返回上一级」冲突；属 P1。
- 状态：**未修**（需随 vc22 一起构建、登记、装机复测）。

### 10.3 Android 15 补测结果

- 安全区：标题 `[55,177][331,277]`，底部 Tab 文字 `y=2226–2302`，导航栏自 2337 起，均未落入系统栏 → PASS。
- 字号：标题约 36sp、正文约 29sp → 满足下限。
- 通知权限：允许通知/锁屏/发声/振动 **全部 off** → 该机不能作为到点提醒正向验收机，记 BLOCKED（未代改系统设置）。
- 完成页文案「到点完成，课程进度已保存」在 0 课程场景复现 → P2 未修。

### 10.4 发布门禁探针（可复跑）

| 探针 | 命令 | 结果 |
|---|---|---|
| 同号不同包 | `register-version.mjs --dry-run` 登记 `76b6b449`（vc21） | 退出码 **3**，`E_DUPLICATE_VERSIONCODE` → 自动阻断 PASS |
| dirty 阻断 | `--dry-run` 登记当前包（工作区有未提交 docs） | 退出码 **0**，走幂等；脚本仅把 dirty 拼进 commit 标签，无 `--require-clean` → **未实现** |

详见 `logs/gate-probe-2026-11-14.txt`。结论：R-028 只落实了一半——「同号不同包」有机器门禁，「dirty/来源不明」仍靠人工。

### 10.5 矩阵纠偏

上一轮矩阵存在「理由泛化、状态偏高」的问题。本轮按正文与实测回改以下项（其余 279 行保持原判）：

| 项 | 原状态 | 改判 | 依据 |
|---|---|---|---|
| G-013 | N/A | FAIL | 锁标签与物理机型不一致，且上一轮测试机不在锁内 |
| AND-004 | PASS | FAIL | 同上，未做到「精确锁定目标设备」 |
| AND-032 | PASS | FAIL | 同上，设备占用/版本核对不成立 |
| AND-005 | PASS | PARTIAL | 首次安装未做（卸载会丢 21 条历史） |
| AND-007 | PASS | PARTIAL | 锁屏首屏不可见；Note 11T Pro+ 通知权限全关 |
| AND-012 | PASS | PARTIAL | 上一轮只在 Android 14 测；Android 15 仅本轮补首屏与安全区 |
| AND-013 | PASS | PARTIAL | 首装默认色仍为 SYSTEM，用户未拍板 |
| AND-031 | PASS | PARTIAL | 本轮未做升级覆盖验证，仅历史证据 |
| AND-036 | PASS | PARTIAL | 精确闹钟 deny，未测授权后/重启重排 |
| R-003 | PASS | PARTIAL | 包有 SHA，但「对应源码」不成立（dirty 出包） |
| R-004 | PASS | FAIL | 远端未推、构建来源错位，四态未对齐 |
| R-016 | FAIL | PARTIAL | HANDOFF 已改真实状态，但仍与设备装机事实冲突 |
| R-026 | PASS | PARTIAL | 六态已列，但设备安装态写错（12T Pro 实为 0.2.7） |
| OPS-021 | PASS | FAIL | 上一轮未实际调用真机 QA Skill；本轮才实际加载 device-lock 与 QA orchestrator |
| G-008 | NOT_RUN | PASS | 25 套设计模板逐套核对，9 套交集已标采用/借鉴/不适用（`logs/design-template-review.md`） |
| G-009 | NOT_RUN | PASS | 中央登记 133 条、QA orchestrator 1.7.0 部署 11/11 已核对；3 个 Skill 实跑（`logs/skill-check-2026-11-14.md`） |
| OPS-016 | N/A | PASS | 本轮使用 3 个已登记 Skill，登记/部署/可发现/实跑分列 |
| OPS-018 | NOT_RUN | N/A | 本轮无 Skill 源或设计资产迁移/删除 |
| G-018 | NOT_RUN | N/A | 纯离线 App，无付费项 |
| R-017 | NOT_RUN | N/A | 无 CI，代码库不以 CI 为交付前提 |

修正后统计（`regression-matrix-2026-11-14.csv` 实算）：适用 146 项中 PASS 68、PARTIAL 45、FAIL 14、BLOCKED 10、NOT_RUN 9（N/A 133）。P0 适用 108 项中 PASS 51、PARTIAL 34、FAIL 12、BLOCKED 10、NOT_RUN 1。

### 10.6 本轮新增证据

- `docs/qa/evidence-2026-11-14-regression/android15-recheck/`（4 截图/XML + README，含阶段 0 / 0.6 / 0.8 / 0.5 回执）
- `logs/device-inventory-2026-11-14.txt`
- `logs/device-lock-state-2026-11-14.txt`
- `logs/unit-test-summary.txt`（14 类 76 条，0 失败，来源 test-results XML）
- `logs/gate-probe-2026-11-14.txt`

### 10.7 仍未闭环（0.2.9 前）

- 返回键、完成页文案、`SettingsRepositoryImpl.write` 静默吞异常：均已在 0.2.9 修复（见 §11）。
- 真实课程链：**接手轮一度误判为「四机均无素材」**；实际目标机 `/sdcard/Movies/第一章家庭与朋友/` 有 13 节 MP3，App 库 13 条，已在 §11.3 补测播放/断点/锁屏有声。
- 首装、双机恢复、AC08 每状态 10 轮、勿扰/OEM 省电：维持未测。
- commit / push / bump：A1/B1 已获授权执行到本地 commit 与登记，**push 未做**。

## 11. 0.2.9 修复与真机复测（2026-11-14 13:31–13:40）

### 11.1 修复内容

| 问题 | 改动 | 文件 |
|---|---|---|
| 返回键无页面栈（P1） | 新增 `resolveBackAction`：非计时 Tab 先回计时页，完成页回 IDLE，其余交给首页双击退出 | `ui/BackNavigation.kt`、`ui/FragmentTimeRoot.kt`、`ui/BackNavigationTest.kt` |
| 完成页文案（P2） | `countdownOnly` 时显示「到点完成」，有课程时才显示「课程进度已保存」 | `ui/screens/TimerScreen.kt`、`strings_timer_ui.xml` |
| 设置写入静默失败（P2） | `write` 返回 Boolean 并写 `Log.e`；失败时设置页 Snackbar 提示「设置保存失败，请重试」 | `SettingsRepositoryImpl.kt`、`Repositories.kt`、`CoursesSettingsViewModel.kt`、`strings_settings_ui.xml` |

提交顺序（保证功能提交早于构建）：`4313599` docs → `5cf5842` fix（含 bump 0.2.9(22)）。

### 11.2 构建与登记

| 项 | 值 |
|---|---|
| versionName / versionCode | 0.2.9 / 22 |
| 构建 commit | `5cf5842abc16db37ccdb43248ba6cae529338fba` |
| 工作区 | 构建前干净（`git status` 空） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`，17,927,397 B |
| SHA-256 | `4a05ddc00c5d2513fe3e4e4092e625d658183786aa8891c55683db4759593359` |
| 登记 | 软件台账 §H 2026-11-14 13:35:16；`VERSION.md` 已由脚本重写 |
| 单测 | 15 类 **79/79**，0 失败 0 错误 0 跳过 |

### 11.3 真机复测（Redmi Note 11T Pro+ / Android 15）

| 项 | 证据 | 结果 |
|---|---|---|
| 覆盖安装 0.2.8→0.2.9 | `install -r` Success；`dumpsys package` 0.2.9(22)，lastUpdateTime 13:31:52 | PASS |
| 数据保留 | 装前 2 场景 / 13 课程 / 10 会话 → 装后同值，checkpoint 1 条 | PASS |
| 真实课程播放 | `media_session` PLAYING，`6.照顾宠物（上）.mp3`，从断点 149,238ms 续播 | PASS |
| 到点完成 | 120s 到点；通知 6901 importance=4 category=alarm；完成页「本次 2 分钟」 | PASS |
| 完成页返回键 | BACK 一次回到 IDLE 首页（`06-back-to-idle`） | PASS（修复生效） |
| 设置页返回键 | 设置 Tab → BACK → 回计时首页（`02/03`） | PASS（修复生效） |
| 锁屏有声 | `mWakefulness=Asleep`，仍 PLAYING，position 274,463→289,599 | PASS |
| 提前结束 | 只弹「记入历史？」，选记入后回 IDLE；会话 id=12 listened=163 completed=0 | PASS |
| 断点保存 | checkpoint courseId=6 positionMs=428,810（07:08） | PASS |
| 完成页文案（有课程） | 「到点完成，课程进度已保存」 | PASS |
| 完成页文案（无课程） | 仅代码路径验证（countdownOnly 分支）；未在无课程场景真机复现 | PARTIAL |

### 11.4 Note 12 Pro 提醒正向复测（Android 14）

覆盖安装 0.2.9(22)（lastUpdate 13:56:03）后实测两轮：

| 轮次 | 条件 | 结果 |
|---|---|---|
| 前台 2 分钟 | 应用前台，无课程 → 仅倒计时 | 到点通知 6901：importance=4、category=alarm、flags=0x10、vis=PUBLIC；`USAGE_ALARM` 有声；完成页「到点完成」（无课程文案修复生效） |
| 锁屏 2 分钟 | 熄屏等待到点，亮屏立即截锁屏 | 通知 6901 在系统记录中存在；**锁屏首屏仍只显示其他 App 媒体卡与 USB 通知，看不到 P069「时间到了」** |

系统设置页实测（`32-12pro-notif-settings.png`）：允许通知=开、悬浮通知=开、锁屏通知=**开**、发声=**开**、振动=开、常驻通知=开；`POST_NOTIFICATIONS granted=true`，勿扰 `zen_mode=0`。

**根因判断**：`dumpsys notification` 中该通知 `sound=null vibrate=null groupKey=silent`。应用采用「通知静音 + MediaPlayer 自播铃声」方案，系统因此把通知归入 Silent 分区；Android 11+ 锁屏只优先展示 Alerting/媒体，Silent 分区不展示。**这不是权限问题，是产品通知策略问题**；历史 Note 11（Android 11）上「打开发声权限后可见」的结论不能外推到 Android 14/15。

处置建议（未改代码）：到点通知改用独立渠道并让通知自身携带 alarm 声音，或保留自播铃声但把通知标记为 `Alerting` 分区（需评估「关闭响铃」语义，不能简单叠加双声）。本轮仅记录缺陷，不擅自改动。

### 11.5 复核中的新事实与纠错

1. **真实课程素材就在目标机**：`/sdcard/Movies/第一章家庭与朋友/` 13 节 MP3，App 库 13 条、全部有音轨。上一轮与接手轮早段「无素材」的判断来自对其他机型的错误盘点（`21091116AC` 才是 0 课程），已纠正。
2. **目标机处于勿扰模式**：锁屏显示「勿扰已开启，来电和通知不再提醒」；通知权限四项仍全关。到点通知 6901 在系统记录中存在，但用户侧不可见，本机不能用于「到点可见」正向验收。
3. **自动化坐标必须用 dump bounds**：按截图目测点「暂停计时」偏差约 146px，落到倒计时数字区，导致首测「暂停无效」；改用 dump bounds（y≈742）后一次命中。此坑与上一轮「暂停坐标偏差」同源。

### 11.6 状态更新

矩阵 `regression-matrix-2026-11-14.csv` 实算：

| 口径 | PASS | PARTIAL | FAIL | BLOCKED | NOT_RUN | N/A |
|---|---|---|---|---|---|---|
| 首轮填写 | 77 | 36 | 10 | 10 | 14 | 132 |
| 接手纠偏 | 68 | 45 | 14 | 10 | 9 | 133 |
| 0.2.9 修复后 | **80** | **45** | **2** | **10** | **9** | **133** |

P0（适用 108）：PASS 61、PARTIAL 34、FAIL 2、BLOCKED 10、NOT_RUN 1。
剩余 FAIL 仅 `R-005`（关键 AC 未全绿）与 `R-025`（未达「相对完善可交付」）。
门禁专项 8 条：构建来源 3 条由 FAIL 转 PASS；同号不同包 PASS；六态 PARTIAL（远端未推）；dirty 阻断仍 PARTIAL。

### 11.7 交付判断

- 构建来源、包身份、设备锁、返回键四项已闭环，证据可复跑。
- 仍不能写 `READY_FOR_USER_ACCEPTANCE`：AC-08 每状态 10 轮未达、首装未做、无音轨提示未测、用户未签收。
- 当前状态仍为 `LOCAL_TESTED`；0.2.9 可作为「待用户验收候选包」，但不得称已验收。
## 12. 0.2.10 到点通知分区修复与锁屏复测（2026-11-14 14:44–16:00，接手轮）

本轮由 OpenCode 接手，接续上一开发者在 14:44 完成的 0.2.10 提交，把「锁屏首屏看不到到点通知」（R22）从
「只记录缺陷」推进到「修复 + 定性 + 可复核证据」。

### 12.1 根因（已用记录字段证实，不是推测）

0.2.9 的到点渠道 `timer_alert_v2` 自身静音，通知又 `setSilent(true)`，系统把 6901 归入 **Silent 分区**：

```text
0.2.9（改造前）：channel=timer_alert_v2  sound=null  groupKey=…|silent  mIsInterruptive=false
```

Android 11+ 锁屏首屏优先展示 Alerting／媒体，Silent 分区不进首屏——这与「权限是否开启」无关，
所以历史 Note 11 上「打开发声权限后可见」的结论不能外推到 Android 14/15（与 §11.4 判断一致）。

### 12.2 修复

| 项 | 改动 |
|---|---|
| 渠道 | `timer_alert_v2` → `timer_alert_v3`；渠道挂 0.1 秒静音 WAV（`res/raw/silence_alert.wav`），音频属性 `USAGE_ALARM` |
| 通知 | 去掉 `setSilent(true)`，加 `setOnlyAlertOnce(true)` |
| 兼容 | 创建新渠道时删除旧的 `timer_alert_v2` / `timer_alert`，避免历史渠道残留 |
| 语义保持 | 真实铃声仍由 `TimerAlertSound` 按用户「到点响铃」开关播放；渠道里的静音音频不发声，不产生叠音，也不改变「关闭响铃」语义 |

代码位置：`app/src/main/java/com/wanghoufan/fragmentplayer/timer/TimerNotifications.kt`。

### 12.3 构建与登记

| 项 | 值 |
|---|---|
| versionName / versionCode | 0.2.10 / 23 |
| 构建 commit | `83b11ece1c765a860b6c958e7518157744cfa485`（功能提交先于构建） |
| 工作区 | 构建前干净（提交 83b11ec 后无业务改动） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`，17,929,244 B，debug 签名 |
| SHA-256 | `af913c0177bd0d29722465bc9b7ddd1872e8e3cec016839e585bcf1a7f78d368` |
| 登记 | 软件台账 §H 2026-11-14 14:45:06；`VERSION.md` 由脚本重写（本轮补齐提交） |
| 装机 | Note 12 Pro 覆盖安装 `install -r`，`dumpsys package` 回读 0.2.10(23)，lastUpdateTime 14:45:06 |

### 12.4 回归测试

`./gradlew :app:testDebugUnitTest --rerun-tasks` → **79/79，0 失败 0 错误 0 跳过**（HEAD=`83b11ec`，`EXIT=0`）。

### 12.5 真机证据（Note 12 Pro / Android 14）

| 检查 | 判据 | 实测 |
|---|---|---|
| 渠道是否带声音 | `dumpsys notification` 渠道字段 | `timer_alert_v3` importance=4、`mSound=android.resource://…/2131755008`、`USAGE_ALARM`、`mDeleted=false`；`timer_alert_v2` 已 `mDeleted=true` |
| 通知是否进 Alerting | 记录字段 | `importance=4`、`category=alarm`、`mIsInterruptive=true`、`groupKey=0\|com.wanghoufan.fragmentplayer\|6901…`（不再落 silent）、`mSuppressedVisualEffects=0` |
| 到点准确性 | 名义 120s | 起点 15:43:37，通知创建 **15:45:37.315**（+0.3s） |
| 铃声未被改成静音 | 音频子系统 | 同刻 `player piid:4863 … usage=USAGE_ALARM` → `event:started`（15:45:37.367） |
| 完成页文案（无课程） | countdownOnly 分支 | 前台到点完成页显示「到点完成」，不再写「课程进度已保存」→ 0.2.9 的 PARTIAL 转 PASS |
| 锁屏首屏可见 | 熄屏到点后唤醒首屏 | **仍不可见**：首屏只有其他 App 媒体卡与 USB 通知 |

**关键对照实验（区分系统行为与应用缺陷）**：在同一锁屏状态下，用 `adb shell cmd notification post` 由系统 shell
发一条与 P069 无关的对照通知，**该通知同样不上锁屏首屏**。说明本机锁屏只渲染媒体会话卡与 USB 常驻通知，
其他通知一律不进首屏——这是设备/ROM 行为，不能判成应用缺陷，也不能用来证明应用通过。

证据：`docs/qa/evidence-2026-11-14-regression/0.2.10/`（4 张压缩截图 + `notification-evidence.txt`）；
原图与原始 dumpsys 在 `.local/qa-0.2.10b/`（本地不入库）。

### 12.6 判定

| 项 | 改前 | 改后 | 依据 |
|---|---|---|---|
| R22「通知被归入 Silent 分区」 | 缺陷成立 | **已修** | 记录字段从 silent 分区变为 Alerting（`mIsInterruptive=true`、渠道带声音） |
| AND-007「屏幕锁定下的实际行为」 | PARTIAL | **PARTIAL（原因改写）** | 应用侧行为已达可判定的最好状态；本机锁屏首屏不展示任何非媒体通知（含对照通知），缺一台锁屏通知正常的机器做正向验证 |
| AC-03 文案/单次通知 | PARTIAL | **PARTIAL** | 单次通知与铃声 PASS；「双通道不响两次」仍需长时轮次 |
| AC-08 到点轮次 | 8/10 + 1 轮前台 + 1 轮锁屏 | **同上，未增加达标轮次** | 本轮锁屏轮用于定性，不计入「可见」通过轮次 |

**不允许的表述**：不得写「锁屏首屏已修复」「锁屏可见已通过」。允许的表述是「通知已进入 Alerting 分区，
应用侧行为正确；本机锁屏不展示该类通知，需换机验证」。

### 12.7 对 V1.1 检查体系的补充（试运行产出，未改中央文件）

1. **对照法应当写进清单**：判断「通知/弹窗在锁屏、勿扰、省电下不出现」是应用缺陷还是系统行为时，先用
   `cmd notification post` 等**应用外来源**发一条同类通知做对照；本轮的结论完全依赖这条对照，否则会把 ROM 行为误判成产品缺陷。
2. **锁屏可见性需要「机型前提」字段**：清单应要求记录「本机锁屏是否渲染第三方通知」这一前提，前提不成立时该机不能作为正向验收机。
3. **通知分区应作为可判定断言**：`groupKey` 是否落 `silent`、`mIsInterruptive`、渠道 `mSound` 三个字段可以直接断言，
   比「肉眼看锁屏」稳定得多，应替代现有「屏幕锁定下实际行为」的模糊表述。

## 13. 0.2.10 交付门禁复检（8 条，2026-11-14 16:00 现读）

| # | 检验点 | 0.2.10 实测 | 结论 |
|---|---|---|---|
| 1 | 构建前源码状态可追溯 | 功能提交 `83b11ec` 先于构建（commit 14:44:27，APK mtime 14:44），该提交同时含 `TimerNotifications.kt` 与 `silence_alert.wav`；构建后无业务文件改动 | PASS |
| 2 | 构建提交是否真含本次功能 | `git show 83b11ec --stat` 三处：`app/build.gradle.kts`（版本 0.2.10/23）、`TimerNotifications.kt`、`res/raw/silence_alert.wav` | PASS |
| 3 | 正式验收包是否来自确定源码 | 包由 `83b11ec` 构建并登记；`VERSION.md` 与台账 §H 同一条（2026-11-14 14:45:06） | PASS |
| 4 | APK SHA-256 是否与实际验收包对应 | `shasum -a 256 app-debug.apk` = `af913c01…d368`，与 `VERSION.md`、台账 §H 逐字符一致；17,929,244 B | PASS |
| 5 | 本地提交/远端/设备分别记录 | 本地 HEAD `83b11ec`（+ 本轮 docs 提交）；`origin/main` 落后 9 个本地提交，**未 push**；设备：Note 12 Pro 0.2.10(23)，Note 11T Pro+ 0.2.9(22)，OPPO 0.2.8，Note 11T Pro 0.2.7 | PARTIAL（三态已分列，远端未推） |
| 6 | HANDOFF/VERSION/构建配置/台账冲突 | `build.gradle.kts` 0.2.10/23 = `VERSION.md` = 台账 §H = 设备回读；HANDOFF/矩阵/风险已同批改为 0.2.10 | PASS |
| 7 | 测试记录是否绑定正确版本 | §12 证据绑定 0.2.10(23)/`af913c01`/Note 12 Pro/时间戳；单测在 HEAD=`83b11ec` 上 `--rerun-tasks` 跑 79/79 | PASS |
| 8 | 未提交/dirty 构建能否被识别并阻断 | `register-version.mjs` 仍只把 dirty 拼进 commit 标签、不阻断（§10.4 探针：退出码 0）；「同号不同包」有机器门禁（退出码 3） | FAIL（R-028 仍未实现，需用户授权改脚本） |

**净结果**：0.2.8 时代 FAIL 的 1/2/3/6 四条已全部转 PASS；第 8 条是本轮唯一没有解决的交付门禁，属脚本能力缺口，不是本轮构建违规。

## 14. 五份交付物索引与一句话结论

| 交付物 | 落在哪 | 结论 |
|---|---|---|
| ① P069 回归测试报告 | 本文件 §1–§4、§10–§13 + `regression-matrix-2026-11-14.csv`（279 行逐项） | 适用 146 项：PASS 80 / PARTIAL 45 / FAIL 2 / BLOCKED 10 / NOT_RUN 9；N/A 133。P0 适用 108：PASS 61 / PARTIAL 34 / FAIL 2 / BLOCKED 10 / NOT_RUN 1 |
| ② 缺陷整改报告 | §5（首轮）、§10.2（返回键）、§11（0.2.9 三项）、§12（0.2.10 通知分区） | 0.2.9 修 3 项（返回键页面栈、完成页文案、设置写入静默失败）已真机复测；0.2.10 修 1 项（通知分区）应用侧达成，锁屏展示受机型限制；遗留 21 条 P2/P3 backlog |
| ③ V1.1 检查体系有效性报告 | §7、§12.7 | R-027/R-001/R-003/R-016/R-026/AND-003/AND-007/UX-032/DATA-026 抓到真实问题；132 项 N/A、P0 108 项偏多、同一事实重复 2–3 次；建议加「项目形态默认 N/A 包」「对照法」「通知分区可判定断言」 |
| ④ 自动化门禁建议 | §7.3、§13 | 可自动：干净工作区 + 版本号 + SHA + 台账/`VERSION.md` 一致性 + 同号不同包阻断（已实现）；建议补 `--require-clean`（需授权）；必须人工：真机锁屏/勿扰/OEM、首次安装、真实课程导入与播放 |
| ⑤ 最终交付状态 | §8、§13、§15 | 本机可测＝是；源码已提交＝是；远端已推送＝**是**；设备已安装＝**两台 0.2.10**；正式已验收＝**否**（勿扰/OEM、自动下一节、无音轨提示、锁屏首屏可见、用户签收） |

**本轮是否能报「验收通过」：不能。** 适用 P0 仍有 FAIL 2（R-005、R-025，均为「AC 未全绿 / 未达相对完善」的汇总判定）、
PARTIAL 34、BLOCKED 10、NOT_RUN 1。可报的是：0.2.10 作为候选包，构建来源、包身份、设备锁、返回键、通知分区五条已闭环且证据可复跑。

## 15. 拍板项执行（A1/B1/C1/D1，2026-11-14 17:00–）

用户回复「全 1，手机你自己操作」：A1 push、B1 补 AC-08 与首装、C1 换机复测锁屏、D1 由我在机上开权限。以下全部为实操结果。

### 15.1 A1｜远端推送

`git push origin main` → `ecffad2..1a61651 main -> main`，本地 12 个提交已全部推到 `origin/main`（docs 提交，无源码改动）。
0.2.10 功能提交 `83b11ec` 首次进入远端。

### 15.2 C1/D1｜第二台机锁屏复测（Redmi Note 11T Pro+ / Android 15 / HyperOS）

| 步骤 | 实测 |
|---|---|
| 设备占用 | 新绑定 profile `opencode` → Redmi Note 11T Pro+（23054RA19C），与 `codearts-069`（Note 12 Pro）分开，不抢同一台 |
| 覆盖安装 | 0.2.9(22) → 0.2.10(23)，`install -r` Success，lastUpdateTime 17:37:02；数据保留：2 场景 / 13 课程 / 10 会话 / 1 断点 |
| 权限原值 | `lock_screen_show_notifications=1`、`lock_screen_allow_private_notifications=1`、`zen_mode=0`、`POST_NOTIFICATION=allow`——**本来就是开的，无需改动**，因此没有改任何系统设置 |
| 到点 | 起 2 分钟计时（真实课程 `6.照顾宠物（上）.mp3`）→ 锁屏 → 到点后 +3s 唤醒：通知记录存在（`timer_alert_v3`、importance=4），**锁屏首屏仍只有媒体卡与 USB 通知**（`first-install/92-pearl-lock-fire+3s.jpg`） |
| 对照实验 | 同机锁屏下 `cmd notification post` 发 `com.android.shell` 对照通知：记录存在，首屏同样不显示（`first-install/97-pearl-control-wake.jpg`） |

**两台机结论一致**：Note 12 Pro（MIUI / Android 14）与 Note 11T Pro+（HyperOS / Android 15）都只渲染媒体卡与 USB 常驻通知，
第三方通知不上锁屏首屏。这不是应用行为差异，也不是权限问题（两台机权限均开）。**C1 的「换机复测」已执行，但没有得到可用机型**，
因此 AND-007 / AC-08 的「锁屏首屏可见」只能记 BLOCKED，并保留 R23（验收前先做对照通知确认机型前提）。

### 15.3 B1 前半｜首次安装与拒绝/授权（Redmi Note 11T Pro+）

| 步骤 | 实测 |
|---|---|
| 卸载前备份 | 设置页导出 → `Movies/第一章家庭与朋友/fragment-time-backup-20261114.json`，15,387 B；结构 `presets=2 / courses=13 / sessions=13 / checkpoint / settings(含 folderUri)` |
| 卸载 | `adb uninstall` → Success（备份已解析确认后才卸载） |
| 全新安装 | `adb install` → Success；`firstInstallTime=2026-11-14 17:46:18`，0.2.10 / vc23 |
| 首次启动 | 立即弹系统通知权限框「允许“碎片时间”发送通知？」，按钮「拒绝 / 始终允许」 |
| 拒绝分支 | 点拒绝 → `POST_NOTIFICATION=ignore`；首页两场景 + 空态「还没有导入课程」+「仅倒计时」入口正常；满 2 分钟到点**无通知**（6901 记录 0 条），应用内正常显示「计时完成 / 本次 2 分钟 / 到点完成」（`first-install/99-pearl-deny-finish.jpg`） |
| 授权分支 | `appops set … allow` 后到点通知恢复（记录 `timer_alert_v3`） |
| 备份恢复 | 设置「导入备份」→ 选择该 JSON → 确认框「备份里有场景 2 个、课程 13 个」→ 恢复 → 课程页回读「共 13 节 / 当前文件夹：第一章家庭与朋友」，SAF 关联随备份恢复 |

**首装默认值**：全新安装后设置页显示「跟随系统」，界面随系统为浅色——首装默认是 `SYSTEM`，不是「默认浅色」。
这是 AND-013「首装默认色」争点的实测答案：清单写的「首次浅色」与产品现状不符，应由用户拍板是否改默认，而不是在回归里记成缺陷。

### 15.4 本轮设备与清场

- Note 11T Pro+：0.2.10，13 课程已恢复；`opencode` profile 绑定保留（未 release）。
- Note 12 Pro：0.2.10，跑 AC-08 轮次；`codearts-069` 绑定保留。
- 未改系统设置（两台的锁屏/通知/勿扰均为原值）；测试产生的 shell 对照通知与到点通知留在通知栏，未写入任何真实数据。

### 15.5 B1 后半｜AC-08 每状态 10 轮（0.2.10 / Note 12 Pro）

30 轮全自动跑完（2026-11-14 18:00–19:16），判据是到点通知 6901 的 `when` 时间戳与「点击仅倒计时 + 120s」的偏差：

| 状态 | 轮次 | 成功 | 偏差范围 | 平均 |
|---|---|---|---|---|
| 前台 | 10 | 10 | 0.38–0.51s | 0.452s |
| 后台 | 10 | 10 | 0.23–0.34s | 0.274s |
| 锁屏 | 10 | 10 | 0.25–0.47s | 0.336s |
| 合计 | **30** | **30** | ≤0.51s | — |

失败数 0；逐轮原始记录在 `docs/qa/evidence-2026-11-14-regression/ac08/rounds-2026-11-14.jsonl`。

**分级分支**（同一台机实测）：

| 分支 | 结果 |
|---|---|
| 拒绝通知权限 | 首装拒绝后跑满 2 分钟：`id=6901` 记录 0 条，应用内「计时完成 / 到点完成」正常（§15.3） |
| 关闭「到点响铃」 | 设置页关掉开关后跑一轮：通知仍发（`id=6901` 1 条），音频子系统**没有新的 USAGE_ALARM 播放器**（最后一台是上一轮的 19:16:11，本轮 19:22:39 无新增）→ 静音语义正确，不会「关了还响」 |
| 勿扰 | **未测**：MIUI/HyperOS 上 `settings put global zen_mode 1` 被系统忽略（读回仍为 0），无法在不点屏设置的情况下构造；不写 PASS |
| OEM 省电 | **未测**，同上需要人工在系统设置里改 |

**AC-08 判定**：轮次要求（每状态 ≥10）已满足且全部在 0.51s 内；「权限拒绝/静音」两个分支已实测；「勿扰/OEM 省电」仍缺，故整条 AC 记 **PARTIAL（轮次与两分支 PASS，勿扰/OEM 未测）**，不能写「全绿」。
