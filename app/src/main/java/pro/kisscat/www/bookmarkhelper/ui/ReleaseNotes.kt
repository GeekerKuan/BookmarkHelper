package pro.kisscat.www.bookmarkhelper.ui

data class ReleaseNote(
    val version: String,
    val title: String,
    val changes: List<String>,
)

/** Verified summary of the original README history and this modernization branch. */
val bookmarkHelperReleaseNotes = listOf(
    ReleaseNote(
        "1.0.0-alpha.1",
        "Edge 标签页、任务通知与浏览器目录",
        listOf(
            "修复 Android 16 上 Edge 位于 data_mirror 时被安全路径白名单拒绝的问题，当前标签页可进入实际解析阶段。",
            "主页改为 SukiSU 风格状态与信息布局；Root 和 LSPosed 卡片分别检测，并显示 Root 管理器、应用版本和浏览器版本。",
            "运行中与待确认任务只在导入页动态显示；待确认会发送系统通知和红点提示，取消、完成或失败后自动回收。",
            "任务支持安全取消：Root 文件操作在原子边界停下，不强行终止正在复制的浏览器数据库。",
            "数据管理按 Via、Edge 和全部来源进入，收藏目录支持新建、父子浏览和批量移动。",
            "任务快照到期不再删除数据管理中的长期记录；只有用户主动删除才会移除收藏、历史或标签页。",
            "移除可选择的 Material 3 界面，应用统一使用 Miuix 组件与主题。",
        ),
    ),
    ReleaseNote(
        "2026-08 · 标签页路径适配",
        "Android 16 标签页路径兼容",
        listOf(
            "Via 与 Edge 标签页读取补齐 Android data_mirror 用户数据命名空间，与收藏和历史读取保持一致。",
            "使用 Via 6.7.1 新样本确认测试标签已保存在 tabs 表，并兼容当前字段与毫秒时间戳。",
            "标签页任务加入不含网址、标题和真实路径的阶段码，便于安全定位 Root、快照或解析故障。",
        ),
    ),
    ReleaseNote(
        "2026-08 · 标签页与文件夹层级",
        "标签页识别与真实文件夹层级",
        listOf(
            "新增 Via 与 Edge 当前已打开标签页读取；Edge 隐私、归档与内部页面不会进入数据管理。",
            "收藏文件夹改为可逐层进入和系统返回的父子页面，不再把完整路径只显示成分组标题。",
            "历史范围滑块改为六个等距节点，并新增精确开始、结束日期选择子页面。",
            "正在运行的任务入口移到主页；应用信息移到主页内容末尾。",
            "收藏确认操作移动到预览列表之前，并使用 Miuix 主操作强调色。",
            "数据编辑新增在浏览器中打开链接。",
        ),
    ),
    ReleaseNote(
        "2026-08 · 多浏览器数据管理",
        "多浏览器数据导入与分组管理",
        listOf(
            "主页新增 Root 与 LSPosed 运行状态卡，并只为这两张状态卡启用 Miuix 3D 按压反馈。",
            "数据迁移重构为数据导入；收藏、历史记录与标签页分别进入浏览器选择页面。",
            "收藏和历史记录支持同时选择 Via 与 Edge；历史记录可自由选择最近 1 至 365 天。",
            "数据管理按收藏文件夹和历史日期分组，新增分组全选、批量移动与批量删除。",
            "主题设置沿用 SukiSU-Ultra 图标映射，页面缩放在拖动过程中即时生效。",
        ),
    ),
    ReleaseNote(
        "2026-08 · Miuix 与 Edge 读取",
        "Miuix 交互与 Edge 读取适配",
        listOf(
            "预见式返回、系统触感、顶栏与底栏模糊改为真实运行时开关。",
            "移除自定义 Activity 进出动画，恢复 Android 与系统界面的原生过渡。",
            "修复 Miuix 下拉菜单宿主，并让页面缩放在滑块结束后生效。",
            "Edge 收藏与历史记录通过统一适配器解析；读取一次同时完成判重与数据管理入库。",
            "新增应用信息页、开源许可说明与独立版本更新日志。",
        ),
    ),
    ReleaseNote(
        "2026-08 · 数据管理与任务中心",
        "数据管理与任务中心",
        listOf(
            "建立应用私有 SQLite 中立数据仓库，收藏、历史、标签页分区管理。",
            "加入来源标记、跨浏览器去重、分页搜索、编辑与删除。",
            "新建任务、任务进度和后台等待拆分为独立页面，并允许无冲突任务排队。",
            "开发者选项改为连续点击版本 7 次开启，诊断包执行双层脱敏。",
        ),
    ),
    ReleaseNote(
        "2026-08 · 历史记录与安全事务",
        "历史迁移与安全事务",
        listOf(
            "加入 Via 历史时间范围读取和 Edge Chromium History 写入实验。",
            "Root 写入增加单飞锁、备份、元数据校验、持久事务日志与失败关闭。",
            "加入浏览器中立收藏、历史与标签页模型，为后续适配器扩展做准备。",
        ),
    ),
    ReleaseNote(
        "2026-08 · Android 12+ 现代化",
        "Android 12+ 现代化分支",
        listOf(
            "最低系统提升至 Android 12，适配 Android 16/17、AndroidX、Java 17 与新版 Gradle。",
            "实现 Root 环境下 Via → Edge 收藏预览、去重、备份和安全写入。",
            "加入 Miuix/Material 双界面、悬浮底栏、主题设置与脱敏诊断导出。",
            "加入 LSPosed 标签云同步可行性实验；未把实验结果宣传为 Edge 云同步保证。",
        ),
    ),
    ReleaseNote(
        "0.0.15–0.0.23（原项目）",
        "继续扩展浏览器兼容",
        listOf(
            "陆续支持 Chrome Dev、MIUI、Yandex、旗鱼、H5、星尘、360 极速、一加 QQ 与多个 X 浏览器版本。",
            "持续修复 UC、Flyme、星尘等数据路径与 SQLite 兼容问题。",
            "0.0.24 及 0.1.x 在原 README 中仅为 Draft，不列为已发布能力。",
        ),
    ),
    ReleaseNote(
        "0.0.6–0.0.14（原项目）",
        "稳定性与更多来源浏览器",
        listOf(
            "增加 360、搜狗、百度、欧朋、猎豹、Firefox、UC 国际版及 Chrome Canary/Beta。",
            "完善嵌套文件夹、文件夹路径和重复性验证。",
            "加强 Root/Shell、运行时权限、异步转换、进度显示与文件操作 double-check。",
        ),
    ),
    ReleaseNote(
        "0.0.1–0.0.5（原项目）",
        "书签助手的起点",
        listOf(
            "0.0.1 首次支持从 Chrome 提取书签并追加到 Via。",
            "随后加入 Flyme/通用系统浏览器、UC、QQ 与 X 浏览器。",
            "开始进行 UI 重构、登录用户数据兼容、异步日志与非阻塞 Root 检查。",
        ),
    ),
)
