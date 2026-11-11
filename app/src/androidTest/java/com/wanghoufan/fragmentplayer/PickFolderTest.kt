package com.wanghoufan.fragmentplayer

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickFolderTest {

    private val tag = "PickFolderTest"

    @Test
    fun pickFragmentLearningFolder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.setServiceInfo(
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        )
        val device = UiDevice.getInstance(instrumentation)

        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(1500)
            logWindows(automation, "after launch")

            assertTrue("课程 tab", device.wait(Until.hasObject(By.text("课程")), 8000))
            device.findObject(By.text("课程")).click()
            Thread.sleep(1000)
            logWindows(automation, "after courses tab")

            assertTrue("选择课程文件夹", device.wait(Until.hasObject(By.text("选择课程文件夹")), 8000))
            device.findObject(By.text("选择课程文件夹")).click()
            Thread.sleep(2500)
            logWindows(automation, "after picker opened")

            assertTrue("Movies", clickExact(automation, "Movies", 10_000))
            Thread.sleep(1500)
            logWindows(automation, "after Movies")
            assertTrue("FragmentLearning", clickExact(automation, "FragmentLearning", 10_000))
            Thread.sleep(1500)
            logWindows(automation, "after FragmentLearning")
            assertTrue("使用此文件夹", clickExact(automation, "使用此文件夹", 10_000))
            Thread.sleep(2500)
            if (clickExact(automation, "允许", 1500)) {
                Thread.sleep(1500)
            }

            assertTrue("导入完成应显示 共 8 节", device.wait(Until.hasObject(By.text("共 7 节")), 30_000))
        }
    }

    private fun logWindows(automation: UiAutomation, stage: String) {
        val windows = automation.windows
        Log.i(tag, "stage=$stage windows=${windows.size}")
        windows.forEach { window ->
            val root = runCatching { window.root }.getOrNull()
            Log.i(tag, "  window=${window.title} pkg=${root?.packageName} class=${root?.className}")
        }
        val active = runCatching { automation.rootInActiveWindow }.getOrNull()
        Log.i(tag, "  active pkg=${active?.packageName} class=${active?.className}")
    }

    private fun roots(automation: UiAutomation): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        runCatching { automation.rootInActiveWindow }.getOrNull()?.let { result.add(it) }
        automation.windows.forEach { window ->
            runCatching { window.root }.getOrNull()?.let { result.add(it) }
        }
        return result
    }

    private fun clickExact(automation: UiAutomation, text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            for (root in roots(automation)) {
                val matches = root.findAccessibilityNodeInfosByText(text)
                    .filter { it.text?.toString() == text }
                for (match in matches) {
                    var node: AccessibilityNodeInfo? = match
                    while (node != null) {
                        if (node.isClickable) {
                            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                Log.i(tag, "clicked '$text' node=${node.className}")
                                return true
                            }
                        }
                        node = node.parent
                    }
                }
            }
            Thread.sleep(300)
        }
        Log.w(tag, "not clicked '$text'")
        return false
    }
}
