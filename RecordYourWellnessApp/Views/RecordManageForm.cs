// Author: Xia Zihang
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.Models;

namespace RecordYourWellnessApp.Views;

public class RecordManageForm : Form
{
    private readonly string _type;
    private readonly IRecordApi _api;
    private readonly Panel _listPanel;
    private readonly Label _errorLabel;

    public RecordManageForm(string type, IRecordApi api, ISessionService session)
    {
        _type = type; _api = api;
        this.Text = type == "sleep" ? "Manage Sleep Records" : "Manage Exercise Records";
        this.ClientSize = new Size(680, 580);
        this.StartPosition = FormStartPosition.CenterParent;
        this.BackColor = Theme.Background;
        this.AutoScaleMode = AutoScaleMode.Dpi;
        this.Font = Theme.Font(10f);

        Controls.Add(LabelBold(type == "sleep" ? "😴  Sleep Records" : "🏃  Exercise Records", 18f, Theme.TextPrimary, 24, 18, 400, 36));

        var addBtn = RoundedButton("+ Add New", Theme.Primary, Color.White, 14, 500, 20, 140, 36);
        addBtn.Click += async (_, _) => await AddNewAsync();
        Controls.Add(addBtn);

        _errorLabel = new Label { Location = new Point(24, 58), Size = new Size(620, 20), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false };
        Controls.Add(_errorLabel);

        _listPanel = new Panel { Location = new Point(24, 84), Size = new Size(632, 470), AutoScroll = true, BackColor = Theme.Background };
        Controls.Add(_listPanel);

        // "empty" placeholder
        Controls.Add(new Label { Name = "emptyHint", Text = "", Visible = false });

        this.Load += async (_, _) => await LoadRecords();
    }

    private record RecordItem(DateTime Date, long Id, string Detail, SleepRecord? Sleep, ExerciseRecord? Exercise);

    private async Task LoadRecords()
    {
        _listPanel.Controls.Clear();
        try
        {
            var data = await _api.GetRecords(0, 200);
            var items = new List<RecordItem>();
            foreach (var d in data.Content)
            {
                if (!DateTime.TryParse(d.RecordDate, out var date)) continue;
                if (_type == "sleep" && d.Sleep != null)
                    items.Add(new RecordItem(date, d.Sleep.Id, $"{d.Sleep.SleepHours:F1}h   {d.Sleep.SleepTime ?? "–"} → {d.Sleep.WakeTime ?? "–"}   Mood:{d.Sleep.MoodScore ?? 0}", d.Sleep, null));
                else if (_type == "exercise")
                    foreach (var ex in d.Exercises)
                        items.Add(new RecordItem(date, ex.Id, $"{ex.ExerciseActivity}   {ex.ExerciseDuration} min", null, ex));
            }
            items = items.OrderByDescending(x => x.Date).ToList();

            if (items.Count == 0)
            {
                _listPanel.Controls.Add(new Label { Text = "No records yet. Click \"+ Add New\" to create one.", ForeColor = Theme.TextHint, Font = Theme.Font(11f), Location = new Point(16, 16), AutoSize = true });
                return;
            }

            string? lastDate = null;
            int y = 6;
            foreach (var item in items)
            {
                var dateStr = item.Date.ToString("ddd, MMM d, yyyy", System.Globalization.CultureInfo.InvariantCulture);
                if (dateStr != lastDate)
                {
                    _listPanel.Controls.Add(LabelBold(dateStr, 10f, Theme.TextHint, 4, y, 600, 20));
                    y += 26; lastDate = dateStr;
                }
                var row = new Panel { Location = new Point(0, y), Size = new Size(624, 44), BackColor = Theme.Surface };
                row.Paint += (_, e) => { e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias; using var path = Theme.RoundedRect(new Rectangle(0, 0, row.Width - 1, row.Height - 1), 10); using var b = new SolidBrush(Theme.Surface); e.Graphics.FillPath(b, path); };
                row.Controls.Add(Label(item.Detail, 10f, Theme.TextPrimary, 14, 12, 420, 22));

                var edit = FlatButton("Edit", Theme.Primary, 450, 8, 70, 26);
                edit.Click += async (_, _) => await EditAsync(item);
                row.Controls.Add(edit);

                var del = FlatButton("Delete", Theme.Error, 530, 8, 80, 26);
                del.Click += async (_, _) => await DeleteAsync(item.Id);
                row.Controls.Add(del);

                _listPanel.Controls.Add(row);
                y += 50;
            }
        }
        catch (Refit.ApiException ex)
        {
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
                ShowError($"Could not load records ({ex.StatusCode}).");
        }
        catch (Exception ex) { ShowError($"Network error: {ex.Message}"); }
    }

    private async Task AddNewAsync()
    {
        using Form form = _type == "sleep"
            ? new AddEditSleepForm(_api, null, null)
            : new AddEditExerciseForm(_api, null, null);
        form.ShowDialog(this);
        if (form.DialogResult == DialogResult.OK && !IsDisposed) await LoadRecords();
    }

    private async Task EditAsync(RecordItem item)
    {
        // Pass the FULL record so the edit form pre-fills every field (the old code
        // sent only the Id, wiping duration/notes/times/mood on save).
        using Form form = _type == "sleep"
            ? new AddEditSleepForm(_api, null, item.Sleep, item.Date.ToString("yyyy-MM-dd"))
            : new AddEditExerciseForm(_api, null, item.Exercise, item.Date.ToString("yyyy-MM-dd"));
        form.ShowDialog(this);
        if (form.DialogResult == DialogResult.OK && !IsDisposed) await LoadRecords();
    }

    private async Task DeleteAsync(long id)
    {
        if (MessageBox.Show(this, "Delete this record? This cannot be undone.", "Confirm Delete",
            MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes) return;
        try
        {
            if (_type == "sleep") await _api.DeleteSleep(id);
            else await _api.DeleteExercise(id);
            if (!IsDisposed) await LoadRecords();
        }
        catch (Refit.ApiException ex)
        {
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
                ShowError($"Could not delete record ({ex.StatusCode}).");
        }
        catch (Exception ex) { ShowError($"Network error: {ex.Message}"); }
    }

    private void ShowError(string msg)
    {
        if (IsDisposed) return;
        _errorLabel.Text = msg; _errorLabel.Visible = true;
    }

    internal static Button FlatButton(string text, Color color, int x, int y, int w, int h) => new()
    {
        Text = text, Location = new Point(x, y), Size = new Size(w, h),
        Font = Theme.Font(9f, FontStyle.Bold), ForeColor = color,
        FlatStyle = FlatStyle.Flat, BackColor = Color.Transparent, Cursor = Cursors.Hand,
        FlatAppearance = { BorderSize = 0 }
    };
}
