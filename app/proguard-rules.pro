# The overlay calls these annotated methods by name from JavaScript.
-keepattributes SourceFile,LineNumberTable

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
