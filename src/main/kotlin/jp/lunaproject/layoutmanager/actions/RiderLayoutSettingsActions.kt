package jp.lunaproject.layoutmanager.actions

import com.intellij.ide.actions.RestoreDefaultLayoutAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.settings.perApp.wm.actions.RiderStoreDefaultLayoutAction
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings

/*
 * Rider's Window | Layout Settings, integrated into the layout menu while the option is on: the menu is
 * hidden, its layout scope switches ("Use the Same Layout for All Projects" / "Use Unique Layout for Every
 * Project") are shown at the end of the chosen layout menu, and its default layout actions, which the
 * layout menus replace, are hidden from Find Action as well.
 */

internal fun isRiderLayoutSettingsIntegrated() = LayoutManagerSettings.getInstance().integrateRiderLayoutSettings

/** Window | Layout Settings (RiderWindowLayoutActions, overridden): hidden while integrated. */
class RiderLayoutSettingsGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = !isRiderLayoutSettingsIntegrated()
    }
}

/** The layout scope switches at the end of either layout menu, while integrated. */
class RiderLayoutScopeGroup : ActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (!isRiderLayoutSettingsIntegrated()) return EMPTY_ARRAY
        val actionManager = ActionManager.getInstance()
        val switches = SCOPE_SWITCH_IDS.mapNotNull(actionManager::getAction)
        return if (switches.isEmpty()) EMPTY_ARRAY else arrayOf(Separator.getInstance(), *switches.toTypedArray())
    }

    private companion object {
        val SCOPE_SWITCH_IDS = listOf("RiderToolWindowPerIde", "RiderToolWindowPerProject")
    }
}

/** One of Rider's default layout actions (overridden), hidden while integrated and otherwise unchanged. */
abstract class RiderDefaultLayoutActionWrapper(private val rider: AnAction) : AnAction(), DumbAware {
    override fun getActionUpdateThread() = rider.actionUpdateThread

    override fun update(e: AnActionEvent) {
        if (isRiderLayoutSettingsIntegrated()) e.presentation.isEnabledAndVisible = false else rider.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) = rider.actionPerformed(e)
}

/** "Store Current Layout as Default" of Rider's Layout Settings. */
class RiderStoreDefaultLayoutWrapper : RiderDefaultLayoutActionWrapper(RiderStoreDefaultLayoutAction())

/**
 * "Restore Default Layout" of Rider's Layout Settings. Rider's class is internal, so it is rebuilt the same way:
 * the platform action, enabled like "Store Current Layout as Default" (only with a layout per project).
 */
class RiderRestoreDefaultLayoutWrapper : RiderDefaultLayoutActionWrapper(RestoreDefaultLayoutAction()) {
    private val store = RiderStoreDefaultLayoutAction()

    override fun update(e: AnActionEvent) {
        if (isRiderLayoutSettingsIntegrated()) e.presentation.isEnabledAndVisible = false else store.update(e)
    }
}
