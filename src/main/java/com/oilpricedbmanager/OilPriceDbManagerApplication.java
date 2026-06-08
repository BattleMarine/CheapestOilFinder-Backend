package com.oilpricedbmanager;

import com.oilpricedbmanager.external.opinet.OpinetProperties;
import com.oilpricedbmanager.external.naver.NaverDirectionsProperties;
import com.oilpricedbmanager.external.kakao.KakaoLocalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({
        OpinetProperties.class,
        NaverDirectionsProperties.class,
        KakaoLocalProperties.class
})
public class OilPriceDbManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OilPriceDbManagerApplication.class, args);
    }
}
