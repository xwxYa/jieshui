<p align="center">
  <img src="docs/icon.png" width="150">
</p>

# 接水

有的学校需用「胖乖生活」App 接水，但这个 App 启动慢、功能杂、广告多。

而很多人只是想接杯水。

这个项目只干两件事：**接水、看账单。**

<p align="center">
  <img src="docs/shots/01-登录页.png" width="23%">
  <img src="docs/shots/02-设备列表.png" width="23%">
  <img src="docs/shots/04-已解锁.png" width="23%">
  <img src="docs/shots/06-订单.png" width="23%">
</p>

## 构建

纯原生 Kotlin + Jetpack Compose 实现，需要 JDK 17、Android SDK 34、Gradle 8.9。

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

单元测试：

```bash
./gradlew testDebugUnitTest
```

## 关于

接口通过反编译官方 App 的 H5 部分得到的，签名密钥来自 [Night-stars-1/wash](https://github.com/Night-stars-1/wash) 。
