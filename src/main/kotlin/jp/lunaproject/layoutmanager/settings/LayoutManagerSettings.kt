package jp.lunaproject.layoutmanager.settings

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** How layouts are captured and applied. */
enum class EngineMode {
    /** The platform's layout model, contributed by Layout Manager Advanced (internal API); [PUBLIC_API] without it. */
    PRECISE,

    /** Public tool window API only. */
    PUBLIC_API,
}

/** Which layout menu is shown under Window. Only one is shown: their layouts are stored separately. */
enum class LayoutMenu {
    /** Window | Layouts of this plugin. */
    LAYOUT_MANAGER,

    /**
     * Window | Layouts as in other JetBrains IDEs: the platform's own layout actions, hidden by Rider. They are
     * internal API, so Layout Manager Advanced contributes this menu; [LAYOUT_MANAGER] is used without it.
     */
    STANDARD,
}

@Service(Service.Level.APP)
@State(name = "LayoutManagerSettings", storages = [Storage("layoutManager.xml")])
class LayoutManagerSettings : SimplePersistentStateComponent<LayoutManagerSettings.SettingsState>(SettingsState()) {
    class SettingsState : BaseState() {
        var restoreOnStartup by property(true)
        var engineMode by enum(EngineMode.PRECISE)
        var layoutMenu by enum(LayoutMenu.LAYOUT_MANAGER)
        var integrateRiderLayoutSettings by property(true)
    }

    var restoreOnStartup: Boolean
        get() = state.restoreOnStartup
        set(value) {
            state.restoreOnStartup = value
        }

    var engineMode: EngineMode
        get() = state.engineMode
        set(value) {
            state.engineMode = value
        }

    var layoutMenu: LayoutMenu
        get() = state.layoutMenu
        set(value) {
            state.layoutMenu = value
        }

    /** The layout menu in effect: [LayoutMenu.STANDARD] only while Layout Manager Advanced provides it. */
    val effectiveLayoutMenu: LayoutMenu
        get() = if (layoutMenu == LayoutMenu.STANDARD && isStandardMenuInstalled) LayoutMenu.STANDARD else LayoutMenu.LAYOUT_MANAGER

    /** Rider's Window | Layout Settings is hidden and its layout scope switches are shown in the layout menu. */
    var integrateRiderLayoutSettings: Boolean
        get() = state.integrateRiderLayoutSettings
        set(value) {
            state.integrateRiderLayoutSettings = value
        }

    companion object {
        /** The standard layout menu, registered by Layout Manager Advanced. */
        const val STANDARD_MENU_ID = "LayoutManager.StandardLayouts"

        fun getInstance(): LayoutManagerSettings = service()

        /** Whether Layout Manager Advanced provides the standard layout menu. */
        val isStandardMenuInstalled: Boolean get() = ActionManager.getInstance().getAction(STANDARD_MENU_ID) != null
    }
}
