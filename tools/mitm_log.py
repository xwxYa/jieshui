"""记录手机 <-> 胖乖生活(qiekj) 的全部请求。只落 qiekj 域名的盘,其它一律不记。
请求一发出就写一行,响应回来再补一行,所以"只发出没回来"也能看见。
"""
import json
import os
import time

from mitmproxy import http

OUT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "flows.jsonl"))
KEEP_HOSTS = ()  # 空 = 全部记录(临时诊断用)


def _keep(flow):
    return True if not KEEP_HOSTS else any(h in flow.request.pretty_host for h in KEEP_HOSTS)


def _text(msg):
    try:
        return msg.get_text(strict=False)[:6000]
    except Exception:
        return "<binary>"


def _write(rec):
    with open(OUT, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")


def request(flow: http.HTTPFlow):
    if not _keep(flow):
        return
    _write({
        "phase": "REQ",
        "t": time.strftime("%H:%M:%S"),
        "method": flow.request.method,
        "host": flow.request.pretty_host,
        "path": flow.request.path,
        "url": flow.request.pretty_url,
        "req": _text(flow.request),
    })


def response(flow: http.HTTPFlow):
    if not _keep(flow):
        return
    _write({
        "phase": "RESP",
        "t": time.strftime("%H:%M:%S"),
        "url": flow.request.pretty_url,
        "status": flow.response.status_code,
        "resp": _text(flow.response),
    })


def error(flow: http.HTTPFlow):
    if not _keep(flow):
        return
    _write({
        "phase": "ERR",
        "t": time.strftime("%H:%M:%S"),
        "url": flow.request.pretty_url,
        "error": str(flow.error),
    })
