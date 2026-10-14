# 驗證矩陣

日期：2026-10-15。`PASS` 只涵蓋表內描述的檢查；`NOT RUN` 不可推定成功。

| 檢查 | 狀態 | 證據／限制 |
|---|---|---|
| M01 指定目標 USB ADB | PASS | 型號驗證、序號不寫入報告 |
| M02 NFC/HCE/ROM/現有服務 | PASS | `artifacts/device/capability_report.json` + `capability.json` |
| APK 編譯與安裝 | PASS | Gradle assembleDebug、ADB install Success、am start Status ok |
| 三個單元測試 | PASS | 接受自有 AID 與可選 Le；拒絕錯誤／截斷 APDU；hex 邊界；卡種不混同 |
| Android lint | PASS | 零 error；SDK 35 固定版本與繁中拼接文字仍有 warning，見報告 |
| 原學生證只讀探測 | PASS | `card_probe.json`，MIFARE Classic / NFC-A；不讀扇區／金鑰／餘額 |
| 檢測結束 NFC reader mode 退出 | PASS | dumpsys `mIsReaderMode=false`，NFC 持續 on |
| App 畫面 | PASS | `artifacts/tests/device-ui.png` 實機截图；可捲動，結果文字與來源證據一致 |
| 合成 AID 非付款登記 | PASS | 系統登記 CATEGORY_OTHER、BIND_NFC_SERVICE，未搶付款角色 |
| M03 NFC 關閉時提示及設定導航 | BLOCKED（完整切換 NOT RUN） | 嘗試 ADB NFC-off，ROM 拒絕非 system/device-owner 呼叫；未實際關閉，不冒充測完 |
| M04 NFC 重新開啟時更新 | BLOCKED（完整切換 NOT RUN） | ADB NFC-on 同樣遭拒；另行讀取證實 NFC 仍為 on，沒有改變初始狀態 |
| M05 自有讀卡器 20 次 HCE 往返 | NOT RUN | 僅一部 Android，未發現 PC 智慧卡讀卡器；學生證是被動卡，不是讀卡器 |
| M06 背景 HCE 往返 | NOT RUN | 系統路由登記不等於實際往返 |
| M07 鎖屏／熄屏 HCE | NOT RUN | 診斷服務刻意要求亮屏及解鎖；未承諾原門禁狀態 |
| M08 重啟後 NFC 與路由 | NOT RUN | 未重新啟動使用者手機 |
| M09 付款共存（預設與路由） | PASS（有限） | Google wallet holder 前後相同，沒有付款 AID；真實交易 NOT RUN |
| M10 宿舍真實十次開門 | BLOCKED | 沒有相容手機憑證／OEM 門卡；沒有現場門禁結果 |
| M11 權限撤銷 | NOT RUN | 未撤销 NFC/系统授权；未測撤銷恢復 |
| M12 USB 拔除後運作 | NOT RUN | 未實體拔線；App 本身不依賴電腦，但不能推定已驗證 |
| M13 悠遊付付款／學生票 | NOT RUN | 已裝官方 App；未開通、綁卡或交易 |

构建／单元测试报告在 `Application/build/reports/`，当地保留，不对外发布；设备报告、截图、APK 在 gitignored `artifacts/`。
