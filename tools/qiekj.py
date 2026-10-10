#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
起开科技 userapi.qiekj.com 接口探测工具 —— 仅用于自己账号的互操作性调试。
签名算法来源: https://github.com/Night-stars-1/wash  (utils/SigningInterceptor.kt)
零依赖, 只用标准库。

用法:
  python qiekj.py send   <手机号>            发短信验证码
  python qiekj.py verify <手机号> <验证码>   注册/登录, 拿到并保存 token
  python qiekj.py me                        看自己账号信息(含钱包余额 balance)
  python qiekj.py scan   <二维码内容>        扫码解析设备 -> 看 categoryCode / categoryName / type
  python qiekj.py list   <categoryCode>      列该品类设备(001=洗衣机)
  python qiekj.py sweep                      遍历 001~030 品类码, 找哪几个有数据
  python qiekj.py detail <goodsId>           设备详情
  python qiekj.py skus   <goodsId>           计费规格
  python qiekj.py raw    <path> k=v k=v ...  裸调任意接口(自己试)
"""
import sys, os, json, time, hashlib, urllib.request, urllib.parse, urllib.error

# Windows 控制台默认 GBK, 强制 UTF-8, 免得中文乱码
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

APP_SECRET = "nFU9pbG8YQoAe1kFh+E7eyrdlSLglwEJeA0wwHB1j5o="
BASE = "https://userapi.qiekj.com"
CHANNEL = "android_app"
VERSION = os.environ.get("QIEKJ_VERSION", "1.60.3")
HERE = os.path.dirname(os.path.abspath(__file__))
TOKEN_FILE = os.path.join(HERE, "token.txt")


def sha256_hex(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def post(path, data, token="", json_body=None):
    ts = str(int(time.time() * 1000))
    raw = ("appSecret=%s&channel=%s&timestamp=%s&token=%s&version=%s&%s"
           % (APP_SECRET, CHANNEL, ts, token, VERSION, path))
    ctype = "application/json" if json_body is not None else "application/x-www-form-urlencoded"
    headers = {
        "Authorization": token,
        "Version": VERSION,
        "channel": CHANNEL,
        "timestamp": ts,
        "sign": sha256_hex(raw),
        "User-Agent": "okhttp/4.12.0",
        "Content-Type": ctype,
    }
    body = (json.dumps(json_body, ensure_ascii=False).encode("utf-8")
            if json_body is not None
            else urllib.parse.urlencode(data).encode("utf-8"))
    req = urllib.request.Request(BASE + path,
                                 data=body,
                                 headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            txt = r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", "replace")
    except Exception as e:
        return {"_error": str(e)}
    try:
        return json.loads(txt)
    except Exception:
        return {"_raw": txt}


LAST_FILE = os.path.join(HERE, "last_result.json")


def show(o):
    """打印 + 落盘。落盘那份给 pi 直接读, 不受终端编码影响。"""
    txt = json.dumps(o, ensure_ascii=False, indent=2)
    print(txt)
    try:
        with open(LAST_FILE, "w", encoding="utf-8") as f:
            f.write(txt)
    except Exception:
        pass


def get_token():
    if os.path.exists(TOKEN_FILE):
        return open(TOKEN_FILE, "r", encoding="utf-8").read().strip()
    return ""


def usage():
    print(__doc__)


def main():
    if len(sys.argv) < 2:
        return usage()
    cmd, a = sys.argv[1], sys.argv[2:]
    t = get_token()

    if cmd == "send":
        show(post("/common/sms/sendCode", {"phone": a[0], "template": "reg"}))
    elif cmd == "verify":
        r = post("/user/reg", {"phone": a[0], "verify": a[1], "channel": CHANNEL})
        show(r)
        tok = (r.get("data") or {}).get("token")
        if tok:
            open(TOKEN_FILE, "w", encoding="utf-8").write(tok)
            print("\n[OK] token 已保存到 token.txt")
    elif cmd == "me":
        show(post("/user/info", {"token": t}, t))
    elif cmd == "scan":
        show(post("/goods/scan/v2", {"NQT": a[0], "token": t}, t))
    elif cmd == "list":
        show(post("/goods/latestUsed", {"categoryCode": a[0], "token": t}, t))
    elif cmd == "sweep":
        for i in range(1, 31):
            cc = "%03d" % i
            r = post("/goods/latestUsed", {"categoryCode": cc, "token": t}, t)
            d = r.get("data")
            if d:
                print("[命中] %s -> %s" % (cc, json.dumps(d, ensure_ascii=False)))
            time.sleep(0.5)
        print("sweep 完成")
    elif cmd == "detail":
        show(post("/goods/normal/details", {"goodsId": a[0], "token": t}, t))
    elif cmd == "skus":
        show(post("/goods/normal/skus", {"goodsId": a[0], "token": t}, t))
    elif cmd == "raw":
        p = a[0] if a[0].startswith("/") else "/" + a[0]
        d = dict(kv.split("=", 1) for kv in a[1:])
        d.setdefault("token", t)
        show(post(p, d, t))
    elif cmd == "jraw":
        p = a[0] if a[0].startswith("/") else "/" + a[0]
        obj = json.loads(a[1])
        obj.setdefault("token", t)
        show(post(p, {}, t, json_body=obj))
    else:
        usage()


if __name__ == "__main__":
    main()
