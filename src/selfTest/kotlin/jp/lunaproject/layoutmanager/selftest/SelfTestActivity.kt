package jp.lunaproject.layoutmanager.selftest

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.DialogWrapper
import jp.lunaproject.layoutmanager.LayoutManager
import jp.lunaproject.layoutmanager.engine.PreciseLayoutEngine
import jp.lunaproject.layoutmanager.model.LayoutRef
import jp.lunaproject.layoutmanager.model.LayoutScope
import jp.lunaproject.layoutmanager.settings.EngineMode
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings
import jp.lunaproject.layoutmanager.settings.LayoutMenu
import jp.lunaproject.layoutmanager.storage.GlobalLayoutStore
import jp.lunaproject.layoutmanager.storage.SolutionLayoutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dialog
import java.awt.Window
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

/**
 * In-IDE self-test run by `./gradlew selfTest` (see build.gradle.kts). Ships in a separate test-only
 * plugin that is installed into the self-test sandbox only.
 *
 * - phase1: exercises the engines and the layout operations, leaves a known layout and exits.
 * - phase2: after the restart, checks that the last-session layout and the saved layouts survived.
 *
 * Runs with Layout Manager alone ("basic" variant) and with Layout Manager Advanced ("advanced").
 */
class SelfTestActivity : ProjectActivity {
    /** Whether Layout Manager Advanced is installed: precise engine and standard menu. */
    private val advanced get() = PreciseLayoutEngine.isInstalled

    /** The engines under test. The first one saves the layouts that the later checks use. */
    private val engines get() = if (advanced) listOf(EngineMode.PRECISE, EngineMode.PUBLIC_API) else listOf(EngineMode.PUBLIC_API)

    /** Name prefix of the layouts saved by the first engine. */
    private val lead get() = engines.first().label

    override suspend fun execute(project: Project) {
        val phase = System.getProperty(PHASE_PROPERTY) ?: return
        val outDir = Path.of(System.getProperty(OUT_PROPERTY))
        val log = SelfTestLog(outDir.resolve("$phase.log"))
        val dialogCloser = closeStartupDialogs(log)
        try {
            val variant = System.getProperty(VARIANT_PROPERTY)
            log.check("$variant variant: Layout Manager Advanced installed=$advanced", advanced == (variant == "advanced"))
            val windows = TestWindows.await(project)
            log.info("test windows: ${windows.ids}")
            when (phase) {
                "phase1" -> phase1(project, log, outDir, windows)
                "phase2" -> phase2(project, log, outDir)
                else -> log.fail("unknown phase", phase)
            }
            checkNoErrorsBlamingPlugin(log)
        } catch (t: Throwable) {
            log.fail("exception", t.stackTraceToString())
        } finally {
            dialogCloser.cancel()
            log.done()
            // Exit like File | Exit does, saving settings, outside of this startup activity.
            ApplicationManager.getApplication().invokeLater {
                (ApplicationManager.getApplication() as ApplicationEx).exit(ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE)
            }
        }
    }

    /**
     * A fresh sandbox opens first-launch dialogs (New UI onboarding, promotions). Their modal loop blocks
     * the EDT work of the test, and the test itself opens none, so any modal dialog is closed while it runs.
     */
    private fun closeStartupDialogs(log: SelfTestLog): Job = CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            ApplicationManager.getApplication().invokeLater({
                for (window in Window.getWindows()) {
                    if (window !is Dialog || !window.isShowing || !window.isModal) continue
                    val dialog = DialogWrapper.findInstance(window) ?: continue
                    log.info("closed startup dialog: ${window.title} (${dialog.javaClass.name})")
                    dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
                }
            }, ModalityState.any())
            delay(1000)
        }
    }

    private suspend fun phase1(project: Project, log: SelfTestLog, outDir: Path, windows: TestWindows) {
        resetState(project)
        withContext(Dispatchers.EDT) { checkPlaceholders(project, log, "no saved layouts", layoutsMenuChildren(project)) }
        val settings = LayoutManagerSettings.getInstance()
        lateinit var leadS1: FrameSnapshot

        for (mode in engines) {
            settings.engineMode = mode
            val engine = mode.label

            setUpState1(project, windows)
            val s1 = LayoutRef(LayoutScope.GLOBAL, "$engine-S1")
            withContext(Dispatchers.EDT) { LayoutManager.save(project, s1) }
            val expected1 = awaitSettled(project)
            if (mode == engines.first()) leadS1 = expected1

            setUpState2(project, windows)
            val s2 = LayoutRef(LayoutScope.SOLUTION, "$engine-S2")
            withContext(Dispatchers.EDT) { LayoutManager.save(project, s2) }
            val expected2 = awaitSettled(project)

            // Public API cannot move floating windows, so after S3 changed their remembered bounds
            // a bounds difference is the documented limitation of that engine.
            val limitations = if (mode == EngineMode.PUBLIC_API) setOf("bounds") else emptySet()
            checkApply(project, log, "$engine apply S1 (from S2)", s1, expected1)
            checkApply(project, log, "$engine apply S2 (from S1)", s2, expected2)
            setUpState3(project, windows)
            checkApply(project, log, "$engine apply S1 (from scrambled S3)", s1, expected1, limitations)
            setUpState3(project, windows)
            checkApply(project, log, "$engine apply S2 (from scrambled S3)", s2, expected2, limitations)

            log.check("$engine active layout after apply S2", LayoutManager.activeLayout(project) == s2)
        }

        if (advanced) {
            // With the public API engine selected, a layout saved by the precise engine must still apply
            // (as after uninstalling Layout Manager Advanced); only what public API can restore is compared.
            settings.engineMode = EngineMode.PUBLIC_API
            setUpState2(project, windows)
            withContext(Dispatchers.EDT) { LayoutManager.apply(project, LayoutRef(LayoutScope.GLOBAL, "precise-S1")) }
            report(log, "public engine applies precise-format S1",
                diff(leadS1, awaitSettled(project), keys = setOf("anchor", "visible", "split", "type")))
        }

        settings.engineMode = EngineMode.PRECISE
        settings.restoreOnStartup = true
        withContext(Dispatchers.EDT) {
            checkOperations(project, log)
            if (advanced) checkStandardLayoutsMenu(project, log) else checkWithoutAdvanced(project, log)
        }
        checkLayoutManagerMenu(project, log, windows, leadS1)

        // Leave a known layout and record the frame for phase 2, which expects it to be restored.
        withContext(Dispatchers.EDT) { LayoutManager.apply(project, LayoutRef(LayoutScope.GLOBAL, "$lead-S1")) }
        outDir.resolve(EXIT_STATE_FILE).writeLines(awaitSettled(project).serialize())
        log.info("exit state recorded, active=${LayoutManager.activeLayout(project)}")
    }

    private fun resetState(project: Project) {
        val global = GlobalLayoutStore.getInstance()
        global.names().forEach(global::remove)
        val solution = SolutionLayoutStore.getInstance(project)
        solution.names().forEach(solution::remove)
        solution.activeLayout = null
    }

    private suspend fun checkApply(
        project: Project,
        log: SelfTestLog,
        title: String,
        ref: LayoutRef,
        expected: FrameSnapshot,
        limitations: Set<String> = emptySet(),
    ) {
        withContext(Dispatchers.EDT) { LayoutManager.apply(project, ref) }
        report(log, title, diff(expected, awaitSettled(project)), limitations)
    }

    private fun report(log: SelfTestLog, title: String, diffs: List<Difference>, limitations: Set<String> = emptySet()) {
        when {
            diffs.isEmpty() -> log.check(title, true)
            diffs.all { it.key in limitations } -> log.expected("$title (${diffs.size} known-limitation diffs)")
            else -> log.check("$title (${diffs.size} diffs)", false)
        }
        diffs.forEach { log.info("    $it") }
    }

    private fun checkOperations(project: Project, log: SelfTestLog) {
        val global = LayoutManager.names(project, LayoutScope.GLOBAL)
        val solution = LayoutManager.names(project, LayoutScope.SOLUTION)
        log.check("global names $global", global == engines.map { "${it.label}-S1" }.sorted())
        log.check("solution names $solution", solution == engines.map { "${it.label}-S2" }.sorted())

        val tmp = LayoutRef(LayoutScope.GLOBAL, "tmp")
        LayoutManager.save(project, tmp)
        log.check("save makes layout active", LayoutManager.activeLayout(project) == tmp)
        log.check("rename succeeds", LayoutManager.rename(project, tmp, "tmp renamed"))
        val renamed = LayoutRef(LayoutScope.GLOBAL, "tmp renamed")
        log.check("rename updates active layout", LayoutManager.activeLayout(project) == renamed)
        log.check("old name gone after rename", !LayoutManager.exists(project, tmp))
        log.check("rename of missing layout fails", !LayoutManager.rename(project, tmp, "x"))
        log.check("delete succeeds", LayoutManager.delete(project, renamed))
        log.check("delete clears active layout", LayoutManager.activeLayout(project) == null)
        log.check("delete of missing layout fails", !LayoutManager.delete(project, renamed))

        val sameName = LayoutRef(LayoutScope.SOLUTION, "$lead-S1")
        LayoutManager.save(project, sameName)
        log.check("same name allowed in both scopes",
            LayoutManager.exists(project, sameName) && LayoutManager.exists(project, LayoutRef(LayoutScope.GLOBAL, "$lead-S1")))
        LayoutManager.delete(project, sameName)
        log.check("deleting solution layout keeps global one",
            LayoutManager.exists(project, LayoutRef(LayoutScope.GLOBAL, "$lead-S1")))
    }

    /**
     * Without Layout Manager Advanced there is no standard menu: a stored choice of it falls back to Layout
     * Manager's menu, and Rider's own "Default" action (used for "Default") is left as Rider registered it.
     */
    private fun checkWithoutAdvanced(project: Project, log: SelfTestLog) {
        val actionManager = ActionManager.getInstance()
        log.check("no standard menu without Advanced", actionManager.getAction("LayoutManager.StandardLayouts") == null)
        val factoryDefault = actionManager.getAction("RestoreFactoryDefaultLayout")
        log.check("Rider's Default action untouched (${factoryDefault?.javaClass?.simpleName})",
            factoryDefault?.javaClass?.name == "com.intellij.ide.actions.RestoreFactoryDefaultLayoutAction")

        val settings = LayoutManagerSettings.getInstance()
        settings.layoutMenu = LayoutMenu.STANDARD
        val shown = updated(project, actionManager.getAction("LayoutManager.Layouts")).isVisible
        log.check("stored standard menu choice falls back to Layout Manager's menu (shown=$shown)",
            shown && settings.effectiveLayoutMenu == LayoutMenu.LAYOUT_MANAGER)
        settings.layoutMenu = LayoutMenu.LAYOUT_MANAGER
        checkRiderLayoutSettings(project, log, LayoutMenu.LAYOUT_MANAGER)
    }

    /** The platform's layout actions Rider unregisters are back, and only the chosen layout menu is shown. */
    private fun checkStandardLayoutsMenu(project: Project, log: SelfTestLog) {
        val actionManager = ActionManager.getInstance()
        val ids = listOf("RestoreFactoryDefaultLayout", "CustomLayoutsGroup", "RestoreDefaultLayout", "StoreDefaultLayout", "StoreNewLayout")
        val missing = ids.filter { actionManager.getAction(it) == null }
        log.check("standard layout actions registered${if (missing.isEmpty()) "" else ", missing $missing"}", missing.isEmpty())

        val group = actionManager.getAction("LayoutManager.StandardLayouts") as ActionGroup
        val children = (group as DefaultActionGroup).getChildActionsOrStubs().mapNotNull { actionManager.getId(it) }
        val notInMenu = ids.filter { it !in children }
        log.check("standard layouts menu contains all actions${if (notInMenu.isEmpty()) "" else ", missing $notInMenu"}", notInMenu.isEmpty())

        // Exactly one of the two menus (and the plugin's own actions with it) is shown at a time.
        val settings = LayoutManagerSettings.getInstance()
        fun isShown(actionId: String) = updated(project, actionManager.getAction(actionId)).isVisible
        log.check("default layout menu is Layout Manager", settings.layoutMenu == LayoutMenu.LAYOUT_MANAGER)
        for (menu in LayoutMenu.entries) {
            settings.layoutMenu = menu
            val plugin = isShown("LayoutManager.Layouts") && isShown("LayoutManager.SaveNewLayout")
            val standard = isShown("LayoutManager.StandardLayouts")
            log.check("$menu menu: plugin menu shown=$plugin, standard menu shown=$standard",
                plugin == (menu == LayoutMenu.LAYOUT_MANAGER) && standard == (menu == LayoutMenu.STANDARD))
            // Hidden actions are not offered by Find Action either.
            val standardActions = listOf("RestoreFactoryDefaultLayout", "StoreNewLayout").associateWith(::isShown)
            log.check("$menu menu: standard actions shown=$standardActions",
                standardActions.values.all { it == (menu == LayoutMenu.STANDARD) })
            checkRiderLayoutSettings(project, log, menu)
        }
        settings.layoutMenu = LayoutMenu.LAYOUT_MANAGER
    }

    /**
     * Rider's Window | Layout Settings is replaced by the plugin's group in the Window menu; while integrated
     * it is hidden with its default layout actions, and its scope switches end the chosen layout menu.
     */
    private fun checkRiderLayoutSettings(project: Project, log: SelfTestLog, menu: LayoutMenu) {
        val actionManager = ActionManager.getInstance()
        val settings = LayoutManagerSettings.getInstance()
        val riderGroup = actionManager.getAction("RiderWindowLayoutActions")
        val windowMenu = actionManager.getAction("WindowMenu") as DefaultActionGroup
        log.check("$menu menu: Window menu holds the overridden Layout Settings (${riderGroup?.javaClass?.simpleName})",
            riderGroup?.javaClass?.simpleName == "RiderLayoutSettingsGroup" &&
                windowMenu.getChildActionsOrStubs().any { actionManager.getId(it) == "RiderWindowLayoutActions" })

        val scopeSwitches = listOf("RiderToolWindowPerIde", "RiderToolWindowPerProject")
        val menuChildren = { (actionManager.getAction(when (menu) {
            LayoutMenu.LAYOUT_MANAGER -> "LayoutManager.Layouts"
            LayoutMenu.STANDARD -> "LayoutManager.StandardLayouts"
        }) as ActionGroup).expandedIds(project) }
        for (integrated in listOf(true, false)) {
            settings.integrateRiderLayoutSettings = integrated
            val groupShown = updated(project, riderGroup).isVisible
            val defaultActionsShown = listOf("RiderStoreDefaultLayout", "RiderRestoreDefaultLayout")
                .associateWith { updated(project, actionManager.getAction(it)).isVisible }
            val ids = menuChildren()
            val switchesAtEnd = ids.takeLast(scopeSwitches.size) == scopeSwitches
            log.check("$menu menu, integrated=$integrated: Layout Settings shown=$groupShown, default layout actions shown=$defaultActionsShown, scope switches at end=$switchesAtEnd",
                groupShown == !integrated && defaultActionsShown.values.all { it == !integrated } &&
                    switchesAtEnd == integrated && scopeSwitches.none { it in ids } == !integrated)
        }
        settings.integrateRiderLayoutSettings = true
    }

    /** Ids of a menu's actions (separators left out), with the inline scope switch group expanded as menus do. */
    private fun ActionGroup.expandedIds(project: Project): List<String> {
        val actionManager = ActionManager.getInstance()
        return getChildren(event(project, templatePresentation.clone())).flatMap { child ->
            val id = actionManager.getId(child)
            if (id == "LayoutManager.RiderLayoutScope") (child as ActionGroup).expandedIds(project) else listOfNotNull(id)
        }
    }

    /**
     * Window | Layouts is laid out like the standard menu, and Shift+F12 ("RestoreDefaultLayout") and
     * "Default" act on Layout Manager's layouts while it is the chosen menu.
     */
    private suspend fun checkLayoutManagerMenu(project: Project, log: SelfTestLog, windows: TestWindows, leadS1: FrameSnapshot) {
        val actionManager = ActionManager.getInstance()
        val s1 = LayoutRef(LayoutScope.GLOBAL, "$lead-S1")
        withContext(Dispatchers.EDT) { LayoutManager.apply(project, s1) }
        awaitSettled(project)

        withContext(Dispatchers.EDT) {
            val group = actionManager.getAction("LayoutManager.Layouts") as ActionGroup
            val children = group.getChildren(event(project, group.templatePresentation.clone()))
            val first = actionManager.getId(children.first())
            // The Rider layout scope switches (checked in checkRiderLayoutSettings) follow "Save as New".
            val last = children.map { actionManager.getId(it) }.last { it != "LayoutManager.RiderLayoutScope" }
            log.check("menu starts with Default and ends with Save as New ($first … $last)",
                first == "LayoutManager.RestoreFactoryDefault" && last == "LayoutManager.SaveNewLayout")

            checkPlaceholders(project, log, "with layouts in both scopes", children)

            fun itemActions(name: String) = children.filterIsInstance<ActionGroup>()
                .first { it.templatePresentation.text == name }
                .getChildren(null)
            val active = itemActions("$lead-S1")
            log.check("active layout offers Restore / Save Changes / Rename / --- / Delete: ${active.map { it.javaClass.simpleName }}",
                active.map { it.javaClass.simpleName } ==
                    listOf("RestoreLayoutAction", "SaveLayoutChangesAction", "RenameLayoutAction", "Separator", "DeleteLayoutAction"))
            log.check("active layout cannot be deleted", !updated(project, active.last()).isEnabled)
            val other = itemActions("$lead-S2")
            log.check("other layouts offer Apply / Rename / --- / Delete: ${other.map { it.javaClass.simpleName }}",
                other.map { it.javaClass.simpleName } == listOf("ApplyLayoutAction", "RenameLayoutAction", "Separator", "DeleteLayoutAction"))
            log.check("other layouts can be deleted", updated(project, other.last()).isEnabled)
        }

        // Shift+F12 restores Layout Manager's active layout, not the standard menu's.
        setUpState2(project, windows)
        withContext(Dispatchers.EDT) {
            val restore = actionManager.getAction("RestoreDefaultLayout")
            val presentation = restore.templatePresentation.clone()
            val event = event(project, presentation)
            restore.update(event)
            log.check("Restore Current Layout enabled for Layout Manager", presentation.isEnabled)
            restore.actionPerformed(event)
        }
        report(log, "Restore Current Layout restores Layout Manager's active layout", diff(leadS1, awaitSettled(project)),
            limitations = if (advanced) emptySet() else setOf("bounds"))

        val restored = withContext(Dispatchers.EDT) {
            val default = actionManager.getAction("LayoutManager.RestoreFactoryDefault")
            LayoutManager.restoreFactoryDefault(event(project, default.templatePresentation.clone()))
        }
        awaitSettled(project)
        log.check("Default applies the factory layout and clears the active layout",
            restored && LayoutManager.activeLayout(project) == null)
        withContext(Dispatchers.EDT) {
            val default = actionManager.getAction("LayoutManager.RestoreFactoryDefault")
            log.check("Default is checked while active", Toggleable.isSelected(updated(project, default)))
            val restore = updated(project, actionManager.getAction("RestoreDefaultLayout"))
            log.check("Restore Current Layout available while Default is active", restore.isEnabled)
            LayoutManager.apply(project, s1)
            log.check("Default is unchecked after applying a saved layout", !Toggleable.isSelected(updated(project, default)))
        }
    }

    private fun event(project: Project, presentation: Presentation): AnActionEvent = AnActionEvent.createEvent(
        SimpleDataContext.getProjectContext(project), presentation, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null,
    )

    /** Every scope has a section header; an empty one shows a disabled "(None)" placeholder. */
    private fun checkPlaceholders(project: Project, log: SelfTestLog, case: String, children: Array<AnAction>) {
        val headers = children.count { it is Separator && !it.text.isNullOrEmpty() }
        val placeholders = children.filter { it.javaClass.simpleName == "NoLayoutsPlaceholder" }
        val emptyScopes = LayoutScope.entries.count { LayoutManager.names(project, it).isEmpty() }
        log.check("$case: a header per scope ($headers) and a disabled placeholder per empty scope (${placeholders.size} for $emptyScopes)",
            headers == LayoutScope.entries.size && placeholders.size == emptyScopes &&
                placeholders.none { updated(project, it).isEnabled })
    }

    private fun layoutsMenuChildren(project: Project): Array<AnAction> {
        val group = ActionManager.getInstance().getAction("LayoutManager.Layouts") as ActionGroup
        return group.getChildren(event(project, group.templatePresentation.clone()))
    }

    private fun updated(project: Project, action: AnAction): Presentation {
        val presentation = action.templatePresentation.clone()
        action.update(event(project, presentation))
        return presentation
    }

    private suspend fun phase2(project: Project, log: SelfTestLog, outDir: Path) {
        val expected = deserializeSnapshot(outDir.resolve(EXIT_STATE_FILE).readLines())
        // Public API cannot move floating windows: their bounds are whatever the IDE restored by itself.
        report(log, "last-session layout restored on open", diff(expected, awaitSettled(project)),
            limitations = if (advanced) emptySet() else setOf("bounds"))

        log.check("global layouts persisted",
            LayoutManager.names(project, LayoutScope.GLOBAL) == engines.map { "${it.label}-S1" }.sorted())
        log.check("solution layouts persisted",
            LayoutManager.names(project, LayoutScope.SOLUTION) == engines.map { "${it.label}-S2" }.sorted())
        log.check("active layout persisted", LayoutManager.activeLayout(project) == LayoutRef(LayoutScope.GLOBAL, "$lead-S1"))

        val s2 = LayoutRef(LayoutScope.SOLUTION, "$lead-S2")
        log.check("saved layout applies after restart", withContext(Dispatchers.EDT) { LayoutManager.apply(project, s2) })
    }

    /**
     * Errors attributed to the plugin show up as IDE error notifications for users. Only this IDE session
     * is checked: the exit of the previous phase reports platform leaks that name the test plugin.
     */
    private fun checkNoErrorsBlamingPlugin(log: SelfTestLog) {
        val session = currentSessionLog()
        log.info("checked ${session.size} log lines of this session")
        val blamed = session.indices.filter { "Plugin to blame: Layout Manager" in session[it] && "Self-Test" !in session[it] }
        log.check("no errors attributed to the plugin in this session (${blamed.size})", blamed.isEmpty())
        // The error itself is logged right before its "Plugin to blame" line.
        blamed.forEach { i -> log.info("    " + session.subList((i - 4).coerceAtLeast(0), i).joinToString(" | ").take(400)) }
    }

    /**
     * The log lines of this IDE session. idea.log is rotated to idea.1.log, idea.2.log, … when it grows,
     * so a session can start in a rotated file: read from the newest file backwards until its start.
     */
    private fun currentSessionLog(): List<String> {
        val dir = Path.of(PathManager.getLogPath())
        val files = generateSequence(0) { it + 1 }
            .map { dir.resolve(if (it == 0) "idea.log" else "idea.$it.log") }
            .takeWhile { Files.exists(it) }
        var lines = emptyList<String>()
        for (file in files) {
            lines = file.readLines() + lines
            val start = lines.indexOfLast { "IDE STARTED" in it }
            if (start >= 0) return lines.subList(start, lines.size)
        }
        return lines
    }

    private val EngineMode.label
        get() = when (this) {
            EngineMode.PRECISE -> "precise"
            EngineMode.PUBLIC_API -> "public"
        }

    private companion object {
        const val PHASE_PROPERTY = "layoutmanager.selftest.phase"
        const val OUT_PROPERTY = "layoutmanager.selftest.out"
        const val VARIANT_PROPERTY = "layoutmanager.selftest.variant"
        const val EXIT_STATE_FILE = "exit-state.txt"
    }
}
