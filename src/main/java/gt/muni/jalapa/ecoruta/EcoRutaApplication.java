package gt.muni.jalapa.ecoruta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
// Hasta ahora las propiedades ecoruta.* estaban declaradas en application.yml y
// nadie las leia. Con esto los records @ConfigurationProperties se enlazan solos.
@ConfigurationPropertiesScan
// El latido del stream SSE (SCRUM-140) necesita el planificador. SCRUM-133 lo
// va a necesitar igual para expirar los registros de espera cada 60 s.
@EnableScheduling
public class EcoRutaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoRutaApplication.class, args);
    }

    /** Reloj inyectable: las pruebas de reserva fijan el instante de expiracion. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
