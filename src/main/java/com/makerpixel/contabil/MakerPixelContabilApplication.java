package com.makerpixel.contabil;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class MakerPixelContabilApplication {

    private static final Logger log = LoggerFactory.getLogger(MakerPixelContabilApplication.class);

    @Value("${notasync.cron.secret:}")
    private String cronSecret;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(MakerPixelContabilApplication.class, args);
    }

    @PostConstruct
    public void verificarSegurancaStartup() {
        boolean emProducao = !"local".equalsIgnoreCase(activeProfile) && !"test".equalsIgnoreCase(activeProfile);
        boolean secretInseguro = cronSecret == null || cronSecret.isBlank() || "notasync_cron_default_secret_2026".equals(cronSecret);

        if (secretInseguro) {
            if (emProducao) {
                log.warn("=========================================================================================");
                log.warn("AVISO DE SEGURANÇA: NOTASYNC_CRON_SECRET não configurado ou com valor default.");
                log.warn("Endpoints /interno/* estarão protegidos por fail-closed (rejeitarão requisições com 401).");
                log.warn("=========================================================================================");
            } else {
                log.info("Startup em perfil local/test: NOTASYNC_CRON_SECRET com valor de desenvolvimento.");
            }
        } else {
            log.info("Segurança de endpoints internos (/interno/*): Segredo de autenticação configurado com sucesso.");
        }
    }
}
