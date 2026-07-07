// Author: Wang Songyu, Xia Zihang
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.Models;

namespace RecordYourWellnessApp.Views;

public class AddEditSleepForm : Form
{
    private readonly IRecordApi _api;
    private readonly long? _existingId;
    private readonly DateTimePicker _datePicker;
    private readonly TextBox _sleepTimeBox, _wakeTimeBox, _notesBox;
    private readonly NumericUpDown _moodPicker;
    private readonly Label _durationLabel, _errorLabel;
    private readonly Button _saveBtn;
    private bool _isEdit => _existingId != null;

    public AddEditSleepForm(IRecordApi api, ISessionService? session, SleepRecord? existing, string? existingDate = null)
    {
        _api = api; _existingId = existing?.Id;
        this.Text = _isEdit ? "Edit Sleep Record" : "Add Sleep Record";
        this.ClientSize = new Size(420, 480); this.StartPosition = FormStartPosition.CenterParent;
        this.BackColor = Theme.Background; this.FormBorderStyle = FormBorderStyle.FixedDialog; this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi; this.Font = Theme.Font(10f);
        this.MaximizeBox = false;

        var card = new Panel { Size = new Size(380, 440), Location = new Point(20, 20), BackColor = Theme.Surface };
        Controls.Add(card);
        card.Paint += (_, e) => { using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };

        card.Controls.Add(LabelBold(_isEdit ? "Edit Sleep" : "Add Sleep", 16f, Theme.TextPrimary, 24, 16, 320, 32));
        card.Controls.Add(LabelBold("Date", 9f, Theme.TextSecondary, 24, 56, 200, 20));
        _datePicker = new DateTimePicker { Location = new Point(24, 78), Size = new Size(332, 30), Format = DateTimePickerFormat.Short, MaxDate = DateTime.Today,
            Value = existingDate != null && DateTime.TryParse(existingDate, out var d) ? d : DateTime.Today }; card.Controls.Add(_datePicker);

        card.Controls.Add(LabelBold("Sleep Time", 9f, Theme.TextSecondary, 24, 114, 100, 20));
        _sleepTimeBox = new TextBox { Location = new Point(24, 136), Size = new Size(155, 36), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, PlaceholderText = "23:00" };
        _sleepTimeBox.Text = existing?.SleepTime ?? ""; card.Controls.Add(_sleepTimeBox);
        _sleepTimeBox.TextChanged += (_, _) => UpdateDur();

        card.Controls.Add(LabelBold("Wake Time", 9f, Theme.TextSecondary, 199, 114, 100, 20));
        _wakeTimeBox = new TextBox { Location = new Point(199, 136), Size = new Size(157, 36), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, PlaceholderText = "07:00" };
        _wakeTimeBox.Text = existing?.WakeTime ?? ""; card.Controls.Add(_wakeTimeBox);
        _wakeTimeBox.TextChanged += (_, _) => UpdateDur();

        _durationLabel = Label("Duration: --", 11f, Theme.TextPrimary, 24, 176, 300, 24); card.Controls.Add(_durationLabel); UpdateDur();

        card.Controls.Add(LabelBold("Mood (1-5, optional)", 9f, Theme.TextSecondary, 24, 210, 200, 20));
        _moodPicker = new NumericUpDown { Location = new Point(24, 232), Size = new Size(100, 30), Minimum = 0, Maximum = 5, Value = existing?.MoodScore ?? 0 }; card.Controls.Add(_moodPicker);

        card.Controls.Add(LabelBold("Notes", 9f, Theme.TextSecondary, 24, 270, 100, 20));
        _notesBox = new TextBox { Location = new Point(24, 292), Size = new Size(332, 60), Multiline = true, Text = existing?.Notes ?? "", Font = Theme.Font(10f), BorderStyle = BorderStyle.FixedSingle }; card.Controls.Add(_notesBox);

        _errorLabel = new Label { Location = new Point(24, 360), Size = new Size(332, 20), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false }; card.Controls.Add(_errorLabel);
        _saveBtn = RoundedButton(_isEdit ? "Update" : "Save", Theme.Primary, Color.White, 14, 24, 388, 332, 36);
        _saveBtn.Click += async (_, _) => await Save(); card.Controls.Add(_saveBtn);
    }

    private void UpdateDur()
    {
        if (TimeSpan.TryParse(_sleepTimeBox.Text, out var s) && TimeSpan.TryParse(_wakeTimeBox.Text, out var w))
        { var d = w > s ? w - s : w + TimeSpan.FromHours(24) - s; _durationLabel.Text = $"Duration: {d.TotalHours:F1} h"; }
        else _durationLabel.Text = "Duration: --";
    }

    private async Task Save()
    {
        if (string.IsNullOrWhiteSpace(_sleepTimeBox.Text) || string.IsNullOrWhiteSpace(_wakeTimeBox.Text))
        { ShowError("Enter both sleep and wake times (HH:mm)."); return; }
        if (!TimeSpan.TryParse(_sleepTimeBox.Text, out _) || !TimeSpan.TryParse(_wakeTimeBox.Text, out _))
        { ShowError("Times must be in HH:mm format."); return; }

        _saveBtn.Enabled = false;
        try
        {
            var sleep = TimeSpan.Parse(_sleepTimeBox.Text);
            var wake = TimeSpan.Parse(_wakeTimeBox.Text);
            var hours = (wake > sleep ? wake - sleep : wake + TimeSpan.FromHours(24) - sleep).TotalHours;

            var req = new SleepRecordRequest
            {
                RecordDate = _datePicker.Value.ToString("yyyy-MM-dd"),
                SleepTime = _sleepTimeBox.Text,
                WakeTime = _wakeTimeBox.Text,
                SleepHours = Math.Round(hours, 2), // sent for both create and update
                MoodScore = (int)_moodPicker.Value > 0 ? (int)_moodPicker.Value : null,
                Notes = string.IsNullOrWhiteSpace(_notesBox.Text) ? null : _notesBox.Text
            };
            if (_isEdit) await _api.UpdateSleep(_existingId!.Value, req);
            else await _api.CreateSleep(req);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Refit.ApiException ex)
        {
            ShowError(ex.StatusCode == System.Net.HttpStatusCode.Unauthorized ? "" :
                      ex.StatusCode == System.Net.HttpStatusCode.BadRequest ? "Please check the values you entered." :
                      $"Server error: {ex.StatusCode}");
        }
        catch (Exception ex) { ShowError($"Connection failed: {ex.Message}"); }
        finally { _saveBtn.Enabled = true; }
    }

    private void ShowError(string msg)
    {
        _errorLabel.Text = msg;
        _errorLabel.Visible = !string.IsNullOrEmpty(msg);
    }
}
