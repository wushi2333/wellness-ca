using System.Drawing.Drawing2D;
using RecordYourWellnessApp.ViewModels;
using RecordYourWellnessApp.Services;
using static RecordYourWellnessApp.Views.LoginForm;

namespace RecordYourWellnessApp.Views;

public class CharacterChatPage : UserControl
{
    private readonly CharacterChatViewModel _vm;
    private readonly IAudioService _audio;
    private readonly ICharacterApi _characterApi;
    private readonly Panel _sidebar, _msgPanel, _header, _inputBar, _sessionListPanel;
    private readonly TextBox _inputBox;
    private readonly Label _statusLabel, _sessionHint;
    private readonly Button _sendBtn, _micBtn, _ttsBtn, _chatModeBtn, _agentModeBtn, _toggleBtn;
    private bool _isRecording;

    public CharacterChatPage(CharacterChatViewModel vm, IAudioService audio, ICharacterApi characterApi)
    {
        _vm = vm; _audio = audio; _characterApi = characterApi;
        this.Dock = DockStyle.Fill; this.BackColor = Theme.Background;
        this.Font = Theme.Font(10f);

        // ── Sidebar (sessions) ─────────────────────────────────────
        _sidebar = new Panel { Width = 244, Dock = DockStyle.Left, BackColor = Theme.Surface };
        _sidebar.Paint += (_, e) => { using var p = new Pen(Theme.Divider); e.Graphics.DrawLine(p, _sidebar.Width - 1, 0, _sidebar.Width - 1, _sidebar.Height); };
        Controls.Add(_sidebar);

        // Sidebar header — keep the title narrow so it never sits under the New button.
        _sidebar.Controls.Add(LabelBold(Loc.T("chat.sessions"), 14f, Theme.TextPrimary, 16, 16, 110, 24));
        var newBtn = RoundedButton(Loc.T("chat.new"), Theme.Primary, Color.White, 10, 152, 14, 78, 30);
        newBtn.Click += async (_, _) => await _vm.CreateSessionCommand.ExecuteAsync(null);
        _sidebar.Controls.Add(newBtn);
        _sessionHint = Label(Loc.T("chat.hint"), 9f, Theme.TextHint, 16, 48, 210, 18);
        _sidebar.Controls.Add(_sessionHint);

        // Scrollable list of sessions (fixes: could not scroll to older sessions).
        _sessionListPanel = new Panel { Location = new Point(0, 74), Size = new Size(244, 400), AutoScroll = true, BackColor = Theme.Surface };
        _sidebar.Controls.Add(_sessionListPanel);

        // ── Main area ──────────────────────────────────────────────
        var mainPanel = new Panel { Dock = DockStyle.Fill, BackColor = Theme.Background };
        Controls.Add(mainPanel);
        // Docking is resolved back-to-front, so the Dock.Fill panel must be at the FRONT
        // of the z-order to receive only the space left over after the Dock.Left sidebar.
        mainPanel.BringToFront();

        // Header
        _header = new Panel { Location = new Point(0, 0), Size = new Size(600, 52), BackColor = Theme.Surface };
        _header.Paint += (_, e) => { using var p = new Pen(Theme.Divider); e.Graphics.DrawLine(p, 0, _header.Height - 1, _header.Width, _header.Height - 1); };
        mainPanel.Controls.Add(_header);

        _toggleBtn = new Button { Text = "☰", Font = Theme.Font(14f), FlatStyle = FlatStyle.Flat, Location = new Point(10, 10), Size = new Size(36, 32), ForeColor = Theme.TextSecondary, BackColor = Color.Transparent, Cursor = Cursors.Hand };
        _toggleBtn.FlatAppearance.BorderSize = 0;
        _toggleBtn.Click += (_, _) => { _sidebar.Visible = !_sidebar.Visible; LayoutControls(); };
        _header.Controls.Add(_toggleBtn);

        var avatar = new Panel { Location = new Point(54, 10), Size = new Size(32, 32) };
        avatar.Paint += (_, e) => { e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; using var b = new SolidBrush(Theme.Secondary); using var path = Theme.RoundedRect(new Rectangle(0, 0, 31, 31), 16); e.Graphics.FillPath(b, path); using var f = Theme.Font(14f); using var w = new SolidBrush(Color.White); var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center }; e.Graphics.DrawString("🐱", f, w, new RectangleF(0, 0, 32, 32), sf); };
        _header.Controls.Add(avatar);
        _header.Controls.Add(LabelBold("Yui", 14f, Theme.TextPrimary, 94, 14, 90, 24));

        _chatModeBtn = ModeBtn(Loc.T("chat.modeChat"), 196, true); _chatModeBtn.Click += (_, _) => SetMode("chat"); _header.Controls.Add(_chatModeBtn);
        _agentModeBtn = ModeBtn(Loc.T("chat.modeAgent"), 280, false); _agentModeBtn.Click += (_, _) => SetMode("agent"); _header.Controls.Add(_agentModeBtn);
        _statusLabel = Label("", 9f, Theme.TextHint, 368, 18, 220, 20); _header.Controls.Add(_statusLabel);

        // Messages
        _msgPanel = new Panel { Location = new Point(0, 52), Size = new Size(600, 400), AutoScroll = true, BackColor = Theme.Background };
        mainPanel.Controls.Add(_msgPanel);

        // Input bar
        _inputBar = new Panel { Location = new Point(0, 460), Size = new Size(600, 60), BackColor = Theme.Surface };
        _inputBar.Paint += (_, e) => { using var p = new Pen(Theme.Divider); e.Graphics.DrawLine(p, 0, 0, _inputBar.Width, 0); };
        mainPanel.Controls.Add(_inputBar);

        _inputBox = new TextBox { Location = new Point(12, 14), Size = new Size(360, 32), Font = Theme.Font(11f), BorderStyle = BorderStyle.FixedSingle, PlaceholderText = Loc.T("chat.placeholder") };
        _inputBox.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter && !e.Shift) { e.SuppressKeyPress = true; _vm.InputText = _inputBox.Text; _ = _vm.SendMessageCommand.ExecuteAsync(null); } };
        _inputBar.Controls.Add(_inputBox);

        // TTS (voice replies) on/off toggle — icon reflects state.
        _ttsBtn = IconButton("🔊");
        _ttsBtn.Click += (_, _) => ToggleTts();
        _inputBar.Controls.Add(_ttsBtn);

        // Microphone ASR toggle — click to start, click again to stop & transcribe.
        _micBtn = IconButton("🎤");
        _micBtn.Click += (_, _) => _ = ToggleRecordingAsync();
        _inputBar.Controls.Add(_micBtn);

        _sendBtn = RoundedButton(Loc.T("chat.send"), Theme.Primary, Color.White, 10, 420, 12, 80, 36);
        _sendBtn.Click += (_, _) => { _vm.InputText = _inputBox.Text; _ = _vm.SendMessageCommand.ExecuteAsync(null); };
        _inputBar.Controls.Add(_sendBtn);

        UpdateTtsIcon();

        // ── VM events ──────────────────────────────────────────────
        _vm.MessagesLoaded += _ => BeginInvoke(() => RenderMessages());
        _vm.ResponseReceived += resp => BeginInvoke(() => { RenderMessages(); _inputBox.Focus(); HandleIntent(resp); });
        _vm.TtsReady += audioData => BeginInvoke(() => { if (_vm.TtsEnabled) _audio.PlayAudio(audioData); });
        _vm.PropertyChanged += (_, e) => { if (InvokeRequired) { BeginInvoke(() => RefreshProp(e.PropertyName)); return; } RefreshProp(e.PropertyName); };

        this.Load += async (_, _) => { LayoutControls(); await _vm.LoadSessionsCommand.ExecuteAsync(null); RenderSessionList(); };
        this.Resize += (_, _) => LayoutControls();
    }

    private static Button IconButton(string glyph) => new()
    {
        Text = glyph, Font = Theme.Font(14f), FlatStyle = FlatStyle.Flat, Size = new Size(44, 40),
        ForeColor = Theme.TextHint, BackColor = Color.Transparent, Cursor = Cursors.Hand,
        FlatAppearance = { BorderSize = 0 }
    };

    private Button ModeBtn(string text, int x, bool active)
    {
        var b = new Button { Text = text, FlatStyle = FlatStyle.Flat, Location = new Point(x, 12), Size = new Size(76, 28),
            Font = Theme.Font(10f, FontStyle.Bold), Cursor = Cursors.Hand,
            ForeColor = active ? Color.White : Theme.TextSecondary, BackColor = active ? Theme.Primary : Theme.SurfaceVariant };
        b.FlatAppearance.BorderSize = 0; return b;
    }

    private void LayoutControls()
    {
        int sw = _sidebar.Visible ? _sidebar.Width : 0;
        int w = this.Width - sw;
        if (w < 100) w = 100;
        _header.Width = w;
        _sessionListPanel.Size = new Size(_sidebar.Width, Math.Max(60, this.Height - 74));
        _msgPanel.Location = new Point(0, 52);
        _msgPanel.Size = new Size(w, Math.Max(100, this.Height - 52 - 60));
        _inputBar.Location = new Point(0, this.Height - 60);
        _inputBar.Width = w;
        // [ input ................ ] [🔊] [🎤] [ Send ]
        _sendBtn.Left = w - 92;
        _micBtn.Left = _sendBtn.Left - 48;
        _ttsBtn.Left = _micBtn.Left - 48;
        _inputBox.Width = Math.Max(160, _ttsBtn.Left - 24);
        RenderMessages(); // re-flow bubbles to the new width
    }

    private void ToggleTts()
    {
        _vm.TtsEnabled = !_vm.TtsEnabled;
        if (!_vm.TtsEnabled) _audio.StopPlayback();
        UpdateTtsIcon();
    }

    private void UpdateTtsIcon()
    {
        _ttsBtn.Text = _vm.TtsEnabled ? "🔊" : "🔇";
        _ttsBtn.ForeColor = _vm.TtsEnabled ? Theme.Primary : Theme.TextHint;
        _statusLabel.Text = _vm.TtsEnabled ? Loc.T("chat.ttsOn") : Loc.T("chat.ttsOff");
    }

    private void SetMode(string mode)
    {
        _vm.SetModeCommand.Execute(mode);
        _chatModeBtn.BackColor = mode == "chat" ? Theme.Primary : Theme.SurfaceVariant;
        _chatModeBtn.ForeColor = mode == "chat" ? Color.White : Theme.TextSecondary;
        _agentModeBtn.BackColor = mode == "agent" ? Theme.Secondary : Theme.SurfaceVariant;
        _agentModeBtn.ForeColor = mode == "agent" ? Theme.TextPrimary : Theme.TextSecondary;
    }

    private void HandleIntent(Models.CharacterChatResponse resp)
    {
        if (resp.Intent == null) return;
        if (resp.Intent.TryGetValue("action", out var aObj) && aObj?.ToString() == "navigate")
        {
            var target = resp.Intent.TryGetValue("target", out var tObj) ? tObj?.ToString() : null;
            (FindForm() as MainForm)?.NavigateToAgentTarget(target);
        }
    }

    private void RenderSessionList()
    {
        _sessionListPanel.SuspendLayout();
        _sessionListPanel.Controls.Clear();

        if (_vm.Sessions.Count == 0) { _sessionHint.Visible = true; _sessionListPanel.ResumeLayout(); return; }
        _sessionHint.Visible = false;

        int y = 6;
        foreach (var s in _vm.Sessions)
        {
            var row = new Panel { Location = new Point(8, y), Size = new Size(204, 52), Cursor = Cursors.Hand,
                BackColor = _vm.SelectedSession?.Id == s.Id ? Theme.SurfaceVariant : Color.Transparent };
            row.Paint += (_, e) => { if (row.BackColor == Theme.SurfaceVariant) { e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; using var b = new SolidBrush(Theme.SurfaceVariant); using var path = Theme.RoundedRect(new Rectangle(0, 0, row.Width - 1, row.Height - 1), 10); e.Graphics.FillPath(b, path); } };
            row.Click += async (_, _) => await _vm.SelectSessionCommand.ExecuteAsync(s);
            var title = string.IsNullOrWhiteSpace(s.Title) ? Loc.T("chat.newChat") : s.Title;
            var tLabel = Label(title, 11f, Theme.TextPrimary, 12, 6, 158, 20); tLabel.AutoEllipsis = true; tLabel.UseCompatibleTextRendering = false;
            row.Controls.Add(tLabel);
            row.Controls.Add(Label($"{s.Mode} · {s.MessageCount} {Loc.T("chat.msgs")}", 9f, Theme.TextHint, 12, 28, 158, 16));
            foreach (Control c in row.Controls) c.Click += async (_, _) => await _vm.SelectSessionCommand.ExecuteAsync(s);

            var del = new Button { Text = "×", Font = Theme.Font(12f, FontStyle.Bold), ForeColor = Theme.TextHint,
                FlatStyle = FlatStyle.Flat, Location = new Point(172, 10), Size = new Size(24, 24), BackColor = Color.Transparent, Cursor = Cursors.Hand };
            del.FlatAppearance.BorderSize = 0;
            del.Click += async (_, _) => await _vm.DeleteSessionCommand.ExecuteAsync(s.Id);
            row.Controls.Add(del);

            _sessionListPanel.Controls.Add(row);
            y += 56;
        }
        _sessionListPanel.ResumeLayout();
    }

    private void RenderMessages()
    {
        _msgPanel.SuspendLayout();
        _msgPanel.Controls.Clear();
        int maxContentW = Math.Max(160, _msgPanel.Width - 130);
        int y = 10;
        Control? last = null;

        foreach (var msg in _vm.Messages)
        {
            var isUser = msg.Role == "user";
            var font = Theme.Font(11f);
            var content = new Label { Text = msg.Content, Font = font, AutoSize = true,
                MaximumSize = new Size(maxContentW, 0),
                ForeColor = isUser ? Color.White : Theme.TextPrimary, BackColor = Color.Transparent,
                UseCompatibleTextRendering = false, Padding = new Padding(0), Margin = new Padding(0) };
            int bubbleH = content.PreferredHeight + 20;
            int bubbleW = Math.Min(content.PreferredWidth + 24, maxContentW);
            int bubbleX = isUser ? _msgPanel.Width - bubbleW - 16 : 16;
            if (_msgPanel.Width < bubbleW + 32) bubbleX = 8;

            var bubble = new Panel { Location = new Point(bubbleX, y), Size = new Size(bubbleW, bubbleH),
                BackColor = isUser ? Theme.Primary : Theme.SurfaceVariant };
            bubble.Region = new Region(Theme.RoundedRect(new Rectangle(0, 0, bubbleW - 1, bubbleH - 1), 14));
            content.Location = new Point(12, 10);
            bubble.Controls.Add(content);
            _msgPanel.Controls.Add(bubble);
            y += bubbleH + 10;
            last = bubble;
        }
        _msgPanel.ResumeLayout();
        if (last != null) _msgPanel.ScrollControlIntoView(last);
    }

    /// <summary>Mic acts as an on/off switch: first click records, second click stops
    /// and transcribes the captured audio, then sends it.</summary>
    private async Task ToggleRecordingAsync()
    {
        if (!_isRecording)
        {
            _isRecording = true;
            _micBtn.Text = "⏺";
            _micBtn.ForeColor = Theme.Error;
            _statusLabel.Text = Loc.T("chat.recording");
            _audio.StartRecording();
            return;
        }

        _isRecording = false;
        _micBtn.Text = "🎤";
        _micBtn.ForeColor = Theme.TextHint;
        _statusLabel.Text = Loc.T("chat.transcribing");
        var audioData = _audio.StopRecording();
        if (audioData.Length < 800) { _statusLabel.Text = ""; return; }

        try
        {
            var base64 = Convert.ToBase64String(audioData);
            var lang = Loc.IsZh ? "zh-CN" : "en-US";
            var result = await _characterApi.Asr(new Dictionary<string, string> { ["audio"] = base64, ["language"] = lang });
            if (result.TryGetValue("text", out var text) && !string.IsNullOrWhiteSpace(text))
            {
                _inputBox.Text = text;
                _vm.InputText = text;
                _ = _vm.SendMessageCommand.ExecuteAsync(null);
            }
        }
        catch { _statusLabel.Text = "Transcription failed"; }
        finally { _statusLabel.Text = ""; }
    }

    private void RefreshProp(string? prop)
    {
        switch (prop)
        {
            case nameof(_vm.IsSending): _sendBtn.Enabled = !_vm.IsSending; if (!_isRecording) _statusLabel.Text = _vm.IsSending ? Loc.T("chat.thinking") : ""; break;
            case nameof(_vm.StatusText): if (!string.IsNullOrEmpty(_vm.StatusText)) _statusLabel.Text = _vm.StatusText; break;
            case nameof(_vm.Sessions): RenderSessionList(); break;
            case nameof(_vm.InputText): if (_inputBox.Text != _vm.InputText) _inputBox.Text = _vm.InputText; break;
        }
    }
}
