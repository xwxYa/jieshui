package com.qiekj.hotwater.data

import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.model.OrderItem

object DemoData {
    val devices = listOf(
        Device(
            goodsId = "demo1",
            name = "1号楼西热水",
            shopName = "教学楼直饮水",
            imei = "860000000000001",
            sn = "860000000000001",
            skuId = "demo1",
            price = "0.011",
            machineId = "dm1",
            online = true,
            supportBle = true,
            workStatus = 1
        ),
        Device(
            goodsId = "demo2",
            name = "2号楼东热水",
            shopName = "宿舍楼直饮水",
            imei = "860000000000002",
            sn = "860000000000002",
            skuId = "demo2",
            price = "0.011",
            machineId = "dm2",
            online = true,
            supportBle = false,
            workStatus = 3
        ),
        Device(
            goodsId = "demo3",
            name = "楼下那台",
            shopName = "老楼直饮水",
            imei = "860000000000003",
            sn = "860000000000003",
            skuId = "demo3",
            price = "",
            machineId = "dm3",
            online = false,
            supportBle = false,
            workStatus = 0
        )
    )

    val orders = listOf(
        OrderItem("d1", "1号楼西热水", 0.25, "2026-03-18 07:12:00", "2026-03-18", "3月18日", "07:12"),
        OrderItem("d2", "2号楼东热水", 0.99, "2026-03-18 07:10:00", "2026-03-18", "3月18日", "07:10"),
        OrderItem("d3", "1号楼西热水", 2.30, "2026-03-17 12:31:00", "2026-03-17", "3月17日", "12:31"),
        OrderItem("d4", "1号楼西热水", 0.76, "2026-03-17 20:02:00", "2026-03-17", "3月17日", "20:02"),
        OrderItem("d5", "1号楼西热水", 1.85, "2026-03-15 21:04:00", "2026-03-15", "3月15日", "21:04"),
        OrderItem("d6", "2号楼东热水", 1.42, "2026-02-24 18:45:00", "2026-02-24", "2月24日", "18:45"),
        OrderItem("d7", "1号楼西热水", 2.10, "2026-01-11 09:20:00", "2026-01-11", "1月11日", "09:20"),
        OrderItem("d8", "1号楼西热水", 3.60, "2025-12-19 22:15:00", "2025-12-19", "12月19日", "22:15"),
        OrderItem("d9", "2号楼东热水", 2.85, "2025-11-08 14:30:00", "2025-11-08", "11月8日", "14:30"),
        OrderItem("d10", "1号楼西热水", 1.95, "2025-10-21 10:05:00", "2025-10-21", "10月21日", "10:05")
    )
}
