// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

import sg.edu.nus.wellness.model.User;
import sg.edu.nus.wellness.model.UserProfile;

public class UserResponse {
    public Long userId;
    public String username;
    public String email;
    public String provider;
    public String avatarUrl;
    public String nickname;
    public Integer heightCm;
    public Integer age;
    public Double weightKg;

    public static UserResponse from(User u, UserProfile p) {
        UserResponse r = new UserResponse();
        r.userId = u.getId();
        r.username = u.getUsername();
        r.email = u.getEmail();
        r.provider = u.getProvider();
        if (p != null) {
            r.avatarUrl = p.getAvatarUrl();
            r.nickname = p.getNickname();
            r.heightCm = p.getHeightCm();
            r.age = p.getAge();
            r.weightKg = p.getWeightKg();
        }
        return r;
    }
}
