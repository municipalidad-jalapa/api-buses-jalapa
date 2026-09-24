#!/usr/bin/env python3
"""
Deja el bus de una ruta en un punto exacto respecto a una parada (QA, ronda 2).

Para las pruebas de ETA y de avisos hace falta el bus en un lugar preciso, no
donde lo haya dejado el simulador. Este script manda unas pocas posiciones a lo
largo del trazado y termina en el punto pedido:

    python tools/posicionar-bus.py --ruta 1 --parada 3 --metros 300   # 300 m antes
    python tools/posicionar-bus.py --ruta 1 --parada 3 --metros 0     # en la parada
    python tools/posicionar-bus.py --ruta 1 --parada 3 --metros -200  # 200 m pasada
    python tools/posicionar-bus.py --ruta 1 --parada 3 --metros 300 --velocidad 0  # detenido

Que sirve para verificar:
  - ETA confiable: el acercamiento previo deja historial de velocidad del bus.
  - ETA "llegando" (0 m) y "el bus ya paso" (metros negativos).
  - Avisos: 250 m dispara "el bus esta por llegar"; 40 m, "¿subiste?".
  - Bus detenido fuera de parada (--velocidad 0 lejos de una parada).

Reutiliza el recorrido, el aprovisionamiento y el envio del simulador
(tools/simulador-gps.py): mismas credenciales y mismas variables de entorno.
"""

import argparse
import importlib.util
import os
import sys
import time
import urllib.error
from datetime import datetime, timezone
from pathlib import Path

_ruta_simulador = Path(__file__).with_name("simulador-gps.py")
_spec = importlib.util.spec_from_file_location("simulador_gps", _ruta_simulador)
simulador = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(simulador)


def main():
    p = argparse.ArgumentParser(description="Deja el bus a N metros de una parada, sobre el trazado.")
    p.add_argument("--api", default=os.environ.get("ECORUTA_API", "http://localhost:8080"))
    p.add_argument("--credencial", default=os.environ.get("ECORUTA_CREDENCIAL"),
                   help="Credencial del equipo. Si falta, aprovisiona uno nuevo.")
    p.add_argument("--ruta", type=int, required=True, help="id de la ruta")
    p.add_argument("--parada", type=int, required=True, help="id de la parada de referencia")
    p.add_argument("--metros", type=float, required=True,
                   help="positivo: antes de la parada; 0: en la parada; negativo: pasada")
    p.add_argument("--velocidad", type=float, default=30.0,
                   help="km/h que reporta el bus (0 = detenido; por defecto 30)")
    p.add_argument("--acercamiento", type=float, default=150.0,
                   help="metros recorridos antes del punto final, para dejar historial (por defecto 150)")
    p.add_argument("--pasos", type=int, default=6, help="posiciones del acercamiento (por defecto 6)")
    p.add_argument("--intervalo", type=float, default=2.0, help="segundos entre posiciones (por defecto 2)")
    args = p.parse_args()

    api = args.api.rstrip("/")
    ruta = next((r for r in simulador.peticion(f"{api}/api/v1/rutas") or [] if r["id"] == args.ruta), None)
    if ruta is None:
        sys.exit(f"No hay ruta activa con id {args.ruta}.")
    if not ruta.get("trazado"):
        sys.exit(f"La ruta '{ruta['nombre']}' no tiene trazado.")

    recorrido = simulador.Recorrido(ruta["trazado"], ruta["paradas"])
    parada = next((pa for pa in recorrido.paradas if pa[2] == args.parada), None)
    if parada is None:
        sys.exit(f"La parada {args.parada} no es de la ruta {args.ruta}. "
                 f"Paradas: {', '.join(f'{pa[2]} ({pa[1]})' for pa in recorrido.paradas)}.")

    credencial = args.credencial or simulador.aprovisionar(
        api, os.environ.get("ECORUTA_ADMIN_TOKEN"), ruta["id"])

    destino = parada[0] - args.metros
    # Detenido no hay acercamiento: todas las posiciones en el mismo punto.
    tramo = 0.0 if args.velocidad <= 0 else args.acercamiento
    pasos = max(1, args.pasos)

    print(f"Ruta {ruta['nombre']} - parada {parada[1]} - "
          f"{'en la parada' if args.metros == 0 else f'{abs(args.metros):.0f} m ' + ('antes' if args.metros > 0 else 'pasada')}")
    for i in range(pasos):
        avance = destino - tramo + tramo * (i + 1) / pasos
        lat, lon = recorrido.en(avance)
        if args.metros == 0 and i == pasos - 1:
            # La parada se situa en el vertice mas cercano del trazado, que puede
            # quedar a decenas de metros: el ultimo punto va sobre la parada.
            lat, lon = parada[3]
        ahora = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        try:
            simulador.peticion(f"{api}/api/v1/telemetria/posiciones", metodo="POST",
                               cuerpo={"posiciones": [{
                                   "latitud": round(lat, 6),
                                   "longitud": round(lon, 6),
                                   "velocidadKmh": args.velocidad,
                                   "timestamp": ahora,
                               }]},
                               cabeceras={"Authorization": f"Bearer {credencial}"})
        except urllib.error.HTTPError as e:
            sys.exit(f"HTTP {e.code} al reportar: {e.read().decode()[:200]}")
        print(f"  {i + 1}/{pasos}  {lat:.6f}, {lon:.6f}  "
              f"a {simulador.metros((lat, lon), parada[3]):.0f} m de la parada")
        if i < pasos - 1:
            time.sleep(args.intervalo)
    print("Listo. Mira el ETA en GET /api/v1/rutas/%d/eta" % ruta["id"])


if __name__ == "__main__":
    main()
