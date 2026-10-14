package com.shub39.grit

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shub39.grit.rhine.RhinePreviewActivity
import org.junit.Assert.*
import org.junit.Test

/** Real two-pointer events: a second finger changes mode while the first owns a drag. */
class RhineAdjustGestureTest {
    @Test
    fun modeSwitchCancelsHeldDragWithoutSubmitting() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun text(node: AccessibilityNodeInfo?): String {
            if (node == null) return ""
            return buildString {
                append(node.text ?: "")
                append(' ')
                for (i in 0 until node.childCount) append(text(node.getChild(i)))
            }
        }
        fun awaitText(value: String) {
            val deadline = SystemClock.uptimeMillis() + 8000
            while (SystemClock.uptimeMillis() < deadline) {
                if (value in text(automation.rootInActiveWindow)) return
                SystemClock.sleep(100)
            }
            fail("Missing: $value; actual: ${text(automation.rootInActiveWindow)}")
        }
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), RhinePreviewActivity::class.java)
                .putExtra("dataset", "adjust")
                .putExtra("adjust", true)
                .putExtra("u", 8f)
                .putExtra("v", 0f)
        ActivityScenario.launch<RhinePreviewActivity>(intent).use { scenario ->
            awaitText("ADJUST")
            SystemClock.sleep(500)
            fun find(node: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.contentDescription?.toString() == description) return node
                for (i in 0 until node.childCount) {
                    find(node.getChild(i), description)?.let {
                        return it
                    }
                }
                return null
            }
            val modeBounds = android.graphics.Rect()
            val modeNode = find(automation.rootInActiveWindow, "切换预览/详情模式")
            assertNotNull("Mode control must be accessible", modeNode)
            modeNode!!.getBoundsInScreen(modeBounds)
            assertFalse(modeBounds.isEmpty)
            val modePoint = modeBounds.exactCenterX() to modeBounds.exactCenterY()
            val downTime = SystemClock.uptimeMillis()
            fun event(action: Int, points: List<Pair<Float, Float>>) {
                val properties =
                    points.indices
                        .map { index ->
                            MotionEvent.PointerProperties().apply {
                                id = index
                                toolType = MotionEvent.TOOL_TYPE_FINGER
                            }
                        }
                        .toTypedArray()
                val coords =
                    points
                        .map { (px, py) ->
                            MotionEvent.PointerCoords().apply {
                                x = px
                                y = py
                                pressure = 1f
                                size = 1f
                            }
                        }
                        .toTypedArray()
                val e =
                    MotionEvent.obtain(
                        downTime,
                        SystemClock.uptimeMillis(),
                        action,
                        points.size,
                        properties,
                        coords,
                        0,
                        0,
                        1f,
                        1f,
                        0,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0,
                    )
                assertTrue(automation.injectInputEvent(e, true))
                e.recycle()
            }
            event(MotionEvent.ACTION_DOWN, listOf(816f to 360f))
            SystemClock.sleep(800)
            event(MotionEvent.ACTION_MOVE, listOf(1330f to 511f))
            awaitText("8 → 2")
            event(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(1330f to 511f, modePoint),
            )
            SystemClock.sleep(100)
            event(
                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(1330f to 511f, modePoint),
            )
            event(MotionEvent.ACTION_UP, listOf(1330f to 511f))
            awaitText("PREVIEW")
            awaitText("08   Task 8")
            scenario.onActivity { activity ->
                for (name in listOf("moveAttempts", "moveCommits")) {
                    val field = RhinePreviewActivity::class.java.getDeclaredField(name)
                    field.isAccessible = true
                    assertEquals(name, 0, field.getInt(activity))
                }
            }
        }
    }
}
