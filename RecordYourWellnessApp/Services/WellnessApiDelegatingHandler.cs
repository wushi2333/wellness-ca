using System.Net.Http.Headers;

namespace RecordYourWellnessApp.Services;

/// <summary>Attaches the JWT bearer token and the shared gateway token to every request,
/// and reacts to a 401 the same way the Android ApiErrorHandler does: invalidate the
/// session so AppContext redirects to login.</summary>
public class WellnessApiDelegatingHandler : DelegatingHandler
{
    private readonly ISessionService _session;

    public WellnessApiDelegatingHandler(ISessionService session)
    {
        _session = session;
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        // Only add Authorization header if logged in
        if (_session.IsLoggedIn)
        {
            request.Headers.Authorization =
                new AuthenticationHeaderValue("Bearer", _session.AccessToken);
        }

        // Add gateway token (required for all requests)
        if (!request.Headers.Contains("X-API-Token"))
        {
            request.Headers.Add("X-API-Token", Constants.ApiGatewayToken);
        }

        var response = await base.SendAsync(request, cancellationToken);

        // 401 → session expired → AppContext navigates to login + notifies the user.
        if ((int)response.StatusCode == 401 && _session.IsLoggedIn)
        {
            _session.OnSessionExpired();
        }

        return response;
    }
}
