#!/usr/bin/env bash
# Prueba manual end-to-end de la HU Desarrollo-135.
#
# Requiere que YA esten arriba:
#   1) Postgres:      docker compose up -d db
#   2) La aplicacion: mvn spring-boot:run \
#        -Dspring-boot.run.jvmArguments="-Decoruta.demanda.vigencia-minutos=1 -Decoruta.demanda.barrido-segundos=5"
#
# Uso:  bash test/prueba-manual.sh
set -u

B="${BASE_URL:-http://localhost:8080}/api/v1/reservas"
DB_CONTAINER="${DB_CONTAINER:-api-buses-jalapa-db-1}"
DISP="dev-manual-$(date +%s)"

req() { curl -s -w '\nHTTP %{http_code}\n' "$@"; }
psql_q() { docker exec "$DB_CONTAINER" psql -U ecoruta -d ecoruta -tAc "$1"; }

echo "### HU Desarrollo-135 - prueba manual   ($(date))"
echo "### dispositivo de prueba: $DISP"
echo

echo "--- 1. crear                       -> se espera HTTP 201, estado ACTIVA, expiraEn a +1 min"
RESP=$(curl -s -XPOST "$B" -H 'Content-Type: application/json' -d "{\"dispositivoId\":\"$DISP\",\"paradaId\":1}")
echo "$RESP"
ID=$(echo "$RESP" | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "    id creado = $ID"
echo

echo "--- 2. renovar $ID estando vigente  -> se espera HTTP 200, estado RENOVADA, mismo id, nuevo expiraEn"
req -XPOST "$B/$ID/renovacion"
echo

echo "--- 3. crear otra con el mismo dispositivo -> se espera HTTP 422"
req -XPOST "$B" -H 'Content-Type: application/json' -d "{\"dispositivoId\":\"$DISP\",\"paradaId\":1}"
echo

echo "--- 4. renovar id inexistente      -> se espera HTTP 404"
req -XPOST "$B/999999/renovacion"
echo

echo "--- 5. crear con parada inexistente -> se espera HTTP 404"
req -XPOST "$B" -H 'Content-Type: application/json' -d "{\"dispositivoId\":\"$DISP-x\",\"paradaId\":424242}"
echo

echo "--- 6. body sin dispositivoId       -> se espera HTTP 400"
req -XPOST "$B" -H 'Content-Type: application/json' -d '{"paradaId":1}'
echo

echo "### esperando 75 s a que venza (vigencia=1min) y actue la tarea @Scheduled (cada 5 s)..."
sleep 75
echo

echo "--- 7. estado en BD de la reserva $ID (la marca la tarea programada, sin intervencion)"
psql_q "SELECT id, dispositivo_id, estado, (expira_en < now()) AS vencida FROM registros_espera WHERE id = $ID"
echo

echo "--- 8. renovar $ID ya EXPIRADA      -> se espera HTTP 422"
req -XPOST "$B/$ID/renovacion"
echo

echo "--- 9. el mismo dispositivo crea otra -> se espera HTTP 201 (la EXPIRADA no bloquea)"
req -XPOST "$B" -H 'Content-Type: application/json' -d "{\"dispositivoId\":\"$DISP\",\"paradaId\":1}"
echo

echo "--- 10. filas del dispositivo en BD  -> 1 EXPIRADA + 1 ACTIVA"
psql_q "SELECT id, estado FROM registros_espera WHERE dispositivo_id = '$DISP' ORDER BY id"
echo

echo "### fin."
