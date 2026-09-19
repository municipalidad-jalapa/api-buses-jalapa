#!/usr/bin/env bash
# Prueba manual end-to-end de la HU Desarrollo-95 (endurecer la seguridad de la API).
#
# El limite por IP es UNA sola bolsa compartida por todas las rutas publicas
# (por diseno: HU Desarrollo-95, criterio 1). Por eso esta prueba corre en DOS
# fases, cada una contra su propia instancia de la app con limites distintos
# -- exactamente como RateLimitPorIpIT y RateLimitPorDispositivoIT lo hacen en
# el suite automatico: si todo compartiera una sola instancia con capacidad
# baja, la fase de IP agotaria la bolsa antes de llegar a las demas.
#
# Uso:
#   FASE=ip     bash test/prueba-manual.sh   # con la app de la fase "ip" arriba
#   FASE=resto  bash test/prueba-manual.sh   # con la app de la fase "resto" arriba
#
# Requiere que YA esten arriba, segun la fase:
#   1) Postgres:  docker compose up -d db
#   2) La app:
#
#   FASE=ip (capacidad de IP baja, capacidad de dispositivo alta para que no
#            interfiera):
#     mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
#       -Decoruta.rate-limit.por-ip-capacidad=5 \
#       -Decoruta.rate-limit.por-dispositivo-capacidad=1000 \
#       -Decoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
#
#   FASE=resto (capacidad de IP alta para que no interfiera, capacidad de
#               dispositivo baja, ritmo minimo bajo):
#     mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
#       -Decoruta.rate-limit.por-ip-capacidad=1000 \
#       -Decoruta.rate-limit.por-dispositivo-capacidad=3 \
#       -Decoruta.demanda.ritmo-minimo-segundos=5 \
#       -Decoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
set -u

B="${BASE_URL:-http://localhost:8080}"
FASE="${FASE:-resto}"
DISP="dev-manual-$(date +%s)"
FALLOS=0

ok() { echo "    [OK] $1"; }
fallo() { echo "    [FALLO] $1"; FALLOS=$((FALLOS + 1)); }
esperar_codigo() {
    # esperar_codigo <esperado> <obtenido> <descripcion>
    if [ "$2" = "$1" ]; then ok "$3 -> HTTP $2"; else fallo "$3 -> se esperaba HTTP $1, se obtuvo HTTP $2"; fi
}

echo "### HU Desarrollo-95 - prueba manual   ($(date))   fase=$FASE"
echo "### dispositivo base de prueba: $DISP"
echo

if [ "$FASE" = "ip" ]; then
    # --------------------------------------------------------------------
    echo "--- Criterio 1: límite por IP (requiere por-ip-capacidad=5) ---"
    for i in 1 2 3 4 5; do
        CODIGO=$(curl -s -o /dev/null -w '%{http_code}' "$B/api/v1/rutas")
        esperar_codigo 200 "$CODIGO" "intento $i dentro del cupo"
    done
    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' "$B/api/v1/rutas")
    esperar_codigo 429 "$CODIGO" "intento 6, ya sin cupo"
    echo

    echo "--- Criterio 1: una ruta pública fuera de la lista protegida no se limita ---"
    # /actuator/health es publico pero no es de negocio: si se limitara, el
    # healthcheck de docker compose (ADR-009) podria tumbar el contenedor.
    OK_SALUD=true
    for i in 1 2 3 4 5 6; do
        CODIGO=$(curl -s -o /dev/null -w '%{http_code}' "$B/actuator/health")
        [ "$CODIGO" = "200" ] || OK_SALUD=false
    done
    $OK_SALUD && ok "6 peticiones a /actuator/health, todas 200 pese al límite de IP agotado" \
              || fallo "/actuator/health se vio afectado por el límite de IP"
    echo
else
    # --------------------------------------------------------------------
    echo "--- Criterio 3: cabeceras de seguridad ---"
    CABECERAS=$(curl -sD - -o /dev/null "$B/api/v1/rutas")
    echo "$CABECERAS" | grep -qi '^x-frame-options: DENY' && ok "X-Frame-Options: DENY" || fallo "X-Frame-Options ausente o distinto de DENY"
    echo "$CABECERAS" | grep -qi '^x-content-type-options: nosniff' && ok "X-Content-Type-Options: nosniff" || fallo "X-Content-Type-Options ausente"
    echo "$CABECERAS" | grep -qi '^referrer-policy: strict-origin-when-cross-origin' && ok "Referrer-Policy correcta" || fallo "Referrer-Policy ausente o distinta"
    echo "$CABECERAS" | grep -qi '^permissions-policy:' && ok "Permissions-Policy presente" || fallo "Permissions-Policy ausente"
    echo "$CABECERAS" | grep -qi '^content-security-policy:' && ok "Content-Security-Policy presente en /api/**" || fallo "CSP ausente en /api/**"

    CABECERAS_DOCS=$(curl -sD - -o /dev/null "$B/v3/api-docs")
    if echo "$CABECERAS_DOCS" | grep -qi '^content-security-policy:'; then
        fallo "CSP presente en /v3/api-docs (rompería swagger-ui)"
    else
        ok "CSP ausente fuera de /api/** (swagger-ui a salvo)"
    fi
    echo

    # ----------------------------------------------------------------------
    echo "--- Criterio 2: CORS restringido a orígenes conocidos ---"
    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X OPTIONS "$B/api/v1/rutas" \
        -H 'Origin: https://qa.buses.jalapa.gob.gt' -H 'Access-Control-Request-Method: GET')
    esperar_codigo 200 "$CODIGO" "preflight desde origen permitido"

    CABECERAS_NEGADO=$(curl -sD - -o /dev/null -X OPTIONS "$B/api/v1/rutas" \
        -H 'Origin: https://sitio-no-autorizado.com' -H 'Access-Control-Request-Method: GET')
    if echo "$CABECERAS_NEGADO" | grep -qi '^access-control-allow-origin:'; then
        fallo "preflight desde origen NO autorizado trae Access-Control-Allow-Origin"
    else
        ok "preflight desde origen NO autorizado sin Access-Control-Allow-Origin"
    fi
    echo

    # ----------------------------------------------------------------------
    echo "--- Criterio 1: límite por dispositivo (requiere por-dispositivo-capacidad=3) ---"
    for i in 1 2 3; do
        CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/v1/reservas/999999/renovacion" \
            -H "X-Dispositivo-Id: $DISP-ritmo")
        esperar_codigo 404 "$CODIGO" "dispositivo $DISP-ritmo, intento $i (id no existe, pero llega al controlador)"
    done
    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/v1/reservas/999999/renovacion" \
        -H "X-Dispositivo-Id: $DISP-ritmo")
    esperar_codigo 429 "$CODIGO" "dispositivo $DISP-ritmo, intento 4 (cortado antes del controlador)"

    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/v1/reservas/999999/renovacion" \
        -H "X-Dispositivo-Id: $DISP-otro")
    esperar_codigo 404 "$CODIGO" "otro dispositivo ($DISP-otro) no hereda el límite agotado"
    echo

    # ----------------------------------------------------------------------
    echo "--- Criterio 4: ritmo imposible para una persona (requiere ritmo-minimo-segundos=5) ---"
    PARADA_LAT=$(curl -s "$B/api/v1/rutas" | sed -n 's/.*"latitud":[[:space:]]*\([0-9.-]*\).*/\1/p' | head -1)
    PARADA_LON=$(curl -s "$B/api/v1/rutas" | sed -n 's/.*"longitud":[[:space:]]*\([0-9.-]*\).*/\1/p' | head -1)
    PARADA_LAT="${PARADA_LAT:-14.634878}"
    PARADA_LON="${PARADA_LON:--89.981202}"
    DISP_RH="$DISP-humano"

    RESP=$(curl -s -X POST "$B/api/v1/reservas" -H 'Content-Type: application/json' \
        -d "{\"dispositivoId\":\"$DISP_RH\",\"paradaId\":1,\"latitud\":$PARADA_LAT,\"longitud\":$PARADA_LON}")
    echo "    crear -> $RESP"
    ID=$(echo "$RESP" | sed -nE 's/.*"id":([0-9]+).*/\1/p')
    if [ -n "$ID" ]; then
        ok "primera reserva creada (id=$ID)"

        CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/v1/reservas/$ID" -H "X-Dispositivo-Id: $DISP_RH")
        esperar_codigo 204 "$CODIGO" "cancelar de inmediato"

        RESP2=$(curl -s -w '\nHTTP %{http_code}' -X POST "$B/api/v1/reservas" -H 'Content-Type: application/json' \
            -d "{\"dispositivoId\":\"$DISP_RH\",\"paradaId\":1,\"latitud\":$PARADA_LAT,\"longitud\":$PARADA_LON}")
        echo "    reintento inmediato -> $RESP2"
        if echo "$RESP2" | grep -q 'HTTP 422' && echo "$RESP2" | grep -qi 'ritmo'; then
            ok "reintento inmediato rechazado por ritmo (422)"
        else
            fallo "reintento inmediato no fue rechazado por ritmo"
        fi

        echo "    esperando 6 s (ritmo-minimo-segundos=5)..."
        sleep 6
        CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/v1/reservas" -H 'Content-Type: application/json' \
            -d "{\"dispositivoId\":\"$DISP_RH\",\"paradaId\":1,\"latitud\":$PARADA_LAT,\"longitud\":$PARADA_LON}")
        esperar_codigo 201 "$CODIGO" "reintento tras esperar el ritmo mínimo"
    else
        fallo "no se pudo crear la primera reserva -> $RESP"
    fi
    echo
fi

# ----------------------------------------------------------------------------
echo "### resumen fase=$FASE: $FALLOS fallo(s)"
[ "$FALLOS" -eq 0 ] && echo "### TODO OK" || echo "### REVISAR LOS [FALLO] DE ARRIBA"
exit $([ "$FALLOS" -eq 0 ] && echo 0 || echo 1)
