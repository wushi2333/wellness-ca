using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Serilog;
using Refit;
using RecordYourWellnessApp.Services;
using RecordYourWellnessApp.Views;

namespace RecordYourWellnessApp;

internal static class Program
{
    public static IServiceProvider? ServiceProvider { get; private set; }

    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();

        Log.Logger = new LoggerConfiguration()
            .MinimumLevel.Information()
            .WriteTo.File(
                Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "RecordYourWellness", "logs", "app-.log"),
                rollingInterval: RollingInterval.Day)
            .CreateLogger();

        IHost? host = null;
        try
        {
            host = Host.CreateDefaultBuilder()
                .UseSerilog()
                .ConfigureServices((context, services) =>
                {
                    // ── Core services ──────────────────────────────
                    services.AddSingleton<ISessionService, SessionService>();
                    services.AddSingleton<WellnessAppContext>();
                    services.AddSingleton<INavigationService>(sp => sp.GetRequiredService<WellnessAppContext>());
                    services.AddSingleton<ISettingsService, SettingsService>();
                    services.AddSingleton<IAudioService, AudioService>();

                    // ── HTTP + Refit ───────────────────────────────
                    services.AddTransient<WellnessApiDelegatingHandler>();

                    services.AddRefitClient<IAuthApi>()
                        .ConfigureHttpClient(c => c.BaseAddress = new Uri(Constants.BaseUrl))
                        .AddHttpMessageHandler<WellnessApiDelegatingHandler>();

                    services.AddRefitClient<IRecordApi>()
                        .ConfigureHttpClient(c => c.BaseAddress = new Uri(Constants.BaseUrl))
                        .AddHttpMessageHandler<WellnessApiDelegatingHandler>();

                    services.AddRefitClient<IAgentApi>()
                        .ConfigureHttpClient(c => c.BaseAddress = new Uri(Constants.BaseUrl))
                        .AddHttpMessageHandler<WellnessApiDelegatingHandler>();

                    services.AddRefitClient<ICharacterApi>()
                        .ConfigureHttpClient(c => c.BaseAddress = new Uri(Constants.BaseUrl))
                        .AddHttpMessageHandler<WellnessApiDelegatingHandler>();

                    services.AddRefitClient<IProfileApi>()
                        .ConfigureHttpClient(c => c.BaseAddress = new Uri(Constants.BaseUrl))
                        .AddHttpMessageHandler<WellnessApiDelegatingHandler>();

                    // ── Forms ──────────────────────────────────────
                    services.AddTransient<LoginForm>();
                    services.AddTransient<RegisterForm>();
                    services.AddTransient<MainForm>(); // fresh instance each login (clean state)
                    services.AddTransient<SettingsPage>();
                    services.AddTransient<ChangePasswordForm>();

                    // ── Pages (UserControls) ───────────────────────
                    services.AddTransient<DashboardPage>();
                    services.AddTransient<SleepDetailPage>();
                    services.AddTransient<ExerciseDetailPage>();
                    services.AddTransient<AiRecommendationPage>();
                    services.AddTransient<CharacterChatPage>();
                    services.AddTransient<ProfilePage>();
                    services.AddTransient<GoogleAuthForm>();

                    // ── ViewModels ─────────────────────────────────
                    services.AddTransient<ViewModels.LoginViewModel>();
                    services.AddTransient<ViewModels.RegisterViewModel>();
                    services.AddTransient<ViewModels.MainViewModel>();
                    services.AddTransient<ViewModels.DashboardViewModel>();
                    services.AddTransient<ViewModels.SleepDetailViewModel>();
                    services.AddTransient<ViewModels.ExerciseDetailViewModel>();
                    services.AddTransient<ViewModels.CharacterChatViewModel>();
                })
                .Build();

            ServiceProvider = host.Services;

            // Load persisted settings (language) before any UI uses them, and prime the
            // localization layer so the first-rendered screen is already in the right language.
            var settings = host.Services.GetRequiredService<ISettingsService>();
            settings.Load();
            Services.Loc.Set(settings.Language);

            // Restore session by token presence only — mirrors Android's TokenManager.isLoggedIn.
            // An expired token is caught later by the 401 handler, which redirects to login.
            var sessionService = host.Services.GetRequiredService<ISessionService>();
            sessionService.TryRestoreSession();

            var appContext = host.Services.GetRequiredService<WellnessAppContext>();
            appContext.Start();
            Application.Run(appContext);
        }
        catch (Exception ex)
        {
            Log.Fatal(ex, "Application startup failed");
            MessageBox.Show($"Startup error: {ex.Message}", "Error",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            host?.Dispose();
            Log.CloseAndFlush();
        }
    }
}
