#!/usr/bin/env python3
"""Simulador del equipo a bordo: recorre la ruta y reporta posiciones (SCRUM-161).

Existe porque el bus real todavia no lleva el equipo instalado, y sin posiciones
entrando la pantalla del pasajero no se puede ver ni demostrar: el mapa sale con
el marcador quieto o directamente vacio.

No inventa el recorrido. Lo pide a GET /api/v1/rutas y camina el `trazado`, cuyos
vertices salen del levantamiento GPS de campo (V13__datos_reales_de_las_rutas_de_jalapa.sql).
Asi las coordenadas viven en un solo sitio: si la ruta cambia en la base, el
simulador cambia con ella y no hay dos verdades que se puedan desincronizar.

La semilla actual es el dato real de HU-41 (SCRUM-136): RUTA PRINCIPAL (circuito
completo) y RUTA SECUNDARIA (tramo parcial disponible).

Habla con la API igual que hablaria el equipo real: se autentica con credencial
de dispositivo en la cabecera Authorization (SCRUM-142) y manda lotes al mismo
POST /api/v1/telemetria/posiciones. No hay atajos ni puertas traseras, asi que
lo que se ve en la demo es la ruta de datos de produccion.

Uso:
    python3 tools/simulador-gps.py                       # aprovisiona equipo y arranca
    python3 tools/simulador-gps.py --velocidad 120       # vuelta rapida, para demo
    python3 tools/simulador-gps.py --credencial eq_xx.yy # reusa un equipo ya creado
    python3 tools/simulador-gps.py --vueltas 1           # una vuelta y termina
    python3 tools/simulador-gps.py --ruta 2              # recorre la ruta 2 con su bus

Cada ruta tiene su propio bus (V12). Con --ruta el simulador recorre esa ruta y,
si tiene que aprovisionar un equipo, lo monta en el bus asignado a esa ruta. Para
simular dos rutas a la vez, corre dos simuladores, uno por ruta, cada uno con la
credencial de su propio equipo.

En cada parada el bus se detiene EN la parada (no unos metros despues) y espera
--espera-parada segundos. Si en esa parada hay reservas activas espera mas
(--espera-con-reserva), igual que el bus real que se detiene a subir gente: asi
se ven en la demo los avisos de llegada y la pregunta de abordaje.

Solo biblioteca estandar: se ejecuta en cualquier maquina del equipo sin instalar
nada. Se corta con Ctrl-C.
"""

import argparse
import json
import math
import os
import signal
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

RADIO_TIERRA_M = 6371000.0


def metros(a, b):
    """Distancia entre dos (lat, lon) en metros. Haversine."""
    la1, lo1 = math.radians(a[0]), math.radians(a[1])
    la2, lo2 = math.radians(b[0]), math.radians(b[1])
    h = (math.sin((la2 - la1) / 2) ** 2
         + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2)
    return 2 * RADIO_TIERRA_M * math.asin(math.sqrt(h))


def peticion(url, metodo="GET", cuerpo=None, cabeceras=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    cab = {"Content-Type": "application/json"}
    cab.update(cabeceras or {})
    req = urllib.request.Request(url, data=datos, headers=cab, method=metodo)
    with urllib.request.urlopen(req, timeout=15) as r:
        texto = r.read().decode()
        return json.loads(texto) if texto else None


class Recorrido:
    """El trazado de la ruta, con las paradas situadas sobre el."""

    def __init__(self, trazado, paradas):
        # [(lat, lon)], cerrado: el ultimo vertice coincide con el primero
        self.puntos = [(p["latitud"], p["longitud"]) for p in trazado]
        self.acumulado = [0.0]
        for i in range(1, len(self.puntos)):
            self.acumulado.append(
                self.acumulado[-1] + metros(self.puntos[i - 1], self.puntos[i]))
        self.largo = self.acumulado[-1]

        # Cada parada, a cuantos metros del inicio queda. Se busca el vertice mas
        # cercano en vez de confiar en el orden: el trazado y las paradas son dos
        # columnas distintas y nadie garantiza que esten alineadas.
        self.paradas = []
        for pa in paradas:
            objetivo = (pa["latitud"], pa["longitud"])
            i = min(range(len(self.puntos)),
                    key=lambda j: metros(objetivo, self.puntos[j]))
            self.paradas.append((self.acumulado[i], pa["nombre"], pa["id"], objetivo))
        self.paradas.sort(key=lambda x: x[0])

    def en(self, distancia):
        """(lat, lon) a `distancia` metros del inicio, interpolando entre vertices.

        Interpolar importa: saltar de vertice en vertice daria un bus a tirones,
        con tramos rectos largos donde la calle tiene pocos vertices.
        """
        d = distancia % self.largo
        for i in range(1, len(self.acumulado)):
            if self.acumulado[i] >= d:
                tramo = self.acumulado[i] - self.acumulado[i - 1]
                t = (d - self.acumulado[i - 1]) / tramo if tramo else 0.0
                (la1, lo1), (la2, lo2) = self.puntos[i - 1], self.puntos[i]
                return (la1 + (la2 - la1) * t, lo1 + (lo2 - lo1) * t)
        return self.puntos[-1]

    def parada_en(self, desde, hasta):
        """La parada (metro, nombre, id, (lat, lon)) en (desde, hasta], si alguna.

        El tramo puede dar la vuelta al circuito (desde > hasta al cerrar la
        vuelta). Sin contemplarlo, la parada del cierre --el Parque Central, que
        esta en el metro 0-- no se anunciaria nunca.
        """
        for parada in self.paradas:
            metro = parada[0]
            dentro = (desde < metro <= hasta) if desde <= hasta \
                else (metro > desde or metro <= hasta)
            if dentro:
                return parada
        return None


def aprovisionar(api, token_admin, ruta_id=None):
    """Crea un equipo a bordo y devuelve su credencial.

    La credencial solo se muestra al crearla: en la base queda su hash bcrypt.
    Por eso se imprime, para poder reutilizarla con --credencial.
    """
    if not token_admin:
        sys.exit("Falta ECORUTA_ADMIN_TOKEN (o pasa --credencial de un equipo ya creado).")
    try:
        vehiculos = peticion(f"{api}/api/v1/admin/vehiculos",
                             cabeceras={"X-Admin-Token": token_admin})
    except urllib.error.HTTPError as e:
        sys.exit(f"No se pudo listar vehiculos ({e.code}). Revisa ECORUTA_ADMIN_TOKEN.")
    if not vehiculos:
        sys.exit("No hay vehiculos dados de alta. Crea uno en /api/v1/admin/vehiculos.")

    # El equipo va montado en el bus de la ruta que se va a recorrer: si fuera en
    # el bus de otra ruta, sus posiciones apareceran en la ruta equivocada.
    de_la_ruta = [v for v in vehiculos if ruta_id is not None and v.get("rutaId") == ruta_id]
    if ruta_id is not None and not de_la_ruta:
        sys.exit(f"Ningun vehiculo tiene asignada la ruta {ruta_id}.")
    vehiculo = de_la_ruta[0] if de_la_ruta else vehiculos[0]
    alta = peticion(f"{api}/api/v1/admin/vehiculos/{vehiculo['id']}/equipos",
                    metodo="POST",
                    cuerpo={"etiqueta": "Simulador de recorrido (desarrollo)"},
                    cabeceras={"X-Admin-Token": token_admin})
    print(f"Equipo aprovisionado para {alta.get('vehiculo', vehiculo['id'])}.")
    print(f"  Reutilizalo con:  --credencial {alta['credencial']}\n")
    return alta["credencial"]


def main():
    p = argparse.ArgumentParser(
        description="Simula el equipo a bordo recorriendo la ruta.",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default=os.environ.get("ECORUTA_API", "http://localhost:8080"))
    p.add_argument("--credencial", default=os.environ.get("ECORUTA_CREDENCIAL"),
                   help="Credencial del equipo. Si falta, aprovisiona uno nuevo.")
    p.add_argument("--velocidad", type=float, default=30.0,
                   help="km/h del bus. 30 es realista en ciudad; sube a 120 para demos (por defecto: 30)")
    p.add_argument("--intervalo", type=float, default=2.0,
                   help="segundos entre reportes (por defecto: 2)")
    p.add_argument("--espera-parada", type=float, default=5.0,
                   help="segundos detenido en cada parada (por defecto: 5)")
    p.add_argument("--espera-con-reserva", type=float, default=20.0,
                   help="segundos detenido si la parada tiene reservas activas (por defecto: 20)")
    p.add_argument("--ruta", type=int, default=None,
                   help="id de la ruta a recorrer (por defecto: la primera activa)")
    p.add_argument("--vueltas", type=int, default=0,
                   help="numero de vueltas; 0 = sin fin (por defecto: 0)")
    args = p.parse_args()

    api = args.api.rstrip("/")

    rutas = peticion(f"{api}/api/v1/rutas")
    if not rutas:
        sys.exit("No hay rutas activas.")
    if args.ruta is None:
        ruta = rutas[0]
    else:
        ruta = next((r for r in rutas if r["id"] == args.ruta), None)
        if ruta is None:
            sys.exit(f"No hay ruta activa con id {args.ruta}. "
                     f"Disponibles: {', '.join(str(r['id']) for r in rutas)}.")
    if not ruta.get("trazado"):
        sys.exit(f"La ruta '{ruta['nombre']}' no tiene trazado cargado. "
                 "Aplica V13__datos_reales_de_las_rutas_de_jalapa.sql.")

    recorrido = Recorrido(ruta["trazado"], ruta["paradas"])
    credencial = args.credencial or aprovisionar(api, os.environ.get("ECORUTA_ADMIN_TOKEN"),
                                                 ruta["id"])

    vuelta_min = (recorrido.largo / 1000) / args.velocidad * 60
    print(f"Ruta      : {ruta['nombre']}")
    print(f"Recorrido : {recorrido.largo/1000:.2f} km, {len(recorrido.puntos)} vertices, "
          f"{len(recorrido.paradas)} paradas")
    print(f"Velocidad : {args.velocidad:.0f} km/h  ->  vuelta de ~{vuelta_min:.1f} min")
    print("Ctrl-C para parar.\n")

    seguir = {"si": True}
    signal.signal(signal.SIGINT, lambda *_: seguir.update(si=False))

    metros_por_tic = args.velocidad * 1000 / 3600 * args.intervalo
    avance = 0.0
    vueltas = 0
    contadores = {"enviadas": 0, "fallos": 0}

    def reportar(lat, lon, velocidad):
        """Manda una posicion. Devuelve False si hay que abortar."""
        ahora = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        try:
            peticion(f"{api}/api/v1/telemetria/posiciones", metodo="POST",
                     cuerpo={"posiciones": [{
                         "latitud": round(lat, 6),
                         "longitud": round(lon, 6),
                         "velocidadKmh": velocidad,
                         "timestamp": ahora,
                     }]},
                     cabeceras={"Authorization": f"Bearer {credencial}"})
            contadores["enviadas"] += 1
            contadores["fallos"] = 0
        except urllib.error.HTTPError as e:
            contadores["fallos"] += 1
            print(f"  ! HTTP {e.code} al reportar: {e.read().decode()[:160]}")
            if e.code in (401, 403):
                sys.exit("Credencial rechazada o revocada. Aprovisiona otro equipo.")
        except urllib.error.URLError as e:
            contadores["fallos"] += 1
            print(f"  ! Sin respuesta de {api}: {e.reason}")
        if contadores["fallos"] >= 5:
            sys.exit("Cinco fallos seguidos: se aborta.")

    def reservas_en(parada_id):
        """Reservas activas en la parada, del resumen de la ruta. 0 si no se sabe."""
        try:
            resumen = peticion(f"{api}/api/v1/rutas/{ruta['id']}/resumen")
            for fila in resumen["reservasActivas"]["porParada"]:
                if fila["paradaId"] == parada_id:
                    return int(fila["reservasActivas"])
        except (urllib.error.URLError, KeyError, TypeError, ValueError):
            pass
        return 0

    while seguir["si"] and (args.vueltas == 0 or vueltas < args.vueltas):
        anterior = avance
        avance += metros_por_tic
        if avance >= recorrido.largo:
            avance -= recorrido.largo
            vueltas += 1
            print(f"  -- vuelta {vueltas} completada --")

        parada = recorrido.parada_en(anterior % recorrido.largo,
                                     avance % recorrido.largo)

        if parada:
            metro, nombre, parada_id, (lat, lon) = parada
            # El bus se detiene EN la parada, no en el punto del tic, que puede
            # quedar decenas de metros despues y fuera del radio de llegada.
            avance = metro
            reservas = reservas_en(parada_id)
            espera = args.espera_con_reserva if reservas else args.espera_parada
            extra = f", {reservas} esperando" if reservas else ""
            print(f"  {metro/1000:5.2f} km  {lat:.6f}, {lon:.6f}  -- parada: {nombre}{extra} ({espera:.0f} s)")
            # Mientras espera sigue reportando: detenido, a 0 km/h. Sin esto la
            # posicion envejeceria justo cuando el pasajero esta mirando.
            fin_espera = time.monotonic() + espera
            while seguir["si"] and time.monotonic() < fin_espera:
                reportar(lat, lon, 0.0)
                time.sleep(min(args.intervalo, max(0.0, fin_espera - time.monotonic())))
            continue

        lat, lon = recorrido.en(avance)
        reportar(lat, lon, args.velocidad)
        km = (avance % recorrido.largo) / 1000
        print(f"  {km:5.2f} km  {lat:.6f}, {lon:.6f}  {args.velocidad:.0f} km/h")
        time.sleep(args.intervalo)

    print(f"\nDetenido. {contadores['enviadas']} posiciones reportadas, {vueltas} vueltas.")


if __name__ == "__main__":
    main()
