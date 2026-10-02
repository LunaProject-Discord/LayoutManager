package jp.lunaproject.layoutmanager.actions

import com.intellij.ide.actions.RestoreDefaultLayoutAction
import com.intellij.ide.actions.StoreDefaultLayoutAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import jp.lunaproject.layoutmanager.LayoutManager
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings
import jp.lunaproject.layoutmanager.settings.LayoutMenu
import jp.lunaproject.layoutmanager.ui.SaveLayoutDialog

/** "Save Current Layout as New…" */
class SaveNewLayoutAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = isLayoutManagerMenuChosen()
        e.presentation.isEnabled = e.presentation.isVisible && e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = SaveLayoutDialog(project)
        if (dialog.showAndGet()) {
            LayoutManager.save(project, dialog.layoutRef)
        }
    }
}

/**
 * "Default": applies the IDE's factory default layout. Like "Default" of the standard layout menu, it is
 * checked while it is the active layout, and choosing it again re-applies it.
 */
class RestoreFactoryDefaultAction : DumbAwareToggleAction() {
    init {
        templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = isLayoutManagerMenuChosen() && LayoutManager.isFactoryDefaultAvailable
        e.presentation.isEnabled = e.presentation.isVisible && e.project != null
    }

    override fun isSelected(e: AnActionEvent): Boolean = e.project?.let(LayoutManager::isFactoryDefaultActive) == true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        LayoutManager.restoreFactoryDefault(project)
    }
}

/**
 * Registered under a platform action id ("Restore Current Layout" with Shift+F12, "Save Changes in
 * Current Layout"), so the shortcut follows the layout menu chosen in settings: the active layout of
 * Layout Manager, or the platform action of the standard menu.
 */
abstract class CurrentLayoutRouterAction(private val standard: AnAction) : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        if (isLayoutManagerMenuChosen()) {
            e.presentation.isEnabledAndVisible = e.project?.let(::hasCurrentLayout) == true
        } else {
            standard.update(e)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (isLayoutManagerMenuChosen()) {
            val project = e.project ?: return
            perform(project)
        } else {
            standard.actionPerformed(e)
        }
    }

    protected open fun hasCurrentLayout(project: Project): Boolean = LayoutManager.activeLayout(project) != null

    protected abstract fun perform(project: Project)
}

/** "Restore Current Layout": re-applies the active layout (or "Default"), discarding changes made since. */
class RestoreCurrentLayoutAction : CurrentLayoutRouterAction(RestoreDefaultLayoutAction()) {
    override fun hasCurrentLayout(project: Project) =
        super.hasCurrentLayout(project) || LayoutManager.isFactoryDefaultActive(project)

    override fun perform(project: Project) {
        val active = LayoutManager.activeLayout(project)
        if (active != null) LayoutManager.apply(project, active) else LayoutManager.restoreFactoryDefault(project)
    }
}

/** "Save Changes in Current Layout": overwrites the active layout with the current one. */
class SaveCurrentLayoutAction : CurrentLayoutRouterAction(StoreDefaultLayoutAction()) {
    override fun perform(project: Project) {
        LayoutManager.activeLayout(project)?.let { LayoutManager.save(project, it) }
    }
}

/** Layout Manager's own actions are hidden while the standard layout menu is chosen. */
internal fun isLayoutManagerMenuChosen() = LayoutManagerSettings.getInstance().layoutMenu == LayoutMenu.LAYOUT_MANAGER
