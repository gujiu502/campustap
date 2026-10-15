# 多品牌 NFC 接口核查

2026-10-15，CampusTap 0.7.2。目标为本人实体卡的识别参数模拟，非 Root，不切换付款钱包。**保留 ST 与实验性欧加直接后端；无需无线调试或辅助App。** 用户的 CPH2653 / Android16 / CPH2653_16.0.10.501(EX01) 报告证明接口和功能检测成功，身份字段与UID getter全空，且无待恢复资料；MODE仅知格式有效，未获0／1值。新增严格的“明确停用且全空”初始分支；实际设置、恢复与门禁尚未验证。

## 判断标准

手机品牌、系统钱包能开门、存在扩展类，均不足以证明普通 APK 能设置相同卡片。本应用不按 Build.MODEL 或品牌名称放行。ST 保留已校准控制器布局与原始参数回读；欧加实验路径只接受可读取的停用状态并核对厂商状态，不具备完整控制器快照。两者的验证强度不同，不能混称。

Android 公共 HCE 面向 ISO-DEP/APDU；没有设置任意固定 NFC-A UID 的公共接口。这里的“未找到”是本轮资料核查结果，不是对所有 ROM 私有实现的不存在证明。[Android HCE](https://developer.android.com/develop/connectivity/nfc/hce)

## 本轮各品牌结论

| 品牌／系列 | 找到的路径与本轮工作 | 当前可交付状态／阻塞 |
|---|---|---|
| Xiaomi／Redmi／POCO | ST 后端不再限型号；增加库来源发现与路由接口完整性检查 | 既有 Redmi 用户反馈可用；其他机器须通过方法、WSS、91字节布局与基线检查，不承诺全系列 |
| OPPO | 新增直接调用设置、厂商状态回读与停用恢复 | 实验性；须通过方法、功能开关、完整停用状态及回读检查；实机 NOT RUN [欧加研究] |
| Realme | 旧平台方法与新 vendor 客户端均可进入同一直接调用流程 | GT Neo 2 / RMX3370 仍 NOT RUN；旧 ROM 方法或状态不足时拒绝写入 [欧加研究] |
| OnePlus | 固定 ROM 库加载、有类型的静态工厂、直接设置／恢复；明确MODE0且全空的初始状态处理 | CPH2653用户只读报告确认接口／功能可读；实际写入及RF NOT RUN，不等于已能开门 [欧加研究] |
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

0.6.5只识别接口，0.7.0加入受限直接事务；0.7.2额外处理明确停用、尚无任何身份记录的初始状态。恢复目标为关闭本次静态UID覆盖并检查受限厂商状态，**不是原控制器逐字节恢复证明，也不保证重新删除初始化前不存在的系统键**：

1. 只用有类型的方法调用，不猜 Binder 事务编号。启用前要求 `hasFeature("HCE_ACCESSCARD")` 返回 true；不改地区或 RUS 功能开关。
2. 读取四个 `hce_access_card_` 状态键以及UID getter。只接受明确停用 `mode="0"`。完整状态要求ATQA为`0000`／`0400`、SAK为`00`／`20`、有效UID；停用时getter可为空，非空时须匹配。额外允许ATQA、SAK、UID、getter全部为空的初始状态，将其记录为空，而不编造原值。MODE不明或为1、部分字段缺失、未知格式或读取失败均不进入这个初始分支。仅规范化等价十六进制书写，不改字节顺序、不猜十进制数组。
3. 使用既有 AndroidKeyStore 加密、AtomicFile 持久化原元数据与目标卡，并解密核对。再次检查原状态；在调用 setter 前，另持久化“准备写入”标记。进程中断后仍能区分未尝试写入和可能已改动射频的情况。
4. 仅调用厂商 `setHceTypeAConfig` 设置本次 UID、ATQA、SAK；必须返回 true，且 mode、三项卡参数与 UID getter 一致。只记录 `vendorStateReadback=PASS`，`controllerReadback=NOT_AVAILABLE`，实际门禁和熄屏仍 NOT_TESTED。
5. 恢复按快照选择原后端。已尝试写入时，即使元数据未变也发送停用调用，因为RF可能先变。完整原状态核对原元数据与getter。初始全空状态使用源分析中出现的停用参数`false, ATQA=0000, SAK=00, UID=00000011`，并要求回读MODE0、身份仍全空或为该已知停用标记（ATQA／SAK只许已校准默认／零值）。这些参数用于调用停用操作，**不充当原配置快照**。核对通过才删恢复文件，失败保留。原先不存在的键可能保留为新生成的停用记录。
6. 若当前身份既不是原状态也不是本次卡片，拒绝覆盖其他操作选择的卡片。若同时出现 ST 和欧加恢复文件，停止写入，保留两者。

这些状态键不是完整 RF／路由快照，读取成功不能证明实际控制器状态。源分析中的 native 停用分支使用厂商默认模板，而非任意原始控制器配置；本实验只从上述已停用基线进入。真实恢复行为、竞态、地区与旧 ROM 差异均尚待真机校准。没有调用监听模式切换、路由 setter、配置文件写入、地区／RUS 改写、eSE 重置或付款角色切换。

状态读取使用 [Settings.Global.getString](https://developer.android.com/reference/android/provider/Settings.Global#getString(android.content.ContentResolver,%20java.lang.String))；应用不直接写这些系统键。0.7.2只把明确MODE0且身份全空当成独立的初始状态，不把缺值替换成虚构参数；这是基于已读到的停用标志和公开分析停用调用制定的实验策略，尚非该ROM完整恢复合约的实机证明。

## 额外源码核对

NXP 旧扩展可提供路由、固件、配置写入方法；不具备已验证的固定 UID 原配置快照／恢复流程，未使用盲写 `setConfig`。三星旧附件 API 与华为旧路由 API 仅用于识别，均不声称完成卡模拟。源码只保留在 gitignored 研究目录，未复制进应用。

- [NXP 扩展源码，039b873](https://github.com/LineageOS/android_vendor_nxp-nfc_opensource_frameworks/blob/039b8733d0a2c1775d1bf710247d9fbd97f9dce9/com/nxp/nfc/NxpNfcAdapter.java)（旧分支，不能证明所有现行 ROM）
- [Samsung 扩展分析源码，30cd25b](https://github.com/488315/samsung_framework/blob/30cd25b68f67be792ab78fb46fb9f63af0964522/services/sources/com/samsung/android/nfc/adapter/SamsungNfcAdapter.java)（非官方 API 合约）
- [Huawei 扩展分析源码，672bb34](https://github.com/dstmath/HWFramework/blob/672bb34094b8780806a10ba9b1d21036fd808b8e/MATE-20_EMUI_11.0.0/src/main/java/com/huawei/nfc/HwNfcAdapter.java)（旧 Android 栈，非 HarmonyOS NEXT）

## 本轮验证

Debug／Release各62项JVM测试，新增8项初始状态边界与停用恢复测试；仍覆盖备份和写入先后、崩溃、部分生效、错误回读、外部变更、存储／服务失败。它们验证客户端协议，不证明厂商实现与假设相符。lint 0 errors / 16 warnings，签名延续旧版。用户已成功复制CPH2653的只读结果；新版厂商设置／恢复、RF、熄屏、支付均 **NOT RUN**。

## 无调试连接的排查

升级后进入“设置 → 複製相容性資訊”，将文本粘贴给维护者。内容为型号／版本、当前后端候选、功能检测、MODE的0／1值、初始状态分类、字段受控标签与长度；有效UID仅报告是否一致，不输出UID原值。ST类存在不证明实际芯片为ST，报告明确标记芯片未验证。复制不执行setter、不读卡片文件、不上传；普通界面不展示工程字段或命令。[Android 剪贴板接口](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)

[欧加研究]: https://gist.github.com/PeronGH/8e272a007edfc192c0aff8d3a6b187da/dbf36c4faf2451d2e53f2b89cd73a5f21f736f9f
[三星]: https://developer.samsung.com/wallet/addtosamsungwallet/nfccapabilities.html
[华为]: https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides-V5/access-onecard-0000001050162309-V5
[荣耀]: https://www.honor.com/cn/support/content/zh-cn15847359/
[vivo]: https://www.vivo.com.cn/service/questions?categoryId=73
[Pixel 项目]: https://github.com/eXtreames/TensorCardEmulator
[Sony]: https://www.sony.net/Products/felica/business/tech-support/data/M1053_Host-Based_Card_Emulation_for_NFC-F_Guideline_1.02e.pdf
[Apple]: https://developer.apple.com/support/nfc-se-platform/
[Android HCE]: https://developer.android.com/develop/connectivity/nfc/hce
