package jp.lunaproject.layoutmanager.storage

import com.intellij.openapi.components.PersistentStateComponent
import org.jdom.Element

/**
 * Named layout snapshots persisted as XML.
 *
 * ```xml
 * <state>
 *   <savedLayout name="Debug"><layout>…</layout></savedLayout>
 * </state>
 * ```
 */
abstract class LayoutStore : PersistentStateComponent<Element> {
    private val layouts = LinkedHashMap<String, Element>()

    @Synchronized
    fun names(): List<String> = layouts.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)

    @Synchronized
    fun contains(name: String): Boolean = name in layouts

    @Synchronized
    fun get(name: String): Element? = layouts[name]?.clone()

    @Synchronized
    fun put(name: String, layout: Element) {
        layouts[name] = layout.clone()
    }

    @Synchronized
    fun remove(name: String): Boolean = layouts.remove(name) != null

    @Synchronized
    fun rename(oldName: String, newName: String): Boolean {
        if (oldName == newName) return oldName in layouts
        val layout = layouts.remove(oldName) ?: return false
        layouts[newName] = layout
        return true
    }

    @Synchronized
    override fun getState(): Element {
        val state = Element("state")
        writeExtraState(state)
        for ((name, layout) in layouts) {
            state.addContent(Element(SAVED_LAYOUT).setAttribute(NAME, name).addContent(layout.clone()))
        }
        return state
    }

    @Synchronized
    override fun loadState(state: Element) {
        layouts.clear()
        for (saved in state.getChildren(SAVED_LAYOUT)) {
            val name = saved.getAttributeValue(NAME) ?: continue
            val layout = saved.children.firstOrNull() ?: continue
            layouts[name] = layout.clone()
        }
        readExtraState(state)
    }

    protected open fun writeExtraState(state: Element) = Unit

    protected open fun readExtraState(state: Element) = Unit

    private companion object {
        const val SAVED_LAYOUT = "savedLayout"
        const val NAME = "name"
    }
}
