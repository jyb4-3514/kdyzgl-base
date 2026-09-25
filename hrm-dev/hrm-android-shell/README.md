# hrm-android-shell · 安卓 H5 壳（两个 APK）

移动端载体：H5 套一层 Android WebView 外壳。**同一工程经 `productFlavors` 产出两个可区分的 APK**
（ADR-SM-07 / split-plan D5 = 「两个 APK」，非单壳双入口）：

| 壳 | 应用名 | applicationId（占位） | H5 入口子路径 |
| - | - | - | - |
| `staff` | 驿站助手 | `com.example.hrmwebview.staff` | `/staff/` |
| `boss` | 驿站精灵 | `com.example.hrmwebview.boss` | `/boss/` |

`com.example` 是安卓官方保留的示例域，不含真实公司域名；**真实包名由用户后续裁定**。
两壳**共用同一份 Java**（`MainActivity` / `HrmJsBridge` 逐字不变），仅包名 / 应用名 / H5 入口区分，
**主题与图标沿用现有**（U-A 未裁定，不做视觉分叉）。

> **状态：未编译验证。** 本机无 JDK / Android SDK / adb / gradle，工程仅完成骨架与代码编写，
> 未做过任何编译、安装、真机运行。**运行期验收（壳加载 / 桥接 / HTTPS / 混合内容 / WebView 缩放缓存）
> 本批未运行，收敛到具备 Android SDK 的构建环境**；本工程**不声称可交付**。构建步骤见 [BUILD.md](./BUILD.md)。

## 1. 薄壳决策

安卓侧只承担三件事：**加载 H5、提供原生能力桥接、处理返回键与状态栏**。
不做原生页面、不做原生缓存、不做离线包管理（Demo 阶段）。

理由：三期移动端选型（小程序 / 安卓 H5 壳）尚未定稿，壳做厚会把未定稿的选型提前固化；
薄壳还意味着 H5 迭代不需要重新发版。

**两壳而非单壳双入口**：单壳运行时切 URL 要求壳内自建品牌 UI，且「同一 APK 内两端登录态混登」
带来审计复杂度，与端准入 fail-closed 的产品意图相左（详见 ADR §3.4 备选栏）。

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
    ├── build.gradle                      # 双 flavor + H5_URL 按变体注入
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml        # applicationId / label 由 flavor 覆盖，无需分叉
        │   ├── java/com/example/hrmwebview/
        │   │   ├── MainActivity.java
        │   │   └── HrmJsBridge.java
        │   ├── assets/h5/                  # 离线包放置位（构建产物拷贝，gitignore）
        │   └── res/
        │       ├── layout/activity_main.xml
        │       ├── xml/network_security_config.xml
        │       └── values/{strings.xml, themes.xml}
        ├── staff/res/values/strings.xml    # 驿站助手（覆盖 app_name）
        └── boss/res/values/strings.xml     # 驿站精灵（覆盖 app_name）
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

`BuildConfig.H5_URL` **不再硬编码**，改为在 `app/build.gradle` 的 `androidComponents.onVariants`
中按变体注入（`variant.buildConfigFields.put('H5_URL', ...)`），地址来源见 `resolveH5Url`：

| 变体 | H5 入口（占位默认） | 说明 |
| ---- | ---- | ---- |
| `staffDebug` | `http://10.0.2.2:5189/staff/` | `10.0.2.2` 是安卓模拟器访问宿主机的**约定地址**，非真实 IP |
| `staffRelease` | `https://example.invalid/staff/` | `example.invalid` 为保留域（RFC 2606），占位 |
| `bossDebug` | `http://10.0.2.2:5190/boss/` | 端口与 `apps/boss-h5`(5190) 对齐 |
| `bossRelease` | `https://example.invalid/boss/` | 占位 |

**取值优先级：`-P` 构建参数 > `local.properties`（不入库）> 上表占位默认。** 键名按变体命名：

```bash
# 真实地址只在命令行 / 本地 local.properties 出现，仓库内不落任何真实域名、IP、密钥
./gradlew assembleStaffRelease -Ph5UrlStaffRelease=https://<真实域名>/staff/
```

`local.properties.example` 已给出四个键的注释样例（值为占位符）。明文流量只对 `10.0.2.2` / `localhost`
放行（见 `res/xml/network_security_config.xml`），其余强制 HTTPS。

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
| Q13 | 真实包名（`com.example.hrmwebview.staff` / `.boss` 为**占位**） | **待用户裁定**；裁定前不得填真实域名/品牌 |
| Q14 | 两壳图标 | **沿用现有**（无自定义图标资源，用系统默认）；U-A 未裁定，**禁止视觉分叉** |
| Q15 | `androidComponents.onVariants` 注入 `H5_URL` 的语法 | **未编译验证**（本机无 SDK），需在具 SDK 环境首跑确认 |

> **待办（运行期，未执行）**：壳加载、`HrmBridge`/`HrmShell` 桥接通、HTTPS 强制与混合内容拦截、
> WebView 缩放与缓存行为（U-3）—— 本批**未运行**，收敛到具备 Android SDK 的构建环境。