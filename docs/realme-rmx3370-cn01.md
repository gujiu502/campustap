# RMX3370 / Android 13 / CN01 同版系统核查

2026-10-15。用户提供0.7.4报告：`RMX3370_13.1.0.601(CN01)`，SDK33；欧加类存在、完整身份接口不匹配、候选数0；ST方法存在但没有运行时权限／服务资料。此次取得同版官方OTA作静态核查，未连接或更改手机。

## 来源与完整性范围

[官方OTA](https://gauss-componentotacostmanual-cn.allawnfs.com/remove-6c1737643586055f68a82f6e134e4965/component-ota/24/10/21/927a42dcf1b44d4da36bac1dcac79358.zip)，总长度7051144843字节。分段读取ZIP目录、metadata、transfer-list以及system_ext压缩条目；没有下载其他系统分区。包内metadata：

- `version_name=RMX3370_13.1.0.601(CN01)`；Android13／SDK33。
- `ota-id=RMX3370_11.F.21_3210_202410161710`。
- fingerprint：`realme/RMX3370/RE5473:13/TP1A.220905.001/R.1a88165-1ce22-e0695:user/release-keys`。

system_ext压缩条目867421518字节，CRC32为3098086273；Brotli结束标志与transfer-list字节数核对通过，恢复的EROFS镜像1140842496字节。验证范围不含整包签名或真机NFC运行。只提取下列NFC文件，未执行OEM文件、未复制OEM实现进App、未公开上传二进制：

| 文件 | SHA256 |
|---|---|
| `framework/nfcvendorlib.jar` | `3594ea3f998e5eef80f62fae77ee099ff4adb4f09f1d9660ca0220e150ef1821` |
| `framework/com.vendor.nfc.jar` | `8cd71dca1ca5e6e32c6c6b4000eae117107163f9383f1b7b6de462d89e8feefc` |
| `app/NfcNci/NfcNci.apk` | `417a698732ff4db14a007ec313ff2383773a86ca87560d7dcef37d1e8f089606` |

提取复用[RemoteZip 0.12.6](https://github.com/gtsystem/python-remotezip)读取目录、[sdat2img](https://github.com/xpirt/sdat2img/tree/0eec6c264645ad610169b3c807175c437e1f3aa4)的transfer-list格式、[EROFS工具移植版v1.8.10-251217](https://github.com/sekaiacg/erofs-tools/releases/tag/v1.8.10-251217)的只读文件提取，以及Android SDK35的dexdump。工具与系统镜像均限本地研究目录。

## 已确认的接口事实

1. `VendorNfcAdapter`确实提供公开实例方法`setHceTypeAConfig(boolean, byte[], byte[], byte[]) -> boolean`、`hasFeature(String) -> boolean`、`getNfcc() -> String`。此版客户端与NFC服务没有`getHceTypeAUid`。0.7.4要求setter、UID getter与feature同时存在，因而把它判为完整API不可用；不能把该结果说成所有身份设置方法都不存在。
2. 服务`VendorNfcService.doSetHceTypeAConfig`先检查NFC已开、海外模式、HCE支持标志，才进入native设置。`NfcService.isOverSeaRegion`读取`ro.oplus.connectivity.oversea`，默认false；`NfcUtils.colorOsIsSupportHceAccessCard`再次要求海外模式，并读取`ro.oplus.nfc.hce_accesscard`，默认false。**即使客户端放宽getter要求，服务也可能因这些条件拒绝。** CN01版本名不能代替当前运行时属性值。
3. setter成功后才写`Settings.Global`的mode／ATQA／SAK／UID；停用成功时mode为0且身份字符串清空。这是旧接口合约，与“getter存在并独立匹配”的当前后端不同。只看到setter不等于完成安全恢复适配，本轮没有为缺getter的客户端增加写入路径。
4. 同一个客户端JAR包含ST与NXP相关类。ST构造器连接`nfc.st_ext`，取不到服务会抛异常；类存在不能证明该手机运行ST硬件／服务。现有ST写入还要求授权、91字节原配置与NCI／路由核对，未放宽。
5. `getNfcc`通过OEM的chip-manager枚举返回芯片标签，读取不依赖UID getter。只可将其视为OEM报告，不能等同实际RF验证。新报告仅允许已核实的标签，未知字符串不导出。

## 0.7.5 交付与仍缺证据

- 欧加逐方法记录MATCH、MISSING_OR_FILTERED、返回类型不符等受控结果，探测不初始化／调用厂商写接口。运行时隐藏API过滤与真正不存在仍保留区别。
- ST报告实际NFC／WSS权限、连接与读回步骤、原配置长度／NCI长度及基线检查；普通复制不输出原始控制器、UID、历史字节或路由内容。
- 旧欧加只读报告独立读取功能开关、白名单芯片标签及两个运行时布尔属性。属性读取采用固定`/system/bin/getprop`与两个固定键，每次有超时；空值或失败为UNKNOWN类状态，不冒充false，不修改任何属性或地区设置。
- ST候选未授权时标为`ST_PERMISSION_REQUIRED`，不再只显示ST。

发布时的报告没有ST真实服务／权限、OEM芯片标签与运行时模式值。下节记录用户随后提供的结果。0.7.5是诊断改进，**没有新增已验证的RMX3370模拟后端**。正常UI不新增工程弹窗，不使用Root／ADB／辅助App，不切换付款钱包。

## 0.7.5 用户实机回报与 NXP 复核

2026-10-15，用户从上述同一型号／系统复制报告；本轮没有调试连接。以下是用户报告的观测，不是维护者另行完成的RF测试：

| 项目 | 报告结果 | 可得结论 |
|---|---|---|
| 基本NFC权限 | `GRANTED` | 不能再把失败归因为忘记申请基本权限 |
| 系统设置权限 | `DENIED` | 未取得ST路径所需权限；普通权限弹窗不能授予此权限 |
| OEM芯片报告 | `SN100T` | 对应NXP系列，支持改查NXP路径；仍不是RF或硬件独立验证 |
| ST服务连接 | `CONNECT_ST_SERVICE / CALL_FAILED / RuntimeException` | ST客户端类和方法存在，但实际连接失败；错误未包含具体服务原因，不能仅由异常类型断言服务不存在或只缺权限 |
| 旧欧加接口 | setter与feature匹配，UID getter缺失／被过滤 | 与同版ROM缺少getter的静态事实吻合，当前完整后端不适用 |
| 旧欧加功能查询 | `featureEnabled=false` | 客户端回报功能不可用；不能仅据此区分地区、属性、RUS开关或客户端失败回退 |
| 两个属性 | `EMPTY_OR_INACCESSIBLE` | 未知，不当成false，也不能据此宣布手机的地区属性值 |
| 本次探测 | `mutationAttempted=false / recoveryPending=false` | 报告未尝试写入，无待恢复记录；不是模拟成功证明 |

`SN100T`与NXP的关系参见[NXP官方产品说明](https://www.nxp.com/company/about-nxp/smarter-world-blog/BL-MORE-100-CHINESE-CITIES)。[Android权限文档](https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS)将`WRITE_SECURE_SETTINGS`标为不供第三方应用使用；它不是可用普通运行时弹窗授予的危险权限。

继续读取同版ROM中先前保留的NFC文件，并从同一个system_ext镜像仅提取`lib64/libsn100nfc_nci.so`（2533280字节，SHA256 `66291c22b63068a0ebd2a6068613e8488c0315b9f98d7eee0581c862cbc013b7`）。本地用pyelftools 0.32与Capstone 5.0.6作静态检查，未加载或执行该库：

1. 服务端`VendorNfcService.doHasFeature`确实识别`HCE_ACCESSCARD`，不是应用拼错查询名。它结合`colorOsIsSupportHceAccessCard()`与NfcFeature的启用状态返回结果；旧客户端还会将部分RemoteException失败转为false。因此报告不能提供更细的关闭原因。
2. NXP的`setConfig(String,String)`与`changeRfParamsByConfig(byte[])`先检查`checkPackageAccess()`；不在可访问名单时调用`NfcPermissions.enforceAdminPermissions`，该ROM实际检查`WRITE_SECURE_SETTINGS`。`setConfig`还操作`/data/nfc/libnfc-nxpTransit.conf`，不是任意App可直接使用的普通文件设置。
3. 另一个`changeRfParams(byte[],boolean)`服务方法只检查基本NFC权限，并转到native。ELF中的JNI登记确认签名`([BZ)I`，函数地址`0x89790`、unwind范围1524字节。原生正常发送分支要求命令头`20 02`（设置配置），调用`NxpNfc_Write_Cmd_Common`；Java层只收到整数结果码。此路径不是可直接借用的原配置查询接口。布尔参数为true的成功分支还调用`send_flush_ram_to_flash`，所以不能用猜测参数反复试写。
4. `startCardEmulation()`没有UID／ATQA／SAK参数；reader pass-through和`transceiveAppData`是另一组读卡器调用，不能因方法名称相近就当作卡片身份设置／原配置回读。

在已检查的客户端／服务合约中，仍未找到与上述NXP写入配套、普通App可用且可验证的原身份／配置读取和恢复流程。**这不是证明该硬件绝无其他路径；这是当前不能交付可靠NXP模拟后端的具体缺项。** 不能拿原生库中的内部`getConfig`函数冒充已经暴露给APK的服务接口，也不能把硬编码默认值冒充原配置备份。

本轮不新增猜测写入、不改属性／地区／付款配置，APK维持0.7.5。源码核查和用户报告已经足以说明：ST授权不是唯一阻塞，删掉getter检查也不能证明可用。不要求用户再安装同类定位版重复提供相同信息。真实门禁、熄屏、控制器回读与恢复仍NOT RUN。
