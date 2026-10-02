package jp.lunaproject.layoutmanager

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import jp.lunaproject.layoutmanager.engine.DesktopLayoutEngine
import jp.lunaproject.layoutmanager.engine.LayoutEngine
import jp.lunaproject.layoutmanager.model.LayoutRef
import jp.lunaproject.layoutmanager.model.LayoutScope
import jp.lunaproject.layoutmanager.storage.GlobalLayoutStore
import jp.lunaproject.layoutmanager.storage.LayoutStore
import jp.lunaproject.layoutmanager.storage.SolutionLayoutStore

/** Operations on saved layouts. Methods that touch the UI must be called on EDT. */
object LayoutManager {
    fun store(project: Project, scope: LayoutScope): LayoutStore = when (scope) {
        LayoutScope.GLOBAL -> GlobalLayoutStore.getInstance()
        LayoutScope.SOLUTION -> SolutionLayoutStore.getInstance(project)
    }

    fun names(project: Project, scope: LayoutScope): List<String> = store(project, scope).names()

    fun exists(project: Project, ref: LayoutRef): Boolean = store(project, ref.scope).contains(ref.name)

    /** The active layout of [project], or `null` if none is active or it no longer exists. */
    fun activeLayout(project: Project): LayoutRef? =
        SolutionLayoutStore.getInstance(project).activeLayout?.takeIf { exists(project, it) }

    /** Saves the current layout of [project] under [ref] (overwriting it) and makes it active. */
    fun save(project: Project, ref: LayoutRef) {
        store(project, ref.scope).put(ref.name, LayoutEngine.current.capture(project))
        SolutionLayoutStore.getInstance(project).activeLayout = ref
    }

    /** Applies [ref] to [project] and makes it active. Returns `false` if it does not exist. */
    fun apply(project: Project, ref: LayoutRef): Boolean {
        val layout = store(project, ref.scope).get(ref.name) ?: return false
        LayoutEngine.forLayout(layout).apply(project, layout)
        SolutionLayoutStore.getInstance(project).activeLayout = ref
        return true
    }

    fun rename(project: Project, ref: LayoutRef, newName: String): Boolean {
        if (!store(project, ref.scope).rename(ref.name, newName)) return false
        val renamed = LayoutRef(ref.scope, newName)
        for (affected in affectedProjects(project, ref.scope)) {
            val solutionStore = SolutionLayoutStore.getInstance(affected)
            if (solutionStore.activeLayout == ref) solutionStore.activeLayout = renamed
        }
        return true
    }

    fun delete(project: Project, ref: LayoutRef): Boolean {
        if (!store(project, ref.scope).remove(ref.name)) return false
        for (affected in affectedProjects(project, ref.scope)) {
            val solutionStore = SolutionLayoutStore.getInstance(affected)
            if (solutionStore.activeLayout == ref) solutionStore.activeLayout = null
        }
        return true
    }

    /** Whether [restoreFactoryDefault] can run in this IDE. */
    val isFactoryDefaultAvailable: Boolean get() = DesktopLayoutEngine.isAvailable

    /**
     * Applies the IDE's factory default layout to [project]. Like the standard menu, "Default" then
     * counts as the active layout: no saved layout is active and [isFactoryDefaultActive] is set.
     */
    fun restoreFactoryDefault(project: Project): Boolean {
        val layout = DesktopLayoutEngine.factoryDefault() ?: return false
        LayoutEngine.forLayout(layout).apply(project, layout)
        val store = SolutionLayoutStore.getInstance(project)
        store.activeLayout = null
        store.factoryDefaultActive = true
        return true
    }

    fun isFactoryDefaultActive(project: Project): Boolean = SolutionLayoutStore.getInstance(project).factoryDefaultActive

    /** Remembers the current layout of [project] so that [restoreLastSession] can bring it back on next open. */
    fun rememberLastSession(project: Project) {
        SolutionLayoutStore.getInstance(project).lastSession = LayoutEngine.current.capture(project)
    }

    /** Applies the layout remembered when [project] was last closed. Does not change the active layout. */
    fun restoreLastSession(project: Project): Boolean {
        val layout = SolutionLayoutStore.getInstance(project).lastSession ?: return false
        LayoutEngine.forLayout(layout).apply(project, layout)
        return true
    }

    /** Open projects whose active layout may refer to a layout of [scope]. */
    private fun affectedProjects(project: Project, scope: LayoutScope): List<Project> = when (scope) {
        LayoutScope.GLOBAL -> ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        LayoutScope.SOLUTION -> listOf(project)
    }
}
