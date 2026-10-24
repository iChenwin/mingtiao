# 验证

## 自动检查

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 scripts/check_permissions.py
```

单元测试覆盖易误点的非广告标签、精确与显式包含选择器、完整资源 ID、动作白名单、触发条件、坐标与时间范围。它们不模拟 Android 无障碍服务，不能作为真机功能成功证据。

权限检查读取最终合并 Manifest，拒绝任何新增 uses-permission，核对服务绑定权限、导出状态和文件备份关闭配置。CI 发布 debug APK，未签名 release 可自行构建和签名。

## 真机验收（均待执行）

目标优先：vivo X200 Pro mini / OriginOS 6 / Android 16，见 VIVO_ORIGINOS6.md。

1. 全新安装：总开关关闭、预览开启、无授权应用，任何其他应用都不应被点击。
2. 授权测试应用：开屏展示「跳过 5s」，预览只记命中，不点击；关闭预览后重新进入，应点击一次。
3. 输入框或密码节点内有“跳过”：不点击、不作为触发条件。
4. 不授权的应用、系统权限页、包安装器、本应用均不执行规则。
5. 同一会话连续显示弹窗：最多 3 次尝试、间隔不小于 900ms；每条成功规则不重复执行。
6. 进入应用超过内置 8 秒后出现跳过：内置不处理；显式 popup 规则仍受会话上限约束。
7. ID 规则只接受完整 ID，Activity 限定需验证真实事件类名。
8. 坐标/返回需要触发节点；切换前台、锁屏、暂停后不允许残留操作。
9. 合法导入先确认再替换；错误版本、损坏 JSON、超大文件/规则数、非法动作与坐标均拒绝，原规则保留。
10. 导入之后总开关关闭、预览开启、应用授权名单不增加。
11. 导出只含规则；清空后统计和日志为空；日志没有实际界面文本。
12. 安装包用 apkanalyzer/aapt 验证没有 INTERNET；按需使用独立网络抓包补充验证。

## 本次执行结果（2026-10-24）

- JDK 17 / AGP 8.10.1 / Gradle 8.11.1 / SDK 36 构建：debug 与未签名 release 成功。
- 8 项 JVM 单元测试：全部通过。
- Android Lint：0 错误；2 条非阻断警告（详见实际 Lint 报告）。
- 最终合并 Manifest 权限检查：通过，无 uses-permission。
- Android 16 模拟器安装因宿主可用磁盘不足中止，未启动模拟器，**未执行运行时烟测**。仓库保留可复现夹具和脚本，不把脚本存在当作测试通过。
- vivo / OriginOS 真机：未执行。

模拟器具备后：

```sh
./gradlew :app:assembleDebug :fixture:assembleDebug
python3 scripts/emulator_smoke.py --adb "$ANDROID_HOME/platform-tools/adb" --serial emulator-5554
```

脚本仅接受 emulator- 前缀的临时设备，安装测试包并改写此模拟器内明跳的 debug 配置及无障碍测试设置。请勿用于有个人数据的日用模拟器。检查暂停、未授权、预览、内置跳过、非广告文字和自定义弹窗 6 个场景。
