# CampusTap — 多卡片 NFC 门禁参数管理

2026-10-15，版本 `0.6.5-vendor-discovery`。本轮扩展 ROM 库加载、欧加新旧接口发现及多品牌只读识别，见 [逐品牌核查表](docs/vendor-compatibility.md)。**当前可写后端仍只有 ST，欧加写入尚未实现，不能称为全品牌通用模拟版。用户已确认原 Redmi 可以使用；新增库路径和其他手机尚未真机验证。** 既有功能证据见 [实机报告](docs/live-device-test.md)、[验证矩阵](docs/test-matrix.md)。


[下载最新版安装包](https://github.com/gujiu502/campustap/releases/latest/download/CampusTap.apk) · [发布说明与校验码](https://github.com/gujiu502/campustap/releases/latest)

## 当前行为

- “新增 → 门禁卡／学生证”进入独立贴卡页，检测到支持的卡片后自动加密保存并返回列表。每次新增独立条目和加密文件，保留旧版卡片，不覆盖此前录入。
- 点击卡片直接启用；右上角 `⋮` 提供编辑、设为默认和删除。可保存名称、图片与裁切位置。普通点击切换不改变默认卡。
- 启动 App 自动启用默认卡；NFC 重新开启时尝试启用默认卡。主动暂停会关闭自动启用，直到再次点击卡片。
- 正常启用后没有两分钟限时，不在离开 App 或熄屏时主动恢复；原配置的加密快照一直保留，供“暂停模拟”核对恢复。没有常驻前台服务或全局保持亮屏。
- 普通流程没有“门禁测试”或工程参数界面。debug 可长按设置页版本文字进入内部开发页；只读后台探针仅打包到 debug，要求发送方有系统 DUMP 权限。
- 普通界面移除长授权说明、电脑命令和“前往授权”按钮。启动只检查基本 NFC，已授权即可进入列表、编辑和读卡；系统特权仅在实际卡片模拟时严格检查，不再锁住整个软件。
- 启用失败区分未授权、缺少厂商接口、NFC关闭与配置异常。接口检测不依赖型号／ROM；所需方法缺失或原配置不匹配时拒绝写入。
- 扩展欧加三处固定系统库路径及可选 NXP 共享库声明；ST 额外检查原路由读取能力。只加载 ROM 中已有库。欧加、NXP、Samsung、Huawei 的后台识别不代表可写支持；欧加缺少完整原配置与恢复验证时不会误导申请 ST 权限。
- 使用记录展示最近一次设置、恢复和失败，不编造刷卡或开门成功记录。

当前实现 ST 扩展接口的 OEM NFC 识别参数设置，不再按手机型号限制，**没有 Classic 密钥认证、扇区复制或原实体悠游卡支付导入**。自订卡片仅保存外观。没有操作悠游付、改变付款钱包、Root、解锁或联系校方。

## 设备与授权

核验设备：Redmi `24115RA8EG` / `amethyst` / `OS2.0.2.0.VOPMIXM` / Android 15，bootloader 锁定。原学生证为 NFC-A / MIFARE Classic，UID 长度 4、ATQA `[4,0]`、SAK `8`；只保存识别参数，不读取余额、密钥、扇区或交易。

普通 NFC 权限随安装提供；OEM 参数写入还需要用户已授权电脑授予 `WRITE_SECURE_SETTINGS`。App 不能自行授予这个系统权限，更新安装保留已有授权。无需切换默认付款钱包。[Android NFC/HCE 文档](https://developer.android.com/develop/connectivity/nfc/hce)

```powershell
# 仅在授权缺失时，对用户确认的指定手机执行：
adb -s SERIAL shell pm grant tw.campustap.diagnostics android.permission.WRITE_SECURE_SETTINGS
```

设置前检查厂商接口、权限、NFC 和已校准的91字节控制器布局及动态 UID／ATS 基线；加密保存并解密验证原配置，再设置与回读。恢复核对控制器、NCI 和用户路由；动态 UID 核对动态模式及随机格式，其余核对保存数据。恢复失败保留快照，不把 null 当空参数。

## 构建与安装

基于 [Google/AOSP CardEmulation](https://github.com/googlearchive/android-CardEmulation)，上游 SHA `167556ae24fd9ffe378715aa13bc197c9ca44361`，保留 Apache-2.0 与 Git 历史。Runtime 仅依赖 Android，未打包 OEM 二进制。JDK 17 / Gradle 8.13 / AGP 8.13.2 / compile、target 35 / min 31。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1 -Distribution
adb devices -l
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy.ps1 -Serial SERIAL
```

发布产物为 Application/build/outputs/apk/release/Application-release.apk；-Distribution 使用本机已有测试签名密钥，不自动创建或下载私钥。普通构建可省略该开关。使用 install -r 保留已录入数据，不先卸载。构建临时使用空闲的 X: ASCII 映射并自动移除。Debug、Release 各 29 项 JVM 测试通过，lint 0 errors / 16 warnings。发布 APK 不可调试且不包含后台 debug 探针。为了兼容已有安装，测试发布保留原 Android Debug 签名；私钥不入库、不上传，后续正式生产签名需单独规划。

| 文件 | 职责 |
|---|---|
| CardsActivity / CardFace | 模板列表、点击启用、三点菜单、编辑与图片裁切 |
| NfcFlowActivity | 贴卡自动添加、暂停恢复，不输出工程细节 |
| CardStore / CardAppearance | 多卡元数据、默认选择、旧版迁移与文件边界 |
| VendorCardController / RadioConfiguration | 串行设置、加密恢复快照、严格配置检查与回读 |
| VendorLibraries / VendorCapabilities | 固定系统库发现、类型检查及多厂商只读识别 |
| NfcStartupReceiver | NFC 开启后启用默认卡，尊重主动暂停 |
| MainActivity | 隐藏开发工具，限时实验与正式持久启用分离 |
| AccessCardProfile / PrivateState | 卡形态限制、AndroidKeyStore AES-GCM 存储 |
| CardService / TestProtocol | CATEGORY_OTHER 合成 HCE，不实现支付或 Classic 认证 |

UI 依据用户三屏模板与 NFC_Card_App_UI_Design_Spec.md，见 [DESIGN.md](DESIGN.md)。默认校园插画不伪造校徽，可换自己的图片。设备证据、APK、OEM 检查材料均 gitignored，不上传私人卡号。

## 使用与边界

添加后点击需要的卡片，或从三点菜单设为默认。实卡移离手机至少 20 厘米后，用手机单独刷本人有权使用的门禁，记录真实结果。熄屏配置和 host routing 已检查，但它们不是空中接口或门禁认证证明。

NFC 付款前先在 App 暂停模拟并确认恢复成功。若恢复失败，请保持 NFC 开启与授权，不卸载、清数据或删除恢复文件。启用期间的 radio_rollback.enc 是必要恢复数据，不是日志。

App 进程退出时配置可继续保留；设备重启、NFC 服务重启、ROM 更新仍可能重置硬件。当前只在启动 App 或 NFC 开启时尝试应用默认卡，不承诺所有整机重启情形。系统选图持久化与完整手动裁切手势尚未实机验收，真实支付未测。

发布约定：每个可下载的最新版 Release 都附带固定名称 CampusTap.apk，并标记为 Latest；保留带版本号的安装包方便历史下载。固定最新版地址无需随版本更换。
