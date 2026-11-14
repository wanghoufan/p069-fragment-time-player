# 0.2.10 到点通知分区修复｜真机证据（2026-11-14）

> 设备：Redmi Note 12 Pro（22101316C，Android 14 / SDK 34，`Secure` 无根）
> 包：`com.wanghoufan.fragmentplayer` 0.2.10 / versionCode 23，SHA-256 `af913c01…d368`，构建 commit `83b11ec`
> 设备锁：`codearts-069`（status 已核对，`adb -s` 全程显式指定）
> 截图原图在 `.local/qa-0.2.10b/`（本地不入库），本目录为压缩副本与文字摘录。

## 1. 修了什么

`TimerNotifications` 把到点渠道从 `timer_alert_v2`（渠道静音 + 应用自播铃声）改为 `timer_alert_v3`：
渠道挂 0.1 秒静音 WAV（`res/raw/silence_alert.wav`，USAGE_ALARM），通知不再 `setSilent(true)`。
目的：让系统把到点提醒归入 Alerting 分区（静音通知会被归入 Silent，Android 11+ 锁屏首屏不展示）。

## 2. 证据

| 文件 | 内容 |
|---|---|
| `notification-evidence.txt` | 渠道/通知记录字段、触发时间核对、铃声播放证据、锁屏对照实验结论 |
| `60-fored-wake.jpg` | 到点后唤醒的锁屏首屏：只有其他 App 媒体卡与 USB 通知，P069 到点通知不在首屏 |
| `70-lock-control.jpg` | 同一锁屏下 `cmd notification post` 的对照通知同样不上首屏 |
| `84-fg-finished.jpg` | 前台到点完成页：无课程场景显示「到点完成」（0.2.9 文案修复生效） |
| `85-fg-running.jpg` | 前台仅倒计时运行页（00:18，用于核对本轮计时起点） |

## 3. 结论边界

- 应用侧已达成：通知 importance=HIGH、category=alarm、`mIsInterruptive=true`、`groupKey` 不再落 silent 分区；
  到点 ±0.3s 创建通知，同一时刻 `USAGE_ALARM` 播放器 `event:started`（铃声未被改成静音）。
- 设备侧限制：Note 12 Pro 的锁屏首屏只渲染媒体会话卡与 USB 常驻通知，**换一条来源完全不同的通知也不显示**。
  因此本机不能作为「锁屏首屏可见」的正向验收机；该结论不能用来说明应用缺陷，也不能用来证明应用通过。
- 需要另一台锁屏通知正常的机型（历史 Note 11/Android 11 曾可见，`build/qa/104-lock-alert-visible.png`）复测后才能把
  AND-007/AC-08 的锁屏可见部分判 PASS。
