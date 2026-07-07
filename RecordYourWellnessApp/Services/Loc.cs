// Author: Xia Zihang
namespace RecordYourWellnessApp.Services;

/// <summary>
/// Lightweight in-app localization (English / 简体中文). Views call <see cref="T"/> at
/// build time; when the language changes the shell (MainForm) is recreated so every
/// view re-reads the strings. Unknown keys fall back to the key itself, so a missing
/// entry is visible rather than blank.
/// </summary>
public static class Loc
{
    public static string Lang { get; private set; } = "en";

    public static void Set(string lang) => Lang = lang == "zh" ? "zh" : "en";

    public static bool IsZh => Lang == "zh";

    public static string T(string key)
    {
        if (Map.TryGetValue(key, out var v)) return IsZh ? v.zh : v.en;
        return key;
    }

    /// <summary>Formatted lookup: T("welcome", name).</summary>
    public static string T(string key, params object[] args) => string.Format(T(key), args);

    private static readonly Dictionary<string, (string en, string zh)> Map = new()
    {
        // ── Nav ──────────────────────────────────────────────
        ["nav.tracker"]      = ("Tracker", "健康助手"),
        ["nav.dashboard"]    = ("Dashboard", "仪表盘"),
        ["nav.sleep"]        = ("Sleep", "睡眠"),
        ["nav.exercise"]     = ("Exercise", "运动"),
        ["nav.ai"]           = ("AI Insights", "AI 建议"),
        ["nav.chat"]         = ("Chat", "聊天"),
        ["nav.settings"]     = ("Settings", "设置"),
        ["nav.logout"]       = ("Logout", "退出登录"),
        ["greeting.morning"]   = ("Good morning", "早上好"),
        ["greeting.afternoon"] = ("Good afternoon", "下午好"),
        ["greeting.evening"]   = ("Good evening", "晚上好"),

        // ── Dashboard ────────────────────────────────────────
        ["dash.title"]       = ("Dashboard", "仪表盘"),
        ["dash.addSleep"]    = ("+ Add Sleep", "+ 添加睡眠"),
        ["dash.addExercise"] = ("+ Add Exercise", "+ 添加运动"),
        ["dash.refresh"]     = ("↻ Refresh", "↻ 刷新"),
        ["dash.welcome"]     = ("Welcome back, {0}!", "欢迎回来，{0}！"),
        ["dash.sleepWeek"]   = ("😴  Sleep This Week", "😴  本周睡眠"),
        ["dash.exWeek"]      = ("🏃  Exercise This Week", "🏃  本周运动"),
        ["dash.avg"]         = ("avg", "平均"),
        ["dash.best"]        = ("best", "最佳"),
        ["dash.mood"]        = ("mood", "心情"),
        ["dash.vsLastWk"]    = ("vs last wk", "对比上周"),
        ["dash.avgDay"]      = ("avg / day", "日均"),
        ["dash.activeDays"]  = ("active days", "活跃天数"),
        ["dash.total"]       = ("total", "总计"),
        ["dash.viewAll"]     = ("View all insights →", "查看全部建议 →"),
        ["dash.noSleep"]     = ("No sleep logged this week", "本周没有睡眠记录"),
        ["dash.noEx"]        = ("No exercise logged this week", "本周没有运动记录"),

        // ── Sleep / Exercise detail ──────────────────────────
        ["sleep.title"]      = ("😴  Sleep", "😴  睡眠"),
        ["ex.title"]         = ("🏃  Exercise", "🏃  运动"),
        ["common.add"]       = ("+ Add", "+ 添加"),
        ["common.manage"]    = ("Manage", "管理"),
        ["sleep.average"]    = ("Average", "平均"),
        ["sleep.bestNight"]  = ("Best Night", "最佳一晚"),
        ["sleep.avgMood"]    = ("Avg Mood", "平均心情"),
        ["sleep.noWeek"]     = ("No sleep records for this week", "本周没有睡眠记录"),
        ["sleep.target"]     = ("7h target", "7 小时目标"),
        ["ex.avgActive"]     = ("Avg / Active Day", "活跃日均"),
        ["ex.activeDays"]    = ("Active Days", "活跃天数"),
        ["ex.noWeek"]        = ("No exercise records for this week", "本周没有运动记录"),
        ["ex.target"]        = ("30m target", "30 分钟目标"),
        ["ex.breakdown"]     = ("Activity Breakdown", "运动类型分布"),
        ["ex.noActivities"]  = ("No activities this week", "本周没有运动"),

        // ── AI Insights ──────────────────────────────────────
        ["ai.title"]         = ("🤖  AI Insights", "🤖  AI 建议"),
        ["ai.subtitle"]      = ("Personalized wellness recommendations based on your records",
                                "根据你的记录生成的个性化健康建议"),
        ["ai.generate"]      = ("✨  Generate Recommendation", "✨  生成建议"),
        ["ai.placeholder"]   = ("Tap \"Generate Recommendation\" to get AI-powered insights tailored to your recent sleep and exercise data.",
                                "点击“生成建议”，根据你近期的睡眠与运动数据获取 AI 个性化建议。"),
        ["ai.history"]       = ("History", "历史记录"),
        ["ai.noHistory"]     = ("No history yet.", "暂无历史记录。"),
        ["ai.delete"]        = ("Delete", "删除"),
        ["ai.noRec"]         = ("(No recommendation returned)", "（未返回建议）"),
        ["ai.pageOf"]        = ("Page {0} / {1}", "第 {0} / {1} 页"),
        ["ai.first"]         = ("« First", "« 首页"),
        ["ai.prev"]          = ("‹ Prev", "‹ 上一页"),
        ["ai.next"]          = ("Next ›", "下一页 ›"),
        ["ai.last"]          = ("Last »", "末页 »"),
        ["ai.go"]            = ("Go", "跳转"),

        // ── Chat ─────────────────────────────────────────────
        ["chat.sessions"]    = ("Sessions", "会话"),
        ["chat.new"]         = ("+ New", "+ 新建"),
        ["chat.hint"]        = ("Switch or start a session", "切换或新建一个会话"),
        ["chat.modeChat"]    = ("Chat", "闲聊"),
        ["chat.modeAgent"]   = ("Agent", "智能体"),
        ["chat.placeholder"] = ("Type a message…  (Enter to send, Shift+Enter for newline)",
                                "输入消息…（回车发送，Shift+回车换行）"),
        ["chat.send"]        = ("Send", "发送"),
        ["chat.thinking"]    = ("Yui is thinking…", "Yui 正在思考…"),
        ["chat.recording"]   = ("● Recording…", "● 录音中…"),
        ["chat.transcribing"]= ("Transcribing…", "识别中…"),
        ["chat.ttsOn"]       = ("Voice replies on", "语音回复：开"),
        ["chat.ttsOff"]      = ("Voice replies off", "语音回复：关"),
        ["chat.newChat"]     = ("New chat", "新会话"),
        ["chat.msgs"]        = ("msgs", "条消息"),

        // ── Settings ─────────────────────────────────────────
        ["settings.title"]      = ("Settings", "设置"),
        ["settings.profile"]    = ("Profile", "个人资料"),
        ["settings.profileSub"] = ("Edit your avatar, name, metrics", "编辑头像、昵称与身体数据"),
        ["settings.password"]   = ("Change Password", "修改密码"),
        ["settings.passwordSub"]= ("Update your password", "更新你的登录密码"),
        ["settings.language"]   = ("🌐  Language", "🌐  语言"),
        ["settings.languageSub"]= ("Interface & AI language", "界面与 AI 语言"),
        ["settings.logout"]     = ("Logout", "退出登录"),
        ["settings.delete"]     = ("Delete Account", "注销账户"),
    };
}
