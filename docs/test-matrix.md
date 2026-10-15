更新（0.6.1）：Debug／Release各19项通过（新增授权／不支持／NFC关闭区分、机型与ROM联合检查），lint均0 errors / 17 warnings。APK签名有效且与0.6.0相同；Release debuggable=false，无DeviceProbeReceiver，无长按调试入口。用户报告自己的Redmi已可用；新版授权引导UI实机及其他手机适配 NOT RUN。以下为0.6.0历史测试矩阵。

# 验证矩阵

2026-10-15，0.6.0-multi-card。详见 [实机报告](live-device-test.md) 和本地交付 manifest。

| 检查 | 状态 | 范围 |
|---|---|---|
| 精确 Redmi／ROM／WSS／NFC | PASS | 更新保留授权，仅指定真机 |
| 实卡只读／加密 | PASS | Classic／NFC-A，4 字节 UID，不读密钥或交易 |
| 原失败修正 | PASS | 动态 UID 与 ATS 校准，未知与 null 拒绝 |
| Debug／JVM／lint | 见 manifest | 17 项：协议3、卡边界2、配置6、脱敏2、外观文件4 |
| 连续独立添加 | PASS，实机 | 旧卡保留，再录门禁卡与学生证，独立 UUID 与文件 |
| 点击切换 | PASS，实机 | 选择 ID 与控制器回读一致 |
| 三点编辑 | PASS，实机 | 菜单和编辑页已截图 |
| 默认卡冷启动 | PASS，实机 | 设默认、force-stop、重新启动后回读 |
| 后台锁屏配置 | PASS，实机 | OFF_LOCKED 与 host routing，不代表实际 RF |
| 超过旧版两分钟 | 见实机报告 | 带时间的后台探针，不凭界面推定 |
| 暂停恢复／自动关闭 | 见实机报告 | 控制器、NCI、路由核验 |
| NFC 关开自动默认 | 见实机报告 | 真正系统广播后核验，不伪造广播替代 |
| 调试隔离 | PASS | 普通流程无工程页，debug 探针权限保护 |
| 图片裁切几何 | PASS，JVM | 草稿、名称、比例和边界 |
| 选图持久化／完整手势 | NOT RUN | 尚未进行系统选图器实测 |
| 第二台 Reader RF／实际开门 | NOT RUN | 学生证不能作为读头；多卡录入时实卡在手机旁 |
| 整机重启／撤权／ROM 更新 | NOT RUN | 不承诺跨版本与所有启动情形 |
| 悠游卡／支付 | NOT IMPLEMENTED | 不操作悠游付或执行交易 |

0.4.1 与 0.5.0 限时恢复历史分别为 120.431 秒和 120.508 秒，均 PASS；当前正常启用已取消限时。报告、截图、OEM 二进制均 gitignored。
