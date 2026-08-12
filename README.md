# BookmarkHelper

换了浏览器，却不想丢掉原来的收藏和历史记录？BookmarkHelper 可以在有 Root 权限的 Android 手机上读取浏览器数据，让你先检查、整理，再迁移到另一个浏览器。

目前主要支持 Via 和 Microsoft Edge，最低支持 Android 12。除了迁移数据，你也可以把收藏、历史记录和已打开的标签页保存在应用里，像使用浏览器自带的数据管理功能一样进行搜索、编辑和分类。

> **目前还没有在酷安或其他应用商店上架。**
>
> 本仓库现在只提供源码和开发进度。测试版以后会通过 [GitHub Releases](https://github.com/GeekerKuan/BookmarkHelper/releases) 发布；在首个 Release 出现前，请不要把其他地方下载的 APK 当作本项目的正式版本。

## 能做什么

- 从 Via 或 Edge 读取收藏、历史记录和当前打开的标签页。
- 导入前先预览数据，确认无误后再保存。
- 在应用里搜索、编辑、移动、复制或删除数据。
- 用多层文件夹整理收藏，并批量处理选中的内容。
- 把全部数据或选中的部分数据写回 Via 或 Edge。
- 按最近几天或自己选择的开始、结束日期导入历史记录。
- 记住每条数据来自哪个浏览器，重复内容不会反复添加。
- 任务可以排队执行，离开任务页面后仍能回来查看进度。

| 数据 | Via | Edge |
| --- | --- | --- |
| 收藏 | 可以读取和写入 | 可以读取和写入 |
| 历史记录 | 可以读取和写入 | 可以读取和写入 |
| 已打开标签页 | 可以读取 | 可以读取 |

标签页目前只能读取，还不能写入浏览器。

## 怎么使用

1. 在“迁移”页面选择收藏、历史记录或标签页。
2. 选择要读取数据的浏览器；历史记录还可以选择时间范围。
3. 等待读取完成，在预览页面核对内容。
4. 确认后，数据会保存到“数据管理”。
5. 需要写入浏览器时，在数据管理中选择全部或部分内容，再选择目标浏览器。

第一次使用时，建议只创建几条专门用于测试的收藏和历史记录。确认读取、写入和浏览器显示都正常后，再处理真实数据。

## 使用前请注意

- 手机必须拥有 Root 权限，并运行 Android 12 或更高版本。
- 当前只识别 Via 国内版 `mark.via` 和 Microsoft Edge 稳定版 `com.microsoft.emmx`。
- 写入前应用会自动创建内部备份并检查文件状态；发现浏览器正在改动文件或数据格式无法确认时，会停止操作。
- “成功写入 Edge”只表示本机文件已经更新。Edge 是否显示这些记录、是否把它们同步到微软账号，仍由 Edge 自己决定。
- Edge 的加密书签、账号专用书签、隐私标签和内部页面不会被强行修改。
- 这是面向 Root 玩家的测试版本，请先自行备份重要的浏览器数据。

## 隐私与问题反馈

应用不会把你的收藏、历史记录或浏览器数据库上传到服务器。运行日志保存在应用自己的私有目录，并限制保存数量和大小。

如果遇到问题，可以连续点击主页中的应用版本 7 次，打开开发者选项并生成脱敏诊断包，然后在 [GitHub Issues](https://github.com/GeekerKuan/BookmarkHelper/issues) 中告诉我们：

- 手机系统、Via、Edge 和 Root 管理器的版本；
- 问题发生前做了什么，以及你实际看到的结果；
- 应用生成的脱敏诊断 ZIP。

请不要上传真实浏览器数据库、Edge `Bookmarks` 原文件、账号截图、未脱敏日志或包含私人网址的文件。

## 项目来源与感谢

这是 [viceyy/BookmarkHelper](https://github.com/viceyy/BookmarkHelper) 的社区 Fork。原项目的介绍、历史版本和早期讨论可以直接前往[原项目仓库](https://github.com/viceyy/BookmarkHelper)查看，这里不再重复已经过期的下载地址和发布信息。

感谢原项目作者、历任维护者，以及所有帮助测试和反馈问题的用户。根据原项目 README，特别感谢：

- 代码贡献：多巴胺
- Bug 分析：〆XYZゾ、李小宅、大思想家、hudiei52、redknee
- 早期支持：西瓜皮香蕉皮、妮哩萌萌官方、hudiei52

现代化版本主要通过 **Vibe Coding** 协作开发，并由维护者进行代码检查和真机测试。欢迎提交 Issue，一起把不同手机和浏览器版本上的问题逐步解决。

## 界面与开源许可

界面使用 [Compose Miuix](https://github.com/compose-miuix-ui/miuix)，并参考了 [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 的页面结构和交互。

SukiSU Ultra 相关代码使用 GPL-3.0；发布包含这部分代码的版本时，需要遵守 GPL-3.0 并提供对应源码。原 BookmarkHelper 文件继续保留 Apache-2.0 归属。完整许可证与署名见 [`LICENSES/`](LICENSES/) 和 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 构建

项目使用 Android SDK 37、Java 17、Kotlin/Compose、AGP 9.3.0 和 Gradle 9.6.0。

```text
gradlew testDebugUnitTest lintDebug assembleDebug
```

正式发布前还需要确定新的应用包名、长期使用的发布签名、维护者信息和隐私说明，不应直接冒用原应用的包名或签名。
