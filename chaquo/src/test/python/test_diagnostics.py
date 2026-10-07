import importlib.util
import io
import logging
from pathlib import Path
import sys
import threading
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
path = Path(__file__).resolve().parents[2] / "main" / "python" / "tv_diagnostics.py"
spec = importlib.util.spec_from_file_location("tv_diagnostics", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class Sink:
    def __init__(self):
        self.enabled = True
        self.lines = []
        self.generation = 1
        self.lock = threading.RLock()

    def isEnabled(self):
        return self.enabled

    def record(self, source, text):
        with self.lock:
            self.lines.append((source, text))

    def getSessionGeneration(self):
        with self.lock:
            return self.generation

    def recordInSession(self, generation, source, text):
        with self.lock:
            if self.enabled and generation == self.generation:
                self.record(source, text)

    def stop(self):
        with self.lock:
            self.enabled = False
            self.generation += 1

    def clear(self):
        with self.lock:
            self.lines.clear()

    def start(self):
        with self.lock:
            self.generation += 1
            self.enabled = True


class DiagnosticTest(unittest.TestCase):
    def test_fragmented_lines_preserve_output_and_flush(self):
        original, sink = io.StringIO(), Sink()
        tee = module.DiagnosticTee(original, sink, "test")
        tee.write("first")
        tee.write(" line\nsecond")
        tee.flush()
        self.assertEqual("first line\nsecond", original.getvalue())
        self.assertEqual([("test", "first line"), ("test", "second")], sink.lines)

    def test_huge_line_is_bounded_and_secret_continuation_is_discarded(self):
        sink = Sink()
        tee = module.DiagnosticTee(io.StringIO(), sink, "test")
        tee.write("password=" + "x" * 1000000)
        tee.write("SECRET_TAIL\nnext\n")
        self.assertEqual(2, len(sink.lines))
        self.assertLess(len(sink.lines[0][1]), 4200)
        self.assertNotIn("SECRET_TAIL", repr(sink.lines))
        self.assertEqual("next", sink.lines[-1][1])

    def test_disabled_and_broken_sink_cannot_break_source_output(self):
        original, sink = io.StringIO(), Sink()
        tee = module.DiagnosticTee(original, sink, "test")
        sink.enabled = False
        self.assertEqual(4, tee.write("off\n"))
        self.assertEqual([], sink.lines)
        sink.enabled = True
        with patch.object(sink, "record", side_effect=RuntimeError("bridge broken")):
            tee.write("on\n")
        self.assertEqual("off\non\n", original.getvalue())

    def test_threads_do_not_mix_partial_lines(self):
        sink = Sink()
        tee = module.DiagnosticTee(io.StringIO(), sink, "test")
        ready = threading.Barrier(2)
        def write(name):
            tee.write(name)
            ready.wait()
            tee.write(" end\n")
        threads = [threading.Thread(target=write, args=(name,)) for name in ("one", "two")]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        self.assertEqual(["one end", "two end"], sorted(text for _, text in sink.lines))

    def test_restart_without_intermediate_write_drops_previous_partial_line(self):
        original, sink = io.StringIO(), Sink()
        tee = module.DiagnosticTee(original, sink, "test")
        tee.write("old session prefix")
        sink.stop()
        sink.clear()
        sink.start()
        tee.write("new session line\n")
        self.assertEqual([("test", "new session line")], sink.lines)
        self.assertEqual("old session prefixnew session line\n", original.getvalue())

    def test_flush_and_discard_state_do_not_cross_session_boundary(self):
        sink = Sink()
        tee = module.DiagnosticTee(io.StringIO(), sink, "test")
        tee.write("old partial")
        sink.stop()
        sink.clear()
        sink.start()
        tee.flush()
        self.assertEqual([], sink.lines)
        tee.write("x" * 5000)
        sink.stop()
        sink.clear()
        sink.start()
        tee.write("first fresh line\n")
        self.assertEqual([("test", "first fresh line")], sink.lines)

    def test_waiting_threads_drop_their_own_partial_lines_after_restart(self):
        sink = Sink()
        tee = module.DiagnosticTee(io.StringIO(), sink, "test")
        ready = threading.Barrier(3)
        restarted = threading.Event()
        def write(name):
            tee.write("old-" + name)
            ready.wait(timeout=2)
            if restarted.wait(timeout=2):
                tee.write("new-" + name + "\n")
        threads = [threading.Thread(target=write, args=(name,)) for name in ("one", "two")]
        for thread in threads:
            thread.start()
        try:
            ready.wait(timeout=2)
            sink.stop()
            sink.clear()
            sink.start()
        finally:
            restarted.set()
            for thread in threads:
                thread.join(timeout=2)
                self.assertFalse(thread.is_alive())
        self.assertEqual(["new-one", "new-two"], sorted(text for _, text in sink.lines))

    def test_restart_between_session_check_and_emit_rejects_in_flight_old_line(self):
        sink = Sink()
        tee = module.DiagnosticTee(io.StringIO(), sink, "test")
        emit = sink.recordInSession
        def restart_then_emit(generation, source, text):
            sink.stop()
            sink.clear()
            sink.start()
            emit(generation, source, text)
        with patch.object(sink, "recordInSession", side_effect=restart_then_emit):
            tee.write("old in-flight line\n")
        self.assertEqual([], sink.lines)
        tee.write("fresh line\n")
        self.assertEqual([("test", "fresh line")], sink.lines)

    def test_logging_bridge_keeps_existing_handlers_and_installs_once(self):
        sink = Sink()
        existing = logging.NullHandler()
        root = logging.getLogger()
        with patch.object(root, "handlers", [existing]), patch.object(sys, "stdout", io.StringIO()), patch.object(sys, "stderr", io.StringIO()):
            module.install(sink)
            module.install(sink)
            self.assertEqual(2, len(root.handlers))
            self.assertIs(existing, root.handlers[0])
            logging.getLogger("spider-test").warning("source failure")
            self.assertTrue(any("source failure" in text for _, text in sink.lines))


if __name__ == "__main__":
    unittest.main()
