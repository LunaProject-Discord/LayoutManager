package jp.lunaproject.layoutmanager.engine

import com.intellij.openapi.project.Project
import jp.lunaproject.layoutmanager.settings.EngineMode
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings
import org.jdom.Element

/**
 * Captures and applies the tool window layout of a project frame.
 *
 * The captured layout is an opaque XML element that is persisted as-is, so an implementation
 * can be swapped without touching the storage or the actions.
 */
interface LayoutEngine {
    /** Returns a detached snapshot of the current tool window layout. Must be called on EDT. */
    fun capture(project: Project): Element

    /** Applies a snapshot previously returned by [capture] of any engine. Must be called on EDT. */
    fun apply(project: Project, layout: Element)

    companion object {
        /** The engine selected in settings, or the public API one when the precise engine cannot run. */
        val current: LayoutEngine
            get() = when (LayoutManagerSettings.getInstance().engineMode) {
                EngineMode.PRECISE -> if (DesktopLayoutEngine.isAvailable) DesktopLayoutEngine else PublicApiLayoutEngine
                EngineMode.PUBLIC_API -> PublicApiLayoutEngine
            }

        /** The engine to apply [layout] with: public API snapshots can only be applied through public API. */
        fun forLayout(layout: Element): LayoutEngine =
            if (PublicApiLayoutEngine.isOwnFormat(layout)) PublicApiLayoutEngine else current
    }
}
