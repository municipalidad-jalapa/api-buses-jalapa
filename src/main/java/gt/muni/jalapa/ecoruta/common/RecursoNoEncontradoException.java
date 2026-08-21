package gt.muni.jalapa.ecoruta.common;

public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String recurso, Object id) {
        super("%s con id %s no existe".formatted(recurso, id));
    }
}
