// Author: Xia Zihang
package sg.edu.nus.wellness.dto;
public class ChatDTO {
    public static class Req { public String message; }
    public static class Resp { public String reply; public Resp(String r){reply=r;} }
}
