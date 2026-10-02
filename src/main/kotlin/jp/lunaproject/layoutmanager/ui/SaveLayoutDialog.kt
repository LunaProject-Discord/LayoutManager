package jp.lunaproject.layoutmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.model.LayoutScope
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * "New Layout": the standard layout name dialog, plus a checkbox choosing whether the layout is shared
 * by all solutions or specific to this one.
 */
class SaveLayoutDialog private constructor(project: Project, private val solutionOnly: JBCheckBox) : LayoutNameDialog(
    project,
    LayoutManagerBundle.message("dialog.new.title"),
    LayoutManagerBundle.message("dialog.save.ok"),
    "",
) {
    constructor(project: Project) : this(
        project,
        JBCheckBox(LayoutManagerBundle.message("dialog.save.solution.only", project.name)).also { checkBoxBeingInitialized = it },
    )

    init {
        // The name is checked against the other scope now.
        solutionOnly.addItemListener { inputChanged() }
    }

    override val scope: LayoutScope
        get() = if (solutionOnly.isSelected) LayoutScope.SOLUTION else LayoutScope.GLOBAL

    /** Called from the superclass constructor, before [solutionOnly] is assigned. */
    override fun createMessagePanel(): JPanel {
        val checkBox = checkBoxBeingInitialized ?: error("SaveLayoutDialog must be created through its public constructor")
        checkBoxBeingInitialized = null
        checkBox.border = JBUI.Borders.emptyTop(8)
        return JPanel(BorderLayout()).apply {
            add(super.createMessagePanel(), BorderLayout.CENTER)
            add(checkBox, BorderLayout.SOUTH)
        }
    }

    private companion object {
        var checkBoxBeingInitialized: JBCheckBox? = null
    }
}
