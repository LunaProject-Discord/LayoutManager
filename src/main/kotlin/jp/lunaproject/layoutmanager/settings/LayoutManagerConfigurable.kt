package jp.lunaproject.layoutmanager.settings

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.engine.PreciseLayoutEngine
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings | Tools | Layout Manager
 *
 * The page is rebuilt when Layout Manager Advanced is installed from it, since that adds options. The controls are therefore bound to [pending] rather than to the settings, so that a rebuild
 * keeps what was changed but not applied yet.
 */
class LayoutManagerConfigurable : Configurable {
    private val settings get() = LayoutManagerSettings.getInstance()

    /** The values shown in the UI; written to the settings only in [apply]. */
    private class Pending(settings: LayoutManagerSettings) {
        var layoutMenu = settings.layoutMenu
        var integrateRiderLayoutSettings = settings.integrateRiderLayoutSettings
        var restoreOnStartup = settings.restoreOnStartup
        var engineMode = settings.engineMode
    }

    private var pending = Pending(settings)
    private var wrapper: JPanel? = null
    private var content: DialogPanel? = null

    override fun getDisplayName(): String = LayoutManagerBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        val wrapper = JPanel(BorderLayout()).also { this.wrapper = it }
        rebuild()
        return wrapper
    }

    /** Shows the options of Layout Manager Advanced once it is installed (when it did not need a restart). */
    private fun advancedInstalled() {
        val wrapper = wrapper ?: return
        // Settings is a modal dialog, so this runs in its modality.
        ApplicationManager.getApplication().invokeLater(
            { if (this.wrapper === wrapper && isAdvancedInstalled) rebuild() },
            ModalityState.stateForComponent(wrapper),
        )
    }

    /** Recreates the controls, keeping the values not applied yet. */
    private fun rebuild() {
        val wrapper = wrapper ?: return
        content?.apply()
        val panel = createPanel()
        content = panel
        wrapper.removeAll()
        wrapper.add(panel, BorderLayout.CENTER)
        wrapper.revalidate()
        wrapper.repaint()
    }

    private fun createPanel(): DialogPanel = panel {
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
            }.bind(pending::layoutMenu)
        }
        row {
            checkBox(LayoutManagerBundle.message("settings.integrate.rider.layout.settings"))
                .bindSelected(pending::integrateRiderLayoutSettings)
                .comment(LayoutManagerBundle.message("settings.integrate.rider.layout.settings.comment"))
        }
        row {
            checkBox(LayoutManagerBundle.message("settings.restore.on.startup"))
                .bindSelected(pending::restoreOnStartup)
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
            }.bind(pending::engineMode)
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
            LayoutManagerBundle.message("settings.advanced.confirm.message", ADVANCED_GITHUB),
            LayoutManagerBundle.message("settings.advanced.confirm.title"),
            LayoutManagerBundle.message("settings.advanced.confirm.ok"),
            Messages.getCancelButton(),
            Messages.getWarningIcon(),
        )
        if (answer != Messages.OK) return
        val hosts = UpdateSettings.getInstance().storedPluginHosts
        if (ADVANCED_REPOSITORY !in hosts) hosts += ADVANCED_REPOSITORY
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
        installAndEnable(project, setOf(PluginId.getId(ADVANCED_ID)), true) { advancedInstalled() }
    }

    override fun isModified(): Boolean {
        if (content?.isModified() == true) return true
        val settings = settings
        return pending.layoutMenu != settings.layoutMenu ||
            pending.integrateRiderLayoutSettings != settings.integrateRiderLayoutSettings ||
            pending.restoreOnStartup != settings.restoreOnStartup ||
            pending.engineMode != settings.engineMode
    }

    override fun reset() {
        pending = Pending(settings)
        // The controls are bound to the previous Pending, so they are recreated without carrying values over.
        content = null
        rebuild()
    }

    override fun apply() {
        content?.apply()
        val settings = settings
        settings.integrateRiderLayoutSettings = pending.integrateRiderLayoutSettings
        settings.restoreOnStartup = pending.restoreOnStartup
        settings.engineMode = pending.engineMode
        if (pending.layoutMenu == settings.layoutMenu) return
        if (confirmMenuSwitch(pending.layoutMenu)) {
            settings.layoutMenu = pending.layoutMenu
        } else {
            pending.layoutMenu = settings.layoutMenu
            content?.reset()
        }
    }

    override fun disposeUIResources() {
        wrapper = null
        content = null
    }

    /** Warns that layouts saved through the other menu stay stored but are not listed in [target]. */
    private fun confirmMenuSwitch(target: LayoutMenu): Boolean {
        val (message, ok) = when (target) {
            LayoutMenu.LAYOUT_MANAGER ->
                "settings.layout.menu.switch.to.plugin" to "settings.layout.menu.switch.to.plugin.ok"
            LayoutMenu.STANDARD ->
                "settings.layout.menu.switch.to.standard" to "settings.layout.menu.switch.to.standard.ok"
        }
        val parent = wrapper ?: return false
        return Messages.showOkCancelDialog(
            parent,
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

        /** The GitHub repository whose releases host Layout Manager Advanced. */
        const val ADVANCED_GITHUB = "LunaProject-Discord/LayoutManager"
    }
}
