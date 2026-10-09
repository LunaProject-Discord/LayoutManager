package jp.lunaproject.layoutmanager.engine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import org.jdom.Element

/**
 * [LayoutEngine] built only on public tool window API.
 *
 * Restores visibility, anchor, split, type, auto-hide, stripe button, content UI and the docked
 * size. The order of buttons within a stripe and floating window bounds cannot be restored.
 */
internal object PublicApiLayoutEngine : LayoutEngine {
    const val TAG = "publicLayout"
    private const val WINDOW = "window"

    /** A tool window of a [PreciseLayoutEngine] snapshot. */
    private const val DESKTOP_WINDOW_INFO = "window_info"

    override fun capture(project: Project): Element {
        val manager = ToolWindowManager.getInstance(project)
        val root = Element(TAG)
        for (id in manager.toolWindowIdSet.sorted()) {
            val window = manager.getToolWindow(id) ?: continue
            val size = window.component.size
            root.addContent(
                Element(WINDOW)
                    .setAttribute("id", id)
                    .setAttribute("anchor", window.anchor.toString())
                    .setAttribute("split", window.isSplitMode.toString())
                    .setAttribute("type", window.type.name)
                    .setAttribute("autoHide", window.isAutoHide.toString())
                    .setAttribute("showStripeButton", window.isShowStripeButton.toString())
                    .setAttribute("contentUiType", window.contentUiType.name)
                    .setAttribute("visible", window.isVisible.toString())
                    .setAttribute("width", size.width.toString())
                    .setAttribute("height", size.height.toString())
            )
        }
        return root
    }

    fun isOwnFormat(layout: Element): Boolean = layout.name == TAG

    override fun apply(project: Project, layout: Element) {
        val manager = ToolWindowManager.getInstance(project)
        val own = if (isOwnFormat(layout)) layout else fromDesktopFormat(project, layout)
        val saved = own.getChildren(WINDOW).associateBy { it.getAttributeValue("id") }

        // Hide first so that showing a window does not fight with one that is about to be hidden.
        for (id in manager.toolWindowIdSet) {
            val window = manager.getToolWindow(id) ?: continue
            val info = saved[id]
            if (window.isVisible && (info == null || !info.getBool("visible"))) window.hide()
        }

        val toResize = mutableListOf<Pair<ToolWindow, Element>>()
        for ((id, info) in saved) {
            val window = manager.getToolWindow(id) ?: continue
            info.getAnchor()?.let { if (window.anchor != it) window.setAnchor(it, null) }
            info.getBoolOrNull("split")?.let { if (window.isSplitMode != it) window.setSplitMode(it, null) }
            info.getEnum<ToolWindowType>("type")?.let { if (window.type != it) window.setType(it, null) }
            info.getBoolOrNull("autoHide")?.let { if (window.isAutoHide != it) window.setAutoHide(it) }
            info.getBoolOrNull("showStripeButton")?.let { if (window.isShowStripeButton != it) window.setShowStripeButton(it) }
            info.getAttributeValue("contentUiType")?.let(ToolWindowContentUiType::getInstance)?.let {
                if (window.contentUiType != it) window.setContentUiType(it, null)
            }
            if (info.getBool("visible") && window.isAvailable) {
                window.show()
                toResize += window to info
            }
        }

        // Sizes are only known once the windows have been laid out.
        ApplicationManager.getApplication().invokeLater({
            for ((window, info) in toResize) resize(window, info)
        }, project.disposed)
    }

    /**
     * Converts a snapshot of a [PreciseLayoutEngine] (the platform's `window_info` XML) to this engine's
     * format, so layouts saved with the precise engine still apply when only public API is usable.
     * Docked sizes are derived from the stored weights and the current frame size.
     */
    private fun fromDesktopFormat(project: Project, layout: Element): Element {
        val frame = WindowManager.getInstance().getFrame(project)?.rootPane?.size
        val root = Element(TAG)
        for (info in layout.getChildren(DESKTOP_WINDOW_INFO)) {
            val id = info.getAttributeValue("id") ?: continue
            val weight = info.getAttributeValue("weight")?.toFloatOrNull()
            val window = Element(WINDOW)
                .setAttribute("id", id)
                // WindowInfoImpl omits attributes that have their default value.
                .setAttribute("anchor", info.getAttributeValue("anchor") ?: ToolWindowAnchor.LEFT.toString())
                .setAttribute("split", info.getAttributeValue("side_tool") ?: "false")
                .setAttribute("type", info.getAttributeValue("type") ?: ToolWindowType.DOCKED.name)
                .setAttribute("autoHide", info.getAttributeValue("auto_hide") ?: "false")
                .setAttribute("showStripeButton", info.getAttributeValue("show_stripe_button") ?: "true")
                .setAttribute("visible", info.getAttributeValue("visible") ?: "false")
            info.getAttributeValue("content_ui")?.let { window.setAttribute("contentUiType", it) }
            if (frame != null && weight != null) {
                window.setAttribute("width", (frame.width * weight).toInt().toString())
                window.setAttribute("height", (frame.height * weight).toInt().toString())
            }
            root.addContent(window)
        }
        return root
    }

    private fun resize(window: ToolWindow, info: Element) {
        if (window.isDisposed || !window.isVisible) return
        if (window.type != ToolWindowType.DOCKED && window.type != ToolWindowType.SLIDING) return
        val ex = window as? ToolWindowEx ?: return
        val current = window.component.size
        when (window.anchor) {
            ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT ->
                info.getInt("width")?.let { ex.stretchWidth(it - current.width) }
            ToolWindowAnchor.TOP, ToolWindowAnchor.BOTTOM ->
                info.getInt("height")?.let { ex.stretchHeight(it - current.height) }
        }
    }

    private fun Element.getBoolOrNull(name: String): Boolean? = getAttributeValue(name)?.toBooleanStrictOrNull()

    private fun Element.getBool(name: String): Boolean = getBoolOrNull(name) == true

    private fun Element.getInt(name: String): Int? = getAttributeValue(name)?.toIntOrNull()

    private fun Element.getAnchor(): ToolWindowAnchor? =
        getAttributeValue("anchor")?.let { runCatching { ToolWindowAnchor.fromText(it) }.getOrNull() }

    private inline fun <reified T : Enum<T>> Element.getEnum(name: String): T? =
        getAttributeValue(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
}
