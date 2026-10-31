#!/usr/bin/env python3
"""Minimal rtl-tcp protocol client for device testing.

This client needs a TCP path to the driver (for example
`adb forward tcp:1234 tcp:1234`). Some wireless-ADB setups make `adb forward`
unreliable; prefer `scripts/stream_test.sh`, which runs the client on the device.

Command layout: 1-byte code + uint32 big-endian parameter.
"""

import argparse
import socket
import struct
import sys
import time


def recv_exact(sock, count):
    data = b""
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise EOFError("connection closed")
        data += chunk
    return data


def send_command(sock, code, parameter):
    sock.sendall(struct.pack(">BI", code, parameter & 0xFFFFFFFF))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1234)
    parser.add_argument("--seconds", type=float, default=2.0, help="stream duration")
    parser.add_argument("--freq", type=int, help="send SET_FREQ (0x01) before streaming")
    parser.add_argument("--rate", type=int, help="send SET_SAMPLE_RATE (0x02) after the delay")
    parser.add_argument(
        "--command-delay",
        type=float,
        default=1.0,
        help="wait after connect before sending commands (listener race)",
    )
    parser.add_argument(
        "--exit",
        action="store_true",
        help="send TCP_ANDROID_EXIT (0x7e) at the end",
    )
    args = parser.parse_args()

    sock = socket.create_connection((args.host, args.port), timeout=5)
    sock.settimeout(5)
    try:
        header = recv_exact(sock, 12)
        magic = header[:4]
        dongle_type, gains = struct.unpack(">II", header[4:])
        print(
            f"header: magic={magic!r} dongle_type={dongle_type} gains={gains}",
            flush=True,
        )
        if magic != b"RTL0":
            print("error: missing RTL0 header", file=sys.stderr)
            return 3

        if args.freq is not None or args.rate is not None:
            time.sleep(args.command_delay)
            if args.freq is not None:
                send_command(sock, 0x01, args.freq)
                print(f"sent SET_FREQ {args.freq}", flush=True)
            if args.rate is not None:
                send_command(sock, 0x02, args.rate)
                print(f"sent SET_SAMPLE_RATE {args.rate}", flush=True)

        total = 0
        started = time.monotonic()
        while time.monotonic() - started < args.seconds:
            sock.settimeout(2)
            try:
                data = sock.recv(262144)
            except socket.timeout:
                continue
            if not data:
                print("driver closed the connection", flush=True)
                break
            total += len(data)

        elapsed = max(time.monotonic() - started, 1e-9)
        print(
            f"streamed {total} bytes in {elapsed:.2f}s "
            f"({total / elapsed / 1e6:.2f} MB/s)",
            flush=True,
        )

        if args.exit:
            send_command(sock, 0x7E, 0)
            print("sent TCP_ANDROID_EXIT", flush=True)
            sock.settimeout(5)
            try:
                while sock.recv(65536):
                    pass
            except socket.timeout:
                # The app processes the exit command but does not always close
                # the accepted socket (CLOSE_WAIT); see references/known-issues.md.
                print(
                    "note: driver processed the exit command but did not "
                    "close the socket (known CLOSE_WAIT issue)",
                    file=sys.stderr,
                )
            except ConnectionResetError:
                pass

        if total <= 0:
            print("error: no sample data received", file=sys.stderr)
            return 4
        return 0
    finally:
        sock.close()


if __name__ == "__main__":
    sys.exit(main())
