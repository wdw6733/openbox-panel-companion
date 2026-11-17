# 面板伴侣（Open-Box 面板管理，非官方）

一个原创的 Android 面板管理工具：用手机管理你自己的 Open-Box 路由器端安装。
原生界面做状态、内核、订阅、节点组和配置管理，完整面板用内嵌网页打开，
一个 App 里全部搞定。

**非官方声明**：本项目与 Open-Box 作者无关，不包含、不复制、不分发
Open-Box 的自有程序或源码，没有反编译或绕过授权。Open-Box 自有程序为闭源
专有软件，详见上游仓库的 `LICENSE` 与 `COPYRIGHT.md`。本 App 只用你自己的
面板密码，通过面板的正常登录接口管理你自己的安装。

## 功能

- **多台面板管理**：保存多台服务器（地址、端口、密码），逐台测健康检查。
- **状态总览**：健康检查结果、内核运行与自启状态、平台与容器模式。
- **内核控制**：启动、停止、重启，操作前二次确认，操作后回读状态验证。
- **订阅管理**：查看订阅列表，新增订阅，修改订阅内容。
- **节点组管理**：查看节点组、类型、模式和成员，开关启用状态。
- **配置查看与少量开关**：IPv6、QUIC 屏蔽、直连旁路、TUN 自动重定向，
  改完回读确认。完整配置请在面板网页里改。
- **完整面板**：内嵌 WebView 打开你自己的面板，策略、分流、连接、流量、
  DNS、规则调试、备份导出都在里面操作，登录态保留在手机里。
- **运维工具箱**：一键复制官方文档里的安装、升级、回退、卸载、改端口、
  查密码、重启、日志、Docker 命令；端口冲突提醒；GitHub 最新 Release
  检查，并链接到官方下载页（不转存官方安装包）。
- **安全**：密码加密存在本机，只发给你自己的面板；不设备份，不上传数据，
  没有统计和广告；权限只有联网和查看网络状态，没有 VPN、相机、定位。

## 只用仓库里能看到的接口

原生界面只调用 Open-Box 公开仓库的发布验证脚本（`scripts/release-verify/`）
和文档里出现的接口，不猜测、不嗅探其他接口：

| 接口 | 用途 |
| --- | --- |
| `GET /api/health` | 健康检查 |
| `POST /api/auth/login` | 面板密码登录，拿会话 Cookie |
| `GET /api/openbox/service/status` | 内核运行、自启、平台、容器模式 |
| `POST /api/openbox/service/core/start` | 启动内核 |
| `POST /api/openbox/service/core/stop` | 停止内核 |
| `POST /api/openbox/service/core/restart` | 重启内核 |
| `GET /api/openbox/subscriptions` | 订阅列表 |
| `POST /api/openbox/subscriptions` | 新增订阅 |
| `PATCH /api/openbox/subscriptions/{id}` | 修改订阅内容 |
| `GET /api/openbox/groups` | 节点组列表 |
| `PUT /api/openbox/groups` | 回写整份节点组（只改启用开关） |
| `GET /api/openbox/profile` | 配置读取 |
| `PUT /api/openbox/profile` | 少量配置开关（以面板回读为准） |

连接、流量、节点测速、策略切换等没有公开接口佐证的功能，一律在内嵌
面板网页里完成，不做假的原生界面。

版本检查另外调用 GitHub 公开 API
（`api.github.com/repos/liandu2024/Open-Box/releases`），只读最新 Release
信息，不登录、不传你的任何数据。

## 下载 APK

推荐用 GitHub Actions 构建：每次推送到 `main` 分支后，打开仓库的
**Actions → Build APK → 最新一次运行 → Artifacts**，下载
`panel-companion-debug-apk`，解压得到 `app-debug.apk`，在手机上安装即可
（需要允许安装未知来源应用）。打 `v*` 标签推送后，APK 还会自动挂到
对应的 GitHub Release 下面。

当前是 debug 签名 APK，仅供自己使用。不要把它发给别人冒充官方版本。

## 本地构建

需要 JDK 17、Android SDK（API 34、build-tools 34.0.0）、Gradle 8.7：

```sh
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

本仓库没有 Gradle Wrapper（二进制 wrapper jar 不适合走文本推送），
本地构建请直接安装 Gradle。

## 使用

1. 先在路由器、Debian / Ubuntu 或 Docker 上装好 Open-Box 路由器端，
   记下面板地址和端口（新装默认 3036，老版本可能是 2026）。
2. 打开 App，在「工具 → 服务器」里添加服务器：名称、http/https、主机、
   端口、面板密码。点「测试连接」确认能连通、密码正确。
3. 「总览」里刷新状态、启停内核；「管理」里改订阅、节点组和配置开关；
   「面板」里打开完整面板网页做其他所有操作。
4. 面板是 HTTP 且没有 TLS，只能在局域网里用，或者走你自己的 VPN、
   SSH 隧道、带 TLS 的反向代理。不要把面板端口直接暴露到公网，
   上游文档也是这么警告的。

## 局限与已知问题

- 原生接口没有经过真实面板实测（开发环境没有可用的面板），字段解析
  全部按“缺字段也不崩溃”来写，遇到面板返回的新字段会原样展示，不会
  报错。如果某个原生页面在你的面板上显示异常，请在面板网页里操作，
  并把报错截图（不要带密码）发给维护者。
- 节点组开关用的是“读整份、改一个字段、写回整份”，写回前会二次确认。
  如果面板版本改了节点组结构，写回可能被拒绝，App 会显示面板返回的
  错误，不会静默失败。
- 配置开关只发最小的部分更新，面板是否接受以回读结果为准。
- 订阅没有删除接口（公开脚本里没有出现），删除请在面板网页里操作。
- WebView 登录和原生接口登录是两套会话：App 会尽力把原生登录的
  Cookie 同步给 WebView，同步不上时在网页里再登录一次即可，密码不
  会被 App 自动填进网页。
- 首次访问面板需要先设置密码的，请先在浏览器或面板页里完成设置，
  不要用本 App 去调用初始化接口。

## 隐私

- 服务器地址、端口、密码只存在手机本地，密码用 Android 密钥库加密
  存储；如果设备不支持加密存储，App 会在「工具」页提示，并退回普通
  本地存储。
- App 不申请备份，换手机需要重新添加服务器。
- 除你自己的面板和 GitHub 公开 Release API 外，App 不连接任何服务器。

## 许可证

本仓库的原创代码按 MIT 许可证发布，见 `LICENSE`。Open-Box 商标、
名称与自有程序的权利归其作者所有。
