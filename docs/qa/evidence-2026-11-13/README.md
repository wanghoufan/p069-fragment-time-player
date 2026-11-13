# 069 碎片时间播放器｜2026-11-13 真机计时精度验证

> 本轮只做「构建 → 覆盖安装 → 前台/锁屏两轮 3 分钟计时精度取证」，不修改任何源码、不 commit、不 push。
> 判定口径：**PASS**＝有证据且判据满足；**FAIL**＝有证据证明不满足；**未测**＝没有执行（未测 ≠ 通过）。
> 本轮被验证的构建对应工作区中**已存在的**未提交改动（`app/build.gradle.kts`、`timer/*.kt`、`TimerScreen.kt`、`TimerEngineTest.kt` 等，非本轮产生）；本轮未编辑任何文件，只新增本目录。

## 1. 环境

| 项 | 值 |
|---|---|
| 主机 | macOS；`JAVA_HOME=~/android-toolchain/jdk-17.0.20.1+1/Contents/Home`；`ANDROID_HOME=~/android-toolchain/sdk` |
| 真机 | Redmi Note 12 Pro，1080x2400，density 440，Android（`wm size` 物理尺寸与截图一致）；序列号不记录 |
| 包名 | `com.wanghoufan.fragmentplayer`；debug 签名 |
| 时间基准 | 全部取设备本地时钟 `adb shell date +%s%3N`（CST，UTC+8），换算后写入本文件 |
| 网络 | 构建阶段允许联网补依赖；测试阶段 App 无 `INTERNET` 权限 |

## 2. 构建

- 命令：`./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**（`compileDebugKotlin` 有一条 `SettingsScreen.kt:83` 的既有注解目标 warning，未修，不影响产物）。
- 产物：`/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/app/build/outputs/apk/debug/app-debug.apk`
- 字节数：**17,528,354 B**
- SHA-256：`c5728e60fa9edfaaed930e42a9a384d2165771d7695b8b585b2a58474d558d86`

## 3. 覆盖安装与版本回读

- 命令：`adb install -r app/build/outputs/apk/debug/app-debug.apk` → `Performing Streamed Install` / `Success`（覆盖安装，未卸载，本地数据保留路径走 `-r`）。

| 时点 | versionCode | versionName | firstInstallTime | lastUpdateTime |
|---|---|---|---|---|
| 安装前 | 16 | 0.2.5 | 2026-11-13 10:11:01 | 2026-11-13 10:11:01 |
| 安装后 | **17** | **0.2.6** | 2026-11-13 10:11:01（未变，符合覆盖安装语义） | 2026-11-13 10:38:57 |

> `dumpsys package` 另有一行 `firstInstallTime=1970-01-01 08:00:00`，为该包的次级用户/占位记录，与本轮安装无关。
> 安装后设备实际加载包路径：`/data/app/~~EHnK0E0fQ440ChXETejA5Q==/com.wanghoufan.fragmentplayer-5xRTzmXgT9_JtlZ3_n-2Yw==/base.apk`。

启动：`am start -n com.wanghoufan.fragmentplayer/.MainActivity` → `mCurrentFocus=Window{... com.wanghoufan.fragmentplayer/...MainActivity}`；**未出现权限弹窗**（无 `GrantPermissionsActivity`），故未执行任何坐标点击授权。

## 4. 精确闹钟授权状态（前置）

| 阶段 | `appops get ... SCHEDULE_EXACT_ALARM` | 说明 |
|---|---|---|
| 本轮开始（A 之前） | `deny` | 设备原有状态 |
| 测试 A 前置 | `allow`（已 `set` 并 `get` 回读） | A 为「前台 + 已授权精确闹钟」路径 |
| 测试 B 前置 | `deny`（已 `set` 并 `get` 回读） | B 为「未授权精确闹钟」真实用户路径 |
| 本轮收尾 | `deny` | 已回到设备原本状态 |

## 5. 测试 A｜前台 3 分钟（验证「仅倒计时沿用所选场景」修复）

操作链：首页 → 滴眼药水卡片 `开始`（[674,814][794,899]，点 734,856）→ 2.6s 后出现错误卡「这次没能开始播放 / 还没有导入课程，可以先只用倒计时」→ 点错误卡内 `仅倒计时`（[647,909][886,994]，点 766,951）。

| 项 | 值 |
|---|---|
| T0（点击前） | 2026-11-13 10:41:33.410（`1794537693410`） |
| T1（点击后） | 2026-11-13 10:41:33.514（`1794537693514`） |
| T2（首次轮询命中 6901） | 2026-11-13 10:44:34.835（`1794537874835`） |
| 命中判定窗口 | `1794537874637 .. 1794537874835`（+181123 ~ +181321 ms） |
| **delta = T2 − T1** | **181,321 ms**；按首次可能出现时刻计 **181,123 ms** |
| 名义时长 | 180,000 ms（场景「滴眼药水」03:00） |
| 偏差 | 较名义晚约 **+0.6 ~ +1.3 s**（轮询间隔≈0.5 s，故给出区间） |

命中原文（`dumpsys notification --noredact`）：

```
NotificationRecord(0x042a0ec0: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=alarm groupKey=silent vis=PUBLIC))
```

到点后（`input keyevent 224` + `wm dismiss-keyguard`）界面文本：

```
计时完成 | 滴眼药水 | 本次 3 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

判定：**PASS** —— 显示「本次 3 分钟」而非「本次 2 分钟」，「仅倒计时沿用所选场景」修复在真机成立。
截图：`aitest-foreground.png`。

## 6. 测试 B｜锁屏 3 分钟（未授权精确闹钟）

操作链：点 `知道了` 回首页 → 同一路径（`开始` 734,856 → 错误卡 `仅倒计时` 766,951）→ 记录 T1 后 **490 ms** 执行 `input keyevent 223` 息屏（`mWakefulness=Dozing`）→ 轮询 6901。

| 项 | 值 |
|---|---|
| T0（点击前） | 2026-11-13 10:45:38.442（`1794537938442`） |
| T1（点击后） | 2026-11-13 10:45:38.519（`1794537938519`） |
| 息屏指令时刻 | 10:45:39.009（T1+490 ms），之后 `mWakefulness=Dozing`，命中时 `mWakefulness=Asleep` |
| T2（首次轮询命中 6901） | 2026-11-13 10:48:38.827（`1794538118827`） |
| 命中判定窗口 | `1794538118627 .. 1794538118827`（+180108 ~ +180308 ms） |
| **delta = T2 − T1** | **180,308 ms**；按首次可能出现时刻计 **180,108 ms** |
| 名义时长 | 180,000 ms |
| 偏差 | 约 **+0.1 ~ +0.3 s**（在轮询分辨率内，可视为精确） |

命中原文：

```
NotificationRecord(0x0b861861: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=alarm groupKey=silent vis=PUBLIC))
```

唤醒 + `wm dismiss-keyguard` 后界面文本：

```
计时完成 | 滴眼药水 | 本次 3 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

判定：**PASS** —— 息屏状态下 3 分钟计时到点、通知发出、唤醒后界面为「计时完成 / 本次 3 分钟」。
截图：`aitest-lockscreen.png`。

### 6.1 `dumpsys alarm` 原文（本轮最新一次，含 type）

```
    u0a398:
      #1: Reason=alarm_cancelled elapsed=-25s569ms rtc=2026-11-13 10:48:38.853
        Snapshot:
          type=RTC_WAKEUP tag=*walarm*:com.wanghoufan.fragmentplayer.timer.TIMER_ALARM
          policyWhenElapsed: requester=-25s927ms app_standby=-2m16s559ms device_idle=-- battery_saver=-3m25s861ms tare=-3m25s861ms power_pending=+2d23h59m34s73ms
      #2: Reason=alarm_cancelled elapsed=-22m30s362ms rtc=2026-11-13 10:26:34.060
        Snapshot:
          type=RTC_WAKEUP tag=*walarm*:com.wanghoufan.fragmentplayer.timer.TIMER_ALARM
      #3: Reason=alarm_cancelled elapsed=-25m39s300ms rtc=2026-11-13 10:23:25.122
        Snapshot:
          type=RTC_WAKEUP tag=*walarm*:com.wanghoufan.fragmentplayer.timer.TIMER_ALARM
      #4: Reason=alarm_cancelled elapsed=-36m20s537ms rtc=2026-11-13 10:12:43.885
        Snapshot:
          type=RTC_WAKEUP tag=*walarm*:com.wanghoufan.fragmentplayer.timer.TIMER_ALARM
```

```
  u0a398:com.wanghoufan.fragmentplayer +785ms running, 3 wakeups:
    +785ms 3 wakes 3 alarms, last -4m30s947ms:
      *walarm*:com.wanghoufan.fragmentplayer.timer.TIMER_ALARM
```

交叉核对：#1 的 `rtc=10:48:38.853` = 测试 B 的 T1（10:45:38.519）**+180,334 ms**，与轮询命中的 180,108~180,308 ms 相互印证，说明锁屏路径由 `RTC_WAKEUP` 精确闹钟承载且触发时刻落在 ±0.35 s 内。

> 观察（非缺陷判定）：`dumpsys alarm` 历史中**没有**测试 A（应于 10:44:33.5 到点）的 `RTC_WAKEUP` 记录，A 的到点由进程内计时承载，实测偏差 +0.6~1.3 s；B（息屏）走闹钟，偏差 +0.1~0.3 s。此差异仅作记录，本轮未做代码层面归因。

## 7. 证据文件

| 文件 | 绝对路径 |
|---|---|
| 本文件 | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/docs/qa/evidence-2026-11-13/README.md` |
| 测试 A 截图 | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/docs/qa/evidence-2026-11-13/aitest-foreground.png` |
| 测试 B 截图 | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/docs/qa/evidence-2026-11-13/aitest-lockscreen.png` |

## 8. 失败点与未测项

- 失败点：**无**。两轮均到点、通知命中、界面文本正确，未出现「本次 2 分钟」。
- 未测项：
  - 未测长时长（>3 min）与多次连续轮次，未覆盖 MIUI 省电策略下的长时间后台驻留。
  - 未测通知的点击跳转与铃声/振动实际听感（本轮只取通知记录与界面文本）。
  - `uiautomator dump` 在倒计时进行中会返回 `ERROR: could not get idle state.`（界面持续刷新），本轮只在到点静止后取界面文本，未把该工具限制记为产品缺陷。
- 手机侧临时文件：截图 `screencap` 后均已 `pull` 到本目录并 `rm` 删除；未创建、未导入、未删除任何课程或用户文件，未进入系统文件选择器。
## 9. 0.2.6 最终包复测（提醒优先修复后）

> 本节为**追加**内容，不修改第 1–8 节。本轮仍是「构建 → 覆盖安装 → 前台/锁屏两轮 3 分钟取证」，未改源码、未 commit、未 push。
> 与第 1–8 节的区别：上一轮测的是候选包（SHA `c5728e60…`）；其后源码又改了「到点提醒优先于落库」，版本号**未变**（仍 0.2.6 / versionCode 17）。本轮用**新构建产物**复测，判定必须基于新 SHA，不能沿用旧结论。

### 9.1 构建产物

| 项 | 值 |
|---|---|
| 命令 | `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**（`compileDebugKotlin` / `dexBuilderDebug` / `packageDebug` 均实际执行，非全量 up-to-date） |
| 产物路径 | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/app/build/outputs/apk/debug/app-debug.apk` |
| 字节数 | **17,596,484 B**（上一轮候选包 17,528,354 B） |
| SHA-256 | `a80f82bf6308b47b1d9beb149b4ed826003bb8ffaf5194d87621498492920876` |
| 与旧包比对 | 与第 2 节的 `c5728e60fa9edfaaed930e42a9a384d2165771d7695b8b585b2a58474d558d86` **不同**，确认本轮为新产物 |

### 9.2 覆盖安装与版本回读

- 命令：`adb install -r …/app-debug.apk` → `Performing Streamed Install` / `Success`（未卸载，本地数据保留）。

| 时点 | versionCode | versionName | firstInstallTime | lastUpdateTime |
|---|---|---|---|---|
| 安装前 | 17 | 0.2.6 | 2026-11-13 10:11:01 | 2026-11-13 10:38:57 |
| 安装后 | **17** | **0.2.6** | **2026-11-13 10:11:01（未变，覆盖安装语义正确）** | **2026-11-13 10:53:44** |

> 安装后实际加载路径：`/data/app/~~zzjxEKZQvzuL1z6gvGGE-g==/com.wanghoufan.fragmentplayer-i1sVsv1R4gwPafcAtuk1xg==`。
> 版本号不变但产物已变，认包以 SHA-256 为准（见 9.1）。

### 9.3 精确闹钟状态

| 阶段 | `appops get … SCHEDULE_EXACT_ALARM` |
|---|---|
| 两轮前置（`set` 后 `get` 回读） | **deny** |
| 收尾（再次 `set deny` 并回读） | **deny** |

> 两轮都在「未授权精确闹钟」的真实用户路径下测得。

### 9.4 首页「仅倒计时」直点验证（步骤 4）

- `uiautomator dump` 定位：`text="仅倒计时"` bounds=`[421,1754][660,1839]`，中心 **(540,1796)**（与上一轮估值一致，以 dump 为准）。
- 点击后：`mCurrentFocus` 仍为 `com.wanghoufan.fragmentplayer/com.wanghoufan.fragmentplayer.MainActivity`；随后 `uiautomator dump` 返回 `ERROR: could not get idle state.`（界面持续刷新＝计时已在跑，属工具限制）。
- 中断取证：`am force-stop` 后重启，首页出现卡片「**上次计时被中断 / 上次计时被系统中断，中断前已进行 01:04 / 知道了**」，证明首页「仅倒计时」确实启动并运行了倒计时。
- 判定：按钮可点、可启动计时（该 2 分钟默认场景轮次未计入下方 3 分钟精度判定）。

### 9.5 前台 3 分钟（步骤 5）

操作链：首页 → 滴眼药水卡片 `开始` bounds=`[674,814][794,899]`（点 734,856）→ 2.6 s 后错误卡「这次没能开始播放 / 还没有导入课程，可以先只用倒计时」→ 点卡内 `仅倒计时` bounds=`[647,909][886,994]`（点 **766,951**，dump 实测，与上一轮一致）→ 记录 T1 → 每 0.5 s 轮询 `dumpsys notification --noredact` 的 `id=6901`。

| 项 | 值（设备本地时钟 CST） |
|---|---|
| T1（点击后） | 2026-11-13 10:56:22.019（`1794538582019`） |
| 最后一次未命中 | 10:59:21.377（`1794538761377`，+179,358 ms） |
| T2（首次命中 6901） | 10:59:22.182（`1794538762182`，+180,163 ms） |
| 命中判定窗口 | `+179,358 .. +180,163 ms`（宽 805 ms） |
| **delta = T2 − T1** | **180,163 ms** |
| 名义时长 | 180,000 ms |
| 偏差 | 落在窗口内（窗口覆盖名义值；轮询周期≈0.8 s，故给区间） |

命中原文：

```
    NotificationRecord(0x0bc84c24: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=alarm groupKey=silent vis=PUBLIC))
```

到点后（`input keyevent 224` + `wm dismiss-keyguard`）界面文本：

```
计时完成 | 滴眼药水 | 本次 3 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

判定：**PASS** —— 通知到点、界面为「计时完成 / 本次 3 分钟」，未出现「本次 2 分钟」。
截图：`final-foreground.png`。

### 9.6 锁屏 3 分钟（步骤 6）

操作链：点 `知道了`（540,789）回首页 → 确认 6901 残留计数为 **0** → 重复同一路径（734,856 → 766,951）→ 记录 T1 后 **453 ms** 执行 `input keyevent 223` 息屏 → 轮询 6901。

| 项 | 值（设备本地时钟 CST） |
|---|---|
| T1（点击后） | 2026-11-13 11:00:08.352（`1794538808352`） |
| 息屏指令时刻 | 11:00:08.805（T1+**453 ms**） |
| 息屏后 0.8 s 回读 | `mWakefulness=Asleep`（**未停留在 Dozing**，已直接入睡，条件比 Dozing 更深） |
| 最后一次未命中 | 11:03:08.292（`1794538988292`，+179,940 ms） |
| T2（首次命中 6901） | 11:03:08.934（`1794538988934`，+180,582 ms） |
| 命中判定窗口 | `+179,940 .. +180,582 ms`（宽 642 ms） |
| **delta = T2 − T1** | **180,582 ms** |
| 名义时长 | 180,000 ms |
| 偏差 | 落在窗口内（窗口同样覆盖名义值） |
| 命中时电源态 | `mWakefulness=Asleep` |

命中原文：

```
    NotificationRecord(0x07ad477a: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=alarm groupKey=silent vis=PUBLIC))
```

唤醒 + `wm dismiss-keyguard` 后（`mCurrentFocus=…MainActivity`）界面文本：

```
计时完成 | 滴眼药水 | 本次 3 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

判定：**PASS** —— 息屏（实测已到 Asleep）状态下 3 分钟到点、通知发出、唤醒后界面为「计时完成 / 本次 3 分钟」。

### 9.7 本轮证据文件

| 文件 | 绝对路径 | SHA-256 |
|---|---|---|
| 本文件（追加节） | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/docs/qa/evidence-2026-11-13/README.md` | — |
| 前台轮截图 | `…/docs/qa/evidence-2026-11-13/final-foreground.png`（108,720 B） | `3877f37292d41eb3857be2e11779df4ab653925858297c06174676b0be729376` |
| 锁屏轮截图 | `…/docs/qa/evidence-2026-11-13/final-lockscreen.png`（108,479 B） | `072bcd16efed2c3a45589878c035c5f4b5d6b255d88fe7f44adc9541153be280` |

### 9.8 失败点与未测项（本轮）

- 失败点：**无**。两轮通知均命中、界面文本均为「本次 3 分钟」，无「本次 2 分钟」。
- 与第 6 节预期口径的差异（记录，非缺陷）：步骤 6 要求息屏后确认 `mWakefulness=Dozing`，实测息屏 0.8 s 后已是 `Asleep`；说明本机休眠更快，锁屏条件更严苛，到点仍命中。
- 未测项：
  - 仍未覆盖长时长（>3 min）与多轮连续计时，未覆盖 MIUI 省电策略下长时间后台驻留。
  - 未测通知铃声/振动的实际听感与点击跳转。
  - `uiautomator dump` 在倒计时运行中仍返回 `ERROR: could not get idle state.`（工具限制，非产品缺陷）。
- 手机侧临时文件：`screencap` 产物与 `ui.xml` 均已 `pull` 后 `rm` 删除（复查：`/sdcard/probe.png`、`/sdcard/ui.xml`、`/sdcard/final-fg.png`、`/sdcard/final-ls.png` 均为 `No such file or directory`）；未创建、未导入、未删除任何课程或用户文件，未进入系统文件选择器。
## 10. 10 分钟锁屏长时长复测（0.2.6/vc17）

> 本轮只做「改场景时长 → 锁屏 10 分钟 → 到点取证 → 恢复 3 分钟」，不重装、不改源码、不 commit、不 push、不进文件选择器。
> 时间基准仍为设备本地时钟 `adb shell date +%s%3N`（CST，UTC+8）。
> 认包：0.2.6/vc17，产物 SHA-256 `a80f82bf6308b47b1d9beb149b4ed826003bb8ffaf5194d87621498492920876`（本轮未重装，仅回读 `versionCode=17 / versionName=0.2.6`）。

### 10.1 前置与改时长（界面操作）

- 精确闹钟：`appops set … SCHEDULE_EXACT_ALARM deny` → `get` 回读 **deny**（开测前、收尾各一次）。
- 首页 dump（先 `rm -f /sdcard/ui.xml`，输出含 `UI hierchary dumped to: /sdcard/ui.xml`）定位「滴眼药水」卡片 `03:00`。
- 改 10 分钟点击链（坐标均为 `uiautomator dump` 实测；`content-desc` 实际为「**更多操作**」）：

| 步骤 | 目标 | bounds | 点击 |
|---|---|---|---|
| 1 | 更多操作 | `[915,823][981,889]` | (948,856) |
| 2 | 编辑 | `[739,973][845,1048]` | (792,1010) |
| 3 | 分钟输入框（值 `3`） | `[166,1170][523,1359]` | (344,1264) → `KEYCODE_MOVE_END` + `DEL`×2 + `input text 10` |
| 4 | 保存（键盘弹起后） | `[775,1072][881,1147]` | (828,1109) |

- 回首页 dump 回读：`text="滴眼药水"` + **`text="10:00"`**（秒框保持 `0`，未动）。

### 10.2 第 1 轮：弃用（工具干扰，非产品结论）

> 开测前为清掉上一轮残留的到点通知，对 `0|com.wanghoufan.fragmentplayer|6901|null|10398` 执行了 `cmd notification snooze --for 10800000`。该 snooze 使同 key 的**新**到点通知被抑制，轮询始终无命中——这是本轮自造干扰，记录以防误导。

| 项 | 值 |
|---|---|
| T1（点击「仅倒计时」后） | 11:08:28.903（`1794539308903`） |
| 息屏 | T1+0.4 s，`mWakefulness=Asleep` |
| 轮询结果 | 15 分钟内**未命中**，最后未命中 `1794540208904`（+900,001 ms） |
| 旁证：`TIMER_ALARM` | `rtc=2026-11-13 11:18:29.289`（**T1+600.386 s**，`Reason=alarm_cancelled`） |
| 旁证：界面 | 唤醒后 dump 为「**计时完成 / 滴眼药水 / 本次 10 分钟**」 |
| 干扰确认 | `dumpsys notification` 的 `Pending snoozed notifications` 含同 key（until 14:06:51） |
| 额外事实 | 该轮中段屏幕被**外部通知唤醒**（`mLastWakeTime` ≈ 11:14，前台变为 MIUI 桌面），App 转后台但进程未重启、计时未中断（见 alarm 与界面） |

结论：本轮通知证据**作废**（`NOT_VALID`）；其 `TIMER_ALARM` 与界面仍证明 600.386 s 到点。干扰已用 `snooze --for 500` 覆盖解除（`Pending snoozed notifications` 清空、6901 计数 0），随后重测。

### 10.3 第 2 轮：有效轮（锁屏 10 分钟）

操作链：首页 `开始` `[674,814][794,899]`（点 734,856）→ 2.5 s 后错误卡「这次没能开始播放 / 还没有导入课程，可以先只用倒计时」→ 卡内 `仅倒计时` `[647,909][886,994]`（点 **766,951**）→ 立即记 T1 → 0.4 s 后 `input keyevent 223` 息屏。

| 项 | 值（设备本地时钟 CST） |
|---|---|
| T1 | 11:25:40.525（`1794540340525`） |
| 息屏后回读 | `mWakefulness=Asleep`（0.9 s 后） |
| 6901 基线 | 0（轮询开始前回读） |
| T2（首次轮询命中 6901） | 11:35:41.618（`1794540941618`） |
| **delta = T2 − T1** | **601,093 ms** |
| 通知 `when` | 11:35:40.661（`1794540940661`，即 **T1+600,136 ms**） |
| 命中判定窗口 | **+600,136 ~ +601,093 ms**（宽 957 ms，轮询周期约 1.2 s） |
| 命中时电源态 | `mWakefulness=Asleep`（**全程未唤醒屏幕**） |
| 名义时长 | 600,000 ms |
| 偏差 | 较名义晚 **+0.14 ~ +1.09 s**（`when` 早于轮询命中，落在一个轮询周期内） |

命中原文：

```
    NotificationRecord(0x07520bb7: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=alarm groupKey=silent vis=PUBLIC))
```

到点后 `input keyevent 224` + `wm dismiss-keyguard`，`mCurrentFocus=…MainActivity`，界面文本：

```
计时完成 | 滴眼药水 | 本次 10 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

`dumpsys alarm` 闹钟原文（本轮最近一条）：

```
    u0a398:
      #1: Reason=alarm_cancelled elapsed=-17s499ms rtc=2026-11-13 11:35:40.764
        Snapshot:
          type=RTC_WAKEUP tag=*walarm*:com.wanghoufan.fragmentplayer.timer.TIMER_ALARM
          policyWhenElapsed: requester=-17s782ms app_standby=-9m15s902ms device_idle=-- battery_saver=-10m17s687ms tare=-10m17s687ms power_pending=+2d23h59m42s218ms
```

> `rtc=11:35:40.764` 即 **T1+600,239 ms**；与通知 `when`（+600,136 ms）、轮询 T2（+601,093 ms）三路一致。

### 10.4 判定

- **PASS** —— 在 `SCHEDULE_EXACT_ALARM=deny`、息屏已到 `Asleep` 的条件下，10 分钟场景到点触发（`TIMER_ALARM` +600.2 s、通知 `when` +600.1 s、轮询命中 +601.1 s），唤醒后界面为「计时完成 / 滴眼药水 / **本次 10 分钟**」，未出现 3 分钟或其它时长。
- 长时长（>3 min）此前未覆盖的空白，本轮已覆盖；MIUI 长时间后台驻留未出现提前中断（进程 `pid 24482` 全程未重启，`exit-info` 无 11:08 之后的新记录）。
- 第 1 轮的 `NOT_VALID` 由本轮自造的 `snooze` 引起，**不计入产品缺陷**。

### 10.5 恢复现场与清场

| 项 | 结果 |
|---|---|
| 场景时长改回 | 编辑对话框分钟框 `3`、秒框 `0` → 保存 → 首页 dump **`text="03:00"`** |
| `SCHEDULE_EXACT_ALARM` | **deny**（`get` 回读，保持用户原状） |
| 6901 通知残留 | **0**（点「知道了」后 App 已 cancel） |
| snooze 残留 | 无（`Pending snoozed notifications` 为空） |
| 手机临时文件 | `/sdcard/ui.xml` 已 `rm`，复查 `No such file or directory` |
| 用户文件 | 未创建、未删除；未进入系统文件选择器 |
| 屏幕参数 | 未改动（未改 `wm size/density/rotation`、未改亮度） |

### 10.6 未测项（本轮）

- 未测通知铃声/振动的实际听感（本轮只验 `NotificationRecord` 存在与电源态）。
- 未测锁屏界面上通知的**可见样式**（`vis=PUBLIC` 已声明；本轮截图只取到唤醒后的前台界面）。
- 第 1 轮出现的「外部通知唤醒屏幕」未做归因（属用户机日常消息，未取证具体来源）；第 2 轮全程 `Asleep`，未复现。
- 仍未覆盖多轮连续 10 分钟计时、以及 `SCHEDULE_EXACT_ALARM=allow` 下的长时长对照。
## 11. 0.2.6 最终构建（倍率标签）复测

> 本节为**追加**内容，不修改第 1–10 节。本轮验证源码新增的「计时页 `timeFlow≠1` 显示『测试 ×N』红色标签」与中英两条字符串，必须用**新构建产物**重新取证。
> 未改源码、未 commit、未 push；未创建/删除手机用户文件，未进入系统文件选择器。
> 设备：Redmi Note 12 Pro（1080×2400，Android 设备本地时钟 CST / UTC+8）。

### 11.1 构建产物

| 项 | 值 |
|---|---|
| 命令 | `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 12s**（`mergeDebugResources` / `compileDebugKotlin` / `dexBuilderDebug` / `packageDebug` 均实际执行） |
| 编译告警 | `TimerScreen.kt:469:45 Condition is always 'true'.`（既有 warning，未修） |
| 产物路径 | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/app/build/outputs/apk/debug/app-debug.apk` |
| 字节数 | **18,799,725 B**（第 9 节 17,596,484 B / 第 10 节沿用同包，本轮明显变大） |
| SHA-256 | `5ffad6dc502137a7789d38e1f8dccef152e245172cc96d6d292e1baef1d4c044` |
| 与旧包比对 | 与第 9 节 `a80f82bf…0876` **不同**，确认本轮为新产物 |

### 11.2 覆盖安装与版本回读

- 命令：`adb install -r …/app-debug.apk` → `Performing Streamed Install` / `Success`（未卸载）。
- 装后加载路径：`/data/app/~~rgtsO_qJujBR_oC5VXamcA==/com.wanghoufan.fragmentplayer-71i8CDOZi1HQJteMKEyBmw==/base.apk`。

| 时点 | versionCode | versionName | firstInstallTime | lastUpdateTime |
|---|---|---|---|---|
| 安装前 | 17 | 0.2.6 | 2026-11-13 10:11:01 | 2026-11-13 10:53:44 |
| 安装后 | **17** | **0.2.6** | **2026-11-13 10:11:01（未变，覆盖安装保数据）** | **2026-11-13 11:40:13** |

> 版本号未变但产物已变（新增倍率标签），认包以 SHA-256 为准。

### 11.3 单元测试

- 命令：`./gradlew :app:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL in 21s**（`28 actionable tasks: 28 executed`）。
- 报告统计（`app/build/test-results/testDebugUnitTest/*.xml` 共 12 个类）：**tests=64, failures=0, errors=0, skipped=0**。
- 新增类 `TimerControllerStartTimingTest` 2 项、`TimeFlowMathTest` 7 项均在报告内且全绿。

### 11.4 首页回读（步骤 4）

- 先 `rm -f /sdcard/ui.xml`，`uiautomator dump` 输出含 `UI hierchary dumped to: /sdcard/ui.xml`。
- 首页文本：`碎片时间 | 点一下，开始今天的英语时间 | 我的场景 | 刷牙 | 02:00 | 开始 | 滴眼药水 | 03:00 | 开始 | + 新增场景 | 当前课程 | 还没有导入课程 | … | 仅倒计时 | 计时 | 课程 | 设置`。
- 「滴眼药水」仍为 **`03:00`**（bounds `[99,869][249,934]`）；卡片「开始」bounds `[674,814][794,899]`，中心 **(734,856)**。

### 11.5 锁屏 3 分钟（步骤 5）

操作链：点「开始」(734,856) → 2.5 s 后错误卡「这次没能开始播放 / 还没有导入课程，可以先只用倒计时」→ 卡内「仅倒计时」bounds `[647,909][886,994]`（点 **766,951**）→ 记 T1 → 0.4 s 后 `input keyevent 223` 息屏 → 每 1 s 轮询 `dumpsys notification --noredact` 的 `id=6901`。

| 项 | 值（设备本地时钟 CST） |
|---|---|
| 6901 基线 | **0**（点击前回读） |
| T1（点击「仅倒计时」后） | 11:41:42.405（`1794541302405`） |
| 息屏 1 s 后电源态 | **`mWakefulness=Asleep`**（未停留 Dozing） |
| 最后一次未命中 | 11:44:42.293（`1794541482293`，+179,888 ms） |
| T2（首次轮询命中 6901） | 11:44:43.662（`1794541483662`，+181,257 ms） |
| 命中判定窗口 | `+179,888 .. +181,257 ms`（宽 1,369 ms，本轮轮询周期约 1.4 s） |
| **delta = T2 − T1** | **181,257 ms** |
| 名义时长 | 180,000 ms |
| 偏差 | 名义值落在命中窗口内（窗口覆盖 180,000） |
| 命中时电源态 | **`mWakefulness=Asleep`**（全程未唤醒） |

命中原文：

```
    NotificationRecord(0x0c14ce4b: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=alarm groupKey=silent vis=PUBLIC))
```

唤醒（`keyevent 224`）+ `wm dismiss-keyguard` 后，`mCurrentFocus=…MainActivity`，界面文本：

```
计时完成 | 滴眼药水 | 本次 3 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

判定：**PASS** —— 息屏至 `Asleep` 状态下 3 分钟到点、通知发出，唤醒后界面为「计时完成 / 滴眼药水 / **本次 3 分钟**」。

### 11.6 倍率标签验证（步骤 6）

**工具口径（必读）**：计时页倒计时**运行中**每秒刷新，`uiautomator dump` 会因 `ERROR: could not get idle state.` 等待约 10 s 后失败；失败重试时 10× 的 12 s 已跑完，只剩到点页。因此本步改为 `screencap` 即时截图 + 本机 Vision OCR（`zh-Hans`）读文本 + 像素颜色判红，**不声称 dump 成功**。截图与 OCR 均取自同一台设备同一页面。

#### 11.6.1 切到 10×（界面操作）

- 回首页 → 底部「设置」(907,2204) → 设置页 dump（`UI hierchary dumped to: /sdcard/ui.xml`）读到 `1× | 2× | 6× | 10× | 仅影响计时速度，不改变音频播放速度。非 1× 时本轮不记入历史与进度。`。
- 「10×」bounds `[188,1424][299,1499]` → 点 **(243,1461)**。
- 回「计时」(172,2204) → 点首页底部「仅倒计时」bounds `[421,1754][660,1839]` → **(540,1796)**。

#### 11.6.2 10× 标签界面文本

| 截图 | 时刻（OCR 读状态栏） | 标签行文本 | 倒计时 |
|---|---|---|---|
| `tag1`（点击后 0.3 s） | 11:46 | **`测试 ×10仅倒计时`** | **01:57** |
| `tag2`（+0.9 s） | 11:46 | **`测试 ×10仅倒计时`** | **01:45** |
| `tag3`（+1.0 s） | 11:46 | **`测试 ×10仅倒计时`** | **01:27** |

- 剩下 2 分钟的场景在真实约 12 s 内跑完（144 s ÷ 10 ≈ 14 s，扣除截图开销），证明 10× 生效。
- 颜色判定：标签区域 `x[540,700) y[160,215)`（设备像素）内红色像素 **1,517** 个，最强色 `rgb(255,180,171)`（Material 3 dark `error` 色 `#FFB4AB`，肉眼为浅红）；同高度左侧空白对照区 `x[60,300)` 红色像素 **0**。
- 到点后 dump（界面已静止）：`计时完成 | 刷牙 | 本次 2 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置`。

> 首轮尝试用 dump 抓标签失败（10 次全部 `ERROR: could not get idle state.`，第二次成功时已是「计时完成 / 本次 2 分钟」），属工具等待 idle 的过程，**不是产品缺陷**；改用截图取证后通过。

#### 11.6.3 改回 1× 反向验证

- 点「知道了」(540,789) → 设置 (907,2204) → 点「1×」bounds `[188,962][262,1037]`（**225,999**）→ 回「计时」(172,2204) → 点「仅倒计时」(540,1796)。
- 点击后 0.4 s / 1.9 s 两张截图 OCR：标签行文本均为 **`仅倒计时`**（**无「测试 ×」字样**），倒计时 `02:00` → `01:58`（1× 真实速率）。
- 同区域 `x[540,700) y[160,215)` 红色像素 **0**（对照 10× 的 1,517）。
- 设置页截图 OCR：`1×` 前为单选圆点 **`◎`**，`2× / 6× / 10×` 前为普通项目符号 `•`，确认已切回 1×。

#### 11.6.4 收尾

- 点「提前结束」→ 对话框 dump：`记入历史？ | 不记入 | 记入`（`不记入` bounds `[474,1216][632,1291]`）→ 点 **(553,1253)** → 回首页，dump 无进行中计时。

### 11.7 本轮证据文件

| 文件 | 绝对路径 | 字节 | SHA-256 |
|---|---|---|---|
| 本文件（追加节） | `…/docs/qa/evidence-2026-11-13/README.md` | — | — |
| 锁屏到点界面 | `…/docs/qa/evidence-2026-11-13/final2-lockscreen.png` | 107,937 | `96dc02153efe0f283b98e94325b8ef2229894c318caf1041c89f98bcee08c8a4` |
| 10× 标签（OCR 源） | `…/docs/qa/evidence-2026-11-13/final2-timeflow-tag.png` | 94,730 | `d0cdf4ec404c86f813f99b15b382958a354da93cf54f6448b9612fbe6bfa29c1` |

### 11.8 失败点与未测项（本轮）

- 失败点：**无**。锁屏 3 分钟到点命中；10× 标签出现且为红色；1× 标签消失；单测 64/64。
- 工具限制：`uiautomator dump` 在倒计时运行中必然 `could not get idle state.`，标签取证改用 `screencap` + Vision OCR（已如实记录，未改写为 dump 成功）。
- 未测项：
  - 未验证 10× 时「非 1× 不记入历史与进度」的落库侧效果（本轮只验标签与到点时长；4 次 10×/1× 轮次均未进入历史写入路径的独立取证）。
  - 未测 2× / 6× 的标签数值显示（只测了 1× 与 10× 两端）。
  - 未测英文界面下 `Test ×%1$d` 的显示（只回读了 `values-en/strings_timer_ui.xml` 内的字符串定义）。

### 11.9 收尾与清场

| 项 | 结果 |
|---|---|
| 时光流速 | **1×**（设置页 `◎` 单选圆点，截图 OCR 回读） |
| `SCHEDULE_EXACT_ALARM` | **deny**（`appops get` 回读） |
| 6901 通知残留 | **0** |
| snooze 残留 | 无（`Pending snoozed notifications` 为空） |
| 屏幕参数 | 未改动（`wm size` 1080×2400 / `wm density` 440，原值） |
| 手机临时文件 | `/sdcard/ui.xml`、`tag1-3.png`、`x1-2.png`、`set11.png`、`final2-*.png` 均已 `rm`，复查 `No such file or directory` |
| 用户文件 | 未创建、未删除；未进入系统文件选择器 |
| reverse/forward | 未为本任务新增或修改（目标设备列表为空） |

## 12. 0.2.7 铃声选项/短时长/试听复测

- 时间：2026-11-13 12:01–12:19
- 设备：Redmi Note 12 Pro（1080×2400，density 440，Android 16 / targetSdk 36）
- 范围：0.2.7 新增的「铃声」4 选项、每个内置音只响一次约 2 秒、（点选项即试听 / 「试听铃声」按钮）、持久化、到点 3 分钟短铃。未改源码、未 commit、未 push；未创建/删除手机用户文件，未进文件选择器。

### 12.1 构建与安装

| 项 | 值 |
|---|---|
| 构建命令 | `./gradlew :app:assembleDebug`（`BUILD SUCCESSFUL in 2s`） |
| APK 路径 | `/Users/zzymima0000/Developer/coding/1.Active/069-ing-碎片时间播放器/app/build/outputs/apk/debug/app-debug.apk` |
| APK 字节数 | **17,907,212** bytes |
| APK SHA-256 | **`a29d8b82334af61bafbdb24c6c127fcdad9dfedd53c9b2118d56914c2ed6f2e2`** |
| 包内声明（aapt2） | `versionCode='18' versionName='0.2.7'` |
| 签名 | Android Debug，证书 SHA-256 `8e82a7975008e8ec87ff0f2ecadf495b5263effa34aaec9ab6f135ebea911cf3` |

安装方式：`adb install -r`（覆盖安装，未卸载），`Success`。

装后回读 `dumpsys package com.wanghoufan.fragmentplayer`：

```text
    versionCode=18 minSdk=26 targetSdk=36
    versionName=0.2.7
    lastUpdateTime=2026-11-13 12:01:51
      firstInstallTime=2026-11-13 10:11:01
```

装前版本为 `versionCode=17 / versionName=0.2.6`，装前 `firstInstallTime=2026-11-13 10:11:01` —— **firstInstallTime 未变（10:11:01）**，确认是覆盖安装。

### 12.2 设置页 4 个铃声选项（dump 原文）

设置页 `uiautomator dump` 文本与 bounds（设备像素）：

```text
铃声 | [88,680][208,765]
叮咚三连 | [188,808][449,899]
钟声 | [188,962][319,1053]
柔和提示 | [188,1116][449,1207]
系统闹钟 | [188,1270][449,1361]
每个铃声只响一次，约 2 秒就停。点一下即可试听。 | [88,1403][992,1565]
测试震动 | [189,1611][428,1696]
试听铃声 | [652,1611][891,1696]
```

4 个选项文本齐全（叮咚三连 / 钟声 / 柔和提示 / 系统闹钟），说明文案命中「每个铃声只响一次，约 2 秒就停」。截图：`settings-tones.png`。

源码核对（只读）：`AlertTone.entries` = `CHIME, BELL, SOFT, SYSTEM`，默认 `alertTone = AlertTone.CHIME`（叮咚三连）；`TimerAlertSound.MAX_DURATION_MS = 2_500L`，`isLooping = false`，3 个内置音走 `R.raw.tone_chime / tone_bell / tone_soft`，系统铃声走 `RingtoneManager.TYPE_ALARM` 并在 2500 ms 截断。

### 12.3 逐项试听与时长取证

取证口径：点击选项后**在同一条设备 shell 内**立刻 `dumpsys audio` 抓 `AudioPlaybackConfiguration`（`u/pid:10398` = 本应用），再循环到该播放器消失；之后固定再等 3 秒复查一次，确认无活跃播放器。时间戳用设备 `date +%s%3N`（毫秒），delta 为「点击 → 观测不到活跃播放器」的粗测值（每次 dumpsys 本身耗时数百毫秒，故略大于真实音频时长）。

```text
叮咚三连 (tap 318,853)  T0=1794542581950
  AudioPlaybackConfiguration piid:4303 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12084 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags=0x800 tags=
  CLEARED t=1794542583364 delta_ms=1414

钟声 (tap 253,1007)  T0=1794542591254
  AudioPlaybackConfiguration piid:4311 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12084 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags=0x800 tags=
  CLEARED t=1794542592923 delta_ms=1669
  AFTER_3S_COUNT=0

柔和提示 (tap 318,1161)  T0=1794542604422
  AudioPlaybackConfiguration piid:4319 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12084 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags=0x800 tags=
  CLEARED t=1794542606307 delta_ms=1885
  AFTER_3S_COUNT=0
```

三项均出现 `state:started`，并在 1.4–1.9 秒内消失，3 秒复查 `COUNT=0`。

对照（旧版长响证据，来自同机 `dumpsys audio` 历史缓冲）：

```text
11-13 10:19:28:991 player piid:4199 event:started
11-13 10:19:44:004 player piid:4199 event:stopped      ← 旧版约 15.0 秒
11-13 10:23:25:113 player piid:4207 event:started
11-13 10:23:40:074 player piid:4207 event:stopped      ← 约 15.0 秒
```

0.2.7 的三项均 ≤ 1.9 秒，未见 15 秒长响。

### 12.4 「试听铃声」按钮

当前选中为「柔和提示」时点「试听铃声」`(771,1653)`：

```text
T0=1794542616095
  AudioPlaybackConfiguration piid:4327 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12084 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags=0x800 tags=
  CLEARED t=1794542617845 delta_ms=1750
  AFTER_3S_COUNT=0
```

### 12.5 持久化验证

1. 点「钟声」→ `run-as` 回读：`files/datastore/settings.preferences_pb` 内 `alert_tone` = **`BELL`**。
2. `am force-stop com.wanghoufan.fragmentplayer` → `ps` 计数 0 → `am start` 重启 → 进设置页 dump（4 项文本齐全）→ 再次回读：

```text
	time_flow
alert_tone
BELL
```

持久化成立（`run-as` 未被拒，无需退回截图 OCR 推断）。

### 12.6 到点 3 分钟复测（精确闹钟 deny，锁屏）

- `appops get com.wanghoufan.fragmentplayer SCHEDULE_EXACT_ALARM` → **`deny`**（装前与装后一致）。
- 说明：本轮尝试执行 `appops set … deny` 被本客户端安全策略拦截，原文 `大模型判定该命令存在安全风险，已禁止执行。原因：修改应用闹钟权限设置`；该项原值已是 `deny`，未做改动，复测在该状态下进行。
- 流程：首页点「滴眼药水 / 开始」(734,857) → 2.5 s 后点错误卡「仅倒计时」(766,951) → 记 T1 → 0.4 s 后 `input keyevent 223` 息屏（`dumpsys power` 回读 `mWakefulness=Asleep`）。
- 为让屏幕真正熄灭，测试期间临时 `svc power stayon false`（原 `stay_on_while_plugged_in=2`，收尾已恢复 2）。

第一轮（轮询 `dumpsys notification` 抓 6901）：

```text
T1=1794542691880
HIT T2=1794542872627 DELTA=180747
NotificationRecord(0x0a091798: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 … category=alarm groupKey=silent vis=PUBLIC))
```

delta **180747 ms**，覆盖 180000 ms（3 分钟）。

第三轮（精确等到 T1+178 s 后密集抓 `dumpsys audio`）：

```text
T1=1794543178514
POLL t=1794543358307 audio=0 delta=179793
POLL t=1794543358574 audio=1 delta=180060
AUDIO_STARTED t=1794543358574 delta=180060
  AudioPlaybackConfiguration piid:4359 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12782 state:idle attr:AudioAttributes: usage=USAGE_ALARM …
  NotificationRecord(0x0e922cc6: … id=6901 … category=alarm …)
AFTER3S t=1794543361828 COUNT=0
```

第四轮（目标取 `state:started`）：

```text
T1=1794543428087
AUDIO_HIT t=1794543608265 delta=180178
SNAP1 t=1794543608339
  AudioPlaybackConfiguration piid:4375 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12782 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags=0x800 tags=
SNAP2 t=1794543608599
  AudioPlaybackConfiguration piid:4375 deviceId:3 type:android.media.MediaPlayer u/pid:10398/12782 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags:0x800
SNAP3/SNAP4/SNAP5 t=1794543608858 / 1794543609117 / 1794543609380
  … piid:4375 … state:started … usage=USAGE_ALARM
AFTER3S t=1794543612645 COUNT=0
```

到点铃声：本例为「叮咚三连」（收尾前已是 CHIME），到点后连续 5 次快照（约 1.04 秒窗口）均为 `state:started` + `usage=USAGE_ALARM`，3 秒后 `COUNT=0`，即已停止；无 15 秒长响。

唤醒后 dump 完成页：

```text
计时完成 | [55,149][339,251]
滴眼药水 | [110,350][394,452]
本次 3 分钟 | [110,480][435,571]
到点完成，课程进度已保存 | [110,599][831,684]
知道了 | [444,744][636,835]
```

截图：`tone-at-finish.png`。

工具限制（如实记录）：第二轮用设备内 `dumpsys notification --noredact | grep` 循环轮询，210 s 超时未命中（息屏 doze 下该 dumpsys 单次耗时变长、轮询粒度变粗），但唤醒后完成页为「计时完成 / 本次 3 分钟」、6901 现场存在。第三轮起改为「精确 sleep 到点 + 抓 `dumpsys audio`」，不再依赖通知轮询，取证成功。T1/T2/delta 结论以第一轮（180747 ms）与第三/四轮（180060 / 180178 ms）为准。

### 12.7 本轮证据文件

| 文件 | 相对路径 | 字节 | SHA-256 |
|---|---|---|---|
| 设置页铃声区 | `docs/qa/evidence-2026-11-13/settings-tones.png` | 161,049 | `5d24b42a65b78468d03a614adeb342515db58c6a8475b6406e94bb3652dd2d46` |
| 到点完成页 | `docs/qa/evidence-2026-11-13/tone-at-finish.png` | 107,822 | `d34498297f8dc616a7512a3cbb357eeb2ab3d1d25e5f8348db429b9a55fa0a9a` |

### 12.8 判定与未测项

- 判定：**PASS**——4 个铃声选项文本齐全；三项内置音均 `state:started` 且在 1.4–1.9 秒内停止；点选项即试听、「试听铃声」按钮均出声且 ≤1.75 s；`alert_tone="BELL"` 跨 force-stop 持久化；锁屏 3 分钟到点 delta 180.06–180.75 s，到点铃声 `state:started`（USAGE_ALARM）并在 3 秒复查时已停止；完成页「计时完成 / 本次 3 分钟」。
- 未测项：
  - 「系统闹钟」选项未试听取证（任务只要求前三项；该音源为系统默认闹钟，受系统音量与机型音源影响）。
  - 未验证到点铃声在「到点响铃」开关关闭 / 震动关闭组合下的行为（本轮两项均为默认开启）。
  - 未能用截图 OCR 复核选中圆点（`run-as` 可用，直接回读了 datastore 值，未走该降级路径）。
## 13. 0.2.7 补充：系统闹钟试听与响铃开关

- 时间：2026-11-13 12:22–12:25
- 设备：Redmi Note 12 Pro（1080×2400，density 440）
- 版本：**0.2.7 / versionCode 18**（未重装，只回读 `dumpsys package`：`versionCode=18 / versionName=0.2.7 / lastUpdateTime=2026-11-13 12:01:51`）
- 范围：补第 12 节两项未测项——「系统闹钟」选项试听时长、到点响铃开关关闭后的到点行为。未改源码、未 commit、未 push；未创建/删除手机用户文件，未进文件选择器。

### 13.1 取证口径的两处修正（如实记录）

1. `dumpsys audio` 的播放器行**不含包名**（形如 `AudioPlaybackConfiguration piid:4383 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12782 state:started attr:AudioAttributes: usage=USAGE_ALARM …`）。按任务书执行 `dumpsys audio | grep -i -A4 fragmentplayer` 实测**命中 0 行**（`grep -c`：a1..a4 四份快照全为 0）。改用同行的 `u/pid:10398/12782` 定位本应用：uid `10398` 与通知 key `…|6901|null|10398` 一致，pid `12782` 由 `pidof com.wanghoufan.fragmentplayer` 回读。
2. `dumpsys notification | grep -c "6901|null"` 会把**历史行**（`intercepted: …6901…`）一并计入（本轮基线即 5 行），不能当「通知活跃」。改用 `grep "NotificationRecord.*id=6901"` 精确定位活跃记录；第 12:23:46 那一轮因此被判为**采样不足、不作 B 的判定依据**（该轮确实到点并出现完成页与 6901，只是命中判定被历史行污染）。

### 13.2 A｜「系统闹钟」试听（点击 → 停止）

前置：设置页 dump（先 `rm -f /sdcard/ui.xml`，输出含 `UI hierchary dumped to: /sdcard/ui.xml`）定位 `系统闹钟` bounds=`[188,1270][449,1361]`，点击 **(318,1315)**（in-safe）。

点击与 `dumpsys audio` 在**同一条设备 shell** 内背靠背执行（消除 adb 往返，点击到首份快照 ~0.12 s）。

第 1 次：

```text
T0=1794543734886（点击，= 12:22:14.886）
a1 完成于 T1=1794543735004（点击后 118 ms）
  AudioPlaybackConfiguration piid:4383 deviceId:0 type:android.media.MediaPlayer u/pid:10398/12782 state:started attr:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION flags:0x800 tags= bundle=null sessionId:8553 …
a4（点击后 5443 ms）：无本应用播放器
事件时间线（00.2.7 音频事件缓冲）：
  12:22:14:913 new player piid:4383 uid/pid:10398/12782 …
  12:22:14:914 player piid:4383 new AudioAttributes:AudioAttributes: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION …
  12:22:14:950 player piid:4383 event:started
  12:22:17:455 player piid:4383 event:stopped
  12:22:17:489 releasing player piid:4383
```

第 2 次（复测）：

```text
T0=1794543764612（点击，= 12:22:44.612）
b1（点击后 153 ms）  state:started  usage=USAGE_ALARM
b2（点击后 1273 ms） state:started  usage=USAGE_ALARM
b3（点击后 3337 ms） 无本应用活跃播放器
b4（点击后 5398 ms） 无本应用活跃播放器
  12:22:44:637 new player piid:4391 uid/pid:10398/12782 …
  12:22:44:663 player piid:4391 event:started
  12:22:47:168 player piid:4391 event:stopped
```

| 项 | 第 1 次 | 第 2 次 |
|---|---|---|
| 点击时刻（设备本地） | 12:22:14.886 | 12:22:44.612 |
| `state:started` + `usage=USAGE_ALARM` | ✅ +118 ms 快照 | ✅ +153 ms / +1273 ms 快照 |
| 播放器创建 | +27 ms | +25 ms |
| `event:started` | +64 ms | +51 ms |
| `event:stopped` | +2569 ms（自点击） | +2556 ms（自点击） |
| **started → stopped** | **2505 ms** | **2505 ms** |
| 点击后 3 s 复查 | 无活跃播放器 | 无活跃播放器（+3337 ms） |

判定：**PASS（附口径）** —— `state:started` 在点击后 0.12–0.15 s 内可证，`usage=USAGE_ALARM`；音频实际播放 **2505 ms**，与源码 `TimerAlertSound.MAX_DURATION_MS = 2_500L`（`handler.postDelayed(stopTask, maxDurationMs)`）一致，多出的 5 ms 为音频事件日志延迟；**自点击到播放器释放 2.556–2.569 s**，其中 51–64 ms 是 MediaPlayer 创建/`start()` 开销。旧版「一直响 ~15 秒」未复现（第 12 节同机历史缓冲对照为 15.0 s）。

### 13.3 B｜「到点响铃」关闭后到点不响

操作链（坐标均 `uiautomator dump` 实测）：

| 步骤 | 目标 | bounds | 点击 | 回读 |
|---|---|---|---|---|
| 1 | 到点响铃 开关 | checkable 节点 `[849,515][992,647]` | (920,581) | 点击前 `checked=true` → 点击后 **`checked=false`**（「到点震动」开关 y=405 仍 true） |
| 2 | 10× | `[188,1403][299,1478]`（需先 `input swipe 540 1600 540 900` 滚出） | (243,1440) | 行为验证见下：2 分钟场景真实 ~12 s 跑完 |
| 3 | 计时 Tab | `[115,2164][230,2245]` | (172,2204) | `mCurrentFocus=…MainActivity` |
| 4 | 仅倒计时 | `[421,1754][660,1839]` | (540,1796) | 计时启动 |

10× 是否真生效：`run-as` 回读 `files/datastore/settings.preferences_pb` 的 `time_flow` = **1** → 点击后变 **10**（protobuf 解析见 13.4）；并以「2 分钟场景 → `本次 2 分钟`」实际耗时 **≈12.4 s** 作行为交叉验证。

到点采样（设备内循环，同一 shell，无 adb 往返）：

```text
T0=1794543871582（点击「仅倒计时」，= 12:24:31.589）
t=…871688 A=0 d=106        ← 首个样本
t=…883895 A=0 d=12313      ← 最后一个样本
样本数 90，A=1 次数 0，A 最大值 0（覆盖 d=106…12313 ms，含 10× 的 12 s 到点时刻）
```

音频侧交叉核对：整轮结束后 `dumpsys audio | grep -c "new player.*uid/pid:10398"` = **12**，与计时启动前基线 **12** 相同；最新一条仍是 `12:22:44:637 new player piid:4391`（A 段复测那次），**到点期间没有任何新的 MediaPlayer 创建**。

通知与完成页（到点后回读）：

```text
NotificationRecord(0x001fa48d: pkg=com.wanghoufan.fragmentplayer user=UserHandle{0} id=6901 tag=null importance=4 key=0|com.wanghoufan.fragmentplayer|6901|null|10398 bbbc=0: Notification(channel=timer_alert_v2 … category=alarm groupKey=silent vis=PUBLIC))
6900（计时进行）：无
界面：计时完成 | 刷牙 | 本次 2 分钟 | 到点完成，课程进度已保存 | 知道了 | 再来一轮 | 计时 | 课程 | 设置
```

判定：**PASS** —— 开关实测 `checked=false`；到点前后 90 个样本、12.3 s 窗口内本应用**无任何** `state:started` 播放器，事件缓冲亦无新 player；**6901 通知照常出现**、6900 已取消、完成页为「计时完成 / 刷牙 / 本次 2 分钟」。

截图：`b-ringoff-finish.png`。

> 旁证（不作判定依据）：12:23:46 那一轮（同样关闭响铃、10×）到点后 12:24:11 回读亦为「无本应用播放器 + 6901 活跃 + 完成页「本次 2 分钟」」，因轮询判定被历史行污染，仅作旁证。

### 13.4 收尾恢复与回读

| 项 | 操作 | 回读 |
|---|---|---|
| 到点响铃 | 点击开关 (920,581) | checkable 节点 `checked=true`（dump 回读） |
| 时光流速 | 点击 `1×` `[188,941][262,1016]` → (225,1906) | `time_flow` = **1** |
| 铃声 | 点击 `叮咚三连` `[188,808][449,899]` → (318,853) | `alert_tone` = **CHIME** |

`run-as com.wanghoufan.fragmentplayer cat files/datastore/settings.preferences_pb` 原文（`strings`）：

```text
time_flow
alert_tone
CHIME
alert_sound
```

本机 protobuf 解析同一份文件（`time_flow` 为 int32，`strings` 读不出数值，故补解一层）：

```text
time_flow  -> field3(int)=1
alert_tone -> field5(str)=CHIME
alert_sound-> field1(int)=1        （booleanPreferencesKey("alert_sound") = 到点响铃，开）
```

键名核对（只读源码）：`SettingsRepositoryImpl.KEY_ALERT_SOUND = booleanPreferencesKey("alert_sound")`、`KEY_ALERT_TONE = stringPreferencesKey("alert_tone")`、`KEY_TIME_FLOW = intPreferencesKey("time_flow")`；`SettingsScreen.kt` 的 `seu_label_alert_sound` = 「到点响铃」、`seu_label_alert_tone` = 「铃声」。

### 13.5 本轮证据文件

| 文件 | 绝对路径 | 字节 | SHA-256 |
|---|---|---|---|
| 响铃关闭到点完成页 | `…/docs/qa/evidence-2026-11-13/b-ringoff-finish.png` | 100,907 | `bd3b4c9c54f60645006b06fd95f9e2271e51c7cbe7c686c9c7370d8e22db723e` |
| 设置页恢复现场（1× 选中、响铃已开） | `…/docs/qa/evidence-2026-11-13/a-verify-10x-back.png` | 161,265 | `9ca0725a9face147160e2c708a4176c889a1a237fe85fe9bc7e83769f6889f99` |

A 段的时长证据为 `dumpsys audio` 文本快照与事件时间线（本轮未截图，理由：判据是毫秒级 `started/stopped`，截图读不出）。

### 13.6 失败点与未测项（本轮）

- 失败点：**无**。
- 工具/口径限制（如实记录，非产品缺陷）：
  - `dumpsys audio` 播放器行不含包名，任务书的 `grep fragmentplayer` 命中 0 行，已改用 `u/pid:10398/12782`（13.1）。
  - `dumpsys notification | grep -c "6901|null"` 会计入历史行，改用 `NotificationRecord.*id=6901`（13.1）。
  - `uiautomator dump` 在倒计时运行中仍会 `ERROR: could not get idle state.`，本轮到点采样全程不依赖 dump，完成页等静止后再 dump。
- 未测项：
  - 未测「到点响铃关闭 + 到点震动开启」时震动的实际触感（本轮只验铃声缺席、通知与完成页仍在）。
  - 未测「到点响铃关闭」下系统闹钟铃声（SYSTEM）的对照（A 段试听时 `alert_tone=SYSTEM`，B 段响铃已关闭，两条未交叉）。
  - 未测英文界面下的设置项文案。
- 清场：手机临时文件 `/sdcard/ui.xml`、`a1..a4.txt`、`b1..b4.txt`、两张 `screencap` 均已 `rm`，复查 `No such file or directory`；`adb reverse --list` 为空（本任务未新增映射，也未移除他人映射）；未改动 `wm size/density`、亮度、音量与通知权限。
