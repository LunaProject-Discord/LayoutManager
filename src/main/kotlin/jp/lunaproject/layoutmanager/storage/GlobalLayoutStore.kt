package jp.lunaproject.layoutmanager.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Layouts shared by every solution. Stored in `options/layoutManager.xml`. */
@Service(Service.Level.APP)
@State(name = "LayoutManagerGlobalLayouts", storages = [Storage("layoutManager.xml")])
class GlobalLayoutStore : LayoutStore() {
    companion object {
        fun getInstance(): GlobalLayoutStore = service()
    }
}
