package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-85 - Consultar el historico de operacion.
 *
 * Valida:
 * - demanda historica por parada;
 * - agrupacion por hora;
 * - franjas horarias;
 * - recorrido historico del bus;
 * - orden cronologico;
 * - seguridad de administrador.
 */
class HistoricoOperacionIT extends IntegracionPostgisTest {

    private static final long PARADA_1 = 1L;
    private static final long PARADA_2 = 2L;

    /*
     * Guatemala usa UTC-6.
     *
     * Por ejemplo:
     * 2026-09-17T12:15:00Z = 06:15 en Guatemala.
     */

    @Test
    void admin_consulta_demanda_historica_agrupada_por_hora()
            throws Exception {

        insertarReserva(
                "dispositivo-001",
                PARADA_1,
                "2026-09-17T12:15:00Z"
        );

        insertarReserva(
                "dispositivo-002",
                PARADA_1,
                "2026-09-17T12:45:00Z"
        );

        insertarReserva(
                "dispositivo-003",
                PARADA_1,
                "2026-09-17T14:10:00Z"
        );

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("paradaId", "1")
                                .param("fecha", "2026-09-17")
                                .param("horaInicio", "6")
                                .param("horaFin", "9")
                )
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.paradaId").value(1))
                .andExpect(jsonPath("$.fecha").value("2026-09-17"))
                .andExpect(jsonPath("$.horaInicio").value(6))
                .andExpect(jsonPath("$.horaFin").value(9))
                .andExpect(jsonPath("$.totalReservas").value(3))

                /*
                 * 06:00 -> 2 reservas
                 */
                .andExpect(jsonPath("$.serie[0].hora").value(6))
                .andExpect(jsonPath("$.serie[0].etiqueta").value("06:00"))
                .andExpect(jsonPath("$.serie[0].cantidad").value(2))

                /*
                 * 07:00 -> ninguna.
                 *
                 * Debe aparecer igualmente para que
                 * la grafica no tenga un hueco.
                 */
                .andExpect(jsonPath("$.serie[1].hora").value(7))
                .andExpect(jsonPath("$.serie[1].etiqueta").value("07:00"))
                .andExpect(jsonPath("$.serie[1].cantidad").value(0))

                /*
                 * 08:00 -> 1 reserva.
                 */
                .andExpect(jsonPath("$.serie[2].hora").value(8))
                .andExpect(jsonPath("$.serie[2].etiqueta").value("08:00"))
                .andExpect(jsonPath("$.serie[2].cantidad").value(1));
    }

    @Test
    void demanda_no_mezcla_otras_paradas_ni_otras_fechas()
            throws Exception {

        /*
         * Esta SI debe contar:
         * 17 de septiembre, parada 1, 10:00 Guatemala.
         */
        insertarReserva(
                "dispositivo-correcto",
                PARADA_1,
                "2026-09-17T16:15:00Z"
        );

        /*
         * Misma hora pero otra parada.
         * NO debe contar.
         */
        insertarReserva(
                "dispositivo-otra-parada",
                PARADA_2,
                "2026-09-17T16:30:00Z"
        );

        /*
         * Misma parada pero otro dia.
         * NO debe contar.
         */
        insertarReserva(
                "dispositivo-otro-dia",
                PARADA_1,
                "2026-09-18T16:15:00Z"
        );

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("paradaId", "1")
                                .param("fecha", "2026-09-17")
                                .param("horaInicio", "10")
                                .param("horaFin", "11")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas").value(1))
                .andExpect(jsonPath("$.serie[0].cantidad").value(1));
    }

    @Test
    void demanda_sin_franja_devuelve_las_24_horas()
            throws Exception {

        insertarReserva(
                "dispositivo-dia-completo",
                PARADA_1,
                "2026-09-17T18:00:00Z"
        );

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("paradaId", "1")
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value(0))
                .andExpect(jsonPath("$.horaFin").value(24))
                .andExpect(jsonPath("$.serie.length()").value(24))
                .andExpect(jsonPath("$.totalReservas").value(1));
    }

    @Test
    void demanda_historica_requiere_administrador()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .param("paradaId", "1")
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_consulta_recorrido_historico_en_orden_cronologico()
            throws Exception {

        Long vehiculoId = busPiloto();

        /*
         * Los insertamos intencionalmente desordenados
         * para comprobar que el endpoint los ordena.
         */
        insertarPosicion(
                vehiculoId,
                14.6330,
                -89.9830,
                30.0,
                "2026-09-17T15:00:00Z"
        );

        insertarPosicion(
                vehiculoId,
                14.6310,
                -89.9810,
                10.0,
                "2026-09-17T13:00:00Z"
        );

        insertarPosicion(
                vehiculoId,
                14.6320,
                -89.9820,
                20.0,
                "2026-09-17T14:00:00Z"
        );

        mockMvc.perform(
                        get("/api/v1/admin/historico/recorrido")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param(
                                        "vehiculoId",
                                        vehiculoId.toString()
                                )
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isOk())

                .andExpect(
                        jsonPath("$.vehiculoId")
                                .value(vehiculoId)
                )

                .andExpect(
                        jsonPath("$.fecha")
                                .value("2026-09-17")
                )

                .andExpect(
                        jsonPath("$.totalPuntos")
                                .value(3)
                )

                /*
                 * Primera posicion cronologica.
                 */
                .andExpect(
                        jsonPath("$.puntos[0].latitud")
                                .value(14.6310)
                )
                .andExpect(
                        jsonPath("$.puntos[0].longitud")
                                .value(-89.9810)
                )
                .andExpect(
                        jsonPath("$.puntos[0].velocidadKmh")
                                .value(10.0)
                )

                /*
                 * Segunda.
                 */
                .andExpect(
                        jsonPath("$.puntos[1].latitud")
                                .value(14.6320)
                )

                /*
                 * Tercera.
                 */
                .andExpect(
                        jsonPath("$.puntos[2].latitud")
                                .value(14.6330)
                );
    }

    @Test
    void recorrido_no_incluye_posiciones_de_otro_dia()
            throws Exception {

        Long vehiculoId = busPiloto();

        /*
         * Dentro del 17 de septiembre en Guatemala.
         */
        insertarPosicion(
                vehiculoId,
                14.6300,
                -89.9800,
                15.0,
                "2026-09-17T12:00:00Z"
        );

        /*
         * 18 de septiembre.
         * No debe aparecer.
         */
        insertarPosicion(
                vehiculoId,
                14.6400,
                -89.9900,
                25.0,
                "2026-09-18T12:00:00Z"
        );

        mockMvc.perform(
                        get("/api/v1/admin/historico/recorrido")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param(
                                        "vehiculoId",
                                        vehiculoId.toString()
                                )
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPuntos").value(1))
                .andExpect(
                        jsonPath("$.puntos[0].latitud")
                                .value(14.6300)
                );
    }

    @Test
    void recorrido_sin_datos_devuelve_lista_vacia()
            throws Exception {

        Long vehiculoId = busPiloto();

        mockMvc.perform(
                        get("/api/v1/admin/historico/recorrido")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param(
                                        "vehiculoId",
                                        vehiculoId.toString()
                                )
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPuntos").value(0))
                .andExpect(jsonPath("$.puntos.length()").value(0));
    }

    @Test
    void recorrido_historico_requiere_administrador()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/recorrido")
                                .param("vehiculoId", "1")
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isUnauthorized());
    }

    /**
     * BUS-01 viene sembrado desde Flyway.
     */
    private Long busPiloto() {
        return jdbc.queryForObject(
                """
                SELECT id
                FROM vehiculos
                WHERE identificador = 'BUS-01'
                """,
                Long.class
        );
    }

    /**
     * Inserta una reserva en un instante especifico.
     */
    private void insertarReserva(
            String dispositivoId,
            long paradaId,
            String creadoEn
    ) {

        Instant creado = Instant.parse(creadoEn);

        jdbc.update(
                """
                INSERT INTO registros_espera (
                    dispositivo_id,
                    parada_id,
                    estado,
                    creado_en,
                    expira_en
                )
                VALUES (?, ?, 'ACTIVA', ?, ?)
                """,
                dispositivoId,
                paradaId,
                Timestamp.from(creado),
                Timestamp.from(creado.plusSeconds(3600))
        );
    }

    /**
     * Inserta una posicion historica directamente.
     *
     * PostGIS utiliza el orden:
     *
     * X = longitud
     * Y = latitud
     */
    private void insertarPosicion(
            Long vehiculoId,
            double latitud,
            double longitud,
            double velocidad,
            String registradoEn
    ) {

        jdbc.update(
                """
                INSERT INTO posiciones_historicas (
                    ubicacion,
                    velocidad_kmh,
                    registrado_en,
                    vehiculo_id
                )
                VALUES (
                    ST_SetSRID(
                        ST_MakePoint(?, ?),
                        4326
                    ),
                    ?,
                    ?,
                    ?
                )
                """,
                longitud,
                latitud,
                velocidad,
                Timestamp.from(
                        Instant.parse(registradoEn)
                ),
                vehiculoId
        );
    }
}