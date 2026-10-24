package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

/** A destination owns its controls, jobs and back actions, independently of other visible pages. */
abstract class NativePage : Fragment() {
    val uiContext: AppCompatActivity by lazy { requireActivity() as AppCompatActivity }
    val intent: Intent get() = Intent().putExtras(arguments ?: Bundle())
    val pageRoot: FrameLayout by lazy {
        FrameLayout(uiContext).apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                // The scrolling scaffold places its header under the status strip itself.
                // Simple form pages need safe content bounds inside their full-screen surface.
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                if (getChildAt(0) is AgoraSettingsScaffold) setPadding(0, 0, 0, 0)
                else setPadding(bars.left, bars.top, bars.right,
                    maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom))
                insets
            }
        }
    }
    val onBackPressedDispatcher = OnBackPressedDispatcher { finish() }
    val contentResolver get() = uiContext.contentResolver
    val cacheDir get() = uiContext.cacheDir
    val packageName get() = uiContext.packageName
    val packageManager get() = uiContext.packageManager
    val theme get() = uiContext.theme
    val currentFocus: View? get() = pageRoot.findFocus()
    fun getSharedPreferences(name: String, mode: Int) = uiContext.getSharedPreferences(name, mode)
    fun getSystemService(name: String): Any? = uiContext.getSystemService(name)
    fun startService(intent: Intent) = uiContext.startService(intent)
    fun stopService(intent: Intent) = uiContext.stopService(intent)
    fun runOnUiThread(action: () -> Unit) = uiContext.runOnUiThread(action)
    fun unregisterReceiver(receiver: android.content.BroadcastReceiver) = uiContext.unregisterReceiver(receiver)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = pageRoot

    fun setContentView(layout: Int) = setContentView(layoutInflater.inflate(layout, pageRoot, false))
    fun setContentView(view: View) {
        pageRoot.removeAllViews()
        pageRoot.addView(view, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.requestApplyInsets(pageRoot)
    }
    @Suppress("UNCHECKED_CAST")
    fun <T> findViewById(id: Int): T = pageRoot.findViewById<View>(id) as T

    override fun startActivity(intent: Intent) {
        if (!(requireActivity() as NativeNavigatorOwner).pageNavigator.open(intent)) super.startActivity(intent)
    }
    fun finish() = (requireActivity() as NativeNavigatorOwner).pageNavigator.back()

    /** Keep scroll owners only. Recreated destinations do not retain stale controls or editor drafts. */
    fun captureScroll(): Bundle = Bundle().also { state ->
        walk(pageRoot, "root") { view, path ->
            when (view) {
                is NestedScrollView -> state.putInt("$path/y", view.scrollY)
                is RecyclerView -> state.putParcelable("$path/list", view.layoutManager?.onSaveInstanceState())
            }
        }
    }
    fun restoreScroll(state: Bundle) {
        walk(pageRoot, "root") { view, path ->
            when (view) {
                is NestedScrollView -> view.scrollTo(0, state.getInt("$path/y"))
                is RecyclerView -> view.layoutManager?.onRestoreInstanceState(state.getParcelable<Parcelable>("$path/list"))
            }
        }
    }
    private fun walk(view: View, path: String, action: (View, String) -> Unit) {
        action(view, path)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), "$path/$i", action)
    }
}
