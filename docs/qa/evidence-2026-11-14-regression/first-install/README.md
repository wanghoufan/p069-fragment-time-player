# 首次安装 / 拒绝权限 / 备份恢复｜真机证据（2026-11-14）

> 设备：Redmi Note 11T Pro+（23054RA19C，Android 15 / SDK 35）
> 包：0.2.10(23)，SHA-256 `af913c01…d368`
> 设备锁：`opencode` profile（本轮新绑，未与 `codearts-069` 抢同一台机）

## 1. 首次安装流程（真机）

| 步骤 | 实测 |
|---|---|
| 卸载前备份 | 设置页「导出备份」→ 系统保存对话框 → 保存到 `Movies/第一章家庭与朋友/fragment-time-backup-20261114.json`，15,387 B；结构 `version=1 / presets=2 / courses=13 / sessions=13 / settings+checkpoint`，`folderUri` 指向 `Movies/第一章家庭与朋友` |
| 卸载 | `adb uninstall com.wanghoufan.fragmentplayer` → Success（卸载前已确认备份可解析） |
| 全新安装 | `adb install app-debug.apk` → Success；`firstInstallTime=2026-11-14 17:46:18`，versionName 0.2.10 / vc23 |
| 首次启动 | 立即弹出系统通知权限对话框「允许“碎片时间”发送通知？」，按钮为「拒绝 / 始终允许」 |
| 拒绝分支 | 点「拒绝」→ `cmd appops get … POST_NOTIFICATION` = `ignore`；应用完全可用：首页两场景 + 空态「还没有导入课程」+ 「仅倒计时」入口；跑满 2 分钟到点，**无通知**（`id=6901` 记录数 0），应用内正常显示「计时完成 / 本次 2 分钟 / 到点完成」（`99-pearl-deny-finish.jpg`） |
| 授权分支 | `appops set … allow` 后跑一轮，通知恢复（见 `../0.2.10/` 与报告 §15） |
| 备份恢复 | 设置「导入备份」→ 系统选择器选上述 JSON → 确认框「恢复这个备份？备份里有场景 2 个、课程 13 个」→ 恢复 → 课程页回读「共 13 节 / 当前文件夹：第一章家庭与朋友」 |

## 2. 同机锁屏可见性（C1/D1）

- 0.2.10 覆盖安装后：允许通知=on、锁屏通知=on、勿扰=off、`POST_NOTIFICATION=allow`（原值记录后未改动，因为本来就是开）。
- 到点后锁屏首屏：只有媒体卡 `6.照顾宠物（上）.mp3` 与 USB 通知，**看不到「时间到了」**（`92-pearl-lock-fire+3s.jpg`），
  但 `dumpsys notification` 记录存在（`timer_alert_v3`，importance=4，`isInterruptive=true`）。
- **对照实验**：同机锁屏下用 `cmd notification post` 发 `com.android.shell` 通知，记录存在但首屏同样不显示（`97-pearl-control-wake.jpg`）。
- 结论：Note 11T Pro+（HyperOS/Android 15）与 Note 12 Pro（MIUI/Android 14）都只渲染媒体卡与 USB 常驻通知，
  第三方通知一律不上锁屏首屏。**两台机都不能作为「锁屏可见」正向验收机**；应用侧行为（Alerting 分区 + 铃声）已达标。

## 3. 首装默认值（AND-013 证据）

全新安装未改任何设置时，设置页显示「跟随系统」主题，界面随系统为浅色；即首装默认 `SYSTEM`，不是「默认浅色」。
