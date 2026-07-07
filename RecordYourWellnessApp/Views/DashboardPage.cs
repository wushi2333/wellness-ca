// Author: Wang Songyu, Xia Zihang
using System.Drawing.Drawing2D;
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.ViewModels;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.Views;

public class DashboardPage : UserControl
{
    private readonly DashboardViewModel _vm;
    private readonly IRecordApi _recordApi;
    private readonly ISessionService _session;
    private readonly Panel _sleepChartPanel, _exChartPanel;
    private readonly Panel _loadingOverlay;
    private readonly Label _errorLabel;
    private readonly Label _lSleepAvg, _lSleepBest, _lSleepMood, _lSleepVs;
    private readonly Label _lExAvg, _lExActive, _lExTotal, _lTipText;

    private const int P = 28;

    public DashboardPage(DashboardViewModel vm, IRecordApi recordApi, ISessionService session)
    {
        _vm = vm; _recordApi = recordApi; _session = session;
        this.Dock = DockStyle.Fill;
        this.BackColor = Theme.Background;
        this.AutoScroll = true;
        this.Font = Theme.Font(10f);

        int y = 16;

        // ── Title + actions ───────────────────────────────────────
        AddAt(LabelBold(Loc.T("dash.title"), 24f, Theme.TextPrimary, P, y, 260, 40));

        // Action buttons laid out left→right after the title, each sized to its label
        // so nothing overlaps and no text is clipped.
        var addSleep = PillButton(Loc.T("dash.addSleep"), Theme.SleepAccent);
        addSleep.Size = new Size(130, 32);
        addSleep.Location = new Point(P + 272, y + 4);
        addSleep.Click += (_, _) => _ = AddRecordAsync("sleep");
        AddAt(addSleep);

        var addEx = PillButton(Loc.T("dash.addExercise"), Theme.ExerciseAccent);
        addEx.Size = new Size(144, 32);
        addEx.Location = new Point(addSleep.Right + 10, y + 4);
        addEx.Click += (_, _) => _ = AddRecordAsync("exercise");
        AddAt(addEx);

        var refresh = PillButton(Loc.T("dash.refresh"), Theme.Primary);
        refresh.Size = new Size(104, 32);
        refresh.Location = new Point(addEx.Right + 10, y + 4);
        refresh.Click += async (_, _) => await _vm.LoadDataCommand.ExecuteAsync(null);
        AddAt(refresh);
        y += 60;

        // ── Welcome bar ───────────────────────────────────────────
        AddAt(LabelBold(Loc.T("dash.welcome", _vm.Username), 16f, Theme.TextPrimary, P, y, 600, 30));
        AddAt(Label(_vm.DateString, 10f, Theme.TextSecondary, P, y + 32, 600, 22));
        y += 70;

        // ── Error banner (hidden by default) ─────────────────────
        _errorLabel = Label("", 10f, Theme.Error, P, y, 800, 22);
        _errorLabel.Visible = false;
        AddAt(_errorLabel);
        y += 28;

        // ── Sleep section ─────────────────────────────────────────
        AddAt(LabelBold(Loc.T("dash.sleepWeek"), 14f, Theme.TextPrimary, P, y, 320, 28));
        y += 36;

        _sleepChartPanel = new Panel { Location = new Point(P, y), Size = new Size(800, 200), BackColor = Theme.Background, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        _sleepChartPanel.Paint += PaintSleep;
        AddAt(_sleepChartPanel);
        y += 210;

        _lSleepAvg = AddStatCard(P, y, Loc.T("dash.avg"), "--h", Theme.SleepAccent);
        _lSleepBest = AddStatCard(P + 210, y, Loc.T("dash.best"), "--h", Theme.TextPrimary);
        _lSleepMood = AddStatCard(P + 420, y, Loc.T("dash.mood"), "--/5", Theme.Warning);
        _lSleepVs = AddStatCard(P + 630, y, Loc.T("dash.vsLastWk"), "--", Theme.Primary);
        y += 86;

        // ── Exercise section ──────────────────────────────────────
        AddAt(LabelBold(Loc.T("dash.exWeek"), 14f, Theme.TextPrimary, P, y, 320, 28));
        y += 36;

        _exChartPanel = new Panel { Location = new Point(P, y), Size = new Size(800, 160), BackColor = Theme.Background, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        _exChartPanel.Paint += PaintExercise;
        AddAt(_exChartPanel);
        y += 170;

        _lExAvg = AddStatCard(P, y, Loc.T("dash.avgDay"), "--m", Theme.ExerciseAccent);
        _lExActive = AddStatCard(P + 210, y, Loc.T("dash.activeDays"), "--", Theme.Primary);
        _lExTotal = AddStatCard(P + 420, y, Loc.T("dash.total"), "--m", Theme.TextPrimary);
        y += 86;

        // ── Tip ───────────────────────────────────────────────────
        var tipCard = new Panel { Location = new Point(P, y), Size = new Size(820, 50), BackColor = Theme.SurfaceVariant, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        tipCard.Paint += (_, e) => { var r = new Rectangle(0, 0, tipCard.Width - 1, tipCard.Height - 1); using var path = Theme.RoundedRect(r, 14); using var b = new SolidBrush(Theme.SurfaceVariant); e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; e.Graphics.FillPath(b, path); };
        tipCard.Controls.Add(Label("💡", 16f, Theme.Warning, 16, 14, 30, 30));
        _lTipText = Label(_vm.LatestTip, 10f, Theme.TextSecondary, 52, 16, 640, 24);
        tipCard.Controls.Add(_lTipText);
        var viewAll = new LinkLabel { Text = Loc.T("dash.viewAll"), Font = Theme.Font(9f, FontStyle.Bold), LinkColor = Theme.Primary, AutoSize = true, Location = new Point(680, 16), Anchor = AnchorStyles.Top | AnchorStyles.Right };
        viewAll.Click += (_, _) => (FindForm() as MainForm)?.ShowAiInsights();
        tipCard.Controls.Add(viewAll);
        AddAt(tipCard);

        // ── Loading overlay (above content) ───────────────────────
        _loadingOverlay = new Panel { Dock = DockStyle.Fill, BackColor = Color.FromArgb(120, 0xF8, 0xFA, 0xFC), Visible = false };
        var spinner = new ProgressBar { Style = ProgressBarStyle.Marquee, Size = new Size(80, 6) };
        var loadingText = new Label { Text = "Loading…", Font = Theme.Font(10f), ForeColor = Theme.TextSecondary, AutoSize = true };
        _loadingOverlay.Controls.Add(spinner);
        _loadingOverlay.Controls.Add(loadingText);
        _loadingOverlay.Paint += (_, _) => { spinner.Location = new Point((_loadingOverlay.Width - 80) / 2, _loadingOverlay.Height / 2 - 4); loadingText.Location = new Point((_loadingOverlay.Width - loadingText.PreferredWidth) / 2, _loadingOverlay.Height / 2 + 10); };
        Controls.Add(_loadingOverlay);
        _loadingOverlay.BringToFront();
        _loadingOverlay.Visible = false;

        // ── Events ────────────────────────────────────────────────
        _vm.PropertyChanged += (_, e) => BeginInvoke(() => Refresh(e.PropertyName));
        _vm.DataRefreshed += () => BeginInvoke(() => { _sleepChartPanel.Invalidate(); _exChartPanel.Invalidate(); });
        this.Load += async (_, _) => { LayoutCharts(); await _vm.LoadDataCommand.ExecuteAsync(null); };
        this.Resize += (_, _) => LayoutCharts();
    }

    private T AddAt<T>(T c) where T : Control { Controls.Add(c); return c; }

    private void LayoutCharts()
    {
        int w = Math.Max(360, Width - P * 2);
        _sleepChartPanel.Width = w;
        _exChartPanel.Width = w;
        // keep the refresh button docked to the right
    }

    private Label AddStatCard(int x, int y, string label, string value, Color accent)
    {
        var p = new Panel { Location = new Point(x, y), Size = new Size(190, 70), BackColor = Theme.Surface, Anchor = AnchorStyles.Top | AnchorStyles.Left };
        p.Paint += (_, e) =>
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var r = new Rectangle(0, 0, p.Width - 1, p.Height - 1);
            using var path = Theme.RoundedRect(r, 12);
            using var b = new SolidBrush(Theme.Surface);
            e.Graphics.FillPath(b, path);
            using var bar = new SolidBrush(accent);
            e.Graphics.FillPath(bar, Theme.RoundedRect(new Rectangle(0, 8, 4, p.Height - 16), 2));
        };
        p.Controls.Add(Label(label, 9f, Theme.TextHint, 16, 10, 170, 16));
        var v = LabelBold(value, 18f, accent, 16, 30, 170, 30);
        p.Controls.Add(v);
        Controls.Add(p);
        return v;
    }

    private static Button PillButton(string text, Color bg)
    {
        var btn = new Button { Text = text, Font = Theme.Font(9f, FontStyle.Bold), BackColor = bg, ForeColor = Color.White, FlatStyle = FlatStyle.Flat, Cursor = Cursors.Hand, Size = new Size(110, 30) };
        btn.FlatAppearance.BorderSize = 0;
        btn.FlatAppearance.MouseOverBackColor = bg;
        btn.Paint += (sender, e) =>
        {
            if (sender is not Button b) return;
            var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(b.Parent?.BackColor ?? b.BackColor);
            using var path = Theme.RoundedRect(new Rectangle(0, 0, b.Width - 1, b.Height - 1), 14);
            using var brush = new SolidBrush(b.Enabled ? b.BackColor : Color.FromArgb(0xD1, 0xD5, 0xDB));
            g.FillPath(brush, path);
            TextRenderer.DrawText(g, b.Text, b.Font, new Rectangle(0, 0, b.Width, b.Height), b.ForeColor, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        };
        return btn;
    }

    private async Task AddRecordAsync(string type)
    {
        Form form = type == "sleep"
            ? new AddEditSleepForm(_recordApi, _session, null)
            : new AddEditExerciseForm(_recordApi, _session, null);
        form.ShowDialog(this);
        if (form.DialogResult == DialogResult.OK)
            await _vm.LoadDataCommand.ExecuteAsync(null);
        form.Dispose();
    }

    private void Refresh(string? p)
    {
        switch (p)
        {
            case nameof(_vm.IsLoading): _loadingOverlay.Visible = _vm.IsLoading; break;
            case nameof(_vm.HasError): _errorLabel.Visible = _vm.HasError; break;
            case nameof(_vm.ErrorMessage): _errorLabel.Text = _vm.ErrorMessage; break;
            case nameof(_vm.AvgSleepHours): _lSleepAvg.Text = $"{_vm.AvgSleepHours:F1}h"; break;
            case nameof(_vm.SleepTrend): _lSleepVs.Text = $"{(_vm.SleepTrend >= 0 ? "+" : "")}{_vm.SleepTrend:F1}h {_vm.SleepTrendDir}"; break;
            case nameof(_vm.SleepTrendDir): _lSleepVs.Text = $"{(_vm.SleepTrend >= 0 ? "+" : "")}{_vm.SleepTrend:F1}h {_vm.SleepTrendDir}"; break;
            case nameof(_vm.BestNight): _lSleepBest.Text = $"{_vm.BestNight:F1}h"; break;
            case nameof(_vm.MoodAvg): _lSleepMood.Text = $"{_vm.MoodAvg:F1}/5"; break;
            case nameof(_vm.AvgExerciseMinutes): _lExAvg.Text = $"{_vm.AvgExerciseMinutes:F0}m"; break;
            case nameof(_vm.ActiveDays): _lExActive.Text = $"{_vm.ActiveDays}d"; break;
            case nameof(_vm.TotalExerciseMinutes): _lExTotal.Text = $"{_vm.TotalExerciseMinutes}m"; break;
            case nameof(_vm.LatestTip): _lTipText.Text = _vm.LatestTip; break;
        }
    }

    private void PaintSleep(object? _, PaintEventArgs e)
    {
        var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        var w = _sleepChartPanel.Width; var h = _sleepChartPanel.Height;
        using (var path = Theme.RoundedRect(new Rectangle(0, 0, w - 1, h - 1), 14))
        using (var bg = new SolidBrush(Theme.Surface)) { g.FillPath(bg, path); }
        var data = _vm.SleepWeekData;
        if (!_vm.HasData) { DrawEmpty(g, w, h, Loc.T("dash.noSleep")); return; }
        var m = 44; var cw = w - m * 2; var ch = h - m - 14;
        var maxV = Math.Max(data.DefaultIfEmpty(1).Max(), 8);
        for (int i = 0; i <= 4; i++) { var gy = m + ch * i / 4f; using var pen = new Pen(Theme.ChartGridLine, 0.5f) { DashStyle = DashStyle.Dash }; g.DrawLine(pen, m, gy, w - m, gy); g.DrawString($"{(int)(12 - 3 * i)}h", Theme.Font(8f), new SolidBrush(Theme.TextHint), 6, gy - 7); }
        var pts = new PointF[data.Count];
        for (int i = 0; i < data.Count; i++) pts[i] = new PointF(m + cw * i / (data.Count - 1f), m + ch - (float)(data[i] / (maxV + 0.1) * ch));
        using var fillPath = new GraphicsPath(); fillPath.AddLines(pts); fillPath.AddLine(pts[^1].X, m + ch, pts[0].X, m + ch); fillPath.CloseFigure();
        using var fill = new LinearGradientBrush(new Point(0, 0), new Point(0, h), Color.FromArgb(90, Theme.ChartSparkline), Color.FromArgb(0, Theme.ChartSparkline)); g.FillPath(fill, fillPath);
        using var lp = new Pen(Theme.ChartSparkline, 3f); g.DrawLines(lp, pts);
        for (int i = 0; i < data.Count; i++)
        {
            g.FillEllipse(Brushes.White, pts[i].X - 4, pts[i].Y - 4, 8, 8);
            using var dot = new SolidBrush(Theme.ChartSparkline); g.FillEllipse(dot, pts[i].X - 2, pts[i].Y - 2, 4, 4);
            g.DrawString($"{data[i]:F1}", Theme.Font(8f, FontStyle.Bold), new SolidBrush(Theme.TextSecondary), pts[i].X - 12, pts[i].Y - 20);
            var lbl = _vm.DayLabels.Count > i ? _vm.DayLabels[i] : "";
            g.DrawString(lbl, Theme.Font(9f), new SolidBrush(Theme.TextHint), pts[i].X - 12, h - 18);
        }
    }

    private void PaintExercise(object? _, PaintEventArgs e)
    {
        var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        var w = _exChartPanel.Width; var h = _exChartPanel.Height;
        using (var path = Theme.RoundedRect(new Rectangle(0, 0, w - 1, h - 1), 14))
        using (var bg = new SolidBrush(Theme.Surface)) { g.FillPath(bg, path); }
        var data = _vm.ExerciseWeekData;
        if (!_vm.HasData) { DrawEmpty(g, w, h, Loc.T("dash.noEx")); return; }
        var m = 44; var cw = w - m * 2; var ch = h - m - 14;
        var maxV = Math.Max(data.DefaultIfEmpty(1).Max(), 60);
        var barGap = 12f; var barW = (cw - barGap * 6) / 7f;
        for (int i = 0; i <= 4; i++) { var gy = m + ch * i / 4f; using var pen = new Pen(Theme.ChartGridLine, 0.5f) { DashStyle = DashStyle.Dash }; g.DrawLine(pen, m, gy, w - m, gy); }
        for (int i = 0; i < data.Count; i++)
        {
            var bh = Math.Max(4f, data[i] / (float)maxV * ch);
            var x = m + i * (barW + barGap); var yb = m + ch - bh;
            if (data[i] > 0)
            { using var brush = new LinearGradientBrush(new PointF(x, yb), new PointF(x, yb + bh), Theme.ChartExerciseGradientTop, Theme.ChartExerciseGradientBottom); FillRoundedRect(g, brush, new RectangleF(x, yb, barW, bh), 8); }
            else { using var brush = new SolidBrush(Theme.SurfaceVariant); FillRoundedRect(g, brush, new RectangleF(x, yb, barW, 6), 4); }
            g.DrawString($"{data[i]}", Theme.Font(8f, FontStyle.Bold), new SolidBrush(Theme.TextSecondary), x + barW / 2 - 10, yb - 18);
            var lbl = _vm.DayLabels.Count > i ? _vm.DayLabels[i] : "";
            g.DrawString(lbl, Theme.Font(9f), new SolidBrush(Theme.TextHint), x + barW / 2 - 12, h - 18);
        }
    }

    private static void DrawEmpty(Graphics g, int w, int h, string msg)
    {
        using var f = Theme.Font(11f);
        using var b = new SolidBrush(Theme.TextHint);
        var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
        g.DrawString(msg, f, b, new RectangleF(0, 0, w, h), sf);
    }

    private static void FillRoundedRect(Graphics g, Brush brush, RectangleF rect, int radius)
    {
        using var path = Theme.RoundedRect(Rectangle.Round(rect), radius);
        g.FillPath(brush, path);
    }
}
