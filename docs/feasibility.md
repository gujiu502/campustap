# Redmi + 雙功能學生證：實機可行性

日期：2026-10-15（Asia/Kuala_Lumpur）。主要目標「Redmi 一貼開宿舍門」目前 **BLOCKED**，未完成。診斷 App 的讀卡成功不是開門成功。

## 已驗證

| 項目 | 結果 | 證據 |
|---|---|---|
| USB | 已授權的單一目標 Redmi | `artifacts/device/capability_report.json`，序號未保存 |
| 手機 | 24115RA8EG / amethyst / Android 15 API 35 / OS2.0.2.0.VOPMIXM | ADB getprop + App 公開 API |
| NFC | 已啟用；宣告 NFC、HCE、eSE、UICC | ADB features、dumpsys、App API |
| 原學生證 | `MifareClassic` + `NfcA`，SAK 8，ATQA [4,0]，UID 長度 4 bytes；沒有宣告 `IsoDep` | `artifacts/device/card_probe.json`，直接感應使用者放好的卡片 |
| MIFARE 加密與內容 | 未驗證、未讀扇區、未讀餘額、未嘗試密鑰 | 不因卡種宣告推斷加密強度或門禁認證規則 |
| realme 路線 | 使用者確認是系統錢包門禁卡 | 使用者本次回覆；型號、錢包版本與完整錄入流程未知 |
| 小米錢包 | `com.mipay.wallet` 6.90.9.5361.2269 已安裝，非預裝 system app 標記 | dumpsys package；啟動後回桌面，未看到可用門卡 UI；沒有足夠錯誤資料判定是 crash |
| 小米門卡組件 | `com.miui.tsmclient` 未安裝，連 `pm list packages -u` 也沒有 | ADB pm；不代表所有 Global Redmi 永遠無法使用 |
| 系統付款 | Google `com.google.android.gms` 保持預設 | 前後比較 Wallet role holder；沒有申請或更換付款角色 |
| 測試 HCE 路由 | 合成 AID `F043414D505553` 登記為 CATEGORY_OTHER | NFC dumpsys；不是實際 APDU 往返證據 |
| 讀卡器模式 | 約一次偵測後退出，`mIsReaderMode=false` | dumpsys；正常付款/HCE 路由恢復 |

## 技術結論

[Android 官方 HCE](https://developer.android.com/develop/connectivity/nfc/hce) 是 ISO-DEP / APDU 模擬，並要求把 HCE UID 視為隨機。這張卡公開的技術清單是 MIFARE Classic / NFC-A；**一般 HostApduService APK 無法作原卡協定的直接替代**。把一個帳號字串或 UID 填進示例 App 無法解決這項限制。

這不否定 realme 成功案例。realme 的系統錢包可能使用廠商 NFC 控制器／安全元件能力，其確切配置沒有實測證據。[小米智能卡官方政策](https://privacy.mi.com/smartcards/zh_CN/)也描述門卡與安全元件的開通業務，但它不是 Redmi Global 相容性清單或可任意呼叫的第三方 SDK。

本手機也宣告 eSE 並存在 `mi_nfc` / NFC / secure-element 服務。**硬體存在與本 App 取得發卡權限是不同條件**。沒有嘗試私有 Binder 指令、修改固定 UID、竄改安全元件或系統 NFC 路由。

## 剩餘路線與可驗證問題

1. 小米官方門卡路線：需確認本機型 Global ROM 是否有正式可安裝、可授權、可開通的智能卡服務。本次未確認可用官方安装来源与完整能力鏈；不從不明 APK 镜像補齊系統元件。取得官方元件也必須先驗證簽章、權限、機型與地區相容，不能只以安裝成功下結論。
2. 校方官方手機憑證：需校方說明門禁控制器／讀卡機型號、是否支援 ISO-DEP/HCE/mobile credential，並核發相容本人憑證。沒有公開資料證明北科大目前提供這項服務。
3. 由管理端登記新的官方手機或穿戴式憑證：需校方明確接受此裝置與憑證。不能把「可登記卡片造型 EasyCard」解讀成可登記隨機 UID 的 Android HCE。

可發給宿舍管理端的技術問題：

> 我的 Redmi Note 14 Pro+ 5G 為 Global HyperOS / Android 15，學生證讀卡顯示 MIFARE Classic。宿舍讀卡機的型號及驗證方式是什麼？是否支援由校方核發 Android NFC/HCE/Mobile Credential，或登記正式手機／穿戴裝置憑證？同一張學生證已可在 realme 系統錢包的門禁卡功能使用，校方是否允許及支援這類手機方案？

本 Agent 沒有聯繫或向管理單位發送任何訊息。

## 停止條件

普通 HCE 原卡替代路線已被卡種實測阻擋，停止擴充此路線。合成 HCE 僅保留為官方相容憑證研發的測試底座。未取得可用 OEM／校方途徑前，真實門禁、待機、鎖屏、熄屏、十次開門均不能標為成功。
