更新（0.6.1，2026-10-15）：用户明确确认自己的 Redmi 已可用，记为 USER_REPORTED_WORKING；未提供独立 RF 记录或连续开门次数。本次截图来自另一台手机。0.6.1 修复授权／兼容性误导提示，Debug、Release 各19项测试及lint通过、签名连续、发布包不可调试。当前 ADB 无连接设备，0.6.1 新授权界面与第二台手机运行 NOT RUN。下文保留 0.6.0 实机证据。

# 2026-10-15 Redmi 实机验收

当前版本 0.6.0-multi-card，versionCode 8；install -r 保留旧卡与 WSS 授权。仅操作指定 Redmi 24115RA8EG / OS2.0.2.0.VOPMIXM，没有操作另一台模拟器、悠游付或系统付款钱包。

## 本次多卡与持久启用

- 原 access 卡保留；门禁卡、学生证分别真实贴卡新增，最终 3 个条目。两次新录入 UUID 不同，对应两个独立 68 字节 AES-GCM 文件，旧文件仍 68 字节。报告不输出 UID 明文。
- 点击第二张卡后直接启用，控制器与 NCI 回读 PASS，activeProfile.cardId 与所点条目一致。
- 第二张卡右上三点打开编辑页；编辑页、菜单和默认选择均实机运行。通过菜单将第二张设为默认，强制停止 App 后冷启动，默认 ID 和回读再次 PASS。
- 后台只读 debug 探针检查 interactive=false、keyguardLocked=true、配置 UID 长度4／SAK8、activeProfile PASS；dumpsys NFC 显示 OFF_LOCKED、Secure NFC=false、host routing=true。旧默认卡冷启动到熄屏探针间隔 163.863 秒，超过旧版两分钟限制仍启用。
- 隐藏开发页打开再返回，正式活动卡 ID 和配置仍 PASS，不触发工程实验的离开恢复。
- 主动“暂停模拟并恢复 NFC”：控制器块、NCI、路由恢复核验 PASS，删除已验证快照；autoActivate=false。冷启动后仍 UID 动态模式、SAK32，没有自动重开模拟。
- 再次点击第二张卡启用后，从系统设置实际关闭 NFC（确认 mState=off）再开启；App 留在后台，由真实系统广播触发默认卡启用。新 ACTIVATE_CARD 时间戳更新，activeProfile 指向第二张默认卡，回读 PASS。没有使用伪造的 NFC 状态广播替代。
- 当前默认付款钱包角色仍 com.google.android.gms；NFC 保持开启。持久启用按用户要求保留恢复快照，付款前主动暂停。

证据：artifacts/tests/multi-card-20261015/，包含两次自动录入、点击切换、菜单设默认、冷启动、后台锁屏、显式暂停、暂停跨启动、NFC 关闭状态和重新启用报告。后台探针只读且仅 debug 打包，DUMP 权限保护，不要求显示调试页。

## 原设定失败与历史校准

原错误已复现为 CHECK_BASELINE / NCI_33_BASELINE_MISMATCH，写入前拒绝。原控制器块91字节、固定UID长度0、SAK32；NCI 0x33 为4字节随机格式、0x58 为默认 ATS 0x70。旧检查误要求它们为空。

修复仅在精确型号、ROM、动态模式与预期基线下接受该格式，null 与未知值仍拒绝。恢复核对原控制器块、ATQA、SAK、ATS 和路由；动态 UID 验证恢复动态模式及随机格式，不强制恢复旧随机字节。见 [Android HCE 协议参数](https://developer.android.com/develop/connectivity/nfc/hce#protocol-parameters-and-details)。

历史 0.4.1 两分钟恢复 120.431 秒、0.5.0 正式页恢复 120.508 秒及离开恢复均 PASS，证据保留 artifacts/tests/live-20261015/。这些是旧限时版本行为，0.6.0 正常启用已取消限时。

## 尚未验证与实现边界

真实读头 RF、手机单独实际开门、连续十次开门、整机重启、撤权与 ROM 更新、系统选图持久化和完整手动裁切手势均 NOT RUN。最后多卡录入时用户已将实体学生证贴上；后续只做配置检查，没有借实体卡在旁的情况声称手机单独开门。

仅识别参数设置，不实现 Classic 密钥认证、扇区复制或原实体悠游卡支付导入。熄屏配置保留不能代替空中接口和门禁实际认证。若读头要求 Classic 加密认证，当前实现无法提供该认证。
