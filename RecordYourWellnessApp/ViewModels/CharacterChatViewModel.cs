// Author: Huang Qianer, Xia Zihang
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Models;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class CharacterChatViewModel : ObservableObject
{
    private readonly ICharacterApi _api;
    private readonly ISessionService _session;

    [ObservableProperty] private List<CharacterSession> _sessions = new();
    [ObservableProperty] private List<CharacterMessage> _messages = new();
    [ObservableProperty] private CharacterSession? _selectedSession;
    [ObservableProperty] private string _inputText = "";
    [ObservableProperty] private string _mode = "chat";
    [ObservableProperty] private bool _isSending;
    [ObservableProperty] private bool _isRecording;
    [ObservableProperty] private bool _ttsEnabled = true;   // voice replies on/off
    [ObservableProperty] private string _statusText = "";

    public event Action<List<CharacterMessage>>? MessagesLoaded;
    public event Action<CharacterChatResponse>? ResponseReceived;
    public event Action<byte[]>? TtsReady;

    public CharacterChatViewModel(ICharacterApi api, ISessionService session)
    { _api = api; _session = session; }

    [RelayCommand] public async Task LoadSessions() { try { Sessions = await _api.GetSessions(); } catch { } }

    [RelayCommand]
    public async Task SelectSession(CharacterSession? s)
    {
        if (s == null) return; SelectedSession = s;
        try { Messages = await _api.GetMessages(s.Id); MessagesLoaded?.Invoke(Messages); }
        catch { }
    }

    [RelayCommand]
    public async Task CreateSession()
    {
        try
        {
            var s = await _api.CreateSession(new Dictionary<string, string> { ["mode"] = Mode });
            await LoadSessions();
            SelectedSession = Sessions.FirstOrDefault(x => x.Id == s.Id);
            Messages = new();
            MessagesLoaded?.Invoke(Messages);
        }
        catch { }
    }

    [RelayCommand]
    public async Task DeleteSession(long id)
    {
        try { await _api.DeleteSession(id); if (SelectedSession?.Id == id) SelectedSession = null; Messages = new(); MessagesLoaded?.Invoke(Messages); await LoadSessions(); }
        catch { }
    }

    [RelayCommand]
    public async Task SendMessage()
    {
        if (string.IsNullOrWhiteSpace(InputText) || IsSending) return;
        var text = InputText; InputText = ""; IsSending = true; StatusText = "Thinking...";

        // Add the user's message locally FIRST so it shows immediately in the current
        // chat view (previously creating a session here wiped this message).
        var userMsg = new CharacterMessage { Role = "user", Content = text, CreatedAt = DateTime.Now.ToString("HH:mm") };
        Messages.Add(userMsg);
        MessagesLoaded?.Invoke(Messages);

        try
        {
            // Ensure a session exists WITHOUT clearing the message we just added.
            long sessionId;
            if (SelectedSession?.Id is long existing) sessionId = existing;
            else
            {
                var created = await _api.CreateSession(new Dictionary<string, string> { ["mode"] = Mode });
                sessionId = created.Id;
                SelectedSession = new CharacterSession { Id = sessionId, Title = "Chat", Mode = Mode, MessageCount = 1, UpdatedAt = DateTime.Now.ToString("O") };
            }

            var req = new CharacterChatRequest { Message = text, Mode = Mode, SessionId = sessionId };
            var resp = Mode == "agent" ? await _api.Agent(req) : await _api.Chat(req);
            SelectedSession = new CharacterSession { Id = resp.SessionId, Title = SelectedSession?.Title ?? "Chat", Mode = Mode, MessageCount = Messages.Count + 1, UpdatedAt = DateTime.Now.ToString("O") };

            var aiMsg = new CharacterMessage { Role = "assistant", Content = resp.Reply, Emotion = resp.Emotion, CreatedAt = DateTime.Now.ToString("HH:mm"), Tools = resp.Tools };
            Messages.Add(aiMsg);
            MessagesLoaded?.Invoke(Messages);
            ResponseReceived?.Invoke(resp);

            // Fetch + play TTS only when voice replies are enabled.
            if (TtsEnabled)
            {
                _ = Task.Run(async () => {
                    try
                    {
                        var ttsResp = await _api.Tts(new Dictionary<string, string> { ["text"] = resp.Reply, ["emotion"] = resp.Emotion ?? "" });
                        var bytes = await ttsResp.Content.ReadAsByteArrayAsync();
                        if (bytes.Length > 0) TtsReady?.Invoke(bytes);
                    }
                    catch { }
                });
            }

            await LoadSessions();
        }
        catch (Exception ex) { StatusText = $"Error: {ex.Message}"; }
        finally { IsSending = false; StatusText = ""; }
    }

    [RelayCommand] private void SetMode(string mode) { Mode = mode; }
}
