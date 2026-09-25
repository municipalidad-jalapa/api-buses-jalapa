package gt.muni.jalapa.ecoruta.exportacion.servicio;

import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository.FilaDemanda;
import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository.FilaRecorrido;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Arma el .xlsx de la exportacion del servicio (HU Desarrollo-86).
 *
 * <p>Tres hojas: {@code Resumen} (periodo, totales y como leer el archivo),
 * {@code Demanda} y {@code Recorridos}. Las fechas y los numeros se escriben como
 * fechas y numeros de Excel, no como texto, para que la Municipalidad pueda
 * filtrar, sumar y hacer tablas dinamicas sin limpiar nada.
 *
 * <p>Solo recibe filas ya agregadas: esta clase no tiene de donde sacar un dato
 * personal aunque alguien quisiera escribirlo.
 *
 * <p>Los anchos de columna son fijos a proposito. {@code autoSizeColumn} necesita
 * fuentes del sistema y el contenedor de produccion (JRE alpine) no las trae.
 */
final class LibroDelServicio {

    static final String HOJA_RESUMEN = "Resumen";
    static final String HOJA_DEMANDA = "Demanda";
    static final String HOJA_RECORRIDOS = "Recorridos";

    static final List<String> COLUMNAS_DEMANDA = List.of(
            "Fecha", "Ruta", "N.º de parada", "Parada", "Reservas creadas", "Abordaron",
            "Canceladas", "Expiradas", "Vigentes al exportar", "Declararon que no abordaron");

    static final List<String> COLUMNAS_RECORRIDOS = List.of(
            "Fecha", "Bus", "Placa", "Ruta", "Lecturas GPS", "Primera lectura", "Última lectura",
            "Distancia estimada (km)", "Velocidad promedio en movimiento (km/h)", "Paradas atendidas");

    private static final int[] ANCHOS_DEMANDA = {12, 42, 14, 38, 18, 12, 13, 12, 20, 28};
    private static final int[] ANCHOS_RECORRIDOS = {12, 12, 12, 42, 14, 16, 16, 24, 34, 18};

    private LibroDelServicio() {
    }

    static byte[] armar(LocalDate desde, LocalDate hasta, ZoneId zona, Instant generadoEn,
                        List<FilaDemanda> demanda, List<FilaRecorrido> recorridos) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            hojaResumen(libro, estilos, desde, hasta, zona, generadoEn, demanda, recorridos);
            hojaDemanda(libro, estilos, demanda);
            hojaRecorridos(libro, estilos, zona, recorridos);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo armar el libro de exportacion", e);
        }
    }

    private static void hojaResumen(Workbook libro, Estilos estilos, LocalDate desde, LocalDate hasta,
                                    ZoneId zona, Instant generadoEn,
                                    List<FilaDemanda> demanda, List<FilaRecorrido> recorridos) {
        Sheet hoja = libro.createSheet(HOJA_RESUMEN);
        hoja.setColumnWidth(0, 44 * 256);
        hoja.setColumnWidth(1, 26 * 256);
        hoja.setColumnWidth(2, 90 * 256);

        int fila = 0;
        celda(hoja.createRow(fila++), 0, "EcoRuta — Datos del servicio de bus eléctrico", estilos.titulo);
        fila++;

        etiqueta(hoja.createRow(fila++), "Desde", estilos).setCellValue(desde);
        estilizar(hoja.getRow(fila - 1), 1, estilos.fecha);
        etiqueta(hoja.createRow(fila++), "Hasta (inclusive)", estilos).setCellValue(hasta);
        estilizar(hoja.getRow(fila - 1), 1, estilos.fecha);
        etiqueta(hoja.createRow(fila++), "Zona horaria de los días", estilos).setCellValue(zona.getId());
        etiqueta(hoja.createRow(fila++), "Generado", estilos)
                .setCellValue(generadoEn.atZone(zona).toLocalDateTime());
        estilizar(hoja.getRow(fila - 1), 1, estilos.fechaHora);
        fila++;

        celda(hoja.createRow(fila++), 0, "Totales del periodo", estilos.subtitulo);
        long reservas = demanda.stream().mapToLong(FilaDemanda::reservas).sum();
        long abordaron = demanda.stream().mapToLong(FilaDemanda::abordaron).sum();
        long canceladas = demanda.stream().mapToLong(FilaDemanda::canceladas).sum();
        long expiradas = demanda.stream().mapToLong(FilaDemanda::expiradas).sum();
        long vigentes = demanda.stream().mapToLong(FilaDemanda::vigentes).sum();
        long noAbordaron = demanda.stream().mapToLong(FilaDemanda::declararonNoAbordo).sum();
        long lecturas = recorridos.stream().mapToLong(FilaRecorrido::lecturas).sum();
        double km = recorridos.stream().mapToDouble(FilaRecorrido::distanciaMetros).sum() / 1000.0;

        total(hoja, fila++, "Reservas creadas", reservas, estilos);
        total(hoja, fila++, "Abordaron", abordaron, estilos);
        total(hoja, fila++, "Canceladas", canceladas, estilos);
        total(hoja, fila++, "Expiradas", expiradas, estilos);
        total(hoja, fila++, "Vigentes al exportar", vigentes, estilos);
        total(hoja, fila++, "Declararon que no abordaron", noAbordaron, estilos);
        total(hoja, fila++, "Lecturas GPS", lecturas, estilos);
        Row filaKm = hoja.createRow(fila++);
        etiqueta(filaKm, "Distancia estimada (km)", estilos);
        Cell celdaKm = filaKm.createCell(1);
        celdaKm.setCellValue(km);
        celdaKm.setCellStyle(estilos.decimal);
        fila++;

        celda(hoja.createRow(fila++), 0, "Cómo leer este archivo", estilos.subtitulo);
        for (String nota : List.of(
                "Hoja Demanda: una fila por día, ruta y parada. Las reservas se cuentan en el día en que se crearon; "
                        + "su estado es el que tenían al generar este archivo.",
                "Hoja Recorridos: una fila por día y bus, calculada con las lecturas del GPS. La distancia es una "
                        + "estimación: se omiten los tramos con huecos de señal y el GPS de un bus detenido varía un poco.",
                "Privacidad: el archivo contiene solo totales. No incluye identificadores de dispositivos, de "
                        + "reservas ni de personas, ni la hora de reservas individuales.")) {
            celda(hoja.createRow(fila++), 0, nota, estilos.normal);
        }
    }

    private static void hojaDemanda(Workbook libro, Estilos estilos, List<FilaDemanda> demanda) {
        Sheet hoja = libro.createSheet(HOJA_DEMANDA);
        encabezado(hoja, estilos, COLUMNAS_DEMANDA, ANCHOS_DEMANDA);

        int numero = 1;
        for (FilaDemanda d : demanda) {
            Row fila = hoja.createRow(numero++);
            fecha(fila, 0, d.fecha(), estilos);
            celda(fila, 1, d.ruta(), estilos.normal);
            entero(fila, 2, d.ordenParada(), estilos);
            celda(fila, 3, d.parada(), estilos.normal);
            entero(fila, 4, d.reservas(), estilos);
            entero(fila, 5, d.abordaron(), estilos);
            entero(fila, 6, d.canceladas(), estilos);
            entero(fila, 7, d.expiradas(), estilos);
            entero(fila, 8, d.vigentes(), estilos);
            entero(fila, 9, d.declararonNoAbordo(), estilos);
        }
        cerrarTabla(hoja, numero, COLUMNAS_DEMANDA.size());
    }

    private static void hojaRecorridos(Workbook libro, Estilos estilos, ZoneId zona,
                                       List<FilaRecorrido> recorridos) {
        Sheet hoja = libro.createSheet(HOJA_RECORRIDOS);
        encabezado(hoja, estilos, COLUMNAS_RECORRIDOS, ANCHOS_RECORRIDOS);

        int numero = 1;
        for (FilaRecorrido r : recorridos) {
            Row fila = hoja.createRow(numero++);
            fecha(fila, 0, r.fecha(), estilos);
            celda(fila, 1, r.bus(), estilos.normal);
            celda(fila, 2, r.placa(), estilos.normal);
            celda(fila, 3, r.ruta() == null ? "Sin ruta asignada" : r.ruta(), estilos.normal);
            entero(fila, 4, r.lecturas(), estilos);

            Cell primera = fila.createCell(5);
            primera.setCellValue(r.primeraLectura().atZone(zona).toLocalDateTime());
            primera.setCellStyle(estilos.hora);
            Cell ultima = fila.createCell(6);
            ultima.setCellValue(r.ultimaLectura().atZone(zona).toLocalDateTime());
            ultima.setCellStyle(estilos.hora);

            Cell km = fila.createCell(7);
            km.setCellValue(r.distanciaMetros() / 1000.0);
            km.setCellStyle(estilos.decimal);

            Cell velocidad = fila.createCell(8);
            if (r.velocidadPromedioKmh() != null) {
                velocidad.setCellValue(r.velocidadPromedioKmh());
            }
            velocidad.setCellStyle(estilos.decimal);

            entero(fila, 9, r.paradasAtendidas(), estilos);
        }
        cerrarTabla(hoja, numero, COLUMNAS_RECORRIDOS.size());
    }

    private static void encabezado(Sheet hoja, Estilos estilos, List<String> columnas, int[] anchos) {
        Row fila = hoja.createRow(0);
        for (int i = 0; i < columnas.size(); i++) {
            celda(fila, i, columnas.get(i), estilos.encabezado);
            hoja.setColumnWidth(i, anchos[i] * 256);
        }
        fila.setHeightInPoints(32);
    }

    /** Fija el encabezado al desplazarse y agrega el filtro de Excel a toda la tabla. */
    private static void cerrarTabla(Sheet hoja, int filasEscritas, int columnas) {
        hoja.createFreezePane(0, 1);
        hoja.setAutoFilter(new CellRangeAddress(0, Math.max(filasEscritas - 1, 0), 0, columnas - 1));
    }

    private static void total(Sheet hoja, int numeroDeFila, String etiqueta, long valor, Estilos estilos) {
        Row fila = hoja.createRow(numeroDeFila);
        etiqueta(fila, etiqueta, estilos);
        entero(fila, 1, valor, estilos);
    }

    private static Cell etiqueta(Row fila, String texto, Estilos estilos) {
        celda(fila, 0, texto, estilos.etiqueta);
        Cell valor = fila.createCell(1);
        valor.setCellStyle(estilos.normal);
        return valor;
    }

    private static void celda(Row fila, int columna, String texto, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        // setCellValue(String) guarda texto, nunca una formula: un nombre de parada
        // que empiece con "=" no se ejecuta al abrir el archivo.
        celda.setCellValue(texto);
        celda.setCellStyle(estilo);
    }

    private static void entero(Row fila, int columna, long valor, Estilos estilos) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilos.entero);
    }

    private static void fecha(Row fila, int columna, LocalDate valor, Estilos estilos) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilos.fecha);
    }

    private static void estilizar(Row fila, int columna, CellStyle estilo) {
        fila.getCell(columna).setCellStyle(estilo);
    }

    /** Los estilos se crean una vez por libro: Excel limita cuantos admite (64 000). */
    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle subtitulo;
        final CellStyle etiqueta;
        final CellStyle normal;
        final CellStyle encabezado;
        final CellStyle entero;
        final CellStyle decimal;
        final CellStyle fecha;
        final CellStyle fechaHora;
        final CellStyle hora;

        Estilos(Workbook libro) {
            var formato = libro.getCreationHelper().createDataFormat();

            Font negrita = libro.createFont();
            negrita.setBold(true);
            Font grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);

            titulo = libro.createCellStyle();
            titulo.setFont(grande);
            subtitulo = libro.createCellStyle();
            subtitulo.setFont(negrita);
            etiqueta = libro.createCellStyle();
            etiqueta.setFont(negrita);
            normal = libro.createCellStyle();
            normal.setAlignment(HorizontalAlignment.LEFT);

            encabezado = libro.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            encabezado.setBorderBottom(BorderStyle.THIN);
            encabezado.setWrapText(true);
            encabezado.setVerticalAlignment(VerticalAlignment.CENTER);

            entero = libro.createCellStyle();
            entero.setDataFormat(formato.getFormat("#,##0"));
            decimal = libro.createCellStyle();
            decimal.setDataFormat(formato.getFormat("#,##0.00"));
            fecha = libro.createCellStyle();
            fecha.setDataFormat(formato.getFormat("yyyy-mm-dd"));
            fecha.setAlignment(HorizontalAlignment.LEFT);
            fechaHora = libro.createCellStyle();
            fechaHora.setDataFormat(formato.getFormat("yyyy-mm-dd hh:mm"));
            fechaHora.setAlignment(HorizontalAlignment.LEFT);
            hora = libro.createCellStyle();
            hora.setDataFormat(formato.getFormat("hh:mm:ss"));
            hora.setAlignment(HorizontalAlignment.LEFT);
        }
    }
}
