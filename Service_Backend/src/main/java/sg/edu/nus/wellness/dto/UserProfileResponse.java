// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

import sg.edu.nus.wellness.model.UserProfile;

public class UserProfileResponse {
    public Long userId;
    public String avatarUrl;
    public String nickname;
    public Integer heightCm;
    public Integer age;
    public Double weightKg;

    public static UserProfileResponse from(UserProfile p) {
        UserProfileResponse r = new UserProfileResponse();
        r.userId = p.getUserId();
        r.avatarUrl = p.getAvatarUrl();
        r.nickname = p.getNickname();
        r.heightCm = p.getHeightCm();
        r.age = p.getAge();
        r.weightKg = p.getWeightKg();
        return r;
    }
}
