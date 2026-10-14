# 交付關卡

日期：2026-10-15（Asia/Kuala_Lumpur）。本文件評估實際完成內容，不能以診斷 APK 取代原門禁驗收。

```text
STATUS: BLOCKED (actual phone door access); PASS (delivered read-only diagnostic baseline)
UPSTREAM: https://github.com/googlearchive/android-CardEmulation
SHA: 167556ae24fd9ffe378715aa13bc197c9ca44361
LICENSE: Apache-2.0
CHANGES: local Git checkpoint; exact source commit in artifacts/builds/build_manifest.json
BUILD: powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1
BUILD RESULT: assembleDebug + 3 JVM tests + lint passed
DEVICE: Redmi 24115RA8EG / Android 15 API 35 / OS2.0.2.0.VOPMIXM
CARD: MIFARE Classic + NFC-A; not ISO-DEP, repeated read-only observation consistent
TESTS: install/launch, physical card probe, UI capture, nonpayment AID registration, wallet unchanged
NOT RUN: RF HCE roundtrip, door unlock, reboot, locked/off-screen, USB disconnect, actual payment
NFC SWITCH TEST: attempted; ROM denied shell enable/disable permission; NFC remained ON
RISK: diagnostic debug APK adds only a synthetic CATEGORY_OTHER AID; it cannot open the dormitory
NEXT: verify official OEM door-card provisioning or obtain a compatible issuer mobile credential
ROLLBACK: Android uninstall tw.campustap.diagnostics; upstream source retained in Git
```

| 階段 | 結果 |
|---|---|
| P0 研究與選型 | PASS；官方 API、四個上游及授權比較，有原版 build 失敗證據 |
| P1 真實設備／卡種探測 | PASS；已讀使用者原卡，報告白名單與去識別化 |
| P2 HCE POC | PARTIAL；build/install、合成協定單元測試與系統路由通過；缺第二台 Reader，RF 往返 NOT RUN |
| P3 真實門禁 | BLOCKED；普通 HCE 不相容原卡協定；OEM／校方憑證鏈尚未建立 |
| P4 NFC 預設與免開 App | PARTIAL；提供設定入口，原付款角色未改；NFC 開關被 ROM 限制；重啟／待機驗收 NOT RUN |
| P5 品質與交付 | PARTIAL；本診斷版 APK/原碼/文件/單元/lint/原卡探測完成；門禁可靠性不可驗收 |
| P6 悠遊卡官方路線 | 報告已交付；官方 App 已存在；登入、綁卡、學生票與付款 NOT RUN |

**阻礙的證據**：卡種由 Redmi 的 ReaderCallback 實測，並非只憑「學生證」名稱猜測；普通 HCE 支援範圍由 Android 官方文件確認。門卡元件不存在由 pm 查詢確認；沒有推定安裝任意 OEM APK 就能取得系统权限和发卡能力。

診斷的界線：没有嘗試卡片認證、解密、讀扇區、改寫、模擬支付、發送第三方有效憑證、移植私有 SDK、root 或更改 ROM。本地設備報告與 APK 不推送公有遠端。NFC shell 切換與 input tap 各有明確 SecurityException；沒有改開發者安全權限去繞過。

使用者最終澄清要添加原實體悠遊卡、不要操作悠遊付，並要求不可行就停止；已停止後續實作，未開啟或操作悠遊付。

清理：任務擁有的手機 XML/PNG 暫存及 ASCII build 映射在停止前清理；任務開啟的診斷／小米錢包視窗關閉，診斷 App 已解除安裝，電腦保留原碼、APK 與證據。共享 ADB server、SDK/Gradle 快取與原有手機 App 保留。清理結果另存 `artifacts/tests/cleanup.json`，不能以此文件文字代替驗證。
