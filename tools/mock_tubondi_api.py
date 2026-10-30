#!/usr/bin/env python3
"""Local-only TuBondi protocol stub for offline/408 launcher QA.

Bind to loopback and pair with `adb reverse tcp:8765 tcp:8765`. It never
forwards to or contacts the production TuBondi host.
"""

from __future__ import annotations

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


class State:
    mode = "success"
    delay_ms = 0
    counts = {"bootstrap": 0, "fingerprint": 0, "arrivals": 0, "responses408": 0}
    lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, _format: str, *_args: object) -> None:
        # Avoid logging request bodies, cookies, or fingerprints.
        return

    def _send(self, status: int, body: bytes = b"", content_type: str = "text/plain") -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/__control/mode":
            requested = parse_qs(parsed.query).get("value", [""])[0]
            if requested not in {"success", "408"}:
                self._send(400, b"mode must be success or 408")
                return
            with State.lock:
                State.mode = requested
            self._send(200, json.dumps({"mode": requested}).encode(), "application/json")
            return
        if parsed.path == "/__control/delay":
            try:
                requested = int(parse_qs(parsed.query).get("ms", ["0"])[0])
            except ValueError:
                requested = -1
            if not 0 <= requested <= 10_000:
                self._send(400, b"delay must be from 0 to 10000 ms")
                return
            with State.lock:
                State.delay_ms = requested
            self._send(200, json.dumps({"delay_ms": requested}).encode(), "application/json")
            return
        if parsed.path == "/__control/stats":
            with State.lock:
                snapshot = {"mode": State.mode, "delay_ms": State.delay_ms, **State.counts}
            self._send(200, json.dumps(snapshot).encode(), "application/json")
            return
        if parsed.path.endswith("/usuario/urbano.php"):
            with State.lock:
                State.counts["bootstrap"] += 1
            self.send_response(200)
            self.send_header("Set-Cookie", "PHPSESSID=qa-local; Path=/")
            body = b"<html><script>operacion:'59608253602766063001';</script></html>"
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            return
        self._send(404)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0"))
        form = parse_qs(self.rfile.read(length).decode("utf-8", errors="replace"))
        query_command = parse_qs(parsed.query).get("cmd", [""])[0]
        body_command = form.get("cmd", [""])[0]
        if parsed.path.endswith("/usuario/urbano2_cmd.php") and query_command == "vista":
            with State.lock:
                State.counts["fingerprint"] += 1
            self._send(200)
            return
        if parsed.path.endswith("/usuario/urbano2_cmd.php") and query_command == "lineasyrutas":
            payload = {
                "lineas": [
                    {
                        "linea_id": "10",
                        "linea_nombre": "10",
                        "color": "#0E627C",
                        "cliente": 1,
                        "rutas": [{"ruta_id": "10", "ruta_nombre": "Centro · prueba local"}],
                    },
                    {
                        "linea_id": "40",
                        "linea_nombre": "40",
                        "color": "#146E68",
                        "cliente": 1,
                        "rutas": [{"ruta_id": "40", "ruta_nombre": "Nueva Córdoba · prueba local"}],
                    },
                ],
                "grupos": [],
                "clientes": [],
            }
            self._send(200, json.dumps(payload).encode(), "application/json")
            return
        if parsed.path.endswith("/usuario/urbano2_cmd.php") and query_command == "seleccionatraza":
            route = form.get("ruta", [""])[0]
            if route == "10":
                stops = [
                    {
                        "codigo": "A911",
                        "descripcion": "AV. DUARTE QUIROS 1717-1728",
                        "lineas": [{"linea_id": "10", "linea_nombre": "10"}],
                    },
                    {
                        "codigo": "CE56",
                        "descripcion": "COLON 545/33",
                        "lineas": [{"linea_id": "10", "linea_nombre": "10"}],
                    },
                ]
            else:
                stops = [
                    {
                        "codigo": "NC15",
                        "descripcion": "AV HIPOLITO IRIGOYEN 660",
                        "lineas": [{"linea_id": "40", "linea_nombre": "40"}],
                    }
                ]
            self._send(200, json.dumps({"paradas": stops, "notificaciones": []}).encode(), "application/json")
            return
        if parsed.path.endswith("/usuario/urbano2_cmd.php") and body_command == "proximos_arribos":
            code = form.get("codigo", ["AA50"])[0]
            with State.lock:
                State.counts["arrivals"] += 1
                mode = State.mode
                delay_ms = State.delay_ms
                if mode == "408":
                    State.counts["responses408"] += 1
            if mode == "408":
                if delay_ms:
                    time.sleep(delay_ms / 1000)
                self._send(408)
                return
            payload = {
                "parada": {"codigo": code, "descripcion": "Parada de prueba local", "lineas": []},
                "notificacion": {
                    "tipo": "aviso",
                    "mensaje": "Aviso de prueba local: desvío temporal; consultá el recorrido antes de salir.",
                },
                "proximos_arribos": [
                    {
                        "linea": "10",
                        "linea_nombre": "10",
                        "ruta_descripcion": "Centro · Terminal de Ómnibus · datos de prueba local",
                        "demora": "4min",
                        "distancia": 0.2,
                        "color": "#0E627C",
                    },
                    {
                        "linea": "40",
                        "linea_nombre": "40",
                        "ruta_descripcion": "Ciudad Universitaria · destino largo para validar el ajuste en pantalla",
                        "demora": "12min",
                        "distancia": 1.6,
                        "color": "#146E68",
                    },
                    {
                        "linea": "50",
                        "linea_nombre": "50",
                        "ruta_descripcion": "Barrio Jardín · servicio simulado",
                        "demora": "27min",
                        "distancia": 3.4,
                        "color": "#815019",
                    },
                ],
            }
            self._send(200, json.dumps(payload).encode(), "application/json")
            return
        self._send(404)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--mode", choices=("success", "408"), default="success")
    args = parser.parse_args()
    State.mode = args.mode
    host = "127.0.0.1"
    server = ThreadingHTTPServer((host, args.port), Handler)
    print(f"QA stub listening on http://{host}:{args.port} mode={State.mode}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
