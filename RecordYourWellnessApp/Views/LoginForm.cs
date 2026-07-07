// Author: Liu Yu, Xia Zihang
using System.ComponentModel;
using Microsoft.Extensions.DependencyInjection;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.ViewModels;

namespace RecordYourWellnessApp.Views;

public class LoginForm : Form
{
    private readonly LoginViewModel _vm;
    private readonly INavigationService _nav;
    private readonly ISessionService _session;
    private readonly ISettingsService _settings;
    private readonly TextBox _usernameBox;
    private readonly TextBox _passwordBox;
    private readonly Button _loginButton;
    private readonly Label _errorLabel;
    private readonly ProgressBar _progressBar;

    public LoginForm(LoginViewModel vm, INavigationService nav, ISessionService session, ISettingsService settings)
    {
        _vm = vm; _nav = nav; _session = session; _settings = settings;
        this.Text = $"Login — {Constants.AppName}";
        this.ClientSize = new Size(480, 680);
        this.StartPosition = FormStartPosition.CenterScreen;
        this.FormBorderStyle = FormBorderStyle.FixedSingle;
        this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi;
        this.BackColor = Theme.Background;
        this.Font = Theme.Font(10f);

        int cardW = 420, cardX = (480 - cardW) / 2;
        int pad = 36; // horizontal padding inside card
        int cw = cardW - pad * 2; // content width

        var card = new Panel { Size = new Size(cardW, 570), Location = new Point(cardX, 50), BackColor = Theme.Surface };
        Controls.Add(card);
        card.Paint += (_, e) => { Theme.PaintCardGloss(e.Graphics, card.ClientRectangle); using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };

        // Title — dynamic y
        int ty = 30;
        var title = LabelBold("Welcome Back", 22f, Theme.TextPrimary, pad, ty, cw, 44);
        card.Controls.Add(title);
        ty += title.Height + 8;

        var sub = Label("Sign in to track your wellness journey", 10f, Theme.TextSecondary, pad, ty, cw, 24);
        card.Controls.Add(sub);
        ty += sub.Height + 28;

        // Username
        var userLabel = LabelBold("Username", 9f, Theme.TextSecondary, pad, ty, cw, 20);
        card.Controls.Add(userLabel);
        ty += userLabel.Height + 4;

        _usernameBox = new TextBox { Location = new Point(pad, ty), Size = new Size(cw, 36), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, PlaceholderText = "Enter your username" };
        StyleInput(_usernameBox);
        card.Controls.Add(_usernameBox);
        ty += 52;

        // Password
        var passLabel = LabelBold("Password", 9f, Theme.TextSecondary, pad, ty, cw, 20);
        card.Controls.Add(passLabel);
        ty += passLabel.Height + 4;

        _passwordBox = new TextBox { Location = new Point(pad, ty), Size = new Size(cw, 36), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, PlaceholderText = "Enter your password", UseSystemPasswordChar = true };
        StyleInput(_passwordBox);
        card.Controls.Add(_passwordBox);
        ty += 52;

        // Error
        _errorLabel = new Label { Location = new Point(pad, ty), Size = new Size(cw, 20), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false };
        card.Controls.Add(_errorLabel);
        ty += 24;

        // Progress
        _progressBar = new ProgressBar { Style = ProgressBarStyle.Marquee, Location = new Point(pad, ty), Size = new Size(cw, 4), Visible = false };
        card.Controls.Add(_progressBar);
        ty += 16;

        // Login button
        _loginButton = RoundedButton("Sign In", Theme.Primary, Color.White, 14, pad, ty, cw, 44);
        card.Controls.Add(_loginButton);
        ty += 54;

        // Google Sign-In
        var googleBtn = new Button { Text = " G   Sign in with Google", Font = Theme.Font(10f, FontStyle.Bold), Location = new Point(pad, ty), Size = new Size(cw, 44), FlatStyle = FlatStyle.Flat, BackColor = Color.White, ForeColor = Theme.TextPrimary, Cursor = Cursors.Hand };
        googleBtn.FlatAppearance.BorderColor = Theme.Outline; googleBtn.FlatAppearance.BorderSize = 1;
        googleBtn.Click += async (_, _) => await GoogleSignIn();
        card.Controls.Add(googleBtn);
        ty += 54;

        // Register link
        var registerLink = new LinkLabel { Text = "Don't have an account? Create one", Location = new Point(pad, ty), Size = new Size(cw, 24), TextAlign = ContentAlignment.MiddleCenter, Font = Theme.Font(9f), LinkColor = Theme.Primary, ActiveLinkColor = Theme.PrimaryDark };
        card.Controls.Add(registerLink);

        // ── Bind ────────────────────────────────────────────────────
        _usernameBox.DataBindings.Add("Text", _vm, nameof(_vm.Username), false, DataSourceUpdateMode.OnPropertyChanged);
        _passwordBox.DataBindings.Add("Text", _vm, nameof(_vm.Password), false, DataSourceUpdateMode.OnPropertyChanged);
        _vm.PropertyChanged += OnVMChanged;
        _vm.LoginSucceeded += () => BeginInvoke(() => _nav.GoToMain());
        _loginButton.Click += (_, _) => _vm.LoginCommand.Execute(null);
        registerLink.Click += (_, _) => ShowRegister();
        _passwordBox.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; _vm.LoginCommand.Execute(null); } };
        _usernameBox.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; _passwordBox.Focus(); } };
    }

    private static void StyleInput(TextBox box)
    {
        box.AutoSize = false;
        box.TextChanged += (_, _) => box.BackColor = string.IsNullOrEmpty(box.Text) ? Theme.InputGradientBottom : Color.White;
    }

    protected override void OnLoad(EventArgs e)
    {
        base.OnLoad(e);
        _usernameBox.Focus();
    }

    private void ShowRegister()
    {
        var f = Program.ServiceProvider!.GetRequiredService<RegisterForm>();
        f.ShowDialog(this);
        // After register, focus username with the chosen name pre-filled (matches Android:
        // registration returns to the login screen, does not auto-login).
        if (!string.IsNullOrWhiteSpace(f.RegisteredUsername))
        {
            _usernameBox.Text = f.RegisteredUsername;
            _passwordBox.Focus();
        }
    }

    private async Task GoogleSignIn()
    {
        _loginButton.Enabled = false;
        Models.LoginResponse? result = null;
        try
        {
            using var googleForm = new GoogleAuthForm(Program.ServiceProvider!.GetRequiredService<Services.IAuthApi>());
            googleForm.ShowDialog(this);
            result = await googleForm.WaitForResultAsync();
        }
        catch (Exception ex)
        {
            _errorLabel.Text = $"Google sign-in failed: {ex.Message}";
            _errorLabel.Visible = true;
        }
        if (result != null)
        {
            _session.Login(result); // AppContext listens to session changes for navigation
            _nav.GoToMain();        // disposes this form — do not touch UI afterwards
            return;
        }
        _loginButton.Enabled = true;
    }

    private void OnVMChanged(object? s, PropertyChangedEventArgs e)
    {
        if (InvokeRequired) { BeginInvoke(() => OnVMChanged(s, e)); return; }
        if (e.PropertyName == nameof(_vm.IsBusy)) { _loginButton.Enabled = !_vm.IsBusy; _progressBar.Visible = _vm.IsBusy; }
        if (e.PropertyName == nameof(_vm.ErrorMessage)) { _errorLabel.Text = _vm.ErrorMessage; _errorLabel.Visible = !string.IsNullOrEmpty(_vm.ErrorMessage); }
    }

    // ── Shared control factories (used by every view via `using static`) ────────────
    internal static Label Label(string text, float size, Color color, int x, int y, int w, int h)
        => MakeLabel(text, size, FontStyle.Regular, color, x, y, w, h);

    internal static Label LabelBold(string text, float size, Color color, int x, int y, int w, int h)
        => MakeLabel(text, size, FontStyle.Bold, color, x, y, w, h);

    private static Label MakeLabel(string text, float size, FontStyle style, Color color, int x, int y, int w, int h)
    {
        using var f = Theme.Font(size, style);
        var realH = TextRenderer.MeasureText("AyÁj", f).Height + 2;
        var actualH = Math.Max(h, realH);
        // Multi-line: caller gave enough height (>1.5x single line) to allow wrapping
        var canWrap = h > realH * 1.5f;
        // Measure actual text width to decide ellipsis
        var textW = TextRenderer.MeasureText(text, f).Width;

        return new()
        {
            Text = text,
            Font = Theme.Font(size, style),
            ForeColor = color,
            Location = new Point(x, y),
            AutoSize = canWrap,
            MaximumSize = canWrap ? new Size(w, 0) : Size.Empty,
            AutoEllipsis = !canWrap && textW > w, // safety net; callers should size wide enough
            Size = new Size(w, actualH),
            TextAlign = canWrap ? ContentAlignment.TopLeft : ContentAlignment.MiddleLeft,
            UseCompatibleTextRendering = false, // match TextRenderer measurement (no clipping)
        };
    }

    internal static TextBox InputBox(int x, int y, string placeholder) => new()
    {
        Location = new Point(x, y),
        Size = new Size(300, 36),
        Font = Theme.Font(11f),
        BorderStyle = BorderStyle.FixedSingle,
        ForeColor = Theme.TextPrimary,
        BackColor = Color.White,
        PlaceholderText = placeholder
    };

    internal static Button RoundedButton(string text, Color bg, Color fg, int radius, int x, int y, int w, int h)
    {
        var btn = new Button
        {
            Text = text,
            Location = new Point(x, y),
            Size = new Size(w, h),
            Font = Theme.Font(11f, FontStyle.Bold),
            BackColor = bg,
            ForeColor = fg,
            FlatStyle = FlatStyle.Flat,
            Cursor = Cursors.Hand
        };
        btn.FlatAppearance.BorderSize = 0;
        btn.FlatAppearance.MouseOverBackColor = bg;   // suppress the default gray hover square
        btn.FlatAppearance.MouseDownBackColor = bg;
        btn.Paint += (sender, e) =>
        {
            if (sender is not Button b) return;
            var g = e.Graphics; g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            // Paint the corners with the parent's colour so the button reads as a rounded
            // pill instead of an accent-coloured rectangle (WinForms fills the full rect first).
            g.Clear(b.Parent?.BackColor ?? b.BackColor);
            var rect = new Rectangle(0, 0, b.Width - 1, b.Height - 1);
            using var path = Theme.RoundedRect(rect, radius);
            using var brush = new SolidBrush(b.Enabled ? b.BackColor : Color.FromArgb(0xD1, 0xD5, 0xDB));
            g.FillPath(brush, path);
            // subtle top highlight
            using var gloss = new System.Drawing.Drawing2D.LinearGradientBrush(
                new Point(0, 0), new Point(0, b.Height), Color.FromArgb(40, 255, 255, 255), Color.FromArgb(0, 255, 255, 255));
            g.FillPath(gloss, path);
            TextRenderer.DrawText(g, b.Text, b.Font, rect, b.ForeColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding);
        };
        return btn;
    }
}
