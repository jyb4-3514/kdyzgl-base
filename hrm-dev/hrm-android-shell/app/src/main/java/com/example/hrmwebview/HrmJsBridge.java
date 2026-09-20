package com.example.hrmwebview;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * H5 → 原生的 JS 桥，注入名 {@link #BRIDGE_NAME}。
 * 只暴露必要且无副作用的能力，不开放文件读写与任意 URL 加载——JS 桥是壳最大的攻击面，
 * H5 一旦被注入脚本即可越权调用原生。
 * 入参一律用字符串：addJavascriptInterface 只可靠支持基本类型，复杂结构走 JSON 字符串。
 */
public class HrmJsBridge {

    /** 注入名，与 H5 约定一致；该名字在 WebView 内全局唯一，改名会直接打断通信 */
    public static final String BRIDGE_NAME = "HrmBridge";

    /** 返回键询问：H5 回 true 表示已消费返回事件 */
    public static final String JS_BACK_QUERY =
            "window.HrmShell && window.HrmShell.onBackPressed && window.HrmShell.onBackPressed()";

    /** 页面恢复通知 */
    public static final String JS_RESUME_NOTIFY =
            "window.HrmShell && window.HrmShell.onResume && window.HrmShell.onResume()";

    private final Activity activity;
    private final WebView webView;
    private int statusBarHeightPx;

    public HrmJsBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /** 宿主在 insets 变化时回填，供 getDeviceInfo 读取 */
    public void setStatusBarHeightPx(int px) {
        this.statusBarHeightPx = px;
    }

    /** 原生 → H5：注入状态栏高度（统一走 evaluateJavascript，不用 loadUrl("javascript:")） */
    public void pushStatusBarHeight() {
        webView.evaluateJavascript(
                "window.HrmShell && window.HrmShell.setStatusBarHeight && window.HrmShell.setStatusBarHeight("
                        + statusBarHeightPx + ")",
                null);
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("platform", "android");
            info.put("appVersion", BuildConfig.VERSION_NAME);
            info.put("screenWidth", activity.getResources().getDisplayMetrics().widthPixels);
            info.put("screenHeight", activity.getResources().getDisplayMetrics().heightPixels);
            info.put("statusBarHeight", statusBarHeightPx);
        } catch (JSONException ignored) {
            // JSONObject 写基本类型不会失败，这里仅满足受检异常签名
        }
        return info.toString();
    }

    @JavascriptInterface
    public void setStatusBarStyle(String json) {
        // 约定 {"dark":true} 表示状态栏图标取深色（页面顶部为浅色背景）
        // 【待确认】设计文档 8.2 未写明 dark 的映射方向，需与 hrm-demo 侧调用方对齐后再定
        boolean darkIcons = false;
        try {
            darkIcons = new JSONObject(json).optBoolean("dark", false);
        } catch (JSONException ignored) {
            // 入参非法时按浅色图标兜底，不向 H5 抛错打断调用链
        }
        boolean finalDarkIcons = darkIcons;
        activity.runOnUiThread(() -> WindowCompat.getInsetsController(
                        activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(finalDarkIcons));
    }

    @JavascriptInterface
    public void toast(String text) {
        // JS 桥回调不在 UI 线程，弹 Toast 必须切回主线程
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public void close() {
        // TODO(扩展): 若后续需要「关闭前询问 H5 是否保存草稿」，在 finish 前增加一次桥回调
        activity.runOnUiThread(activity::finish);
    }
}