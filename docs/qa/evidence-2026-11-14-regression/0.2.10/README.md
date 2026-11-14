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

## 4. 本轮真机前置体检与清场

```text
【前置体检回执｜0.2.10 锁屏轮】
设备锁：status=已固定 Redmi Note 12 Pro（在线 USB）；resolve 后全程 adb -s <已解析目标>
型号核对：ro.product.model=22101316C（Note 12 Pro，Android 14 / SDK 34），与 profile 标签一致
①灭屏：screen_off_timeout=600000（未改）
②端口：adb reverse --list 空；adb forward --list 空（无本项目映射，未动他人映射）
③服务：纯本地 App，无服务端，不适用
④前台归属：每次操作前 dumpsys window | mCurrentFocus 均为 com.wanghoufan.fragmentplayer 或通知栏/锁屏
系统设置：未修改任何系统设置；测试用的对照通知由 cmd notification post 发出（应用外来源）
结论：允许开跑
```

清场：测试产生的 2 条 shell 对照通知与 1 条到点通知留在通知栏（无数据写入）；App 侧除测试计时记录外未改动用户数据。

## 5. 复跑命令

```bash
S=$(python3 "$HOME/.config/opencode/skills/android-device-target-lock-local-zh/android_device_lock.py" resolve --profile codearts-069)
adb -s "$S" shell dumpsys notification --noredact | grep -A6 "pkg=com.wanghoufan.fragmentplayer"
adb -s "$S" shell cmd notification post -S bigtext -t 对照 tag "锁屏对照通知"   # 判断机型前提
```
