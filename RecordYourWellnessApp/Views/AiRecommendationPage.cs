// Author: Cai Peilin, Xia Zihang
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.Models;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.Views;

public class AiRecommendationPage : UserControl
{
    private readonly IAgentApi _agentApi;
    private readonly ISettingsService _settings;
    private readonly Button _generateBtn;
    private readonly ProgressBar _progress;
    private readonly TextBox _resultBox;
    private readonly Panel _historyPanel, _pager;
    private readonly Label _errorLabel, _pageLabel, _emptyLabel;
    private readonly Button _firstBtn, _prevBtn, _nextBtn, _lastBtn, _goBtn;
    private readonly TextBox _jumpBox;

    private const int P = 32;
    private const int PageSize = 5;
    private List<AgentHistoryItem> _all = new();
    private int _page;   // 0-based

    public AiRecommendationPage(IAgentApi agentApi, ISettingsService settings)
    {
        _agentApi = agentApi; _settings = settings;
        this.Dock = DockStyle.Fill; this.BackColor = Theme.Background; this.AutoScroll = true;
        this.Font = Theme.Font(10f);

        Controls.Add(LabelBold(Loc.T("ai.title"), 24f, Theme.TextPrimary, P, 18, 320, 40));
        Controls.Add(Label(Loc.T("ai.subtitle"), 10f, Theme.TextSecondary, P, 60, 640, 24));

        _generateBtn = RoundedButton(Loc.T("ai.generate"), Theme.Primary, Color.White, 14, P, 96, 280, 42);
        _generateBtn.Click += async (_, _) => await GenerateAsync();
        Controls.Add(_generateBtn);

        _progress = new ProgressBar { Style = ProgressBarStyle.Marquee, Location = new Point(P, 146), Size = new Size(280, 4), Visible = false };
        Controls.Add(_progress);

        _errorLabel = Label("", 10f, Theme.Error, P + 300, 110, 460, 20); _errorLabel.Visible = false; Controls.Add(_errorLabel);

        _resultBox = new TextBox { Location = new Point(P, 160), Size = new Size(760, 150), Multiline = true,
            ReadOnly = true, ScrollBars = ScrollBars.Vertical, Font = Theme.Font(11f),
            ForeColor = Theme.TextPrimary, BackColor = Theme.Surface, BorderStyle = BorderStyle.FixedSingle,
            Text = Loc.T("ai.placeholder"),
            Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        Controls.Add(_resultBox);

        Controls.Add(LabelBold(Loc.T("ai.history"), 14f, Theme.TextPrimary, P, 326, 200, 24));

        // ── Pagination bar ──────────────────────────────────────────
        _pager = new Panel { Location = new Point(P, 356), Size = new Size(760, 32), BackColor = Theme.Background };
        _firstBtn = PagerBtn(Loc.T("ai.first"), 0);   _firstBtn.Click += (_, _) => GoToPage(0);
        _prevBtn  = PagerBtn(Loc.T("ai.prev"), 66);   _prevBtn.Click  += (_, _) => GoToPage(_page - 1);
        _pageLabel = new Label { Text = "", Font = Theme.Font(10f, FontStyle.Bold), ForeColor = Theme.TextPrimary,
            Location = new Point(136, 6), Size = new Size(150, 22), TextAlign = ContentAlignment.MiddleCenter, UseCompatibleTextRendering = false };
        _nextBtn  = PagerBtn(Loc.T("ai.next"), 292);  _nextBtn.Click  += (_, _) => GoToPage(_page + 1);
        _lastBtn  = PagerBtn(Loc.T("ai.last"), 358);  _lastBtn.Click  += (_, _) => GoToPage(int.MaxValue);
        _jumpBox = new TextBox { Location = new Point(436, 4), Size = new Size(46, 26), Font = Theme.Font(10f), BorderStyle = BorderStyle.FixedSingle, TextAlign = HorizontalAlignment.Center };
        _jumpBox.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; JumpTo(); } };
        _goBtn = PagerBtn(Loc.T("ai.go"), 488); _goBtn.Click += (_, _) => JumpTo();
        _pager.Controls.AddRange([_firstBtn, _prevBtn, _pageLabel, _nextBtn, _lastBtn, _jumpBox, _goBtn]);
        Controls.Add(_pager);

        _historyPanel = new Panel { Location = new Point(P, 396), Size = new Size(760, PageSize * 92 + 4), BackColor = Theme.Background, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        Controls.Add(_historyPanel);

        _emptyLabel = new Label { Text = Loc.T("ai.noHistory"), ForeColor = Theme.TextHint, Font = Theme.Font(11f), Location = new Point(P, 400), AutoSize = true, Visible = false };
        Controls.Add(_emptyLabel);

        this.Load += async (_, _) => await LoadHistoryAsync();
        this.Resize += (_, _) =>
        {
            int w = Math.Max(400, Width - 64);
            _resultBox.Width = w; _historyPanel.Width = w; _pager.Width = w;
            RenderHistoryPage();
        };
    }

    private static Button PagerBtn(string text, int x)
    {
        var b = new Button { Text = text, Font = Theme.Font(9f, FontStyle.Bold), FlatStyle = FlatStyle.Flat,
            ForeColor = Theme.Primary, BackColor = Theme.Surface, Location = new Point(x, 2), Size = new Size(62, 28), Cursor = Cursors.Hand };
        b.FlatAppearance.BorderColor = Theme.Outline; b.FlatAppearance.BorderSize = 1;
        return b;
    }

    private async Task GenerateAsync()
    {
        _generateBtn.Enabled = false; _progress.Visible = true; _errorLabel.Visible = false;
        try
        {
            var body = new Dictionary<string, object> { ["language"] = _settings.Language == "zh" ? "zh" : "en" };
            var rec = await _agentApi.Recommend(body);
            _resultBox.Text = string.IsNullOrWhiteSpace(rec.Recommendation)
                ? Loc.T("ai.noRec") : rec.Recommendation;
        }
        catch (Refit.ApiException ex)
        {
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
            { _errorLabel.Text = $"Could not generate a recommendation ({ex.StatusCode})."; _errorLabel.Visible = true; }
        }
        catch (Exception ex)
        {
            _errorLabel.Text = $"Network error: {ex.Message}"; _errorLabel.Visible = true;
        }
        finally { _generateBtn.Enabled = true; _progress.Visible = false; _page = 0; await LoadHistoryAsync(); }
    }

    private async Task LoadHistoryAsync()
    {
        if (IsDisposed) return;
        try
        {
            _all = await _agentApi.GetHistory(1000);
        }
        catch (Refit.ApiException) { return; /* 401 handled globally */ }
        catch { return; }
        RenderHistoryPage();
    }

    private int TotalPages => Math.Max(1, (_all.Count + PageSize - 1) / PageSize);

    private void GoToPage(int p)
    {
        _page = Math.Clamp(p, 0, TotalPages - 1);
        RenderHistoryPage();
    }

    private void JumpTo()
    {
        if (int.TryParse(_jumpBox.Text.Trim(), out var oneBased)) GoToPage(oneBased - 1);
        _jumpBox.Text = "";
    }

    private void RenderHistoryPage()
    {
        if (IsDisposed) return;
        _historyPanel.Controls.Clear();

        bool empty = _all.Count == 0;
        _emptyLabel.Visible = empty;
        _pager.Visible = !empty;
        if (empty) return;

        _page = Math.Clamp(_page, 0, TotalPages - 1);
        _pageLabel.Text = Loc.T("ai.pageOf", _page + 1, TotalPages);
        _firstBtn.Enabled = _prevBtn.Enabled = _page > 0;
        _nextBtn.Enabled = _lastBtn.Enabled = _page < TotalPages - 1;

        int y = 0;
        foreach (var item in _all.Skip(_page * PageSize).Take(PageSize))
        {
            var card = new Panel { Location = new Point(0, y), Size = new Size(_historyPanel.Width - 24, 84), BackColor = Theme.Surface, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
            card.Paint += (_, e) => { e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias; using var path = Theme.RoundedRect(new Rectangle(0, 0, card.Width - 1, card.Height - 1), 10); using var b = new SolidBrush(Theme.Surface); e.Graphics.FillPath(b, path); };
            var date = !string.IsNullOrEmpty(item.CreatedAt) && DateTime.TryParse(item.CreatedAt, out var dt) ? dt.ToString("MMM d, HH:mm", System.Globalization.CultureInfo.InvariantCulture) : item.CreatedAt;
            card.Controls.Add(Label(date, 9f, Theme.TextHint, 14, 8, 180, 16));
            var preview = item.Content.Length > 200 ? item.Content[..200] + "…" : item.Content;
            var previewLbl = new Label { Text = preview, Font = Theme.Font(10f), ForeColor = Theme.TextPrimary,
                Location = new Point(14, 28), Size = new Size(card.Width - 110, 48), AutoEllipsis = true,
                UseCompatibleTextRendering = false, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
            card.Controls.Add(previewLbl);
            var del = RecordManageForm.FlatButton(Loc.T("ai.delete"), Theme.Error, card.Width - 88, 8, 74, 24);
            del.Click += async (_, _) => { try { await _agentApi.DeleteRecommendation(item.Id); await LoadHistoryAsync(); } catch { } };
            del.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            card.Controls.Add(del);
            _historyPanel.Controls.Add(card);
            y += 92;
        }
    }
}
