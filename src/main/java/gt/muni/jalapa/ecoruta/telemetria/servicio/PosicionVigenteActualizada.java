package gt.muni.jalapa.ecoruta.telemetria.servicio;

import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;

/**
 * Se publica tras cada lote aceptado que mueve la posicion vigente.
 *
 * <p>Lleva el DTO ya resuelto y NO la entidad, por dos razones:
 *
 * <ul>
 *   <li>Quien lo escucha lo hace despues del commit, cuando la sesion de
 *       Hibernate ya esta cerrada (open-in-view esta en false). Navegar el
 *       vehiculo LAZY de una PosicionHistorica ahi lanzaria
 *       LazyInitializationException.</li>
 *   <li>Deja el evento como contrato estable: quien se suscriba no queda atado
 *       al modelo de persistencia.</li>
 * </ul>
 */
public record PosicionVigenteActualizada(PosicionActualResponse posicion) {
}
