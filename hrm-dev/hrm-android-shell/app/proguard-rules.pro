# JS 桥方法靠方法名反射调用，混淆改名会直接打断 H5 通信（官方推荐的 keep 规则）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 桥类整体保留，避免类名被改写后 WebView 注入名与实际类不匹配
-keep class com.example.hrmwebview.HrmJsBridge { *; }