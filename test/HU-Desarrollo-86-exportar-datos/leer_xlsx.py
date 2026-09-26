#!/usr/bin/env python3
"""Lee un .xlsx de la exportacion (HU Desarrollo-86) para las pruebas manuales.

Solo biblioteca estandar (un .xlsx es un ZIP de XML), igual que tools/simulador-gps.py:
no hay que instalar nada.

    python leer_xlsx.py archivo.xlsx --hojas            # nombres de hojas, una por linea
    python leer_xlsx.py archivo.xlsx --hoja Demanda     # filas separadas por TAB
    python leer_xlsx.py archivo.xlsx --resumen          # "etiqueta<TAB>valor" de la hoja Resumen
    python leer_xlsx.py archivo.xlsx --texto            # TODO el texto de TODAS las partes del ZIP

--texto existe para la prueba de privacidad: busca en el archivo entero, no solo en las
celdas visibles, asi que una columna oculta o una propiedad del documento tambien cuentan.
"""
import argparse
import datetime
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
      "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
      "rel": "http://schemas.openxmlformats.org/package/2006/relationships"}
BASE_EXCEL = datetime.datetime(1899, 12, 30)
FORMATOS_DE_FECHA_INTERNOS = set(range(14, 23)) | {45, 46, 47}


def columna(referencia):
    letras = re.match(r"[A-Z]+", referencia).group(0)
    n = 0
    for c in letras:
        n = n * 26 + (ord(c) - 64)
    return n - 1


def cargar_cadenas(z):
    if "xl/sharedStrings.xml" not in z.namelist():
        return []
    raiz = ET.fromstring(z.read("xl/sharedStrings.xml"))
    return ["".join(t.text or "" for t in si.iter("{%s}t" % NS["m"])) for si in raiz.findall("m:si", NS)]


def estilos_de_fecha(z):
    """Indices de estilo (xf) cuyo formato es una fecha u hora."""
    raiz = ET.fromstring(z.read("xl/styles.xml"))
    personalizados = {}
    for f in raiz.findall("m:numFmts/m:numFmt", NS):
        personalizados[int(f.get("numFmtId"))] = f.get("formatCode", "")
    resultado = set()
    for i, xf in enumerate(raiz.findall("m:cellXfs/m:xf", NS)):
        fid = int(xf.get("numFmtId", "0"))
        codigo = re.sub(r'"[^"]*"|\[[^\]]*\]', "", personalizados.get(fid, ""))
        if fid in FORMATOS_DE_FECHA_INTERNOS or re.search(r"[ymdhs]", codigo, re.I):
            resultado.add(i)
    return resultado


def hojas(z):
    """[(nombre, ruta_en_el_zip)] en el orden del libro."""
    libro = ET.fromstring(z.read("xl/workbook.xml"))
    rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    destino = {r.get("Id"): r.get("Target") for r in rels.findall("rel:Relationship", NS)}
    salida = []
    for h in libro.findall("m:sheets/m:sheet", NS):
        objetivo = destino[h.get("{%s}id" % NS["r"])]
        salida.append((h.get("name"), "xl/" + objetivo.lstrip("/").replace("xl/", "", 1)))
    return salida


def formatear(valor, es_fecha):
    if es_fecha:
        momento = BASE_EXCEL + datetime.timedelta(days=float(valor))
        if momento.time() == datetime.time(0, 0):
            return momento.strftime("%Y-%m-%d")
        return momento.strftime("%Y-%m-%d %H:%M:%S")
    numero = float(valor)
    return str(int(numero)) if numero == int(numero) else ("%.4f" % numero).rstrip("0")


def filas_de(z, ruta, cadenas, fechas):
    raiz = ET.fromstring(z.read(ruta))
    for fila in raiz.findall("m:sheetData/m:row", NS):
        celdas = {}
        for c in fila.findall("m:c", NS):
            tipo = c.get("t")
            v = c.find("m:v", NS)
            if tipo == "s" and v is not None:
                celdas[columna(c.get("r"))] = cadenas[int(v.text)]
            elif tipo == "inlineStr":
                celdas[columna(c.get("r"))] = "".join(t.text or "" for t in c.iter("{%s}t" % NS["m"]))
            elif v is not None and v.text is not None:
                es_fecha = int(c.get("s", "0")) in fechas
                celdas[columna(c.get("r"))] = formatear(v.text, es_fecha)
        if celdas:
            yield [celdas.get(i, "") for i in range(max(celdas) + 1)]
        else:
            yield []


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("archivo")
    grupo = p.add_mutually_exclusive_group(required=True)
    grupo.add_argument("--hojas", action="store_true")
    grupo.add_argument("--hoja")
    grupo.add_argument("--resumen", action="store_true")
    grupo.add_argument("--texto", action="store_true")
    args = p.parse_args()

    try:
        z = zipfile.ZipFile(args.archivo)
    except zipfile.BadZipFile:
        sys.exit("No es un .xlsx valido (no es un ZIP).")

    if args.texto:
        for nombre in z.namelist():
            print(z.read(nombre).decode("utf-8", errors="replace"))
        return
    if args.hojas:
        for nombre, _ in hojas(z):
            print(nombre)
        return

    cadenas, fechas = cargar_cadenas(z), estilos_de_fecha(z)
    destino = "Resumen" if args.resumen else args.hoja
    ruta = dict(hojas(z)).get(destino)
    if ruta is None:
        sys.exit("No existe la hoja '%s'." % destino)
    for fila in filas_de(z, ruta, cadenas, fechas):
        if args.resumen:
            if len(fila) >= 2 and fila[0] and fila[1]:
                print("%s\t%s" % (fila[0], fila[1]))
        else:
            print("\t".join(fila))


if __name__ == "__main__":
    main()
