# Android 15 跨机型复核｜2026-11-14 接手轮

> 设备：Redmi Note 11T Pro+（23054RA19C，Android 15 / SDK 35，1080×2460 @440dpi）
> 被测包：0.2.8(21)，SHA-256 `779c8433bce45045c07d266dfa20d90bf8a46132047c446286a40be41b01814c`（设备已装，未重装）
> 目的：补上一轮未覆盖的 Android 15 edge-to-edge / 安全区，并当场复核返回键、通知权限与完成页文案
> 时间：2026-11-14 13:16 +08:00

## 强制回执

【前置体检回执】
体检是否执行：已执行
逐项结果：①灭屏 通过（screen_off_timeout=2147483647，原值即最大，未改；stay_on=0）②端口 通过（本设备无本地服务；reverse 空；forward 仅他机 OPPO 映射，未动）③服务 通过（纯本地 App，无服务端，不适用）④反向映射 通过（无本项目映射）
结论：允许开跑

【MIUI 专项门禁回执】
前台包名：com.wanghoufan.fragmentplayer 已核对（input 前 dumpsys window mCurrentFocus）
测试 APK：未安装
文件选择器：未使用
系统通知权限：允许通知=**off** 锁屏=**off** 发声=**off** 振动=**off**
媒体完整性：完整课程文件 0 个；排除 .downloading/.part 0 个（本机无课程目录）
结论：存在阻断项——通知权限全关，本机不能用于「到点提醒可见/有声」的正向验收；其余界面与返回键结论可信

【UI 取证新鲜度回执】
- dump 前清理：已删旧文件（每次 rm -f /sdcard/ui.xml）
- dump 结果：成功（dumped to）
- 交叉证据：截图像素（01/02/03）+ dumpsys window mCurrentFocus
- 结论：可信

【点击门禁回执】
安全区判定：本次无坐标 tap，只用系统 BACK 键与 am start
点击与回读：无坐标点击
破坏性按钮：无
结论：点击操作可信（无点击）

## 证据清单

| 文件 | 内容 |
|---|---|
| `01-launch.png` / `01-launch.xml` | 计时完成页（滴眼药水 3 分钟）首屏：状态栏、标题、底部 Tab 与导航栏 |
| `02-after-back.png` / `02-after-back.xml` | 第 1 次 BACK 后：界面仍为计时完成页 |
| `03-after-back2.png` / `03-after-back2.xml` | 第 2 次 BACK（间隔 >2s）后：界面仍为计时完成页 |
| `04-notification-settings.xml` | 系统通知设置：允许通知/锁屏/发声/振动 全部 checked=false |

## 结论

| 检查 | 判据 | 实测 | 结论 |
|---|---|---|---|
| Android 15 edge-to-edge / 安全区 | 标题不压状态栏，底栏不被手势条遮挡 | 标题 bounds `[55,177][331,277]`，状态栏高约 130px；Tab 文字 `y=2226–2302`，导航栏自 2337 起 | PASS |
| 字体下限 | 正文 ≥15sp、标题 ≥23sp | 标题「计时完成」高度 100px / 440dpi ≈ 36sp；正文「到点完成…」高度 81px ≈ 29sp | PASS |
| 返回键（完成页） | 非首页返回上一级，不静默停留 | 连续两次 BACK（间隔 >2s）界面均不变，仍停「计时完成」 | **FAIL（新发现 P1）** |
| 完成页文案 | 无课程时不得声称「课程进度已保存」 | 「到点完成，课程进度已保存」在 0 课程场景照常显示 | **FAIL（P2，已在上轮报告记录，本轮 Android 15 复现）** |
| 到点通知正向验收 | 允许通知 + 锁屏 + 发声开启 | 四项全 off | BLOCKED（系统前提未满足，未代改设置） |
| 应用版本一致性 | 包内 versionName/Code 与应用「关于」 | 0.2.8 / vc21，设备 lastUpdateTime 2026-11-13 13:40:24 | PASS（仅本机） |

## 新发现：返回键没有页面栈

代码事实（`MainActivity.kt:52`）：

```kotlin
BackHandler { exitBackGuard.onBackPressed() }
```

`ExitBackGuard` 只实现「2 秒内再按一次退出」，不区分当前是首页、完成页、设置页还是计时中。因此：

- 计时完成页按 BACK：只弹「再按一次退出」，不回到 IDLE 首页；
- 设置/课程 Tab 按 BACK：同样只弹退出提示，不回计时 Tab；
- 计时运行中按 BACK：同样走退出窗口，存在误退风险。

与中央 APP 规范「非首页按返回一律回上一级；首页按返回先给一次再按一次退出」冲突。
上一轮矩阵中 AND-027 标 PASS（「切后台/旋转/杀进程恢复」），但未覆盖返回键语义；本轮据此新增缺陷。

## 清场

- 已删除 `/sdcard/ui.xml`；系统设置页已退出并 `am start` 回 P069 前台。
- 未改 screen_off_timeout / stay_on / 主题 / 通知权限。
- 未安装、未卸载、未删数据；未改他人 forward/reverse。
- 设备锁保持当前绑定（23054RA19C），未 takeover、未 release。