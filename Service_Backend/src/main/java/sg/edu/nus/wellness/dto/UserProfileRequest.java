// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

/**
 * All fields optional — allows partial updates via PATCH semantics.
 */
public class UserProfileRequest {
    public String avatarUrl;
    public String nickname;
    public Integer heightCm;
    public Integer age;
    public Double weightKg;
}
