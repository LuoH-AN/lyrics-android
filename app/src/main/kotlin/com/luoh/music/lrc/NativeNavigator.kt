package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle

interface NativeNavigatorOwner { val pageNavigator: NativeNavigator }

/** One Fragment/View host for all in-app destinations; Activities remain OS entry points only. */
class NativeNavigator(
    private val activity: AppCompatActivity,
    private val parent: ViewGroup,
    private val overlaysHome: Boolean,
    state: Bundle? = null
) {
    private val stack = mutableListOf<Bundle>()
    // Agora retains scroll by route path for the lifetime of an open settings session.
    private val sessionScroll = object : LinkedHashMap<String, Bundle>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bundle>?) = size > 64
    }
    private val pages = mutableMapOf<View, NativePage>()
    private var overlay: AgoraNavigationOverlay? = null
    private var standaloneHost: AgoraPageHost? = null
    private val host: AgoraPageHost? get() = overlay?.host ?: standaloneHost
    val currentPage: NativePage? get() = host?.targetView?.let(pages::get)
    val depth: Int get() = stack.size
    val isOpen: Boolean get() = stack.isNotEmpty()
    var onPresentationChanged: (Boolean) -> Unit = {}
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (overlay?.closing == true) return
            currentPage?.onBackPressedDispatcher?.onBackPressed() ?: back()
        }
    }

    init {
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        // Restore destinations into new containers; transient outgoing fragments have no navigation ownership.
        val restored = activity.supportFragmentManager.fragments.filter { it.tag?.startsWith("native/") == true }
        if (restored.isNotEmpty()) activity.supportFragmentManager.beginTransaction().apply {
            restored.forEach { remove(it) }
        }.commitNow()
        state?.getParcelableArrayList<Bundle>("scroll_session")?.forEach { entry ->
            val path = entry.getString("path") ?: return@forEach
            entry.getBundle("scroll")?.let { sessionScroll[path] = it }
        }
        state?.getParcelableArrayList<Bundle>("stack")?.let { stack.addAll(it) }
        if (stack.isNotEmpty()) {
            ensureHost()
            show(stack.last(), forward = true, animate = false)
            overlay?.enter(animate = false)
            backCallback.isEnabled = true
        }
    }

    fun open(intent: Intent): Boolean {
        val type = intent.component?.className ?: return false
        if (type !in destinationTypes) return false
        if (activity.supportFragmentManager.isStateSaved || overlay?.closing == true) return true
        retainCurrentScroll()
        val first = stack.isEmpty()
        val destination = Bundle().apply { putString("type", type); putBundle("args", intent.extras ?: Bundle()) }
        val route = routeKey(destination)
        destination.putString("path", (stack.lastOrNull()?.getString("path").orEmpty()) + "${route.length}:$route")
        destination.getString("path")?.let { sessionScroll[it] }?.let { destination.putBundle("scroll", it) }
        stack.add(destination)
        if (first) onPresentationChanged(true)
        ensureHost()
        show(destination, forward = true, animate = !first)
        if (first) overlay?.enter()
        backCallback.isEnabled = true
        return true
    }

    fun back() {
        if (overlay?.closing == true || stack.isEmpty() || activity.supportFragmentManager.isStateSaved) return
        if (stack.size > 1) {
            retainCurrentScroll()
            stack.removeAt(stack.lastIndex)
            show(stack.last(), forward = false)
        } else if (overlaysHome) dismissOverlay() else activity.finish()
    }

    private fun dismissOverlay() {
        if (overlay?.closing == true || activity.supportFragmentManager.isStateSaved) return
        overlay?.exit {
            clearPages()
            overlay?.let(parent::removeView)
            overlay?.dispose(); overlay = null
            stack.clear()
            sessionScroll.clear()
            backCallback.isEnabled = false
            parent.getChildAt(0)?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            onPresentationChanged(false)
        }
    }

    private fun routeKey(destination: Bundle): String {
        val args = destination.getBundle("args") ?: Bundle()
        return destination.getString("type").orEmpty() + args.keySet().sorted().joinToString("") { key ->
            val value = args.get(key)?.toString().orEmpty()
            "${key.length}:$key${value.length}:$value"
        }
    }

    private fun retainCurrentScroll() {
        val destination = stack.lastOrNull() ?: return
        val page = currentPage ?: return
        // Rapid Back before the restored page's first layout must not replace its saved offset with zero.
        val scroll = if (!page.pageRoot.isLaidOut) destination.getBundle("scroll") ?: page.captureScroll()
            else page.captureScroll()
        destination.putBundle("scroll", scroll)
        destination.getString("path")?.let { sessionScroll[it] = scroll }
    }

    private fun ensureHost() {
        if (host != null) return
        if (overlaysHome) {
            parent.getChildAt(0)?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            overlay = AgoraNavigationOverlay(activity) { dismissOverlay() }.also { parent.addView(it, ViewGroup.LayoutParams(-1, -1)) }
        } else standaloneHost = AgoraPageHost(activity).also { parent.addView(it, ViewGroup.LayoutParams(-1, -1)) }
        host!!.onDiscard = { surface ->
            pages.remove(surface)?.let { page ->
                activity.supportFragmentManager.beginTransaction().remove(page).commitNowAllowingStateLoss()
            }
        }
    }

    private fun show(destination: Bundle, forward: Boolean, animate: Boolean = true) {
        val page = requireNotNull(newPage(requireNotNull(destination.getString("type"))))
        page.arguments = destination.getBundle("args") ?: Bundle()
        destination.getParcelable<Fragment.SavedState>("saved")?.let(page::setInitialSavedState)
        val surface = AgoraPageSurface(activity).apply { id = View.generateViewId() }
        val previous = currentPage
        host!!.show(surface, forward, animate)
        pages[surface] = page
        activity.supportFragmentManager.beginTransaction().apply {
            if (previous?.isAdded == true) setMaxLifecycle(previous, Lifecycle.State.STARTED)
            add(surface.id, page, "native/${surface.id}")
        }.commitNow()
        destination.getBundle("scroll")?.let { scroll -> surface.doOnPreDraw { page.restoreScroll(scroll) } }
    }

    fun saveState(): Bundle {
        retainCurrentScroll()
        currentPage?.let { page ->
            stack.last().putParcelable("saved", activity.supportFragmentManager.saveFragmentInstanceState(page))
        }
        return Bundle().apply {
            putParcelableArrayList("stack", ArrayList(stack))
            putParcelableArrayList("scroll_session", ArrayList(sessionScroll.map { (path, scroll) ->
                Bundle().apply { putString("path", path); putBundle("scroll", scroll) }
            }))
        }
    }

    /** Settings preserves its theme draft itself; other pages are recreated in place with saved state. */
    fun refreshTheme() {
        overlay?.refreshTheme()
        if (currentPage == null || currentPage is SettingsPage || activity.supportFragmentManager.isStateSaved) return
        currentPage?.let {
            stack.last().putBundle("scroll", it.captureScroll())
            stack.last().putParcelable("saved", activity.supportFragmentManager.saveFragmentInstanceState(it))
        }
        show(stack.last(), forward = true, animate = false)
    }

    private fun clearPages() {
        val fragments = pages.values.toList()
        pages.clear()
        if (fragments.isNotEmpty()) activity.supportFragmentManager.beginTransaction().apply {
            fragments.forEach { remove(it) }
        }.commitNowAllowingStateLoss()
    }
    fun dispose() { overlay?.dispose(); standaloneHost?.dispose(); pages.clear(); backCallback.remove() }

    private fun newPage(type: String): NativePage? = when (type) {
        SettingsActivity::class.java.name -> SettingsPage()
        TranslationSettingsActivity::class.java.name -> TranslationSettingsPage()
        ApiProfileManagerActivity::class.java.name -> ApiProfileManagerPage()
        LyricOffsetMemoryActivity::class.java.name -> LyricOffsetMemoryPage()
        LyricSourceManagerActivity::class.java.name -> LyricSourceManagerPage()
        PrivacyActivity::class.java.name -> PrivacyPage()
        CustomLyricsManagerActivity::class.java.name -> CustomLyricsManagerPage()
        CustomLyricsEditActivity::class.java.name -> CustomLyricsEditPage()
        else -> null
    }

    companion object {
        private val destinationTypes = setOf(SettingsActivity::class.java.name, TranslationSettingsActivity::class.java.name,
            ApiProfileManagerActivity::class.java.name, LyricOffsetMemoryActivity::class.java.name,
            LyricSourceManagerActivity::class.java.name, PrivacyActivity::class.java.name,
            CustomLyricsManagerActivity::class.java.name, CustomLyricsEditActivity::class.java.name)
    }
}

open class NativeNavigationActivity : AppCompatActivity(), NativeNavigatorOwner {
    override lateinit var pageNavigator: NativeNavigator
    override fun onCreate(state: Bundle?) {
        ThemePrefs.apply(getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).getString(ThemePrefs.KEY, ThemePrefs.FOLLOW))
        super.onCreate(state)
        ThemePrefs.updateSystemBars(this)
        pageNavigator = NativeNavigator(this, findViewById(android.R.id.content), false, state?.getBundle("navigation"))
        if (!pageNavigator.isOpen) pageNavigator.open(Intent(this, javaClass).putExtras(intent.extras ?: Bundle()))
    }
    override fun startActivity(intent: Intent) {
        if (!::pageNavigator.isInitialized || !pageNavigator.open(intent)) super.startActivity(intent)
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putBundle("navigation", pageNavigator.saveState())
        super.onSaveInstanceState(state)
    }
    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        theme.applyStyle(R.style.Theme_DesktopLyrics, true)
        pageNavigator.refreshTheme()
        ThemePrefs.updateSystemBars(this)
    }
    override fun onDestroy() { if (::pageNavigator.isInitialized) pageNavigator.dispose(); super.onDestroy() }
}
