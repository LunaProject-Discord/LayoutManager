package jp.lunaproject.layoutmanager.selftest

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The four tool windows the scenario moves around: a and b live on the side stripes, c and d at the
 * bottom. Fixed candidates are used because the current anchors may be whatever the last run left.
 */
internal class TestWindows(val ids: List<String>) {
    operator fun component1() = ids[0]
    operator fun component2() = ids[1]
    operator fun component3() = ids[2]
    operator fun component4() = ids[3]

    companion object {
        private val SIDE = listOf("Assembly Explorer", "Project", "Structure", "Commit", "Bookmarks")
        private val BOTTOM = listOf("Build", "NuGet", "TODO", "Terminal", "Problems View")

        /**
         * Waits until Rider has registered its tool windows (the backend adds some late) and the set
         * stays unchanged for a few seconds, then picks the test windows.
         */
        suspend fun await(project: Project): TestWindows {
            val start = TimeSource.Monotonic.markNow()
            var lastIds = emptySet<String>()
            var stableSince = TimeSource.Monotonic.markNow()
            while (true) {
                val (ids, picked) = withContext(Dispatchers.EDT) {
                    val manager = ToolWindowManagerEx.getInstanceEx(project)
                    fun pick(candidates: List<String>) = candidates.filter { manager.getToolWindow(it)?.isAvailable == true }
                    manager.toolWindowIdSet.toSet() to (pick(SIDE).take(2) + pick(BOTTOM).take(2))
                }
                if (ids != lastIds) {
                    lastIds = ids
                    stableSince = TimeSource.Monotonic.markNow()
                }
                if (picked.size == 4 && stableSince.elapsedNow() > 5.seconds) return TestWindows(picked)
                check(start.elapsedNow() < 3.minutes) { "tool windows not ready: $ids" }
                delay(500.milliseconds)
            }
        }
    }
}

/** S1: a left, b right, c docked bottom, d floating. */
internal suspend fun setUpState1(project: Project, windows: TestWindows) {
    edt(project, windows) { a, b, c, d ->
        hideAll(project)
        a.setAnchor(ToolWindowAnchor.LEFT, null); a.setType(ToolWindowType.DOCKED, null); a.show()
        b.setAnchor(ToolWindowAnchor.RIGHT, null); b.setType(ToolWindowType.DOCKED, null); b.show()
        c.setAnchor(ToolWindowAnchor.BOTTOM, null); c.setType(ToolWindowType.DOCKED, null); c.show()
        d.setType(ToolWindowType.FLOATING, null); d.show()
    }
    awaitSettled(project)
    edt(project, windows) { a, _, c, d ->
        stretchTo(a, width = 420)
        stretchTo(c, height = 260)
        floatAt(d, Rectangle(150, 150, 500, 320))
    }
    awaitSettled(project)
}

/** S2: a right, b left, c floating, d docked bottom; every size differs from S1. */
internal suspend fun setUpState2(project: Project, windows: TestWindows) {
    edt(project, windows) { a, b, c, d ->
        hideAll(project)
        a.setAnchor(ToolWindowAnchor.RIGHT, null); a.show()
        b.setAnchor(ToolWindowAnchor.LEFT, null); b.show()
        c.setType(ToolWindowType.FLOATING, null); c.show()
        d.setType(ToolWindowType.DOCKED, null); d.setAnchor(ToolWindowAnchor.BOTTOM, null); d.show()
    }
    awaitSettled(project)
    edt(project, windows) { a, b, c, d ->
        stretchTo(a, width = 300)
        stretchTo(b, width = 520)
        stretchTo(d, height = 360)
        floatAt(c, Rectangle(700, 380, 640, 360))
    }
    awaitSettled(project)
}

/**
 * S3 scrambles what public API cannot set: c and d are floated at a new position and docked again
 * (changing their remembered floating bounds), and a/b move to the end of their stripes.
 */
internal suspend fun setUpState3(project: Project, windows: TestWindows) {
    edt(project, windows) { a, b, c, d ->
        hideAll(project)
        for (w in listOf(c, d)) {
            w.setType(ToolWindowType.FLOATING, null)
            w.show()
        }
        for (w in listOf(a, b)) {
            val anchor = w.anchor
            w.setAnchor(ToolWindowAnchor.TOP, null)
            w.setAnchor(anchor, null)
        }
    }
    awaitSettled(project)
    edt(project, windows) { _, _, c, d ->
        floatAt(c, Rectangle(420, 260, 450, 300))
        floatAt(d, Rectangle(460, 300, 450, 300))
    }
    awaitSettled(project)
    edt(project, windows) { _, _, c, d ->
        for (w in listOf(c, d)) {
            w.hide()
            w.setType(ToolWindowType.DOCKED, null)
        }
    }
    awaitSettled(project)
}

private suspend fun edt(
    project: Project,
    windows: TestWindows,
    block: (ToolWindow, ToolWindow, ToolWindow, ToolWindow) -> Unit,
) = withContext(Dispatchers.EDT) {
    val manager = ToolWindowManagerEx.getInstanceEx(project)
    val (a, b, c, d) = windows.ids.map { manager.getToolWindow(it) ?: error("tool window $it not found") }
    block(a, b, c, d)
}

private fun hideAll(project: Project) {
    val manager = ToolWindowManagerEx.getInstanceEx(project)
    for (id in manager.toolWindowIdSet) manager.getToolWindow(id)?.takeIf { it.isVisible }?.hide()
}

private fun stretchTo(window: ToolWindow, width: Int? = null, height: Int? = null) {
    val ex = window as ToolWindowEx
    width?.let { ex.stretchWidth(it - window.component.width) }
    height?.let { ex.stretchHeight(it - window.component.height) }
}

private fun floatAt(window: ToolWindow, bounds: Rectangle) {
    SwingUtilities.getWindowAncestor(window.component)?.bounds = bounds
}
