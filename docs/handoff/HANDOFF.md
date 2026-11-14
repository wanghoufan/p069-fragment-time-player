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

- Captured at（2026-11-14 19:30，A1/B1/C1/D1 执行后）：
- PROJECT_PHASE：DEVELOP
- PLAN_VERSION：PRODUCT_PLAN_V1.1（顾问稿在 docs/plan；docs/pm 尚无正式 PRODUCT_PLAN）
- PLAN_GATE：IN_PROGRESS
- DEV_BASELINE：P069 产品计划 V1.1（Android APK、离线优先）
- Stage ID：M19 回归体系 V1.1 试运行 + 0.2.9/0.2.10 修复批次
- 版本：**0.2.10 / vc23**，SHA `af913c01…d368`，17,929,244 B，debug 签名，构建 commit `83b11ec`，构建 2026-11-14 14:44
- 设备装机：Note 12 Pro 与 Note 11T Pro+ 均为 **0.2.10(23)**（后者另做过卸载重装首装 + 备份恢复 13 课程）；OPPO 0.2.8(21)；Note 11T Pro 0.2.7(18)
- 源码：功能提交 `5cf5842`(0.2.9) + `83b11ec`(0.2.10)；docs 提交已 **push 到 origin/main**（`ecffad2..1a61651`），本轮后续 docs 再补推
- 当前状态：**LOCAL_TESTED**（0.2.10 为待验收候选；不可写 READY/RELEASED/ACCEPTED）
- P0 阻断：构建来源/同号不同包/设备锁/首装/AC-08 轮次已清零；剩余 勿扰与 OEM 省电、自动下一节、无音轨提示、锁屏首屏可见（两台机均不渲染第三方通知）、用户签收
- 本轮已完成：单测 79/79；0.2.10 干净构建 + 台账登记；通知改 Alerting；AC-08 **30/30**（前台 0.452s / 后台 0.274s / 锁屏 0.336s 平均，最大 0.51s）；Note 11T Pro+ 首装（拒绝分支可用、授权后通知恢复）+ 备份恢复 13 课程；双机锁屏对照实验证明两台机都不渲染第三方锁屏通知
- 279 项最终 **PASS 83 / PARTIAL 42 / FAIL 2 / BLOCKED 10 / NOT_RUN 9**（适用 146；P0 适用 108，PASS 64 / PARTIAL 31 / FAIL 2 / BLOCKED 10 / NOT_RUN 1）
- 证据：`docs/qa/evidence-2026-11-14-regression/`（0.2.10 段见 `0.2.10/README.md`）；原始 dumpsys 与截图在 `.local/qa-0.2.10b/`（本地，不入库）

## §2 下一步

- 下一步（Next Single Action）：**勿扰/OEM 省电需人工点屏构造**，或补自动下一节/无音轨提示；锁屏首屏可见需一台会渲染第三方锁屏通知的机型（两台测试机都不渲染）。
- 已完成（本轮）：0.2.10 提交/构建/登记/装机/通知分区复测、对照实验、VERSION 与 docs 补交。
- 待用户：
  - 勿扰/OEM 省电：需人工在设置页开启后再复测（脚本无法改 zen_mode）。
  - 锁屏首屏可见：需提供一台会渲染第三方锁屏通知的机型。
  - AC-12 签收。
- permission_request：push / 卸载重装 / 改系统设置均未授权。

## §3 未完成

- AC08 常规 30 轮已达标；勿扰、OEM 省电、精确闹钟授权后复测未做。
- 锁屏首屏可见：应用侧已修到 Alerting，但本机（Note 12 Pro）锁屏不渲染第三方通知（含对照通知），需换机验证。
- 首次安装已在 Note 11T Pro+ 完成并从备份恢复；SAF 真实撤回授权仍用卸载重装模拟，未测系统真实撤回。
- 真实课程链：播放/断点/锁屏有声已 PASS；自动下一节、无音轨提示未测。
- 代码遗留：21 条 P2/P3 review backlog 仍在。
- 0.2.6/0.2.7 仍未单独登记台账 §H（0.2.8/0.2.9/0.2.10 已登记）。
- 设备锁 `codearts-069` 绑 Redmi Note 12 Pro（未 release）；OPPO 仍被 `codex` profile 占用。

## §4 红线

- 不 push；commit 需明确授权（含分支名）。
- 产品验收未落盘或关键 AC 未测、首次发布未签收，不得报完工。
- dirty 包不得作正式验收；同 versionCode 不得出现第二个包。
- 不删历史（只迁移/归档）；不替用户判定文件无用；不改系统设置。

## §5 三日流水

- 2026-11-11 的 6 条流水已迁 `docs/history/HANDOFF-流水-2026-11-11归档.md`。
- 2026-11-12：0.2.5/vc16 时光流速与到点铃声；真机 10× 下 2:00 约 12 秒；sessions/断点零变化。
- 2026-11-13：用户报「时间不准」，0.2.6/vc17 修起算点、失败回退、锁屏不精确闹钟；前台 +0.163s、锁屏 +0.582s、10 分钟通知 +0.136s；单测 64/64。傍晚 0.2.7/vc18 铃声 4 选 1（1.41–1.89s，系统音 2.51s），单测 71/71，四机推送。晚 0.2.8/vc21 桌面快捷方式，12 Pro 8 项真机 PASS，四机同包。
- 2026-11-14：V1.1 回归首跑。单测 76/76；前台/锁屏/后台到点均 ≤1.5s；强停横幅、备份往返、场景 CRUD、快捷冷启动 PASS；发现构建来源错位、同号不同包、三处文档口径不一致；结论 LOCAL_TESTED，P0 未清零。接手轮 13:10–13:30：逐台回读发现 12T Pro 实为 0.2.7；设备锁 label 与实际机型不符；Android 15 补测发现返回键无页面栈、通知权限四项全关；矩阵纠偏；门禁探针确认同号不同包 exit 3、dirty 不阻断。13:31–13:40：0.2.9(22) 修复返回键/文案/设置提示，干净构建并登记（commit 5cf5842、SHA 4a05ddc0），覆盖安装目标机，真实课程 13 节播放/断点/锁屏有声/完成页返回全部 PASS，单测 79/79。
- 2026-11-14 17:00–19:30（A1/B1/C1/D1）：push 到 origin/main；Note 11T Pro+ 双机锁屏对照（两台机都不渲染第三方锁屏通知）；首装 + 拒绝/授权 + 备份恢复 13 课程；AC-08 30/30（≤0.51s）；关闭响铃轮次通知照发、无音频播放器。
- 2026-11-14 14:44–16:00（OpenCode 接手）：0.2.10(23) 到点通知从 Silent 改 Alerting（commit 83b11ec、SHA af913c01），干净构建、台账登记、Note 12 Pro 覆盖安装；单测 79/79；到点 +0.3s、铃声 USAGE_ALARM event:started；锁屏首屏仍不可见，但 `cmd notification post` 对照通知同样不可见 → 判为设备/ROM 行为，R22 改判、新增 R23 验收机前提；报告 §12 入库。

## §6 接续提示词

先读本文件 §0–§3 与 `docs/qa/regression-2026-11-14-v1.1.md`，按 §2 先向用户取 commit/bump 授权；未获授权只做不改文件的核查，不得 push、不得重装、不得改系统设置。
