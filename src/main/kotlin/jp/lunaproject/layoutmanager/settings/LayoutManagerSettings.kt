package jp.lunaproject.layoutmanager.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** How layouts are captured and applied. */
enum class EngineMode {
    /** Platform layout model (internal API); falls back to [PUBLIC_API] when it is unavailable. */
    PRECISE,

    /** Public tool window API only. */
    PUBLIC_API,
}

/** Which layout menu is shown under Window. Only one is shown: their layouts are stored separately. */
enum class LayoutMenu {
    /** Window | Layouts of this plugin. */
    LAYOUT_MANAGER,

    /** Window | Layouts as in other JetBrains IDEs: the platform's own layout actions, hidden by Rider. */
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

    /** Rider's Window | Layout Settings is hidden and its layout scope switches are shown in the layout menu. */
    var integrateRiderLayoutSettings: Boolean
        get() = state.integrateRiderLayoutSettings
        set(value) {
            state.integrateRiderLayoutSettings = value
        }

    companion object {
        fun getInstance(): LayoutManagerSettings = service()
    }
}
