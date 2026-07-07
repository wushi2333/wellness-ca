using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Models;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class LoginViewModel : ObservableObject
{
    private readonly IAuthApi _authApi;
    private readonly ISessionService _session;
    private readonly INavigationService _navigation;

    [ObservableProperty]
    private string _username = "";

    [ObservableProperty]
    private string _password = "";

    [ObservableProperty]
    private bool _isBusy;

    [ObservableProperty]
    private string _errorMessage = "";

    public LoginViewModel(IAuthApi authApi, ISessionService session,
        INavigationService navigation)
    {
        _authApi = authApi;
        _session = session;
        _navigation = navigation;
    }

    [RelayCommand]
    private async Task LoginAsync()
    {
        if (string.IsNullOrWhiteSpace(Username) || string.IsNullOrWhiteSpace(Password))
        {
            ErrorMessage = "Please enter username and password.";
            return;
        }

        IsBusy = true;
        ErrorMessage = "";

        try
        {
            var response = await _authApi.Login(new LoginRequest
            {
                Username = Username,
                Password = Password
            });

            _session.Login(response);
        }
        catch (Refit.ApiException ex)
        {
            ErrorMessage = ex.StatusCode switch
            {
                System.Net.HttpStatusCode.BadRequest => "Incorrect username or password.",
                _ => $"Server error: {ex.StatusCode}"
            };
            IsBusy = false;
            return;
        }
        catch (Exception ex)
        {
            ErrorMessage = $"Connection failed: {ex.Message}";
            IsBusy = false;
            return;
        }

        IsBusy = false;
        LoginSucceeded?.Invoke();
    }

    /// <summary>Fired on UI thread after successful login. The View calls NavigationService from here.</summary>
    public event Action? LoginSucceeded;
}
