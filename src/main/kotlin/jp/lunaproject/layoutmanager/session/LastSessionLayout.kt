package jp.lunaproject.layoutmanager.session

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity
import jp.lunaproject.layoutmanager.LayoutManager
import jp.lunaproject.layoutmanager.settings.LayoutManagerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<LastSessionRecorder>()

/** Records the layout of a solution right before it is closed and its workspace is saved. */
class LastSessionRecorder : ProjectCloseListener {
    override fun projectClosingBeforeSave(project: Project) {
        if (!LayoutManagerSettings.getInstance().restoreOnStartup) return
        if (!ApplicationManager.getApplication().isDispatchThread) {
            LOG.warn("projectClosingBeforeSave called outside EDT; last session layout not recorded")
            return
        }
        runCatching { LayoutManager.rememberLastSession(project) }
            .onFailure { LOG.warn("Failed to record last session layout", it) }
    }
}

/** Applies the recorded layout when a solution is opened. */
class LastSessionRestorer : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!LayoutManagerSettings.getInstance().restoreOnStartup) return
        withContext(Dispatchers.EDT) {
            runCatching { LayoutManager.restoreLastSession(project) }
                .onFailure { LOG.warn("Failed to restore last session layout", it) }
        }
    }
}
