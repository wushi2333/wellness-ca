// Author: Xia Zihang
package sg.edu.nus.wellness.dto;
import jakarta.validation.constraints.NotBlank;
public class AuthRequest {
    @NotBlank public String username;
    @NotBlank public String password;
}
