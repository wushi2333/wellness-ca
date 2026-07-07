// Author: Wang Songyu, Xia Zihang
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class ExerciseDetailViewModel : ObservableObject
{
    private readonly IRecordApi _api;
    [ObservableProperty] private double _avgMinutes;
    [ObservableProperty] private int _activeDays;
    [ObservableProperty] private List<int> _weekData = new(7);
    [ObservableProperty] private List<string> _weekLabels = new(7);
    [ObservableProperty] private DateTime _weekStart = DateTime.Today.AddDays(-(int)DateTime.Today.DayOfWeek + 1);
    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private bool _hasError;
    [ObservableProperty] private string _errorMessage = "";
    [ObservableProperty] private bool _hasData;

    /// <summary>Activity type → total minutes for the displayed week (drives the pie chart).</summary>
    public Dictionary<string, int> ActivityBreakdown { get; private set; } = new();

    public event Action? DataRefreshed;

    public ExerciseDetailViewModel(IRecordApi api) { _api = api; }

    public string WeekRangeText => FormattableString.Invariant($"{WeekStart:MMM d} – {WeekStart.AddDays(6):MMM d, yyyy}");

    [RelayCommand]
    public async Task LoadAsync()
    {
        IsLoading = true;
        HasError = false;
        ErrorMessage = "";
        try
        {
            var data = await _api.GetRecords(0, 90);
            var weekData = new int[7];
            var labels = new string[7];
            int total = 0, activeDays = 0;
            var breakdown = new Dictionary<string, int>();

            for (int i = 0; i < 7; i++)
            {
                var date = WeekStart.AddDays(i);
                labels[i] = date.ToString("ddd", CultureInfo.InvariantCulture);
                var d = data.Content.FirstOrDefault(x => DateTime.TryParse(x.RecordDate, out var dt) && dt == date);
                var mins = d?.Exercises.Sum(e => e.ExerciseDuration) ?? 0;
                weekData[i] = mins;
                total += mins;
                if (mins > 0) activeDays++;
                // accumulate per-activity minutes for the pie chart
                if (d != null)
                    foreach (var ex in d.Exercises)
                    {
                        var name = string.IsNullOrWhiteSpace(ex.ExerciseActivity) ? "Other" : ex.ExerciseActivity;
                        breakdown[name] = breakdown.TryGetValue(name, out var v) ? v + ex.ExerciseDuration : ex.ExerciseDuration;
                    }
            }

            WeekData = weekData.ToList();
            WeekLabels = labels.ToList();
            AvgMinutes = activeDays > 0 ? Math.Round((double)total / activeDays, 0) : 0;
            ActiveDays = activeDays;
            ActivityBreakdown = breakdown;
            HasData = activeDays > 0;
            OnPropertyChanged(nameof(ActivityBreakdown));
            OnPropertyChanged(nameof(WeekRangeText));
        }
        catch (Refit.ApiException ex)
        {
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
            { HasError = true; ErrorMessage = $"Could not load exercise data ({ex.StatusCode})."; }
        }
        catch (Exception ex)
        {
            HasError = true;
            ErrorMessage = $"Network error: {ex.Message}";
        }
        finally { IsLoading = false; DataRefreshed?.Invoke(); }
    }

    [RelayCommand] private void PrevWeek() { WeekStart = WeekStart.AddDays(-7); _ = LoadAsync(); }
    [RelayCommand] private void NextWeek() { WeekStart = WeekStart.AddDays(7); _ = LoadAsync(); }
}
