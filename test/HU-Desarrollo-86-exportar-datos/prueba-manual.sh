#!/usr/bin/env bash
# Prueba manual end-to-end de la HU Desarrollo-86 (exportar los datos del servicio).
#
# Habla con la API real por HTTP, como lo haria el panel. Dos modos:
#
#   MODO=simulado (por defecto)  Genera sus propios datos por los MISMOS caminos que usa
#                                produccion (el simulador GPS del repo para el bus y
#                                POST /api/v1/reservas para los pasajeros) y comprueba que
#                                el archivo trae exactamente lo generado.
#   MODO=real                    No genera nada: exporta lo que YA hay en el entorno (QA, o
#                                la base local con datos de uso) y comprueba lo que se
#                                puede afirmar sin conocer los datos: formato, errores,
#                                privacidad y, si hay acceso a la base, que los totales
#                                coincidan con un SQL independiente.
#
# Uso (con la app y la base ya arriba, ver MANUAL_DE_PRUEBAS.md):
#   ADMIN_TOKEN=... bash test/HU-Desarrollo-86-exportar-datos/prueba-manual.sh
#   MODO=real BASE_URL=https://qa... ADMIN_JWT=... bash test/HU-Desarrollo-86-exportar-datos/prueba-manual.sh
#
# Variables:
#   BASE_URL       http://localhost:8080
#   ADMIN_TOKEN    valor de ECORUTA_ADMIN_TOKEN de la app (cabecera X-Admin-Token)
#   ADMIN_JWT      alternativa: JWT del administrador (cabecera Authorization: Bearer)
#   PYTHON         interprete de Python 3 (por defecto: python)
#   CORS_ORIGEN    si se define, tambien prueba que CORS expone Content-Disposition
#   DB_CONTENEDOR  si se define (nombre de un contenedor con psql), contrasta contra SQL
#   DB_USUARIO / DB_NOMBRE   ecoruta / ecoruta
set -u
export PYTHONIOENCODING=utf-8   # Python en Windows imprime con la pagina de codigos de la consola

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"
B="${BASE_URL:-http://localhost:8080}"
MODO="${MODO:-simulado}"
PY="${PYTHON:-python}"
LECTOR="$AQUI/leer_xlsx.py"
TMP="$(mktemp -d)"
FALLOS=0
PRUEBAS=0
trap 'rm -rf "$TMP"' EXIT

ok()    { PRUEBAS=$((PRUEBAS + 1)); echo "    [OK] $1"; }
fallo() { PRUEBAS=$((PRUEBAS + 1)); echo "    [FALLO] $1"; FALLOS=$((FALLOS + 1)); }
esperar() { # esperar <esperado> <obtenido> <descripcion>
    if [ "$2" = "$1" ]; then ok "$3 -> $2"; else fallo "$3 -> se esperaba $1, se obtuvo $2"; fi
}
seccion() { echo; echo "--- $1 ---"; }

# Cabecera de autenticacion del administrador.
if [ -n "${ADMIN_JWT:-}" ]; then
    AUTH=(-H "Authorization: Bearer $ADMIN_JWT")
elif [ -n "${ADMIN_TOKEN:-}" ]; then
    AUTH=(-H "X-Admin-Token: $ADMIN_TOKEN")
else
    echo "Falta ADMIN_TOKEN (X-Admin-Token) o ADMIN_JWT. Ver el manual, seccion 3."; exit 2
fi

codigo() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
exportar_url() { echo "$B/api/v1/admin/exportaciones/servicio?desde=$1&hasta=$2"; }
# Python en Windows termina las lineas con \r\n: se quita el \r para poder comparar texto.
json() { "$PY" -c "import json,sys; d=json.load(sys.stdin); print($1)" | tr -d '\r'; }
lector() { "$PY" "$LECTOR" "$@" | tr -d '\r'; }
resumen() { lector "$1" --resumen | awk -F'\t' -v e="$2" '$1==e {print $2}'; }
filas() { lector "$1" --hoja "$2" | tail -n +2 | grep -c . ; }

# Hoy en Guatemala (UTC-6, sin horario de verano): asi caen bien los datos de "hoy".
HOY="$(date -u -d '6 hours ago' +%F)"
MANANA="$(date -u -d "$HOY + 1 day" +%F)"
HACE_DOS_ANIOS="$(date -u -d "$HOY - 2 years" +%F)"
HACE_7="$(date -u -d "$HOY - 7 days" +%F)"
HACE_30="$(date -u -d "$HOY - 30 days" +%F)"
HACE_200="$(date -u -d "$HOY - 200 days" +%F)"

echo "### HU Desarrollo-86 - prueba manual   ($(date))   modo=$MODO"
echo "### API: $B     hoy (Guatemala): $HOY"

# ---------------------------------------------------------------------------
seccion "0. La API esta arriba"
esperar 200 "$(codigo "$B/actuator/health")" "GET /actuator/health"

# ---------------------------------------------------------------------------
RESERVAS_CREADAS=0; CANCELADAS=0; ABORDARON=0
if [ "$MODO" = "simulado" ]; then
    seccion "1. Datos SIMULADOS: demanda (pasajeros) por POST /api/v1/reservas"
    # Linea base: lo que la base ya tenia hoy, para poder repetir el script sobre la misma base.
    curl -s "${AUTH[@]}" -o "$TMP/base.xlsx" "$(exportar_url "$HOY" "$HOY")"
    BASE_RES="$(resumen "$TMP/base.xlsx" 'Reservas creadas')"; BASE_ABO="$(resumen "$TMP/base.xlsx" 'Abordaron')"
    BASE_CAN="$(resumen "$TMP/base.xlsx" 'Canceladas')"; BASE_VIG="$(resumen "$TMP/base.xlsx" 'Vigentes al exportar')"
    BASE_LEC="$(resumen "$TMP/base.xlsx" 'Lecturas GPS')"; BASE_KM="$(resumen "$TMP/base.xlsx" 'Distancia estimada (km)')"
    echo "    linea base de hoy: ${BASE_RES:-0} reservas, ${BASE_LEC:-0} lecturas GPS"
    RUTA_JSON="$(curl -s "$B/api/v1/rutas/1")"
    N_PARADAS="$(echo "$RUTA_JSON" | json "len(d['paradas'])")"
    echo "    ruta 1 con $N_PARADAS paradas (calles reales de Jalapa, circuito de ejemplo)"
    : > "$TMP/dispositivos.txt"; : > "$TMP/reservas.txt"

    for i in $(seq 0 $((N_PARADAS - 1))); do
        PARADA="$(echo "$RUTA_JSON" | json "d['paradas'][$i]['id']")"
        LAT="$(echo "$RUTA_JSON" | json "d['paradas'][$i]['latitud']")"
        LON="$(echo "$RUTA_JSON" | json "d['paradas'][$i]['longitud']")"
        for k in $(seq 1 $(( (i % 3) + 1 ))); do
            DISP="$("$PY" -c 'import uuid; print(uuid.uuid4())' | tr -d '[:space:]')"
            RESP="$(curl -s -X POST "$B/api/v1/reservas" -H 'Content-Type: application/json' \
                -d "{\"dispositivoId\":\"$DISP\",\"paradaId\":$PARADA,\"latitud\":$LAT,\"longitud\":$LON}")"
            ID="$(echo "$RESP" | json "d.get('id','')" 2>/dev/null)"
            if [ -n "$ID" ]; then
                echo "$DISP" >> "$TMP/dispositivos.txt"; echo "$ID $DISP" >> "$TMP/reservas.txt"
                RESERVAS_CREADAS=$((RESERVAS_CREADAS + 1))
            fi
        done
    done
    esperar 15 "$RESERVAS_CREADAS" "reservas creadas por la API (8 paradas con 1,2,3,1,2,3,1,2)"

    # Las 3 primeras se cancelan; las 2 siguientes abordan.
    n=0
    while read -r ID DISP; do
        n=$((n + 1))
        if [ $n -le 3 ]; then
            [ "$(codigo -X DELETE "$B/api/v1/reservas/$ID" -H "X-Dispositivo-Id: $DISP")" = "204" ] \
                && CANCELADAS=$((CANCELADAS + 1))
        elif [ $n -le 5 ]; then
            [ "$(codigo -X POST "$B/api/v1/reservas/$ID/abordaje" -H "X-Dispositivo-Id: $DISP" \
                -H 'Content-Type: application/json' -d '{"subio":true}')" = "200" ] \
                && ABORDARON=$((ABORDARON + 1))
        fi
    done < "$TMP/reservas.txt"
    esperar 3 "$CANCELADAS" "reservas canceladas"
    esperar 2 "$ABORDARON" "reservas con abordaje del pasajero"

    seccion "2. Datos SIMULADOS: el bus recorre la ruta real (tools/simulador-gps.py)"
    # Una vuelta a 300 km/h con una lectura por segundo: ~1 minuto, ~62 lecturas.
    ECORUTA_ADMIN_TOKEN="${ADMIN_TOKEN:-}" "$PY" "$RAIZ/tools/simulador-gps.py" --api "$B" \
        --ruta 1 --velocidad 300 --intervalo 1 --espera-parada 0 --espera-con-reserva 0 --vueltas 1 \
        > "$TMP/simulador.log" 2>&1
    tail -3 "$TMP/simulador.log" | sed 's/^/    /'
    grep -q "vuelta 1 completada" "$TMP/simulador.log" && ok "el simulador completo una vuelta" \
        || fallo "el simulador no completo la vuelta (ver salida de arriba)"
fi

# ---------------------------------------------------------------------------
seccion "3. Acceso: solo el administrador"
esperar 401 "$(codigo "$(exportar_url "$HOY" "$HOY")")" "sin credencial"
esperar 401 "$(codigo -H 'X-Admin-Token: token-equivocado-pero-largo-0123456789' "$(exportar_url "$HOY" "$HOY")")" "con credencial equivocada"

seccion "4. Criterio: rango de fechas seleccionable (validaciones)"
esperar 400 "$(codigo "${AUTH[@]}" "$B/api/v1/admin/exportaciones/servicio?hasta=$HOY")" "falta 'desde'"
esperar 400 "$(codigo "${AUTH[@]}" "$B/api/v1/admin/exportaciones/servicio?desde=$HOY")" "falta 'hasta'"
esperar 400 "$(codigo "${AUTH[@]}" "$(exportar_url 15/09/2026 "$HOY")")" "formato de fecha distinto de AAAA-MM-DD"
esperar 422 "$(codigo "${AUTH[@]}" "$(exportar_url "$HOY" "$HACE_7")")" "rango invertido (desde > hasta)"
esperar 422 "$(codigo "${AUTH[@]}" "$(exportar_url "$HACE_DOS_ANIOS" "$MANANA")")" "rango de mas de 366 dias"
CUERPO="$(curl -s "${AUTH[@]}" "$(exportar_url "$HOY" "$HACE_7")")"
echo "$CUERPO" | grep -q '"status":422' && ok "el 422 viene en el formato ApiError: $(echo "$CUERPO" | json "d['message']")" \
    || fallo "el 422 no trae el formato ApiError: $CUERPO"

# ---------------------------------------------------------------------------
seccion "5. Criterio: exportacion en hoja de calculo"
ARCHIVO="$TMP/servicio.xlsx"
curl -s "${AUTH[@]}" -D "$TMP/cabeceras.txt" -o "$ARCHIVO" "$(exportar_url "$HOY" "$HOY")"
head -1 "$TMP/cabeceras.txt" | grep -q " 200" && ok "GET exportar hoy..hoy -> 200" || fallo "no respondio 200: $(head -1 "$TMP/cabeceras.txt")"
grep -qi '^content-type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' "$TMP/cabeceras.txt" \
    && ok "Content-Type es el de .xlsx" || fallo "Content-Type no es el de .xlsx"
grep -qi "^content-disposition: attachment; filename=\"ecoruta-servicio_${HOY}_a_${HOY}.xlsx\"" "$TMP/cabeceras.txt" \
    && ok "se descarga como ecoruta-servicio_${HOY}_a_${HOY}.xlsx" || fallo "Content-Disposition inesperado: $(grep -i content-disposition "$TMP/cabeceras.txt")"
grep -qi '^cache-control:.*no-store' "$TMP/cabeceras.txt" && ok "Cache-Control: no-store (no se guarda el reporte)" || fallo "falta Cache-Control: no-store"
[ "$(head -c 2 "$ARCHIVO")" = "PK" ] && ok "el contenido es un ZIP (.xlsx real, $(wc -c < "$ARCHIVO") bytes)" || fallo "el contenido no es un .xlsx"
HOJAS="$(lector "$ARCHIVO" --hojas | tr '\n' ',')"
esperar "Resumen,Demanda,Recorridos," "$HOJAS" "hojas del libro"

echo; echo "    Resumen del archivo:"; lector "$ARCHIVO" --resumen | sed 's/^/      /'

if [ "$MODO" = "simulado" ]; then
    seccion "6. El archivo trae exactamente lo generado (datos simulados)"
    # Lo generado por este script es la DIFERENCIA contra la linea base tomada antes de generar,
    # asi el script se puede repetir sobre la misma base.
    esperar $((${BASE_RES:-0} + RESERVAS_CREADAS)) "$(resumen "$ARCHIVO" 'Reservas creadas')" "Resumen: reservas creadas (base + $RESERVAS_CREADAS)"
    esperar $((${BASE_ABO:-0} + ABORDARON)) "$(resumen "$ARCHIVO" 'Abordaron')" "Resumen: abordaron (base + $ABORDARON)"
    esperar $((${BASE_CAN:-0} + CANCELADAS)) "$(resumen "$ARCHIVO" 'Canceladas')" "Resumen: canceladas (base + $CANCELADAS)"
    esperar $((${BASE_VIG:-0} + RESERVAS_CREADAS - ABORDARON - CANCELADAS)) "$(resumen "$ARCHIVO" 'Vigentes al exportar')" "Resumen: vigentes"
    if [ "${BASE_RES:-0}" = "0" ]; then
        esperar "$N_PARADAS" "$(filas "$ARCHIVO" Demanda)" "hoja Demanda: una fila por parada con demanda"
    else
        echo "    [--] hoja Demanda: no se cuentan filas porque la base ya tenia reservas de hoy"
    fi
    LECTURAS="$(resumen "$ARCHIVO" 'Lecturas GPS')"
    NUEVAS=$((${LECTURAS:-0} - ${BASE_LEC:-0}))
    [ "$NUEVAS" -ge 30 ] && ok "hoja Recorridos: $NUEVAS lecturas GPS nuevas del simulador" || fallo "muy pocas lecturas GPS nuevas: $NUEVAS"
    KM="$(resumen "$ARCHIVO" 'Distancia estimada (km)')"
    # La ruta 1 mide unos 5.17 km; con pasos de ~83 m se recortan un poco las esquinas.
    "$PY" -c "import sys; d = float('${KM:-0}') - float('${BASE_KM:-0}'); sys.exit(0 if 4.6 <= d <= 5.6 else 1)" \
        && ok "distancia estimada: ${KM} km en total, base ${BASE_KM:-0} (la vuelta nueva mide unos 5.17 km)" || fallo "distancia nueva fuera de lo esperable: '$KM' (base '$BASE_KM') km"
    echo; echo "    Hoja Demanda:"; lector "$ARCHIVO" --hoja Demanda | sed 's/^/      /'
    echo; echo "    Hoja Recorridos:"; lector "$ARCHIVO" --hoja Recorridos | sed 's/^/      /'

    seccion "7. El rango limita lo exportado"
    for RANGO in "$HACE_200:$HACE_30:de hace 200 a hace 30 dias" "$MANANA:$MANANA:solo manana"; do
        D="${RANGO%%:*}"; RESTO="${RANGO#*:}"; H="${RESTO%%:*}"; NOMBRE="${RESTO#*:}"
        curl -s "${AUTH[@]}" -o "$TMP/otro.xlsx" "$(exportar_url "$D" "$H")"
        esperar 0 "$(filas "$TMP/otro.xlsx" Demanda)" "rango '$NOMBRE': la hoja Demanda queda solo con encabezado"
        esperar 0 "$(resumen "$TMP/otro.xlsx" 'Reservas creadas')" "rango '$NOMBRE': el Resumen dice 0 reservas"
    done
fi

# ---------------------------------------------------------------------------
seccion "8. Criterio: no expone datos que identifiquen a un pasajero"
lector "$ARCHIVO" --texto > "$TMP/todo.txt"
# Cualquier UUID (asi son los identificadores de dispositivo que manda la app del pasajero).
UUIDS="$(grep -Eoi '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' "$TMP/todo.txt" | sort -u | wc -l)"
esperar 0 "$UUIDS" "identificadores con forma de UUID dentro del archivo (todas las partes del ZIP)"
if [ -s "$TMP/dispositivos.txt" ]; then
    ENCONTRADOS=0
    while read -r DISP; do grep -q "$DISP" "$TMP/todo.txt" && ENCONTRADOS=$((ENCONTRADOS + 1)); done < "$TMP/dispositivos.txt"
    esperar 0 "$ENCONTRADOS" "de los $(wc -l < "$TMP/dispositivos.txt") dispositivos que reservaron, cuantos aparecen en el archivo"
fi
ENCABEZADOS="$(lector "$ARCHIVO" --hoja Demanda | head -1; lector "$ARCHIVO" --hoja Recorridos | head -1)"
if echo "$ENCABEZADOS" | grep -Eqi 'dispositivo|pasajero|token|uuid|conductor'; then
    fallo "algun encabezado nombra a un pasajero, dispositivo o conductor: $ENCABEZADOS"
else
    ok "ningun encabezado de columna nombra a un pasajero, dispositivo, token o conductor"
fi

# ---------------------------------------------------------------------------
if [ -n "${CORS_ORIGEN:-}" ]; then
    seccion "9. CORS: el navegador puede leer el nombre del archivo"
    EXPUESTAS="$(curl -s -D - -o /dev/null "${AUTH[@]}" -H "Origin: $CORS_ORIGEN" "$(exportar_url "$HOY" "$HOY")" \
        | grep -i '^access-control-expose-headers:')"
    echo "$EXPUESTAS" | grep -qi 'content-disposition' && ok "Access-Control-Expose-Headers incluye Content-Disposition" \
        || fallo "CORS no expone Content-Disposition (origen $CORS_ORIGEN): '$EXPUESTAS'"
fi

# ---------------------------------------------------------------------------
if [ -n "${DB_CONTENEDOR:-}" ]; then
    seccion "10. Contraste con un SQL independiente (base: contenedor $DB_CONTENEDOR)"
    SQL="SELECT count(*) FROM registros_espera WHERE (creado_en AT TIME ZONE 'America/Guatemala')::date = DATE '$HOY'"
    EN_BD="$(docker exec "$DB_CONTENEDOR" psql -U "${DB_USUARIO:-ecoruta}" -d "${DB_NOMBRE:-ecoruta}" -tA -c "$SQL" 2>/dev/null | tr -d '[:space:]')"
    esperar "$EN_BD" "$(resumen "$ARCHIVO" 'Reservas creadas')" "reservas de hoy: SQL directo vs. Resumen del archivo"
    SQL="SELECT count(*) FROM posiciones_historicas WHERE vehiculo_id IS NOT NULL AND (registrado_en AT TIME ZONE 'America/Guatemala')::date = DATE '$HOY'"
    EN_BD="$(docker exec "$DB_CONTENEDOR" psql -U "${DB_USUARIO:-ecoruta}" -d "${DB_NOMBRE:-ecoruta}" -tA -c "$SQL" 2>/dev/null | tr -d '[:space:]')"
    esperar "$EN_BD" "$(resumen "$ARCHIVO" 'Lecturas GPS')" "lecturas GPS de hoy: SQL directo vs. Resumen del archivo"
fi

echo
echo "=== resumen modo=$MODO: $PRUEBAS comprobaciones, $FALLOS fallo(s) ==="
if [ "$FALLOS" -eq 0 ]; then echo "TODO OK"; else echo "HAY FALLOS"; fi
exit $((FALLOS > 0 ? 1 : 0))
