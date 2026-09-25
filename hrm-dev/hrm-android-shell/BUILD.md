# BUILD.md · 构建与验证

> **本机（开发机）无 JDK、无 Android SDK、无 adb、无 gradle，以下步骤全部未执行。**
> 本文档的每一条命令都**未编译验证**，请在补齐环境的机器上按下述顺序逐条验证后再标记完成。

## 0. 交付边界

| 类别 | 内容 | 状态 |
| ---- | ---- | ---- |
| 交付 | 工程骨架、`MainActivity` / `HrmJsBridge`、清单/布局/网络安全配置、**双 flavor 构建配置（两 APK）**、文档、`.gitignore` | 已交付（代码按约定编写） |
| 不交付 | APK、签名文件 `*.jks`、真实域名/IP、`local.properties`、真实包名 | 明确不做 |
| 未验证 | ① 能否编译通过 ② WebView 加载与 JS 桥通信 ③ 返回键链路与状态栏注入实际效果 ④ AGP/Gradle/JDK 版本组合是否匹配 ⑤ **两 flavor 是否确实产出两个可区分 APK** ⑥ HTTPS 强制与混合内容拦截 ⑦ WebView 缩放/缓存行为 | **全部未运行** |

> **运行期验收（壳加载 / 桥接 / HTTPS / 混合内容 / WebView 缩放缓存）本批未运行**，
> 收敛到具备 Android SDK 的构建环境；**本工程不声称可交付**（ADR §3.4 / §3.7 B6）。

## 1. 版本选型（Q10）

**已查证的官方结论**（来源：`developer.android.com/build/releases/about-agp` 的 AGP–Gradle 兼容性表；
`developer.android.com/build/jdks` 的 Java 版本说明）：

- **AGP 8.x 要求 JDK 17**（官方原文：Android Gradle 插件 8.x 需要 JDK 17）
- **AGP 8.7 要求 Gradle ≥ 8.9**（官方兼容性表）
- 官方兼容性表参考值：AGP 8.8 → Gradle ≥ 8.10.2；AGP 8.9 / 8.10 → Gradle ≥ 8.11.1；AGP 8.11–8.13 → Gradle ≥ 8.13

**本骨架采用的占位组合**（写在各配置文件中，附注释）：

| 项 | 本次占位值 | 落点 |
| -- | ---------- | ---- |
| JDK | 17 | `app/build.gradle` 的 `compileOptions` |
| AGP | `8.7.0` | `gradle.properties` 的 `agpVersion` |
| Gradle | `8.9` | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk | 35 | `app/build.gradle` |
| minSdk | 24 | `app/build.gradle` |

**【存疑】不确定项，必须复核后才能定稿：**

1. **AGP 补丁号未确认**。官方兼容性表只给到 `8.7` 这一档，未查到 `8.7.x` 的具体可用补丁版本。
   **验证方式**：在 `https://maven.google.com`（Google Maven）搜索 `com.android.tools.build:gradle`，
   取 8.7 系列的最新稳定补丁号，回填 `gradle.properties`。
2. **依赖坐标版本未验证**。`androidx.appcompat:appcompat:1.7.0`、`androidx.activity:activity:1.9.3`
   为占位值。**验证方式**：以 AndroidX 各库官方发布说明为准，或改用 BOM 统一版本。
3. **compileSdk / targetSdk = 35 未与已安装 SDK Platform 核对**。**验证方式**：`sdkmanager --list`
   查看已安装 Platform，确保与 `compileSdk` 一致，否则同步安装对应 Platform。
4. **三元组最终以官方兼容性表为准**，禁止凭记忆改写；若选定的 AGP 高于 8.7，需按上表同步抬高 Gradle 版本。

## 2. 环境补齐

1. **JDK 17**（AGP 8.x 硬性要求）。验证：`java -version` 输出 `17.x`。
2. **Android SDK**：Platform（对应 `compileSdk`）+ Build-Tools + Platform-Tools（含 adb）。
   验证：`sdkmanager --list` 与 `adb version`。
3. **生成 `local.properties`**（不入库）：

   ```properties
   sdk.dir=<本机 Android SDK 绝对路径>
   ```

   可从 `local.properties.example` 复制后修改。

4. **生成 Gradle Wrapper 脚本与 jar**。本仓库的 `gradle/wrapper/` 下**只有 `gradle-wrapper.properties`**，
   缺 `gradlew` / `gradlew.bat` / `gradle-wrapper.jar`（二进制，本机无 gradle 无法生成）。
   在具备 gradle 的机器上执行一次并提交脚本与 jar：

   ```bash
   gradle wrapper --gradle-version 8.9
   ```

## 3. 构建（两个 APK）

两壳由 `app/build.gradle` 的 `productFlavors { staff / boss }` × `buildTypes { debug / release }`
组成 4 个变体。**每个变体产出独立 APK**（两壳可同时安装，`applicationId` 不同）：

```bash
# 驿站助手（staff）/ 驿站精灵（boss）
./gradlew assembleStaffDebug     # → app/build/outputs/apk/staff/debug/app-staff-debug.apk
./gradlew assembleBossDebug      # → app/build/outputs/apk/boss/debug/app-boss-debug.apk
./gradlew assembleStaffRelease   # → app/build/outputs/apk/staff/release/app-staff-release.apk
./gradlew assembleBossRelease    # → app/build/outputs/apk/boss/release/app-boss-release.apk

# 一次性产出全部 4 个
./gradlew assemble

# 真实 H5 地址经构建参数注入（不入库）；键名 h5UrlStaffDebug / h5UrlStaffRelease / h5UrlBossDebug / h5UrlBossRelease
./gradlew assembleStaffRelease -Ph5UrlStaffRelease=https://<真实域名>/staff/
./gradlew assembleBossRelease  -Ph5UrlBossRelease=https://<真实域名>/boss/
```

安装到模拟器（两壳可并存）：

```bash
adb install -r app/build/outputs/apk/staff/debug/app-staff-debug.apk
adb install -r app/build/outputs/apk/boss/debug/app-boss-debug.apk
```

## 4. 运行前准备

对应端的 H5 需先起来（`hrm-dev/hrm-clients/apps/{staff-h5,boss-h5}`，端口 5189 / 5190），
且必须监听局域网地址以便模拟器/真机访问：

```bash
# 员工端壳
npm run dev -- --host   # apps/staff-h5（base '/staff/'，端口 5189）
# 管理端壳
npm run dev -- --host   # apps/boss-h5（base '/boss/'，端口 5190）
```

地址约定：

| 壳 | 场景 | H5 地址 | 额外操作 |
| - | ---- | ---- | ---- |
| staff | 模拟器 | `http://10.0.2.2:5189/staff/` | 无（默认 debug 占位值） |
| boss | 模拟器 | `http://10.0.2.2:5190/boss/` | 无（默认 debug 占位值） |
| 任一 | 真机 | `http://<本机局域网IP>:<端口>/<staff|boss>/` | 把该 IP 加入 `res/xml/network_security_config.xml` 明文放行域，并以 `-Ph5Url...` 注入 |
| 任一 | 生产 | `https://<真实域名>/<staff|boss>/` | 以 `-Ph5Url...` 注入或写入本地 `local.properties`（不入库） |

> 真机局域网 IP / 生产域名 **不写入仓库**，只在命令行 `-P` 或本地 `local.properties` 出现
> （`local.properties` 已 gitignore）。

## 5. 验证清单（逐条执行并记录结果）

| # | 检查项 | 期望 | 结果 |
| - | ---- | ---- | ---- |
| 1 | `./gradlew assembleStaffDebug` + `assembleBossDebug` | 编译通过，**各产出一个 APK** | 未运行 |
| 2 | 两个 APK 的 `applicationId` | 分别为 `com.example.hrmwebview.staff` / `.boss`，可同时安装 | 未运行 |
| 3 | 两个 APK 的应用名 | 分别为「驿站助手」/「驿站精灵」 | 未运行 |
| 4 | 启动 staff 壳 | 加载 `/staff/`，无白屏，H5 首页正常渲染 | 未运行 |
| 5 | 启动 boss 壳 | 加载 `/boss/`，无白屏，H5 首页正常渲染 | 未运行 |
| 6 | H5 调 `HrmBridge.getDeviceInfo()` | 返回合法 JSON，`statusBarHeight > 0`，字段名与 8.2 一致 | 未运行 |
| 7 | H5 调 `HrmBridge.toast('x')` | 弹出原生 Toast（验证桥回调切主线程正确） | 未运行 |
| 8 | 二级页按返回键 | 回到上一级（说明 `onBackPressed` 回 `true` 生效） | 未运行 |
| 9 | 首页按返回键 | 首次 Toast「再按一次退出应用」，1.5s 内二次按下退出 | 未运行 |
| 10 | 切后台再回前台 | `HrmShell.onResume()` 被调用（未读计数刷新） | 未运行 |
| 11 | 状态栏 | 内容不被状态栏/刘海遮挡，`--status-bar-height` 与实测一致 | 未运行 |
| 12 | H5 调 `HrmBridge.setStatusBarStyle('{"dark":true}')` | 状态栏图标颜色变化符合约定 | 未运行（见 Q11 语义待确认） |
| 13 | H5 调 `HrmBridge.close()` | Activity 关闭 | 未运行 |
| 14 | 刷新页面后数据仍存在 | `localStorage` 持久化正常（`setDomStorageEnabled(true)` 生效） | 未运行 |
| 15 | 同源跳转 / 外链 | 同源留在壳内，外链跳系统浏览器 | 未运行 |
| 16 | Release 包混淆后 | JS 桥方法名未被混淆（`proguard-rules.pro` 生效） | 未运行 |
| 17 | **HTTPS / 混合内容** | release 壳只加载 https；明文仅 debug 放行 `10.0.2.2`/`localhost` | 未运行 |
| 18 | **WebView 缩放 / 缓存（U-3）** | 缩放与缓存行为符合 UI 方案 R-2 口径 | 未运行（U-3 待核实） |

> 本表全部为**运行期验收**，**本批未运行**，收敛到具备 Android SDK 的构建环境；本工程不声称可交付。

## 6. 回滚

本工程为新增骨架，未接入任何现有工程。**回滚 = 反向 diff（ADR §3.7 B6 原文口径）+ 目录可整体剥离**：

| # | 动作 | 说明 |
| - | ---- | ---- |
| 1 | 还原 `app/build.gradle` | 去掉 `flavorDimensions` / `productFlavors` / `androidComponents` 块，恢复 `buildTypes` 内原 `H5_URL` 的 `buildConfigField`（debug `http://10.0.2.2:5188/mobile.html`、release `https://example.invalid/mobile.html`） |
| 2 | 删除 `app/src/staff/`、`app/src/boss/` | 两壳 flavor 源集（各含 `res/values/strings.xml`） |
| 3 | 还原 `app/src/main/res/values/strings.xml` | 去掉新增的注释行（取值未变） |
| 4 | 还原 `local.properties.example` | 去掉 `h5Url*` 注释块 |
| 5 | 还原 `README.md` / `BUILD.md` | 反向 diff；或整体删除 `hrm-dev/hrm-android-shell/` 目录 |

不影响 `hrm-admin`、`hrm-server`、`hrm-demo`、`hrm-clients`、`deploy`。**删文件属 C 档，须主智能体授权。**