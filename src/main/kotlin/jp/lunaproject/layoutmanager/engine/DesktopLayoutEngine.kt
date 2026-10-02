package jp.lunaproject.layoutmanager.engine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jdom.Element

/**
 * [LayoutEngine] backed by the platform's [DesktopLayout], the same mechanism the
 * "Window | Layouts" feature of other JetBrains IDEs uses. Restores order, weights, split and
 * floating bounds exactly, but relies on internal API.
 */
internal object DesktopLayoutEngine : LayoutEngine {
    private val LOG = logger<DesktopLayoutEngine>()

    /** Whether the internal API this engine needs exists and behaves as expected in this IDE build. */
    val isAvailable: Boolean by lazy {
        try {
            val element = DesktopLayout().writeExternal(DesktopLayout.TAG) ?: Element(DesktopLayout.TAG)
            DesktopLayout().readExternal(element)
            ToolWindowManagerEx::class.java.getMethod("getLayout")
            ToolWindowManagerEx::class.java.getMethod("setLayout", DesktopLayout::class.java)
            true
        } catch (e: LinkageError) {
            LOG.warn("Precise layout engine is unavailable, falling back to public API", e)
            false
        } catch (e: ReflectiveOperationException) {
            LOG.warn("Precise layout engine is unavailable, falling back to public API", e)
            false
        }
    }

    /**
     * The IDE's factory default layout, as used by "Default" in the standard layout menu. Read without
     * touching the standard menu's own state. `null` when the internal API is unavailable.
     */
    fun factoryDefault(): Element? {
        if (!isAvailable) return null
        return try {
            ToolWindowDefaultLayoutManager.getInstance().getFactoryDefaultLayoutCopy().writeExternal(DesktopLayout.TAG)
        } catch (e: LinkageError) {
            LOG.warn("Factory default layout is unavailable", e)
            null
        }
    }

    override fun capture(project: Project): Element =
        ToolWindowManagerEx.getInstanceEx(project).getLayout().writeExternal(DesktopLayout.TAG)
            ?: Element(DesktopLayout.TAG)

    override fun apply(project: Project, layout: Element) {
        val desktopLayout = DesktopLayout()
        desktopLayout.readExternal(layout.clone())
        val manager = ToolWindowManagerEx.getInstanceEx(project)

        // readExternal drops the visibility of windows that must not open on IDE startup (e.g. Build),
        // which is right for restoring a frame but not for applying a layout the user saved.
        // Take visibility from the XML and show those windows once the layout is applied.
        val shouldBeVisible = layout.getChildren(WINDOW_INFO)
            .filter { it.getAttributeValue("visible") == "true" }
            .mapNotNull { it.getAttributeValue("id") }

        manager.setLayout(desktopLayout)
        ApplicationManager.getApplication().invokeLater({
            for (id in shouldBeVisible) {
                val window = manager.getToolWindow(id) ?: continue
                if (!window.isVisible && window.isAvailable) window.show()
            }
        }, project.disposed)
    }

    const val WINDOW_INFO = "window_info"
}
