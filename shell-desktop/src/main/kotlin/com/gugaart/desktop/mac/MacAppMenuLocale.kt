package com.gugaart.desktop.mac

import com.gugaart.desktop.mac.accept.Objc

/**
 * 系统应用菜单（最前面那个「咕咕嘎嘎」）的中文化（1.4.0 遗留项）。
 *
 * 那个菜单不是我们出的：JDK 从系统 `JavaVM.framework` 的 `DefaultApp.nib` 加载「About / Preferences… /
 * Services / Hide / Hide Others / Show All / Quit」。这台机器的系统框架只带英文（`Menu.loctable` 只有 en），
 * 所以无论 Info.plist 的 `CFBundleDevelopmentRegion` / `CFBundleLocalizations` 怎么写，菜单都是英文——
 * 中文串根本不存在于框架里。最小改法：菜单装好后，按系统首选语言把这几条的标题改成中文（`setTitle:`，
 * 不动 action / target，关于 / 设置 / 退出的处理器和 ⌘Q / ⌘, 快捷键照旧）。开发运行和打出来的 .app 走同一条路。
 *
 * 标题映射是纯函数（[appMenuLocale] / [localizedAppMenuTitle]），单测；改标题那一下经 [Objc] 在 AppKit 主线程做。
 */

/** 应用菜单用哪套中文。null = 系统语言不是中文，保留 JDK 的英文。 */
internal enum class AppMenuLocale { Simplified, Traditional }

/** 系统首选语言（`NSLocale.preferredLanguages` 的第一条，如 `zh-Hans-CN`）→ 简 / 繁。不是中文 = null。纯函数。 */
internal fun appMenuLocale(preferredLanguage: String?): AppMenuLocale? {
    val lang = preferredLanguage?.trim()?.lowercase() ?: return null
    if (!lang.startsWith("zh")) return null
    val traditional = "hant" in lang || lang.endsWith("-tw") || lang.endsWith("-hk") || lang.endsWith("-mo")
    return if (traditional) AppMenuLocale.Traditional else AppMenuLocale.Simplified
}

/**
 * JDK 默认应用菜单的英文标题 → 中文标题（保留标题里的应用名，如「About 咕咕嘎嘎」→「关于 咕咕嘎嘎」）。
 * 认不出的项（分隔线、系统自动加的「听写 / 表情」等）= null，不动它。纯函数，单测。
 */
internal fun localizedAppMenuTitle(title: String, loc: AppMenuLocale): String? {
    val t = title.trim()
    val s = loc == AppMenuLocale.Simplified
    return when {
        t.startsWith("About ") -> (if (s) "关于 " else "關於 ") + t.removePrefix("About ")
        t == "About" -> if (s) "关于" else "關於"
        t.startsWith("Preferences") -> if (s) "设置…" else "設定…"
        t.startsWith("Settings") -> if (s) "设置…" else "設定…"
        t == "Services" -> if (s) "服务" else "服務"
        // 「Hide Others」要在「Hide 」前头认（它也以「Hide 」开头）
        t == "Hide Others" -> if (s) "隐藏其他" else "隱藏其他"
        t.startsWith("Hide ") -> (if (s) "隐藏 " else "隱藏 ") + t.removePrefix("Hide ")
        t == "Hide" -> if (s) "隐藏" else "隱藏"
        t == "Show All" -> if (s) "全部显示" else "全部顯示"
        t.startsWith("Quit ") -> (if (s) "退出 " else "結束 ") + t.removePrefix("Quit ")
        t == "Quit" -> if (s) "退出" else "結束"
        else -> null
    }
}

/**
 * 把系统应用菜单的几条英文标题按系统语言改成中文。菜单还没装上（NSApp.mainMenu 为空）就什么也不做。
 * 幂等：已经是中文的项认不出英文前缀，不会再改。要在**非主线程**调（内部 [Objc.onMain] 同步派发到主线程）。
 * 只改标题，不碰 action / target / 快捷键——关于 / 设置 / 退出的处理器照旧。
 */
internal fun localizeSystemAppMenu() {
    runCatching {
        Objc.onMain {
            val lang = Objc.string(Objc.array(Objc.msg(Objc.cls("NSLocale"), "preferredLanguages")).firstOrNull())
            val loc = appMenuLocale(lang) ?: return@onMain
            val app = Objc.msg(Objc.cls("NSApplication"), "sharedApplication") ?: return@onMain
            val main = Objc.msg(app, "mainMenu") ?: return@onMain
            val appItem = Objc.array(Objc.msg(main, "itemArray")).firstOrNull() ?: return@onMain
            val sub = Objc.msg(appItem, "submenu") ?: return@onMain
            for (item in Objc.array(Objc.msg(sub, "itemArray"))) {
                val title = Objc.string(Objc.msg(item, "title")) ?: continue
                val zh = localizedAppMenuTitle(title, loc) ?: continue
                // NSString stringWithUTF8String:（const char*）：传 null 结尾的 UTF-8 字节
                val ns = Objc.msg(Objc.cls("NSString"), "stringWithUTF8String:", zh.toByteArray(Charsets.UTF_8) + 0.toByte()) ?: continue
                Objc.send(item, "setTitle:", ns)
            }
        }
    }
}