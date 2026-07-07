// Author: Liu Yu, Xia Zihang
using System.ComponentModel;
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.ViewModels;

namespace RecordYourWellnessApp.Views;

public class RegisterForm : Form
{
    private readonly RegisterViewModel _vm;
    private readonly TextBox _usernameBox, _emailBox, _passwordBox;
    private readonly Button _registerButton;
    private readonly Label _errorLabel, _successLabel;
    private readonly ProgressBar _progressBar;

    /// <summary>Set on success so the login screen can pre-fill the username
    /// (Android returns to the login screen after registration, it does not auto-login).</summary>
    public string? RegisteredUsername { get; private set; }

    public RegisterForm(RegisterViewModel vm)
    {
        _vm = vm;
        this.Text = $"Create Account — {Constants.AppName}";
        this.ClientSize = new Size(480, 580);
        this.StartPosition = FormStartPosition.CenterParent;
        this.FormBorderStyle = FormBorderStyle.FixedSingle;
        this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi;
        this.BackColor = Theme.Background;
        this.Font = Theme.Font(10f);

        int cardW = 420, cx = (480 - cardW) / 2, pad = 36, cw = cardW - pad * 2;
        var card = new Panel { Size = new Size(cardW, 500), Location = new Point(cx, 40), BackColor = Theme.Surface };
        Controls.Add(card);
        card.Paint += (_, e) => { Theme.PaintCardGloss(e.Graphics, card.ClientRectangle); using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };

        int ty = 30;
        var title = LabelBold("Create Account", 22f, Theme.TextPrimary, pad, ty, cw, 44); card.Controls.Add(title);
        ty += title.Height + 24;

        string[][] fields = [["Username", "Choose a username"], ["Email (optional)", "you@example.com"], ["Password", "At least 8 characters"]];
        foreach (var fld in fields)
        {
            var lbl = LabelBold(fld[0], 9f, Theme.TextSecondary, pad, ty, cw, 20); card.Controls.Add(lbl);
            ty += 24;
            var box = new TextBox { Location = new Point(pad, ty), Size = new Size(cw, 36),
                Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, PlaceholderText = fld[1] };
            card.Controls.Add(box);
            if (fld[0] == "Username") _usernameBox = box;
            else if (fld[0] == "Email (optional)") _emailBox = box;
            else _passwordBox = box;
            ty += 50;
        }
        _passwordBox.UseSystemPasswordChar = true;

        card.Controls.Add(Label("At least 8 chars, one letter and one digit", 8f, Theme.TextHint, pad, ty, cw, 16));
        ty += 20;

        _errorLabel = new Label { Location = new Point(pad, ty), Size = new Size(cw, 20), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false }; card.Controls.Add(_errorLabel);
        _successLabel = new Label { Location = new Point(pad, ty), Size = new Size(cw, 20), ForeColor = Theme.Success, Visible = false, UseCompatibleTextRendering = false }; card.Controls.Add(_successLabel);
        ty += 24;
        _progressBar = new ProgressBar { Style = ProgressBarStyle.Marquee, Location = new Point(pad, ty), Size = new Size(cw, 4), Visible = false }; card.Controls.Add(_progressBar);
        ty += 16;

        _registerButton = RoundedButton("Create Account", Theme.Primary, Color.White, 14, pad, ty, cw, 44); card.Controls.Add(_registerButton);
        ty += 54;

        var back = new LinkLabel { Text = "Already have an account? Sign in", Location = new Point(pad, ty), Size = new Size(cw, 24),
            TextAlign = ContentAlignment.MiddleCenter, Font = Theme.Font(9f), LinkColor = Theme.Primary }; card.Controls.Add(back);

        _usernameBox.DataBindings.Add("Text", _vm, nameof(_vm.Username), false, DataSourceUpdateMode.OnPropertyChanged);
        _emailBox.DataBindings.Add("Text", _vm, nameof(_vm.Email), false, DataSourceUpdateMode.OnPropertyChanged);
        _passwordBox.DataBindings.Add("Text", _vm, nameof(_vm.Password), false, DataSourceUpdateMode.OnPropertyChanged);
        _vm.PropertyChanged += OnVMChanged;
        _vm.RegistrationSucceeded += () => BeginInvoke(() =>
        {
            RegisteredUsername = _vm.Username;
            MessageBox.Show(this, "Registration successful! You can now sign in.", "Success",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            Close();
        });
        _vm.GoBackRequested += () => BeginInvoke(() => Close());
        _registerButton.Click += (_, _) => _vm.RegisterCommand.Execute(null);
        _passwordBox.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; _vm.RegisterCommand.Execute(null); } };
        back.Click += (_, _) => Close();
    }

    private void OnVMChanged(object? s, PropertyChangedEventArgs e)
    {
        if (InvokeRequired) { BeginInvoke(() => OnVMChanged(s, e)); return; }
        if (e.PropertyName == nameof(_vm.IsBusy)) { _registerButton.Enabled = !_vm.IsBusy; _progressBar.Visible = _vm.IsBusy; }
        if (e.PropertyName == nameof(_vm.ErrorMessage)) { _errorLabel.Text = _vm.ErrorMessage; _errorLabel.Visible = !string.IsNullOrEmpty(_vm.ErrorMessage); _successLabel.Visible = false; }
        if (e.PropertyName == nameof(_vm.SuccessMessage)) { _successLabel.Text = _vm.SuccessMessage; _successLabel.Visible = true; _errorLabel.Visible = false; }
    }
}
