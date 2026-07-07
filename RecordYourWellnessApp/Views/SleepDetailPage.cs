using System.Drawing.Drawing2D;
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.ViewModels;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.Views;

public class SleepDetailPage : UserControl
{
    private readonly SleepDetailViewModel _vm;
    private readonly IRecordApi _recordApi;
    private readonly ISessionService _session;
    private readonly Label _avgVal, _bestVal, _moodVal, _weekLabel, _errorLabel;
    private readonly Panel _chartPanel, _loadingOverlay;
    private readonly Button _addBtn, _manageBtn, _nextBtn;
    private List<double> _data = new();

    private const int P = 32;
    private static DateTime CurrentMonday => DateTime.Today.AddDays(-(int)DateTime.Today.DayOfWeek + 1);

    public SleepDetailPage(SleepDetailViewModel vm, IRecordApi recordApi, ISessionService session)
    {
        _vm = vm; _recordApi = recordApi; _session = session;
        this.Dock = DockStyle.Fill; this.BackColor = Theme.Background; this.AutoScroll = true;
        this.Font = Theme.Font(10f);

        // ── Row 1: title (left) + Add / Manage (right) ────────────────
        Controls.Add(LabelBold(Loc.T("sleep.title"), 24f, Theme.TextPrimary, P, 14, 240, 40));

        _addBtn = RoundedButton(Loc.T("common.add"), Theme.SleepAccent, Color.White, 14, 0, 16, 100, 38);
        _addBtn.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        _addBtn.Click += (_, _) => _ = AddOrRefreshAsync();
        Controls.Add(_addBtn);
        _manageBtn = RoundedButton(Loc.T("common.manage"), Theme.Primary, Color.White, 14, 0, 16, 110, 38);
        _manageBtn.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        _manageBtn.Click += (_, _) => OpenManage();
        Controls.Add(_manageBtn);

        // ── Row 2: week navigation (< week-range >) ───────────────────
        var prevBtn = NavBtn("<", P, 58); prevBtn.Click += (_, _) => _vm.PrevWeekCommand.Execute(null); Controls.Add(prevBtn);
        _weekLabel = Label(_vm.WeekRangeText, 13f, Theme.TextPrimary, P + 48, 62, 250, 26);
        _weekLabel.TextAlign = ContentAlignment.MiddleCenter; _weekLabel.UseCompatibleTextRendering = false; Controls.Add(_weekLabel);
        _nextBtn = NavBtn(">", P + 306, 58); _nextBtn.Click += (_, _) => _vm.NextWeekCommand.Execute(null); Controls.Add(_nextBtn);
        UpdateWeekNav();

        // Error
        _errorLabel = Label("", 10f, Theme.Error, P, 100, 780, 22); _errorLabel.Visible = false; Controls.Add(_errorLabel);

        // Chart
        _chartPanel = new Panel { Location = new Point(P, 124), Size = new Size(800, 280), BackColor = Theme.Background, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        _chartPanel.Paint += PaintChart; Controls.Add(_chartPanel);

        // Stats row
        var statsY = 420;
        _avgVal = AddStatCard(P, statsY, Loc.T("sleep.average"), "-- h", Theme.SleepAccent);
        _bestVal = AddStatCard(P + 220, statsY, Loc.T("sleep.bestNight"), "-- h", Theme.TextPrimary);
        _moodVal = AddStatCard(P + 440, statsY, Loc.T("sleep.avgMood"), "--", Theme.Warning);

        // Loading overlay
        _loadingOverlay = MakeLoadingOverlay();
        Controls.Add(_loadingOverlay); _loadingOverlay.BringToFront(); _loadingOverlay.Visible = false;

        _vm.PropertyChanged += (_, e) => { if (InvokeRequired) BeginInvoke(() => RefreshUI(e.PropertyName)); else RefreshUI(e.PropertyName); };
        this.Load += async (_, _) => { LayoutCharts(); await _vm.LoadCommand.ExecuteAsync(null); };
        this.Resize += (_, _) => LayoutCharts();
    }

    private Button NavBtn(string text, int x, int y)
    {
        var b = new Button { Text = text, Font = Theme.Font(14f, FontStyle.Bold), ForeColor = Theme.Primary, FlatStyle = FlatStyle.Flat, Location = new Point(x, y), Size = new Size(40, 36), Cursor = Cursors.Hand };
        b.FlatAppearance.BorderSize = 0;
        b.FlatAppearance.MouseOverBackColor = Theme.SurfaceVariant;
        b.Paint += (sender, e) => { if (sender is Button btn) { e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; e.Graphics.Clear(btn.Parent?.BackColor ?? Theme.Background); using var p = new Pen(Theme.Outline, 1); var r = new Rectangle(0, 0, btn.Width - 1, btn.Height - 1); using var path = Theme.RoundedRect(r, 10); using var fill = new SolidBrush(btn.Enabled ? Theme.Surface : Theme.SurfaceVariant); e.Graphics.FillPath(fill, path); e.Graphics.DrawPath(p, path); TextRenderer.DrawText(e.Graphics, btn.Text, btn.Font, r, btn.Enabled ? btn.ForeColor : Theme.TextHint, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter); } };
        return b;
    }

    private void LayoutCharts()
    {
        _chartPanel.Width = Math.Max(360, Width - P * 2);
        // Pin Add / Manage to the right edge (deterministic — the old anchor-only
        // approach left both buttons stacked at x=0).
        _manageBtn.Location = new Point(Width - P - _manageBtn.Width, 16);
        _addBtn.Location = new Point(_manageBtn.Left - 10 - _addBtn.Width, 16);
    }

    /// <summary>Prev is always available (there may be older data); Next is disabled at the
    /// current week since future weeks can never contain records.</summary>
    private void UpdateWeekNav() => _nextBtn.Enabled = _vm.WeekStart.Date < CurrentMonday;

    private Label AddStatCard(int x, int y, string label, string value, Color accent)
    {
        var p = new Panel { Location = new Point(x, y), Size = new Size(200, 80), BackColor = Theme.Surface, Anchor = AnchorStyles.Top | AnchorStyles.Left };
        p.Paint += (_, e) => { e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var r = new Rectangle(0, 0, p.Width - 1, p.Height - 1); using var path = Theme.RoundedRect(r, 14); using var b = new SolidBrush(Theme.Surface); e.Graphics.FillPath(b, path); using var bar = new SolidBrush(accent); e.Graphics.FillPath(bar, Theme.RoundedRect(new Rectangle(0, 10, 4, p.Height - 20), 2)); };
        p.Controls.Add(Label(label, 11f, Theme.TextHint, 16, 12, 170, 18));
        var v = LabelBold(value, 22f, accent, 16, 34, 170, 36); p.Controls.Add(v); Controls.Add(p);
        return v;
    }

    private static Panel MakeLoadingOverlay()
    {
        var overlay = new Panel { Dock = DockStyle.Fill, BackColor = Color.FromArgb(110, 0xF8, 0xFA, 0xFC), Visible = false };
        var spinner = new ProgressBar { Style = ProgressBarStyle.Marquee, Size = new Size(80, 6) };
        overlay.Controls.Add(spinner);
        overlay.Paint += (_, _) => { spinner.Location = new Point((overlay.Width - 80) / 2, overlay.Height / 2); };
        return overlay;
    }

    private async Task AddOrRefreshAsync()
    {
        using var form = new AddEditSleepForm(_recordApi, _session, null);
        form.ShowDialog(this);
        if (form.DialogResult == DialogResult.OK)
            await _vm.LoadCommand.ExecuteAsync(null);
    }

    private void OpenManage()
    {
        using var f = new RecordManageForm("sleep", _recordApi, _session);
        f.ShowDialog(this);
        _ = _vm.LoadCommand.ExecuteAsync(null); // refresh (matches Android: back from manage → reload)
    }

    private void PaintChart(object? _, PaintEventArgs e)
    {
        var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        var w = _chartPanel.Width; var h = _chartPanel.Height;
        using (var path = Theme.RoundedRect(new Rectangle(0, 0, w - 1, h - 1), 14))
        using (var bg = new SolidBrush(Theme.Surface)) { g.FillPath(bg, path); }
        if (!_vm.HasData) { DrawEmpty(g, w, h, Loc.T("sleep.noWeek")); return; }
        var margin = 44; var barW = Math.Min(60, (w - margin * 2) / 7 - 8); var maxH = h - 70;

        for (int i = 0; i <= 4; i++)
        {
            var gy = margin + maxH * i / 4f;
            using var pen = new Pen(Theme.ChartGridLine, 0.5f) { DashStyle = DashStyle.Dash };
            g.DrawLine(pen, margin, gy, w - margin, gy);
            g.DrawString($"{12 - i * 3}h", Theme.Font(8f), new SolidBrush(Theme.TextHint), 8, gy - 8);
        }
        var targetY = margin + maxH * (1 - 7f / 12f);
        using var targetPen = new Pen(Theme.ChartTargetLine, 1f) { DashStyle = DashStyle.Dash };
        g.DrawLine(targetPen, margin, targetY, w - margin, targetY);
        g.DrawString(Loc.T("sleep.target"), Theme.Font(8f, FontStyle.Bold), new SolidBrush(Theme.ChartTargetLine), w - 78, targetY - 16);

        for (int i = 0; i < _data.Count; i++)
        {
            var barH = Math.Max(2f, (float)(_data[i] / 12f * maxH));
            var x = margin + (w - margin * 2f) / 7f * i + ((w - margin * 2f) / 7f - barW) / 2f;
            var y = margin + maxH - barH;
            var rect = new RectangleF(x, y, barW, barH);
            using var barBrush = new LinearGradientBrush(new PointF(x, y), new PointF(x, y + barH), Theme.ChartSleepGradientTop, Theme.ChartSleepGradientBottom);
            g.FillRoundedRectangle(barBrush, rect, 12);
            if (_data[i] > 0)
                g.DrawString($"{_data[i]:F1}", Theme.Font(9f, FontStyle.Bold), new SolidBrush(Theme.TextSecondary), x, y - 18);
            var label = _vm.WeekLabels.Count > i ? _vm.WeekLabels[i] : "";
            g.DrawString(label, Theme.Font(9f), new SolidBrush(Theme.TextHint), x + barW / 2 - 14, h - 22);
        }
    }

    private void RefreshUI(string? prop)
    {
        switch (prop)
        {
            case nameof(_vm.WeekStart):
            case nameof(_vm.WeekRangeText):
                _weekLabel.Text = _vm.WeekRangeText; UpdateWeekNav(); break;
            case nameof(_vm.IsLoading): _loadingOverlay.Visible = _vm.IsLoading; break;
            case nameof(_vm.HasError): _errorLabel.Visible = _vm.HasError; _errorLabel.Text = _vm.ErrorMessage; break;
            case nameof(_vm.AvgHours): _avgVal.Text = $"{_vm.AvgHours:F1} h"; break;
            case nameof(_vm.BestNight): _bestVal.Text = $"{_vm.BestNight:F1} h"; break;
            case nameof(_vm.AvgMood): _moodVal.Text = $"{_vm.AvgMood:F1}/5"; break;
            case nameof(_vm.WeekData): _data = _vm.WeekData; _chartPanel.Invalidate(); break;
        }
    }

    private static void DrawEmpty(Graphics g, int w, int h, string msg)
    {
        using var f = Theme.Font(11f); using var b = new SolidBrush(Theme.TextHint);
        var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
        g.DrawString(msg, f, b, new RectangleF(0, 0, w, h), sf);
    }
}

/// <summary>Shared rounded-rectangle fill used by chart painting across pages.</summary>
internal static class GraphicsExtensions
{
    public static void FillRoundedRectangle(this Graphics g, Brush brush, RectangleF rect, int radius)
    {
        using var path = new GraphicsPath();
        float d = radius * 2;
        path.AddArc(rect.X, rect.Y, d, d, 180, 90);
        path.AddArc(rect.Right - d, rect.Y, d, d, 270, 90);
        path.AddArc(rect.Right - d, rect.Bottom - d, d, d, 0, 90);
        path.AddArc(rect.X, rect.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        g.FillPath(brush, path);
    }
}
