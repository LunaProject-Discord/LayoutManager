package jp.lunaproject.layoutmanager.model

/** Where a saved layout lives. */
enum class LayoutScope {
    /** Shared by every solution opened in this IDE. */
    GLOBAL,

    /** Stored in the solution's workspace and only visible there. */
    SOLUTION,
}

/** Identifies a saved layout by scope and name. */
data class LayoutRef(val scope: LayoutScope, val name: String)
