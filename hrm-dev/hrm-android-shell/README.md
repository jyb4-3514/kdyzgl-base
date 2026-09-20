# hrm-android-shell · 安卓 H5 壳

三端演示 Demo 的移动端载体：H5（`hrm-dev/hrm-demo`）套一层 Android WebView 外壳。
包名占位 `com.example.hrmwebview`（`com.example` 是安卓官方保留的示例域，不含真实公司域名）。

> **状态：未编译验证。** 本机无 JDK / Android SDK / adb / gradle，工程仅完成骨架与代码编写，
> 未做过任何编译、安装、真机运行。构建步骤见 [BUILD.md](./BUILD.md)。

## 1. 薄壳决策

安卓侧只承担三件事：**加载 H5、提供原生能力桥接、处理返回键与状态栏**。
不做原生页面、不做原生缓存、不做离线包管理（Demo 阶段）。

理由：三期移动端选型（小程序 / 安卓 H5 壳）尚未定稿，壳做厚会把未定稿的选型提前固化；
薄壳还意味着 H5 迭代不需要重新发版。

## 2. 目录结构

```text
hrm-android-shell/
├── README.md
├── BUILD.md
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── local.properties.example
├── .gitignore
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/hrmwebview/
        │   ├── MainActivity.java
        │   └── HrmJsBridge.java
        ├── assets/h5/                      # 离线包放置位（构建产物拷贝，gitignore）
        └── res/
            ├── layout/activity_main.xml
            ├── xml/network_security_config.xml
            └── values/{strings.xml, themes.xml}
```

## 3. 桥接约定速查

### 3.1 H5 → 原生：`window.HrmBridge`

注入名 `HrmBridge`（见 `HrmJsBridge.BRIDGE_NAME`）。**入参一律用字符串**：`addJavascriptInterface`
只可靠支持基本类型，复杂结构走 JSON 字符串。

| 方法 | 签名 | 说明 | 实现位置 |
| ---- | ---- | ---- | ---- |
| `getDeviceInfo` | `(): string` | 返回 JSON `{platform, appVersion, screenWidth, screenHeight, statusBarHeight}` | `HrmJsBridge.getDeviceInfo` |
| `setStatusBarStyle` | `(json: string)` | `{"dark":true}` 控制状态栏图标深浅 | `HrmJsBridge.setStatusBarStyle` |
| `toast` | `(text: string)` | 原生 Toast（H5 弹层被 WebView 遮挡时兜底） | `HrmJsBridge.toast` |
| `close` | `()` | 关闭当前 Activity | `HrmJsBridge.close` |

### 3.2 原生 → H5：`window.HrmShell`

H5 在 App 挂载时注册。所有调用统一走 `WebView.evaluateJavascript(...)`（异步），
**不使用** `loadUrl("javascript:...")` —— 后者拿不到返回值、会污染历史栈。

| 方法 | 签名 | 说明 | 调用位置 |
| ---- | ---- | ---- | ---- |
| `onBackPressed` | `(): boolean` | `true` = H5 已消费返回；`false` = 允许原生退出 | `MainActivity.interceptBackPressed` |
| `onResume` | `()` | 页面恢复（刷新未读计数等） | `MainActivity.onResume` |
| `setStatusBarHeight` | `(px: number)` | 注入状态栏高度，H5 写入 CSS 变量 `--status-bar-height` | `HrmJsBridge.pushStatusBarHeight` |

调用前统一做 `window.HrmShell && window.HrmShell.xxx && ...` 存在性判断，H5 未注册时不会报错。

### 3.3 返回键链路

```text
用户按返回键
  → OnBackPressedCallback 拦截（不调用 super）
  → evaluateJavascript(JS_BACK_QUERY)
  → H5 判断：有可返回路由 → history.back() 并回调 true
             已在首页    → 回调 false
  → 收到 true  → 不处理
     收到其它  → 首次 Toast「再按一次退出应用」，1.5s 内二次按下 finish()
```

### 3.4 状态栏链路

`WindowCompat.setDecorFitsSystemWindows(window, false)` → `ViewCompat.setOnApplyWindowInsetsListener`
取 `systemBars().top` 与 `displayCutout().top` 的**较大值**（刘海屏挖孔区可能大于状态栏区）
→ `evaluateJavascript("window.HrmShell.setStatusBarHeight(<px>)")`。

## 4. H5 地址配置

`BuildConfig.H5_URL` 由 `app/build.gradle` 的 `buildConfigField` 注入：

- debug：`http://10.0.2.2:5188/mobile.html`（`10.0.2.2` 是安卓模拟器访问宿主机的**约定地址**，非真实 IP）
- release：`https://example.invalid/mobile.html`（`example.invalid` 为保留域，占位）

仓库内**不出现任何真实 IP、域名、密钥**。生产地址应由构建参数传入。

明文流量只对 `10.0.2.2` / `localhost` 放行（见 `res/xml/network_security_config.xml`），其余强制 HTTPS。

## 5. 安全约定

- JS 桥只注入必要且无副作用的方法，不暴露文件系统读写、任意 URL 加载
- 明文放行范围最小化，上线前删除全部明文项
- `local.properties` / `*.jks` / `local.properties` 已 gitignore，不进仓库
- `HrmBridge` / `HrmShell` 命名刻意避开 `Android` / `JSBridge` 等通用名：`addJavascriptInterface`
  的注入名是全局的，重名会被第三方 SDK 或系统直接覆盖

## 6. 已知待确认项

| # | 事项 | 状态 |
| - | ---- | ---- |
| Q10 | AGP / Gradle / JDK 版本组合 | **部分查证**，见 BUILD.md 第 1 节；补丁号未定 |
| Q11 | `setStatusBarStyle` 的 `dark` 语义映射方向（dark=true 表示图标深色还是深色主题） | **待确认**，设计文档 8.2 未写明，需与 `hrm-demo` 侧调用方对齐 |
| Q12 | `displayCutout` 在刘海屏上的实际取值 | **未验证**，需真机/模拟器实测 |