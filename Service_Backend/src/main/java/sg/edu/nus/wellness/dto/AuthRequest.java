// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    public String username;

    @NotBlank
    @Size(min = 6, max = 128)
    public String password;

    public String email;
}
