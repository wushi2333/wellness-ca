// Author: Xia Zihang
using Microsoft.Extensions.DependencyInjection;

namespace RecordYourWellnessApp.Services;

/// <summary>Shell navigation: swaps between LoginForm and MainForm, mirroring the
/// Android app's clear login ⇄ main transitions. Both forms are transient, so each
/// transition starts from a clean state (fresh data load) — same behaviour as the
/// Android activities recreated on logout. The context also listens to the session
/// service so a 401 (expired token) anywhere redirects to login, just like
/// ApiErrorHandler in the Android app.</summary>
public interface INavigationService
{
    void GoToMain();
    void GoToLogin();
    Form? CurrentForm { get; }
}

public class WellnessAppContext : ApplicationContext, INavigationService
{
    private readonly IServiceProvider _sp;
    private readonly ISessionService _session;
    private Form? _current;
    private bool _exiting;

    public Form? CurrentForm => _current;

    public WellnessAppContext(IServiceProvider sp, ISessionService session)
    {
        _sp = sp;
        _session = session;
        _session.LoggedOut += OnLoggedOut;
        _session.SessionExpired += OnSessionExpired;
    }

    /// <summary>Show the entry form (MainForm if a session was restored, else LoginForm).</summary>
    public void Start()
    {
        if (_current != null) return;
        Show(_session.IsLoggedIn ? _sp.GetRequiredService<Views.MainForm>() : _sp.GetRequiredService<Views.LoginForm>());
    }

    public void GoToMain()
    {
        if (_exiting) return;
        CloseModalDialogs();
        SwapTo(_sp.GetRequiredService<Views.MainForm>());
    }

    public void GoToLogin()
    {
        if (_exiting) return;
        CloseModalDialogs();
        SwapTo(_sp.GetRequiredService<Views.LoginForm>());
    }

    private void Show(Form form)
    {
        _current = form;
        form.FormClosed += OnCurrentClosed;
        form.Show();
    }

    private void SwapTo(Form next)
    {
        var prev = _current;
        if (prev != null)
        {
            prev.FormClosed -= OnCurrentClosed; // we are driving the close, not the user
            try { prev.Close(); } catch { }     // Close() disposes a non-modal form
        }
        Show(next);
    }

    private void OnLoggedOut() => OnAuthChanged(expired: false);
    private void OnSessionExpired() => OnAuthChanged(expired: true);

    private void OnAuthChanged(bool expired)
    {
        if (_exiting) return;
        var f = _current;
        if (f == null || f.IsDisposed) { RunTransition(expired); return; }
        // Fire on the UI thread, after any in-flight modal interaction unwinds.
        try { f.BeginInvoke(() => RunTransition(expired)); }
        catch { RunTransition(expired); }
    }

    private void RunTransition(bool expired)
    {
        if (_exiting) return;
        if (expired)
            MessageBox.Show("Your session has expired. Please sign in again.",
                "Session Expired", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        GoToLogin();
    }

    /// <summary>Dismiss any modal dialogs (Settings/Profile/Add/Edit) open over the shell
    /// before swapping it — prevents "cannot close form showing a modal" errors.</summary>
    private void CloseModalDialogs()
    {
        foreach (var f in Application.OpenForms.Cast<Form>().ToArray())
        {
            if (f.Modal && f != _current)
            {
                try { f.DialogResult = DialogResult.Cancel; f.Close(); } catch { }
            }
        }
    }

    private void OnCurrentClosed(object? sender, EventArgs e)
    {
        if (_exiting) return;
        _exiting = true;
        _session.LoggedOut -= OnLoggedOut;
        _session.SessionExpired -= OnSessionExpired;
        ExitThread();
    }
}
