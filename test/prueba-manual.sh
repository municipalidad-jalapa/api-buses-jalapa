#!/usr/bin/env bash
# Prueba manual end-to-end de la HU Desarrollo-135 y Desarrollo-95.
#
# Requiere que YA esten arriba, segun la fase:
#   1) Postgres:  docker compose up -d db
#   2) La app:
#
#   FASE=ip (capacidad de IP baja, capacidad de dispositivo alta):
#     mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
#       -Decoruta.demanda.vigencia-minutos=1 \
#       -Decoruta.demanda.barrido-segundos=5 \
#       -Decoruta.rate-limit.por-ip-capacidad=5 \
#       -Decoruta.rate-limit.por-dispositivo-capacidad=1000 \
#       -Decoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
#
#   FASE=resto (capacidad de IP alta, dispositivo baja, ritmo bajo):
#     mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
#       -Decoruta.demanda.vigencia-minutos=1 \
#       -Decoruta.demanda.barrido-segundos=5 \
#       -Decoruta.rate-limit.por-ip-capacidad=1000 \
#       -Decoruta.rate-limit.por-dispositivo-capacidad=3 \
#       -Decoruta.demanda.ritmo-minimo-segundos=5 \
#       -Decoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
#
# Uso:
#   FASE=ip     bash test/prueba-manual.sh
#   FASE=resto  bash test/prueba-manual.sh

set -u

B="${BASE_URL:-http://localhost:8080}"
BR="$B/api/v1/reservas"
FASE="${FASE:-resto}"
DISP="dev-manual-$(date +%s)"
DB_CONTAINER="${DB_CONTAINER:-api-buses-jalapa-db-1}"
FALLOS=0

req() { curl -s -w '\nHTTP %{http_code}\n' "$@"; }
psql_q() { docker exec "$DB_CONTAINER" psql -U ecoruta -d ecoruta -tAc "$1"; }
ok() { echo "    [OK] $1"; }
fallo() { echo "    [FALLO] $1"; FALLOS=$((FALLOS + 1)); }
esperar_codigo() {
    # esperar_codigo <esperado> <obtenido> <descripcion>
    if [ "$2" = "$1" ]; then ok "$3 -> HTTP $2"; else fallo "$3 -> se esperaba HTTP $1, se obtuvo HTTP $2"; fi
}

echo "### HU Desarrollo-135 y Desarrollo-95 - prueba manual  ($(date))  fase=$FASE"
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
        CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BR/999999/renovacion" \
            -H "X-Dispositivo-Id: $DISP-ritmo")
        esperar_codigo 404 "$CODIGO" "dispositivo $DISP-ritmo, intento $i (id no existe, pero llega al controlador)"
    done
    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BR/999999/renovacion" \
        -H "X-Dispositivo-Id: $DISP-ritmo")
    esperar_codigo 429 "$CODIGO" "dispositivo $DISP-ritmo, intento 4 (cortado antes del controlador)"

    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BR/999999/renovacion" \
        -H "X-Dispositivo-Id: $DISP-otro")
    esperar_codigo 404 "$CODIGO" "otro dispositivo ($DISP-otro) no hereda el límite agotado"
    echo

    # ----------------------------------------------------------------------
    echo "--- Criterio: vigencia, expiracion y tareas (Desarrollo-135) ---"
    PARADA_LAT=$(curl -s "$B/api/v1/rutas" | sed -n 's/.*"latitud":[[:space:]]*\([0-9.-]*\).*/\1/p' | head -1)
    PARADA_LON=$(curl -s "$B/api/v1/rutas" | sed -n 's/.*"longitud":[[:space:]]*\([0-9.-]*\).*/\1/p' | head -1)
    PARADA_LAT="${PARADA_LAT:-14.634878}"
    PARADA_LON="${PARADA_LON:--89.981202}"

    echo "1. crear -> se espera HTTP 201, estado ACTIVA"
    RESP=$(curl -s -XPOST "$BR" -H 'Content-Type: application/json' -d "{\"dispositivoId\":\"$DISP\",\"paradaId\":1,\"latitud\":$PARADA_LAT,\"longitud\":$PARADA_LON}")
    ID=$(echo "$RESP" | sed -E 's/.*"id":([0-9]+).*/\1/')
    if [ -n "$ID" ]; then
        ok "reserva creada con id $ID"
    else
        fallo "no se pudo crear reserva"
    fi
    echo

    echo "2. renovar $ID estando vigente -> se espera HTTP 200, estado RENOVADA"
    req -XPOST "$BR/$ID/renovacion" -H "X-Dispositivo-Id: $DISP"
    echo

    echo "3. renovar id inexistente -> se espera HTTP 404"
    req -XPOST "$BR/999999/renovacion" -H "X-Dispositivo-Id: $DISP"
    echo

    echo "4. body sin dispositivoId -> se espera HTTP 400"
    req -XPOST "$BR" -H 'Content-Type: application/json' -d "{\"paradaId\":1,\"latitud\":$PARADA_LAT,\"longitud\":$PARADA_LON}"
    echo

    echo "### esperando 75 s a que venza (vigencia=1min) y actue la tarea @Scheduled (cada 5 s)..."
    sleep 75
    echo

    echo "5. estado en BD de la reserva $ID (sin intervencion)"
    psql_q "SELECT id, dispositivo_id, estado, (expira_en < now()) AS vencida FROM registros_espera WHERE id = $ID"
    echo

    echo "6. renovar $ID ya EXPIRADA -> se espera HTTP 422"
    req -XPOST "$BR/$ID/renovacion" -H "X-Dispositivo-Id: $DISP"
    echo
fi

# ----------------------------------------------------------------------------
echo "### resumen fase=$FASE: $FALLOS fallo(s)"
[ "$FALLOS" -eq 0 ] && echo "### TODO OK" || echo "### REVISAR LOS [FALLO] DE ARRIBA"
exit $([ "$FALLOS" -eq 0 ] && echo 0 || echo 1)