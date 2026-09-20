package com.example.hrmwebview;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 安卓 H5 壳的宿主页：只做三件事——加载 H5、注入 JS 桥、处理返回键与状态栏。
 * 壳保持「薄」，业务一律不落原生，避免把尚未定稿的移动端选型提前固化。
 */
public class MainActivity extends AppCompatActivity {

    // 双击退出窗口：1.5s 内的第二次返回键才真正退出，防止误触
    private static final long EXIT_INTERVAL_MS = 1500L;

    private WebView webView;
    private HrmJsBridge hrmBridge;
    private long lastBackTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 内容延伸到状态栏下方，由 H5 按 --status-bar-height 自行避让，壳不额外占位
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        initWebView();
        applyStatusBarInsets();
        interceptBackPressed();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        webView = findViewById(R.id.web_view);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // Demo 的写操作全部落 H5 的 localStorage，不开 DOM Storage 会导致刷新即丢数据
        settings.setDomStorageEnabled(true);

        hrmBridge = new HrmJsBridge(this, webView);
        // 注入名在 WebView 内是全局的，统一取 HrmJsBridge.BRIDGE_NAME，避免各处硬编码写歪
        webView.addJavascriptInterface(hrmBridge, HrmJsBridge.BRIDGE_NAME);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 同主机跳转留在壳内，外链交给系统浏览器，避免 H5 被顶出 WebView
                return !isSameHost(request.getUrl());
            }
        });

        webView.loadUrl(BuildConfig.H5_URL);
    }

    /** 目标地址与 H5_URL 同主机即视为壳内路由 */
    private boolean isSameHost(Uri target) {
        if (target == null) {
            return false;
        }
        String baseHost = Uri.parse(BuildConfig.H5_URL).getHost();
        return baseHost != null && baseHost.equalsIgnoreCase(target.getHost());
    }

    /**
     * 状态栏链路：取 systemBars 与 displayCutout 的较大值作为安全区顶部高度，
     * 注入 H5 后由 H5 写入 CSS 变量 --status-bar-height。
     */
    private void applyStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            // 刘海屏的挖孔区域可能大于状态栏区域，取大值才能保证内容不被遮挡
            int cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top;
            hrmBridge.setStatusBarHeightPx(Math.max(statusBar, cutout));
            hrmBridge.pushStatusBarHeight();
            // 原样返回，不消费 insets，保证后续分发不被截断
            return insets;
        });
    }

    /** 返回键链路：先问 H5 是否消费，未消费则走双击退出 */
    private void interceptBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                webView.evaluateJavascript(HrmJsBridge.JS_BACK_QUERY, consumed -> {
                    // H5 回 true 表示已自行返回上一级；其余情况（false / 未注册）按壳退出处理
                    if (Boolean.parseBoolean(consumed)) {
                        return;
                    }
                    confirmExit();
                });
            }
        });
    }

    private void confirmExit() {
        long now = System.currentTimeMillis();
        if (now - lastBackTime < EXIT_INTERVAL_MS) {
            finish();
            return;
        }
        lastBackTime = now;
        Toast.makeText(this, R.string.exit_confirm, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 通知 H5 恢复（刷新未读计数等），壳只转发不参与业务判断
        if (webView != null) {
            webView.evaluateJavascript(HrmJsBridge.JS_RESUME_NOTIFY, null);
        }
    }

    @Override
    protected void onDestroy() {
        // 先断桥再销毁，避免 WebView 的异步回调打到已解绑的 Activity
        if (webView != null) {
            webView.removeJavascriptInterface(HrmJsBridge.BRIDGE_NAME);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}