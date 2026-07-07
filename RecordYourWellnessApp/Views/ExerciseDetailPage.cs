// Author: Wang Songyu, Xia Zihang
using System.Drawing.Drawing2D;
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.ViewModels;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.Views;

public class ExerciseDetailPage : UserControl
{
    private readonly ExerciseDetailViewModel _vm;
    private readonly IRecordApi _recordApi;
    private readonly ISessionService _session;
    private readonly Label _avgVal, _activeVal, _weekLabel, _errorLabel;
    private readonly Panel _chartPanel, _piePanel, _loadingOverlay;
    private readonly Button _addBtn, _manageBtn, _nextBtn;
    private List<int> _data = new();

    private const int P = 32;
    private static DateTime CurrentMonday => DateTime.Today.AddDays(-(int)DateTime.Today.DayOfWeek + 1);

    private static readonly Color[] PieColors =
    {
        Color.FromArgb(0x34, 0xC7, 0x59), Color.FromArgb(0x45, 0xB5, 0xD6), Color.FromArgb(0xF5, 0x9E, 0x0B),
        Color.FromArgb(0x64, 0xE0, 0xB5), Color.FromArgb(0xA8, 0x55, 0xF7), Color.FromArgb(0xEF, 0x44, 0x44),
        Color.FromArgb(0x38, 0xBD, 0xF8), Color.FromArgb(0x94, 0xA3, 0xB8),
    };

    public ExerciseDetailPage(ExerciseDetailViewModel vm, IRecordApi recordApi, ISessionService session)
    {
        _vm = vm; _recordApi = recordApi; _session = session;
        this.Dock = DockStyle.Fill; this.BackColor = Theme.Background; this.AutoScroll = true;
        this.Font = Theme.Font(10f);

        // ── Row 1: title (left) + Add / Manage (right) ────────────────
        Controls.Add(LabelBold(Loc.T("ex.title"), 24f, Theme.TextPrimary, P, 14, 240, 40));

        _addBtn = RoundedButton(Loc.T("common.add"), Theme.ExerciseAccent, Color.White, 14, 0, 16, 100, 38);
        _addBtn.Anchor = AnchorStyles.Top | AnchorStyles.Right; _addBtn.Click += (_, _) => _ = AddOrRefreshAsync(); Controls.Add(_addBtn);
        _manageBtn = RoundedButton(Loc.T("common.manage"), Theme.Primary, Color.White, 14, 0, 16, 110, 38);
        _manageBtn.Anchor = AnchorStyles.Top | AnchorStyles.Right; _manageBtn.Click += (_, _) => OpenManage(); Controls.Add(_manageBtn);

        // ── Row 2: week navigation (< week-range >) ───────────────────
        var prev = NavBtn("<", P, 58); prev.Click += (_, _) => _vm.PrevWeekCommand.Execute(null); Controls.Add(prev);
        _weekLabel = Label(_vm.WeekRangeText, 13f, Theme.TextPrimary, P + 48, 62, 250, 26);
        _weekLabel.TextAlign = ContentAlignment.MiddleCenter; _weekLabel.UseCompatibleTextRendering = false; Controls.Add(_weekLabel);
        _nextBtn = NavBtn(">", P + 306, 58); _nextBtn.Click += (_, _) => _vm.NextWeekCommand.Execute(null); Controls.Add(_nextBtn);
        UpdateWeekNav();

        _errorLabel = Label("", 10f, Theme.Error, P, 100, 780, 22); _errorLabel.Visible = false; Controls.Add(_errorLabel);

        _chartPanel = new Panel { Location = new Point(P, 124), Size = new Size(800, 280), BackColor = Theme.Background, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        _chartPanel.Paint += PaintChart; Controls.Add(_chartPanel);

        var statsY = 420;
        _avgVal = AddStatCard(P, statsY, Loc.T("ex.avgActive"), "-- min", Theme.ExerciseAccent);
        _activeVal = AddStatCard(P + 220, statsY, Loc.T("ex.activeDays"), "--", Theme.Primary);

        _piePanel = new Panel { Location = new Point(P + 440, statsY), Size = new Size(360, 160), BackColor = Theme.Surface, Anchor = AnchorStyles.Top | AnchorStyles.Left };
        _piePanel.Paint += PaintPie; Controls.Add(_piePanel);

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
        _manageBtn.Location = new Point(Width - P - _manageBtn.Width, 16);
        _addBtn.Location = new Point(_manageBtn.Left - 10 - _addBtn.Width, 16);
    }

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
        using var form = new AddEditExerciseForm(_recordApi, _session, null);
        form.ShowDialog(this);
        if (form.DialogResult == DialogResult.OK)
            await _vm.LoadCommand.ExecuteAsync(null);
    }

    private void OpenManage()
    {
        using var f = new RecordManageForm("exercise", _recordApi, _session);
        f.ShowDialog(this);
        _ = _vm.LoadCommand.ExecuteAsync(null);
    }

    private void PaintChart(object? _, PaintEventArgs e)
    {
        var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        var w = _chartPanel.Width; var h = _chartPanel.Height;
        using (var path = Theme.RoundedRect(new Rectangle(0, 0, w - 1, h - 1), 14))
        using (var bg = new SolidBrush(Theme.Surface)) { g.FillPath(bg, path); }
        if (!_vm.HasData) { DrawEmpty(g, w, h, Loc.T("ex.noWeek")); return; }
        var margin = 44; var barW = Math.Min(60, (w - margin * 2) / 7 - 8); var maxH = h - 70;
        var maxVal = Math.Max(_data.DefaultIfEmpty(1).Max(), 30);

        for (int i = 0; i <= 4; i++)
        {
            var gy = margin + maxH * i / 4f;
            using var pen = new Pen(Theme.ChartGridLine, 0.5f) { DashStyle = DashStyle.Dash };
            g.DrawLine(pen, margin, gy, w - margin, gy);
            g.DrawString($"{(int)(maxVal - maxVal * i / 4)}m", Theme.Font(8f), new SolidBrush(Theme.TextHint), 4, gy - 8);
        }
        var targetY = margin + maxH * (1 - 30f / maxVal);
        using var tp = new Pen(Theme.ChartTargetLine, 1f) { DashStyle = DashStyle.Dash };
        g.DrawLine(tp, margin, targetY, w - margin, targetY);
        g.DrawString(Loc.T("ex.target"), Theme.Font(8f, FontStyle.Bold), new SolidBrush(Theme.ChartTargetLine), w - 82, targetY - 16);

        for (int i = 0; i < _data.Count; i++)
        {
            var bh = Math.Max(2f, (float)(_data[i] / (float)maxVal * maxH));
            var x = margin + (w - margin * 2f) / 7f * i + ((w - margin * 2f) / 7f - barW) / 2f;
            var y = margin + maxH - bh;
            var rect = new RectangleF(x, y, barW, bh);
            if (_data[i] > 0)
            { using var brush = new LinearGradientBrush(new PointF(x, y), new PointF(x, y + bh), Theme.ChartExerciseGradientTop, Theme.ChartExerciseGradientBottom); g.FillRoundedRectangle(brush, rect, 12); }
            else { using var brush = new SolidBrush(Theme.SurfaceVariant); g.FillRoundedRectangle(brush, new RectangleF(x, y, barW, 6), 4); }
            if (_data[i] > 0) g.DrawString($"{_data[i]}", Theme.Font(9f, FontStyle.Bold), new SolidBrush(Theme.TextSecondary), x, y - 18);
            var label = _vm.WeekLabels.Count > i ? _vm.WeekLabels[i] : "";
            g.DrawString(label, Theme.Font(9f), new SolidBrush(Theme.TextHint), x + barW / 2 - 14, h - 22);
        }
    }

    private void PaintPie(object? _, PaintEventArgs e)
    {
        var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        var r = _piePanel.ClientRectangle;
        g.DrawString(Loc.T("ex.breakdown"), Theme.Font(11f, FontStyle.Bold), new SolidBrush(Theme.TextPrimary), 12, 8);

        var breakdown = _vm.ActivityBreakdown;
        if (breakdown == null || breakdown.Count == 0)
        {
            using var f = Theme.Font(10f); using var b = new SolidBrush(Theme.TextHint);
            var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            g.DrawString(Loc.T("ex.noActivities"), f, b, new RectangleF(0, 28, r.Width, r.Height - 28), sf);
            return;
        }

        int cx = 70, cy = r.Height / 2 + 8, cr = Math.Min(54, (r.Height - 36) / 2);
        float total = breakdown.Values.Sum();
        float start = -90; int ci = 0; int ly = 30;
        var font = Theme.Font(9f);
        foreach (var kv in breakdown.OrderByDescending(x => x.Value))
        {
            var sweep = kv.Value / total * 360f;
            using var brush = new SolidBrush(PieColors[ci % PieColors.Length]);
            g.FillPie(brush, cx - cr, cy - cr, cr * 2, cr * 2, start, sweep);
            g.FillRectangle(brush, cx + cr + 14, ly, 10, 10);
            g.DrawString($"{kv.Key}: {kv.Value}m ({kv.Value / total * 100:F0}%)", font, new SolidBrush(Theme.TextSecondary), cx + cr + 30, ly - 2);
            start += sweep; ly += 18; ci++;
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
            case nameof(_vm.AvgMinutes): _avgVal.Text = $"{_vm.AvgMinutes:F0} min"; break;
            case nameof(_vm.ActiveDays): _activeVal.Text = $"{_vm.ActiveDays}"; break;
            case nameof(_vm.WeekData): _data = _vm.WeekData; _chartPanel.Invalidate(); break;
            case nameof(_vm.ActivityBreakdown): _piePanel.Invalidate(); break;
        }
    }

    private static void DrawEmpty(Graphics g, int w, int h, string msg)
    {
        using var f = Theme.Font(11f); using var b = new SolidBrush(Theme.TextHint);
        var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
        g.DrawString(msg, f, b, new RectangleF(0, 0, w, h), sf);
    }
}
