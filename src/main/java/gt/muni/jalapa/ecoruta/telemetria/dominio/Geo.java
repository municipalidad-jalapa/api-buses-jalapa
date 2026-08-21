package gt.muni.jalapa.ecoruta.telemetria.dominio;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Unico lugar del codigo autorizado a construir o leer un {@link Point}.
 *
 * <p>ADR-007 lo llama "el error clasico del dominio": PostGIS y JTS trabajan en
 * (longitud, latitud), mientras los DTO de la API se llaman latitud/longitud y
 * se leen en el orden contrario. Centralizarlo en una fabrica hace que el error
 * solo se pueda cometer una vez, aqui, y que una prueba lo fije para siempre.
 */
public final class Geo {

    /**
     * El SRID va en la fabrica: si el Point sale con SRID 0, PostGIS rechaza el
     * INSERT con "Geometry SRID (0) does not match column SRID (4326)".
     */
    private static final GeometryFactory FABRICA =
            new GeometryFactory(new PrecisionModel(), 4326);

    private Geo() {
    }

    /** OJO: la coordenada se arma (longitud, latitud), no al reves. */
    public static Point punto(double latitud, double longitud) {
        return FABRICA.createPoint(new Coordinate(longitud, latitud));
    }

    public static double latitud(Point punto) {
        return punto.getY();
    }

    public static double longitud(Point punto) {
        return punto.getX();
    }
}
