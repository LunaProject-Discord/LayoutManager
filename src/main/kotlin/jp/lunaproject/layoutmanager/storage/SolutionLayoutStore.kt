package jp.lunaproject.layoutmanager.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import jp.lunaproject.layoutmanager.model.LayoutRef
import jp.lunaproject.layoutmanager.model.LayoutScope
import org.jdom.Element

private const val ACTIVE = "activeLayout"
private const val SCOPE = "scope"
private const val NAME = "name"
private const val LAST_SESSION = "lastSession"
private const val FACTORY_DEFAULT_ACTIVE = "factoryDefaultActive"

/**
 * Layouts specific to one solution, plus the layout currently active in it.
 * Stored in the solution's workspace file so it is not shared through VCS.
 */
@Service(Service.Level.PROJECT)
@State(name = "LayoutManagerSolutionLayouts", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SolutionLayoutStore : LayoutStore() {
    @Volatile
    var activeLayout: LayoutRef? = null
        set(value) {
            field = value
            if (value != null) factoryDefaultActive = false
        }

    /** Whether "Default" (the factory default layout) was applied last, as the standard menu marks it. */
    @Volatile
    var factoryDefaultActive: Boolean = false

    /** The layout at the time the solution was last closed. */
    @Volatile
    var lastSession: Element? = null
        get() = field?.clone()
        set(value) {
            field = value?.clone()
        }

    override fun writeExtraState(state: Element) {
        activeLayout?.let { active ->
            state.addContent(
                Element(ACTIVE)
                    .setAttribute(SCOPE, active.scope.name)
                    .setAttribute(NAME, active.name)
            )
        }
        if (factoryDefaultActive) state.addContent(Element(FACTORY_DEFAULT_ACTIVE))
        lastSession?.let { state.addContent(Element(LAST_SESSION).addContent(it)) }
    }

    override fun readExtraState(state: Element) {
        val active = state.getChild(ACTIVE)
        val scope = active?.getAttributeValue(SCOPE)?.let { runCatching { LayoutScope.valueOf(it) }.getOrNull() }
        val name = active?.getAttributeValue(NAME)
        activeLayout = if (scope != null && name != null) LayoutRef(scope, name) else null
        factoryDefaultActive = state.getChild(FACTORY_DEFAULT_ACTIVE) != null
        lastSession = state.getChild(LAST_SESSION)?.children?.firstOrNull()
    }

    companion object {
        fun getInstance(project: Project): SolutionLayoutStore = project.service()
    }
}
