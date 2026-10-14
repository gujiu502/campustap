# 悠遊卡官方方案

查核日期：2026-10-15（Asia/Kuala_Lumpur）。使用者確認原學生證兼具門禁與悠遊卡；本 App 只讀卡片技術，不確認帳戶、餘額或学生票資格。

**最終需求澄清**：使用者要的是把原實體悠遊卡添加到 Redmi，不要動現有悠遊付；「不能就算了」後停止。本次沒有開啟／操作悠遊付。下表官方 App 路線只是研究對照，不是本次需求的替代交付。

| 能力 | 狀態 | 意義 |
|---|---|---|
| 實體卡管理 | VIA_OFFICIAL_APP | 已安裝 `com.easycard.wallet`；是否可加入這張學生證尚未操作驗證 |
| 嗶乘車 | VIA_OFFICIAL_APP | Android 15 + NFC 符合公開基本要求；本人帳戶開通、學生票、實際過閘未測 |
| 本機原生虛擬悠遊卡 | UNSUPPORTED_BY_CURRENT_PUBLISHED_DEVICE_LIST | 小米官方行動悠遊卡清單是特定台灣 NFC 穿戴裝置，未列此 Redmi 手機 |
| 原學生證憑證／餘額直接搬入手機 | NO_VERIFIED_OFFICIAL_ROUTE | 本 App 不仿造或搬移卡內支付資料 |

[悠遊付嗶乘車官方說明](https://easywallet.easycard.com.tw/tutorial/step?cls=500000000&cls2=510000000&cls3=512000000)的基本要求是 Android 9.0+ NFC；使用時打開嗶乘車畫面並完成解鎖驗證，扣款來自悠遊付錢包。這不是把原實體學生證轉成同一張手機票證。

[管理實體卡官方流程](https://easywallet.easycard.com.tw/tutorial/step.php?cls=400000000&cls2=1609904608&cls3=1609905062)應在官方 App 內執行；本診斷 App 只提供開啟官方已裝 App 的按鈕，不接觸登入資料、不自動新增或聲稱綁定成功。

[悠遊卡公司小米相容清單](https://www.easycard.com.tw/mobilecard?id=1717472520)本次列出 Xiaomi 手環 9/10/11 NFC、手環 10 Pro NFC、Watch S5 46mm、REDMI Watch 6 NFC 等穿戴型號。官網清單會更新；本次沒有列出 Redmi Note 14 Pro+ 5G。[小米 FAQ](https://www.mi.com/tw/support/faq/details/KA-723383/)是另一查核入口。

學生票必須在官方方案依資格重新確認；不得承諾學生證中的票價資格與原餘額自動保留。交通支付與宿舍授權各自獨立，嗶乘車成功也不代表能開宿舍門。

實機支付／新增學生證／實際過閘：**NOT RUN**。未更改悠遊付帳戶、付款預設或執行交易。
