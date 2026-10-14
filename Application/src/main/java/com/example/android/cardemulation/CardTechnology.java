package com.example.android.cardemulation;

import java.util.Arrays;

public final class CardTechnology {
    public static String describe(String[] technologies) {
        if (Arrays.asList(technologies).contains("android.nfc.tech.IsoDep"))
            return "ISO-DEP／APDU；門禁應用與憑證仍待發行方確認";
        if (Arrays.asList(technologies).contains("android.nfc.tech.MifareClassic"))
            return "MIFARE Classic；一般 Android HCE 無法模擬此協定";
        if (Arrays.asList(technologies).contains("android.nfc.tech.NfcA"))
            return "NFC-A；未宣告 ISO-DEP，一般 HCE 路線尚未成立";
        return "已偵測 NFC 標籤；門禁協定仍未知";
    }
}
