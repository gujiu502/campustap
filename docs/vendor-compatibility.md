# 多品牌 NFC 接口核查

2026-10-15，CampusTap 0.7.1。目标为本人实体卡的识别参数模拟，非 Root，不切换付款钱包。**保留 ST 与实验性欧加直接设置／恢复后端；无需无线调试或辅助 App。** 一加用户反馈0.7.0显示原状态检查失败；具体型号、字段与格式尚不明确。0.7.1细分错误、兼容等价十六进制格式，并增加无卡号的本机复制检查结果。不能宣称已解决该机模拟。

## 判断标准

手机品牌、系统钱包能开门、存在扩展类，均不足以证明普通 APK 能设置相同卡片。本应用不按 Build.MODEL 或品牌名称放行。ST 保留已校准控制器布局与原始参数回读；欧加实验路径只接受可读取的停用状态并核对厂商状态，不具备完整控制器快照。两者的验证强度不同，不能混称。

Android 公共 HCE 面向 ISO-DEP/APDU；没有设置任意固定 NFC-A UID 的公共接口。这里的“未找到”是本轮资料核查结果，不是对所有 ROM 私有实现的不存在证明。[Android HCE](https://developer.android.com/develop/connectivity/nfc/hce)

## 本轮各品牌结论

| 品牌／系列 | 找到的路径与本轮工作 | 当前可交付状态／阻塞 |
|---|---|---|
| Xiaomi／Redmi／POCO | ST 后端不再限型号；增加库来源发现与路由接口完整性检查 | 既有 Redmi 用户反馈可用；其他机器须通过方法、WSS、91字节布局与基线检查，不承诺全系列 |
| OPPO | 新增直接调用设置、厂商状态回读与停用恢复 | 实验性；须通过方法、功能开关、完整停用状态及回读检查；实机 NOT RUN [欧加研究] |
| Realme | 旧平台方法与新 vendor 客户端均可进入同一直接调用流程 | GT Neo 2 / RMX3370 仍 NOT RUN；旧 ROM 方法或状态不足时拒绝写入 [欧加研究] |
| OnePlus | 固定 ROM 库加载、有类型的静态工厂、直接设置／恢复 | 实验性；不先索取 ST 权限，服务端权限／区域／功能限制仍须遵守，实机 NOT RUN [欧加研究] |
| Samsung | 识别 SamsungNfcAdapter；核对公开 Wallet NFC 接入与已分析的附件接口 | 附件通信不是固定 UID 后端；Wallet 要求接入配置，未接入 [三星] |
| Huawei | 识别 HwNfcAdapter；核对 Wallet Kit 及旧 Android 扩展路由接口 | 路由选择不是任意 UID 设置；Wallet 为商户凭证接入，未接入 [华为] |
| Honor | 核查原生钱包门禁支持；Android 机仍按实际接口检测 | 官方钱包能力不等于第三方接口，未取得可实现的 UID／恢复接口 [荣耀] |
| vivo／iQOO | 核查官方 NFC／钱包能力；沿用公共 NFC 读卡与通用接口检测 | 未取得可实现的第三方固定 UID／恢复接口，未完成模拟适配 [vivo] |
| Google Pixel | 公共 HCE；另审查 TensorCardEmulator 的安装前提 | 已找到的该实现要求 Root 模块，排除；本轮未完成非 Root UID 后端 [Pixel 项目] |
| Motorola／Lenovo | 公共 NFC/HCE 路径及通用 ST／NXP 发现 | 未取得可实现的私有 UID／恢复接口，未完成模拟适配 [Android HCE] |
| Sony Xperia | 公共 HCE；核对 Sony 的 NFC-F 开发指南 | NFC-F 不等于本项目的 NFC-A Classic UID，未完成相应模拟适配 [Sony] |
| ASUS | 公共 NFC/HCE 路径及通用 ST／NXP 发现 | 未取得可实现的私有 UID／恢复接口，未完成模拟适配 [Android HCE] |
| HMD／Nokia | 公共 NFC/HCE 路径及通用 ST／NXP 发现 | 同上；具体型号是否有 NFC 另以系统能力为准 [Android HCE] |
| Meizu | 公共 NFC/HCE 路径及通用 ST／NXP 发现 | 未取得可实现的私有 UID／恢复接口，未完成模拟适配 [Android HCE] |
| ZTE／Nubia | 公共 NFC/HCE 路径及通用 ST／NXP 发现 | 未取得可实现的私有 UID／恢复接口，未完成模拟适配 [Android HCE] |
| TECNO／Infinix | 公共 NFC/HCE 路径及通用 ST／NXP 发现 | 未取得可实现的私有 UID／恢复接口，未完成模拟适配 [Android HCE] |
| Apple iPhone | 核查 NFC & SE Platform | 需要单独 iOS 工程及 entitlement／协议；本 Android APK 不适用 [Apple] |

## 已落地的代码

- `VendorLibraries`：优先使用系统提供的共享库；额外只加载以下固定 ROM 位置的可读、不可写 JAR。规范化后的路径须仍在系统目录内。不下载或打包私有库，不绕过隐藏 API 限制。
  - `/system_ext/framework/nfcvendorlib.jar`
  - `/system_ext/framework/oplusex/com.oplus.NfcNci/nfcvendorlib.jar`
  - `/system_ext/framework/com.vendor.nfc.jar`
- Manifest 将 `com.nxp.nfc` 声明为可选共享库；缺少库仍能安装。不把识别到 NXP 类视为能模拟。[NXP 构建配置](https://github.com/NXPNFCLinux/nxpnfc_android11/blob/master/device-nfc.mk)、[Android 共享库声明](https://developer.android.com/guide/topics/manifest/uses-library-element)
- ST 只在参数写入／回读方法及路由快照方法完整时选择。启用和恢复使用同一个扩展类加载器，保留原有权限、加密快照及严格回读。没有放宽91字节布局。
- 欧加检查 `setHceTypeAConfig(boolean, byte[], byte[], byte[]) → boolean`、`getHceTypeAUid() → String`、`hasFeature(String) → boolean`，也检查工厂是否静态且有明确返回接口。发现阶段不初始化厂商类、不调用 setter；点击卡片后才连接并执行预检与事务。
- 后台报告区分 ST、Oplus、NXP、Samsung、Huawei 类存在与后端实现；欧加明确标注实验性和控制器回读不可用。详细报告仍在隐藏工程入口，正常 UI 不展示命令或接口清单。

系统库来源限制遵循 [Android 动态加载安全说明](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)。这些约束限制应用可加载的路径，不替代 ROM 完整性验证。

## 欧加直接路径与恢复范围

[欧加研究]是研究者对 OnePlus ColorOS 16 / Android 16 / NFC 16.0.0.250924 的一手反编译分析，**不是厂商公开 API 合约，也不是本项目真机验证**。本轮检查版本 `dbf36c4faf2451d2e53f2b89cd73a5f21f736f9f`。只借鉴接口事实，不复制未授权代码或打包 OEM 二进制。

0.6.5 只识别接口。0.7.0 改为以下受限实验策略，恢复目标是“关闭本次静态 UID 覆盖并核对厂商元数据”，**不是原控制器每个字节的恢复证明**：

1. 只用有类型的方法调用，不猜 Binder 事务编号。启用前要求 `hasFeature("HCE_ACCESSCARD")` 返回 true；不改地区或 RUS 功能开关。
2. 读取四个 `hce_access_card_` 状态键以及 UID getter。只接受明确的停用状态 `mode="0"`、ATQA 十六进制 `0000` 或 `0400`、SAK `00` 或 `20`，以及长度有效且与 getter 一致的 UID。0.7.1仅规范化外侧空白、0x前缀、逐字节空格／冒号／连字符，不改变字节顺序、不猜十进制数组。缺值、未知格式、已有活动卡或读取被拒绝均不写。上述编码是保守实验约束，未宣称所有 ROM 都如此编码。
3. 使用既有 AndroidKeyStore 加密、AtomicFile 持久化原元数据与目标卡，并解密核对。再次检查原状态；在调用 setter 前，另持久化“准备写入”标记。进程中断后仍能区分未尝试写入和可能已改动射频的情况。
4. 仅调用厂商 `setHceTypeAConfig` 设置本次 UID、ATQA、SAK；必须返回 true，且 mode、三项卡参数与 UID getter 一致。只记录 `vendorStateReadback=PASS`，`controllerReadback=NOT_AVAILABLE`，实际门禁和熄屏仍 NOT_TESTED。
5. 恢复按快照选择原后端，不因权限或默认后端变化而选错。对已尝试写入的欧加事务，即使元数据看似未变，也发送停用调用，因为 RF 可能先变而元数据后变。再次核对全部原元数据和 getter 后才删除恢复文件；失败保留文件。
6. 若当前身份既不是原状态也不是本次卡片，拒绝覆盖其他操作选择的卡片。若同时出现 ST 和欧加恢复文件，停止写入，保留两者。

这些状态键不是完整 RF／路由快照，读取成功不能证明实际控制器状态。源分析中的 native 停用分支使用厂商默认模板，而非任意原始控制器配置；本实验只从上述已停用基线进入。真实恢复行为、竞态、地区与旧 ROM 差异均尚待真机校准。没有调用监听模式切换、路由 setter、配置文件写入、地区／RUS 改写、eSE 重置或付款角色切换。

状态读取使用 [Settings.Global.getString](https://developer.android.com/reference/android/provider/Settings.Global#getString(android.content.ContentResolver,%20java.lang.String))；应用不直接写入这些系统键。权限或编码不足时停止，不用默认值伪造快照。全新 ROM 尚未生成这些键时也可能被拒绝，不能把缺失键当成安全默认状态。

## 额外源码核对

NXP 旧扩展可提供路由、固件、配置写入方法；不具备已验证的固定 UID 原配置快照／恢复流程，未使用盲写 `setConfig`。三星旧附件 API 与华为旧路由 API 仅用于识别，均不声称完成卡模拟。源码只保留在 gitignored 研究目录，未复制进应用。

- [NXP 扩展源码，039b873](https://github.com/LineageOS/android_vendor_nxp-nfc_opensource_frameworks/blob/039b8733d0a2c1775d1bf710247d9fbd97f9dce9/com/nxp/nfc/NxpNfcAdapter.java)（旧分支，不能证明所有现行 ROM）
- [Samsung 扩展分析源码，30cd25b](https://github.com/488315/samsung_framework/blob/30cd25b68f67be792ab78fb46fb9f63af0964522/services/sources/com/samsung/android/nfc/adapter/SamsungNfcAdapter.java)（非官方 API 合约）
- [Huawei 扩展分析源码，672bb34](https://github.com/dstmath/HWFramework/blob/672bb34094b8780806a10ba9b1d21036fd808b8e/MATE-20_EMUI_11.0.0/src/main/java/com/huawei/nfc/HwNfcAdapter.java)（旧 Android 栈，非 HarmonyOS NEXT）

## 本轮验证

Debug／Release 各54项 JVM 测试：覆盖直接后端选择、快照先于写入、准备写入标记、进程中断恢复、调用被拒与部分生效、错误回读、存储／服务失败、外部卡片变更、恢复失败保留快照、格式规范化与缺值／未知编码区分。它们验证客户端协议，不证明厂商实际实现与假设相符。lint 0 errors / 16 warnings。发布签名延续旧版。目标手机复制按钮、厂商设置／恢复、真实 RF、熄屏及支付恢复均 **NOT RUN**；用户照片仅确认状态检查失败。

## 无调试连接的排查

升级后进入“设置 → 複製相容性資訊”，将文本粘贴给维护者。内容为设备型号／版本、接口发现、功能检测，以及各状态字段的受控状态标签和长度；有效 UID 的两处读数仅输出是否一致，不输出任何 UID 或原值。复制操作不执行 setter、不读取卡片文件、不自动上传。一般界面不展示字段或工程命令。[Android 剪贴板接口](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)

[欧加研究]: https://gist.github.com/PeronGH/8e272a007edfc192c0aff8d3a6b187da/dbf36c4faf2451d2e53f2b89cd73a5f21f736f9f
[三星]: https://developer.samsung.com/wallet/addtosamsungwallet/nfccapabilities.html
[华为]: https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides-V5/access-onecard-0000001050162309-V5
[荣耀]: https://www.honor.com/cn/support/content/zh-cn15847359/
[vivo]: https://www.vivo.com.cn/service/questions?categoryId=73
[Pixel 项目]: https://github.com/eXtreames/TensorCardEmulator
[Sony]: https://www.sony.net/Products/felica/business/tech-support/data/M1053_Host-Based_Card_Emulation_for_NFC-F_Guideline_1.02e.pdf
[Apple]: https://developer.apple.com/support/nfc-se-platform/
[Android HCE]: https://developer.android.com/develop/connectivity/nfc/hce
