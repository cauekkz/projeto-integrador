#!/usr/bin/env python3
"""Stubs locais para os testes de API do VanRoute (sem dependencias externas).

- SMTP sink em 127.0.0.1:2525: aceita qualquer AUTH e grava os e-mails em api-tests/out/mail.log
- Geocoder fake em 127.0.0.1:8089/pelias/v1/search: um ponto por endereco perto da Se (formato Pelias)
  e uma lista vazia quando o texto contem "NAOEXISTE" (simula endereco nao encontrado).
- Directions fake em 127.0.0.1:8089/v2/directions/driving-car/geojson (formato OpenRouteService):
  devolve a linha passando pelos pontos, com distancia em linha reta, os trechos (segments) e a posicao
  de cada parada (way_points), como a API real; cada chamada vai pra out/directions.log
"""
import hashlib
import json
import math
import os
import socketserver
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
os.makedirs(OUT, exist_ok=True)
MAIL_LOG = os.path.join(OUT, "mail.log")


class SmtpHandler(socketserver.StreamRequestHandler):
    def send(self, line):
        self.wfile.write((line + "\r\n").encode())

    def handle(self):
        self.send("220 stub ESMTP")
        while True:
            raw = self.rfile.readline()
            if not raw:
                return
            cmd = raw.decode(errors="replace").strip()
            up = cmd.upper()
            if up.startswith("EHLO") or up.startswith("HELO"):
                self.wfile.write(b"250-stub\r\n250-AUTH PLAIN LOGIN\r\n250 OK\r\n")
            elif up.startswith("AUTH LOGIN"):
                self.send("334 VXNlcm5hbWU6")
                self.rfile.readline()
                self.send("334 UGFzc3dvcmQ6")
                self.rfile.readline()
                self.send("235 OK")
            elif up.startswith("AUTH"):
                if len(cmd.split()) == 2:
                    self.send("334 ")
                    self.rfile.readline()
                self.send("235 OK")
            elif up == "DATA":
                self.send("354 end with .")
                lines = []
                while True:
                    line = self.rfile.readline().decode(errors="replace")
                    if line.rstrip("\r\n") == "." or not line:
                        break
                    lines.append(line)
                with open(MAIL_LOG, "a") as f:
                    f.write("".join(lines) + "\n=====\n")
                self.send("250 queued")
            elif up == "QUIT":
                self.send("221 bye")
                return
            else:
                self.send("250 OK")


class GeoHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        text = parse_qs(urlparse(self.path).query).get("text", [""])[0]
        # um ponto por endereco (rua + numero), espalhado ate ~3 km da Praca da Se; o mesmo endereco cai sempre no mesmo ponto
        key = ",".join(part.strip().lower() for part in text.split(",")[:2])
        h = hashlib.md5(key.encode()).digest()
        lng = round(-46.6333 + (h[0] / 255 - 0.5) * 0.06, 6)
        lat = round(-23.5505 + (h[1] / 255 - 0.5) * 0.06, 6)
        features = [] if "NAOEXISTE" in text.upper() else [
            {"geometry": {"type": "Point", "coordinates": [lng, lat]}}]
        body = json.dumps({"features": features}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        coords = body.get("coordinates", [])
        # como a API real: um trecho (segment) entre cada par de paradas e a posicao de cada parada na linha (way_points)
        segments = []
        for (lng1, lat1), (lng2, lat2) in zip(coords, coords[1:]):
            dx = math.radians(lng2 - lng1) * math.cos(math.radians((lat1 + lat2) / 2))
            dy = math.radians(lat2 - lat1)
            meters = 6371000 * math.hypot(dx, dy)
            segments.append({"distance": round(meters, 1), "duration": round(meters / 8.3, 1) or 30.0})
        distance = sum(seg["distance"] for seg in segments)
        with open(os.path.join(OUT, "directions.log"), "a") as f:
            f.write(json.dumps(coords) + "\n")
        result = {"type": "FeatureCollection", "features": [{
            "type": "Feature",
            "properties": {"summary": {"distance": round(distance, 1), "duration": sum(seg["duration"] for seg in segments)},
                           "segments": segments, "way_points": list(range(len(coords)))},
            "geometry": {"type": "LineString", "coordinates": coords}}]}
        payload = json.dumps(result).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    smtp = socketserver.ThreadingTCPServer(("127.0.0.1", 2525), SmtpHandler)
    threading.Thread(target=smtp.serve_forever, daemon=True).start()
    print("stubs: smtp 127.0.0.1:2525, geocoder e directions 127.0.0.1:8089", flush=True)
    HTTPServer(("127.0.0.1", 8089), GeoHandler).serve_forever()
