# 晚点名助手（智汇福大）

![白底黑字“签”图标](docs/icon.png)

一个只在本机运行的 Android 辅助工具：到点提醒解锁，随后自动完成“打开智汇福大 → 我的服务 → 晚点名签到 → 等待定位 → 点击签到”，并报告结果。

> **当前状态：一键真实签到已跑通；低内存杀进程问题已加前台保活修复；60 秒定时测试已跑通（已签到场景自动识别并关闭）。**  
> 明天 21:03 是第一次“未签到状态”的完整定时实测；锁屏/勿扰弹出仍需手机权限配合。

---

## 先读哪份文档

| 顺序 | 文档 | 用途 |
|---|---|---|
| 1 | [03-当前进度.md](03-当前进度.md) | **下次开始先读这个**：现在做到哪、卡在哪、下一步做什么 |
| 2 | [01-产品需求.md](01-产品需求.md) | 要做什么、不做什么、验收标准、已确认需求 |
| 3 | [02-软件架构.md](02-软件架构.md) | 怎么实现：模块、状态机、无障碍、调度、权限、测试 |

---

## 一句话需求

- **用户**：福州大学旗山校区、vivo X100（V2309A，Android 16）、本人住校。
- **目标 App**：智汇福大，已确认 `cn.edu.fzu.fdxypa` / `cn.edu.fzu.fdxy_app.MainActivity`，v1.17.4。
- **触发**：每天 21:03 自动；桌面“立即签到”兜底。
- **锁屏**：到点自动亮屏提示；**密码由用户自己解**，解锁后全自动。
- **成功**：以页面“签到成功”为准；没有证据就标记“不确定”，不重复点击。
- **重试**：明确失败后每 30 秒一次，最多 5 次；23:55 截止。

---

## 当前已验证

- JDK 17、Android SDK 36、Build Tools 36、Gradle 8.11.1、AGP 8.10.1 可编译。
- adb 识别手机 `10ADBX11S50024P`。
- APK 已安装；无障碍服务可连接。
- 首页“晚点名签到”入口：`content-desc="晚点名签到"`，clickable。
- 签到页是 WebView；无障碍服务可读取内部文本（无需 `uiautomator`）。
- “无法签到”是 WebView 内 `TextView`，`clickable=false`，需要按 bounds 手势点击。
- **演练模式真机通过 3 次**：首页 → 签到页 → 识别按钮状态。
- **一键真实签到真机跑通**：点图标 → 自动进入签到页 → 按钮“签到” → 点击 → 读到“签到成功” → 停留 1 秒 → 回桌面并退出智汇福大界面（后台进程可能保留）。
- 已签到页面会识别“签到成功/已签到”，**不会重复点击**。
- 应用图标：白底黑字“签”。

## 尚未完全验证

- 明天 21:03 首次“未签到状态”的完整定时实测。
- 锁屏 + 勿扰模式下自动弹出/全屏提示还会被系统挡住，需要按下面权限设置。
- 失败重试、历史记录。

---

## 定时要求（重要）

到 21:03 想让它自动跑，手机上至少要有：

1. **精确闹钟**：App 内点“允许精确闹钟”；
2. **通知权限**：允许通知、锁屏通知、横幅；
3. **后台弹出界面**：App 内点“允许后台弹出界面”；
4. **电池/自启动**：设置 → 应用管理 → 晚点名助手 → 允许自启动、后台运行、后台高耗电；
5. **勿扰模式**：允许闹钟/重要提醒，或 21:03 前关闭勿扰；
6. 手机解锁后，才会真正点击签到；锁屏密码仍然由你输入；
7. 通知栏保留“晚点名助手待命中”，不要划掉；这是前台保活服务，防止签到跑到一半被系统杀掉。

---

## 下载 / 安装

- APK：[dist/rollcall-helper-debug.apk](dist/rollcall-helper-debug.apk)
- 手机下载后点 APK，按系统提示允许“安装未知应用”，再点“安装/更新”。
- 安装后打开 App，按提示开启无障碍服务。
- 这是 **debug 签名**，适合自己侧载测试；公开分享前建议生成 release 签名。
- `artifacts/`（真机截图/节点）含姓名、班级等个人信息，已加入 `.gitignore`，不要上传。

---

## 构建

```powershell
# 一键构建并安装
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1

# 手动
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. .\tools\env.ps1
.\tools\gradle-8.11.1\bin\gradle.bat :app:assembleDebug --no-daemon --no-watch-fs --console=plain
adb install --no-streaming -r .\app\build\outputs\apk\debug\app-debug.apk

# 如果 install 卡住：
# adb push .\app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/rollcall.apk
# adb shell pm install -r /data/local/tmp/rollcall.apk
```

工具链在 `tools/`，不依赖系统级 Android Studio。已知坑见 [当前进度](03-当前进度.md) 第 5.1 节。

---

## 硬性边界

本工具**不做**：

- GPS 伪造、虚拟定位、异地代签；
- 保存/输入账号密码、锁屏密码；
- 自动过人脸、验证码、滑块；
- 绕过学校风控或 Android 安全锁屏；
- 把界面数据/截图上传网络。

使用前请自行确认学校/学院是否允许自动化签到；如明确禁止，请停止使用。

---

## 目录

```
.
├── README.md
├── 01-产品需求.md
├── 02-软件架构.md
├── 03-当前进度.md
├── app/                  # Android 工程（无障碍/一键签到）
├── dist/                 # 可安装的 debug APK
├── docs/                 # 图标等公开素材
├── artifacts/            # 真机截图/dump（含 PII，已 gitignore，不上传）
└── tools/                # JDK/SDK/Gradle + 构建/抓取脚本（大文件已 gitignore）
```

---

## 下次续接

直接说：

> 继续晚点名项目。先读 `03-当前进度.md`，检查 `adb devices` 和无障碍服务；一键真实签到已跑通，下一步做 21:03 定时 + 锁屏门禁。
