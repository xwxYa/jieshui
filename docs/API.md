# 胖乖生活 饮水机 完整流程(reverse 结果)

Base: https://userapi.qiekj.com
签名: sha256("appSecret=nFU9pbG8YQoAe1kFh+E7eyrdlSLglwEJeA0wwHB1j5o=&channel=android_app&timestamp={ms}&token={token}&version=1.60.3&{path}")
Header: Authorization/Version/channel/timestamp/sign

设备: 某栋某楼热水  goodsId=1000000001  skuId=1000000002  imei=860000000000001
      shopId=202200000000000000000000000001  orgId=200000001  categoryCode=04

## 出水(无需先下单)
1. POST /goods/normal/details              goodsId=1000000001
2. POST /payChannelRoute/afterPay          imei=..&useTicket=0&shopId=200000001&goodsCategory=04
3. POST /payChannelRoute/addUserAfterPayChannel   method=15     (仅在未绑定时)
4. POST /orderRisk/isCheckLocation         categoryCode=04&imei=..
5. POST /goods/ble/water/unlock            skuId=1000000002&promotions=[...]
      -> 返回 orderNo + encryptionCmd
6. 轮询 POST /goods/water/sync              skuId=1000000002
      status 6=启动中  1=出水中  3/5=结束
7. POST /goods/endWater                    goodsId=1000000001

## promotions 固定值
[{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-6"},
 {"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-7"},
 {"assetId":"0","oldPromotionId":"0","orgId":"0","promotionId":"0","promotionType":"-8"}]

---

# 附录:饮水机完整接口地图(2026-10 补充)

## 结论先说

**没有"直接出水"的接口。** App 只能「解锁」和「结束」,真正出水靠机器上的物理按钮。
证据:全站 119 个抓包路径 + h5 全部页面里,不存在 start / open / flow / valve 类接口。

## 两条下发通道

### A. 服务端通道(2G/4G,机器自带网络模块)

```
POST machine/water/unlock      {type, machineFunctionId, msgId}   → {msgId}
POST machine/water/stateCmd    {type:1, machineId}                → {msgId}   ← 2G 查状态
POST machine/sync              {msgId}                            → 机器有无拉到指令
POST machine/function/listState {machineId, msgId}                → {items:[{id,state,isBelong}]}
POST machine/msg/sync                                             → {single,state,data,code}
POST machine/water/sync        {machineFunctionId}                → {status, amount, identify}
POST goods/ble/water/unlock    {skuId, promotions}                → {msgId, orderNo, cmd:{encryptionCmd, bleCmd}}
```

### B. 蓝牙通道(手机直连机器,近距离)

```
POST machine/encryption/cmd    {type:1, machineId}                → {bleCmd}   ← 解锁指令(蓝牙)
POST machine/encryption/cmd    {type:3, machineId, identify}      → {bleCmd}   ← 结算指令(蓝牙)
POST machine/bluetooth/report  {encryptionData, imei, cmdCode:"0105"} → 上报
```

蓝牙交互(来自 app.js):

```js
await u.connect(imei, {handleCmdback: a => {
   let t = a.substring(2,4);          // 指令类型
   if("07" !== t) return;             // 只认 07 = 机器主动上报
   let s = a.substring(18);           // 数据段
   s && this.bleSecretUp(s, imei);    // → machine/bluetooth/report
}});
u.write(bleCmd, true, msgId);         // 发指令,回码 "00" = 成功
```

## 状态机(`machine/water/sync`)

```js
switch(e.status){
  case 1: case 2: case 4: return;                 // 进行中,每秒继续轮询
  case 3: case 5:                                 // 结束
      if (e.amount > 0 && e.identify) handleStop(e.identify);  // → /pay/afterPay/addOrder?id=
      else                            handleReLaunch();        // 没花钱,回首页
  case 6: return handleReLaunch();
}
```

**注意:`amount` 是金额(元),不是水量。`identify` 是订单号。**

## 官方文案(照抄用)

```
"请稍等，正在解锁中"
"解锁成功"
"请在出水口处点按开始接水"     ← 证明出水由机器按钮触发
"结束后将自动为您计费"
"如何开启蓝牙？"
```

## 定量接水可行吗?

**官方不支持** —— `quantity {minValue,maxValue,stepValue,defaultValue}` 是**充电桩**(预付费)的参数,
饮水机是**后付费按流量**,没有"预先选量"。

**但 App 端可以近似实现:**

```
解锁 → 每秒轮询 machine/water/sync 拿 amount
     → 水量 ≈ (amount ÷ 0.011) × 0.1 L
     → 达到目标 → 自动 POST goods/endWater
```

**精度估算**(基于真实数据 2:14 / 1.6 L → 0.012 L/s):

| 轮询间隔 | 最大超量 | 接 1L 的相对误差 |
|---|---|---|
| 1 s | ~12 mL | ~1.2% |

**前提:`amount` 在取水过程中实时更新。** ← 待实测
若 `amount` 只精确到 0.01 元 → 再接 4.5% 误差;只到 0.1 元 → 误差 45%,不可用。

**注意:** 解锁后是用户按机器按钮出水,App 只能"提前结束",不能"启动出水"。
所以定量接水的效果是「**防止接多**,而不是自动接」。

---

# 附录 B:官方 App v1.96.1 反编补充(2026-10)

> 来源:另一个项目 `~/projects/reverse` —— 对官方 APK(`com.qiekj.user` 1.96.1 / versionCode 174,
> 360 加固)做了 frida 脱壳 + jadx 反编译,拿到了 `com.qiekj.user.http.*` 的真实实现。
> 那边的 `FINDINGS.md`(脱壳 + 签名)和 `FLOWS_API.md`(60+ 接口抓包清单)记得更全,
> `ApiService.java` 里总共 510 个接口路径。

## B.1 签名其实有三套

### ① header `sign` —— 每个请求都带,本项目已实现

来源 `com.qiekj.user.http.interceptor.HeadInterceptor`:

```
SHA256("appSecret=" + SECRET
     + "&channel=android_app"
     + "&timestamp=" + System.currentTimeMillis()
     + "&token=" + token
     + "&version=" + AppUtils.getAppVersionName()
     + "&" + request.url().encodedPath())
```

`SECRET` 在官方源码里是两层 Base64 混淆的,解出来就是
`nFU9pbG8YQoAe1kFh+E7eyrdlSLglwEJeA0wwHB1j5o=` —— 和 `Net.kt` 里那个一致。

⚠️ **`version` 是动态取的**(`AppUtils.getAppVersionName()`),不是写死的常数。
官方当前是 `1.96.1`,所以 `Net.kt` 里硬编码 `1.96.1` 现在是对的 —— 但官方一升级就得跟着改。
(本文档开头写的 `version=1.60.3` 是更早的抓包,已过时。)

### ② 表单 `_sign` + `_timestamp` + `_appid` —— 本项目没实现

来源 `com.qiekj.user.http.interceptor.SignInterceptor`,对 `FormBody` 额外塞两个字段:

```
1. 收集所有非空表单字段 "name=value"(value 含特殊字符时先 URLDecode)
2. 追加 "_timestamp=" + System.currentTimeMillis()
3. 追加 "_appid=44efec05494c4ca3a4a7ada47722a1a8"
4. Collections.sort()          ← 字典序排序,关键
5. SHA1(用 "&" 连接)           ← 注意是 SHA1,不是 SHA256
6. 把 _timestamp 和 _sign 塞回表单体
```

**当前不实现**:查询类接口都不校验它(实测全部 200)。
哪天某个接口开始返回签名错误,这段就是答案。

### ③ `orderRiskSign` —— 只对三条路径

`/goods/shower/unlock`、`/goods/water/unlock`、`/trade/create`。
命中时额外加 `orderRiskSign` + `orderRiskTimestamp` 两个 header,算法里额外掺了
`categoryCode / imei / lat / lng`。

> 这三条路径正好落在本项目的红线上(会产生真实扣费订单)。
> **只记录算法,不实现。**

## B.2 每个请求还带 `phoneBrand`

官方 header 里有 `phoneBrand = BrandUtils.getDeviceBrand()`。
`Net.kt` 原本没有,2026-10 补上了(`android.os.Build.BRAND`)。

## B.3 `workStatus` —— 机器到底空不空

设备卡片上那行状态要说真话,靠这个。

### `POST /goods/water/sync`(参数 `skuId`)的 `workStatus`

| 值 | 含义 |
|---|---|
| 1 | 空闲,可解锁 |
| 2 | 我自己的订单在跑 |
| 3 | 别人在用 → 官方 Toast「设备工作中」 |

### `POST /goods/latestUsed` / `deviceWorkStatus` 的 10/30/40 体系

| 值 | 含义 |
|---|---|
| 10 | 空闲 |
| 30 | 故障 |
| 40 | 停用 |
| 其它 | 使用中 |

**两套不是一个体系,别混用。** 同一个 `goods/water/sync` 响应里还有 `status`(出水过程中用):
1 = 进行中、3/5 = 结束、6 = 设备异常。实测序列:`{6,1}` → `{1,2,identify=orderNo}` → `{5,1,amount=0}`。

## B.4 空闲/占用能查的接口一览

| 接口 | 参数 | 状态字段 |
|---|---|---|
| `POST /goods/water/sync` | `skuId` | `workStatus` 1/2/3,实时 ← **本项目用的** |
| `POST /goods/latestUsed` | `categoryCode` | `data[].workStatus` 10/30/40 |
| `POST /appointNew/near/newMachines` | `goodsPage pageSize shopId machineTypeId orgId` | `items[].deviceWorkStatus` |
| `POST /shop/nearby/list` | 经纬度 | `freeMachine` / `machineCount`(网点级) |
| `POST /machine/device_is_start` | `orderNo` | Boolean |
| `POST /trade/check_device_status` | `goodsId` | 设备健康检查(`health`) |
