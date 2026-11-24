import http.server
import socketserver
import urllib.request
import ssl
import os
from pathlib import Path

PORT = 8899
CACHE_DIR = Path(__file__).parent / ".maven_cache"
CACHE_DIR.mkdir(exist_ok=True)

ssl_ctx = ssl.create_default_context()
ssl_ctx.check_hostname = False
ssl_ctx.verify_mode = ssl.CERT_NONE

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

class MavenProxyHandler(http.server.BaseHTTPRequestHandler):
    def handle_request(self, send_body=True):
        path = self.path.lstrip("/")
        if path.startswith("google/"):
            remote_url = "https://maven.aliyun.com/repository/google/" + path[len("google/"):]
        elif path.startswith("central/"):
            remote_url = "https://maven.aliyun.com/repository/public/" + path[len("central/"):]
        elif path.startswith("jitpack/"):
            remote_url = "https://jitpack.io/" + path[len("jitpack/"):]
        else:
            self.send_response(404)
            self.end_headers()
            return

        cache_file = CACHE_DIR / path.replace("/", os.sep)
        if cache_file.exists():
            try:
                data = cache_file.read_bytes()
                self.send_response(200)
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Content-Type", "application/octet-stream")
                self.end_headers()
                if send_body:
                    self.wfile.write(data)
                return
            except Exception:
                pass

        try:
            req = urllib.request.Request(remote_url, headers={"User-Agent": "Gradle/8.13"})
            with urllib.request.urlopen(req, context=ssl_ctx, timeout=30) as resp:
                data = resp.read()
                cache_file.parent.mkdir(parents=True, exist_ok=True)
                try:
                    cache_file.write_bytes(data)
                except Exception:
                    pass
                self.send_response(resp.status)
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Content-Type", "application/octet-stream")
                self.end_headers()
                if send_body:
                    self.wfile.write(data)
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.end_headers()
        except Exception:
            self.send_response(500)
            self.end_headers()

    def do_GET(self):
        self.handle_request(send_body=True)

    def do_HEAD(self):
        self.handle_request(send_body=False)

    def log_message(self, format, *args):
        pass

if __name__ == "__main__":
    server = ThreadedHTTPServer(("127.0.0.1", PORT), MavenProxyHandler)
    print(f"Threaded Maven cache proxy running on http://127.0.0.1:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
