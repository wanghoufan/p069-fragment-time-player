# 第三台机（Redmi Note 11T Pro / Android 11）锁屏复测

- 包：0.2.10(23)，SHA `af913c01…d368`，`install -r` 覆盖 0.2.7，lastUpdateTime 19:49:14
- 系统：MIUI / Android 11（SDK 30），`lock_screen_show_notifications=1`

## 记录

- 到点通知记录：`NotificationRecord(0x006b885c: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|`
- 创建时间：19:57:34（真实起点是「开始」点击 19:55:34，名义 120s → 19:57:34，与 0.2.6「准备耗时计入」一致；脚本里的 19:56:01 是点完「仅倒计时」后的记录时刻，不是计时起点）

## 三次锁屏取样

| 条件 | 结果 | 截图 |
|---|---|---|
| 勿扰开启（原状态 zen_mode=1） | 锁屏显示勿扰横幅与 USB 通知，**看不到「时间到了」** | `evergo-lock.jpg` |
| 关闭勿扰（点锁屏「立即退出」，zen_mode=0） | 锁屏仍只有 USB 通知，**看不到「时间到了」** | `evergo-lock-nodnd.jpg` |
| 同机对照：`cmd notification post`（com.android.shell） | 通知记录存在，锁屏首屏同样不显示，仅在通知栏可见 | `evergo-control.jpg` |

## 结论

- 该机锁屏**会**渲染通知（勿扰横幅、USB 通知都显示），但第三方应用通知一律不上首屏（对照通知同样如此）。
- 三台机（Android 11 / 14 / 15）一致：到点通知在通知栏可见、可响铃，锁屏首屏不展示。属系统/ROM 策略，不是本应用缺陷。
- 勿扰开启时额外屏蔽全部提醒横幅（原生行为），可作为 AC-08「勿扰」分支的一条实测结论：**勿扰下到点通知仍在通知栏，锁屏不展示**。
- 收尾：该机勿扰已恢复为开启（zen_mode=1），未改其他系统设置。
