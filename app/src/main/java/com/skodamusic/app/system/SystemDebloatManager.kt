package com.skodamusic.app.system

import android.content.Context
import android.content.pm.PackageManager

object SystemDebloatManager {

    enum class Category {
        DEAD_CLOUD,   // 彻底停服
        RECOMMENDED   // 建议停用
    }

    data class AppInfo(
        val id: String,
        val displayName: String,
        val apkFileName: String,
        val packageName: String,
        val sizeMb: Float,
        val description: String,
        val category: Category,
        var isInstalled: Boolean = true,
        var isDisabled: Boolean = false
    )

    private val APP_CATALOG = listOf(
        // === 彻底停服服务 (DEAD_CLOUD) ===
        AppInfo(
            id = "bm_operation",
            displayName = "斑马运营中心",
            apkFileName = "BmOperationCenter.apk",
            packageName = "com.zebra.operationcenter",
            sizeMb = 16.7f,
            description = "云端活动广告与推送，服务器已彻底停服",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "bm_yuncar",
            displayName = "斑马云车联",
            apkFileName = "BmYunCarConnect.apk",
            packageName = "com.yunosforcar.yuncarservice",
            sizeMb = 4.95f,
            description = "远程车联控制服务，服务器已下线失效",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "bm_intelligent",
            displayName = "斑马智能场景",
            apkFileName = "BmIntelligentService.apk",
            packageName = "com.banma.intelligentparking",
            sizeMb = 4.50f,
            description = "阿里智能推荐引擎，云端接口已关闭",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "ali_account",
            displayName = "阿里车机账号",
            apkFileName = "Account.apk",
            packageName = "com.yunos.account",
            sizeMb = 4.48f,
            description = "车机淘宝与云OS账号服务，已废弃",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "bm_bigdata",
            displayName = "斑马大数据服务",
            apkFileName = "BmBigDataService.apk",
            packageName = "com.banma.bigdataservice",
            sizeMb = 1.68f,
            description = "持续收集车辆遥测与操作日志，耗费RAM",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "fast_driver",
            displayName = "极速驾驶员",
            apkFileName = "FastDriver.apk",
            packageName = "com.yunos.fastdriver",
            sizeMb = 1.50f,
            description = "斑马旧扩展服务，已失效",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "update4car",
            displayName = "车机在线升级",
            apkFileName = "Update4Car.apk",
            packageName = "com.yunos4car.update",
            sizeMb = 1.47f,
            description = "原厂在线OTA检测，服务器已永久关闭",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "weather_service",
            displayName = "云OS天气服务",
            apkFileName = "WeatherService.apk",
            packageName = "com.yunos.weatherservice",
            sizeMb = 1.44f,
            description = "阿里旧天气API，已失效报错",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "fota",
            displayName = "阿里系统FOTA",
            apkFileName = "Fota.apk",
            packageName = "com.aliyun.fota",
            sizeMb = 0.94f,
            description = "阿里云系统OTA检测，服务器已停服",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "first_experience",
            displayName = "首次开机引导",
            apkFileName = "FirstExperience.apk",
            packageName = "com.yunos.firstexperience",
            sizeMb = 0.72f,
            description = "仅首次开机有效，后续纯属冗余驻留",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "ali_datausage",
            displayName = "阿里流量监控",
            apkFileName = "AliDatausage.apk",
            packageName = "com.aliyun.datausage",
            sizeMb = 0.46f,
            description = "原厂内置流量卡统计组件",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "offline_update",
            displayName = "离线升级客户端",
            apkFileName = "OfflineUpdate.apk",
            packageName = "com.yunos.offlineupdate",
            sizeMb = 0.29f,
            description = "本地冗余离线升级检查组件",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "taobao_statistic",
            displayName = "淘宝行为统计",
            apkFileName = "SI20160408_TAMain4Aliyunos-1.1.14.i.apk",
            packageName = "com.taobao.statistic",
            sizeMb = 0.22f,
            description = "后台埋点上传组件，空耗网络与唤醒",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "tyid_service",
            displayName = "阿里移动服务标识",
            apkFileName = "TyidService.apk",
            packageName = "com.aliyun.ams.tyid",
            sizeMb = 0.14f,
            description = "阿里TYID设备鉴权，已无云端支撑",
            category = Category.DEAD_CLOUD
        ),

        // === 建议停用应用 (RECOMMENDED) ===
        AppInfo(
            id = "amap_auto",
            displayName = "出厂高德车机2.7",
            apkFileName = "Auto_V2.7.8.24225_C04010328001_MQB.apk",
            packageName = "com.autonavi.amapauto",
            sizeMb = 64.7f,
            description = "2018年远古版地图，体积高达65MB",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "easy_connect",
            displayName = "亿连手机互联",
            apkFileName = "EasyConnected.apk",
            packageName = "net.easyconn",
            sizeMb = 42.0f,
            description = "老旧手机投屏软件，体积高达42MB",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "svw_portal",
            displayName = "上汽大众车主门户",
            apkFileName = "SVW.apk",
            packageName = "com.svw.car.dvr",
            sizeMb = 22.2f,
            description = "原厂大众斯柯达门户，服务已全部废弃",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "jiuyin_assistant",
            displayName = "久音车机助手",
            apkFileName = "ccJiuYinCheJi.apk",
            packageName = "com.jiuyin.car.dvr",
            sizeMb = 22.2f,
            description = "早期全家桶组件，占用22MB",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "kaola_fm",
            displayName = "考拉FM车机版",
            apkFileName = "kaolafm_carauto-banma-release.apk",
            packageName = "com.edog.car",
            sizeMb = 19.7f,
            description = "考拉FM官方服务早已终止停运",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "kuwo_music",
            displayName = "酷我音乐2019版",
            apkFileName = "kwplay_cr_4.0.3.4_shangqi_Sikeda_banma_2019-11-20.apk",
            packageName = "cn.kuwo.kwmusiccar",
            sizeMb = 12.7f,
            description = "2019年原厂定制酷我，已被本项目替代",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "jsbd_mupdf",
            displayName = "PDF车主手册",
            apkFileName = "JSBDMupdf.apk",
            packageName = "com.jsbd.mupdf",
            sizeMb = 1.38f,
            description = "原厂电子版说明书PDF查看器",
            category = Category.RECOMMENDED
        ),
        AppInfo(
            id = "live_wallpaper",
            displayName = "动态壁纸组件",
            apkFileName = "LiveWallpapersPicker.apk",
            packageName = "com.android.wallpaper.livepicker",
            sizeMb = 0.33f,
            description = "动态壁纸选择器，浪费GPU和内存",
            category = Category.RECOMMENDED
        )
    )

    fun getCatalog(): List<AppInfo> {
        return APP_CATALOG.map { it.copy() }
    }

    fun refreshAppStates(context: Context, apps: List<AppInfo>) {
        val pm = context.packageManager
        val disabledPackages = getDisabledPackageListFromSystem()

        for (app in apps) {
            val pkgName = app.packageName
            val isPkgInstalled = isPackageInstalled(pm, pkgName)
            app.isInstalled = isPkgInstalled

            if (!isPkgInstalled) {
                app.isDisabled = false
                continue
            }

            // Check if disabled via PackageManager or pm list
            val isPmDisabled = try {
                val state = pm.getApplicationEnabledSetting(pkgName)
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            } catch (_: Exception) {
                false
            }

            app.isDisabled = isPmDisabled || disabledPackages.contains(pkgName)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getDisabledPackageListFromSystem(): Set<String> {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-d"))
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            out.lines()
                .map { it.trim().removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setAppEnabled(packageName: String, enable: Boolean): Pair<Boolean, String> {
        val action = if (enable) "enable" else "disable"
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm $action $packageName"))
            val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            val err = proc.errorStream.bufferedReader().use { it.readText() }.trim()
            proc.waitFor()
            val msg = if (out.isNotEmpty()) out else err
            val success = msg.contains("new state") || msg.contains("enabled") || msg.contains("disabled")
            Pair(success, msg.ifEmpty { "执行完成" })
        } catch (e: Exception) {
            Pair(false, "执行异常: ${e.message}")
        }
    }

    fun disableAllDeadCloud(apps: List<AppInfo>): Int {
        var count = 0
        val deadCloudApps = apps.filter { it.category == Category.DEAD_CLOUD && it.isInstalled && !it.isDisabled }
        for (app in deadCloudApps) {
            val res = setAppEnabled(app.packageName, false)
            if (res.first) {
                app.isDisabled = true
                count++
            }
        }
        return count
    }

    fun enableAllDebloated(apps: List<AppInfo>): Int {
        var count = 0
        val disabledApps = apps.filter { it.isInstalled && it.isDisabled }
        for (app in disabledApps) {
            val res = setAppEnabled(app.packageName, true)
            if (res.first) {
                app.isDisabled = false
                count++
            }
        }
        return count
    }

    fun calculateSavedSpaceMb(apps: List<AppInfo>): Float {
        return apps.filter { it.isDisabled }.map { it.sizeMb }.sum()
    }
}
