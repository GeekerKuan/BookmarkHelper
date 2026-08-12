# BookmarkHelper

BookmarkHelper 是一款面向 Android Root 用户的浏览器数据迁移与管理工具。它可以从受支持的浏览器读取收藏、历史记录和已打开标签页，经过预览与去重后保存到应用内的数据管理，再按需写入另一个浏览器。

本项目目前主要适配 Via 与 Microsoft Edge，最低支持 Android 12。它仍处于测试阶段，不建议直接对唯一一份真实浏览数据进行操作。

> **发布状态：当前版本尚未在酷安或其他应用商店上架。**
>
> 本仓库目前只提供源码与开发进度。测试 APK 仅应从本仓库后续明确创建的 [GitHub Releases](https://github.com/GeekerKuan/BookmarkHelper/releases) 获取；在首个 Release 发布前，请勿将第三方 APK 视为本 Fork 的正式版本。

## 当前能力

| 数据类型 | Via 读取 | Via 写入 | Edge 读取 | Edge 写入 |
| --- | :---: | :---: | :---: | :---: |
| 收藏 | ✓ | ✓ | ✓ | ✓ |
| 历史记录 | ✓ | ✓ | ✓ | ✓ |
| 已打开标签页 | ✓ | 暂不支持 | ✓ | 暂不支持 |

- 支持收藏、历史记录和标签页的独立导入任务。
- 支持按常用天数或精确起止日期读取历史记录。
- 支持在数据管理中搜索、编辑、批量选择、移动、复制、删除和按文件夹整理收藏。
- 支持把全部数据或选中的部分数据写入目标浏览器。
- 记录数据来源，并对相同收藏及相同时间的历史记录进行去重。
- 冲突任务会排队执行，任务进度、预览、确认和结果均在独立页面显示。
- 写入前创建应用私有事务备份，并校验目标文件、权限、所有者与 SELinux 元数据。

## 使用条件

- Android 12 或更高版本。
- 已取得 Root 权限。
- 当前仅识别 Via 国内版 `mark.via` 与 Microsoft Edge 稳定版 `com.microsoft.emmx`。
- 首次测试请使用专门创建的假收藏和假历史记录，并先确认原始浏览器数据已有独立备份。

## 重要限制

- 数据成功写入 Edge 本地文件，不代表 Edge 一定会在界面中显示，也不代表数据会进入微软账号云同步。
- Edge 的账号书签、加密书签、隐私标签、归档标签、内部页面及未知数据格式会被安全拒绝或跳过。
- 标签页写入缺少可稳定验证的跨版本格式，因此当前明确禁用。
- 若检测到浏览器进程重启、文件被并发修改、数据库 sidecar 未合并、校验失败或 Root 命令结果不确定，任务会停止写入并保留诊断现场。
- 当前版本属于 Root 玩家测试版，尚未完成大范围设备、Root 管理器和浏览器版本兼容测试。

## 界面与交互

界面统一使用 [Compose Miuix](https://github.com/compose-miuix-ui/miuix)，并参考 [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 的页面结构与交互实现。应用支持 Miuix 主题、Monet 动态取色、页面缩放、系统触感、悬浮底栏及可关闭的视觉效果。

SukiSU Ultra 相关代码使用 GPL-3.0；包含该部分代码的组合版本发布时必须遵守 GPL-3.0 并提供对应源码。原 BookmarkHelper 文件继续保留其 Apache-2.0 归属，完整许可证与署名见 [`LICENSES/`](LICENSES/) 和 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 隐私与诊断

应用不会把书签、历史记录或数据库上传到服务器。运行日志保存在应用私有目录，并限制数量与大小。连续点击主页中的应用版本 7 次可显示开发者选项，随后可以生成脱敏诊断包。

提交 Issue 时请勿上传真实浏览器数据库、Edge `Bookmarks` 原件、账号截图、未脱敏日志或包含私人网址的数据。推荐附上：

- Android、Via、Edge 和 Root 管理器版本；
- 可复现步骤与实际结果；
- 由应用开发者选项生成的脱敏诊断 ZIP。

## 构建

构建基线：minSdk 31、compile/targetSdk 37、Java 17、Kotlin/Compose、AGP 9.3.0、Gradle 9.6.0。

```text
gradlew testDebugUnitTest lintDebug assembleDebug
```

构建前需要安装 Android SDK Platform 37。正式发布前还需确定新的 `applicationId`、长期保存的发布签名、维护者信息与隐私说明；不应直接冒用原应用包名或原作者签名。

## 项目来源与致谢

本仓库是 [viceyy/BookmarkHelper](https://github.com/viceyy/BookmarkHelper) 的社区 Fork，保留原项目版权、提交历史与 Apache-2.0 归属。原项目的详细介绍、历史版本计划和讨论请前往[原项目仓库](https://github.com/viceyy/BookmarkHelper)查看，不再在本 README 中重复收录已过期的下载地址、发布状态和反馈渠道。

感谢原项目作者、历任维护者以及所有参与测试和反馈的用户。根据原项目 README，特别感谢：

- 代码贡献：多巴胺
- Bug 分析：〆XYZゾ、李小宅、大思想家、hudiei52、redknee
- 早期支持：西瓜皮香蕉皮、妮哩萌萌官方、hudiei52

现代化版本主要通过 **Vibe Coding** 协作开发，并由维护者进行代码审查与真机验证。欢迎通过 [GitHub Issues](https://github.com/GeekerKuan/BookmarkHelper/issues) 提交可复现的问题、兼容性信息和功能建议。
