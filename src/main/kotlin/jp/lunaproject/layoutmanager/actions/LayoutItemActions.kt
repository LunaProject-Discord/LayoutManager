package jp.lunaproject.layoutmanager.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import jp.lunaproject.layoutmanager.LayoutManager
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.model.LayoutRef
import jp.lunaproject.layoutmanager.ui.LayoutNameDialog

/** Base for actions operating on one saved layout. Not registered, created by [LayoutsActionGroup]. */
internal abstract class LayoutItemAction(protected val ref: LayoutRef, textKey: String) : DumbAwareAction() {
    init {
        templatePresentation.text = LayoutManagerBundle.message(textKey)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.let { LayoutManager.exists(it, ref) } == true
    }
}

/** "Apply", offered for layouts other than the active one. */
internal class ApplyLayoutAction(ref: LayoutRef) : LayoutItemAction(ref, "action.apply.text") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LayoutManager.apply(project, ref)
    }
}

/** "Restore" of the active layout; shows the shortcut of Restore Current Layout as the standard menu does. */
internal class RestoreLayoutAction(ref: LayoutRef) : LayoutItemAction(ref, "action.restore.text") {
    init {
        ActionManager.getInstance().getAction(RESTORE_CURRENT_LAYOUT_ID)?.let(::copyShortcutFrom)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LayoutManager.apply(project, ref)
    }
}

/** "Save Changes" of the active layout. */
internal class SaveLayoutChangesAction(ref: LayoutRef) : LayoutItemAction(ref, "action.save.changes.text") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LayoutManager.save(project, ref)
    }
}

private const val RESTORE_CURRENT_LAYOUT_ID = "RestoreDefaultLayout"

internal class RenameLayoutAction(ref: LayoutRef) : LayoutItemAction(ref, "action.rename.text") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // The standard "Rename Layout" dialog.
        val dialog = LayoutNameDialog(
            project,
            LayoutManagerBundle.message("dialog.rename.title"),
            LayoutManagerBundle.message("dialog.rename.ok"),
            ref.name,
            renaming = ref,
        )
        if (!dialog.showAndGet()) return
        LayoutManager.rename(project, ref, dialog.layoutRef.name)
    }
}

/** "Delete…"; like the standard menu, the active layout cannot be deleted. */
internal class DeleteLayoutAction(ref: LayoutRef) : LayoutItemAction(ref, "action.delete.text") {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val isActive = e.project?.let(LayoutManager::activeLayout) == ref
        if (isActive) {
            e.presentation.isEnabled = false
            e.presentation.description = LayoutManagerBundle.message("action.delete.active.description")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // The standard delete confirmation: "Delete" / "Cancel".
        val answer = Messages.showOkCancelDialog(
            project,
            LayoutManagerBundle.message("dialog.delete.message", ref.name),
            LayoutManagerBundle.message("dialog.delete.title"),
            LayoutManagerBundle.message("dialog.delete.ok"),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon(),
        )
        if (answer == Messages.OK) {
            LayoutManager.delete(project, ref)
        }
    }
}
