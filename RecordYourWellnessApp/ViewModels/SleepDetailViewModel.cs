// Author: Wang Songyu, Xia Zihang
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using RecordYourWellnessApp.Services;

namespace RecordYourWellnessApp.ViewModels;

public partial class SleepDetailViewModel : ObservableObject
{
    private readonly IRecordApi _api;
    [ObservableProperty] private double _avgHours;
    [ObservableProperty] private double _bestNight;
    [ObservableProperty] private double _avgMood;
    [ObservableProperty] private List<double> _weekData = new(7);
    [ObservableProperty] private List<string> _weekLabels = new(7);
    [ObservableProperty] private DateTime _weekStart = DateTime.Today.AddDays(-(int)DateTime.Today.DayOfWeek + 1);
    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private bool _hasError;
    [ObservableProperty] private string _errorMessage = "";
    [ObservableProperty] private bool _hasData;

    public event Action? DataRefreshed;

    public SleepDetailViewModel(IRecordApi api) { _api = api; }

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
            var weekData = new double[7];
            var labels = new string[7];
            double total = 0, max = 0, moodTotal = 0;
            int count = 0, moodCount = 0;

            for (int i = 0; i < 7; i++)
            {
                var date = WeekStart.AddDays(i);
                labels[i] = date.ToString("ddd", CultureInfo.InvariantCulture);
                var d = data.Content.FirstOrDefault(x => DateTime.TryParse(x.RecordDate, out var dt) && dt == date);
                if (d?.Sleep != null)
                {
                    var h = d.Sleep.SleepHours;
                    weekData[i] = h;
                    total += h; count++;
                    if (h > max) max = h;
                    if (d.Sleep.MoodScore > 0) { moodTotal += d.Sleep.MoodScore.Value; moodCount++; }
                }
            }

            WeekData = weekData.ToList();
            WeekLabels = labels.ToList();
            AvgHours = count > 0 ? Math.Round(total / count, 1) : 0;
            BestNight = max;
            AvgMood = moodCount > 0 ? Math.Round(moodTotal / moodCount, 1) : 0;
            HasData = count > 0;
            OnPropertyChanged(nameof(WeekRangeText));
        }
        catch (Refit.ApiException ex)
        {
            if (ex.StatusCode != System.Net.HttpStatusCode.Unauthorized)
            { HasError = true; ErrorMessage = $"Could not load sleep data ({ex.StatusCode})."; }
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
