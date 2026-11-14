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
| 本轮回归报告（V1.1 首跑） | `docs/qa/regression-2026-11-14-v1.1.md` |
| 本轮真机证据 | `docs/qa/evidence-2026-11-14-regression/README.md` |
| AC 追踪矩阵 | `docs/qa/产品验收追踪矩阵.md` |
| 回归矩阵（279 项筛选） | `docs/qa/regression-matrix-2026-11-14.csv` |

## §1 当前进展

- Captured at（2026-11-14 13:30，V1.1 回归首跑 + 接手轮复核后）：
- PROJECT_PHASE：DEVELOP
- PLAN_VERSION：PRODUCT_PLAN_V1.1（顾问稿在 docs/plan；docs/pm 尚无正式 PRODUCT_PLAN）
- PLAN_GATE：IN_PROGRESS
- DEV_BASELINE：P069 产品计划 V1.1（Android APK、离线优先）
- Stage ID：M19 回归体系 V1.1 试运行（M18 快捷方式 0.2.8 已完成）
- 版本：**0.2.8 / vc21**，SHA `779c8433…814c`，17,926,541 B，debug 签名，构建 2026-11-13 13:26
- 设备装机：OPPO PLG110、Note 11T Pro+、Note 12 Pro = 0.2.8(21)；**Note 11T Pro（21091116AC）= 0.2.7(18)**，非四机同包（逐台 `dumpsys package`，见 `docs/qa/evidence-2026-11-14-regression/logs/device-inventory-2026-11-14.txt`）
- 源码：0.2.8 功能已提交 `a74c8235`（11-13）；当前 HEAD `8915d0dd`（docs retro）；**远端 origin/main 落后 1 个 docs 提交，未 push**
- 当前状态：**LOCAL_TESTED**（不可写 READY/RELEASED/ACCEPTED）
- P0 阻断（3 类）：①VERSION/台账把构建 commit 记为 `756e1715-dirty`（0.2.5 提交），不含 0.2.8 功能 ②干净 HEAD 重建 SHA=`76b6b449…`，与现包不同，同 vc 不同包 ③设备/锁/矩阵口径与实测不符（已在本轮纠偏，见报告 §10）
- 本轮已完成：单测 76/76（test-results XML 复核）；9 轮真机；Android 15 跨机型补测；279 项纠偏后适用 146 / N/A 133（PASS 68 / PARTIAL 45 / FAIL 14 / BLOCKED 10 / NOT_RUN 9；P0 适用 108，PASS 51 / PARTIAL 34 / FAIL 12 / BLOCKED 10 / NOT_RUN 1）
- 本轮通过：到点准确（120.194/120.377/120.087s，名义 120s）、通知 6901 importance=4 category=alarm、强停横幅、备份 2/0/21 往返、QA Test 增改删、快捷方式冷启动、Android 15 安全区
- 接手轮新发现：①计时完成页连续两次返回键无响应（无页面栈，P1）②完成页「课程进度已保存」无课程也显示（P2）③设备锁 label 写 12T Pro、实际绑定 23054RA19C（Note 11T Pro+）且未 release ④Note 11T Pro+ 通知权限四项全关 ⑤register-version 只拦同号不同包，不拦 dirty
- 证据：`docs/qa/evidence-2026-11-14-regression/`（含 `android15-recheck/` 与 5 份新日志）

## §2 下一步

- 下一步（Next Single Action）：**向用户申请 commit 授权**（需分支名），并把 `USER_MODEL_OVERRIDE.md.旧版-2026-11-11` 移入 `.local/archive/` 让工作区干净。
- 随后（需用户逐项授权）：修返回键页面栈 + 完成页文案 + 设置写入异常 → bump `versionCode=22` → 干净构建 → 登记 → `install -r` 复测 → 真实课程补测 → 通知权限/精确闹钟授权复测 → 用户签收 AC-12。
- 人要拍板（列出来问，不问不许开工）：
  - A｜是否授权本地 commit（分支名 main）；push 是否同批授权。
  - B｜是否授权 bump vc22 并重出干净包（现包不可签收）；是否把返回键/文案/设置异常并入本批。
  - C｜真实课程素材在哪里：四机均未找到 mp3（Note 11T Pro 库内 courses=0），需用户指定路径或设备。
  - D｜Note 11T Pro+ 通知权限四项全关：是否由用户开启后复测，或按 BLOCKED 记录。
- permission_request：commit / push / bump 版本 / 卸载重装 / 改系统设置均未授权。

## §3 未完成

- 真实课程链：四台在连设备均未找到 mp3/mp4（maxdepth 3）；Note 11T Pro 的 App 库 courses=0、sessions=0。导入、播放、断点、自动下一节、无音轨提示、锁屏有声均 BLOCKED。
- AC08 仅前台/锁屏/后台各 1 轮（历史 8/10），未达每状态 10 轮；勿扰、OEM 省电、精确闹钟授权后复测未做。
- 锁屏首屏未见 P069 通知（下拉可见）；Note 11T Pro+ 通知权限四项全关，正向复测需用户开启。
- 首次安装未做（卸载会丢 21 条历史）；双机备份恢复、SAF 真实撤回授权未测。
- 代码遗留：返回键无页面栈（P1，完成页/设置页/计时中按返回不回上一级）；完成页「课程进度已保存」无课程也显示（P2）；`SettingsRepositoryImpl.write` 静默吞 IOException（P2）；21 条 P2/P3 review backlog。
- 本轮新增测试历史 id=18–21 保留在应用库（未删用户数据）；测试导出 JSON 已从 Download 删除。
- 0.2.6/0.2.7 仍未单独登记台账 §H（0.2.8 已登记）。
- 设备锁 `codearts-069` 仍绑定 23054RA19C，label 错写「Redmi Note 12T Pro」，未 release；OPPO 仍被 `codex` profile 占用。

## §4 红线

- 不 push；commit 需明确授权（含分支名）。
- 产品验收未落盘或关键 AC 未测、首次发布未签收，不得报完工。
- dirty 包不得作正式验收；同 versionCode 不得出现第二个包。
- 不删历史（只迁移/归档）；不替用户判定文件无用；不改系统设置。

## §5 三日流水

- 2026-11-11 的 6 条流水已迁 `docs/history/HANDOFF-流水-2026-11-11归档.md`。
- 2026-11-12：0.2.5/vc16 时光流速与到点铃声；真机 10× 下 2:00 约 12 秒；sessions/断点零变化。
- 2026-11-13：用户报「时间不准」，0.2.6/vc17 修起算点、失败回退、锁屏不精确闹钟；前台 +0.163s、锁屏 +0.582s、10 分钟通知 +0.136s；单测 64/64。傍晚 0.2.7/vc18 铃声 4 选 1（1.41–1.89s，系统音 2.51s），单测 71/71，四机推送。晚 0.2.8/vc21 桌面快捷方式，12 Pro 8 项真机 PASS，四机同包。
- 2026-11-14：V1.1 回归首跑。单测 76/76；前台/锁屏/后台到点均 ≤1.5s；强停横幅、备份往返、场景 CRUD、快捷冷启动 PASS；发现构建来源错位、同号不同包、三处文档口径不一致；结论 LOCAL_TESTED，P0 未清零。接手轮 13:10–13:30：逐台回读发现 12T Pro 实为 0.2.7；设备锁 label 与实际机型不符；Android 15 补测发现返回键无页面栈、通知权限四项全关；矩阵纠偏 14 项；门禁探针确认同号不同包 exit 3、dirty 不阻断。

## §6 接续提示词

先读本文件 §0–§3 与 `docs/qa/regression-2026-11-14-v1.1.md`，按 §2 先向用户取 commit/bump 授权；未获授权只做不改文件的核查，不得 push、不得重装、不得改系统设置。