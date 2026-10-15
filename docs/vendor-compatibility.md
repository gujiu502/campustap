# 多品牌 NFC 接口核查

2026-10-15，CampusTap 0.6.5。目标为本人实体卡的识别参数模拟，非 Root，不切换付款钱包。**本轮交付是扩大系统库发现及只读检测，不是所有品牌都能模拟。当前可写后端仍只有 ST；欧加后端未实现。** 所有新增加载路径及品牌实机均为 NOT RUN，当前 ADB 无连接设备。

## 判断标准

手机品牌、系统钱包能开门、存在扩展类，均不足以证明普通 APK 能设置相同卡片。写入至少需要可调用的方法、实时授权、可读取的原配置、可验证恢复，以及已校准的控制器布局。本应用不按 Build.MODEL 或品牌名称放行。

Android 公共 HCE 面向 ISO-DEP/APDU；没有设置任意固定 NFC-A UID 的公共接口。这里的“未找到”是本轮资料核查结果，不是对所有 ROM 私有实现的不存在证明。[Android HCE](https://developer.android.com/develop/connectivity/nfc/hce)

## 本轮各品牌结论

| 品牌／系列 | 找到的路径与本轮工作 | 当前可交付状态／阻塞 |
|---|---|---|
| Xiaomi／Redmi／POCO | ST 后端不再限型号；增加库来源发现与路由接口完整性检查 | 既有 Redmi 用户反馈可用；其他机器须通过方法、WSS、91字节布局与基线检查，不承诺全系列 |
| OPPO | 新系统 vendor 客户端与旧 NfcAdapter 的类型签名检测、只读 UID 状态探针 | 找到接口也不能启用；缺少完整原配置及经验证的恢复流程 [欧加研究] |
| Realme | 同时检查旧平台方法、新 vendor 库，不因品牌自动判为支持 | GT Neo 2 / RMX3370 的旧 ROM 未核验；不可套用 ColorOS 16 资料 [欧加研究] |
| OnePlus | 检查三处已公开分析的 ROM 库路径；尝试解析有类型的静态工厂 | 源资料涉及 Android 16 的一套栈；第三方可调用性、区域限制与硬件仍待实测 [欧加研究] |
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
- 欧加识别 `setHceTypeAConfig(boolean, byte[], byte[], byte[]) → boolean` 与 `getHceTypeAUid() → String`，也检查工厂是否静态且有明确返回接口。探测不实例化、不初始化、不调用 setter。后台只读检查可连接服务、调用 getter；只输出有效长度，不保存或返回 UID 内容。
- 后台报告区分 ST、Oplus、NXP、Samsung、Huawei 类存在与可写后端；不凭手机品牌生成“已适配”。详细报告仍在隐藏工程入口，正常 UI 不展示命令或接口清单。

系统库来源限制遵循 [Android 动态加载安全说明](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)。这些约束限制应用可加载的路径，不替代 ROM 完整性验证。

## 为什么本轮没有直接写欧加

[欧加研究]是研究者对 OnePlus ColorOS 16 / Android 16 / NFC 16.0.0.250924 的一手反编译分析，**不是厂商公开 API 合约，也不是本项目真机验证**。本轮检查版本 `dbf36c4faf2451d2e53f2b89cd73a5f21f736f9f`。只借鉴接口事实，不复制未授权代码或打包 OEM 二进制。

该分析描述了四个持久化状态键，但未给出全部值的编码与恢复契约。UID getter 无法证明实际控制器 ATQA、SAK、随机 UID 模式及原监听配置可恢复。另有监听模式设置，但其具体语义仍含推断，也没有原值读取方法。因此本轮不把这些键当成完整恢复快照，不发送未经校准的模式值；界面明确显示“尚缺安全恢复适配”，不再误导为缺少 ST 授权。

后续写入适配的必要证据：相应 ROM 的实际方法返回、完整初始状态与恢复行为、失败和中断后的恢复，以及独立读头核验。Realme 旧 ROM 尤其不能以新 OnePlus 的接口权限结论代替。没有进行默认付款角色、地区标志、RUS、eSE 或系统配置文件改写。

## 额外源码核对

NXP 旧扩展可提供路由、固件、配置写入方法；不具备已验证的固定 UID 原配置快照／恢复流程，未使用盲写 `setConfig`。三星旧附件 API 与华为旧路由 API 仅用于识别，均不声称完成卡模拟。源码只保留在 gitignored 研究目录，未复制进应用。

- [NXP 扩展源码，039b873](https://github.com/LineageOS/android_vendor_nxp-nfc_opensource_frameworks/blob/039b8733d0a2c1775d1bf710247d9fbd97f9dce9/com/nxp/nfc/NxpNfcAdapter.java)（旧分支，不能证明所有现行 ROM）
- [Samsung 扩展分析源码，30cd25b](https://github.com/488315/samsung_framework/blob/30cd25b68f67be792ab78fb46fb9f63af0964522/services/sources/com/samsung/android/nfc/adapter/SamsungNfcAdapter.java)（非官方 API 合约）
- [Huawei 扩展分析源码，672bb34](https://github.com/dstmath/HWFramework/blob/672bb34094b8780806a10ba9b1d21036fd808b8e/MATE-20_EMUI_11.0.0/src/main/java/com/huawei/nfc/HwNfcAdapter.java)（旧 Android 栈，非 HarmonyOS NEXT）

## 本轮验证

Debug／Release 各29项 JVM 测试通过：新增欧加不初始化的签名检测、不完整接口拒绝、ST 路由条件、只读候选不能成为可写后端、失败原因不误导申请 ST 权限。lint 0 errors / 16 warnings。发布签名与0.6.4保持一致。新 ROM 库加载、欧加服务 getter、真实 RF、熄屏及支付恢复均 **NOT RUN**；没有连接手机，无法将这些检查标为通过。

[欧加研究]: https://gist.github.com/PeronGH/8e272a007edfc192c0aff8d3a6b187da/dbf36c4faf2451d2e53f2b89cd73a5f21f736f9f
[三星]: https://developer.samsung.com/wallet/addtosamsungwallet/nfccapabilities.html
[华为]: https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides-V5/access-onecard-0000001050162309-V5
[荣耀]: https://www.honor.com/cn/support/content/zh-cn15847359/
[vivo]: https://www.vivo.com.cn/service/questions?categoryId=73
[Pixel 项目]: https://github.com/eXtreames/TensorCardEmulator
[Sony]: https://www.sony.net/Products/felica/business/tech-support/data/M1053_Host-Based_Card_Emulation_for_NFC-F_Guideline_1.02e.pdf
[Apple]: https://developer.apple.com/support/nfc-se-platform/
[Android HCE]: https://developer.android.com/develop/connectivity/nfc/hce
