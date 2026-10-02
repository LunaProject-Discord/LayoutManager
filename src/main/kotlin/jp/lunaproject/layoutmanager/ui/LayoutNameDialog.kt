package jp.lunaproject.layoutmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.DocumentAdapter
import jp.lunaproject.layoutmanager.LayoutManager
import jp.lunaproject.layoutmanager.LayoutManagerBundle
import jp.lunaproject.layoutmanager.model.LayoutRef
import jp.lunaproject.layoutmanager.model.LayoutScope
import javax.swing.Action
import javax.swing.event.DocumentEvent

/**
 * The layout name dialog ("New Layout", "Rename Layout") on the public [Messages.InputDialog].
 *
 * The name is checked while typing and problems are shown in a balloon on the field: a name over the
 * platform's length limit and, when renaming, the name of another existing layout are errors that disable
 * OK; when saving, an existing name only warns that OK overwrites it. A blank name just disables OK.
 */
open class LayoutNameDialog private constructor(
    project: Project,
    title: String,
    okText: String,
    initialName: String,
    private val validator: NameValidator,
) : Messages.InputDialog(project, LayoutManagerBundle.message("dialog.layout.name"), title, null, initialName, validator) {
    constructor(project: Project, title: String, okText: String, initialName: String, renaming: LayoutRef? = null) :
        this(project, title, okText, initialName, NameValidator(project, renaming))

    init {
        // As the standard dialog: rename OK rather than passing button options, which would bypass doOKAction.
        okAction.putValue(Action.NAME, okText)
        validator.scope = { scope }
        myField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = inputChanged()
        })
    }

    /** The scope the name is checked against. */
    protected open val scope: LayoutScope get() = validator.renaming?.scope ?: LayoutScope.GLOBAL

    /** Read from the field: [getInputString] stays `null` until the dialog has been closed with OK. */
    val layoutRef: LayoutRef get() = LayoutRef(scope, myField.text.trim())

    /** Re-checks the name and shows the result in a balloon on the field. */
    protected fun inputChanged() {
        val name = myField.text.trim()
        okAction.isEnabled = validator.checkInput(name)
        val info = validator.error(name)?.let { ValidationInfo(it, myField) }
            ?: validator.warning(name)?.let { ValidationInfo(it, myField).asWarning() }
        setErrorInfoAll(listOfNotNull(info))
    }

    /** Enables OK for a non-blank name that has no [error]. */
    private class NameValidator(private val project: Project, val renaming: LayoutRef?) : InputValidator {
        /** Set by the dialog once constructed; the scope can depend on the dialog's own components. */
        var scope: () -> LayoutScope = { renaming?.scope ?: LayoutScope.GLOBAL }

        private val maxLength get() = Registry.intValue("ide.max.tool.window.layout.name.length", 50)

        fun error(name: String): String? = when {
            name.length > maxLength -> LayoutManagerBundle.message("dialog.layout.name.too.long", maxLength)
            renaming != null && name != renaming.name && exists(name) -> LayoutManagerBundle.message("dialog.layout.already.exists")
            else -> null
        }

        fun warning(name: String): String? =
            if (renaming == null && name.isNotEmpty() && exists(name)) LayoutManagerBundle.message("dialog.save.warning.overwrite") else null

        private fun exists(name: String) = LayoutManager.exists(project, LayoutRef(scope(), name))

        override fun checkInput(inputString: String): Boolean {
            val name = inputString.trim()
            return name.isNotEmpty() && error(name) == null
        }

        override fun canClose(inputString: String) = checkInput(inputString)
    }
}
