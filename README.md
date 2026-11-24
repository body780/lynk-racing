# LYNK RACING

原生 Android 赛车风格仪表盘，支持 Android 6.0（API 23）及以上。应用免费、无广告、无需账号，不申请网络权限。卫星定位、传感器计算和 SQLite 记录均在设备上完成。

## 使用

- 首次使用时，启用系统定位并允许**精准位置**。到开阔处等待卫星锁定，离线搜星可能较慢。
- 仪表盘显示实时 GPS 车速和水平 G 值。点击「开始记录」后累计本次最高速度、行驶里程和峰值 G。结束记录或退出前台时保存到「驾驶记录」。
- 进入「加速测试」，确认车辆位于封闭场地，并固定手机。静止就绪后起步，到达 100 km/h 时自动保存。计时使用 GPS 样本插值，起步以越过 1 km/h 为准，读数精度取决于设备和信号。
- 传感器优先使用线性加速度与重力数据。设备缺少相应传感器时，使用普通加速度计估算。

## 构建与测试

使用 JDK 17、Android SDK Platform 35 与 Build Tools 35.0.0。

```sh
bash scripts/test.sh
./gradlew assembleDebug lintDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。每次推送或提交拉取请求都会触发 GitHub Actions，也可以手动运行。在成功运行的详情页下载 `LYNK-RACING-APK` 并解压，得到可安装的 APK 和 SHA-256 校验文件。自动构建使用临时调试签名，来自不同构建环境的安装包可能无法覆盖安装。
