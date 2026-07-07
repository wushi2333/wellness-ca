// Author: Wang Songyu, Liu Yu, Huang Qianer, Xia Zihang
using Microsoft.Extensions.DependencyInjection;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.ViewModels;

namespace RecordYourWellnessApp.Views;

public class MainForm : Form
{
    private readonly ISessionService _session;
    private readonly IServiceProvider _sp;
    private readonly MainViewModel _vm;
    private readonly IProfileApi _profileApi;
    private readonly Panel _navPanel, _contentPanel;
    private readonly Label _greetingLabel, _usernameLabel;
    private readonly Panel _avatarPanel;
    private UserControl? _currentPage;
    private Image? _avatarImage;
    private readonly List<(Button Btn, string Label)> _navBtns = new();
    private Button? _activeBtn;

    public MainForm(ISessionService session, IServiceProvider sp, MainViewModel vm, IProfileApi profileApi)
    {
        _session = session; _sp = sp; _vm = vm; _profileApi = profileApi;
        this.Text = Constants.AppName;
        this.ClientSize = new Size(1100, 720);
        this.StartPosition = FormStartPosition.CenterScreen;
        this.BackColor = Theme.Background;
        this.Font = Theme.Font(10f);
        this.MinimumSize = new Size(960, 640);
        this.AutoScaleMode = AutoScaleMode.Dpi;

        // ── Content (fills everything behind the nav) ───────────────
        _contentPanel = new Panel { BackColor = Theme.Background, Dock = DockStyle.Fill };
        Controls.Add(_contentPanel);

        // ── Left nav (deep teal, matching Android bottom nav colour) ──
        // Solid fill (no gradient): a gradient here painted behind the labels, whose own
        // solid NavBar background then showed as a mismatched box around the text.
        _navPanel = new Panel { Width = 232, Dock = DockStyle.Left, BackColor = Theme.NavBar };
        Controls.Add(_navPanel);

        // App title
        _navPanel.Controls.Add(new Label { Text = "Wellness", Font = Theme.Font(18f, FontStyle.Bold),
            ForeColor = Color.White, BackColor = Color.Transparent, Location = new Point(22, 24), AutoSize = true });
        _navPanel.Controls.Add(new Label { Text = Loc.T("nav.tracker"), Font = Theme.Font(13f),
            ForeColor = Theme.NavTextInactive, BackColor = Color.Transparent, Location = new Point(24, 56), AutoSize = true });

        // Avatar (click → Profile)
        _avatarPanel = new Panel { Size = new Size(46, 46), Location = new Point(22, 104), Cursor = Cursors.Hand };
        _avatarPanel.Click += (_, _) => OpenProfile();
        _avatarPanel.Paint += (_, e) =>
        {
            var g = e.Graphics; g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var r = new Rectangle(0, 0, 46, 46);
            using var path = Theme.RoundedRect(r, 23);
            if (_avatarImage != null)
            {
                g.SetClip(path);
                g.DrawImage(_avatarImage, r);
                g.ResetClip();
            }
            else
            {
                using var b = new SolidBrush(Theme.Primary);
                g.FillPath(b, path);
                var init = string.IsNullOrEmpty(_session.Username) ? "U" : char.ToUpper(_session.Username[0]).ToString();
                using var f = Theme.Font(16f, FontStyle.Bold);
                using var w = new SolidBrush(Color.White);
                var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
                g.DrawString(init, f, w, new RectangleF(0, 0, 46, 46), sf);
            }
            using var outline = new Pen(Color.FromArgb(40, 255, 255, 255), 2);
            g.DrawPath(outline, path);
        };
        _navPanel.Controls.Add(_avatarPanel);

        _greetingLabel = new Label { Text = _vm.Greeting + ",", Font = Theme.Font(9f),
            ForeColor = Theme.NavTextInactive, Location = new Point(78, 106), AutoSize = true };
        _navPanel.Controls.Add(_greetingLabel);
        _usernameLabel = new Label { Text = _session.Username, Font = Theme.Font(11f, FontStyle.Bold),
            ForeColor = Color.White, Location = new Point(78, 124), AutoSize = true };
        _navPanel.Controls.Add(_usernameLabel);

        // Nav items — the first arg is a stable key (used for programmatic matching), the
        // second is the localized label shown to the user.
        AddNav("Dashboard", Loc.T("nav.dashboard"), "🏠", 178, () => LoadPage<DashboardPage>());
        AddNav("Sleep", Loc.T("nav.sleep"), "😴", 224, () => LoadPage<SleepDetailPage>());
        AddNav("Exercise", Loc.T("nav.exercise"), "🏃", 270, () => LoadPage<ExerciseDetailPage>());
        AddNav("AI Insights", Loc.T("nav.ai"), "🤖", 316, () => LoadPage<AiRecommendationPage>());
        AddNav("Chat", Loc.T("nav.chat"), "💬", 362, () => LoadPage<CharacterChatPage>());

        _navPanel.Controls.Add(new Label { Text = new string('─', 22), ForeColor = Color.FromArgb(70, 255, 255, 255),
            Location = new Point(22, 416), Size = new Size(188, 18), Font = Theme.Font(7f) });

        AddNav("Settings", Loc.T("nav.settings"), "⚙️", 438, () => OpenSettings(), bottom: true);
        AddNav("Logout", Loc.T("nav.logout"), "🚪", 484, () => _vm.LogoutCommand.Execute(null), bottom: true, danger: true);

        this.Load += (_, _) => _ = LoadProfileAsync();
        LoadPage<DashboardPage>();
    }

    private void AddNav(string key, string display, string icon, int y, Action action, bool bottom = false, bool danger = false)
    {
        var btn = new Button
        {
            Text = $"  {icon}   {display}",
            TextAlign = ContentAlignment.MiddleLeft,
            FlatStyle = FlatStyle.Flat,
            Size = new Size(208, 42),
            Location = new Point(12, y),
            Font = Theme.Font(10f),
            ForeColor = danger ? Color.FromArgb(0xFF, 0xC4, 0xC4) : Theme.NavText,
            BackColor = Color.Transparent,
            Cursor = Cursors.Hand,
            FlatAppearance = { BorderSize = 0, MouseOverBackColor = Color.FromArgb(28, 255, 255, 255) }
        };
        btn.Click += (_, _) =>
        {
            if (danger) { action(); return; }
            Highlight(btn);
            action();
        };
        _navPanel.Controls.Add(btn);
        _navBtns.Add((btn, key)); // store the stable key for matching, not the localized text
        if (key == "Dashboard") Highlight(btn);
    }

    private void Highlight(Button btn)
    {
        foreach (var (b, _) in _navBtns) { b.BackColor = Color.Transparent; b.ForeColor = Theme.NavText; }
        btn.BackColor = Theme.NavActiveBg;
        btn.ForeColor = Color.White;
        _activeBtn = btn;
    }

    /// <summary>Swap the content page (disposes the previous one — fresh state each visit,
    /// matching the Android fragments recreated on navigation).</summary>
    private void LoadPage<T>() where T : UserControl
    {
        _currentPage?.Dispose();
        var page = _sp.GetRequiredService<T>();
        _currentPage = page;
        page.Dock = DockStyle.Fill;
        _contentPanel.Controls.Clear();
        _contentPanel.Controls.Add(page);
    }

    /// <summary>Programmatic navigation from a page (e.g. Dashboard "View all insights"),
    /// without the fragile reflection the old code used.</summary>
    public void ShowAiInsights()
    {
        var match = _navBtns.FirstOrDefault(x => x.Label == "AI Insights").Btn;
        if (match != null) Highlight(match);
        LoadPage<AiRecommendationPage>();
    }

    /// <summary>Agent intents from the chat page navigate the user to the relevant screen,
    /// mirroring the Android ChatFragment.handleIntent flow.</summary>
    public void NavigateToAgentTarget(string? target)
    {
        switch (target)
        {
            case "sleep_detail":
                Highlight(_navBtns.FirstOrDefault(x => x.Label == "Sleep").Btn!);
                LoadPage<SleepDetailPage>(); break;
            case "exercise_detail":
                Highlight(_navBtns.FirstOrDefault(x => x.Label == "Exercise").Btn!);
                LoadPage<ExerciseDetailPage>(); break;
            case "wellness_insights":
                ShowAiInsights(); break;
            case "wellness_entry":
                // take the user to the Dashboard, which has the + Add buttons
                Highlight(_navBtns.FirstOrDefault(x => x.Label == "Dashboard").Btn!);
                LoadPage<DashboardPage>(); break;
            case "dashboard":
            default:
                Highlight(_navBtns.FirstOrDefault(x => x.Label == "Dashboard").Btn!);
                LoadPage<DashboardPage>(); break;
        }
    }

    private async Task LoadProfileAsync()
    {
        try
        {
            var user = await _profileApi.GetUser();
            _session.SetUserProfile(user.Username, user.UserId, user.Provider);
            _vm.RefreshIdentity();
            _greetingLabel.Text = _vm.Greeting + ",";
            _usernameLabel.Text = _session.Username;
            if (!string.IsNullOrWhiteSpace(user.AvatarUrl))
                _ = LoadAvatarAsync(user.AvatarUrl);
        }
        catch { /* 401 handled centrally by the delegating handler */ }
    }

    private async Task LoadAvatarAsync(string url)
    {
        try
        {
            var fullUrl = url.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? url : Constants.BaseUrl + url;
            using var http = new HttpClient();
            var bytes = await http.GetByteArrayAsync(fullUrl);
            using var ms = new MemoryStream(bytes);
            _avatarImage?.Dispose();
            _avatarImage = Image.FromStream(ms)?.Clone() as Image; // detach from stream
            _avatarPanel.Invalidate();
        }
        catch { }
    }

    private void OpenProfile()
    {
        var p = _sp.GetRequiredService<ProfilePage>();
        p.ShowDialog(this);
        // Profile may have changed username/avatar — refresh the nav + page.
        _ = LoadProfileAsync();
        _currentPage?.Invalidate();
    }

    private void OpenSettings()
    {
        var s = _sp.GetRequiredService<SettingsPage>();
        s.ShowDialog(this);
        if (s.LogoutRequested)
        {
            _session.Logout(); // AppContext listens and navigates to login
        }
        else if (s.LanguageChanged)
        {
            // Recreate the shell so every view (and this nav) re-renders in the new language.
            _sp.GetRequiredService<INavigationService>().GoToMain();
        }
        else
        {
            _currentPage?.Invalidate();
        }
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        _avatarImage?.Dispose();
        base.OnFormClosing(e);
    }
}
