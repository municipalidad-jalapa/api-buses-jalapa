package gt.muni.jalapa.ecoruta.tiempo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Parametros de tiempo estimado de una ruta (HU-72).
 *
 * <p>Una fila por ruta: dar de alta otra es insertar aqui, no escribir codigo.
 * Los horarios viajan como texto {@code HH:mm,HH:mm} para poder editarlos en
 * la base sin recompilar.
 */
@Entity
@Table(name = "parametros_de_ruta")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ParametrosDeRuta {

    @Id
    @Column(name = "ruta_id")
    @ToString.Include
    private Long rutaId;

    @Column(name = "duracion_recorrido_minutos", nullable = false)
    private int duracionRecorridoMinutos;

    @Column(name = "detencion_por_parada_segundos", nullable = false)
    private int detencionPorParadaSegundos;

    @Column(name = "permanencia_origen_minutos", nullable = false)
    private int permanenciaOrigenMinutos;

    @Column(name = "horarios_habil", nullable = false)
    private String horariosHabil;

    @Column(name = "horarios_jueves", nullable = false)
    private String horariosJueves;

    @Column(name = "horarios_domingo", nullable = false)
    private String horariosDomingo;

    public ParametrosDeRuta(Long rutaId,
                            int duracionRecorridoMinutos,
                            int detencionPorParadaSegundos,
                            int permanenciaOrigenMinutos,
                            String horariosHabil,
                            String horariosJueves,
                            String horariosDomingo) {
        this.rutaId = rutaId;
        this.duracionRecorridoMinutos = duracionRecorridoMinutos;
        this.detencionPorParadaSegundos = detencionPorParadaSegundos;
        this.permanenciaOrigenMinutos = permanenciaOrigenMinutos;
        this.horariosHabil = horariosHabil;
        this.horariosJueves = horariosJueves;
        this.horariosDomingo = horariosDomingo;
    }

    public List<LocalTime> horariosDe(TipoDeDia tipo) {
        String crudo = switch (tipo) {
            case HABIL -> horariosHabil;
            case JUEVES -> horariosJueves;
            case DOMINGO -> horariosDomingo;
        };
        return Arrays.stream(crudo.split(","))
                .map(String::trim)
                .filter(parte -> !parte.isEmpty())
                .map(LocalTime::parse)
                .toList();
    }
}
