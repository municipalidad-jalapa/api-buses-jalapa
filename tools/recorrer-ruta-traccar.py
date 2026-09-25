#!/usr/bin/env python3

from __future__ import annotations

import json
import math
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
MUESTRA = RAIZ / "src/test/resources/traccar/traccar-position-sample.json"
RUTA = "/api/v1/integraciones/traccar/posiciones"
PASO_S = 2.0


def vertices_ruta() -> list[tuple[float, float]]:
    geo = json.loads(
        Path("/tmp/trazado-ruta1.json").read_text(encoding="utf-8")
        if Path("/tmp/trazado-ruta1.json").exists()
        else "null"
    )
    if geo and geo.get("coordinates"):
        return [(lat, lon) for lon, lat in geo["coordinates"]]
    return [
        (14.634878, -89.981202),
        (14.63436, -89.98277),
        (14.633843, -89.984031),
        (14.633161, -89.985636),
        (14.63245, -89.987308),
        (14.631911, -89.988584),
        (14.63107, -89.99111),
        (14.630328, -89.993654),
        (14.629695, -89.996067),
        (14.628685, -89.999829),
        (14.62773, -90.003034),
        (14.62836, -89.999765),
        (14.62928, -89.994964),
        (14.630252, -89.989841),
        (14.632386, -89.984545),
        (14.634878, -89.981202),
    ]


def rumbo_grados(a: tuple[float, float], b: tuple[float, float]) -> float:
    phi1, phi2 = math.radians(a[0]), math.radians(b[0])
    dlam = math.radians(b[1] - a[1])
    y = math.sin(dlam) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(dlam)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def nudos(a: tuple[float, float], b: tuple[float, float], segundos: float) -> float:
    r = 6371.0
    dphi = math.radians(b[0] - a[0])
    dlam = math.radians(b[1] - a[1])
    h = (
        math.sin(dphi / 2) ** 2
        + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlam / 2) ** 2
    )
    km = 2 * r * math.asin(min(1.0, math.sqrt(h)))
    kmh = km / (segundos / 3600.0) if segundos > 0 else 0
    return kmh / 1.852


def enviar(base: str, token: str, lat: float, lon: float, course: float, speed_kn: float) -> int:
    cuerpo = json.loads(MUESTRA.read_text(encoding="utf-8"))
    ahora = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000+00:00")
    pos = cuerpo["position"]
    pos["id"] = 0
    pos["latitude"] = lat
    pos["longitude"] = lon
    pos["course"] = course
    pos["speed"] = speed_kn
    pos["fixTime"] = ahora
    pos["deviceTime"] = ahora
    pos["serverTime"] = ahora
    cuerpo["device"]["uniqueId"] = os.environ.get("TRACCAR_UNIQUE_ID", "860000000000001")
    data = json.dumps(cuerpo).encode()
    cab = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-Traccar-Token": token,
    }
    req = urllib.request.Request(base.rstrip("/") + RUTA, data=data, headers=cab, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def main() -> int:
    base = os.environ.get("API_BASE", "http://127.0.0.1:18080")
    token = os.environ.get("TRACCAR_TOKEN", "solo-para-desarrollo-local-traccar-32chars")
    puntos = vertices_ruta()
    i = 0
    print(f"recorrido: {len(puntos)} vertices cada {PASO_S}s → {base}", flush=True)
    while True:
        actual = puntos[i]
        nxt = puntos[(i + 1) % len(puntos)]
        course = rumbo_grados(actual, nxt)
        speed = max(8.0, min(25.0, nudos(actual, nxt, PASO_S)))
        codigo = enviar(base, token, actual[0], actual[1], course, speed)
        print(f"{codigo} lat={actual[0]:.6f} lon={actual[1]:.6f}", flush=True)
        i = (i + 1) % len(puntos)
        time.sleep(PASO_S)


if __name__ == "__main__":
    raise SystemExit(main())
