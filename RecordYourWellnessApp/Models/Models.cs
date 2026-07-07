// Author: Xia Zihang
using System.Text.Json.Serialization;

namespace RecordYourWellnessApp.Models;

// ── Auth ──────────────────────────────────────────────────────────────

public class LoginRequest
{
    [JsonPropertyName("username")]
    public string Username { get; set; } = "";

    [JsonPropertyName("password")]
    public string Password { get; set; } = "";
}

public class LoginResponse
{
    [JsonPropertyName("accessToken")]
    public string AccessToken { get; set; } = "";

    [JsonPropertyName("tokenType")]
    public string TokenType { get; set; } = "bearer";

    [JsonPropertyName("userId")]
    public long UserId { get; set; }

    [JsonPropertyName("username")]
    public string Username { get; set; } = "";
}

public class RegisterRequest
{
    [JsonPropertyName("username")]
    public string Username { get; set; } = "";

    [JsonPropertyName("password")]
    public string Password { get; set; } = "";

    [JsonPropertyName("email")]
    public string? Email { get; set; }
}

public class RegisterResponse
{
    [JsonPropertyName("message")]
    public string Message { get; set; } = "";

    [JsonPropertyName("userId")]
    public long UserId { get; set; }
}

public class GoogleAuthRequest
{
    [JsonPropertyName("idToken")]
    public string? IdToken { get; set; }

    [JsonPropertyName("authCode")]
    public string? AuthCode { get; set; }

    [JsonPropertyName("redirectUri")]
    public string? RedirectUri { get; set; }

    [JsonPropertyName("username")]
    public string Username { get; set; } = "";
}

// ── Wellness Records ──────────────────────────────────────────────────

public class SleepRecordRequest
{
    [JsonPropertyName("sleepHours")]
    public double SleepHours { get; set; }

    [JsonPropertyName("sleepTime")]
    public string? SleepTime { get; set; }

    [JsonPropertyName("wakeTime")]
    public string? WakeTime { get; set; }

    [JsonPropertyName("moodScore")]
    public int? MoodScore { get; set; }

    [JsonPropertyName("recordDate")]
    public string RecordDate { get; set; } = "";

    [JsonPropertyName("notes")]
    public string? Notes { get; set; }
}

public class ExerciseRecordRequest
{
    [JsonPropertyName("exerciseActivity")]
    public string ExerciseActivity { get; set; } = "";

    [JsonPropertyName("exerciseDuration")]
    public int ExerciseDuration { get; set; }

    [JsonPropertyName("recordDate")]
    public string RecordDate { get; set; } = "";

    [JsonPropertyName("notes")]
    public string? Notes { get; set; }
}

public class CreateResponse
{
    [JsonPropertyName("message")]
    public string Message { get; set; } = "";

    [JsonPropertyName("id")]
    public long Id { get; set; }
}

// ── Daily Wellness ────────────────────────────────────────────────────

public class PagedResponse<T>
{
    [JsonPropertyName("content")]
    public List<T> Content { get; set; } = new();

    [JsonPropertyName("page")]
    public int Page { get; set; }

    [JsonPropertyName("size")]
    public int Size { get; set; }

    [JsonPropertyName("totalElements")]
    public long TotalElements { get; set; }

    [JsonPropertyName("totalPages")]
    public int TotalPages { get; set; }

    [JsonPropertyName("last")]
    public bool Last { get; set; }
}

public class DailyWellness
{
    [JsonPropertyName("dailyRecordId")]
    public long DailyRecordId { get; set; }

    [JsonPropertyName("recordDate")]
    public string RecordDate { get; set; } = "";

    [JsonPropertyName("sleep")]
    public SleepRecord? Sleep { get; set; }

    [JsonPropertyName("exercises")]
    public List<ExerciseRecord> Exercises { get; set; } = new();
}

public class SleepRecord
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("sleepHours")]
    public double SleepHours { get; set; }

    [JsonPropertyName("sleepTime")]
    public string? SleepTime { get; set; }

    [JsonPropertyName("wakeTime")]
    public string? WakeTime { get; set; }

    [JsonPropertyName("moodScore")]
    public int? MoodScore { get; set; }

    [JsonPropertyName("notes")]
    public string? Notes { get; set; }
}

public class ExerciseRecord
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("exerciseActivity")]
    public string ExerciseActivity { get; set; } = "";

    [JsonPropertyName("exerciseDuration")]
    public int ExerciseDuration { get; set; }

    [JsonPropertyName("notes")]
    public string? Notes { get; set; }
}

// ── Agent ─────────────────────────────────────────────────────────────

public class AgentRecommendation
{
    [JsonPropertyName("recommendation")]
    public string Recommendation { get; set; } = "";

    [JsonPropertyName("evidence")]
    public List<ToolTrace> Evidence { get; set; } = new();

    [JsonPropertyName("iterations")]
    public int Iterations { get; set; }

    [JsonPropertyName("saved_id")]
    public int SavedId { get; set; }
}

public class AgentHistoryItem
{
    [JsonPropertyName("id")]
    public int Id { get; set; }

    [JsonPropertyName("content")]
    public string Content { get; set; } = "";

    [JsonPropertyName("evidence")]
    public object? Evidence { get; set; }

    [JsonPropertyName("iterations")]
    public int Iterations { get; set; }

    [JsonPropertyName("created_at")]
    public string CreatedAt { get; set; } = "";
}

public class ToolTrace
{
    [JsonPropertyName("name")]
    public string Name { get; set; } = "";

    [JsonPropertyName("summary")]
    public string Summary { get; set; } = "";
}

// ── Character ─────────────────────────────────────────────────────────

public class CharacterSession
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("title")]
    public string Title { get; set; } = "";

    [JsonPropertyName("mode")]
    public string Mode { get; set; } = "";

    [JsonPropertyName("messageCount")]
    public int MessageCount { get; set; }

    [JsonPropertyName("updatedAt")]
    public string UpdatedAt { get; set; } = "";
}

public class CharacterMessage
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("role")]
    public string Role { get; set; } = "";

    [JsonPropertyName("content")]
    public string Content { get; set; } = "";

    [JsonPropertyName("emotion")]
    public string? Emotion { get; set; }

    [JsonPropertyName("createdAt")]
    public string CreatedAt { get; set; } = "";

    [JsonPropertyName("tools")]
    public List<string>? Tools { get; set; }
}

public class CharacterChatRequest
{
    [JsonPropertyName("message")]
    public string Message { get; set; } = "";

    [JsonPropertyName("mode")]
    public string Mode { get; set; } = "chat";

    [JsonPropertyName("sessionId")]
    public long? SessionId { get; set; }
}

public class CharacterChatResponse
{
    [JsonPropertyName("reply")]
    public string Reply { get; set; } = "";

    [JsonPropertyName("emotion")]
    public string? Emotion { get; set; }

    [JsonPropertyName("sessionId")]
    public long SessionId { get; set; }

    [JsonPropertyName("intent")]
    public Dictionary<string, object?>? Intent { get; set; }

    [JsonPropertyName("tools")]
    public List<string>? Tools { get; set; }
}

public class CreateSessionResponse
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("title")]
    public string Title { get; set; } = "";

    [JsonPropertyName("mode")]
    public string Mode { get; set; } = "";
}

// ── Profile ───────────────────────────────────────────────────────────

public class UserProfileData
{
    [JsonPropertyName("userId")]
    public long UserId { get; set; }

    [JsonPropertyName("username")]
    public string Username { get; set; } = "";

    [JsonPropertyName("email")]
    public string? Email { get; set; }

    [JsonPropertyName("provider")]
    public string? Provider { get; set; }

    [JsonPropertyName("avatarUrl")]
    public string? AvatarUrl { get; set; }

    [JsonPropertyName("nickname")]
    public string? Nickname { get; set; }

    [JsonPropertyName("heightCm")]
    public int? HeightCm { get; set; }

    [JsonPropertyName("age")]
    public int? Age { get; set; }

    [JsonPropertyName("weightKg")]
    public double? WeightKg { get; set; }
}

public class ProfileUpdateRequest
{
    [JsonPropertyName("nickname")]
    public string? Nickname { get; set; }

    [JsonPropertyName("heightCm")]
    public int? HeightCm { get; set; }

    [JsonPropertyName("age")]
    public int? Age { get; set; }

    [JsonPropertyName("weightKg")]
    public double? WeightKg { get; set; }
}

public class ChangePasswordRequest
{
    [JsonPropertyName("oldPassword")]
    public string OldPassword { get; set; } = "";

    [JsonPropertyName("newPassword")]
    public string NewPassword { get; set; } = "";
}

public class ApiErrorResponse
{
    [JsonPropertyName("detail")]
    public string Detail { get; set; } = "";
}

// ── Simple Chat ───────────────────────────────────────────────────────

public class ChatRequest
{
    [JsonPropertyName("message")]
    public string Message { get; set; } = "";
}

public class ChatResponse
{
    [JsonPropertyName("reply")]
    public string Reply { get; set; } = "";
}
