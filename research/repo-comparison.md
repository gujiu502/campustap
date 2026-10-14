# 開源選型與研究關卡 R0

查核日期：2026-10-15（Asia/Kuala_Lumpur）。GitHub REST API 即時查核最後 commit、授權與封存狀態；不是以 stars 判定可用性。

| 上游 | SHA / 最後 commit | 授權 / 狀態 | 任務選擇 |
|---|---|---|---|
| [Google/AOSP CardEmulation](https://github.com/googlearchive/android-CardEmulation) | `167556ae24fd9ffe378715aa13bc197c9ca44361` / 2019-10-04 | Apache-2.0，已封存 | 採用；官方範例、最小非付款 HCE、保留 Git 歷史與 LICENSE |
| [grundid sample](https://github.com/grundid/host-card-emulation-sample) | `8b9b9272ffc58a9f32b6a6125a4508cbbcb79baf` / 2017-05-04 | MIT，未封存 | Eclipse/Ant 舊結構，移植面比官方 Gradle 範例大 |
| [layonmartins sample](https://github.com/layonmartins/RFID-NFC-CardEmulateSample) | `19b9e1b49c4b216de01c5748561fda6ed71801f9` / 2022-09-19 | API 沒有識別到授權 | 不採用；未確認可再散布授權，不因 Kotlin 改寫引入額外依賴 |
| [NFCGate](https://github.com/nfcgate/nfcgate) | `35f73eef091433800e75642b0517fd6a8f3c84eb` / 2026-07-20 | Apache-2.0，未封存 | 成熟協定研究工具，但 root/hook/中繼等功能不適合本 App 範圍；未安裝或部署 |

架構上，NFCGate 提供更完整的研究功能；本任務最佳母工程是官方小型範例。沒有確認到能直接滿足「Android 15 Global Redmi + 原学生证门禁」的維護中授權方案或 fork。這是搜尋結果的範圍，不能解讀成證明所有 fork 都不存在。

## 實際 build 與修改面

先 clone 官方上游，再試跑原 `:Application:assembleDebug`。Gradle 4.4 在本機 JDK 17.0.12 直接失敗：`Could not determine java version from '17.0.12'`。

升級 Gradle 8.13、AGP 8.13.2、compile/target SDK 35、min SDK 31、Java 17。本診斷版只針對實測 Android 15；沒有承諾舊 Android 相容性。保留 Application 模組、原 namespace、launcher icon、HCE service 模式；改成原生 Activity，不增加 Compose/AndroidX 依賴。刪除舊 Fragment、帳號儲存、support logger、相應 layout/menu 及不適用的舊測試。正式 App 不傳回任意帳號，HCE 只回固定的合成測試字串。

在中文原路徑建置 APK 成功，但 JUnit worker 找不到測試類別。使用臨時 ASCII `subst X:` 後三個測試通過；`tools/build.ps1` 使用 finally 移除該映射。

Gradle wrapper 由 8.13 自行重新產生；distribution SHA-256 取自 [Gradle 官方校驗檔](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256)，值為 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`。

## 權威來源與結論

- [Android HCE](https://developer.android.com/develop/connectivity/nfc/hce)：HostApduService 模擬 ISO-DEP/APDU；UID 应视为随机，不是任意固定 UID 設定介面。`CATEGORY_OTHER` 與付款錢包角色應分開。
- [小米機型 NFC FAQ](https://www.mi.com/tw/support/faq/details/KA-535595/)：硬體 NFC 支援，不能據此推導 Global ROM 可錄入所有門卡。
- [realme 舊版官方手冊](https://gkm-sg-oppo-com.s3.ap-southeast-1.amazonaws.com/realme/attch/realmeUI1.0-V1.0-cn_793184718081363968.pdf)：描述廠商門卡功能；這是舊版資料，不能套作 UI 7.0 的完整相容清單。
- 使用者於本次對話確認 realme 成功路線是「系統錢包裡的門禁卡」；精確機型與錢包版本未知。此為本人觀察，不冒充本 Agent 做過 realme 實測。
- [北科大東校區宿舍公告](https://eastdorm.ntut.edu.tw/p/406-1048-145545,r1275.php?Lang=zh-tw)：住宿生使用學生證；公告未提供 Android 發卡 SDK。
- [北科大新北校區入宿程序](https://newtaipei.ntut.edu.tw/p/404-1058-97366.php?Lang=en)：提及登記卡片造型 EasyCard。此政策不能外推到所有宿舍或 HCE 手機。

没有校方授權手機憑證或可用 OEM 門卡服務之前，不把示例 APK 當作門卡整合成功。
