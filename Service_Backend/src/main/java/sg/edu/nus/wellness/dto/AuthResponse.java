// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.dto;

public class AuthResponse {
    public String accessToken;
    public String tokenType = "bearer";
    public Long userId;
    public String username;

    public static AuthResponse of(String token, Long userId, String username) {
        AuthResponse response = new AuthResponse();
        response.accessToken = token;
        response.userId = userId;
        response.username = username;
        return response;
    }
}
