package jp.lunaproject.layoutmanager.settings

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.engine.PreciseLayoutEngine
import javax.swing.JComponent

/** Settings | Tools | Layout Manager */
class LayoutManagerConfigurable : BoundConfigurable(LayoutManagerBundle.message("settings.display.name")) {
    private val settings get() = LayoutManagerSettings.getInstance()

    /** The menu chosen in the UI; written to [settings] only once the switch is confirmed in [apply]. */
    private var chosenMenu: LayoutMenu = settings.layoutMenu

    override fun createPanel(): DialogPanel = panel {
        // The standard menu and the precise engine are contributed by Layout Manager Advanced.
        if (LayoutManagerSettings.isStandardMenuInstalled) {
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
        }
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
        if (PreciseLayoutEngine.isInstalled) {
            buttonsGroup(LayoutManagerBundle.message("settings.engine")) {
                row {
                    radioButton(LayoutManagerBundle.message("settings.engine.precise"), EngineMode.PRECISE)
                        .comment(
                            if (PreciseLayoutEngine.available != null) LayoutManagerBundle.message("settings.engine.precise.comment")
                            else LayoutManagerBundle.message("settings.engine.precise.unavailable")
                        )
                }
                row {
                    radioButton(LayoutManagerBundle.message("settings.engine.public"), EngineMode.PUBLIC_API)
                        .comment(LayoutManagerBundle.message("settings.engine.public.comment"))
                }
            }.bind(settings::engineMode)
        }
        group(LayoutManagerBundle.message("settings.advanced")) {
            row {
                text(LayoutManagerBundle.message("settings.advanced.comment"))
            }
            row {
                if (isAdvancedInstalled) {
                    label(LayoutManagerBundle.message("settings.advanced.installed"))
                } else {
                    button(LayoutManagerBundle.message("settings.advanced.install")) { event ->
                        installAdvanced(event.source as JComponent)
                    }
                }
            }
        }
    }

    private val isAdvancedInstalled get() = PreciseLayoutEngine.isInstalled || LayoutManagerSettings.isStandardMenuInstalled

    /**
     * Installs Layout Manager Advanced from its plugin repository on GitHub, once the user agreed: the
     * repository is added to the IDE's custom plugin repositories, so that the IDE also offers its updates.
     */
    private fun installAdvanced(component: JComponent) {
        val answer = Messages.showOkCancelDialog(
            component,
            LayoutManagerBundle.message("settings.advanced.confirm.message", ADVANCED_RELEASES, ADVANCED_REPOSITORY),
            LayoutManagerBundle.message("settings.advanced.confirm.title"),
            LayoutManagerBundle.message("settings.advanced.confirm.ok"),
            Messages.getCancelButton(),
            Messages.getWarningIcon(),
        )
        if (answer != Messages.OK) return
        val hosts = UpdateSettings.getInstance().storedPluginHosts
        if (ADVANCED_REPOSITORY !in hosts) hosts += ADVANCED_REPOSITORY
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
        installAndEnable(project, setOf(PluginId.getId(ADVANCED_ID)), true) {}
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

    private companion object {
        const val ADVANCED_ID = "jp.lunaproject.layoutmanager.advanced"

        /** Custom plugin repository (updatePlugins.xml in the source repository) offering Layout Manager Advanced. */
        const val ADVANCED_REPOSITORY = "https://raw.githubusercontent.com/LunaProject-Discord/LayoutManager/main/updatePlugins.xml"
        const val ADVANCED_RELEASES = "https://github.com/LunaProject-Discord/LayoutManager/releases"
    }
}
