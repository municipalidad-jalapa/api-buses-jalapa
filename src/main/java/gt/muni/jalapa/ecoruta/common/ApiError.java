package gt.muni.jalapa.ecoruta.common;

import java.time.Instant;

/** Formato uniforme de error para toda la API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
