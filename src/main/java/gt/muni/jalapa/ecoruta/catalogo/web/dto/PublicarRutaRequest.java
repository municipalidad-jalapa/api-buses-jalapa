package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** true: el pasajero ve la ruta; false: vuelve a borrador. */
public record PublicarRutaRequest(@Schema(example = "true") boolean activa) {
}
