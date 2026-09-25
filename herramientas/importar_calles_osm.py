#!/usr/bin/env python3
"""Genera la migracion con la red de calles de Jalapa (SCRUM-26, bloque C).

Descarga las vias de OpenStreetMap con la API de Overpass, las parte en los
cruces (una arista entre cruce y cruce, como haria osm2pgrouting) y escribe el
SQL de la migracion. La descarga ocurre SOLO aqui: en ejecucion el backend no
consume ningun servicio externo, lee lo que esta en la base.

    python herramientas/importar_calles_osm.py

Opciones: --bbox sur,oeste,norte,este   --salida <archivo.sql>
"""

import argparse
import json
import math
import pathlib
import sys
import urllib.parse
import urllib.request

OVERPASS = "https://overpass-api.de/api/interpreter"

# Vias por las que puede circular un bus urbano. Se dejan fuera las peatonales,
# escaleras, ciclovias y caminos de tierra sin clasificar.
TIPOS = ("motorway|trunk|primary|secondary|tertiary|unclassified|residential"
         "|living_street|service|motorway_link|trunk_link|primary_link"
         "|secondary_link|tertiary_link")

BBOX_JALAPA = (14.60, -90.03, 14.68, -89.95)

CABECERA = """\
-- SCRUM-26 (HU Desarrollo-146), bloque C: red de calles de Jalapa.
--
-- GENERADO, NO EDITAR A MANO. Se regenera con:
--     python herramientas/importar_calles_osm.py
--
-- Origen: OpenStreetMap ({bbox}), via la API de Overpass.
-- Licencia: (c) colaboradores de OpenStreetMap, ODbL.
-- Aristas: {aristas}. Nodos: {nodos}.
--
-- Las vias vienen partidas en los cruces: cada fila va de un nodo a otro sin
-- pasar por un tercero, que es lo que pgr_dijkstra necesita para enrutar. El
-- costo es la longitud en metros; costo_reverso vale -1 en las de un solo
-- sentido, y asi pgRouting respeta el sentido de la via.
"""


def descargar(bbox):
    consulta = ('[out:json][timeout:180];way["highway"~"^(%s)$"](%s);out geom;'
                % (TIPOS, ",".join(str(v) for v in bbox)))
    peticion = urllib.request.Request(
        OVERPASS,
        data=urllib.parse.urlencode({"data": consulta}).encode(),
        headers={"User-Agent": "ecoruta-import/1.0 (municipalidad de Jalapa)"})
    with urllib.request.urlopen(peticion, timeout=300) as respuesta:
        return json.load(respuesta)["elements"]


def metros(a, b):
    """Distancia aproximada entre dos puntos (lat, lon), suficiente al costo."""
    radio = 6371000.0
    fi1, fi2 = math.radians(a[0]), math.radians(b[0])
    dfi = fi2 - fi1
    dlambda = math.radians(b[1] - a[1])
    h = (math.sin(dfi / 2) ** 2
         + math.cos(fi1) * math.cos(fi2) * math.sin(dlambda / 2) ** 2)
    return 2 * radio * math.asin(min(1.0, math.sqrt(h)))


def un_solo_sentido(etiquetas):
    sentido = etiquetas.get("oneway", "no")
    if sentido in ("yes", "true", "1", "-1"):
        return True
    # Las autopistas y los enlaces son de un solo sentido aunque no lo digan.
    return etiquetas.get("highway") in ("motorway", "motorway_link")


def aristas(vias):
    """Parte cada via en los nodos que comparte con otra via."""
    cruces = {}
    for via in vias:
        for nodo in via["nodes"]:
            cruces[nodo] = cruces.get(nodo, 0) + 1

    salida = []
    for via in vias:
        etiquetas = via.get("tags", {})
        nodos = via["nodes"]
        puntos = [(p["lat"], p["lon"]) for p in via["geometry"]]
        if len(nodos) != len(puntos) or len(nodos) < 2:
            continue
        invertida = etiquetas.get("oneway") == "-1"
        if invertida:
            nodos, puntos = nodos[::-1], puntos[::-1]
        inicio = 0
        for i in range(1, len(nodos)):
            ultimo = i == len(nodos) - 1
            if cruces.get(nodos[i], 0) > 1 or ultimo:
                tramo = puntos[inicio:i + 1]
                largo = sum(metros(tramo[j], tramo[j + 1]) for j in range(len(tramo) - 1))
                if largo > 0 and nodos[inicio] != nodos[i]:
                    salida.append({
                        "osm_id": via["id"],
                        "nombre": etiquetas.get("name"),
                        "tipo": etiquetas.get("highway"),
                        "unico": un_solo_sentido(etiquetas),
                        "origen": nodos[inicio],
                        "destino": nodos[i],
                        "largo": largo,
                        "puntos": tramo,
                    })
                inicio = i
    return salida


def texto(valor):
    return "NULL" if valor is None else "'" + valor.replace("'", "''") + "'"


def escribir(destino, bbox, lista):
    nodos = {}
    for arista in lista:
        nodos.setdefault(arista["origen"], arista["puntos"][0])
        nodos.setdefault(arista["destino"], arista["puntos"][-1])
    # Los identificadores de OSM se renumeran: pgRouting trabaja mejor con
    # enteros pequenos y consecutivos, y asi el archivo no depende de ellos.
    numero = {osm: i + 1 for i, osm in enumerate(sorted(nodos))}

    with open(destino, "w", encoding="utf-8", newline="\n") as f:
        f.write(CABECERA.format(bbox=",".join(str(v) for v in bbox),
                                aristas=len(lista), nodos=len(nodos)))
        f.write("\nINSERT INTO calles_nodos (id, punto) VALUES\n")
        f.write(",\n".join(
            " (%d, ST_SetSRID(ST_MakePoint(%.6f, %.6f), 4326))"
            % (numero[osm], nodos[osm][1], nodos[osm][0]) for osm in sorted(nodos)))
        f.write(";\n\nSELECT setval('calles_nodos_id_seq', (SELECT max(id) FROM calles_nodos));\n")

        f.write("\nINSERT INTO calles (osm_id, nombre, tipo, sentido_unico, origen, destino,"
                " costo, costo_reverso, trazo) VALUES\n")
        filas = []
        for arista in lista:
            linea = "LINESTRING(" + ",".join(
                "%.6f %.6f" % (p[1], p[0]) for p in arista["puntos"]) + ")"
            filas.append(
                " (%d, %s, %s, %s, %d, %d, %.2f, %s, ST_SetSRID(ST_GeomFromText('%s'), 4326))"
                % (arista["osm_id"], texto(arista["nombre"]), texto(arista["tipo"]),
                   "true" if arista["unico"] else "false",
                   numero[arista["origen"]], numero[arista["destino"]],
                   arista["largo"], "-1" if arista["unico"] else "%.2f" % arista["largo"],
                   linea))
        f.write(",\n".join(filas))
        f.write(";\n\nANALYZE calles;\nANALYZE calles_nodos;\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bbox", default=",".join(str(v) for v in BBOX_JALAPA),
                        help="sur,oeste,norte,este")
    parser.add_argument("--salida", default=str(pathlib.Path(__file__).resolve().parent.parent
                                                / "src/main/resources/db/migration"
                                                / "V20__red_de_calles_de_jalapa.sql"))
    parser.add_argument("--json", help="usa un archivo ya descargado en vez de la API")
    args = parser.parse_args()

    bbox = tuple(float(v) for v in args.bbox.split(","))
    if args.json:
        vias = json.load(open(args.json, encoding="utf-8"))["elements"]
    else:
        vias = descargar(bbox)
    lista = aristas(vias)
    if not lista:
        sys.exit("La consulta no devolvio ninguna via.")
    escribir(args.salida, bbox, lista)
    print("%d vias -> %d aristas en %s" % (len(vias), len(lista), args.salida))


if __name__ == "__main__":
    main()
