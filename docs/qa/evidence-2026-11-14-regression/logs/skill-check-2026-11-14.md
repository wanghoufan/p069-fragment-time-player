# G-009 / OPS-021 Skill 核对与实跑｜P069｜2026-11-14 接手轮

> 依据：`005-Skill 管家/自动扫描记录/2026-11-14.md`、`005-Skill 管家/自动扫描状态.json`、`~/.codeartsdoer/skills/`
> 用途：补 V1.1 的 G-009「查询中央 Skill 登记、部署端、可发现与实跑」与 OPS-021「关键 Skill 至少实际调用一次」。

## 1. 登记与部署现状

- 中央登记 133 条；2026-11-14 巡检新增 0、更新 4、消失 0。
- `android-true-device-qa-orchestrator-local-zh`：1.7.0，已分发 11/11，逐端 diff 一致。
- `android-device-target-lock-local-zh`：2.2.0，部署在 CodeArts 端（`~/.codeartsdoer/skills/`），本轮实际调用。
- `version-registry-local-zh`：1.2.0，`register-version.mjs` 本轮以 `--dry-run` 实际调用。
- 结论：「文件一致」只证明分发，不等于可调用；以下为本轮真实调用记录。

## 2. 本轮实际调用

| Skill | 调用方式 | 结果 |
|---|---|---|
| `android-device-target-lock-local-zh` 2.2.0 | `python3 android_device_lock.py profiles / status / resolve` | 成功；但发现 label 与 model 不一致（见 `device-lock-state-2026-11-14.txt`） |
| `android-true-device-qa-orchestrator-local-zh` 1.7.0 | 加载正文，执行阶段 0 / 0.5 / 0.6 / 0.8 回执 | 成功；回执落在 `android15-recheck/README.md` |
| `version-registry-local-zh` 1.2.0 | `node register-version.mjs --dry-run` ×2 | 成功；同号不同包 exit 3，dirty 不阻断（见 `gate-probe-2026-11-14.txt`） |

## 3. 结论

- G-009 由 NOT_RUN 改 **PASS**：登记、部署 11/11、版本与调用入口均已核对。
- OPS-021 由 PASS 改 **FAIL（上一轮）→ 本轮已补跑**：上一轮报告未记录任何 Skill 实际调用；本轮已对三个关键 Skill 实际执行并留痕。
- 发现并需上报的 Skill 缺陷：`android-device-target-lock-local-zh` 把 `23054RA19C` 的市场名标成「Redmi Note 12T Pro」；真实 12T Pro 是 `21091116AC`。建议在 Skill 侧修正型号映射或改为「用户确认标签」由用户输入，不自动推断 market_name。