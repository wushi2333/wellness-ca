// Author: Yutong Luo
package sg.edu.nus.wellness.dto;
import jakarta.validation.constraints.NotBlank;

/** Lightweight DTO for /login — does NOT enforce password length.
 *  AuthRequest (with @Size(min=8)) is only for /register. */
public class LoginRequest {
    @NotBlank
    public String username;

    @NotBlank
    public String password;
}
