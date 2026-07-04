// Author: Xia Zihang
package sg.edu.nus.wellness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
public class WellnessApplication {
    public static void main(String[] args) { SpringApplication.run(WellnessApplication.class, args); }
}
