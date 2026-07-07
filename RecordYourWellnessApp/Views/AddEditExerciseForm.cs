using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.Models;

namespace RecordYourWellnessApp.Views;

public class AddEditExerciseForm : Form
{
    private readonly IRecordApi _api;
    private readonly long? _existingId;
    private readonly DateTimePicker _datePicker;
    private readonly ComboBox _typeCombo;
    private readonly NumericUpDown _durationPicker;
    private readonly TextBox _notesBox;
    private readonly Label _errorLabel;
    private readonly Button _saveBtn;
    private bool _isEdit => _existingId != null;

    private static readonly string[] Types = ["Running","Walking","Cycling","Swimming","Yoga","Weight Training","HIIT","Dancing","Basketball","Badminton","Other"];

    public AddEditExerciseForm(IRecordApi api, ISessionService? session, ExerciseRecord? existing, string? existingDate = null)
    {
        _api = api; _existingId = existing?.Id;
        this.Text = _isEdit ? "Edit Exercise" : "Add Exercise";
        this.ClientSize = new Size(420, 440); this.StartPosition = FormStartPosition.CenterParent; this.BackColor = Theme.Background;
        this.FormBorderStyle = FormBorderStyle.FixedDialog; this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi; this.Font = Theme.Font(10f);

        var card = new Panel { Size = new Size(380, 400), Location = new Point(20, 20), BackColor = Theme.Surface }; Controls.Add(card);
        card.Paint += (_, e) => { using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };

        card.Controls.Add(LabelBold(_isEdit ? "Edit Exercise" : "Add Exercise", 16f, Theme.TextPrimary, 24, 16, 320, 32));
        card.Controls.Add(LabelBold("Date", 9f, Theme.TextSecondary, 24, 56, 200, 20));
        _datePicker = new DateTimePicker { Location = new Point(24, 78), Size = new Size(332, 30), Format = DateTimePickerFormat.Short, MaxDate = DateTime.Today,
            Value = existingDate != null && DateTime.TryParse(existingDate, out var d) ? d : DateTime.Today }; card.Controls.Add(_datePicker);

        card.Controls.Add(LabelBold("Activity", 9f, Theme.TextSecondary, 24, 114, 200, 20));
        _typeCombo = new ComboBox { Location = new Point(24, 136), Size = new Size(332, 30), DropDownStyle = ComboBoxStyle.DropDownList, Font = Theme.Font(11f) };
        _typeCombo.Items.AddRange(Types);
        var idx = _existingId != null && !string.IsNullOrEmpty(existing?.ExerciseActivity) ? Math.Max(0, Array.IndexOf(Types, existing.ExerciseActivity)) : 0;
        _typeCombo.SelectedIndex = idx; card.Controls.Add(_typeCombo);

        card.Controls.Add(LabelBold("Duration (minutes)", 9f, Theme.TextSecondary, 24, 172, 200, 20));
        _durationPicker = new NumericUpDown { Location = new Point(24, 194), Size = new Size(120, 30), Minimum = 1, Maximum = 1440, Value = existing?.ExerciseDuration ?? 30 }; card.Controls.Add(_durationPicker);

        card.Controls.Add(LabelBold("Notes", 9f, Theme.TextSecondary, 24, 234, 200, 20));
        _notesBox = new TextBox { Location = new Point(24, 256), Size = new Size(332, 50), Multiline = true, Text = existing?.Notes ?? "", Font = Theme.Font(10f), BorderStyle = BorderStyle.FixedSingle }; card.Controls.Add(_notesBox);

        _errorLabel = new Label { Location = new Point(24, 314), Size = new Size(332, 20), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false }; card.Controls.Add(_errorLabel);
        _saveBtn = RoundedButton(_isEdit ? "Update" : "Save", Theme.Primary, Color.White, 14, 24, 344, 332, 36);
        _saveBtn.Click += async (_, _) => await Save(); card.Controls.Add(_saveBtn);
    }

    private async Task Save()
    {
        _saveBtn.Enabled = false;
        try
        {
            var req = new ExerciseRecordRequest
            {
                RecordDate = _datePicker.Value.ToString("yyyy-MM-dd"),
                ExerciseActivity = _typeCombo.SelectedItem?.ToString() ?? "Other",
                ExerciseDuration = (int)_durationPicker.Value,
                Notes = string.IsNullOrWhiteSpace(_notesBox.Text) ? null : _notesBox.Text
            };
            if (_isEdit) await _api.UpdateExercise(_existingId!.Value, req);
            else await _api.CreateExercise(req);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Refit.ApiException ex)
        {
            _errorLabel.Text = ex.StatusCode == System.Net.HttpStatusCode.Unauthorized ? "" :
                               ex.StatusCode == System.Net.HttpStatusCode.BadRequest ? "Please check the values you entered." :
                               $"Server error: {ex.StatusCode}";
            _errorLabel.Visible = !string.IsNullOrEmpty(_errorLabel.Text);
        }
        catch (Exception ex) { _errorLabel.Text = $"Connection failed: {ex.Message}"; _errorLabel.Visible = true; }
        finally { _saveBtn.Enabled = true; }
    }
}
