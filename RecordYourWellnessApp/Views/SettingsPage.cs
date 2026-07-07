// Author: Xia Zihang
using Microsoft.Extensions.DependencyInjection;
using static RecordYourWellnessApp.Views.LoginForm;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.Views;

public class SettingsPage : Form
{
    private readonly ISessionService _session;
    private readonly ISettingsService _settings;
    private readonly IServiceProvider _sp;

    /// <summary>Set when the user confirmed logout or account deletion. The host
    /// (MainForm) inspects this after ShowDialog returns and tears down the session.</summary>
    public bool LogoutRequested { get; private set; }

    /// <summary>Set when the user switched language, so the host can recreate the shell
    /// to re-render every view in the new language.</summary>
    public bool LanguageChanged { get; private set; }

    public SettingsPage(ISessionService session, ISettingsService settings, IServiceProvider sp)
    {
        _session = session; _settings = settings; _sp = sp;
        this.Text = "Settings";
        this.ClientSize = new Size(440, 520);
        this.StartPosition = FormStartPosition.CenterParent;
        this.BackColor = Theme.Background;
        this.FormBorderStyle = FormBorderStyle.FixedDialog;
        this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi;
        this.Font = Theme.Font(10f);

        var card = new Panel { Size = new Size(400, 480), Location = new Point(20, 20), BackColor = Theme.Surface };
        Controls.Add(card);
        card.Paint += (_, e) => { using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };

        card.Controls.Add(LabelBold(Loc.T("settings.title"), 22f, Theme.TextPrimary, 24, 20, 300, 36));

        int y = 76;
        AddRow(card, "👤", Loc.T("settings.profile"), Loc.T("settings.profileSub"), y, () => OpenProfile()); y += 56;
        AddRow(card, "🔒", Loc.T("settings.password"), Loc.T("settings.passwordSub"), y, () => OpenChangePassword()); y += 56;

        // Language — switching recreates the shell so all views re-render in the new language.
        var langCombo = new ComboBox { Location = new Point(266, y + 6), Size = new Size(110, 28), DropDownStyle = ComboBoxStyle.DropDownList, Font = Theme.Font(10f) };
        langCombo.Items.AddRange(["English", "中文"]);
        langCombo.SelectedIndex = _settings.Language == "zh" ? 1 : 0;
        langCombo.SelectedIndexChanged += (_, _) =>
        {
            var lang = langCombo.SelectedIndex == 1 ? "zh" : "en";
            if (lang == _settings.Language) return;
            _settings.Language = lang; _settings.Save();
            Loc.Set(lang);
            LanguageChanged = true; // host recreates MainForm on close
        };
        card.Controls.Add(LabelBold(Loc.T("settings.language"), 11f, Theme.TextPrimary, 24, y + 8, 200, 24));
        card.Controls.Add(Label(Loc.T("settings.languageSub"), 9f, Theme.TextHint, 24, y + 30, 230, 16));
        card.Controls.Add(langCombo);
        y += 68;

        var logoutBtn = RoundedButton(Loc.T("settings.logout"), Theme.Error, Color.White, 14, 24, y, 352, 40);
        logoutBtn.Click += (_, _) =>
        {
            if (MessageBox.Show(this, "Are you sure you want to log out?", "Logout",
                MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
            { LogoutRequested = true; Close(); }
        };
        card.Controls.Add(logoutBtn);
        y += 50;

        var deleteBtn = RoundedButton(Loc.T("settings.delete"), Color.FromArgb(0xDC, 0x26, 0x26), Color.White, 14, 24, y, 352, 40);
        deleteBtn.Click += async (_, _) => await DeleteAccountAsync();
        card.Controls.Add(deleteBtn);
    }

    private void AddRow(Panel p, string icon, string title, string sub, int y, Action action)
    {
        var row = new Panel { Location = new Point(12, y), Size = new Size(376, 48), Cursor = Cursors.Hand, BackColor = Color.Transparent };
        row.Paint += (_, e) =>
        {
            var r = new Rectangle(0, 0, row.Width - 1, row.Height - 1);
            using var path = Theme.RoundedRect(r, 10);
            using var hover = row.BackColor == Theme.SurfaceVariant ? new SolidBrush(Theme.SurfaceVariant) : new SolidBrush(Color.FromArgb(8, 0, 0, 0));
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            e.Graphics.FillPath(hover, path);
        };
        row.MouseEnter += (_, _) => { row.BackColor = Theme.SurfaceVariant; row.Invalidate(); };
        row.MouseLeave += (_, _) => { row.BackColor = Color.Transparent; row.Invalidate(); };
        row.Controls.Add(Label(icon, 14f, Theme.Primary, 14, 12, 24, 24));
        row.Controls.Add(LabelBold(title, 11f, Theme.TextPrimary, 48, 6, 240, 20));
        row.Controls.Add(Label(sub, 9f, Theme.TextHint, 48, 26, 260, 16));
        row.Click += (_, _) => action();
        foreach (Control c in row.Controls) c.Click += (_, _) => action();
        p.Controls.Add(row);
    }

    private async Task DeleteAccountAsync()
    {
        if (MessageBox.Show(this, "This will permanently delete ALL your data. This cannot be undone.",
            "Delete Account", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes) return;

        var typed = PromptForText("Type DELETE to confirm", "Delete Account", "DELETE");
        if (typed != "DELETE")
        {
            if (typed != null) MessageBox.Show(this, "Confirmation did not match. Account was not deleted.",
                "Cancelled", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        try
        {
            await _sp.GetRequiredService<IProfileApi>().DeleteAccount();
            LogoutRequested = true;
            Close();
        }
        catch (Refit.ApiException ex)
        {
            MessageBox.Show(this, $"Failed to delete account: {ex.StatusCode}", "Error",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, $"Failed to delete account: {ex.Message}", "Error",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    /// <summary>A small modal text-entry dialog (replaces the fake Yes/No confirmation).</summary>
    private string? PromptForText(string prompt, string title, string placeholder)
    {
        string? result = null;
        using var f = new Form { Text = title, ClientSize = new Size(380, 200),
            StartPosition = FormStartPosition.CenterParent, FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false, BackColor = Theme.Background, Font = Theme.Font(10f) };
        var card = new Panel { Size = new Size(340, 160), Location = new Point(20, 20), BackColor = Theme.Surface };
        f.Controls.Add(card);
        card.Controls.Add(LabelBold(prompt, 11f, Theme.TextPrimary, 20, 18, 300, 28));
        var box = new TextBox { Location = new Point(20, 56), Size = new Size(300, 32), Font = Theme.Font(11f), PlaceholderText = placeholder };
        card.Controls.Add(box);
        var err = new Label { Location = new Point(20, 94), Size = new Size(300, 18), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false };
        card.Controls.Add(err);
        var ok = RoundedButton("Confirm", Theme.Error, Color.White, 10, 200, 116, 120, 32);
        var cancel = RoundedButton("Cancel", Theme.SurfaceVariant, Theme.TextSecondary, 10, 20, 116, 120, 32);
        card.Controls.Add(ok); card.Controls.Add(cancel);
        ok.Click += (_, _) => { f.DialogResult = DialogResult.OK; f.Close(); };
        cancel.Click += (_, _) => { f.DialogResult = DialogResult.Cancel; f.Close(); };
        box.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; ok.PerformClick(); } };
        f.FormClosed += (_, _) => result = f.DialogResult == DialogResult.OK ? box.Text.Trim() : null;
        f.ShowDialog(this);
        return result;
    }

    private void OpenProfile()
    {
        var p = _sp.GetRequiredService<ProfilePage>();
        p.ShowDialog(this);
    }

    private void OpenChangePassword()
    {
        if (string.Equals(_session.Provider, "GOOGLE", StringComparison.OrdinalIgnoreCase))
        {
            MessageBox.Show(this, "Google sign-in users do not have a password.", "Change Password",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        _sp.GetRequiredService<ChangePasswordForm>().ShowDialog(this);
    }
}

public class ChangePasswordForm : Form
{
    public ChangePasswordForm(IProfileApi api)
    {
        _api = api;
        this.Text = "Change Password"; this.ClientSize = new Size(380, 320);
        this.StartPosition = FormStartPosition.CenterParent; this.BackColor = Theme.Background;
        this.FormBorderStyle = FormBorderStyle.FixedDialog; this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi; this.Font = Theme.Font(10f);

        var card = new Panel { Size = new Size(340, 280), Location = new Point(20, 20), BackColor = Theme.Surface };
        Controls.Add(card);
        card.Paint += (_, e) => { using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };
        card.Controls.Add(LabelBold("Change Password", 16f, Theme.TextPrimary, 24, 18, 280, 30));

        var oldBox = InputBox(24, 60, "Current password"); oldBox.UseSystemPasswordChar = true; card.Controls.Add(oldBox);
        card.Controls.Add(LabelBold("Current", 9f, Theme.TextSecondary, 24, 56, 200, 16)); // label above (tiny)
        var newBox = InputBox(24, 110, "New password (8+, letter + digit)"); newBox.UseSystemPasswordChar = true; card.Controls.Add(newBox);
        var confirmBox = InputBox(24, 160, "Confirm new password"); confirmBox.UseSystemPasswordChar = true; card.Controls.Add(confirmBox);

        var err = new Label { Location = new Point(24, 200), Size = new Size(292, 18), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false };
        card.Controls.Add(err);
        var saveBtn = RoundedButton("Save", Theme.Primary, Color.White, 14, 24, 226, 292, 36);
        saveBtn.Click += async (_, _) =>
        {
            err.Visible = false;
            if (string.IsNullOrWhiteSpace(newBox.Text) || newBox.Text.Length < 8
                || !newBox.Text.Any(char.IsLetter) || !newBox.Text.Any(char.IsDigit))
            { err.Text = "New password: min 8 chars, one letter + one digit."; err.Visible = true; return; }
            if (newBox.Text != confirmBox.Text) { err.Text = "Passwords do not match."; err.Visible = true; return; }
            try
            {
                saveBtn.Enabled = false;
                await _api.ChangePassword(new Models.ChangePasswordRequest { OldPassword = oldBox.Text, NewPassword = newBox.Text });
                MessageBox.Show(this, "Password changed successfully!", "Success", MessageBoxButtons.OK, MessageBoxIcon.Information);
                Close();
            }
            catch (Refit.ApiException ex)
            {
                err.Text = ex.StatusCode == System.Net.HttpStatusCode.BadRequest
                    ? "Current password is incorrect." : $"Error: {ex.StatusCode}";
                err.Visible = true;
            }
            catch (Exception ex) { err.Text = $"Connection failed: {ex.Message}"; err.Visible = true; }
            finally { saveBtn.Enabled = true; }
        };
        card.Controls.Add(saveBtn);
    }
    private readonly IProfileApi _api;
}
