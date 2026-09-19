package gt.muni.jalapa.ecoruta.opiniones.dominio;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** En el JSON va en minusculas, como fija el contrato de SCRUM-26: queja | comentario | calificacion. */
public enum TipoOpinion {
    QUEJA,
    COMENTARIO,
    CALIFICACION;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Null si el valor no es un tipo conocido: la validacion responde 422. */
    @JsonCreator
    public static TipoOpinion de(String valor) {
        if (valor == null) {
            return null;
        }
        for (TipoOpinion tipo : values()) {
            if (tipo.name().equalsIgnoreCase(valor.trim())) {
                return tipo;
            }
        }
        return null;
    }
}
