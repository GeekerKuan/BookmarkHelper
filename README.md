# BookmarkHelper

本仓库是 [viceyy/BookmarkHelper](https://github.com/viceyy/BookmarkHelper) 的社区 Fork，
保留原项目版权、提交历史与 Apache-2.0 归属。现代化分支主要通过 **Vibe Coding**
协作开发，并由维护者进行真机测试；欢迎通过 GitHub Issues 报告可复现问题、浏览器版本、
Android/Root 环境和脱敏诊断包。请勿在 Issue 中上传真实浏览数据、账号信息或未脱敏数据库。

> **现代化实验分支（尚未作为稳定版发布）**
>
> 当前分支面向 Android 12 及以上的 Root 用户。核心已经改为浏览器无关的适配器与规范化数据模型：每个浏览器分别声明收藏、历史记录和标签页的读取/写入能力，任务层不再依赖特定数据库格式。当前 Via 与 Edge 支持收藏、历史记录的读取与写入；标签页仅开放经过验证的读取能力。写入前会创建应用私有事务备份，并在校验失败、目标进程重启、文件被并发修改或检测到未知格式时安全停止。
>
> 构建基线为 minSdk 31、compile/targetSdk 37、Java 17、Kotlin/Compose、AGP 9.3.0、Gradle 9.6.0。界面统一使用 Miuix；设置与主题交互按 SukiSU Ultra 的实现迁移，包含页内明暗模式 TabRow、Monet、模糊、悬浮底栏、玻璃效果、页面缩放 Slider、系统触感和过渡动画。收藏、历史记录与已打开标签页是三个独立的数据导入任务；历史记录可按六个常用范围或精确开始/结束日期读取。

应用会把每次进程的运行轨迹写入应用私有目录，并限制日志数量与大小。连续点击主页应用版本 7 次才会显示“开发者选项”；脱敏诊断与调试数据清理均只放在该页面。诊断 ZIP 不会打包旧版日志、书签、数据库、备份正文、账号标识或整机 logcat。

开发验证命令：

```text
gradlew testDebugUnitTest lintDebug assembleDebug
```

需要先安装 Android SDK Platform 37。原项目说明与历史版本记录保留如下。

## 当前分支的边界

- 仅识别 Via 国内版 `mark.via` 与 Microsoft Edge 稳定版 `com.microsoft.emmx`。
- 可把 Via 与 Edge 的收藏、历史记录和当前已打开标签页读取到应用私有数据管理；也可将数据管理中的收藏、历史记录全部或按选择写入 Via/Edge。Edge 隐私标签、归档标签、内部页面和自定义标签不会被导入；标签页写入仍因缺少稳定格式而明确禁用。
- 历史记录只按用户选择的时间范围读取，并写入 Edge `Default/History`。写入采用 Chromium 可见访问语义；alpha05/06 生成的隐藏访问仅在精确匹配旧版写入特征时修复，不会批量改写 Edge 原生记录。
- 新建任务先进入独立配置页，选好浏览器或时间范围后生成预览；只有存在运行中、排队或待确认任务时，导入页才显示任务入口。争用同一 Via/Edge 数据资源的任务会排队；任务可在 Root 原子操作边界安全取消，进度、预览、确认和结果均在独立任务页面显示。
- 检测到 `EncryptedBookmarks(2)`、仅有 `AccountBookmarks`、未知文件版本、校验和不一致、重复 ID/GUID、Edge 在事务中重启或文件被并发修改时会拒绝写入。
- 写入前会自动保留应用私有事务备份，并在写入校验失败时自动回滚；普通界面不再提供手动恢复入口。应用不申请联网、存储或已安装应用列表权限；仅在用户首次创建导入任务时请求通知权限，用于提醒待确认任务。目标浏览器可见性只通过 Manifest 中的两个显式包查询声明获得。
- 遇到失败时，可在开发者选项中生成并分享脱敏诊断 ZIP。请勿发送 Via 数据库、Edge `Bookmarks` 原件、账号截图或包含真实收藏内容的手工日志。
- 导入成功只代表 Edge 本机数据已安全更新。新增数据不伪造 Edge 的账号同步元数据，因此不能承诺进入微软账号云端。
- alpha08 包含一个默认无批量行为的现代 libxposed API 101 验证桥。它只识别应用内固定的 `example.com` 测试标记：Via 6.7.1 完成加载后，将同一测试页显式交给 Edge 150 正常打开，用来验证 Edge 自己产生的真实访问/标签是否进入账号同步。普通网页不会被转发；测试会切换到 Edge 前台，并不代表后台自动同步已经实现。

## 界面与许可证

设置、主题和页面交互以 [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
为最终参考，固定参考提交为 `35467545b2826e3acfc88699755981a889956b1a`。其代码使用 GPL-3.0，
因此包含这套界面的组合版本发布时必须遵守 GPL-3.0 并提供对应源码；原
BookmarkHelper 文件仍保留其 Apache-2.0 归属。完整文本和署名见
`LICENSES/` 与 `THIRD_PARTY_NOTICES.md`。

## Android 16 真机验证

在首个测试版发布前，至少需要确认：Via 当前 `bookmarks` 表结构、Edge 在 `edge://version` 显示的 Profile Path、明文/账号/加密书签文件组合、Root 方案对 `cp --preserve=all`/`stat`/SELinux 标签的支持，以及导入后 Edge 的本地显示与重启持久性。测试只应使用专门创建的假书签，先验证备份恢复，再使用真实数据。

## 酷安发布前仍需决定

原应用包名和签名密钥不应被新维护者直接冒用。正式发布前需要确定新的 `applicationId`、长期保存的发布签名、版本号、维护者与隐私说明；Android 16 真机矩阵通过前只应标记为 Root 玩家测试版。

## 原项目历史说明（存档）

> 以下内容来自旧版 README，仅用于保留原作者、贡献者和版本历史。旧下载、捐赠、联系方式、功能范围及发布状态不代表当前实验分支。

## 这是什么？
BookmarkHelper（书签助手）是一款Android平台下，浏览器书签转换工具类APP，运行时需要Root权限，目标全面兼容主流Android浏览器相互转换书签数据，暂未考虑支持iOS或桌面端。

使用举例：从[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)中，提取用户书签数据，追加合并到[Via浏览器](http://www.coolapk.com/apk/mark.via)，带你轻松脱坑，成功用上最牛逼浏览器！

BookmarkHelper是个人作品，完整开源代码，持续维护，永不考虑收费推广，不滥用Root权限，使用完毕可立即卸载，运行时按需连接网络。如果你用的爽，可以[鼓励一下](https://www.kisscat.pro/rewards/wechat-reward-image.png)，我将用于：支付VPS月租，开发更多精彩APP，谢谢您的点滴支持！

BookmarkHelper APP目前仅在[酷安网上架](http://www.coolapk.com/apk/pro.kisscat.www.bookmarkhelper)并维护，如果您在其他渠道获得APK，则可能遭受盗版软件的困扰，请您在[酷安网](http://www.coolapk.com/apk/pro.kisscat.www.bookmarkhelper)获取最新稳定版软件。

<a target="_blank" href="http://ww2.sinaimg.cn/mw690/becd6b85gw1f9oaniwbdig208e0dwu11.gif">使用教程Gif：5.8MB</a>

## 精彩报道

1.[书签助手，迈出换用 Via 浏览器的第一步 _ App+1 - 少数派](http://sspai.com/36385)

## 版本计划
### 0.0.X
#### [0.0.1版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.1%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.1.apk)
支持从[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。
#### [0.0.2版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.2%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.2.apk)
1.增加兼容：支持从Flyme5自带浏览器提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.当rom不是flyme时，如果rom存在包名为：com.android.browser的浏览器，也可以用Flyme5的规则，一般的内置浏览器都是这个包名。

3.Flyme4.X系列的rom暂未测试，没有设备。如您感兴趣，请与我联系，见下方的邮件地址！

4.Via合并书签修复bug，增强稳定性，现在安装via，但是没有打开过，将不能合并，最少需要您手动打开一次！
#### [0.0.3版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.3%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.3.apk)
1.增加兼容：支持从[UC浏览器](http://www.coolapk.com/apk/com.UCMobile)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.界面大调整，美美哒！

3.部分代码重构，精简掉重合代码段。
#### [0.0.4版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.4%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.4.apk)
1.增加兼容：支持从[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.底栏小Tip，关于我、五星好评、捐助
#### [0.0.5版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.5%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.5.apk)
1.增加兼容：支持从[X浏览器](http://www.coolapk.com/apk/com.mmbox.xbrowser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)支持读取QQ用户登录的书签数据，微信登录的还在修复中。

3.Bug 修复：[UC浏览器](http://www.coolapk.com/apk/com.UCMobile)支持读取QQ和微信用户登录的书签数据。

4.延迟对Root权限的检查和获取！

5.日志记录进行异步化，不再阻塞主线程

#### [0.0.6版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.6%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.6.apk)
1.增加兼容：支持从[360浏览器](http://www.coolapk.com/apk/com.qihoo.browser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)现在支持读取完整的书签数据。

3.Bug 修复：[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)支持读取微信用户登录的书签数据。

4.优化：减少主循环中日志打印，避免UI响应超时！

5.背景线程处理书签交换，避免UI响应超时

#### [0.0.7版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.7%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.7.apk)
1.增加兼容：支持从[搜狗浏览器](http://www.coolapk.com/apk/sogou.mobile.explorer)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：修复部分机型底层shell命令无限阻塞问题

3.Bug 修复：现在所有书签已经完成重复性验证。

4.Bug 修复：[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)支持提取嵌套文件夹里的书签数据。

5.Bug 修复：[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)支持提取时保留文件夹路径。

6.Bug 修复：[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)支持提取时保留文件夹路径。

7.Bug 修复：[UC浏览器](http://www.coolapk.com/apk/com.UCMobile)支持提取时保留文件夹路径。

8.Bug 修复：[360浏览器](http://www.coolapk.com/apk/com.qihoo.browser)支持提取时保留文件夹路径。

9.Bug 修复：[X浏览器](http://www.coolapk.com/apk/com.mmbox.xbrowser)支持提取时保留文件夹路径。

10.Bug 修复：完善对浏览器应用是否安装的检测。

11.Bug 修复：[Via浏览器](http://www.coolapk.com/apk/mark.via)现在支持最新版本(2.1.1)。

12.一些webview的优化。

================  割   ================

已知问题：

1.[UC浏览器](http://www.coolapk.com/apk/com.UCMobile)无法读取首页的书签数据，找不到存取路径，研究中。

#### [0.0.8版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.8%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.8.apk)
1.增加兼容：支持从[百度浏览器](http://www.coolapk.com/apk/com.baidu.browser.apps)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.代码优化

#### [0.0.9版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.9%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.9.apk)
1.增加兼容：支持从[欧朋浏览器](http://www.coolapk.com/apk/com.oupeng.mini.android)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.代码优化
#### [0.0.10版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.10%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.10.apk)
1.增加兼容：支持从[猎豹浏览器](http://www.coolapk.com/apk/com.ijinshan.browser_fast)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.完善Android6.x及以上的权限检查机制

3.针对三星机型锁定/data/目录，现加入重新挂载机制

4.少许错误描述修复

#### [0.0.11版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.11%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.11.apk)
1.增加兼容：支持从[FireFox浏览器](http://www.coolapk.com/apk/org.mozilla.firefox)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.现在6.0及以上的rom，会在运行时申请权限

3.改善shell命令交互稳定性

4.Bug 修复：部分选项在点击时，不变底色的问题

5.Bug 修复：[UC浏览器](http://www.coolapk.com/apk/com.UCMobile)支持提取时保留文件夹路径。

6.Bug 修复：“文件拷贝失败”问题。

#### [0.0.12版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.12%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.12.apk)
1.增加兼容：支持从[UC国际版浏览器](http://www.coolapk.com/apk/com.UCMobile.intl)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：针对部分机型，无法获取到：QQ、搜狗、360、UC等浏览器的登录用户书签的问题

3.稳定性：针对部分机型对文件操作有延迟现象，现加入double-check机制

4.Bug 修复：Flyme5自带浏览器支持提取时保留文件夹路径。

5.现在转换任务进行异步化，不再阻塞UI响应，对应的，加入处理进度条。

#### [0.0.13版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.13%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.13.apk)
1.增加兼容：支持从[Chrome Canary浏览器](http://www.coolapk.com/apk/com.chrome.canary)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

#### [0.0.14版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.14%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.14.apk)
1.增加兼容：支持从[Chrome Beta浏览器](http://www.coolapk.com/apk/com.chrome.beta)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：现在支持读取[UC国际版浏览器](http://www.coolapk.com/apk/com.UCMobile.intl)已登录用户的书签

3.兼容部分机型，在获取root权限时，返回warning信息。

#### [0.0.15版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.15%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.15.apk)
1.增加兼容：支持从[Chrome Dev浏览器](http://www.coolapk.com/apk/com.chrome.dev)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：4.4平台webview加载出错的问题

3.Bug 修复：捐助页面左上角无法返回的问题

4.兼容部分机型，在获取root权限时，返回warning信息。

#### [0.0.16版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.16%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.16.apk)
1.增加兼容：支持从[MIUI浏览器](https://github.com/amao12580/BookmarkHelper/tree/master/origin/resource/miui)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.现在捐助页面，支持长按二维码使用微信打开

3.Merger pull request，改善UI交互，感谢酷友@多巴胺

4.部分代码重构，规范化。

-------------------FBI Waring！-------------------

近期工作忙，停更1到2周，有问题或建议请发邮件。

#### [0.0.17版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.17%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.17.apk)
1.增加兼容：支持从[Yandex浏览器](http://www.coolapk.com/apk/com.yandex.browser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。



-------------------FBI Waring！-------------------

近期工作忙，停更1到2周，有问题或建议请发邮件。

#### [0.0.18版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.18%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.18.apk)
1.增加兼容：支持从[旗鱼浏览器](http://www.coolapk.com/apk/com.ruanmei.qiyubrowser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：Flyme5浏览器转换到Via时出错

#### [0.0.19版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.19%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.19.apk)
1.增加兼容：支持从[H5浏览器](http://www.coolapk.com/apk/org.noear.h5)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

#### [0.0.20版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.20%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.20.apk)
1.增加兼容：支持从[星尘浏览器](http://www.coolapk.com/apk/com.chaozhuo.browser_phone)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

【未完成】2.增加兼容：支持从[星尘浏览器 平板x86版](http://www.coolapk.com/apk/com.chaozhuo.browser.x86)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

备注：下载量不超过1w，放弃适配

3.增加兼容：支持从[星尘浏览器（平板版）](http://www.coolapk.com/apk/com.chaozhuo.browser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

#### [0.0.21版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.21%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.21.apk)
1.增加兼容：支持从[360极速浏览器](http://www.coolapk.com/apk/com.qihoo.chrome360)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.Bug 修复：UC浏览器转换到Via报错

3.Bug 修复：UC浏览器国际版转换到Via报错

4.Bug 修复：星尘手机版转换到Via报错

#### [0.0.22版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.22%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.22.apk)
1.增加兼容：支持从[一加内置QQ浏览器](https://github.com/amao12580/BookmarkHelper/blob/master/origin/resource/qq/%E4%B8%80%E5%8A%A0/%E6%B5%8F%E8%A7%88%E5%99%A8_2.1.4.apk)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

#### [0.0.23版 Release](https://github.com/amao12580/BookmarkHelper/raw/master/origin/release/0.0.23%E7%89%88/pro.kisscat.www.bookmarkhelper_0.0.23.apk)
1.增加兼容：支持从[X浏览器-pro](https://apkpure.com/x-browser-super-fast-mini/com.mmbox.xbrowser.pro)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

2.增加兼容：支持从[X浏览器-gp](https://apkpure.com/x-browser/com.mmbox.xbrowser.gp)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

3.增加兼容：支持从[X浏览器-套壳](https://apkpure.com/fastest-mini-browser/com.mmbox.browser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

4.稳定性：改善SQLite数据库交互稳定性。

加上[0.0.5版](https://github.com/amao12580/BookmarkHelper#005版-release)，共适配了4款X浏览器，这X浏览器是要上天？

#### 0.0.24版 Draft
1.增加兼容：支持从[欧朋浏览器经典版](https://github.com/amao12580/BookmarkHelper/blob/master/origin/resource/%E6%AC%A7%E6%9C%8B/com.oupeng.mini.classic-1.apk)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

com.oupeng.mini.classic

---

【FBI Waring！】0.1.X版的开发顺序随时会做出调整(依据作者心情和用户的关注度)，请关注最新README

---

### 0.1.X
#### 0.1.1版 Draft
1.增加兼容：支持从[Via浏览器](http://www.coolapk.com/apk/mark.via)提取书签，追加到[夸克浏览器](http://www.coolapk.com/apk/com.quark.browser)。

#### 0.1.2版 Draft
1.增加兼容：支持从[Via浏览器](http://www.coolapk.com/apk/mark.via)提取书签，追加到[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)。

#### 0.1.3版 Draft
1.增加兼容：支持从[Via浏览器](http://www.coolapk.com/apk/mark.via)提取书签，追加到[妮哩萌萌](http://www.coolapk.com/apk/nico.styTool)。

#### 0.1.4版 Draft
1.增加兼容：支持从[Via浏览器](http://www.coolapk.com/apk/mark.via)提取书签，追加到[Mercury浏览器](http://www.coolapk.com/apk/com.ilegendsoft.mercury)。

#### 0.1.5版 Draft
1.增加兼容：支持从[UC浏览器](http://www.coolapk.com/apk/com.UCMobile)提取书签，追加到[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)。

#### 0.1.6版 Draft
1.增加兼容：支持从[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)提取书签，追加到[X浏览器](http://www.coolapk.com/apk/com.mmbox.xbrowser)。

#### 0.1.7版 Draft
1.增加兼容：支持从[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)提取书签，追加到[QQ浏览器](http://www.coolapk.com/apk/com.tencent.mtt)。

---

### 0.?.X

"0.?.X"指的是不会在近期开发，仅作为未来开发的计划，最早也需要3个月以后才能实现，这通常是近期版本计划过于密集，只能将一些任务延后。

#### 0.?.1版 Draft
1.增加兼容：支持从[Opera Mini](http://www.coolapk.com/apk/com.opera.mini.native)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

#### 0.?.2版 Draft
1.增加兼容：支持从[Opera browser](http://www.coolapk.com/apk/com.opera.android.browser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

#### 0.?.3版 Draft
1.增加兼容：支持从[Chrome浏览器](http://www.coolapk.com/apk/com.android.chrome)提取书签，追加到[Via浏览器GP版](https://apkpure.com/cn/via-web-browser/mark.via.gp)。

#### 0.?.4版 Draft
1.增加兼容：支持从[傲游浏览器](http://www.coolapk.com/apk/com.mx.browser)提取书签，追加到[Via浏览器](http://www.coolapk.com/apk/mark.via)。

## TO-DO List

### Feature

1.导出为文件

可能是sqlite3

2.从文件导入



### Issue

1.读取不到... 红米2 5.1 MIUI8     红米3

没有收到日志，暂停修复

2.能做一个转移书签到夸克浏览器不？

增加兼容

3.识别不出chrome beta 和dev还有chromium啊。。有啥用？

增加兼容

4.有没有adui自带浏览器的书签转换？   给你说，MIUI的浏览器也是定制的QQ浏览器#(勉强)

增加兼容

5.~~魅族mx4，版本号：Flyme 5.1.11.0A 并不能转移成功……点击一下就卡在界面了~~

确认修复

6.~~谷歌浏览器书签栏的书签能转移了，但是书签栏里的文件夹里面的书签仍然不行~~

确认修复

7.可不可以把via书签移到chrome？

增加兼容

8.~~作者大大，反馈一个bug，uc浏览器转via书签中有相当一部分会复制两次，如图~~

确认修复

9.什么时候做个移书签到妮哩萌萌浏览器

增加兼容

10.希望能将别的浏览器书签能导入mercury浏览器

增加兼容

11.不能保存为文件吗

新特性

12.~~[笑哭再见]是我打开方式不对吗？导入QQ的书签，然后原来QQ浏览器和via分好类的文件夹都被被拆开了，现在打开书签满屏的书签，我得整理到什么时候啊！心酸[w累]~~

确认修复

13.可以导UC的首页标签吗？

无法定位UC主页书签的存取路径，暂停修复

14.大兄弟们告诉我，这个能不能把uc的导入QQ？

增加兼容

15.支持yandex

增加兼容

16.支持旗鱼浏览器

增加兼容

17.~~QQ浏览器也有这样的主页书签，也没法同步的吗？[w调皮][w调皮][w调皮]~~

确认QQ主页书签可以读取，不是bug

18.~~打开后安装新浏览器，显示未安装~~

确认修复

19.报via未安装，我的是g play版的，包名与国内版不一致

增加兼容

20.~~新版via，2.1.1，不兼容~~

确认修复

21.那希望大大早日添加华为自带浏览器的切换，应该不是很难，可能就包名不一样吧[w调皮][w调皮][w调皮]

增加兼容

22.@amao12580 何时支持h5

增加兼容

23.via浏览器 文件删除失败！



## UnStable
UnStable是指最新通过基础测试，但未进行大面积设备兼容验证的非稳定版本，不会发布到[Google Play](https://play.google.com/store?hl=zh_CN)或[Cool Market](http://www.coolapk.com)。如果你是狂热爱好者，请点击【[下载链接](https://github.com/amao12580/BookmarkHelper/raw/master/app/build/outputs/apk/app-debug.apk)】，从我的[Github Repo](https://github.com/amao12580/BookmarkHelper)实时获取，不保证会有小瑕疵。



## Bug & 反馈
由于个人精力有限，没有大量设备供测试，如您遇到使用问题，请发送log文件（SD路径：BookmarkHelper/logs/*Log.txt）到Mail：stevenchengmask # gmail.com，我将尽快答复您的疑问！同时也欢迎您的来信建议，我将最短时间内答复您！


## 鸣谢

Bug的快速修复离不开用户的积极反馈，正是因为大量的日志分析，才能使得高效率解决。

鸣谢积极用户

### 酷安网

#### 代码贡献

@多巴胺

#### Bug分析

@〆XYZゾ

@李小宅

@大思想家

@hudiei52

@redknee

#### 捐助

@西瓜皮香蕉皮

@妮哩萌萌官方

@hudiei52
