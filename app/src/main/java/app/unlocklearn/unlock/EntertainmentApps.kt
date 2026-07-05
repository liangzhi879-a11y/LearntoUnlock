package app.unlocklearn.unlock

/**
 * 娱乐 App 预设白名单。
 *
 * 打开这些 App 时（若启用了娱乐 App 模式）有概率触发答题。
 * 列表按类别组织，便于后续在 UI 里分类展示或让用户按类别启用。
 *
 * 包名以国内主流版本为准，国际版（如 TikTok）单独列出。
 */
object EntertainmentApps {

    /** 短视频 */
    val SHORT_VIDEO: Set<String> = setOf(
        "com.ss.android.ugc.aweme",          // 抖音
        "com.ss.android.ugc.aweme.lite",     // 抖音极速版
        "com.smile.gifmaker",                 // 快手
        "com.kuaishou.nebula",                // 快手极速版
        "com.ss.android.article.video",       // 西瓜视频
        "com.zhihu.android.app"               // 知乎（含短视频，归此类）
    )

    /** 社交动态 */
    val SOCIAL: Set<String> = setOf(
        "com.sina.weibo",                     // 微博
        "com.xingin.xhs",                     // 小红书
        "com.zhihu.android"                   // 知乎主包
    )

    /** 长视频 */
    val LONG_VIDEO: Set<String> = setOf(
        "tv.danmaku.bili",                    // 哔哩哔哩
        "com.youku.phone",                    // 优酷
        "com.qiyi.video",                     // 爱奇艺
        "com.tencent.qqlive"                  // 腾讯视频
    )

    /** 音乐 */
    val MUSIC: Set<String> = setOf(
        "com.netease.cloudmusic",             // 网易云音乐
        "com.tencent.qqmusic",                // QQ音乐
        "com.kugou.android",                  // 酷狗音乐
        "cn.kuwo.player"                      // 酷我音乐
    )

    /** 资讯 */
    val NEWS: Set<String> = setOf(
        "com.ss.android.article.news",        // 今日头条
        "com.tencent.news",                   // 腾讯新闻
        "com.netease.news"                    // 网易新闻
    )

    /** 热门游戏 */
    val GAMES: Set<String> = setOf(
        "com.tencent.tmgp.pubgmhd",           // 和平精英
        "com.tencent.tmgp.sgame",             // 王者荣耀
        "com.miHoYo.Genshin",                 // 原神
        "com.miHoYo.Yuanshen",               // 原神（国内包名）
        "com.hypergryph.arknights",           // 明日方舟
        "com.netease.onmyoji",                // 阴阳师
        "com.tencent.tmgp.cf"                 // 穿越火线手游
    )

    /** 国际版（TikTok 等，海外用户） */
    val INTERNATIONAL: Set<String> = setOf(
        "com.zhiliaoapp.musically",           // TikTok
        "com.instagram.android",              // Instagram
        "com.facebook.katana",                // Facebook
        "com.twitter.android",                // Twitter / X
        "com.reddit.frontpage"                // Reddit
    )

    /** 全量白名单：所有类别合并 */
    val ALL: Set<String> = SHORT_VIDEO + SOCIAL + LONG_VIDEO + MUSIC + NEWS + GAMES + INTERNATIONAL

    /** 判断包名是否在娱乐 App 白名单内 */
    fun isEntertainment(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in ALL
    }
}
