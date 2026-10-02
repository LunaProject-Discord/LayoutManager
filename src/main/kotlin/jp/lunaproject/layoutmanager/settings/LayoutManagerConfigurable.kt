package jp.lunaproject.layoutmanager.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.engine.DesktopLayoutEngine

/** Settings | Tools | Layout Manager */
class LayoutManagerConfigurable : BoundConfigurable(LayoutManagerBundle.message("settings.display.name")) {
    private val settings get() = LayoutManagerSettings.getInstance()

    /** The menu chosen in the UI; written to [settings] only once the switch is confirmed in [apply]. */
    private var chosenMenu: LayoutMenu = settings.layoutMenu

    override fun createPanel(): DialogPanel = panel {
        buttonsGroup(LayoutManagerBundle.message("settings.layout.menu")) {
            row {
                radioButton(LayoutManagerBundle.message("settings.layout.menu.plugin"), LayoutMenu.LAYOUT_MANAGER)
                    .comment(LayoutManagerBundle.message("settings.layout.menu.plugin.comment"))
            }
            row {
                radioButton(LayoutManagerBundle.message("settings.layout.menu.standard"), LayoutMenu.STANDARD)
                    .comment(LayoutManagerBundle.message("settings.layout.menu.standard.comment"))
            }
        }.bind(::chosenMenu)
        row {
            checkBox(LayoutManagerBundle.message("settings.integrate.rider.layout.settings"))
                .bindSelected(settings::integrateRiderLayoutSettings)
                .comment(LayoutManagerBundle.message("settings.integrate.rider.layout.settings.comment"))
        }
        row {
            checkBox(LayoutManagerBundle.message("settings.restore.on.startup"))
                .bindSelected(settings::restoreOnStartup)
                .comment(LayoutManagerBundle.message("settings.restore.on.startup.comment"))
        }
        buttonsGroup(LayoutManagerBundle.message("settings.engine")) {
            row {
                radioButton(LayoutManagerBundle.message("settings.engine.precise"), EngineMode.PRECISE)
                    .comment(
                        if (DesktopLayoutEngine.isAvailable) LayoutManagerBundle.message("settings.engine.precise.comment")
                        else LayoutManagerBundle.message("settings.engine.precise.unavailable")
                    )
            }
            row {
                radioButton(LayoutManagerBundle.message("settings.engine.public"), EngineMode.PUBLIC_API)
                    .comment(LayoutManagerBundle.message("settings.engine.public.comment"))
            }
        }.bind(settings::engineMode)
    }

    override fun reset() {
        chosenMenu = settings.layoutMenu
        super.reset()
    }

    override fun apply() {
        super.apply()
        if (chosenMenu == settings.layoutMenu) return
        if (confirmMenuSwitch(chosenMenu)) {
            settings.layoutMenu = chosenMenu
        } else {
            reset()
        }
    }

    /** Warns that layouts saved through the other menu stay stored but are not listed in [target]. */
    private fun confirmMenuSwitch(target: LayoutMenu): Boolean {
        val (message, ok) = when (target) {
            LayoutMenu.LAYOUT_MANAGER ->
                "settings.layout.menu.switch.to.plugin" to "settings.layout.menu.switch.to.plugin.ok"
            LayoutMenu.STANDARD ->
                "settings.layout.menu.switch.to.standard" to "settings.layout.menu.switch.to.standard.ok"
        }
        return Messages.showOkCancelDialog(
            createComponent(),
            LayoutManagerBundle.message(message),
            LayoutManagerBundle.message("settings.layout.menu.switch.title"),
            LayoutManagerBundle.message(ok),
            Messages.getCancelButton(),
            Messages.getWarningIcon(),
        ) == Messages.OK
    }
}
