package gt.muni.jalapa.ecoruta.herramientas;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simulador del equipo a bordo (SCRUM-304).
 *
 * <p>Una sola clase, solo JDK 21. Se ejecuta suelta:
 * {@code java src/test/java/gt/muni/jalapa/ecoruta/herramientas/SimuladorGps.java}
 * y las pruebas la reutilizan. El modo {@code directo} habla con
 * POST /api/v1/telemetria/posiciones; el modo {@code traccar} manda el
 * protocolo tk103/gps103 por TCP a Traccar.
 *
 * <p>Formatos de protocolo copiados de Traccar v6.15.3
 * (Tk103ProtocolDecoderTest / Gps103ProtocolDecoderTest), no de memoria.
 */
public final class SimuladorGps {

    public static final double RADIO_TIERRA_M = 6_371_000.0;
    public static final String IMEI_DEFECTO = "860000000000001";

    private SimuladorGps() {
    }

    // --- geometria (metodos puros, sin red) ---------------------------------

    public record Punto(double latitud, double longitud) {
    }

    public record ParadaEnRuta(double metros, String nombre, long id, Punto ubicacion) {
    }

    /** Distancia haversine en metros. */
    public static double metros(Punto a, Punto b) {
        double la1 = Math.toRadians(a.latitud());
        double lo1 = Math.toRadians(a.longitud());
        double la2 = Math.toRadians(b.latitud());
        double lo2 = Math.toRadians(b.longitud());
        double h = Math.pow(Math.sin((la2 - la1) / 2), 2)
                + Math.cos(la1) * Math.cos(la2) * Math.pow(Math.sin((lo2 - lo1) / 2), 2);
        return 2 * RADIO_TIERRA_M * Math.asin(Math.sqrt(h));
    }

    /**
     * Recorrido acumulado: indice i guarda los metros desde el inicio hasta
     * el vertice i. El ultimo valor es el largo total.
     */
    public static double[] acumulado(List<Punto> puntos) {
        double[] acc = new double[puntos.size()];
        for (int i = 1; i < puntos.size(); i++) {
            acc[i] = acc[i - 1] + metros(puntos.get(i - 1), puntos.get(i));
        }
        return acc;
    }

    /** (lat, lon) a {@code distancia} metros del inicio, interpolando. */
    public static Punto interpolar(List<Punto> puntos, double[] acc, double distancia) {
        double largo = acc[acc.length - 1];
        if (largo <= 0) {
            return puntos.getFirst();
        }
        double d = distancia % largo;
        if (d < 0) {
            d += largo;
        }
        for (int i = 1; i < acc.length; i++) {
            if (acc[i] >= d) {
                double tramo = acc[i] - acc[i - 1];
                double t = tramo == 0 ? 0.0 : (d - acc[i - 1]) / tramo;
                Punto a = puntos.get(i - 1);
                Punto b = puntos.get(i);
                return new Punto(
                        a.latitud() + (b.latitud() - a.latitud()) * t,
                        a.longitud() + (b.longitud() - a.longitud()) * t);
            }
        }
        return puntos.getLast();
    }

    /**
     * La parada en (desde, hasta], si alguna. El tramo puede cerrar el
     * circuito (desde > hasta): sin eso, Parque Central (metro 0) no se veria.
     */
    public static ParadaEnRuta paradaEn(List<ParadaEnRuta> paradas, double desde, double hasta) {
        for (ParadaEnRuta parada : paradas) {
            double metro = parada.metros();
            boolean dentro = desde <= hasta
                    ? desde < metro && metro <= hasta
                    : metro > desde || metro <= hasta;
            if (dentro) {
                return parada;
            }
        }
        return null;
    }

    public static List<ParadaEnRuta> situarParadas(List<Punto> puntos, double[] acc,
                                                   List<ParadaEnRuta> crudas) {
        List<ParadaEnRuta> situadas = new ArrayList<>();
        for (ParadaEnRuta pa : crudas) {
            int mejor = 0;
            double mejorDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < puntos.size(); i++) {
                double d = metros(pa.ubicacion(), puntos.get(i));
                if (d < mejorDist) {
                    mejorDist = d;
                    mejor = i;
                }
            }
            situadas.add(new ParadaEnRuta(acc[mejor], pa.nombre(), pa.id(), pa.ubicacion()));
        }
        situadas.sort((a, b) -> Double.compare(a.metros(), b.metros()));
        return situadas;
    }

    // --- protocolo tk103 / gps103 (metodos puros) ---------------------------

    /** ddmm.mmmm / dddmm.mmmm y hemisferio, como el decoder de Traccar. */
    public static String nmea(double decimal, boolean latitud) {
        String hemisferio = latitud
                ? (decimal >= 0 ? "N" : "S")
                : (decimal >= 0 ? "E" : "W");
        double absoluto = Math.abs(decimal);
        int grados = (int) absoluto;
        double minutos = (absoluto - grados) * 60.0;
        String cuerpo = latitud
                ? String.format(Locale.US, "%02d%07.4f", grados, minutos)
                : String.format(Locale.US, "%03d%07.4f", grados, minutos);
        return cuerpo + hemisferio;
    }

    /**
     * Formato coma-delimitado de Tk103ProtocolDecoderTest, p. ej.
     * {@code (352606090042050,BP05,240414,A,4527.3513N,00909.9758E,4.80,112825,155.49)}.
     * Fecha DDMMYY (setDateReverse). Velocidad en km/h, como el decoder.
     */
    public static String mensajeTk103(String imei, Instant cuando, double lat, double lon,
                                      double velocidadKmh, boolean fijo) {
        ZonedDateTime z = cuando.atZone(ZoneOffset.UTC);
        String fecha = String.format("%02d%02d%02d", z.getDayOfMonth(), z.getMonthValue(), z.getYear() % 100);
        String hora = String.format("%02d%02d%02d", z.getHour(), z.getMinute(), z.getSecond());
        String validez = fijo ? "A" : "V";
        return "(" + imei + ",BR00," + fecha + "," + validez + ","
                + nmea(lat, true) + "," + nmea(lon, false) + ","
                + String.format(Locale.US, "%.2f", Math.max(0.0, velocidadKmh)) + ","
                + hora + ",000.0)";
    }

    /**
     * Formato de Gps103ProtocolDecoderTest, p. ej.
     * {@code imei:359710045559474,tracker,151030080103,,F,000101.000,A,5443.3834,N,02512.9071,E,0.00,0;}.
     * La velocidad del gps103 se guarda en nudos.
     */
    public static String mensajeGps103(String imei, Instant cuando, double lat, double lon,
                                       double velocidadKmh, boolean fijo) {
        ZonedDateTime z = cuando.atZone(ZoneOffset.UTC);
        String local = String.format("%02d%02d%02d%02d%02d%02d",
                z.getYear() % 100, z.getMonthValue(), z.getDayOfMonth(),
                z.getHour(), z.getMinute(), z.getSecond());
        String utc = String.format("%02d%02d%02d.000", z.getHour(), z.getMinute(), z.getSecond());
        String validez = fijo ? "A" : "V";
        String latN = nmea(lat, true);
        String lonN = nmea(lon, false);
        double nudos = Math.max(0.0, velocidadKmh) / 1.852;
        return "imei:" + imei + ",tracker," + local + ",,F," + utc + "," + validez + ","
                + latN.substring(0, latN.length() - 1) + "," + latN.charAt(latN.length() - 1) + ","
                + lonN.substring(0, lonN.length() - 1) + "," + lonN.charAt(lonN.length() - 1) + ","
                + String.format(Locale.US, "%.2f", nudos) + ",0;";
    }

    public static String mensajeProtocolo(String protocolo, String imei, Instant cuando,
                                          double lat, double lon, double velocidadKmh, boolean fijo) {
        if ("gps103".equals(protocolo)) {
            return mensajeGps103(imei, cuando, lat, lon, velocidadKmh, fijo);
        }
        return mensajeTk103(imei, cuando, lat, lon, velocidadKmh, fijo);
    }

    public static int puertoPorDefecto(String protocolo) {
        return "gps103".equals(protocolo) ? 5001 : 5002;
    }

    // --- opciones y arranque ------------------------------------------------

    public static final class Opciones {
        public String api = env("ECORUTA_API", "http://localhost:8080");
        public String credencial = env("ECORUTA_CREDENCIAL", null);
        public double velocidad = 30;
        public double intervalo = 2;
        public double esperaParada = 5;
        public double esperaConReserva = 20;
        public Integer rutaId;
        public int vueltas = 0;
        public String modo = "directo";
        public String traccarHost = "127.0.0.1";
        public Integer traccarPuerto;
        public String protocolo = "tk103";
        public String imei = IMEI_DEFECTO;
        public double antiguedadHoras = 0;
        public boolean fijo = true;
        public int mensajes = 0;
        public String tokenAdmin = env("ECORUTA_ADMIN_TOKEN", null);
        public Long equipoId;
    }

    public record Resultado(int enviadas, int vueltas) {
    }

    public static void main(String[] args) {
        try {
            Opciones o = parsear(args);
            Estado estado = new Estado();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> estado.seguir = false));
            Resultado r = correr(o, estado);
            System.out.printf("%nDetenido. %d posiciones reportadas, %d vueltas.%n",
                    r.enviadas(), r.vueltas());
        } catch (Salida e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static Resultado correr(Opciones o) {
        return correr(o, new Estado());
    }

    static Resultado correr(Opciones o, Estado estado) {
        o.api = o.api.replaceAll("/$", "");
        RutaCargada ruta = cargarRuta(o);
        if (o.credencial == null && "directo".equals(o.modo)) {
            o.credencial = aprovisionar(o, ruta.id());
        } else if (o.credencial == null && "traccar".equals(o.modo)) {
            try {
                o.credencial = aprovisionar(o, ruta.id());
            } catch (RuntimeException e) {
                System.err.println("No se aprovisiono equipo (" + e.getMessage()
                        + "). El INSERT de abajo lleva el equipo_id a mano.");
            }
        }
        if ("traccar".equals(o.modo)) {
            String equipo = o.equipoId == null ? "<id del equipo ACTIVO del bus>" : String.valueOf(o.equipoId);
            System.out.println("Asocia el IMEI al equipo antes de esperar telemetria:");
            System.out.println("  INSERT INTO dispositivos_externos (identificador, equipo_id)");
            System.out.println("  VALUES ('" + o.imei + "', " + equipo + ");");
        }

        Recorrido recorrido = new Recorrido(ruta.trazado(), ruta.paradas());
        double vueltaMin = (recorrido.largo / 1000) / Math.max(o.velocidad, 0.1) * 60;
        System.out.println("Ruta      : " + ruta.nombre());
        System.out.println("Recorrido : " + String.format(Locale.US, "%.2f", recorrido.largo / 1000)
                + " km, " + recorrido.puntos.size() + " vertices, "
                + recorrido.paradas.size() + " paradas");
        System.out.println("Modo      : " + o.modo
                + ("traccar".equals(o.modo) ? " " + o.protocolo + " " + o.traccarHost + ":" + puertoTraccar(o) : ""));
        System.out.println("Velocidad : " + String.format(Locale.US, "%.0f", o.velocidad)
                + " km/h  ->  vuelta de ~" + String.format(Locale.US, "%.1f", vueltaMin) + " min");
        System.out.println("Ctrl-C para parar.\n");

        double metrosPorTic = o.velocidad * 1000 / 3600 * o.intervalo;
        double avance = 0.0;
        while (estado.seguir) {
            if (o.mensajes > 0 && estado.enviadas >= o.mensajes) {
                break;
            }
            if (o.vueltas > 0 && estado.vueltas >= o.vueltas) {
                break;
            }
            double anterior = avance;
            avance += metrosPorTic;
            if (avance >= recorrido.largo) {
                avance -= recorrido.largo;
                estado.vueltas++;
                System.out.println("  -- vuelta " + estado.vueltas + " completada --");
            }
            ParadaEnRuta parada = paradaEn(recorrido.paradas, anterior % recorrido.largo,
                    avance % recorrido.largo);
            if (parada != null) {
                avance = parada.metros();
                int reservas = reservasEn(o, ruta.id(), parada.id());
                double espera = reservas > 0 ? o.esperaConReserva : o.esperaParada;
                String extra = reservas > 0 ? ", " + reservas + " esperando" : "";
                System.out.printf(Locale.US, "  %5.2f km  %.6f, %.6f  -- parada: %s%s (%.0f s)%n",
                        parada.metros() / 1000, parada.ubicacion().latitud(),
                        parada.ubicacion().longitud(), parada.nombre(), extra, espera);
                long fin = System.nanoTime() + (long) (espera * 1_000_000_000L);
                while (estado.seguir && System.nanoTime() < fin) {
                    if (o.mensajes > 0 && estado.enviadas >= o.mensajes) {
                        break;
                    }
                    reportar(o, estado, parada.ubicacion(), 0.0);
                    dormir(Math.min(o.intervalo, Math.max(0.0, (fin - System.nanoTime()) / 1_000_000_000.0)));
                }
                continue;
            }
            Punto p = interpolar(recorrido.puntos, recorrido.acc, avance);
            reportar(o, estado, p, o.velocidad);
            System.out.printf(Locale.US, "  %5.2f km  %.6f, %.6f  %.0f km/h%n",
                    (avance % recorrido.largo) / 1000, p.latitud(), p.longitud(), o.velocidad);
            dormir(o.intervalo);
        }
        return new Resultado(estado.enviadas, estado.vueltas);
    }

    // --- interno ------------------------------------------------------------

    private static final class Recorrido {
        final List<Punto> puntos;
        final double[] acc;
        final double largo;
        final List<ParadaEnRuta> paradas;

        Recorrido(List<Punto> puntos, List<ParadaEnRuta> crudas) {
            this.puntos = puntos;
            this.acc = acumulado(puntos);
            this.largo = this.acc[this.acc.length - 1];
            this.paradas = situarParadas(puntos, this.acc, crudas);
        }
    }

    private static final class Estado {
        volatile boolean seguir = true;
        int enviadas;
        int vueltas;
        int fallos;
    }

    private record RutaCargada(long id, String nombre, List<Punto> trazado, List<ParadaEnRuta> paradas) {
    }

    private static void reportar(Opciones o, Estado estado, Punto p, double velocidad) {
        Instant cuando = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                .minus((long) (o.antiguedadHoras * 3600), ChronoUnit.SECONDS);
        try {
            if ("traccar".equals(o.modo)) {
                String msg = mensajeProtocolo(o.protocolo, o.imei, cuando,
                        p.latitud(), p.longitud(), velocidad, o.fijo);
                enviarTcp(o.traccarHost, puertoTraccar(o), o.protocolo, o.imei, msg);
            } else {
                String ts = cuando.toString();
                String cuerpo = "{\"posiciones\":[{\"latitud\":"
                        + String.format(Locale.US, "%.6f", p.latitud())
                        + ",\"longitud\":" + String.format(Locale.US, "%.6f", p.longitud())
                        + ",\"velocidadKmh\":" + velocidad
                        + ",\"timestamp\":\"" + ts + "\"}]}";
                Respuesta r = http("POST", o.api + "/api/v1/telemetria/posiciones", cuerpo,
                        Map.of("Authorization", "Bearer " + o.credencial,
                                "Content-Type", "application/json"));
                if (r.codigo() == 401 || r.codigo() == 403) {
                    throw new Salida("Credencial rechazada o revocada. Aprovisiona otro equipo.");
                }
                if (r.codigo() >= 400) {
                    throw new IOException("HTTP " + r.codigo() + " " + recortar(r.cuerpo(), 160));
                }
            }
            estado.enviadas++;
            estado.fallos = 0;
        } catch (Salida e) {
            throw e;
        } catch (Exception e) {
            estado.fallos++;
            System.out.println("  ! al reportar: " + e.getMessage());
            if (estado.fallos >= 5) {
                throw new Salida("Cinco fallos seguidos: se aborta.");
            }
        }
    }

    public static void enviarTcp(String host, int puerto, String protocolo, String imei, String mensaje)
            throws IOException {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, puerto), 8000);
            sock.setSoTimeout(400);
            if ("gps103".equals(protocolo)) {
                sock.getOutputStream().write(("imei:" + imei + ",").getBytes(StandardCharsets.US_ASCII));
                leerSilencio(sock);
            }
            sock.getOutputStream().write(mensaje.getBytes(StandardCharsets.US_ASCII));
            leerSilencio(sock);
        }
    }

    private static void leerSilencio(Socket sock) {
        try {
            InputStream in = sock.getInputStream();
            if (in.available() > 0 || sock.getSoTimeout() > 0) {
                in.read(new byte[256]);
            }
        } catch (IOException ignored) {
            // El decoder a veces no responde; el reporte ya se envio.
        }
    }

    private static int puertoTraccar(Opciones o) {
        return o.traccarPuerto != null ? o.traccarPuerto : puertoPorDefecto(o.protocolo);
    }

    private static RutaCargada cargarRuta(Opciones o) {
        Respuesta r = http("GET", o.api + "/api/v1/rutas", null, Map.of());
        if (r.codigo() != 200 || r.cuerpo().isBlank()) {
            throw new Salida("No hay rutas activas.");
        }
        Object json = Json.leer(r.cuerpo());
        List<?> rutas = Json.lista(json);
        if (rutas.isEmpty()) {
            throw new Salida("No hay rutas activas.");
        }
        Map<String, Object> ruta;
        if (o.rutaId == null) {
            ruta = Json.objeto(rutas.getFirst());
        } else {
            ruta = null;
            for (Object x : rutas) {
                Map<String, Object> cand = Json.objeto(x);
                if (o.rutaId.longValue() == Json.entero(cand.get("id"))) {
                    ruta = cand;
                    break;
                }
            }
            if (ruta == null) {
                throw new Salida("No hay ruta activa con id " + o.rutaId + ".");
            }
        }
        List<Punto> trazado = new ArrayList<>();
        for (Object p : Json.lista(ruta.get("trazado"))) {
            Map<String, Object> pt = Json.objeto(p);
            trazado.add(new Punto(Json.decimal(pt.get("latitud")), Json.decimal(pt.get("longitud"))));
        }
        if (trazado.isEmpty()) {
            throw new Salida("La ruta '" + ruta.get("nombre")
                    + "' no tiene trazado cargado. Aplica V6__circuito_de_ejemplo.sql.");
        }
        List<ParadaEnRuta> paradas = new ArrayList<>();
        for (Object p : Json.lista(ruta.get("paradas"))) {
            Map<String, Object> pa = Json.objeto(p);
            paradas.add(new ParadaEnRuta(0,
                    String.valueOf(pa.get("nombre")),
                    Json.entero(pa.get("id")),
                    new Punto(Json.decimal(pa.get("latitud")), Json.decimal(pa.get("longitud")))));
        }
        return new RutaCargada(Json.entero(ruta.get("id")), String.valueOf(ruta.get("nombre")),
                trazado, paradas);
    }

    private static String aprovisionar(Opciones o, long rutaId) {
        if (o.tokenAdmin == null || o.tokenAdmin.isBlank()) {
            throw new Salida("Falta ECORUTA_ADMIN_TOKEN (o pasa --credencial de un equipo ya creado).");
        }
        Respuesta lista = http("GET", o.api + "/api/v1/admin/vehiculos", null,
                Map.of("X-Admin-Token", o.tokenAdmin));
        if (lista.codigo() != 200) {
            throw new Salida("No se pudo listar vehiculos (" + lista.codigo()
                    + "). Revisa ECORUTA_ADMIN_TOKEN.");
        }
        List<?> vehiculos = Json.lista(Json.leer(lista.cuerpo()));
        if (vehiculos.isEmpty()) {
            throw new Salida("No hay vehiculos dados de alta. Crea uno en /api/v1/admin/vehiculos.");
        }
        Map<String, Object> elegido = null;
        for (Object v : vehiculos) {
            Map<String, Object> ve = Json.objeto(v);
            Object rid = ve.get("rutaId");
            if (rid != null && Json.entero(rid) == rutaId) {
                elegido = ve;
                break;
            }
        }
        if (elegido == null) {
            throw new Salida("Ningun vehiculo tiene asignada la ruta " + rutaId + ".");
        }
        long vehiculoId = Json.entero(elegido.get("id"));
        Respuesta alta = http("POST", o.api + "/api/v1/admin/vehiculos/" + vehiculoId + "/equipos",
                "{\"etiqueta\":\"Simulador de recorrido (desarrollo)\"}",
                Map.of("X-Admin-Token", o.tokenAdmin, "Content-Type", "application/json"));
        if (alta.codigo() / 100 != 2) {
            throw new Salida("No se pudo emitir equipo (" + alta.codigo() + "): " + recortar(alta.cuerpo(), 160));
        }
        Map<String, Object> cuerpo = Json.objeto(Json.leer(alta.cuerpo()));
        o.equipoId = cuerpo.get("id") == null ? null : Json.entero(cuerpo.get("id"));
        String credencial = String.valueOf(cuerpo.get("credencial"));
        System.out.println("Equipo aprovisionado para " + cuerpo.getOrDefault("vehiculo", vehiculoId) + ".");
        System.out.println("  Reutilizalo con:  --credencial " + credencial + "\n");
        return credencial;
    }

    private static int reservasEn(Opciones o, long rutaId, long paradaId) {
        try {
            Respuesta r = http("GET", o.api + "/api/v1/rutas/" + rutaId + "/resumen", null, Map.of());
            if (r.codigo() != 200) {
                return 0;
            }
            Map<String, Object> resumen = Json.objeto(Json.leer(r.cuerpo()));
            Map<String, Object> bloque = Json.objeto(resumen.get("reservasActivas"));
            for (Object fila : Json.lista(bloque.get("porParada"))) {
                Map<String, Object> f = Json.objeto(fila);
                if (Json.entero(f.get("paradaId")) == paradaId) {
                    return (int) Json.entero(f.get("reservasActivas"));
                }
            }
        } catch (RuntimeException ignored) {
            return 0;
        }
        return 0;
    }

    private record Respuesta(int codigo, String cuerpo) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static Respuesta http(String metodo, String url, String cuerpo, Map<String, String> cabeceras) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15));
            cabeceras.forEach(b::header);
            if (cuerpo != null) {
                b.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
            } else {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Respuesta(r.statusCode(), r.body() == null ? "" : r.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Sin respuesta de " + url + ": " + e.getMessage(), e);
        }
    }

    private static void dormir(double segundos) {
        if (segundos <= 0) {
            return;
        }
        try {
            Thread.sleep((long) (segundos * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String recortar(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String env(String clave, String defecto) {
        String v = System.getenv(clave);
        return v == null || v.isBlank() ? defecto : v;
    }

    static Opciones parsear(String[] args) {
        Opciones o = new Opciones();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--api" -> o.api = args[++i];
                case "--credencial" -> o.credencial = args[++i];
                case "--velocidad" -> o.velocidad = Double.parseDouble(args[++i]);
                case "--intervalo" -> o.intervalo = Double.parseDouble(args[++i]);
                case "--espera-parada" -> o.esperaParada = Double.parseDouble(args[++i]);
                case "--espera-con-reserva" -> o.esperaConReserva = Double.parseDouble(args[++i]);
                case "--ruta" -> o.rutaId = Integer.parseInt(args[++i]);
                case "--vueltas" -> o.vueltas = Integer.parseInt(args[++i]);
                case "--modo" -> o.modo = args[++i];
                case "--traccar-host" -> o.traccarHost = args[++i];
                case "--traccar-puerto" -> o.traccarPuerto = Integer.parseInt(args[++i]);
                case "--protocolo" -> o.protocolo = args[++i];
                case "--imei" -> o.imei = args[++i];
                case "--antiguedad-horas" -> o.antiguedadHoras = Double.parseDouble(args[++i]);
                case "--sin-fijo" -> o.fijo = false;
                case "--mensajes" -> o.mensajes = Integer.parseInt(args[++i]);
                default -> throw new Salida("Opcion desconocida: " + a);
            }
        }
        if (!"directo".equals(o.modo) && !"traccar".equals(o.modo)) {
            throw new Salida("--modo debe ser directo o traccar.");
        }
        if (!"tk103".equals(o.protocolo) && !"gps103".equals(o.protocolo)) {
            throw new Salida("--protocolo debe ser tk103 o gps103.");
        }
        return o;
    }

    static final class Salida extends RuntimeException {
        Salida(String m) {
            super(m);
        }
    }

    // --- lector JSON minimo (sin Jackson) -----------------------------------

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object leer(String texto) {
            Json j = new Json(texto);
            Object v = j.valor();
            j.saltar();
            return v;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> objeto(Object v) {
            if (v instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            throw new IllegalArgumentException("Se esperaba un objeto JSON.");
        }

        static List<?> lista(Object v) {
            if (v instanceof List<?> l) {
                return l;
            }
            return List.of();
        }

        static long entero(Object v) {
            if (v instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(v));
        }

        static double decimal(Object v) {
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            return Double.parseDouble(String.valueOf(v));
        }

        private Object valor() {
            saltar();
            if (i >= s.length()) {
                throw new IllegalArgumentException("JSON incompleto.");
            }
            char c = s.charAt(i);
            if (c == '{') {
                return objeto();
            }
            if (c == '[') {
                return arreglo();
            }
            if (c == '"') {
                return cadena();
            }
            if (c == 't') {
                i += 4;
                return Boolean.TRUE;
            }
            if (c == 'f') {
                i += 5;
                return Boolean.FALSE;
            }
            if (c == 'n') {
                i += 4;
                return null;
            }
            return numero();
        }

        private Map<String, Object> objeto() {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            saltar();
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                saltar();
                String k = cadena();
                saltar();
                if (s.charAt(i) != ':') {
                    throw new IllegalArgumentException("Falta ':'.");
                }
                i++;
                m.put(k, valor());
                saltar();
                char c = s.charAt(i++);
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("JSON de objeto mal formado.");
                }
            }
        }

        private List<Object> arreglo() {
            i++;
            List<Object> l = new ArrayList<>();
            saltar();
            if (s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(valor());
                saltar();
                char c = s.charAt(i++);
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("JSON de arreglo mal formado.");
                }
            }
        }

        private String cadena() {
            if (s.charAt(i) != '"') {
                throw new IllegalArgumentException("Se esperaba una cadena.");
            }
            i++;
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    b.append(switch (e) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> (char) Integer.parseInt(s.substring(i, i += 4), 16);
                        default -> e;
                    });
                } else {
                    b.append(c);
                }
            }
            throw new IllegalArgumentException("Cadena sin cerrar.");
        }

        private Number numero() {
            int ini = i;
            if (s.charAt(i) == '-') {
                i++;
            }
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || ".eE+-".indexOf(s.charAt(i)) >= 0)) {
                i++;
            }
            String n = s.substring(ini, i);
            if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) {
                return Double.parseDouble(n);
            }
            return Long.parseLong(n);
        }

        private void saltar() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
