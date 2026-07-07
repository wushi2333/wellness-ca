using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Models;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class DashboardViewModel : ObservableObject
{
    private readonly IRecordApi _recordApi;
    private readonly IAgentApi _agentApi;
    private readonly ISessionService _session;

    public string Username => _session.Username;
    [ObservableProperty] private string _greeting = "";
    [ObservableProperty] private string _dateString = "";
    [ObservableProperty] private double _avgSleepHours;
    [ObservableProperty] private double _bestNight;
    [ObservableProperty] private double _moodAvg;
    [ObservableProperty] private double _sleepTrend;
    [ObservableProperty] private string _sleepTrendDir = "";
    [ObservableProperty] private double _avgExerciseMinutes;
    [ObservableProperty] private int _activeDays;
    [ObservableProperty] private int _totalExerciseMinutes;
    [ObservableProperty] private string _latestTip = "Stay hydrated and get some morning sunlight!";
    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private bool _hasError;
    [ObservableProperty] private string _errorMessage = "";
    [ObservableProperty] private bool _hasData;

    public List<double> SleepWeekData { get; private set; } = new();
    public List<int> ExerciseWeekData { get; private set; } = new();
    public List<string> DayLabels { get; private set; } = new();

    public event Action? DataRefreshed;

    private static readonly string[] Tips =
    {
        "Stay hydrated and get some morning sunlight!",
        "A 10-minute walk after meals steadies blood sugar.",
        "Keep a consistent bedtime — your body loves rhythm.",
        "Stretch for two minutes every hour you sit.",
        "Deep breathing for 60s lowers stress measurably.",
        "Dim screens an hour before bed for deeper sleep.",
    };

    public DashboardViewModel(IRecordApi recordApi, IAgentApi agentApi, ISessionService session)
    {
        _recordApi = recordApi; _agentApi = agentApi; _session = session;
        RefreshDateTime();
    }

    private void RefreshDateTime()
    {
        Greeting = DateTime.Now.Hour switch { >= 5 and < 12 => Loc.T("greeting.morning"), >= 12 and < 17 => Loc.T("greeting.afternoon"), _ => Loc.T("greeting.evening") };
        DateString = DateTime.Now.ToString("dddd, MMMM d, yyyy", CultureInfo.InvariantCulture);
    }

    [RelayCommand]
    public async Task LoadDataAsync()
    {
        IsLoading = true;
        HasError = false;
        ErrorMessage = "";
        RefreshDateTime(); // correct even if the app has been open past midnight

        try
        {
            var data = await _recordApi.GetRecords(0, 90);
            var dailies = data.Content;
            var today = DateTime.Today;
            var monday = today.AddDays(-(int)today.DayOfWeek + 1);

            var thisWeek = dailies.Where(d => DateTime.TryParse(d.RecordDate, out var dt) && dt >= monday && dt <= monday.AddDays(6)).ToList();
            var prevWeek = dailies.Where(d => DateTime.TryParse(d.RecordDate, out var dt) && dt >= monday.AddDays(-7) && dt < monday).ToList();

            // Sleep data
            var sleepData = new double[7];
            var labels = new string[7];
            double sleepTotal = 0, best = 0, moodTotal = 0; int sleepCount = 0, moodCount = 0;
            for (int i = 0; i < 7; i++)
            {
                var d = thisWeek.FirstOrDefault(w => DateTime.TryParse(w.RecordDate, out var dt) && dt == monday.AddDays(i));
                sleepData[i] = d?.Sleep?.SleepHours ?? 0;
                labels[i] = monday.AddDays(i).ToString("ddd", CultureInfo.InvariantCulture);
                if (d?.Sleep != null) { sleepTotal += d.Sleep.SleepHours; sleepCount++; if (d.Sleep.SleepHours > best) best = d.Sleep.SleepHours; }
                if (d?.Sleep?.MoodScore > 0) { moodTotal += d.Sleep.MoodScore.Value; moodCount++; }
            }
            AvgSleepHours = sleepCount > 0 ? Math.Round(sleepTotal / sleepCount, 1) : 0;
            BestNight = best;
            MoodAvg = moodCount > 0 ? Math.Round(moodTotal / moodCount, 1) : 0;

            var prevSleepAvg = prevWeek.Where(d => d.Sleep != null).Select(d => d.Sleep!.SleepHours).DefaultIfEmpty(0).Average();
            SleepTrend = prevSleepAvg > 0 ? Math.Round(AvgSleepHours - prevSleepAvg, 1) : 0;
            SleepTrendDir = SleepTrend > 0 ? "↑" : SleepTrend < 0 ? "↓" : "→";

            // Exercise data
            var exData = new int[7];
            int exTotal = 0, activeDays = 0;
            for (int i = 0; i < 7; i++)
            {
                var d = thisWeek.FirstOrDefault(w => DateTime.TryParse(w.RecordDate, out var dt) && dt == monday.AddDays(i));
                var mins = d?.Exercises.Sum(e => e.ExerciseDuration) ?? 0;
                exData[i] = mins; exTotal += mins;
                if (mins > 0) activeDays++;
            }
            AvgExerciseMinutes = activeDays > 0 ? Math.Round((double)exTotal / activeDays, 0) : 0;
            ActiveDays = activeDays;
            TotalExerciseMinutes = exTotal;

            SleepWeekData = sleepData.ToList();
            ExerciseWeekData = exData.ToList();
            DayLabels = labels.ToList();
            HasData = sleepCount > 0 || activeDays > 0;

            LatestTip = Tips[DateTime.Now.DayOfYear % Tips.Length];
        }
        catch (Refit.ApiException ex)
        {
            // 401 is handled centrally (redirect to login); stay silent so the redirect is clean.
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
            {
                HasError = true;
                ErrorMessage = $"Could not load your data ({ex.StatusCode}).";
            }
        }
        catch (Exception ex)
        {
            HasError = true;
            ErrorMessage = $"Network error: {ex.Message}";
        }
        finally
        {
            IsLoading = false;
            DataRefreshed?.Invoke();
        }
    }
}
