package jp.lunaproject.layoutmanager.engine

import com.intellij.openapi.extensions.ExtensionPointName
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
        /** The engine selected in settings, or the public API one when no precise engine can run. */
        val current: LayoutEngine
            get() = when (LayoutManagerSettings.getInstance().engineMode) {
                EngineMode.PRECISE -> PreciseLayoutEngine.available ?: PublicApiLayoutEngine
                EngineMode.PUBLIC_API -> PublicApiLayoutEngine
            }

        /** The engine to apply [layout] with: public API snapshots can only be applied through public API. */
        fun forLayout(layout: Element): LayoutEngine =
            if (PublicApiLayoutEngine.isOwnFormat(layout)) PublicApiLayoutEngine else current
    }
}

/**
 * A [LayoutEngine] that restores layouts exactly through the platform's own layout model. That model is
 * internal API, so the engine is not part of this plugin: Layout Manager Advanced, distributed outside
 * JetBrains Marketplace, contributes it. Its snapshots are the platform's `window_info` XML, which
 * [PublicApiLayoutEngine] can still apply when the precise engine is gone.
 */
interface PreciseLayoutEngine : LayoutEngine {
    /** Whether the internal API the engine needs exists and behaves as expected in this IDE build. */
    val isAvailable: Boolean

    /** The IDE's factory default layout, or `null` when it cannot be read. */
    fun factoryDefault(): Element?

    companion object {
        val EP_NAME = ExtensionPointName<PreciseLayoutEngine>("jp.lunaproject.layoutmanager.preciseEngine")

        /** Whether an engine is installed, even if it cannot run in this IDE build. */
        val isInstalled: Boolean get() = EP_NAME.extensionList.isNotEmpty()

        /** The installed engine that can run in this IDE build, if any. */
        val available: PreciseLayoutEngine? get() = EP_NAME.extensionList.firstOrNull { it.isAvailable }
    }
}
