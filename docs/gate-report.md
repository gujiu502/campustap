# 当前交付关卡

2026-10-15，0.6.0-multi-card。PASS 限于下表证据，实际开门未验证。

| 关卡 | 状态 | 证据与边界 |
|---|---|---|
| 上游与授权 | PASS | Google/AOSP CardEmulation、Apache-2.0、保留历史 |
| 原卡识别与加密 | PASS | 本人 NFC-A / Classic，仅识别参数 |
| 原设定失败 | 已修复并复测 | 动态 UID／ATS 被误判为空，严格校准该 ROM |
| 连续真实贴卡新增 | PASS | 旧版卡 + 两次新录入 = 3 个条目和独立加密文件 |
| 点击卡片切换 | PASS | 第二张卡 ID 与控制器回读一致，无中间测试／编辑页 |
| 三点编辑入口 | PASS | 右上菜单与编辑页运行截图 |
| 默认卡冷启动 | PASS | 菜单设默认、force-stop 后启动，默认 ID 回读一致 |
| 后台锁屏配置保留 | PASS | interactive=false、keyguardLocked=true、verifyActive PASS |
| 熄屏实际 RF／开门 | NOT RUN | 参数与 host routing 不是实际读头认证证明 |
| 暂停／NFC 关开 | 见实机报告 | 最终状态以运行证据为准 |
| 工程入口隔离 | PASS | 正常无门禁测试；开发 Activity 非导出，探针仅 debug 且权限保护 |
| 本地构建／单元／lint | 见 manifest | 17 个 JVM 测试，lint 数量按最终机器报告 |
| 系统选图完整流程 | NOT RUN | 几何测试不替代系统选图与手势实测 |
| 原实体悠游卡导入 | NOT IMPLEMENTED | 没操作悠游付、不执行交易 |

持久启用是用户要求的正式行为，保留恢复快照；离开 App 或两分钟到期不再恢复。主动暂停关闭自动启用。恢复核对通过才删除快照。NFC 付款前暂停，恢复未通过前不要卸载或清数据。

当前证据 artifacts/tests/multi-card-20261015；旧版限时历史 artifacts/tests/live-20261015。无 Root、解锁、校方查询或钱包角色变更。
