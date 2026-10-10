#!/system/bin/sh
set -e
TMP=/data/local/tmp
HASH=c8750f0d
DEST=/system/etc/security/cacerts

echo "[1] 准备证书目录"
rm -rf $TMP/cacerts
mkdir -p $TMP/cacerts
cp $DEST/* $TMP/cacerts/ 2>/dev/null || true
cp $TMP/$HASH.0 $TMP/cacerts/
chmod 644 $TMP/cacerts/*
chown root:root $TMP/cacerts/*
echo "    原证书数: $(ls $DEST | wc -l)  准备: $(ls $TMP/cacerts | wc -l)"

echo "[2] tmpfs 覆盖挂载"
mount -t tmpfs tmpfs $DEST
cp $TMP/cacerts/* $DEST/
chmod 644 $DEST/*
chown root:root $DEST/*
echo "    挂载后: $(ls $DEST | wc -l)"

echo "[3] 校验"
ls -la $DEST/$HASH.0
