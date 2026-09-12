package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservaAbordajePasajeroIT extends IntegracionPostgisTest {

    @BeforeEach
    void limpiarDatosHu76Pasajero() {

        jdbc.update("""
                DELETE FROM paradas_atendidas
                """);

        jdbc.update("""
                DELETE FROM registros_espera
                """);
    }

    @Test
    void conductor_tiene_prioridad_y_pasajero_ve_que_fue_recogido()
            throws Exception {

        String dispositivoId =
                UUID.randomUUID().toString();

        Long reservaId =
                insertarReserva(dispositivoId);

        /*
         * PASO 1:
         * El pasajero indica que considera
         * que NO abordo.
         */
        mockMvc.perform(
                        post(
                                "/api/v1/reservas/{id}/declaracion-no-abordo",
                                reservaId
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "dispositivoId": "%s"
                                        }
                                        """.formatted(dispositivoId))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.estado")
                                .value("ACTIVA")
                )
                .andExpect(
                        jsonPath("$.pasajeroDeclaroNoAbordo")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.declaracionNoAbordoEn")
                                .isNotEmpty()
                );

        /*
         * PASO 2:
         * El conductor marca la parada como atendida.
         *
         * La confirmacion del conductor tiene prioridad.
         */
        mockMvc.perform(
                        post(
                                "/api/v1/rutas/1/paradas/1/atendida"
                        )
                                .with(
                                        user("conductor1")
                                                .roles("CONDUCTOR")
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.reservasCerradas")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.marcadaEn")
                                .isNotEmpty()
                );

        /*
         * PASO 3:
         * El pasajero consulta nuevamente su reserva.
         *
         * Ahora debe aparecer como ABORDO.
         */
        mockMvc.perform(
                        get(
                                "/api/v1/reservas/{id}",
                                reservaId
                        )
                                .param(
                                        "dispositivoId",
                                        dispositivoId
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.estado")
                                .value("ABORDO")
                )
                .andExpect(
                        jsonPath("$.abordadoEn")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.pasajeroDeclaroNoAbordo")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.declaracionNoAbordoEn")
                                .isNotEmpty()
                );

        /*
         * PASO 4:
         * Verificamos directamente la base de datos.
         *
         * Deben existir al mismo tiempo:
         *
         * estado = ABORDO
         * declaracion del pasajero = true
         * conductor registrado
         * fecha de abordaje
         * fecha de declaracion
         */
        Long registrosCorrectos =
                jdbc.queryForObject("""
                        SELECT count(*)
                          FROM registros_espera
                         WHERE id = ?
                           AND estado = 'ABORDO'
                           AND pasajero_declaro_no_abordo = TRUE
                           AND declaracion_no_abordo_en IS NOT NULL
                           AND abordado_en IS NOT NULL
                           AND abordado_por = 'conductor1'
                        """,
                        Long.class,
                        reservaId
                );

        assertThat(registrosCorrectos)
                .isEqualTo(1);
    }

    @Test
    void otro_dispositivo_no_puede_consultar_la_reserva()
            throws Exception {

        String dispositivoId =
                UUID.randomUUID().toString();

        Long reservaId =
                insertarReserva(dispositivoId);

        mockMvc.perform(
                        get(
                                "/api/v1/reservas/{id}",
                                reservaId
                        )
                                .param(
                                        "dispositivoId",
                                        "otro-dispositivo"
                                )
                )
                .andExpect(status().isNotFound());
    }

    private Long insertarReserva(
            String dispositivoId
    ) {

        return jdbc.queryForObject("""
                INSERT INTO registros_espera (
                    dispositivo_id,
                    parada_id,
                    estado,
                    creado_en,
                    expira_en
                )
                VALUES (
                    ?,
                    1,
                    'ACTIVA',
                    now(),
                    now() + interval '20 minutes'
                )
                RETURNING id
                """,
                Long.class,
                dispositivoId
        );
    }
}