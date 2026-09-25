package gt.muni.jalapa.ecoruta.panel.web;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Catálogo de solo lectura para filtrar opiniones y métricas históricas. */
@RestController
@RequiredArgsConstructor
public class CatalogoVehiculosController {
    private final JdbcTemplate jdbc;

    public record VehiculoFiltro(Long id, String identificador) {}

    @GetMapping("/api/v1/admin/catalogo/vehiculos")
    @Transactional(readOnly = true)
    public List<VehiculoFiltro> listar() {
        // Incluye inactivos: también pueden tener opiniones o abordajes históricos.
        return jdbc.query("SELECT id, identificador FROM vehiculos ORDER BY identificador, id",
                (rs, fila) -> new VehiculoFiltro(rs.getLong("id"), rs.getString("identificador")));
    }
}
