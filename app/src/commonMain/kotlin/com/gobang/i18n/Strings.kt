package com.gobang.i18n

/** 中英文字符串表，key 为标识符，value 为翻译文本 */
object Strings {
    val zh = mapOf(
        "app_title" to "五子棋",
        "app_subtitle" to "连珠五子",
        "game_mode" to "游戏模式",
        "mode_pvai" to "人先手 对战 AI",
        "mode_aivp" to "AI先手 人后手",
        "mode_pvp" to "双人对战",
        "mode_aivai" to "AI 对战（观战）",
        "difficulty" to "难度",
        "diff_easy" to "简单",
        "diff_easy_desc" to "快速，可能犯错",
        "diff_medium" to "中等",
        "diff_medium_desc" to "均衡对弈",
        "diff_hard" to "困难",
        "diff_hard_desc" to "强力对弈，思考较慢",
        "start_game" to "开始游戏",
        "continue_game" to "继续游戏",
        "settings" to "设置",
        "back" to "返回",
        "language" to "语言",
        "lang_zh" to "中文",
        "lang_en" to "English",
        "round" to "第 {n} 回合",
        "black_wins" to "⚫ 黑棋胜！",
        "white_wins" to "⚪ 白棋胜！",
        "draw" to "平局！",
        "ai_thinking" to "AI 思考中…",
        "black_turn" to "⚫ 黑棋回合",
        "white_turn" to "⚪ 白棋回合",
        "undo" to "悔棋",
        "redo" to "重做",
        "menu" to "菜单",
        "new_game" to "新局",
        "opening" to "开局",
        "opening_random" to "随机",
        "opening_none" to "无",
        "opening_direct" to "—— 直接开局 ——",
        "opening_indirect" to "—— 间接开局 ——",
        "opening_nonstandard" to "—— 非标准 ——",
        "difficulty_always" to "难度",
    )

    val en = mapOf(
        "app_title" to "Gobang",
        "app_subtitle" to "Five in a Row",
        "game_mode" to "Game Mode",
        "mode_pvai" to "Player vs AI",
        "mode_aivp" to "AI vs Player",
        "mode_pvp" to "Player vs Player",
        "mode_aivai" to "AI vs AI (Watch)",
        "difficulty" to "Difficulty",
        "diff_easy" to "Easy",
        "diff_easy_desc" to "Fast, may make mistakes",
        "diff_medium" to "Medium",
        "diff_medium_desc" to "Balanced play",
        "diff_hard" to "Hard",
        "diff_hard_desc" to "Strong play, slower thinking",
        "start_game" to "Start Game",
        "continue_game" to "Continue",
        "settings" to "Settings",
        "back" to "Back",
        "language" to "Language",
        "lang_zh" to "中文",
        "lang_en" to "English",
        "round" to "Round {n}",
        "black_wins" to "\u25CF Black wins!",
        "white_wins" to "\u25CB White wins!",
        "draw" to "Draw!",
        "ai_thinking" to "AI thinking\u2026",
        "black_turn" to "\u25CF Black's turn",
        "white_turn" to "\u25CB White's turn",
        "undo" to "Undo",
        "redo" to "Redo",
        "menu" to "Menu",
        "new_game" to "New Game",
        "opening" to "Opening",
        "opening_random" to "Random",
        "opening_none" to "None",
        "opening_direct" to "—— Direct ——",
        "opening_indirect" to "—— Indirect ——",
        "opening_nonstandard" to "—— Non-standard ——",
        "difficulty_always" to "Difficulty",
    )

    fun get(lang: String, key: String, vararg args: Pair<String, Any>): String {
        val map = if (lang == "zh") zh else en
        var s = map[key] ?: (en[key] ?: key)
        for ((k, v) in args) {
            s = s.replace("{$k}", v.toString())
        }
        return s
    }
}