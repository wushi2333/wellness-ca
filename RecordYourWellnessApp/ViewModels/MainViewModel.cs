using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly ISessionService _session;

    [ObservableProperty] private string _greeting = "";
    [ObservableProperty] private string _username = "";

    public MainViewModel(ISessionService session)
    {
        _session = session;
        RefreshIdentity();
    }

    /// <summary>Recompute greeting/username from the session. Called on MainForm show
    /// so the greeting is correct even if the app has been open past midnight (Android
    /// recomputes the greeting in HomeFragment.onResume).</summary>
    public void RefreshIdentity()
    {
        Username = _session.Username;
        Greeting = DateTime.Now.Hour switch
        {
            >= 5 and < 12 => Loc.T("greeting.morning"),
            >= 12 and < 17 => Loc.T("greeting.afternoon"),
            _ => Loc.T("greeting.evening")
        };
    }

    [RelayCommand]
    private void Logout()
    {
        var result = MessageBox.Show("Are you sure you want to log out?",
            "Logout", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
        if (result == DialogResult.Yes)
        {
            _session.Logout(); // AppContext listens and navigates to login
        }
    }
}
