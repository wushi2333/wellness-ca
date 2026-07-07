using System.Text.Json;

namespace RecordYourWellnessApp.Services;

public interface ISettingsService
{
    string Language { get; set; }
    void Save();
    void Load();
}

public class SettingsService : ISettingsService
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "RecordYourWellness", "settings.json");

    public string Language { get; set; } = "en";

    public void Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var json = File.ReadAllText(FilePath);
                var data = JsonSerializer.Deserialize<SettingsData>(json);
                if (data != null)
                {
                    Language = data.Language;
                }
            }
        }
        catch { }
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            var data = new SettingsData
            {
                Language = Language
            };
            File.WriteAllText(FilePath, JsonSerializer.Serialize(data));
        }
        catch { }
    }

    private class SettingsData
    {
        public string Language { get; set; } = "en";
    }
}
