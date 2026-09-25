#!/usr/bin/env python3

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
MUESTRA = RAIZ / "src/test/resources/traccar/traccar-position-sample.json"
RUTA = "/api/v1/integraciones/traccar/posiciones"


def cargar_muestra(unique_id=None, lat=None, lon=None):
    cuerpo = json.loads(MUESTRA.read_text(encoding="utf-8"))
    ahora = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000+00:00")
    posicion = cuerpo["position"]
    posicion["fixTime"] = ahora
    posicion["deviceTime"] = ahora
    posicion["serverTime"] = ahora
    if unique_id is not None:
        cuerpo["device"]["uniqueId"] = unique_id
    if lat is not None:
        posicion["latitude"] = lat
    if lon is not None:
        posicion["longitude"] = lon
    return json.dumps(cuerpo, ensure_ascii=False)


def enviar(base, token, cuerpo):
    cab = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        cab["X-Traccar-Token"] = token
    req = urllib.request.Request(base.rstrip("/") + RUTA, data=cuerpo.encode(), headers=cab, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--caso", default="202",
                        choices=["202", "401", "400", "422-dispositivo", "422-coordenadas"])
    args = parser.parse_args()
    base = os.environ.get("API_BASE", "http://localhost:8080")
    token = os.environ.get("TRACCAR_TOKEN", "solo-para-desarrollo-local-traccar-32chars")

    if args.caso == "401":
        token = "token-equivocado-de-mas-de-32-caracteres"
        cuerpo = cargar_muestra()
    elif args.caso == "400":
        cuerpo = "{no es json"
    elif args.caso == "422-dispositivo":
        cuerpo = cargar_muestra(unique_id="000000000000000")
    elif args.caso == "422-coordenadas":
        cuerpo = cargar_muestra(lat=95.0)
    else:
        cuerpo = cargar_muestra()

    status, texto = enviar(base, token, cuerpo)
    print(f"HTTP {status}")
    print(texto)
    return 0 if status in (202, 400, 401, 422) else 1


if __name__ == "__main__":
    sys.exit(main())
