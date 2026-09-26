package gt.muni.jalapa.ecoruta.exportacion;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lee de vuelta el .xlsx de la exportacion, como lo haria la Municipalidad con Excel. */
final class LectorDeXlsx {

    private LectorDeXlsx() {
    }

    static Workbook abrir(byte[] archivo) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(archivo));
    }

    /** Las filas debajo del encabezado. */
    static List<Row> filasDeDatos(Sheet hoja) {
        List<Row> filas = new ArrayList<>();
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            filas.add(hoja.getRow(i));
        }
        return filas;
    }

    /** El valor numerico que esta a la derecha de una etiqueta de la hoja Resumen. */
    static double valorDelResumen(Sheet resumen, String etiqueta) {
        for (Row fila : resumen) {
            Cell primera = fila.getCell(0);
            if (primera != null && primera.getCellType() == CellType.STRING
                    && primera.getStringCellValue().equals(etiqueta)) {
                return fila.getCell(1).getNumericCellValue();
            }
        }
        throw new AssertionError("El resumen no tiene la etiqueta '" + etiqueta + "'");
    }

    /**
     * Todo el texto de todas las partes del .xlsx (es un ZIP de XML). Sirve para
     * afirmar que un dato NO esta en el archivo aunque estuviera en una columna
     * oculta o en las propiedades del documento, no solo en las celdas visibles.
     */
    static String todoElContenido(byte[] xlsx) throws IOException {
        StringBuilder todo = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry parte = zip.getNextEntry(); parte != null; parte = zip.getNextEntry()) {
                todo.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return todo.toString();
    }
}
