plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.qiekj.hotwater"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qiekj.hotwater"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 只留 arm64-v8a。
        // ML Kit 那个 libbarhopper_v3.so 带了四种架构，合起来 17.3MB ——
        // arm64 只占 4.4MB，剩下 13MB 是这台手机一辈子用不上的。
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            /*
             * 用同一份 debug keystore 签 release。
             *
             * 为什么不用专门的 release keystore：换密钥就装不上
             * （INSTALL_FAILED_UPDATE_INCOMPATIBLE），只能卸载重装，
             * 而卸载会清掉登录状态和设备列表 —— 而短信验证码有每日额度，
             * 撞光了当天就登不回去。自用 App，不值当。
             *
             * 这里的密码是 Android 的公开默认值（android / androiddebugkey），
             * 不是秘密。keystore 本体在 ~/.android/ 下，不进仓库。
             *
             * release 跟 debug 的实际区别：debuggable 标志没了 ——
             * 于是 WebView 的远程调试口自动关掉（见 MainActivity 里
             * WebContentsDebuggingEnabled 的判断）。
             */
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes.addAll(listOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES"))
    }

    lint {
        /*
         * lint 的 InvalidFragmentVersionForActivityResult 是个误报。
         *
         * 它看到 registerForActivityResult 就要求把 Fragment 升到 1.3.0+，
         * 但那是给「在 Fragment 里注册」的场景用的。这个 App 压根没有 Fragment
         * —— 就一个 ComponentActivity 包着 WebView。为了消掉这条警告
         * 去引一个用不上的依赖，反而把包撑大。
         *
         * 它只在 release 构建里报（lintVitalRelease），debug 不拦，
         * 所以之前一直没暴露。
         */
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    // 界面在 assets/app.html 里，这边只有 WebView + 网络，不需要 Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // 本地网页资源要用 https 源加载，否则网页里调不起相机
    implementation("androidx.webkit:webkit:1.11.0")

    // 扫码解码。机器上的码是蓝色背光屏上的低对比图，普通解码器读不出来，用 Google 这个
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
}
