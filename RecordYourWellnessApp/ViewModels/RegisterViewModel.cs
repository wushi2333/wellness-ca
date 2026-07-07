// Author: Liu Yu, Xia Zihang
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Models;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class RegisterViewModel : ObservableObject
{
    private readonly IAuthApi _authApi;
    private readonly INavigationService _navigation;

    [ObservableProperty] private string _username = "";
    [ObservableProperty] private string _password = "";
    [ObservableProperty] private string _email = "";
    [ObservableProperty] private bool _isBusy;
    [ObservableProperty] private string _errorMessage = "";
    [ObservableProperty] private string _successMessage = "";

    public event Action? RegistrationSucceeded;
    public event Action? GoBackRequested;

    public RegisterViewModel(IAuthApi authApi, INavigationService navigation)
    {
        _authApi = authApi;
        _navigation = navigation;
    }

    [RelayCommand]
    private async Task RegisterAsync()
    {
        if (string.IsNullOrWhiteSpace(Username) || Username.Length < 3)
        {
            ErrorMessage = "Username must be at least 3 characters.";
            return;
        }
        if (string.IsNullOrWhiteSpace(Password) || Password.Length < 8)
        {
            ErrorMessage = "Password must be at least 8 characters.";
            return;
        }
        if (!Password.Any(char.IsLetter) || !Password.Any(char.IsDigit))
        {
            ErrorMessage = "Password must contain at least one letter and one digit.";
            return;
        }

        IsBusy = true;
        ErrorMessage = "";
        SuccessMessage = "";

        try
        {
            var response = await _authApi.Register(new RegisterRequest
            {
                Username = Username,
                Password = Password,
                Email = string.IsNullOrWhiteSpace(Email) ? null : Email
            });

            SuccessMessage = response.Message;
            IsBusy = false;
            RegistrationSucceeded?.Invoke();
        }
        catch (Refit.ApiException ex)
        {
            var body = ex.Content;
            ErrorMessage = body?.Contains("exist") == true
                ? "Username or email already taken."
                : $"Error: {ex.Message}";
            IsBusy = false;
        }
        catch (Exception ex)
        {
            ErrorMessage = $"Connection failed: {ex.Message}";
            IsBusy = false;
        }
    }

    [RelayCommand]
    private void GoBack()
    {
        GoBackRequested?.Invoke();
    }
}
