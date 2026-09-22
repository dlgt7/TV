#!/usr/bin/env python3
"""Remote ssh/scp replacement for the FongMi/TV build server.

Why this exists
---------------
``zyq@192.168.42.153`` only accepts the ed25519 key, which lives in the **Windows
OpenSSH agent**.  The on-disk ``~/.ssh/id_ed25519`` is passphrase-encrypted, and
MSYS/Git-Bash ``ssh`` cannot talk to the Windows agent named pipe
(``\\\\.\\pipe\\openssh-ssh-agent``), so plain ``ssh``/``scp`` fail from a
tool-spawned shell.  Paramiko can use that pipe, so this script wraps it.

Usage
-----
    python remote.py run "git log -1 --oneline"
    python remote.py run --cwd /data/... --timeout 3600 "./gradlew :app:assembleMobileArm64_v8aRelease"
    python remote.py put  local/path  /remote/path
    python remote.py get  /remote/path  local/path
    python remote.py sync --root . --remote-root /data/... app/src docs

``run`` derives ``FONGMI_NATIVE_ABIS`` from the ABI flavors named in the command so
media3compat only compiles the ABIs you are actually assembling; override with
``--native-abis`` if needed.

Requires the isolated venv: C:\\Users\\zyq\\.workbuddy-ai\\binaries\\python\\envs\\default
"""

import argparse
import os
import posixpath
import stat
import sys
import time

import paramiko

HOST = "192.168.42.153"
USER = "zyq"
PROJECT = "/data/home/zyq/IdeaProjects/github/FongMi/TV"
ENV = {
    "JAVA_HOME": "/usr/lib/jvm/java-25-openjdk-amd64",
    "ANDROID_HOME": "/data/home/zyq/Android/Sdk",
    # /data/home/zyq/.local/bin is where the user's python3.10 lives; Chaquopy
    # (chaquo/build.gradle sets python.version = "3.10") resolves buildPython
    # from PATH and fails with "Couldn't find Python 3.10" without it.
    "PATH": "/data/home/zyq/.local/bin:/usr/lib/jvm/java-25-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin",
}

# media3compat/build.gradle reads FONGMI_NATIVE_ABIS and falls back to
# "arm64-v8a,armeabi-v7a".  It compiles libmpv, FFmpeg's JNI and dav1d from source
# (bash + NDK + Docker) once per listed ABI, into a single shared jniLibs directory.
#
# media3compat has no ABI flavor dimension, so ONE native build serves every app variant in
# the invocation.  The value therefore has to be the *union* of the ABI flavors being
# assembled: asking for arm64-v8a alone while also assembling the v7a flavor would produce a
# v7a APK with no libmpv.so and no libdav1dJNI.so.  Deriving the union from the task names
# keeps the value exactly sufficient, and never leaves a variant short.
#
# x86_64 was dropped from the project (the closed-source forcetech/jianpian/thunder SDKs have
# no x86_64 binaries), so it is not in this map any more.
ABI_FLAVORS = {
    "Arm64_v8a": "arm64-v8a",
    "Armeabi_v7a": "armeabi-v7a",
}


def native_abis_for(command):
    """Union of the ABI flavors named in a Gradle command, or None if none are named."""
    found = {abi for flavor, abi in ABI_FLAVORS.items() if flavor in command}
    return ",".join(sorted(found)) if found else None


def connect():
    agent = paramiko.Agent()
    if not agent.get_keys():
        raise SystemExit("Windows ssh-agent has no identities; run: ssh-add %USERPROFILE%\\.ssh\\id_ed25519")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, allow_agent=True, look_for_keys=False, timeout=20)
    return client


def run(client, command, cwd=None, timeout=None, env=None, stream=True):
    prefix = ""
    merged = dict(ENV)
    merged.update(env or {})
    for key, value in merged.items():
        prefix += f"export {key}={sh_quote(value)}; "
    if cwd:
        prefix += f"cd {sh_quote(cwd)}; "
    full = prefix + command
    stdin, stdout, stderr = client.exec_command(full, timeout=timeout, get_pty=False)
    if stream:
        # Stream so long builds do not look hung and so we keep partial output on timeout.
        out_chunks, err_chunks = [], []
        channel = stdout.channel
        deadline = None if timeout is None else time.time() + timeout
        while True:
            if channel.recv_ready():
                data = channel.recv(65536).decode("utf-8", "ignore")
                out_chunks.append(data)
                sys.stdout.write(data)
                sys.stdout.flush()
            if channel.recv_stderr_ready():
                data = channel.recv_stderr(65536).decode("utf-8", "ignore")
                err_chunks.append(data)
                sys.stderr.write(data)
                sys.stderr.flush()
            if channel.exit_status_ready() and not channel.recv_ready() and not channel.recv_stderr_ready():
                break
            if deadline and time.time() > deadline:
                channel.close()
                print(f"\n[remote] TIMEOUT after {timeout}s", file=sys.stderr)
                return 124, "".join(out_chunks), "".join(err_chunks)
            time.sleep(0.05)
        status = channel.recv_exit_status()
        return status, "".join(out_chunks), "".join(err_chunks)
    out = stdout.read().decode("utf-8", "ignore")
    err = stderr.read().decode("utf-8", "ignore")
    return stdout.channel.recv_exit_status(), out, err


def sh_quote(value):
    return "'" + str(value).replace("'", "'\\''") + "'"


def ensure_dir(sftp, path):
    parts, current = [], ""
    for part in path.split("/"):
        if not part:
            current = "/"
            continue
        current = posixpath.join(current, part) if current else part
        parts.append(current)
    for directory in parts:
        try:
            sftp.stat(directory)
        except IOError:
            sftp.mkdir(directory)


def put_file(sftp, local, remote):
    ensure_dir(sftp, posixpath.dirname(remote))
    sftp.put(local, remote)


def get_file(sftp, remote, local):
    parent = os.path.dirname(local)
    if parent:
        os.makedirs(parent, exist_ok=True)
    sftp.get(remote, local)


def walk_local(root):
    for current, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle", ".idea", ".workbuddy-ai")]
        for name in files:
            full = os.path.join(current, name)
            yield full, os.path.relpath(full, root).replace("\\", "/")


def cmd_run(args):
    env = dict(kv.split("=", 1) for kv in args.env) if args.env else {}
    if args.native_abis:
        env["FONGMI_NATIVE_ABIS"] = args.native_abis
        print(f"[remote] FONGMI_NATIVE_ABIS={args.native_abis} (explicit)", file=sys.stderr)
    elif "FONGMI_NATIVE_ABIS" not in env:
        derived = native_abis_for(args.command)
        if derived:
            env["FONGMI_NATIVE_ABIS"] = derived
            print(f"[remote] FONGMI_NATIVE_ABIS={derived} (derived from requested flavors)", file=sys.stderr)
    client = connect()
    try:
        status, _, _ = run(client, args.command, cwd=args.cwd, timeout=args.timeout, env=env)
        return status
    finally:
        client.close()


def cmd_put(args):
    client = connect()
    try:
        sftp = client.open_sftp()
        put_file(sftp, args.local, args.remote)
        print(f"put {args.local} -> {args.remote}")
        sftp.close()
    finally:
        client.close()
    return 0


def cmd_get(args):
    client = connect()
    try:
        sftp = client.open_sftp()
        get_file(sftp, args.remote, args.local)
        print(f"get {args.remote} -> {args.local}")
        sftp.close()
    finally:
        client.close()
    return 0


def cmd_sync(args):
    """Upload the listed paths (files or directories) preserving relative layout."""
    client = connect()
    try:
        sftp = client.open_sftp()
        count = 0
        for relative in args.paths:
            local_path = os.path.join(args.root, relative)
            if os.path.isfile(local_path):
                remote = posixpath.join(args.remote_root, relative.replace("\\", "/"))
                put_file(sftp, local_path, remote)
                count += 1
            elif os.path.isdir(local_path):
                for full, rel in walk_local(local_path):
                    remote = posixpath.join(args.remote_root, relative.replace("\\", "/"), rel)
                    put_file(sftp, full, remote)
                    count += 1
            else:
                print(f"skip (missing): {relative}", file=sys.stderr)
        print(f"uploaded {count} file(s) to {args.remote_root}")
        sftp.close()
    finally:
        client.close()
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_run = sub.add_parser("run")
    p_run.add_argument("command")
    p_run.add_argument("--cwd", default=None)
    p_run.add_argument("--timeout", type=int, default=None)
    p_run.add_argument("--env", action="append", help="KEY=VALUE, repeatable")
    p_run.add_argument(
        "--native-abis",
        default=None,
        help="force FONGMI_NATIVE_ABIS (e.g. arm64-v8a); default derives it from the "
             "ABI flavors in the command so media3compat only compiles what is needed",
    )
    p_run.set_defaults(func=cmd_run)

    p_put = sub.add_parser("put")
    p_put.add_argument("local")
    p_put.add_argument("remote")
    p_put.set_defaults(func=cmd_put)

    p_get = sub.add_parser("get")
    p_get.add_argument("remote")
    p_get.add_argument("local")
    p_get.set_defaults(func=cmd_get)

    p_sync = sub.add_parser("sync")
    p_sync.add_argument("paths", nargs="+")
    p_sync.add_argument("--root", default=".")
    p_sync.add_argument("--remote-root", default=PROJECT)
    p_sync.set_defaults(func=cmd_sync)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
