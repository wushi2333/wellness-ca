// Author: Xia Zihang
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.Models;
using static RecordYourWellnessApp.Views.LoginForm;

namespace RecordYourWellnessApp.Views;

public class ProfilePage : Form
{
    private readonly IProfileApi _api;
    private readonly ISessionService _session;
    private readonly Panel _avatar;
    private readonly Label _usernameLabel, _emailLabel, _providerLabel;
    private readonly Button _editEmailBtn;
    private readonly TextBox _heightBox, _weightBox, _ageBox, _nicknameBox;
    private readonly Label _errorLabel;
    private readonly Button _saveMetrics;
    private UserProfileData? _profile;
    private Image? _avatarImage;

    private static readonly HttpClient AvatarClient = new HttpClient();

    public ProfilePage(IProfileApi api, ISessionService session)
    {
        _api = api; _session = session;
        this.Text = "Profile"; this.ClientSize = new Size(520, 660);
        this.StartPosition = FormStartPosition.CenterParent; this.BackColor = Theme.Background;
        this.FormBorderStyle = FormBorderStyle.FixedDialog; this.MaximizeBox = false;
        this.AutoScaleMode = AutoScaleMode.Dpi; this.Font = Theme.Font(10f);

        var card = new Panel { Size = new Size(480, 620), Location = new Point(20, 20), BackColor = Theme.Surface, AutoScroll = true };
        Controls.Add(card);
        card.Paint += (_, e) => { using var b = new Pen(Theme.Outline, 1); e.Graphics.DrawRectangle(b, 0, 0, card.Width - 1, card.Height - 1); };

        card.Controls.Add(LabelBold("Profile", 22f, Theme.TextPrimary, 24, 20, 300, 36));

        // Avatar (circular, click to change)
        _avatar = new Panel { Location = new Point(24, 70), Size = new Size(84, 84), Cursor = Cursors.Hand };
        _avatar.Click += async (_, _) => await UploadAvatar();
        _avatar.Paint += (_, e) =>
        {
            var g = e.Graphics; g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var r = new Rectangle(2, 2, 80, 80);
            using var path = Theme.RoundedRect(r, 40);
            if (_avatarImage != null) { g.SetClip(path); g.DrawImage(_avatarImage, r); g.ResetClip(); }
            else { using var b = new SolidBrush(Theme.Primary); g.FillPath(b, path); var init = string.IsNullOrEmpty(_session.Username) ? "U" : char.ToUpper(_session.Username[0]).ToString(); using var f = Theme.Font(26f, FontStyle.Bold); using var w = new SolidBrush(Color.White); var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center }; g.DrawString(init, f, w, new RectangleF(2, 2, 80, 80), sf); }
            using var outline = new Pen(Theme.Outline, 1); g.DrawPath(outline, path);
        };
        card.Controls.Add(_avatar);

        var uploadBtn = RoundedButton("Change Photo", Theme.Primary, Color.White, 14, 124, 108, 130, 36);
        uploadBtn.Click += async (_, _) => await UploadAvatar();
        card.Controls.Add(uploadBtn);

        // Username
        int y = 176;
        card.Controls.Add(LabelBold("Username", 11f, Theme.TextPrimary, 24, y, 100, 24));
        _usernameLabel = Label("", 11f, Theme.TextPrimary, 130, y, 230, 24); card.Controls.Add(_usernameLabel);
        var editUser = EditBtn(372, y); editUser.Click += async (_, _) => await EditUsername(); card.Controls.Add(editUser); y += 36;

        // Email
        card.Controls.Add(LabelBold("Email", 11f, Theme.TextPrimary, 24, y, 100, 24));
        _emailLabel = Label("", 11f, Theme.TextPrimary, 130, y, 230, 24); card.Controls.Add(_emailLabel);
        _editEmailBtn = EditBtn(372, y); _editEmailBtn.Click += async (_, _) => await EditEmail(); card.Controls.Add(_editEmailBtn); y += 36;

        // Provider
        card.Controls.Add(LabelBold("Provider", 11f, Theme.TextPrimary, 24, y, 100, 24));
        _providerLabel = Label("", 11f, Theme.TextHint, 130, y, 230, 24); card.Controls.Add(_providerLabel);
        y += 44;

        var div = new Label { Location = new Point(24, y), Size = new Size(432, 1), BackColor = Theme.Divider }; card.Controls.Add(div); y += 18;

        // Body metrics
        card.Controls.Add(LabelBold("Body Metrics", 14f, Theme.TextPrimary, 24, y, 200, 24)); y += 36;

        void AddMetric(string label, ref TextBox box, int x)
        {
            card.Controls.Add(LabelBold(label, 11f, Theme.TextSecondary, x, y, 70, 20));
            box = new TextBox { Location = new Point(x + 70, y - 2), Size = new Size(80, 28), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, TextAlign = HorizontalAlignment.Center };
            card.Controls.Add(box);
        }

        AddMetric("Height", ref _heightBox, 24);
        card.Controls.Add(Label("cm", 10f, Theme.TextHint, 184, y, 30, 20));
        AddMetric("Weight", ref _weightBox, 224);
        card.Controls.Add(Label("kg", 10f, Theme.TextHint, 384, y, 30, 20));
        y += 40;
        AddMetric("Age", ref _ageBox, 24);
        y += 36;
        card.Controls.Add(LabelBold("Nickname", 11f, Theme.TextSecondary, 24, y, 100, 20));
        _nicknameBox = new TextBox { Location = new Point(24, y + 24), Size = new Size(320, 32), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle };
        card.Controls.Add(_nicknameBox); y += 64;

        _errorLabel = new Label { Location = new Point(24, y), Size = new Size(432, 20), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false };
        card.Controls.Add(_errorLabel); y += 24;

        _saveMetrics = RoundedButton("Save Metrics", Theme.Secondary, Theme.TextPrimary, 14, 24, y, 160, 36);
        _saveMetrics.Click += async (_, _) => await SaveMetrics();
        card.Controls.Add(_saveMetrics);
        var closeBtn = RoundedButton("Done", Theme.SurfaceVariant, Theme.TextPrimary, 14, 296, y, 160, 36);
        closeBtn.Click += (_, _) => Close();
        card.Controls.Add(closeBtn);

        this.Load += async (_, _) => await LoadProfile();
    }

    private Button EditBtn(int x, int y)
    {
        var b = new Button { Text = "Edit", Font = Theme.Font(9f, FontStyle.Bold), ForeColor = Theme.Primary, FlatStyle = FlatStyle.Flat, Location = new Point(x, y), Size = new Size(56, 24), Cursor = Cursors.Hand };
        b.FlatAppearance.BorderSize = 0; return b;
    }

    private async Task LoadProfile()
    {
        try
        {
            _profile = await _api.GetUser();
            _usernameLabel.Text = _profile.Username;
            _emailLabel.Text = string.IsNullOrWhiteSpace(_profile.Email) ? "Not set" : _profile.Email;
            _providerLabel.Text = string.IsNullOrWhiteSpace(_profile.Provider) ? "Local" : char.ToUpper(_profile.Provider[0]) + _profile.Provider.Substring(1).ToLower();
            _heightBox.Text = _profile.HeightCm?.ToString() ?? "";
            _weightBox.Text = _profile.WeightKg?.ToString() ?? "";
            _ageBox.Text = _profile.Age?.ToString() ?? "";
            _nicknameBox.Text = _profile.Nickname ?? "";
            // Google users cannot change their email (matches Android ProfileActivity)
            _editEmailBtn.Visible = !string.Equals(_profile.Provider, "GOOGLE", StringComparison.OrdinalIgnoreCase);
            if (!string.IsNullOrWhiteSpace(_profile.AvatarUrl))
                _ = LoadAvatarAsync(_profile.AvatarUrl);
        }
        catch (Refit.ApiException ex)
        {
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
                ShowError($"Could not load profile ({ex.StatusCode}).");
        }
        catch (Exception ex) { ShowError($"Network error: {ex.Message}"); }
    }

    private async Task LoadAvatarAsync(string url)
    {
        try
        {
            var fullUrl = url.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? url : Constants.BaseUrl + url;
            var bytes = await AvatarClient.GetByteArrayAsync(fullUrl);
            using var ms = new MemoryStream(bytes);
            _avatarImage?.Dispose();
            _avatarImage = Image.FromStream(ms);
            _avatar.Invalidate();
        }
        catch { }
    }

    private async Task UploadAvatar()
    {
        using var dlg = new OpenFileDialog { Filter = "Images|*.png;*.jpg;*.jpeg", Title = "Choose Avatar" };
        if (dlg.ShowDialog(this) != DialogResult.OK) return;
        try
        {
            using var img = Image.FromFile(dlg.FileName);
            using var resized = ResizeImage(img, 512);
            using var ms = new MemoryStream();
            resized.Save(ms, System.Drawing.Imaging.ImageFormat.Jpeg);
            ms.Position = 0; // CRITICAL: StreamPart reads from Position; without this 0 bytes upload
            var streamPart = new Refit.StreamPart(ms, "avatar.jpg", "image/jpeg");
            await _api.UploadAvatar(streamPart);
            await Task.Delay(400); // give the server a moment to persist the new file
            await LoadProfile();
        }
        catch (Refit.ApiException ex)
        {
            ShowError(ex.StatusCode == System.Net.HttpStatusCode.Unauthorized ? "" :
                      ex.StatusCode == System.Net.HttpStatusCode.RequestEntityTooLarge ? "Image is too large (max 5MB)." :
                      $"Upload failed ({ex.StatusCode}).");
        }
        catch (Exception ex) { ShowError($"Upload failed: {ex.Message}"); }
    }

    private static Image ResizeImage(Image img, int maxSize)
    {
        var ratio = Math.Min((double)maxSize / img.Width, (double)maxSize / img.Height);
        if (ratio >= 1) return new Bitmap(img);
        var w = (int)(img.Width * ratio); var h = (int)(img.Height * ratio);
        var bmp = new Bitmap(w, h);
        using var g = Graphics.FromImage(bmp);
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
        g.DrawImage(img, 0, 0, w, h);
        return bmp;
    }

    private async Task EditUsername()
    {
        var input = PromptForText("Enter new username", "Edit Username", _usernameLabel.Text);
        if (string.IsNullOrWhiteSpace(input) || input.Length < 3) { if (input != null) ShowError("Username must be at least 3 characters."); return; }
        try
        {
            await _api.ChangeUsername(new Dictionary<string, string> { ["username"] = input });
            _usernameLabel.Text = input;
            _session.SetUserProfile(input, _session.UserId, _session.Provider);
            ShowError(""); HideError();
        }
        catch (Refit.ApiException ex)
        {
            ShowError(ex.StatusCode == System.Net.HttpStatusCode.Conflict ? "Username is already taken." :
                      ex.StatusCode == System.Net.HttpStatusCode.Unauthorized ? "" : $"Error: {ex.StatusCode}");
        }
        catch (Exception ex) { ShowError($"Network error: {ex.Message}"); }
    }

    private async Task EditEmail()
    {
        var input = PromptForText("Enter email address", "Edit Email", _emailLabel.Text);
        if (string.IsNullOrWhiteSpace(input)) return;
        try
        {
            await _api.ChangeEmail(new Dictionary<string, string> { ["email"] = input });
            _emailLabel.Text = input;
            HideError();
        }
        catch (Refit.ApiException ex)
        {
            ShowError(ex.StatusCode == System.Net.HttpStatusCode.Conflict ? "Email is already linked to another account." :
                      ex.StatusCode == System.Net.HttpStatusCode.Unauthorized ? "" : $"Error: {ex.StatusCode}");
        }
        catch (Exception ex) { ShowError($"Network error: {ex.Message}"); }
    }

    private async Task SaveMetrics()
    {
        try
        {
            _saveMetrics.Enabled = false;
            var req = new ProfileUpdateRequest
            {
                Nickname = string.IsNullOrWhiteSpace(_nicknameBox.Text) ? null : _nicknameBox.Text,
                HeightCm = int.TryParse(_heightBox.Text, out var h) ? h : null,
                WeightKg = double.TryParse(_weightBox.Text, out var w) ? w : null,
                Age = int.TryParse(_ageBox.Text, out var a) ? a : null,
            };
            await _api.UpdateProfile(req);
            HideError();
            MessageBox.Show(this, "Profile saved!", "Success", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        catch (Refit.ApiException ex)
        {
            ShowError(ex.StatusCode == System.Net.HttpStatusCode.Unauthorized ? "" : $"Error: {ex.StatusCode}");
        }
        catch (Exception ex) { ShowError($"Network error: {ex.Message}"); }
        finally { _saveMetrics.Enabled = true; }
    }

    private void ShowError(string msg) { _errorLabel.Text = msg; _errorLabel.Visible = !string.IsNullOrEmpty(msg); }
    private void HideError() => _errorLabel.Visible = false;

    /// <summary>A small modal text-entry dialog (replaces VisualBasic.InputBox for a
    /// consistent look with the rest of the app).</summary>
    private string? PromptForText(string prompt, string title, string initial)
    {
        string? result = null;
        using var f = new Form { Text = title, ClientSize = new Size(400, 200),
            StartPosition = FormStartPosition.CenterParent, FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false, BackColor = Theme.Background, Font = Theme.Font(10f) };
        var card = new Panel { Size = new Size(360, 160), Location = new Point(20, 20), BackColor = Theme.Surface };
        f.Controls.Add(card);
        card.Controls.Add(LabelBold(prompt, 11f, Theme.TextPrimary, 20, 18, 320, 24));
        var box = new TextBox { Text = initial, Location = new Point(20, 52), Size = new Size(320, 32), Font = Theme.Font(11f) };
        card.Controls.Add(box);
        var err = new Label { Location = new Point(20, 90), Size = new Size(320, 18), ForeColor = Theme.Error, Visible = false, UseCompatibleTextRendering = false };
        card.Controls.Add(err);
        var ok = RoundedButton("OK", Theme.Primary, Color.White, 10, 240, 118, 100, 32);
        var cancel = RoundedButton("Cancel", Theme.SurfaceVariant, Theme.TextSecondary, 10, 20, 118, 100, 32);
        card.Controls.Add(ok); card.Controls.Add(cancel);
        ok.Click += (_, _) => { f.DialogResult = DialogResult.OK; f.Close(); };
        cancel.Click += (_, _) => { f.DialogResult = DialogResult.Cancel; f.Close(); };
        box.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; ok.PerformClick(); } };
        f.FormClosed += (_, _) => result = f.DialogResult == DialogResult.OK ? box.Text.Trim() : null;
        f.ShowDialog(this);
        return result;
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        _avatarImage?.Dispose();
        base.OnFormClosing(e);
    }
}
