# 验证矩阵

2026-10-15，交付 `0.4.0-access-ui`。PASS 只覆盖对应说明；历史检查不代替新版本验证。

| 检查 | 状态 | 范围 |
|---|---|---|
| 指定 Redmi／原卡只读／Reader 退出 | PASS，历史实机 | NFC-A / Classic，不读密钥、扇区、余额 |
| OEM 只读、WSS 授权与加密录入 | PASS，0.3.0 | 控制器／路由读取与加密回读，不证明写入成功 |
| 原门禁测试设置 | FAIL，0.3.0 | 用户报告设定失败，根因待新诊断确认 |
| Debug APK 构建／lint | 见 build_manifest.json | assembleDebug、lint 不跳过；版本、单语与 UI warning 保留 |
| 合成 HCE／APDU／分类测试 | 3 项 | SELECT、边界、错误输入；非支付协议 |
| 实卡参数边界测试 | 2 项 | 限定卡形态、复制输入、错误 UID/ATQA/SAK |
| 基线与设置回读测试 | 4 项 | 截断、无返回、参数不符、UID/ATQA/SAK 双侧回读不符均拒绝 |
| 失败脱敏测试 | 2 项 | 解开反射异常、保留阶段、不导出原异常消息 |
| 卡面模型与裁切测试 | 3 项 | 名称、草稿隔离、比例、平移边界、不留空白、无效尺寸 |
| 新版安装及数据保留 | NOT RUN | Redmi 已断开，旧版数据不主动删除 |
| 新版设置及恢复 | NOT RUN | 两分钟、离开、杀进程重开、撤权／NFC 关闭 |
| 新 UI 实际运行及视觉检查 | NOT RUN | 列表、新增、编辑、导航、系统栏、键盘、旋转 |
| 选图／保存／手动裁切运行 | NOT RUN | 几何算法测试不等于系统选图器实测 |
| 新版预设选择与钱包共存 | NOT RUN | App 选择不等于系统默认卡；实际付款未测试 |
| 第二台 HCE Reader 往返 | NOT RUN | 无自有第二台 Reader，学生证是被动卡 |
| 手机单独开门十次 | NOT RUN | 旧版设定失败，不计成开门验收 |
| 重启、锁屏、熄屏、免开 App | NOT RUN | 当前为前景测试版，不承诺永久后台门卡 |
| 原实体悠游卡／支付 | NOT IMPLEMENTED | 未操作悠游付或进行交易 |

本地报告在 `Application/build/reports/`、`Application/build/test-results/`；交付 manifest 会统计实际通过数量。`artifacts/` 为 gitignored 私人证据，不对外上传。
