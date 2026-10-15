package com.example.android.cardemulation;

import java.lang.reflect.InvocationTargetException;

/** Only controlled codes and exception types may leave the private controller. */
final class VendorFailure extends Exception {
    final String stage, code;
    VendorFailure(String stage, String code, Throwable cause) {
        super(code, cause);
        this.stage = stage;
        this.code = code;
    }
    static VendorFailure at(String stage, Throwable cause) {
        return cause instanceof VendorFailure ? (VendorFailure) cause
                : new VendorFailure(stage, "CALL_FAILED", cause);
    }
    String errorType() {
        Throwable error = getCause();
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error == null ? "ValidationFailure" : error.getClass().getSimpleName();
    }
    String description() {
        if ("SecurityException".equals(errorType()) || "PERMISSION_REQUIRED".equals(code))
            return "尚未取得系統 NFC 設定權限，請查看授權方式並完成一次電腦授權。";
        switch (code) {
            case "NFC_OFF": return "請先開啟 NFC";
            case "NFC_UNAVAILABLE": return "此手機未提供 NFC 功能。";
            case "UNVERIFIED_DEVICE": return "此機型或系統版本尚未支援卡片模擬，並非一般 NFC 權限問題。授權後也需先完成裝置適配。";
            case "PROFILE_REQUIRED": return "請先感應本人學生證，錄入門卡參數";
            case "NO_NCI_READBACK": return "系統未回傳此 NFC 參數，不能安全設定";
            case "NO_CONTROLLER_READBACK": return "無法讀取控制器原配置，不能安全設定";
            case "ENABLE_READBACK_MISMATCH": return "控制器未接受測試參數，已嘗試恢復";
            case "RESTORE_READBACK_MISMATCH": return "恢復核對失敗，已保留復原資料";
            default: return code.endsWith("_BASELINE_MISMATCH")
                    ? "目前 NFC 配置與已核驗基線不同，未寫入設定" : "啟用未完成；請在設定檢查授權與裝置相容性，原卡片資料已保留。";
        }
    }
    boolean needsAuthorization() { return "PERMISSION_REQUIRED".equals(code) || "SecurityException".equals(errorType()); }
}
