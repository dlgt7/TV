"""Best-effort, opt-in Python bridge. Preserve original streams and logging handlers."""
import logging
import sys
import threading

_LIMIT = 4096


class DiagnosticTee:
    def __init__(self, original, sink, source):
        self._original = original
        self._sink = sink
        self._source = source
        self._local = threading.local()

    def __getattr__(self, name):
        return getattr(self._original, name)

    def write(self, value):
        result = self._original.write(value)
        try:
            if not self._sink.isEnabled():
                self._local.pending = ""
                self._local.discard = False
                return result
            pending = getattr(self._local, "pending", "")
            discard = getattr(self._local, "discard", False)
            # Bounded streaming, including a single print containing megabytes without a newline.
            start = 0
            while start < len(value):
                end = value.find("\n", start)
                stop = len(value) if end < 0 else end
                if not discard:
                    available = _LIMIT - len(pending)
                    pending += value[start:min(stop, start + available)]
                    if stop - start > available:
                        self._sink.record(self._source, pending + " [truncated]")
                        pending = ""
                        discard = True
                if end < 0:
                    break
                if not discard:
                    self._sink.record(self._source, pending.rstrip("\r"))
                pending, discard = "", False
                start = end + 1
            self._local.pending = pending
            self._local.discard = discard
        except Exception:
            pass  # A failed diagnostic bridge must never fail a spider's print/write.
        return result

    def flush(self):
        self._original.flush()
        try:
            pending = getattr(self._local, "pending", "")
            if pending and self._sink.isEnabled():
                self._sink.record(self._source, pending)
            self._local.pending = ""
        except Exception:
            pass


class DiagnosticHandler(logging.Handler):
    def __init__(self, sink):
        super().__init__()
        self._sink = sink

    def emit(self, record):
        try:
            if self._sink.isEnabled():
                self._sink.record("python:logging", self.format(record)[:32768])
        except Exception:
            pass


def install(sink=None):
    try:
        if sink is None:
            from com.github.catvod.crawler.diagnostics import DiagnosticLog
            sink = DiagnosticLog
        if not isinstance(sys.stdout, DiagnosticTee):
            sys.stdout = DiagnosticTee(sys.stdout, sink, "python:stdout")
        if not isinstance(sys.stderr, DiagnosticTee):
            sys.stderr = DiagnosticTee(sys.stderr, sink, "python:stderr")
        root = logging.getLogger()
        if not root.handlers:
            logging.basicConfig()
        if not any(isinstance(handler, DiagnosticHandler) for handler in root.handlers):
            handler = DiagnosticHandler(sink)
            handler.setFormatter(logging.Formatter("%(levelname)s %(name)s: %(message)s"))
            root.addHandler(handler)
    except Exception:
        pass
