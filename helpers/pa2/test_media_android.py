#!/usr/bin/env python3
"""Exercise the signed APK and recording progress on an Android 11 test device."""
import os
import re
import shlex
import subprocess
import sys
import tempfile
import threading
import time
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PACKAGE = os.environ["PA_TRIAL_ID"]
COMPONENT = PACKAGE + "/com.ispina.lokalnie."
PORT = 18808
REPORT = Path("android-test")


def adb(*args, timeout=30):
    return subprocess.check_output(["adb", *args], text=True, timeout=timeout).strip()


def shell(*args):
    return adb("shell", shlex.join(args))


def hierarchy():
    shell("uiautomator", "dump", "/data/local/tmp/pa18-ui.xml")
    text = adb("exec-out", "cat", "/data/local/tmp/pa18-ui.xml")
    return text, ET.fromstring(text).iter("node")


def find_text(predicate, filename=""):
    xml, nodes = hierarchy()
    if filename:
        (REPORT / filename).write_text(xml, encoding="utf-8")
    return next((node for node in nodes if predicate(node.get("text", ""))), None)


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    assert x1 < x2 and y1 < y2, node.attrib
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def wait_for(probe, reason):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = probe()
        if result is not None:
            return result
        time.sleep(0.35)
    raise AssertionError(reason)


def open_tv():
    assert "Status: ok" in shell("am", "start", "-W", "-n", COMPONENT + "MainActivity")
    for _ in range(8):
        tv = find_text(lambda text: text == "Telewizja", "main.xml")
        if tv is not None:
            tap(tv)
            break
        shell("input", "swipe", "360", "1030", "360", "420", "330")
    else:
        raise AssertionError("Telewizja tile missing from the main screen")
    wait_for(lambda: find_text(lambda text: "Nagraj telewizję" in text, "tv-open.xml"),
             "TV screen with the recording control did not open")
    assert COMPONENT + "TvActivity" in shell("dumpsys", "activity", "activities")
    print("PASS: Telewizja opens from the main screen", flush=True)


def start_server(directory):
    def ffmpeg(*args):
        subprocess.run(["ffmpeg", "-v", "error", *args], check=True)

    audio = directory / "fixture.mp3"
    ffmpeg("-f", "lavfi", "-i", "sine=frequency=600", "-t", "4", "-c:a", "libmp3lame",
           "-b:a", "128k", "-write_xing", "0", "-id3v2_version", "0", str(audio))
    ffmpeg("-f", "lavfi", "-i", "color=c=black:s=128x72:r=15",
           "-f", "lavfi", "-i", "sine=frequency=600", "-t", "32", "-c:v", "libx264",
           "-g", "30", "-sc_threshold", "0", "-threads", "2", "-c:a", "aac",
           "-f", "hls", "-hls_time", "2", "-hls_list_size", "0", str(directory / "fixture.m3u8"))
    audio_bytes = audio.read_bytes()
    segments = [file.read_bytes() for file in sorted(directory.glob("*.ts"), key=lambda file: int(file.stem[7:]))]

    class MediaHandler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            pass

        def do_GET(self):
            try:
                if self.path == "/radio":
                    self.send_response(200)
                    self.send_header("Content-Type", "audio/mpeg")
                    self.end_headers()
                    while True:
                        for start in range(0, len(audio_bytes), 4000):
                            self.wfile.write(audio_bytes[start:start + 4000])
                            self.wfile.flush()
                            time.sleep(0.25)
                elif self.path == "/tv.m3u8":
                    if server.tv_start is None:
                        server.tv_start = time.monotonic()
                    last = min(len(segments) - 1, int((time.monotonic() - server.tv_start) / 2))
                    first = max(0, last - 2)
                    body = f"#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:{first}\n"
                    for index in range(first, last + 1):
                        body += f"#EXTINF:2.000,\n/segment-{index}.ts\n"
                    self.send_body("application/vnd.apple.mpegurl", body.encode())
                elif self.path.startswith("/segment-"):
                    index = int(self.path.removeprefix("/segment-").removesuffix(".ts"))
                    self.send_body("video/mp2t", segments[index])
                else:
                    self.send_error(404)
            except (BrokenPipeError, ConnectionResetError):
                return

        def send_body(self, mime, data):
            self.send_response(200)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    server = ThreadingHTTPServer(("127.0.0.1", PORT), MediaHandler)
    server.tv_start = None
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def check_recording(title, path, fmt, tv):
    result = shell("am", "start-foreground-service", "-n", COMPONENT + "PARecordingService",
                   "--es", "title", title, "--es", "url", f"http://127.0.0.1:{PORT}{path}",
                   "--ez", "television", str(tv).lower())
    assert result.startswith("Starting service:"), result

    def progress_seconds(minimum):
        node = find_text(lambda text: text.endswith("·  " + fmt), f"{fmt.lower()}-progress.xml")
        if node is None:
            return None
        details = node.get("text")
        match = re.fullmatch(r"(\d+):(\d{2})\s+·\s+[\d,.]+ (B|kB|MB)\s+·\s+" + fmt, details)
        assert match, details
        seconds = int(match[1]) * 60 + int(match[2])
        return seconds if seconds >= minimum else None

    first = wait_for(lambda: progress_seconds(1), f"{fmt}: timer, file size and format not displayed")
    later = wait_for(lambda: progress_seconds(first + 2), f"{fmt}: timer stopped increasing")
    assert find_text(lambda text: "Nagrywanie · " + title in text) is not None
    notification = shell("dumpsys", "notification", "--noredact")
    (REPORT / f"{fmt.lower()}-notification.txt").write_text(notification, encoding="utf-8")
    assert f"Nagrywanie · {title}" in notification and f"·  {fmt}" in notification
    stop = wait_for(lambda: find_text(lambda text: "Zakończ nagranie" in text), "Stop button missing")
    tap(stop)
    wait_for(lambda: find_text(lambda text: text.startswith("Zapisano w Pobrane"), f"{fmt.lower()}-saved.xml"),
             f"{fmt}: saved result did not appear")
    directory = "/sdcard/Download/Pomocnik Alfa"
    recorded = next(name for name in shell("ls", "-1", directory).splitlines()
                    if name.endswith(f"{title}.{fmt.lower()}"))
    assert int(shell("stat", "-c", "%s", f"{directory}/{recorded}")) > 1000
    print(f"PASS: {fmt} from {first} to {later} seconds, notification and file saved", flush=True)


def main():
    REPORT.mkdir(exist_ok=True)
    adb("root")
    adb("wait-for-device")
    assert shell("getprop", "ro.build.version.sdk") == "30"
    adb("install", "-r", sys.argv[1], timeout=90)
    shell("input", "keyevent", "82")
    shell("wm", "size", "720x1280")
    shell("wm", "density", "240")
    adb("logcat", "-c")
    try:
        open_tv()
        with tempfile.TemporaryDirectory() as work, start_server(Path(work)) as server:
            adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")
            try:
                check_recording("TEST TV", "/tv.m3u8", "TS", True)
                assert "Status: ok" in shell("am", "start", "-W", "-n", COMPONENT + "PARadioCatalogActivity")
                wait_for(lambda: find_text(lambda text: "Nagraj radio" in text, "radio-open.xml"),
                         "Radio screen did not show the recording button")
                check_recording("TEST RADIO", "/radio", "MP3", False)
            finally:
                server.shutdown()
                adb("reverse", "--remove", f"tcp:{PORT}")
    finally:
        crashes = adb("logcat", "-d", "-b", "crash")
        (REPORT / "crashes.txt").write_text(crashes, encoding="utf-8")
        (REPORT / "runtime.txt").write_text(
            adb("logcat", "-d", "-s", "AndroidRuntime", "ActivityTaskManager", "MediaProvider"), encoding="utf-8")
        assert "Process: " + PACKAGE not in crashes, crashes


if __name__ == "__main__":
    main()
