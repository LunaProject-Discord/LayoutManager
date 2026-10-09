package jp.lunaproject.layoutmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import jp.lunaproject.layoutmanager.LayoutManager
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.model.LayoutRef
import jp.lunaproject.layoutmanager.model.LayoutScope
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings
import jp.lunaproject.layoutmanager.settings.LayoutMenu

/**
 * Window | Layouts, laid out like the standard layout menu of other JetBrains IDEs: "Default", the saved
 * layouts (one section per scope, each layout a submenu), then "Save Current Layout as New…" and, while
 * Rider's Layout Settings is integrated, its layout scope switches.
 */
class LayoutsActionGroup : ActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && isLayoutManagerMenuChosen()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val actionManager = ActionManager.getInstance()
        val children = mutableListOf<AnAction>()
        actionManager.getAction("LayoutManager.RestoreFactoryDefault")?.let { children += it }

        val project = e?.project
        if (project != null) addSavedLayouts(project, children)

        children += Separator.getInstance()
        actionManager.getAction("LayoutManager.SaveNewLayout")?.let { children += it }
        actionManager.getAction("LayoutManager.RiderLayoutScope")?.let { children += it }
        return children.toTypedArray()
    }

    /** Each scope is a section whose header is its separator; an empty scope shows a disabled placeholder. */
    private fun addSavedLayouts(project: Project, children: MutableList<AnAction>) {
        val active = LayoutManager.activeLayout(project)
        for (scope in LayoutScope.entries) {
            val names = LayoutManager.names(project, scope)
            val header = when (scope) {
                LayoutScope.GLOBAL -> LayoutManagerBundle.message("group.global")
                LayoutScope.SOLUTION -> LayoutManagerBundle.message("group.solution", project.name)
            }
            children += Separator.create(header)
            if (names.isEmpty()) children += NoLayoutsPlaceholder()
            names.mapTo(children) { name ->
                val ref = LayoutRef(scope, name)
                LayoutItemGroup(ref, isActive = ref == active)
            }
        }
    }
}

/** "(None)" under the header of a scope without saved layouts. */
private class NoLayoutsPlaceholder : DumbAwareAction(LayoutManagerBundle.message("group.empty")) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = false
    }

    override fun actionPerformed(e: AnActionEvent) = Unit
}

/**
 * Submenu for one saved layout, as in the standard menu: the active layout offers Restore / Save Changes /
 * Rename / --- / Delete (disabled), the others Apply / Rename / --- / Delete.
 */
private class LayoutItemGroup(private val ref: LayoutRef, isActive: Boolean) : ActionGroup(), DumbAware {
    private val children: Array<AnAction> = if (isActive) {
        arrayOf(RestoreLayoutAction(ref), SaveLayoutChangesAction(ref), RenameLayoutAction(ref), Separator.getInstance(), DeleteLayoutAction(ref))
    } else {
        arrayOf(ApplyLayoutAction(ref), RenameLayoutAction(ref), Separator.getInstance(), DeleteLayoutAction(ref))
    }

    init {
        isPopup = true
        templatePresentation.setText(ref.name, false)
        if (isActive) templatePresentation.icon = AllIcons.Actions.Checked
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = children
}
