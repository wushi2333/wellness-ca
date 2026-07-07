// Author: Xia Zihang
package sg.edu.nus.wellness.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CharacterAsrService {

    private final RestTemplate http;
    private final String appId, token;

    public CharacterAsrService(
            @Value("${app.volcano.tts.appid}") String appId,
            @Value("${app.volcano.tts.token}") String token,
            RestTemplate rt) {
        this.appId = appId;
        this.token = token;
        this.http = rt;
    }

    @SuppressWarnings("unchecked")
    public String recognize(String base64Audio, String language) {
        try {
            // Convert raw PCM to WAV by adding a 44-byte header
            byte[] pcm = java.util.Base64.getDecoder().decode(base64Audio);
            byte[] wav = pcmToWav(pcm);
            String wavBase64 = java.util.Base64.getEncoder().encodeToString(wav);

            Map<String, Object> audio = new HashMap<>();
            audio.put("data", wavBase64);

            Map<String, Object> body = new HashMap<>();
            body.put("user", Map.of("uid", appId));
            body.put("audio", audio);
            body.put("request", Map.of("model_name", "bigmodel"));

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("X-Api-App-Key", appId);
            h.set("X-Api-Access-Key", token);
            h.set("X-Api-Resource-Id", "volc.bigasr.auc_turbo");
            h.set("X-Api-Request-Id", UUID.randomUUID().toString());
            h.set("X-Api-Sequence", "-1");

            ResponseEntity<Map> resp = http.exchange(
                "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash",
                HttpMethod.POST, new HttpEntity<>(body, h), Map.class);

            System.err.println("[ASR] status=" + resp.getStatusCode() + " body=" + resp.getBody());

            if (resp.getBody() != null && resp.getBody().containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) resp.getBody().get("result");
                String text = (String) result.getOrDefault("text", "");
                if (text != null && !text.isEmpty()) return text;
            }
            System.err.println("[ASR] No text in response: " + resp.getBody());
            return null;
        } catch (Exception e) {
            System.err.println("[ASR] Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** Add WAV header to raw PCM 16kHz 16-bit mono data. */
    private static byte[] pcmToWav(byte[] pcm) {
        int sampleRate = 16000;
        int bitsPerSample = 16;
        int channels = 1;
        int dataSize = pcm.length;
        int headerSize = 44;
        byte[] wav = new byte[headerSize + dataSize];

        // RIFF header
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        int fileSize = 36 + dataSize;
        wav[4] = (byte)(fileSize); wav[5] = (byte)(fileSize >> 8);
        wav[6] = (byte)(fileSize >> 16); wav[7] = (byte)(fileSize >> 24);
        // WAVE
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        // fmt chunk
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0; // chunk size
        wav[20] = 1; wav[21] = 0; // PCM
        wav[22] = (byte)channels; wav[23] = 0;
        wav[24] = (byte)(sampleRate); wav[25] = (byte)(sampleRate >> 8);
        wav[26] = (byte)(sampleRate >> 16); wav[27] = (byte)(sampleRate >> 24);
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        wav[28] = (byte)(byteRate); wav[29] = (byte)(byteRate >> 8);
        wav[30] = (byte)(byteRate >> 16); wav[31] = (byte)(byteRate >> 24);
        wav[32] = (byte)(channels * bitsPerSample / 8); wav[33] = 0; // block align
        wav[34] = (byte)bitsPerSample; wav[35] = 0;
        // data chunk
        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
        wav[40] = (byte)(dataSize); wav[41] = (byte)(dataSize >> 8);
        wav[42] = (byte)(dataSize >> 16); wav[43] = (byte)(dataSize >> 24);
        // PCM data
        System.arraycopy(pcm, 0, wav, 44, dataSize);
        return wav;
    }
}
