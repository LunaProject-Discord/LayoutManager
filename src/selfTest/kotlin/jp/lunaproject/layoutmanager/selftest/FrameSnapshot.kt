package jp.lunaproject.layoutmanager.selftest

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Observable state of every tool window: id -> attribute -> value. */
internal typealias FrameSnapshot = Map<String, Map<String, String>>

/** Captures what the user would see. Must be called on EDT. */
internal fun observe(project: Project): FrameSnapshot {
    val manager = ToolWindowManagerEx.getInstanceEx(project)
    val layout = manager.getLayout()
    // The platform renumbers orders, so record the position within each stripe instead.
    val rank = manager.toolWindowIdSet
        .mapNotNull { id -> layout.getInfo(id)?.takeIf { it.order >= 0 }?.let { id to it } }
        .groupBy { (_, info) -> info.anchor }
        .flatMap { (_, entries) -> entries.sortedBy { it.second.order }.mapIndexed { i, (id, _) -> id to i } }
        .toMap()
    return manager.toolWindowIdSet.sorted().associateWith { id ->
        val window = manager.getToolWindow(id)!!
        buildMap {
            put("anchor", window.anchor.toString())
            put("visible", window.isVisible.toString())
            put("split", window.isSplitMode.toString())
            put("type", window.type.name)
            put("order", rank[id]?.toString() ?: "null")
            if (window.isVisible) {
                if (window.type == ToolWindowType.FLOATING || window.type == ToolWindowType.WINDOWED) {
                    SwingUtilities.getWindowAncestor(window.component)?.bounds?.let {
                        put("bounds", "${it.x},${it.y},${it.width},${it.height}")
                    }
                } else {
                    put("width", window.component.width.toString())
                    put("height", window.component.height.toString())
                }
            }
        }
    }
}

/**
 * Waits until the frame stops changing (layout, deferred resizes, window bounds) and returns it.
 * Replaces fixed sleeps: returns as soon as three samples in a row are identical.
 */
internal suspend fun awaitSettled(project: Project, timeout: Duration = 10.seconds): FrameSnapshot {
    val start = TimeSource.Monotonic.markNow()
    var last = withContext(Dispatchers.EDT) { observe(project) }
    var stableSamples = 0
    while (stableSamples < 2 && start.elapsedNow() < timeout) {
        delay(250.milliseconds)
        val current = withContext(Dispatchers.EDT) { observe(project) }
        stableSamples = if (current == last) stableSamples + 1 else 0
        last = current
    }
    return last
}

/** Differences between two snapshots, as `id.attribute expected=… actual=…`. */
internal fun diff(expected: FrameSnapshot, actual: FrameSnapshot, keys: Set<String>? = null): List<Difference> {
    val diffs = mutableListOf<Difference>()
    for ((id, exp) in expected) {
        val act = actual[id] ?: continue
        for ((key, value) in exp) {
            if (keys != null && key !in keys) continue
            val got = act[key]
            if (!sameValue(key, value, got)) diffs += Difference(id, key, value, got)
        }
    }
    return diffs
}

internal data class Difference(val id: String, val key: String, val expected: String, val actual: String?) {
    override fun toString() = "$id.$key expected=$expected actual=$actual"
}

private fun sameValue(key: String, expected: String, actual: String?): Boolean {
    // Windows never shown have no order yet; the platform reports that as null or -1.
    if (key == "order" && expected in NO_ORDER && actual in NO_ORDER) return true
    if (actual == null) return false
    if (key == "width" || key == "height") {
        val e = expected.toIntOrNull() ?: return expected == actual
        val a = actual.toIntOrNull() ?: return false
        return abs(e - a) <= SIZE_TOLERANCE
    }
    return expected == actual
}

private val NO_ORDER = setOf(null, "null", "-1")
private const val SIZE_TOLERANCE = 8

internal fun FrameSnapshot.serialize(): List<String> =
    map { (id, attrs) -> id + "\t" + attrs.entries.joinToString(";") { "${it.key}=${it.value}" } }

internal fun deserializeSnapshot(lines: List<String>): FrameSnapshot =
    lines.filter { it.isNotBlank() }.associate { line ->
        val (id, attrs) = line.split('\t', limit = 2)
        id to attrs.split(';').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') }
    }
