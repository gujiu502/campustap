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
        if (stage.startsWith("OPLUS") && "SecurityException".equals(errorType()))
            return "系統拒絕此 NFC 介面呼叫，App 無法自行解除此限制。";
        if ("SecurityException".equals(errorType()) || "PERMISSION_REQUIRED".equals(code))
            return "未取得卡片模擬所需的系統權限，無法在 App 內直接取得。";
        switch (code) {
            case "NFC_PERMISSION_REQUIRED": return "系統未授予基本 NFC 存取權限。";
            case "NFC_OFF": return "請先開啟 NFC";
            case "NFC_UNAVAILABLE": return "此手機未提供 NFC 功能。";
            case "VENDOR_INTERFACE_UNAVAILABLE": return "尚未找到可安全啟用的 NFC 模擬介面，讀卡與卡片管理仍可使用。";
            case "OPLUS_FEATURE_UNAVAILABLE": return "此系統未開放直接卡片模擬功能，未更改 NFC 設定。";
            case "OPLUS_STATE_UNREADABLE": return "系統未完整提供原 NFC 狀態，無法安全啟用。";
            case "OPLUS_STATE_MISSING": return "系統未提供必要的 NFC 初始狀態，暫不能啟用。請在設定複製相容性資訊。";
            case "OPLUS_STATE_FORMAT_UNSUPPORTED": return "此系統回傳的 NFC 狀態格式尚未支援。請在設定複製相容性資訊。";
            case "OPLUS_UID_STATE_MISMATCH": return "系統回傳的兩處卡片狀態不一致，已停止設定。請在設定複製相容性資訊。";
            case "OPLUS_SET_REJECTED": return "系統未接受卡片設定，已嘗試恢復原狀態。";
            case "OPLUS_ENABLE_MISMATCH": return "卡片設定回讀不一致，已嘗試恢復原狀態。";
            case "OPLUS_RESTORE_REJECTED": return "系統未接受恢復操作，已保留復原資料。";
            case "OPLUS_STATE_CHANGED": return "NFC 狀態已由其他操作變更，已保留復原資料，未覆蓋目前卡片。";
            case "RECOVERY_BACKEND_CONFLICT": return "存在多組待恢復資料，已停止設定並保留資料。";
            case "PROFILE_REQUIRED": return "請先感應本人學生證，錄入門卡參數";
            case "NO_NCI_READBACK": return "系統未回傳此 NFC 參數，不能安全設定";
            case "NO_CONTROLLER_READBACK": return "無法讀取控制器原配置，不能安全設定";
            case "ENABLE_READBACK_MISMATCH": return "控制器未接受測試參數，已嘗試恢復";
            case "RESTORE_READBACK_MISMATCH": return "恢復核對失敗，已保留復原資料";
            default: return code.endsWith("_BASELINE_MISMATCH")
                    ? "目前 NFC 配置與已核驗基線不同，未寫入設定" : "啟用未完成；請在設定檢查授權與裝置相容性，原卡片資料已保留。";
        }
    }
    boolean needsAuthorization() { return "PERMISSION_REQUIRED".equals(code) || "NFC_PERMISSION_REQUIRED".equals(code)
            || (!stage.startsWith("OPLUS") && "SecurityException".equals(errorType())); }
}
