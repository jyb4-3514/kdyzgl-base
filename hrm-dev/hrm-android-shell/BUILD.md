# BUILD.md · 构建与验证

> **本机（开发机）无 JDK、无 Android SDK、无 adb、无 gradle，以下步骤全部未执行。**
> 本文档的每一条命令都**未编译验证**，请在补齐环境的机器上按下述顺序逐条验证后再标记完成。

## 0. 交付边界

| 类别 | 内容 | 状态 |
| ---- | ---- | ---- |
| 交付 | 工程骨架、`MainActivity` / `HrmJsBridge`、清单/布局/网络安全配置、文档、`.gitignore` | 已交付（代码按约定编写） |
| 不交付 | APK、签名文件 `*.jks`、真实域名/IP、`local.properties` | 明确不做 |
| 未验证 | ① 能否编译通过 ② WebView 加载与 JS 桥通信 ③ 返回键链路与状态栏注入实际效果 ④ AGP/Gradle/JDK 版本组合是否匹配 | **全部未验证** |

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

## 3. 构建

```bash
# 调试包（H5_URL 指向 10.0.2.2:5188）
./gradlew assembleDebug

# 产物路径
# app/build/outputs/apk/debug/app-debug.apk
```

安装到模拟器：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. 运行前准备

H5 侧需先起来（另一工程 `hrm-demo`，端口 5188），且必须监听局域网地址以便模拟器/真机访问：

```bash
npm run dev -- --host
```

地址约定：

| 场景 | H5 地址 | 额外操作 |
| ---- | ---- | ---- |
| 模拟器 | `http://10.0.2.2:5188/mobile.html` | 无（默认 debug 值） |
| 真机 | `http://<本机局域网IP>:5188/mobile.html` | 需把该 IP 加入 `res/xml/network_security_config.xml` 的明文放行域，并改 `buildConfigField` |

> 真机局域网 IP **不写入仓库**，只在本地临时改 `buildConfigField`，或在补齐构建参数注入后再用。

## 5. 验证清单（逐条执行并记录结果）

| # | 检查项 | 期望 | 结果 |
| - | ---- | ---- | ---- |
| 1 | `./gradlew assembleDebug` | 编译通过，产出 APK | 未验证 |
| 2 | 启动应用 | 页面加载无白屏，H5 首页正常渲染 | 未验证 |
| 3 | H5 调 `HrmBridge.getDeviceInfo()` | 返回合法 JSON，`statusBarHeight > 0`，字段名与 8.2 一致 | 未验证 |
| 4 | H5 调 `HrmBridge.toast('x')` | 弹出原生 Toast（验证桥回调切主线程正确） | 未验证 |
| 5 | 二级页按返回键 | 回到上一级（说明 `onBackPressed` 回 `true` 生效） | 未验证 |
| 6 | 首页按返回键 | 首次 Toast「再按一次退出应用」，1.5s 内二次按下退出 | 未验证 |
| 7 | 切后台再回前台 | `HrmShell.onResume()` 被调用（未读计数刷新） | 未验证 |
| 8 | 状态栏 | 内容不被状态栏/刘海遮挡，`--status-bar-height` 与实测一致 | 未验证 |
| 9 | H5 调 `HrmBridge.setStatusBarStyle('{"dark":true}')` | 状态栏图标颜色变化符合约定 | 未验证（见 Q11 语义待确认） |
| 10 | H5 调 `HrmBridge.close()` | Activity 关闭 | 未验证 |
| 11 | 刷新页面后数据仍存在 | `localStorage` 持久化正常（`setDomStorageEnabled(true)` 生效） | 未验证 |
| 12 | 同源跳转 / 外链 | 同源留在壳内，外链跳系统浏览器 | 未验证 |
| 13 | Release 包混淆后 | JS 桥方法名未被混淆（`proguard-rules.pro` 生效） | 未验证 |

## 6. 回滚

本工程为新增骨架，未接入任何现有工程，回滚方式：删除 `hrm-dev/hrm-android-shell/` 目录即可，
不影响 `hrm-admin`、`hrm-server`、`hrm-demo`。