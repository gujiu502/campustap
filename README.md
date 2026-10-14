# CampusTap — Redmi 學生證實機診斷工程

更新：2026-10-15（Asia/Kuala_Lumpur）。**診斷 APK 已編譯並曾安裝完成實機讀卡；手機代替學生證開門／加入原實體悠遊卡未完成，狀態 BLOCKED。使用者要求「不能就算了」後已停止，診斷 App 已從手機移除，電腦保留原碼、APK 與證據。**

## 核心結果

使用者的 Redmi Note 14 Pro+ 5G（24115RA8EG、Global `OS2.0.2.0.VOPMIXM`、Android 15）實際讀到原雙功能學生證，技術為 MIFARE Classic / NFC-A，未宣告 ISO-DEP。普通 Android HostApduService 僅支援 ISO-DEP/APDU，不能直接取代原卡協定。完整理由、實機證據、OEM／校方替代路線見 [可行性報告](docs/feasibility.md)。

使用者確認另一台 realme 是透過系統錢包門禁卡錄入成功。本 Redmi 已裝小米錢包，但缺少 `com.miui.tsmclient` 門卡元件，暫未找到經核驗的官方開通途徑。這是目前這台手機的檢查結果，不是所有小米手機的永久能力判斷。

使用者最後澄清：要添加原實體悠遊卡，不要操作現有悠遊付；官方嗶乘車不是本次需求。本次從未開啟或操作悠遊付，未新增卡片、開通帳戶、改付款角色或執行交易。[悠遊卡研究資料](docs/easycard-feasibility.md)僅保留為方案差異的參考，不是已執行的整合。

## 來源與版本

本專案是 [Google/AOSP CardEmulation](https://github.com/googlearchive/android-CardEmulation) 的本機 Git clone / 衍生工程，保留 upstream 歷史，並非建立公有 GitHub fork。上游 SHA `167556ae24fd9ffe378715aa13bc197c9ca44361`，Apache-2.0，見 [LICENSE](LICENSE) 及 [選型記錄](research/repo-comparison.md)。沒有公開上傳私人設備或卡片資料。

工具鏈：JDK 17、Gradle 8.13（校驗碼已固定）、AGP 8.13.2、Android compile/target SDK 35、min SDK 31。Runtime 只依賴 Android 平台；JUnit 4.13.2 僅用於 JVM 測試。APK 是 debug 簽名的本地診斷版，不是正式門卡／儲值支付產品。

## 實作範圍

單頁原生 Android UI（繁中）：真實 NFC/HCE 狀態、NFC 與付款設定入口、限時只讀卡種偵測、合成 HCE 測試、官方悠遊付啟動入口、去識別化報告分享與診斷清除。第一次開啟且 NFC on 時自動只讀偵測一次，30 秒超時；離開前景或偵測結束時退出 reader mode。

只讀學生證時呼叫公開 Tag 技術資訊與 NFC-A SAK/ATQA，不讀卡內扇區、金鑰、餘額或交易資料。不儲存完整 UID、UID hash、學生姓名／學號。手動「測試另一部 CampusTap」才對自有測試裝置送合成 SELECT；必須先移開學生證。

HCE 的唯一 AID 是 `F043414D505553`，類別 `other`，只傳回 `CAMPUS_TEST_ONLY` + `9000`；不處理付款 AID，不可修改帳號／UID 冒充門卡。示例服務要求亮屏及解鎖；背景、鎖屏、熄屏等行為不能冒充實測。

## 檔案職責與介面

| 檔案 | 職責 |
|---|---|
| `Application/src/main/java/.../MainActivity.java` | 公開 API 設備診斷、ReaderCallback、30 秒限時與前景生命週期、設定與官方 App 入口、私有 JSON |
| `CardTechnology.java` | 依系統技術清單分類，不推斷門禁金鑰／支付餘額 |
| `TestProtocol.java` | 合成 SELECT 解析與固定回應，未知或格式錯誤 APDU 拒絕 |
| `CardService.java` | 保留上游 HostApduService 架構，只呼叫合成協定；不保存 APDU |
| `Application/src/main/AndroidManifest.xml` | NFC 權限、HCE service、BIND_NFC_SERVICE、必要 package visibility |
| `Application/src/main/res/xml/aid_list.xml` | 唯一合成非付款 AID 路由 |
| `Application/src/test/.../TestProtocolTest.java` | 解析邊界與協定／卡種分類回歸 |
| `tools/collect_device_info.ps1` | 指定/唯一 ADB 設備、驗證 Redmi 型號、僅保存白名單設備欄位 |
| `tools/build.ps1` | ASCII 臨時映射、build/unit/lint、finally 移除映射 |
| `tools/deploy.ps1` | 指定序號與驗證型號後安裝、啟動診斷 APK |
| `artifacts/` | gitignored 設備 JSON、實機截圖、測試報告、APK與校驗碼 |

JSON `capability.json` 包含 NFC/HCE/ROM、官方 App 安裝狀態、錢包角色可用性與未開通門禁狀態。`card_probe.json` 包含技術清單、SAK/ATQA、UID 長度和只讀旗標。`reader_test.json`（只在自有 Reader 測試後產生）包含合成測試 PASS/FAIL，不包含回應封包。

## 可重現命令

從此專案根目錄用 PowerShell 執行：

```powershell
# 需 Git、JDK 17、Android SDK 35、adb；手機已授權 USB 偵錯。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\collect_device_info.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1
# SERIAL 只在本機 adb devices -l 取得，不寫入可公開報告。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy.ps1 -Serial SERIAL
```

Build 腳本使用已驗證的本機 `C:\Program Files\Java\jdk-17` 與標準 `%LOCALAPPDATA%\Android\Sdk`，若路徑不同須調整腳本。`X:` 必須空閒；中文路徑曾造成 JVM 測試 classpath 問題，勿只看 APK 成功便略過單元測試。

APK：`artifacts/builds/CampusTap-diagnostics-debug.apk`，SHA-256：同目錄 `SHA256.txt`。亦可由 `Application/build/outputs/apk/debug/Application-debug.apk` 重建取得。建置 metadata 在 `artifacts/builds/build_manifest.json`。

## 驗收、限制、回退

詳見 [測試矩陣](docs/test-matrix.md)。已驗證 USB、公開 NFC/HCE API、原卡只讀、真實 UI、非付款合成 AID 系統登記、三個 JVM 測試與 lint。自有 Reader 實際 HCE 往返、真正門禁開門、付款交易等沒有實測，不推定成功。

真正完成須取得相容、合法的 OEM 或校方手機憑證，並由本人在有權使用的宿舍門禁現場記錄至少十次成功；在此之前 App 永遠不顯示已開門。

普通 APK 不設定 Android 15 NFC 開機強制啟用、不申請系統特權、不要求常駐服務、不搶 Google/悠遊付付款角色。沒有 root、解鎖 bootloader、刷 ROM 或更改 `/system`。系統預設與支付可用性以實機回歸為準。

移除診斷版：在 Android 系統解除安裝，或指定正確目標執行 `adb -s SERIAL uninstall tw.campustap.diagnostics`。解除安裝移除本 App 私有診斷與合成 AID，不會刪學生證、票證帳戶或改動使用者現有 App。App 內「清除診斷」只刪此 App 的報告。

程式回退可用上游 commit，但原上游是舊範例且 JDK 17 無法原樣建置；工程修改已存在本機 Git checkpoint，不要將回退誤當可用門卡版本。

## 後續最小一步

先核驗本 Global 機型的官方智能卡開通支持，或取得校方讀卡機型號及官方手機憑證程序。找到可行路線再接入並做門禁十次驗證。不要擴充被卡種否定的原卡 HCE 仿製路線。
