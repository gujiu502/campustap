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
    static VendorFailure authorizationFailure(boolean nfcGranted, boolean systemGranted) {
        if (!nfcGranted) return new VendorFailure("CHECK_PERMISSION", "NFC_PERMISSION_REQUIRED", null);
        return systemGranted ? null : new VendorFailure("CHECK_PERMISSION", "PERMISSION_REQUIRED", null);
    }
    String errorType() {
        Throwable error = getCause();
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error == null ? "ValidationFailure" : error.getClass().getSimpleName();
    }
    String description() {
        if ("SecurityException".equals(errorType()) || "PERMISSION_REQUIRED".equals(code))
            return "未取得卡片模擬所需的系統權限，無法在 App 內直接取得。";
        switch (code) {
            case "NFC_PERMISSION_REQUIRED": return "系統未授予基本 NFC 存取權限。";
            case "NFC_OFF": return "請先開啟 NFC";
            case "NFC_UNAVAILABLE": return "此手機未提供 NFC 功能。";
            case "VENDOR_INTERFACE_UNAVAILABLE": return "此手機的系統未提供目前使用的 NFC 模擬介面。";
            case "PROFILE_REQUIRED": return "請先感應本人學生證，錄入門卡參數";
            case "NO_NCI_READBACK": return "系統未回傳此 NFC 參數，不能安全設定";
            case "NO_CONTROLLER_READBACK": return "無法讀取控制器原配置，不能安全設定";
            case "ENABLE_READBACK_MISMATCH": return "控制器未接受測試參數，已嘗試恢復";
            case "RESTORE_READBACK_MISMATCH": return "恢復核對失敗，已保留復原資料";
            default: return code.endsWith("_BASELINE_MISMATCH")
                    ? "目前 NFC 配置與已核驗基線不同，未寫入設定" : "啟用未完成；請在設定檢查授權與裝置相容性，原卡片資料已保留。";
        }
    }
    boolean needsAuthorization() { return "PERMISSION_REQUIRED".equals(code) || "NFC_PERMISSION_REQUIRED".equals(code) || "SecurityException".equals(errorType()); }
}
