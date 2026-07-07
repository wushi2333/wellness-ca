// Author: Xia Zihang
using RecordYourWellnessApp.Models;
using Refit;

namespace RecordYourWellnessApp.Services;

public interface IAuthApi
{
    [Post("/login")]
    Task<LoginResponse> Login([Body] LoginRequest request);

    [Post("/register")]
    Task<RegisterResponse> Register([Body] RegisterRequest request);

    [Post("/auth/google")]
    Task<string> GoogleLogin([Body] GoogleAuthRequest request);
}

public interface IRecordApi
{
    [Get("/records")]
    Task<PagedResponse<DailyWellness>> GetRecords(int page = 0, int size = 90);

    [Post("/sleep-records")]
    Task<CreateResponse> CreateSleep([Body] SleepRecordRequest request);

    [Put("/sleep-records/{id}")]
    Task<ApiErrorResponse> UpdateSleep(long id, [Body] SleepRecordRequest request);

    [Delete("/sleep-records/{id}")]
    Task DeleteSleep(long id);

    [Post("/exercise-records")]
    Task<CreateResponse> CreateExercise([Body] ExerciseRecordRequest request);

    [Put("/exercise-records/{id}")]
    Task<ApiErrorResponse> UpdateExercise(long id, [Body] ExerciseRecordRequest request);

    [Delete("/exercise-records/{id}")]
    Task DeleteExercise(long id);
}

public interface IAgentApi
{
    [Post("/agent/recommend")]
    Task<AgentRecommendation> Recommend([Body] Dictionary<string, object>? body = null);

    [Get("/agent/recommend/history")]
    Task<List<AgentHistoryItem>> GetHistory(int limit = 100);

    [Delete("/agent/recommend/{id}")]
    Task DeleteRecommendation(int id);
}

public interface ICharacterApi
{
    [Get("/character/sessions")]
    Task<List<CharacterSession>> GetSessions();

    [Post("/character/sessions")]
    Task<CreateSessionResponse> CreateSession([Body] Dictionary<string, string> body);

    [Delete("/character/sessions/{id}")]
    Task DeleteSession(long id);

    [Get("/character/sessions/{id}/messages")]
    Task<List<CharacterMessage>> GetMessages(long id);

    [Post("/character/chat")]
    Task<CharacterChatResponse> Chat([Body] CharacterChatRequest request);

    [Post("/character/agent")]
    Task<CharacterChatResponse> Agent([Body] CharacterChatRequest request);

    [Post("/character/tts")]
    Task<HttpResponseMessage> Tts([Body] Dictionary<string, string> body);

    [Post("/character/asr")]
    Task<Dictionary<string, string>> Asr([Body] Dictionary<string, string> body);
}

public interface IProfileApi
{
    [Get("/user")]
    Task<UserProfileData> GetUser();

    [Get("/profile")]
    Task<UserProfileData> GetProfile();

    [Put("/profile")]
    Task<UserProfileData> UpdateProfile([Body] ProfileUpdateRequest request);

    [Post("/profile/avatar")]
    [Multipart]
    Task<Dictionary<string, string>> UploadAvatar(StreamPart file);

    [Put("/user/username")]
    Task<Dictionary<string, string>> ChangeUsername([Body] Dictionary<string, string> body);

    [Put("/auth/email")]
    Task<Dictionary<string, string>> ChangeEmail([Body] Dictionary<string, string> body);

    [Post("/auth/change-password")]
    Task<Dictionary<string, string>> ChangePassword([Body] ChangePasswordRequest request);

    [Delete("/auth/account")]
    Task DeleteAccount();
}
