package gt.muni.jalapa.ecoruta.tiempo.servicio;

import gt.muni.jalapa.ecoruta.tiempo.dominio.ParametrosDeRuta;
import gt.muni.jalapa.ecoruta.tiempo.dominio.TipoDeDia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Tiempo estimado en minutos enteros (HU-72).
 *
 * <p>Suma al trayecto la detencion de cada parada intermedia. Si el bus sigue
 * en el origen, el reloj arranca en la proxima salida programada del tipo de
 * dia (habil / jueves / domingo), no en el instante actual.
 *
 * <p>La zona es America/Guatemala: es la de Jalapa y no tiene horario de verano.
 */
public class CalculadorDeTiempoEstimado {

    static final ZoneId ZONA = ZoneId.of("America/Guatemala");

    /** Distancia al origen por debajo de la cual el bus aun no salio. */
    private static final double METROS_EN_ORIGEN = 75;

    private static final double RADIO_TIERRA_METROS = 6_371_000;

    public int minutos(ParametrosDeRuta parametros,
                       List<PuntoDeParada> paradas,
                       double latitud,
                       double longitud,
                       ZonedDateTime ahora) {
        List<PuntoDeParada> enOrden = paradas.stream()
                .sorted(Comparator.comparingInt(PuntoDeParada::orden))
                .toList();
        if (enOrden.isEmpty()) {
            throw new IllegalArgumentException("No se puede estimar el tiempo de una ruta sin paradas");
        }
        PuntoDeParada origen = enOrden.getFirst();
        boolean iniciado = metros(latitud, longitud, origen.latitud(), origen.longitud())
                > METROS_EN_ORIGEN;

        if (!iniciado) {
            ZonedDateTime salida = proximaSalidaProgramada(parametros, ahora);
            double esperaSegundos = Math.max(0,
                    (salida.toEpochSecond() - ahora.toEpochSecond()));
            return aMinutos(esperaSegundos
                    + trayectoCompletoSegundos(parametros, enOrden));
        }

        PuntoDeParada cercana = masCercana(enOrden, latitud, longitud);
        return aMinutos(trayectoRestanteSegundos(parametros, enOrden, cercana));
    }

    ZonedDateTime proximaSalidaProgramada(ParametrosDeRuta parametros, ZonedDateTime ahora) {
        ZonedDateTime instante = ahora.withZoneSameInstant(ZONA);
        LocalDate dia = instante.toLocalDate();
        for (int i = 0; i < 8; i++) {
            LocalDate fecha = dia.plusDays(i);
            for (LocalTime hora : parametros.horariosDe(TipoDeDia.de(fecha))) {
                ZonedDateTime salida = ZonedDateTime.of(fecha, hora, ZONA);
                if (!salida.isBefore(instante)) {
                    return salida;
                }
            }
        }
        throw new IllegalStateException(
                "La ruta " + parametros.getRutaId() + " no tiene una salida programada en los proximos dias");
    }

    private static double trayectoCompletoSegundos(ParametrosDeRuta parametros,
                                                   List<PuntoDeParada> enOrden) {
        return parametros.getDuracionRecorridoMinutos() * 60.0
                + paradasIntermedias(enOrden).size()
                * (double) parametros.getDetencionPorParadaSegundos();
    }

    private static double trayectoRestanteSegundos(ParametrosDeRuta parametros,
                                                   List<PuntoDeParada> enOrden,
                                                   PuntoDeParada cercana) {
        PuntoDeParada destino = enOrden.getLast();
        int segmentosTotales = destino.orden() - enOrden.getFirst().orden();
        if (segmentosTotales <= 0) {
            return 0;
        }
        int segmentosRestantes = Math.max(0, destino.orden() - cercana.orden());
        double trayecto = parametros.getDuracionRecorridoMinutos() * 60.0
                * segmentosRestantes / segmentosTotales;
        long detenciones = paradasIntermedias(enOrden).stream()
                .filter(parada -> parada.orden() > cercana.orden())
                .count();
        return trayecto + detenciones * (double) parametros.getDetencionPorParadaSegundos();
    }

    private static List<PuntoDeParada> paradasIntermedias(List<PuntoDeParada> enOrden) {
        if (enOrden.size() <= 2) {
            return List.of();
        }
        return enOrden.subList(1, enOrden.size() - 1);
    }

    private static PuntoDeParada masCercana(List<PuntoDeParada> enOrden,
                                            double latitud,
                                            double longitud) {
        return enOrden.stream()
                .min(Comparator.comparingDouble(parada ->
                        metros(latitud, longitud, parada.latitud(), parada.longitud())))
                .orElseThrow();
    }

    private static int aMinutos(double segundos) {
        return (int) Math.ceil(segundos / 60.0);
    }

    static double metros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
