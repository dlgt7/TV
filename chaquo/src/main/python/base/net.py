"""Native crawler API adapters. Legacy requests-based Spider helpers stay separate."""
import json
import os

from java import dynamic_proxy, jclass

_Callable = jclass("java.util.concurrent.Callable")
_File = jclass("java.io.File")
_Init = jclass("com.github.catvod.Init")
_Options = jclass("com.github.catvod.net.NetOptions")


def _encode(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":"))


def _decode(value):
    return None if value is None else json.loads(str(value))


def _response(value, options):
    result = _decode(value)
    if options.get("buffer") == 3 and isinstance(result.get("content"), list):
        result["content"] = bytes(n & 255 for n in result["content"])
    return result


class _Loader(dynamic_proxy(_Callable)):
    def __init__(self, function):
        super().__init__()
        self.function = function

    def call(self):
        return _encode(self.function())


class Net:
    def __init__(self, native):
        self._native = native

    def req(self, url, options=None):
        options = {} if options is None else options
        return _response(self._native.req(url, _encode(options)), options)

    def json(self, url, options=None):
        return _decode(self._native.json(url, _encode({} if options is None else options)))

    def get(self, url, headers=None):
        if headers is None:
            return str(self._native.get(url))
        result = self.req(url, {"headers": headers})
        if result.get("error"):
            raise RuntimeError(result["error"])
        return result["content"]

    def post(self, url, value, headers=None):
        options = {"method": "POST", "data": value}
        if headers is not None:
            options["headers"] = headers
        result = self.req(url, options)
        if result.get("error"):
            raise RuntimeError(result["error"])
        return result["content"]

    def ws(self, url, options=None):
        options = {} if options is None else options
        return _response(self._native.ws(url, _encode(options)), options)

    def batch(self, requests, limit=4):
        results = self._native.batch(_encode(requests), int(limit))
        output = []
        for index, result in enumerate(results):
            options = requests[index].get("options") or {}
            settings = getattr(_Options, "from")(_encode(options))
            output.append(_response(result.json(settings), options))
        return output

    def cached(self, key, options, loader):
        return _decode(self._native.cached(key, _encode(options), _Loader(loader)))

    def peek(self, key):
        return _decode(self._native.peek(key))

    def refresh(self, key, options, loader):
        self._native.refresh(key, _encode(options), _Loader(loader))

    def clearCache(self, key=None):
        if key is None:
            self._native.clearCache()
        else:
            self._native.clearCache(key)

    def download(self, url, path, options=None):
        path = os.fspath(path)
        if not os.path.isabs(path):
            path = os.path.join(str(_Init.context().getCacheDir().getAbsolutePath()), path)
        self._native.download(url, _encode({} if options is None else options), _File(path))
        return path

    def getUserAgent(self):
        return str(jclass("com.github.catvod.utils.Util").CHROME)

    def getCookie(self, url):
        return str(self._native.getCookie(url) or "")

    def setCookie(self, url, value):
        return bool(self._native.setCookie(url, value))

    def sleep(self, milliseconds):
        self._native.sleep(int(milliseconds))

    def session(self):
        return Net(self._native.session())

    def close(self):
        self._native.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class Local:
    def __init__(self, native):
        self._native = native

    def get(self, key):
        return _decode(self._native.get(key))

    def set(self, key, value):
        self._native.set(key, _encode(value))

    def delete(self, key):
        self._native.delete(key)

    def clear(self):
        self._native.clear()


def bind(spider, native_net, native_local):
    # An old source may already use these attribute names for its own objects.
    if getattr(spider, "net", None) is None or isinstance(spider.net, Net):
        spider.net = Net(native_net)
    if getattr(spider, "local", None) is None or isinstance(spider.local, Local):
        spider.local = Local(native_local)
