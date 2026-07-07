using Microsoft.Web.WebView2.WinForms;
using Microsoft.Web.WebView2.Core;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.Models;
using Newtonsoft.Json.Linq;
using System.Net;
using System.Text;

namespace RecordYourWellnessApp.Views;

public class GoogleAuthForm : Form
{
    private readonly WebView2 _webView;
    private readonly IAuthApi _authApi;
    private readonly TaskCompletionSource<LoginResponse?> _tcs;
    private readonly HttpListener _httpListener;
    private static readonly int Port = 42387;
    private static readonly string RedirectPath = "/cb/";
    private static string RedirectUri => $"http://localhost:{Port}{RedirectPath}";
    private const string ClientId = "375016829980-l1gpslqj0cltlc5aqf2f403c52oepeg7.apps.googleusercontent.com";

    public LoginResponse? Result { get; private set; }

    public GoogleAuthForm(IAuthApi authApi)
    {
        _authApi = authApi; _tcs = new TaskCompletionSource<LoginResponse?>();
        _httpListener = new HttpListener();
        _httpListener.Prefixes.Add($"http://localhost:{Port}{RedirectPath}");
        this.Text = "Sign in with Google"; this.ClientSize = new Size(500, 640);
        this.StartPosition = FormStartPosition.CenterParent; this.BackColor = Theme.Background;

        var label = new Label { Text = "Loading...", Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleCenter, Font = Theme.Font(12f), ForeColor = Theme.TextSecondary };
        Controls.Add(label);

        _webView = new WebView2 { Dock = DockStyle.Fill, Visible = false };
        Controls.Add(_webView);

        this.Load += async (_, _) => {
            try {
                _httpListener.Start();
                _ = ListenLoop();
                await _webView.EnsureCoreWebView2Async();
                var authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=" + ClientId +
                    "&redirect_uri=" + Uri.EscapeDataString(RedirectUri) +
                    "&response_type=code&scope=email%20profile%20openid" +
                    "&access_type=offline&prompt=select_account";
                _webView.CoreWebView2.Navigate(authUrl);
                label.Visible = false; _webView.Visible = true;
            } catch (Exception ex) { _tcs.TrySetResult(null); Close(); }
        };
    }

    private async Task ListenLoop()
    {
        try {
            while (true) {
                var ctx = await _httpListener.GetContextAsync();
                var code = ctx.Request.QueryString["code"];
                var error = ctx.Request.QueryString["error"];

                string html;
                if (!string.IsNullOrEmpty(error)) {
                    html = OkHtml("Sign-in cancelled", false);
                    BeginInvoke(() => CloseWithResult(null));
                } else if (!string.IsNullOrEmpty(code)) {
                    html = OkHtml("Signed in! You can close this window.", true);
                    await ServeAndProcess(ctx, html, code);
                    return;
                } else {
                    html = "<html><body><h2>No auth code</h2></body></html>";
                    await Serve(ctx, html);
                }
            }
        } catch (HttpListenerException) { } catch (ObjectDisposedException) { }
    }

    private async Task ServeAndProcess(HttpListenerContext ctx, string html, string authCode)
    {
        var buffer = Encoding.UTF8.GetBytes(html);
        ctx.Response.ContentType = "text/html; charset=utf-8";
        ctx.Response.ContentLength64 = buffer.Length;
        await ctx.Response.OutputStream.WriteAsync(buffer);
        ctx.Response.OutputStream.Close();

        BeginInvoke(async () => {
            _webView.Visible = false;
            await ProcessGoogleLogin(authCode);
        });
    }

    private static async Task Serve(HttpListenerContext ctx, string html)
    {
        var buffer = Encoding.UTF8.GetBytes(html);
        ctx.Response.ContentType = "text/html; charset=utf-8";
        ctx.Response.ContentLength64 = buffer.Length;
        await ctx.Response.OutputStream.WriteAsync(buffer);
        ctx.Response.OutputStream.Close();
    }

    private static string OkHtml(string msg, bool success) =>
        $"<html><body style='font-family:Segoe UI;text-align:center;padding-top:60px;background:#F8FAFC;'><div style='font-size:48px;'>{(success ? "✓" : "✗")}</div><h2 style='color:#1E293B'>{msg}</h2></body></html>";

    /// <summary>Core login flow: first call detects conflict/newUser, second confirms.</summary>
    private async Task ProcessGoogleLogin(string authCode)
    {
        try {
            // ── Step 1: first call with authCode ────────────────────
            string json1;
            try {
                json1 = await _authApi.GoogleLogin(new GoogleAuthRequest { AuthCode = authCode, RedirectUri = RedirectUri, Username = "" });
            } catch (Refit.ApiException ex) {
                // 409 = conflict, 400 may contain newUser
                json1 = ex.Content ?? "{}";
            }
            var obj = JObject.Parse(json1);

            // Conflict?
            if (obj.Value<bool?>("conflict") == true) {
                var existingUser = obj.Value<string>("existingUsername") ?? "";
                var email = obj.Value<string>("email") ?? "";
                var idToken = obj.Value<string>("idToken") ?? "";
                await HandleConflict(idToken, existingUser, email);
                return;
            }

            // New user?
            if (obj.Value<bool?>("newUser") == true) {
                var email = obj.Value<string>("email") ?? "";
                var suggested = obj.Value<string>("suggestedUsername") ?? "user";
                var idToken = obj.Value<string>("idToken") ?? "";
                await HandleNewUser(idToken, email, suggested);
                return;
            }

            // Direct login
            CloseWithResult(MakeLoginResponse(obj));
        } catch (Refit.ApiException ex) {
            MessageBox.Show(this, $"Server error ({ex.StatusCode}): {ex.Content}", "Sign In Failed",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            CloseWithResult(null);
        } catch (Exception ex) {
            MessageBox.Show(this, ex.Message, "Sign In Failed",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            CloseWithResult(null);
        }
    }

    // ── Conflict: email already linked to existing account ──────────
    private async Task HandleConflict(string idToken, string existingUsername, string email)
    {
        var result = MessageBox.Show(this,
            $"\"{email}\" is already linked to username \"{existingUsername}\".\n\nLog in as \"{existingUsername}\"?",
            "Account Already Exists", MessageBoxButtons.YesNo, MessageBoxIcon.Question);

        if (result != DialogResult.Yes) { CloseWithResult(null); return; }

        try {
            var json2 = await _authApi.GoogleLogin(new GoogleAuthRequest { IdToken = idToken, Username = existingUsername });
            CloseWithResult(MakeLoginResponse(JObject.Parse(json2)));
        } catch (Refit.ApiException ex) {
            MessageBox.Show(this, $"Server error ({ex.StatusCode}): {ex.Content}", "Sign In Failed",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            CloseWithResult(null);
        } catch (Exception ex) {
            MessageBox.Show(this, ex.Message, "Sign In Failed",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            CloseWithResult(null);
        }
    }

    // ── New user: choose username ───────────────────────────────────
    private async Task HandleNewUser(string idToken, string email, string suggested)
    {
        var cur = suggested;
        while (true) {
            var chosen = await ShowUsernameDialog(email, cur);
            if (chosen == null) { CloseWithResult(null); return; }

            try {
                var json2 = await _authApi.GoogleLogin(new GoogleAuthRequest { IdToken = idToken, Username = chosen });
                var obj = JObject.Parse(json2);

                if (obj.Value<bool?>("newUser") == true && obj.Value<string>("error") == "username_taken") {
                    cur = obj.Value<string>("suggestedUsername") ?? chosen + "1";
                    continue;
                }
                CloseWithResult(MakeLoginResponse(obj));
                return;
            } catch (Refit.ApiException ex) {
                MessageBox.Show(this, $"Server error ({ex.StatusCode}): {ex.Content}", "Error",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                CloseWithResult(null); return;
            } catch (Exception ex) {
                MessageBox.Show(this, ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                CloseWithResult(null); return;
            }
        }
    }

    private static LoginResponse MakeLoginResponse(JObject obj) => new()
    {
        AccessToken = obj.Value<string>("accessToken") ?? "",
        TokenType = obj.Value<string>("tokenType") ?? "bearer",
        UserId = obj.Value<long?>("userId") ?? 0,
        Username = obj.Value<string>("username") ?? ""
    };

    private void CloseWithResult(LoginResponse? r)
    {
        Result = r;
        _tcs.TrySetResult(r);
        BeginInvoke(Close);
    }

    // ── Username dialog ─────────────────────────────────────────────
    private static Task<string?> ShowUsernameDialog(string email, string suggestion)
    {
        var tcs = new TaskCompletionSource<string?>();
        var f = new Form { Text = "Choose Username", ClientSize = new Size(400, 220),
            StartPosition = FormStartPosition.CenterParent, FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false, BackColor = Theme.Background };
        var card = new Panel { Size = new Size(360, 180), Location = new Point(20, 20), BackColor = Theme.Surface };
        f.Controls.Add(card);
        card.Controls.Add(new Label { Text = "Google Account", Font = Theme.Font(10f, FontStyle.Bold), ForeColor = Theme.TextPrimary, Location = new Point(24, 16), Size = new Size(310, 20) });
        card.Controls.Add(new Label { Text = email, Font = Theme.Font(10f), ForeColor = Theme.TextSecondary, Location = new Point(24, 38), Size = new Size(310, 20) });
        card.Controls.Add(new Label { Text = "Choose username:", Font = Theme.Font(10f, FontStyle.Bold), ForeColor = Theme.TextPrimary, Location = new Point(24, 70), Size = new Size(200, 24) });
        var box = new TextBox { Text = suggestion, Location = new Point(24, 96), Size = new Size(310, 32), Font = Theme.Font(11f) };
        card.Controls.Add(box);
        var err = new Label { Location = new Point(24, 132), Size = new Size(310, 20), ForeColor = Theme.Error, Visible = false }; card.Controls.Add(err);
        var ok = new Button { Text = "Create", Location = new Point(180, 142), Size = new Size(154, 32), FlatStyle = FlatStyle.Flat, BackColor = Theme.Primary, ForeColor = Color.White, Font = Theme.Font(10f, FontStyle.Bold), Cursor = Cursors.Hand }; ok.FlatAppearance.BorderSize = 0;
        ok.Click += (_, _) => { var c = box.Text.Trim(); if (c.Length < 3) { err.Text = "Min 3 chars"; err.Visible = true; return; } f.DialogResult = DialogResult.OK; f.Close(); }; card.Controls.Add(ok);
        var cancel = new Button { Text = "Cancel", Location = new Point(24, 142), Size = new Size(80, 32), FlatStyle = FlatStyle.Flat, BackColor = Theme.SurfaceVariant, ForeColor = Theme.TextSecondary, Font = Theme.Font(10f), Cursor = Cursors.Hand }; cancel.FlatAppearance.BorderSize = 0;
        cancel.Click += (_, _) => { f.DialogResult = DialogResult.Cancel; f.Close(); }; card.Controls.Add(cancel);
        f.FormClosed += (_, _) => tcs.TrySetResult(f.DialogResult == DialogResult.OK ? box.Text.Trim() : null);
        f.ShowDialog(); return tcs.Task;
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        base.OnFormClosing(e);
        try { _httpListener.Stop(); } catch { }
        try { _httpListener.Close(); } catch { }
        if (!_tcs.Task.IsCompleted) _tcs.TrySetResult(null);
    }

    public Task<LoginResponse?> WaitForResultAsync() => _tcs.Task;
}
