# BookmarkHelper

换了浏览器，却不想丢掉原来的收藏和历史记录？BookmarkHelper 可以在有 Root 权限的 Android 手机上读取浏览器数据，让你先检查和整理，再迁移到另一个浏览器。

目前主要支持 Via 和 Microsoft Edge，最低支持 Android 12。收藏、历史记录和已打开的标签页也可以保存在应用中，方便随时搜索、编辑和分类。

> **目前还没有在酷安或其他应用商店上架。**
>
> 测试版以后会通过 [GitHub Releases](https://github.com/GeekerKuan/BookmarkHelper/releases) 发布。在首个 Release 出现前，请不要把其他地方下载的 APK 当作本项目的正式版本。

## 能做什么

- 从 Via 或 Edge 读取收藏、历史记录和当前打开的标签页。
- 导入前预览内容，确认后再保存。
- 搜索、编辑、移动、复制、删除和批量管理数据。
- 使用多层文件夹整理收藏。
- 把全部数据或选中的部分数据写入 Via 或 Edge。
- 按最近几天或指定的起止日期导入历史记录。
- 记录数据来自哪个浏览器，并自动忽略重复内容。
- 让多个任务排队执行，离开任务页面后仍可回来查看进度。

| 数据 | Via | Edge |
| --- | --- | --- |
| 收藏 | 可以读取和写入 | 可以读取和写入 |
| 历史记录 | 可以读取和写入 | 可以读取和写入 |
| 已打开标签页 | 可以读取 | 可以读取 |

标签页目前只能读取，还不能写入浏览器。

## 怎么使用

1. 在“迁移”页面选择收藏、历史记录或标签页。
2. 选择要读取数据的浏览器；导入历史记录时还可以选择时间范围。
3. 等待读取完成，然后核对预览内容。
4. 确认后，数据会保存到“数据管理”。
5. 需要写入浏览器时，在数据管理中选择全部或部分内容，再选择目标浏览器。

## 使用前请注意

- 手机需要拥有 Root 权限，并运行 Android 12 或更高版本。
- 当前只识别 Via 国内版 `mark.via` 和 Microsoft Edge 稳定版 `com.microsoft.emmx`。
- 这是测试版本。第一次使用时，请先创建几条专门用于测试的数据，并提前备份重要的浏览器内容。
- “成功写入 Edge”只表示手机中的文件已经更新。Edge 是否显示这些数据、是否将其同步到微软账号，仍由 Edge 自己决定。
- 遇到无法确认的数据格式或文件正在被浏览器修改时，应用会停止操作，避免继续写入。

## 遇到问题

连续点击主页中的应用版本 7 次，可以打开开发者选项并生成一份已隐藏私人信息的诊断文件。

请在 [GitHub Issues](https://github.com/GeekerKuan/BookmarkHelper/issues) 中说明手机系统、浏览器和 Root 管理器版本，写清问题发生前的操作，并附上应用生成的诊断文件。

请不要上传真实浏览器数据库、账号截图、未经应用处理的日志，或其他包含私人网址的文件。

## 项目来源与感谢

这是 [viceyy/BookmarkHelper](https://github.com/viceyy/BookmarkHelper) 的社区 Fork。原项目的介绍、历史版本和早期讨论请前往[原项目仓库](https://github.com/viceyy/BookmarkHelper)查看。

感谢原项目作者、历任维护者，以及所有帮助测试和反馈问题的用户。根据原项目 README，特别感谢：

- 代码贡献：多巴胺
- Bug 分析：〆XYZゾ、李小宅、大思想家、hudiei52、redknee
- 早期支持：西瓜皮香蕉皮、妮哩萌萌官方、hudiei52

现代化版本主要通过 **Vibe Coding** 协作开发，并由维护者进行检查和真机测试。欢迎提交 Issue，一起完善不同手机和浏览器版本的支持。

项目使用 [Compose Miuix](https://github.com/compose-miuix-ui/miuix)，界面参考了 [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)。开源许可和相关署名见 [`LICENSES/`](LICENSES/) 与 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
