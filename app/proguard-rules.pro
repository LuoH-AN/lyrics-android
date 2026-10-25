# 开启 R8 后的保守规则。
# 默认规则已覆盖 Compose/协程/ML Kit 的 consumer rules；这里只补直接反射面。

# WebView JS 桥：JS 侧通过 window.LobstaNativeLyrics.<method> 按名调用，
# 方法名与注解都必须原样保留。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.luoh.music.lrc.LyricsJavascriptBridge { *; }

# QRC 解码器被 Kotlin 以静态方法调用，R8 可正常内联；无反射，无需额外规则。
