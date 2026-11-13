# android-exit-and-launcher-label-local-zh（上收稿）

> 用途：可直接整段放进 `SKILL.md` 的正文草稿；登记与分发由中央管家 / Skill 管家执行。
> 来源：069-ing-碎片时间播放器 2026-11-11 的 0.2.0–0.2.4 真机验收实测。

## 触发词

- 安卓退出与桌面名检查〔建议〕
- 用户说「点返回不要直接退」「结束别问那么多」「桌面名怎么是英文」时也必须命中〔建议〕

## 这个 skill 解决什么

安卓应用最容易在三个地方违背用户预期，而且都发生在「功能全对」之后：按一次返回直接退出、结束一个计时要点三四次、应用内切成中文但桌面图标还是英文。三处都属用户可见的体感问题，靠自测很难发现，必须做成验收前的机械检查。

## 三个问题：现象、根因、本项目证据

### 1. 按一次返回直接退到桌面

- 现象：首页按一次系统返回键，Activity 直接 finish，应用消失，无任何提示。用户会以为进度丢了。
- 根因：Compose 根部没有 `BackHandler`，返回事件落到 Activity 默认行为（finish）。
- 本项目证据：0.2.0 包在 Redmi Note 12T Pro 上复现，`adb shell input keyevent 4` 后 `dumpsys activity activities` 的 `topResumedActivity` 立即变为 `com.miui.home/.launcher.Launcher`。
- 修复：根部加 `BackHandler` + 2 秒窗口；首次返回 Toast「再按一次退出」并留在应用，窗口内二次返回才 `finish()`。

### 2. 结束计时要点多次

- 现象：点「结束」先弹「提前结束这一轮？」确认，确认后又停在「完成/结果页」，用户还要再点一次才回首页；一次操作跨三屏。
- 根因：把「防误触确认」「结果展示」「回到首页」拆成三个连续页面状态，每个都要一次交互。
- 本项目证据：0.2.0 之前 `TimerScreen` 的结束链路是「确认弹窗 → FINISHED 结果页 → 手动返回」；0.2.0 改为点结束即停播结算，只弹一个「记入历史？记入/不记入」，两种选择都直接回 IDLE。
- 正确口径：结束/删除类操作只允许一次页内二次确认，确认后立即执行并回到主界面；不叠加结果页停留。

### 3. 应用内切了中文，桌面图标还是英文

- 现象：App 内语言选「简体中文」，界面已中文，但桌面/应用列表显示 `Fragment Time`。
- 根因：应用内语言（AppCompatDelegate / per-app locales）只影响应用自身资源；桌面由系统按**系统语言**读取 `android:label`。两者作用域不同。
- 本项目证据：0.2.2 在系统语言为英文的 OPPO PLG110 上，界面切中文成功，应用列表仍显示英文名；`aapt2 dump badging` 显示 `application-label-en:'Fragment Time'`。
- 修复（本项目选择）：新增 `app_label`＝「碎片时间」只放默认 `values`，Manifest `android:label` 指向它；App 内标题与通知仍用 `app_name` 的中英资源。0.2.4 后 `aapt2` 全语言标签均为「碎片时间」，OPPO 应用列表实测显示中文。

## 正确行为口径

### 返回键

- 非首页：优先回上一级（tab/页面），不得直接退出应用。
- 首页：第一次返回只提示「再按一次退出」，不退出；2 秒内第二次返回才退出；超过 2 秒重新计时。
- 弹窗、二次确认、系统选择器打开时，返回交给它们自己处理，根部拦截不得抢占。
- 判定阈值建议固定 2_000 ms，并在单测里覆盖「恰好 2 秒」「超时重计时」边界。

### 结束 / 删除类操作

- 只做一次页内二次确认（页内对话框，不用 `window.confirm` / `window.alert`；Android WebView 壳内系统 confirm 会被吞或首次返回 false）。
- 确认后立即执行并回主界面，不增加「结果页」停留；需要展示结果时用一次性提示或列表内状态。
- 破坏性操作（删除课程、清空数据）同样单次确认，且确认文案必须写清影响对象与数量。

### 桌面名（两种交付形态分开处理）

- **原生 APK**：桌面名取 `android:label`，默认按系统语言。产品要求固定中文名时，单独定义 `app_label`（仅默认 values），Manifest 指向它；不要求固定时保持本地化 `app_name`。**原生 App 不加 `.pwa` 后缀。**
- **PWA**：桌面名按生效语言取对应 `manifest`，`name`（安装对话框）与 `short_name`（桌面图标）必须同语言、同后缀；中文用「·pwa」，英文用 `.pwa` 半角后缀；应用内标题（页面 `<title>`、页头、关于页）不带后缀。
- 通用：应用内语言切换不得承诺改变桌面名；若要改，必须走系统级 per-app locale 并在真机应用列表回读确认。

## 检查清单（机械可核对）

| # | 检查 | 通过判据 | 不合格示例 |
|---|---|---|---|
| 1 | 首页首次返回 | `dumpsys` 前台仍是本应用，屏幕出现「再按一次退出」 | 直接回桌面 |
| 2 | 2 秒内二次返回 | 前台变为 launcher 或上一应用 | 仍需第三次 |
| 3 | 超 2 秒后再按 | 重新按「第一次」处理，仍不退出 | 直接退出 |
| 4 | 弹窗内返回 | 只关闭弹窗，应用不退出 | 连应用一起退出 |
| 5 | 结束计时 | 只出现 1 次确认（记入/不记入），选完直接回主界面 | 出现二次确认或多一层结果页 |
| 6 | 结束的两分支 | 记入＝历史+1；不记入＝历史不变且断点回滚 | 不记入仍写了历史 |
| 7 | 桌面名 | 应用列表显示名与产品要求一致 | 应用内中文、桌面英文 |
| 8 | label 回读 | `aapt2 dump badging` 中目标语言的 application-label 等于要求值 | 只改了 values 却指向 app_name |
| 9 | PWA 命名（若适用） | 桌面名与当前语言一致且带「·pwa」/`.pwa`；应用内标题不带后缀 | 英文版桌面出现中文名 |

## 069 实际命令与片段（脱敏）

```bash
# 返回键两连测（任意一台已连接设备，serial 用 adb devices 结果）
adb -s <serial> shell input keyevent 4
adb -s <serial> shell dumpsys activity activities | grep -m1 mResumedActivity
adb -s <serial> shell input keyevent 4
adb -s <serial> shell dumpsys activity activities | grep -m1 mResumedActivity

# 桌面名全语言回读（本机 build-tools 路径按实际取）
aapt2 dump badging app-debug.apk | grep application-label

# 结束流程：历史与断点核对（应用需为 debuggable）
adb -s <serial> exec-out run-as <applicationId> cat databases/<db>-wal > /tmp/db-wal
adb -s <serial> exec-out run-as <applicationId> cat databases/<db> > /tmp/db
sqlite3 /tmp/db "select count(*) from timer_sessions; select positionMs from checkpoints;"
```

```kotlin
// 返回双击退出（Compose，2 秒窗口，可单测）
class ExitBackGuard(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val onFirstBack: () -> Unit,
    private val onExit: () -> Unit
) {
    private var lastBackAt: Long? = null
    fun onBackPressed() {
        val now = elapsedRealtime()
        val prev = lastBackAt
        if (prev != null && now - prev <= 2_000L) { lastBackAt = null; onExit() }
        else { lastBackAt = now; onFirstBack() }
    }
}
```

```kotlin
// 结束只做一次选择，两分支都回 IDLE（ViewModel 层）
fun commitPendingFinish(saveHistory: Boolean) {
    if (saveHistory) { persistSession() } else { rollbackCheckpointToSessionStart() }
    resetToIdle()
}
```

## 待管家决定

1. 桌面名一节的 PWA 命名规则是否原样保留（本稿已按「中文「·pwa」/ 英文 `.pwa`」写入；原生 APK 明确不加后缀，避免混淆）。
2. 是否把「返回键 + 结束流程 + 桌面名」三项固化为 mechanism-two 强制门禁，配固定回执：
   `【交互门禁回执】返回键 / 结束流程 / 桌面名 / 结论`，缺失不算完成。
3. 是否需要在 description 首行写明触发场景，并在目标项目的 AGENTS.md 点名该门禁。
