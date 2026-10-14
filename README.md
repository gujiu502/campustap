# CampusTap — 学生证门禁测试与卡片界面

更新：2026-10-15，当前版本 `0.4.0-access-ui`，本地 debug APK。**代码、模板 UI 已实现；新版本尚未安装到 Redmi，门禁设置成功与实际开门尚待明天真机测试。** 用户已拔走手机，不把编译成功当成开门成功。

## 当前功能与证据

- Redmi `24115RA8EG` / `amethyst` / `OS2.0.2.0.VOPMIXM` / Android 15，锁定 bootloader。之前实际读到本人学生证为 NFC-A / MIFARE Classic，UID 长度 4、ATQA `[4,0]`、SAK `8`。不读取扇区、密钥、余额或交易。
- 手机原生 ST 扩展库可从 App 加载；用户启用 USB 调试安全设置后，ADB 已授予 `WRITE_SECURE_SETTINGS`。只读配置成功不代表参数写入或门禁认证成功。
- `0.3.0` 已安装并加密录入实卡。用户现场测试报告“设定失败”。旧版未记录确切失败阶段；最后可见文件没有恢复快照或启用成功记录，具体原因仍未确认。
- 新版逐步记录设备、权限、卡片解密、原配置读取、基线检查、恢复快照、设置及回读的失败；持久保存最近错误。分享仅输出步骤、代号、异常类型，不输出原异常消息或完整 UID。
- 写入前加密保存并解密核验原配置。设置异常、成功后报告保存异常、离开诊断页及两分钟计时结束均尝试恢复，并核对控制器数据、已快照 NCI 参数与用户默认路由。失败保留恢复文件；重新开 App 进入恢复页。
- 严格限制到已核验型号与 ROM、NFC 开启和 ADB 授权。原配置不符或读回 `null` 时拒绝写入，不把 `null` 当空数组放宽检查。

这是有限的设备参数测试，只尝试卡号识别，没有 MIFARE Classic 密钥认证实现。设置回读通过仍需本人到有权使用的门禁现场测试。

## 按模板实现的界面

参考用户 `NFC_Card_App_UI_Design_Spec.md` 与三屏图，视觉规范见 [DESIGN.md](DESIGN.md)。

- 我的卡片：浅色背景、右上蓝色圆形 `+`、实体卡比例 1.586、圆角与预设徽章，底部“我的卡片／使用纪录／设定”。只显示已保存卡片，不预装假凭证。
- 新增面板：贴底显示门禁卡、学生证、交通卡、自订卡片。门禁卡进入实卡读取；学生证与自订卡片创建外观；交通卡明确提示原实体悠游卡加入未支持。
- 编辑：名称、缩略图、系统选图器、自动中心裁切、手动拖动与缩放、预设选择、保存及删除。按保存后持久化；返回可放弃草稿。图片缩小后复制到 App 私有目录，不上传。
- 预设：只能将已录入的门卡选为本 App 测试卡，同一时间一个选择；不冒充系统默认 NFC 卡，不改变付款钱包。仅外观卡的 NFC 开关禁用。
- 使用纪录：显示最近一次设置、恢复、失败报告，均标明开门未验证，不制造刷卡成功历史。

默认卡面使用内置校园建筑插画，不伪造校徽；可换自己的图片。卡面存在不等于具备 NFC 功能。

## 来源、工具链与文件

基于 [Google/AOSP CardEmulation](https://github.com/googlearchive/android-CardEmulation) 的本地 clone，保留 Apache-2.0 与 Git 历史。上游 SHA `167556ae24fd9ffe378715aa13bc197c9ca44361`，见 [LICENSE](LICENSE) 与 [选型记录](research/repo-comparison.md)。没有上传私人设备或卡片数据。

JDK 17 / Gradle 8.13 / AGP 8.13.2 / compile、target 35 / min 31。Runtime 只依赖 Android 平台；JUnit 4.13.2 仅用于测试。未将设备 OEM 二进制打进 APK，共享库为可选声明。

| 文件 | 职责 |
|---|---|
| `CardsActivity.java` | 三项导航、新增面板、编辑和系统选图器 |
| `CardAppearance.java` / `CardStore.java` / `CardFace.java` | 外观数据、唯一 App 预设、卡面与裁切 |
| `MainActivity.java` | 实卡只读、测试入口、限时与生命周期、恢复及脱敏诊断 |
| `AccessCardProfile.java` / `PrivateState.java` | 限制实际卡形态、AndroidKeyStore AES-GCM 私有存储 |
| `VendorProbe.java` | 只读探测，UID 与历史字节只输出长度，区分空参数与无返回 |
| `VendorCardController.java` / `RadioConfiguration.java` / `VendorFailure.java` | 快照、严格检查、受控设置、恢复回读与错误代号 |
| `CardService.java` / `TestProtocol.java` | 合成 HCE，AID `F043414D505553` / CATEGORY_OTHER，不处理支付 |
| `tools/build.ps1` / `tools/deploy.ps1` | 编译／单元／lint；指定设备并核验型号后安装 |
| `artifacts/` | gitignored APK、校验码与私人诊断证据 |

## 构建、安装与明天测试

从 `CampusTap` 目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1
adb devices -l
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy.ps1 -Serial SERIAL
# 仅在 App 显示权限缺失时，对指定 Redmi 重新授权：
adb -s SERIAL shell pm grant tw.campustap.diagnostics android.permission.WRITE_SECURE_SETTINGS
```

`SERIAL` 从当前设备列表取得，不写入公开报告，不误选模拟器。部署使用 `install -r` 保留旧版已加密录入的数据，启动新卡片主页。构建需 `X:` 空闲，完成后自动移除映射。

APK：`artifacts/builds/CampusTap-0.4.0-access-ui-debug.apk`，旁边有 `SHA256.txt`、`build_manifest.json`。本地 debug 签名，不是正式票证发行产品。

1. 接回同一部 Redmi，更新安装；不要先卸载旧版，以免删除已录入参数。
2. 确认保存的卡片及待恢复状态；若有恢复文件，先完成恢复核对。
3. 点“测试门禁”，将实卡移离至少 20 厘米，手动确认启用。失败时保留界面步骤与代号，并分享诊断。
4. 若控制器回读通过，再用手机单独刷本人有权使用的门禁；保存真实结果，设置成功不等于开门成功。
5. 按停止并核对恢复通过，再使用 NFC 付款。测试两分钟退出、切换页面、图片编辑与原钱包共存；这些新版真机检查均未运行。

## 状态与恢复限制

本地编译、14 个 JVM 单元测试及 lint 是交付检查；新 UI 实际运行、选图持久化、两分钟真机恢复与门禁复测 **NOT RUN**。现有模拟器 API 30，低于 min 31，且正在运行用户游戏，没有在该设备安装、切换页面或截图。见 [测试矩阵](docs/test-matrix.md)、[交付关卡](docs/gate-report.md)。

进程被强制停止或系统杀掉时，Activity 计时及离开回调不能保证执行；当前不是无人值守门卡服务。重新开 App 会检测快照并进入恢复页。恢复未通过时保持 NFC 开启，不删除 App、清除数据或撤销权限，先恢复后再付款。解除安装不能代替控制器恢复。

删除录入卡片先恢复再删除 App 加密参数，不写实际学生证。恢复快照不属于可随意清除的日志。代码回退用本地 Git checkpoint；旧上游无法在当前工具链原样构建。

当前范围：手机自身无 Root 门禁测试、模板 UI。没有 Root、解锁、刷 ROM、找校方或操作悠游付；原实体悠游卡加入未实现。既有可行性研究仅为历史材料，不代表现在执行其建议。
