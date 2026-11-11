package com.skodamusic.app.system

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

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
            id = "zebra_account",
            displayName = "斑马车机账号",
            apkFileName = "ZebraAccount.apk",
            packageName = "com.zebra.account",
            sizeMb = 6.52f,
            description = "斑马车机账号与云端通行证体系，已废弃",
            category = Category.DEAD_CLOUD
        ),
        AppInfo(
            id = "ali_account",
            displayName = "阿里车机账号",
            apkFileName = "account.apk",
            packageName = "com.yunos.account",
            sizeMb = 3.89f,
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
            id = "http_dns",
            displayName = "阿里HttpDNS",
            apkFileName = "HttpDNS.apk",
            packageName = "com.aliyun.httpdns",
            sizeMb = 0.20f,
            description = "阿里早期域名解析服务，已失效",
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
        AppInfo(
            id = "ali_uuid",
            displayName = "阿里UUID注册",
            apkFileName = "ali-uuidreg.apk",
            packageName = "com.aliyun.uuid",
            sizeMb = 0.10f,
            description = "设备唯一号云端注册组件，服务器已停运",
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
            id = "kaola_fm",
            displayName = "考拉FM车机版",
            apkFileName = "kaolafm_carauto-banma-release.apk",
            packageName = "com.edog.car",
            sizeMb = 19.7f,
            description = "考拉FM官方服务早已终止停运",
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
        val allSysFiles = getSystemAppFileNames()

        for (app in apps) {
            val pkgName = app.packageName
            val apkName = app.apkFileName

            // Multi-tier install check: PM + filesystem check
            val isPkgInstalled = isPackageInstalled(pm, pkgName, apkName, allSysFiles)
            app.isInstalled = isPkgInstalled

            if (!isPkgInstalled) {
                app.isDisabled = false
                continue
            }

            // Multi-tier disabled check: .disabled/.bak file + PM setting + pm list -d
            app.isDisabled = isAppDisabled(pm, pkgName, apkName, disabledPackages, allSysFiles)
        }
    }

    private fun getSystemAppFileNames(): Set<String> {
        return try {
            val dir = File("/system/app")
            if (dir.exists() && dir.isDirectory) {
                dir.list()?.map { it.lowercase() }?.toSet() ?: emptySet()
            } else {
                emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun isPackageInstalled(
        pm: PackageManager,
        packageName: String,
        apkFileName: String,
        sysFilesLower: Set<String>
    ): Boolean {
        // 1. Filesystem existence check (handles /system/app directly)
        val apkLower = apkFileName.lowercase()
        if (sysFilesLower.contains(apkLower) ||
            sysFilesLower.contains("$apkLower.disabled") ||
            sysFilesLower.contains("$apkLower.bak")
        ) {
            return true
        }

        // 2. PackageManager check (with GET_UNINSTALLED_PACKAGES flag)
        return try {
            pm.getPackageInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isAppDisabled(
        pm: PackageManager,
        packageName: String,
        apkFileName: String,
        disabledPackages: Set<String>,
        sysFilesLower: Set<String>
    ): Boolean {
        // 1. File-level freeze check
        val apkLower = apkFileName.lowercase()
        if (sysFilesLower.contains("$apkLower.disabled") || sysFilesLower.contains("$apkLower.bak")) {
            return true
        }

        // 2. PM disabled packages list
        if (disabledPackages.contains(packageName)) {
            return true
        }

        // 3. PM component enabled setting
        return try {
            val state = pm.getApplicationEnabledSetting(packageName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
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

    fun setAppEnabled(app: AppInfo, enable: Boolean): Pair<Boolean, String> {
        return setAppEnabled(app.packageName, app.apkFileName, enable)
    }

    fun setAppEnabled(packageName: String, apkFileName: String, enable: Boolean): Pair<Boolean, String> {
        var anySuccess = false
        val logs = mutableListOf<String>()

        if (!enable) {
            // --- 停用流程 ---
            // 1. 尝试 pm disable 和 pm disable-user
            runCatching {
                val proc = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "pm disable $packageName; pm disable-user $packageName")
                )
                val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
                val exit = proc.waitFor()
                if (exit == 0 || out.contains("new state") || out.contains("disabled")) {
                    anySuccess = true
                    logs.add("pm已停用")
                }
            }

            // 2. 文件级重命名冻结 (mount rw -> rename .disabled -> mount ro)
            val candidates = listOf(
                File("/system/app/$apkFileName"),
                File("/system/app/${apkFileName.lowercase()}"),
                File("/system/app/${apkFileName.replaceFirstChar { it.uppercase() }}")
            )
            val foundApk = candidates.firstOrNull { it.exists() }
            if (foundApk != null) {
                val realName = foundApk.name
                val remountCmd = "mount -o remount,rw /system && mv /system/app/$realName /system/app/$realName.disabled && mount -o remount,ro /system"
                runCatching {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", remountCmd))
                    proc.waitFor()
                    if (File("/system/app/$realName.disabled").exists() || !File("/system/app/$realName").exists()) {
                        anySuccess = true
                        logs.add("文件已冻结(.disabled)")
                    }
                }
            }

            // 3. 强制终止后台残留进程
            runCatching {
                Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "am force-stop $packageName; pkill -f $packageName")
                ).waitFor()
            }
        } else {
            // --- 恢复流程 ---
            // 1. 文件级解冻恢复
            val disabledCandidates = listOf(
                File("/system/app/$apkFileName.disabled"),
                File("/system/app/$apkFileName.bak"),
                File("/system/app/${apkFileName.lowercase()}.disabled"),
                File("/system/app/${apkFileName.lowercase()}.bak")
            )
            val foundFrozen = disabledCandidates.firstOrNull { it.exists() }
            if (foundFrozen != null) {
                val targetName = foundFrozen.name.removeSuffix(".disabled").removeSuffix(".bak")
                val remountCmd = "mount -o remount,rw /system && mv ${foundFrozen.absolutePath} /system/app/$targetName && chmod 644 /system/app/$targetName && mount -o remount,ro /system"
                runCatching {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", remountCmd))
                    proc.waitFor()
                    if (File("/system/app/$targetName").exists()) {
                        anySuccess = true
                        logs.add("文件已恢复(.apk)")
                    }
                }
            }

            // 2. pm enable
            runCatching {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm enable $packageName"))
                val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
                val exit = proc.waitFor()
                if (exit == 0 || out.contains("new state") || out.contains("enabled")) {
                    anySuccess = true
                    logs.add("pm已启用")
                }
            }
        }

        return if (anySuccess) {
            Pair(true, "操作成功: ${logs.joinToString(", ")}")
        } else {
            Pair(false, "Root 冻结命令未返回成功状态，请检查 su 授权与系统分区读写状态")
        }
    }

    fun disableAllDeadCloud(apps: List<AppInfo>): Int {
        var count = 0
        val deadCloudApps = apps.filter { it.category == Category.DEAD_CLOUD && it.isInstalled && !it.isDisabled }
        for (app in deadCloudApps) {
            val res = setAppEnabled(app, false)
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
            val res = setAppEnabled(app, true)
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
