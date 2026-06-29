// Author: Xia Zihang
package sg.edu.nus.wellness.dto;
public class AuthResponse {
    public String accessToken; public String tokenType="bearer";
    public static AuthResponse of(String t){AuthResponse r=new AuthResponse();r.accessToken=t;return r;}
}
