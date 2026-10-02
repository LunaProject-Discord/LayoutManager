package jp.lunaproject.layoutmanager.actions

import com.intellij.ide.actions.RestoreFactoryDefaultLayoutAction
import com.intellij.ide.actions.StoreNewLayoutAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings
import jp.lunaproject.layoutmanager.settings.LayoutMenu

/**
 * Window | Layouts of the standard menu: the platform's own "Layouts" menu of other JetBrains IDEs, which Rider
 * unregisters. Its children are the platform actions, re-registered in plugin.xml; the group is only
 * shown when chosen as the layout menu in Settings | Tools | Layout Manager.
 */
class StandardLayoutsGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && LayoutManagerSettings.getInstance().layoutMenu == LayoutMenu.STANDARD
    }
}

/**
 * "Default" of the standard menu (RestoreFactoryDefaultLayout, registered by Rider and overridden by this
 * plugin): the platform action, hidden while Layout Manager's menu is chosen so that Find Action does not
 * offer it next to Layout Manager's own "Default".
 */
class StandardRestoreFactoryDefaultLayoutAction : DumbAwareToggleAction() {
    private val standard = RestoreFactoryDefaultLayoutAction()

    init {
        templatePresentation.keepPopupOnPerform = standard.templatePresentation.keepPopupOnPerform
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (isLayoutManagerMenuChosen()) e.presentation.isEnabledAndVisible = false
    }

    override fun isSelected(e: AnActionEvent) = standard.isSelected(e)

    override fun setSelected(e: AnActionEvent, state: Boolean) = standard.setSelected(e, state)
}

/** "Save Current Layout as New…" of the standard menu: the platform action, hidden like [StandardRestoreFactoryDefaultLayoutAction]. */
class StandardStoreNewLayoutAction : AnAction(), DumbAware {
    private val standard = StoreNewLayoutAction()

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        if (isLayoutManagerMenuChosen()) e.presentation.isEnabledAndVisible = false else standard.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) = standard.actionPerformed(e)
}
