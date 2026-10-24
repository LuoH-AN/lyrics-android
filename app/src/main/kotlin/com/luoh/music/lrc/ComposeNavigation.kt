package com.luoh.music.lrc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.*

/** Business destinations are independent of Activity, XML and animation ownership. */
data class ComposeRoute(
    val kind: String,
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val category: String = "",
)

class ComposeNavigator(saved: Bundle? = null, initial: ComposeRoute? = null) {
    private val stack = mutableStateListOf<ComposeRoute>()
    var isOpen by mutableStateOf(false); private set
    var forward by mutableStateOf(true); private set
    var wide by mutableStateOf(false)
    var onPresentationChanged: (Boolean) -> Unit = {}
    val route: ComposeRoute get() = stack.lastOrNull() ?: ComposeRoute("settings")
    val settingsSession: Boolean get() = stack.firstOrNull()?.kind in listOf("settings", "category")
    var selectedCategory by mutableStateOf("access"); private set
    private var retention by mutableStateOf(SettingsScrollRetention())

    init {
        val entries = saved?.getParcelableArrayList<Bundle>("routes")
        entries?.forEach { stack += ComposeRoute(it.getString("kind").orEmpty(),it.getString("id").orEmpty(),it.getString("title").orEmpty(),it.getString("artist").orEmpty(),it.getString("category").orEmpty()) }
        isOpen = saved?.getBoolean("open") == true
        selectedCategory = saved?.getString("selected") ?: "access"
        if (stack.isEmpty() && initial != null) { stack += initial; isOpen = true; if(initial.kind == "category") selectedCategory=initial.category }
    }
    fun navigate(destination: ComposeRoute) {
        if (!isOpen) {
            stack.clear(); stack += destination; retention = SettingsScrollRetention()
            selectedCategory = destination.category.ifBlank { "access" }
            forward = true; isOpen = true; onPresentationChanged(true)
        } else if (wide && settingsSession && destination.kind == "category") {
            selectedCategory = destination.category
            while(stack.size > 1) stack.removeAt(stack.lastIndex)
        } else if (route != destination) {
            forward = true; stack += destination
            if(destination.kind == "category") selectedCategory=destination.category
        }
    }
    fun open(intent: Intent): Boolean = routeForIntent(intent)?.let { navigate(it); true } ?: false
    fun back() {
        if (!isOpen) return
        if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } else dismiss()
    }
    fun dismiss() { if(isOpen) { isOpen = false; onPresentationChanged(false) } }
    fun onExitFinished() { if (!isOpen) stack.clear() }
    fun saveState() = Bundle().apply {
        putBoolean("open", isOpen);putString("selected",selectedCategory)
        putParcelableArrayList("routes",ArrayList(stack.map { r -> Bundle().apply { putString("kind",r.kind);putString("id",r.id);putString("title",r.title);putString("artist",r.artist);putString("category",r.category) } }))
    }
    @Composable internal fun Content() {
        CompositionLocalProvider(LocalSettingsScrollRetention provides retention) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val useTwoPane = maxWidth >= 840.dp && settingsSession
                SideEffect { wide = useTwoPane }
                BackHandler(isOpen) {
                    if(useTwoPane && route.kind in listOf("settings","category")) dismiss() else back()
                }
                if(useTwoPane) SettingsTwoPane(this@ComposeNavigator) else {
                    GuardedAnimatedContent(route,forward) { destination -> Destination(destination,::navigate,::back) }
                }
            }
        }
    }
}

internal fun routeForIntent(intent: Intent): ComposeRoute? = when(intent.component?.className?.substringAfterLast('.')) {
    "SettingsActivity" -> intent.getStringExtra("settings_section")?.let { ComposeRoute("category",category=it.lowercase()) } ?: ComposeRoute("settings")
    "TranslationSettingsActivity" -> ComposeRoute("translation")
    "ApiProfileManagerActivity" -> ComposeRoute("profiles")
    "LyricOffsetMemoryActivity" -> ComposeRoute("offsets")
    "LyricSourceManagerActivity" -> ComposeRoute("sources",id=intent.getStringExtra("source_key").orEmpty(),category=intent.getStringExtra("source_query").orEmpty())
    "PrivacyActivity" -> ComposeRoute("privacy")
    "CustomLyricsManagerActivity" -> ComposeRoute("custom")
    "CustomLyricsEditActivity" -> ComposeRoute("editor",id=intent.getStringExtra("custom_lyrics_id").orEmpty(),title=intent.getStringExtra("custom_lyrics_title").orEmpty(),artist=intent.getStringExtra("custom_lyrics_artist").orEmpty())
    else -> null
}

private data class SettingsCategory(val key:String,val title:String,val summary:String,val icon:ImageVector)
private val categories = listOf(
    SettingsCategory("access","权限与后台","通知使用权与后台运行",Icons.Default.Security),
    SettingsCategory("overlay","悬浮窗","布局、字号与显示位置",Icons.Default.PictureInPicture),
    SettingsCategory("sync","歌词同步","调整时间与管理同步记忆",Icons.Default.Sync),
    SettingsCategory("lyrics","歌词与翻译","显示方式、自定义歌词与歌词源",Icons.Default.Translate),
    SettingsCategory("appearance","应用外观","主题与主调颜色",Icons.Default.Palette),
    SettingsCategory("home","主页歌词","歌词对齐方式",Icons.Default.MusicNote),
    SettingsCategory("motion","主页歌词动效","滚动、错位、缩放与模糊",Icons.Default.Animation),
    SettingsCategory("about","关于","版本、更新与项目信息",Icons.Default.Info),
)

@Composable
private fun Destination(route:ComposeRoute,onNavigate:(ComposeRoute)->Unit,onBack:()->Unit) {
    when(route.kind) {
        "settings" -> CollapsingSettingsScaffold("设置",onBack) {
            SettingsGroup("桌面歌词",items=categories.map { category -> { CategoryRow(category,Modifier.clickable { onNavigate(ComposeRoute("category",category=category.key)) }) } })
        }
        "category" -> SettingsCategoryContent(route.category,onNavigate,onBack)
        else -> LibraryDestination(route,onNavigate,onBack)
    }
}

@Composable
private fun CategoryRow(category:SettingsCategory,modifier:Modifier = Modifier,selected:Boolean=false) {
    val color by animateColorAsState(if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, tween(250),label="settingsSelection")
    Surface(color=color,modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)) {
        SettingsItem(headlineContent={Text(category.title)},supportingContent={Text(category.summary)},leadingContent={Icon(category.icon,null)},trailingContent={Icon(Icons.AutoMirrored.Filled.ArrowForward,null)})
    }
}

@Composable
private fun SettingsTwoPane(navigator:ComposeNavigator) {
    val listState=rememberSettingsLazyListState()
    val parentPath=LocalSettingsScrollPath.current
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.width(400.dp).fillMaxHeight().clip(RoundedCornerShape(topEnd=24.dp,bottomEnd=24.dp)).background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(start=16.dp,end=20.dp,top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                CircularBackButton(navigator::dismiss,contentDescription="返回")
                Spacer(Modifier.width(14.dp));Text("设置",style=MaterialTheme.typography.headlineSmall)
            }
            LazyColumn(state=listState,contentPadding=PaddingValues(start=12.dp,end=12.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(categories,key={it.key}) { category -> SettingsNavigationItem(category,navigator.selectedCategory==category.key) { navigator.navigate(ComposeRoute("category",category=category.key)) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal=16.dp),contentAlignment=Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().widthIn(max=840.dp).fillMaxWidth().clipToBounds()) {
                Crossfade(navigator.selectedCategory,animationSpec=tween(250),label="settingsCategory") { category ->
                    val active=category==navigator.selectedCategory
                    CompositionLocalProvider(LocalSettingsScrollPath provides (parentPath+category),LocalSettingsScrollRetentionActive provides active) {
                        val paneRoute=if(navigator.route.kind in listOf("settings","category")) ComposeRoute("category",category=category) else navigator.route
                        GuardedAnimatedContent(paneRoute,navigator.forward) { destination ->
                            CompositionLocalProvider(LocalSettingsPaneBackButtonVisible provides (destination.kind!="category")) {
                                Destination(destination,navigator::navigate,navigator::back)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationItem(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 250),
        label = "settingsNavigationContainer",
    )
    val primaryContentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 250),
        label = "settingsNavigationPrimaryContent",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 250),
        label = "settingsNavigationIcon",
    )
    val supportingContentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 250),
        label = "settingsNavigationSupportingContent",
    )
    Surface(
        color = containerColor,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector=category.icon,contentDescription=null,modifier=Modifier.size(22.dp),tint=iconColor)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = primaryContentColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = category.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** External Android entry points share the same Compose navigation and theme. */
open class ComposeNavigationActivity : ComponentActivity() {
    private lateinit var navigator:ComposeNavigator
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        navigator=ComposeNavigator(savedInstanceState?.getBundle("navigation"),routeForIntent(intent) ?: ComposeRoute("settings"))
        setContent {
            LyricsComposeTheme {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    SettingsOverlayHost(navigator.isOpen,navigator::dismiss,onExitFinished={navigator.onExitFinished();if(!navigator.isOpen) finish()}) { navigator.Content() }
                }
            }
        }
    }
    override fun startActivity(intent:Intent) { if(!::navigator.isInitialized || !navigator.open(intent)) super.startActivity(intent) }
    override fun onSaveInstanceState(outState:Bundle) { outState.putBundle("navigation",navigator.saveState());super.onSaveInstanceState(outState) }
}
