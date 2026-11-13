# 0.2.8 桌面长按快捷方式 · 真机验收（Redmi Note 12 Pro）

- 日期：2026-11-13
- 设备：Redmi Note 12 Pro（USB）
- 包：`com.wanghoufan.fragmentplayer` 0.2.8（debug）。验收先用 vc20（SHA `076d5875…c39a`），补旋转防护后以 **vc21（SHA `779c8433bce45045c07d266dfa20d90bf8a46132047c446286a40be41b01814c`）** 复测冷启动通过；两轮均 `install -r` 覆盖、`firstInstallTime` 未变
- 覆盖安装：`adb install -r` → Success；`firstInstallTime` 保持 2026-11-13 10:11:01，数据未丢

## 验收断言与结果

| # | 断言 | 结果 | 依据 |
|---|---|---|---|
| 1 | 长按图标出现两个快捷项，文案为「开始刷牙」「开始滴眼药水」 | PASS | `01-longpress-menu.png`；`uiautomator` 命中 `item_title` 两条 |
| 2 | 点「开始刷牙」直接进入运行中的计时（不再点开始） | PASS | `02-start-brush-running.png`（标题刷牙、01:57 递减、仅倒计时） |
| 3 | 无课程设备自动按「仅倒计时」开始 | PASS | 该机无课程（首页显示「还没有导入课程」），启动即带「仅倒计时」标记 |
| 4 | 计时中再点另一个快捷方式不重开 | PASS | 点「开始滴眼药水」后仍是刷牙、剩余 00:34 继续递减（`03-reclick-ignored.png`） |
| 5 | 快捷方式启动的会话能正常到点收尾 | PASS | `04-finished.png`（本次 2 分钟、到点完成） |
| 6 | 冷启动（进程不存在）点快捷方式可直达 | PASS | 点前 `pidof` 为空 → 点后 pid=2194，`05-coldstart-running.png`（01:57）；vc21 复测 pid 5152，`07-vc21-coldstart.png` |
| 7 | 场景增删后菜单同步，无需重启 App | PASS | 新增 TestBrush 后 `cmd shortcut` 立即出现「开始TestBrush」；删除后立即消失，恢复两项 |
| 8 | 快捷方式图标分别对应牙刷 / 眼药水 | PASS | `cmd shortcut` 输出 `iconRes=drawable/ic_shortcut_brush` / `ic_shortcut_eye` |

## 关键命令输出

```text
$ adb -s <serial> cmd shortcut get-shortcuts --user 0 com.wanghoufan.fragmentplayer
ShortcutInfo {id=preset-2 ... shortLabel=开始滴眼药水, longLabel=null, rank=1,
  intents=[Intent { act=com.wanghoufan.fragmentplayer.action.START_PRESET ... }]/PersistableBundle[{...PRESET_ID=2}],
  iconRes=...[drawable/ic_shortcut_eye]}
ShortcutInfo {id=preset-1 ... shortLabel=开始刷牙, longLabel=null, rank=0,
  intents=[Intent { act=...START_PRESET ... }]/PersistableBundle[{...PRESET_ID=1}],
  iconRes=...[drawable/ic_shortcut_brush]}
```

```text
$ adb -s <serial> shell pidof com.wanghoufan.fragmentplayer   # 冷启动前
（空）
$ adb ... input tap 775 1318     # 点「开始刷牙」
$ adb -s <serial> shell pidof com.wanghoufan.fragmentplayer   # 冷启动后
2194
```

## 本轮修正

- 重建防护：`MainActivity` 只在 `savedInstanceState == null` 时消费快捷 Intent，避免旋转/重建把同一次点击再执行一遍（vc21）。
- 首版设置了 longLabel「开始刷牙 · 02:00」，MIUI 长按菜单优先显示长标签并截断成「开始刷牙 · 0…」（`01b-longlabel-truncated.png`）。已改为只保留短标签，重新出包 versionCode 20 后复测正常。

## 清场

- `screen_off_timeout` 原值 600000 未改；`svc power stayon` 已恢复 false。
- 未使用 `adb reverse` / `forward`，无残留映射。
- 设备临时文件 `/sdcard/ui.xml` 已删除。
- 设备锁：按用户要求保留绑定（profile `qoder-p069` → Redmi Note 12 Pro），未释放。

## 未验证 / 残留

- 场景「改名」未在真机上单独跑；改名与增删走同一条场景流，标签取自 `preset.name`（单测 `ShortcutStartTest` 已锁）。
- 测试轮次在历史里留下 1 条 2 分钟「刷牙」记录（13:15 到点完成，countdownOnly）；第二轮回已选「不记入」无残留。
- 有课程设备的「计时 + 续播」路径本次未跑（该机无课程）；启动链路与 App 内「开始」完全一致，由既有 0.2.7 验收覆盖。
- 长按菜单未显示时长副标题（Android 启动器不渲染 longLabel）；示意图中的「2 分钟」仅作设计说明。
