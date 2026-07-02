// Author: Wang Songyu, Xia Zihang
package sg.edu.nus.wellness.dto;

import java.util.List;
import java.util.Map;

public class CharacterDTO {

    public static class Req {
        public String message;
        public String mode;
        public Long sessionId;
    }

    public static class Resp {
        public String reply;
        public String emotion;
        public Long sessionId;
        public Map<String, String> intent;
        public List<String> tools;

        public Resp(String reply, String emotion) {
            this.reply = reply;
            this.emotion = emotion;
        }
    }
}
