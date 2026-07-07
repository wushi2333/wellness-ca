// Author: Xia Zihang
using System.Runtime.InteropServices;
using System.Text;

namespace RecordYourWellnessApp.Services;

public interface ISessionService
{
    string AccessToken { get; }
    long UserId { get; }
    string Username { get; }
    string Provider { get; }
    bool IsLoggedIn { get; }

    /// <summary>Fires when the user explicitly signs out (navigate to login silently).</summary>
    event Action? LoggedOut;

    /// <summary>Fires when the server rejects the token (401) — navigate to login + notify.</summary>
    event Action? SessionExpired;

    void Login(Models.LoginResponse response);
    void Logout();
    void OnSessionExpired();
    bool TryRestoreSession();
    void SetUserProfile(string username, long userId, string? provider = null);
}

public class SessionService : ISessionService
{
    public string AccessToken { get; private set; } = "";
    public long UserId { get; private set; }
    public string Username { get; private set; } = "";
    public string Provider { get; private set; } = "LOCAL";
    public bool IsLoggedIn => !string.IsNullOrEmpty(AccessToken);

    public event Action? LoggedOut;
    public event Action? SessionExpired;

    public void Login(Models.LoginResponse response)
    {
        AccessToken = response.AccessToken;
        UserId = response.UserId;
        Username = response.Username;

        try { SaveToCredentialManager(Username, AccessToken); } catch { }
    }

    public void Logout()
    {
        Clear();
        LoggedOut?.Invoke();
    }

    public void OnSessionExpired()
    {
        Clear();
        SessionExpired?.Invoke();
    }

    private void Clear()
    {
        AccessToken = "";
        UserId = 0;
        Username = "";
        Provider = "LOCAL";
        try { DeleteFromCredentialManager(); } catch { }
    }

    public bool TryRestoreSession()
    {
        try
        {
            var (user, token) = ReadFromCredentialManager();
            if (!string.IsNullOrEmpty(token))
            {
                AccessToken = token;
                Username = user;
                return true;
            }
        }
        catch { }
        return false;
    }

    public void SetUserProfile(string username, long userId, string? provider = null)
    {
        Username = username;
        UserId = userId;
        if (!string.IsNullOrEmpty(provider)) Provider = provider;
    }

    // ── Windows Credential Manager (P/Invoke) ───────────────────────

    private const string CredTarget = "RecordYourWellnessApp";

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredWriteW(ref CREDENTIALW credential, uint flags);

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredReadW(string target, int type, uint flags, out IntPtr credential);

    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern void CredFree(IntPtr buffer);

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredDeleteW(string target, int type, uint flags);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct CREDENTIALW
    {
        public uint Flags;
        public int Type;
        public IntPtr TargetName;
        public IntPtr Comment;
        public long LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public IntPtr TargetAlias;
        public IntPtr UserName;
    }

    private const int CRED_TYPE_GENERIC = 1;

    private void SaveToCredentialManager(string username, string token)
    {
        var targetName = Marshal.StringToCoTaskMemUni(CredTarget);
        var userName = Marshal.StringToCoTaskMemUni(username);
        var tokenBytes = Encoding.Unicode.GetBytes(token);
        var blob = Marshal.AllocCoTaskMem(tokenBytes.Length);
        Marshal.Copy(tokenBytes, 0, blob, tokenBytes.Length);

        var cred = new CREDENTIALW
        {
            Type = CRED_TYPE_GENERIC,
            TargetName = targetName,
            UserName = userName,
            CredentialBlobSize = (uint)tokenBytes.Length,
            CredentialBlob = blob,
            Persist = 2, // local machine
        };

        CredWriteW(ref cred, 0);

        Marshal.FreeCoTaskMem(targetName);
        Marshal.FreeCoTaskMem(userName);
        Marshal.FreeCoTaskMem(blob);
    }

    private (string username, string token) ReadFromCredentialManager()
    {
        if (!CredReadW(CredTarget, CRED_TYPE_GENERIC, 0, out var ptr))
            return ("", "");

        var cred = Marshal.PtrToStructure<CREDENTIALW>(ptr);
        var username = cred.UserName != IntPtr.Zero
            ? Marshal.PtrToStringUni(cred.UserName) ?? ""
            : "";
        string token = "";
        if (cred.CredentialBlob != IntPtr.Zero && cred.CredentialBlobSize > 0)
        {
            var bytes = new byte[cred.CredentialBlobSize];
            Marshal.Copy(cred.CredentialBlob, bytes, 0, (int)cred.CredentialBlobSize);
            token = Encoding.Unicode.GetString(bytes);
        }

        CredFree(ptr);
        return (username, token);
    }

    private void DeleteFromCredentialManager()
    {
        CredDeleteW(CredTarget, CRED_TYPE_GENERIC, 0);
    }
}
