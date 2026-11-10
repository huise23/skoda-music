package com.skodamusic.app.system

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import java.io.File

object SystemUpgradeManager {

    data class FirmwareInspection(
        val isUsbMounted: Boolean,
        val mountedPaths: List<String>,
        val firmwarePath: String?,
        val hasTriggerFile: Boolean,
        val hasSystemImg: Boolean,
        val hasUboot: Boolean
    ) {
        val isComplete: Boolean
            get() = hasTriggerFile && hasSystemImg && hasUboot
    }

    fun inspectFirmware(): FirmwareInspection {
        val candidateMounts = mutableListOf<File>()
        val mntDir = File("/mnt")
        if (mntDir.exists() && mntDir.isDirectory) {
            mntDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.lowercase().contains("udisk")) {
                    candidateMounts.add(file)
                }
            }
        }
        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            storageDir.listFiles()?.forEach { file ->
                if (file.isDirectory && (file.name.lowercase().contains("usb") || file.name.lowercase().contains("udisk"))) {
                    candidateMounts.add(file)
                }
            }
        }

        var foundDir: File? = null
        var foundTrigger = false
        var foundSystem = false
        var foundUboot = false

        for (dir in candidateMounts) {
            val trigger = File(dir, "yc8317.img")
            val system = File(dir, "system.img.ext4")
            val uboot = File(dir, "u-boot.bin")

            val triggerExists = trigger.exists()
            val systemExists = system.exists()
            val ubootExists = uboot.exists()

            if (triggerExists || systemExists || ubootExists) {
                foundDir = dir
                foundTrigger = triggerExists
                foundSystem = systemExists
                foundUboot = ubootExists
                if (triggerExists && systemExists && ubootExists) {
                    break
                }
            }
        }

        return FirmwareInspection(
            isUsbMounted = candidateMounts.isNotEmpty(),
            mountedPaths = candidateMounts.map { it.absolutePath },
            firmwarePath = foundDir?.absolutePath,
            hasTriggerFile = foundTrigger,
            hasSystemImg = foundSystem,
            hasUboot = foundUboot
        )
    }

    sealed class UpgradeTriggerResult {
        object Rebooting : UpgradeTriggerResult()
        data class ManualRebootRequired(val message: String) : UpgradeTriggerResult()
        data class Failure(val error: String) : UpgradeTriggerResult()
    }

    fun triggerUpgrade(context: Context, log: (String) -> Unit): UpgradeTriggerResult {
        log("SystemUpgradeManager: attempting to write METAZONE update mode = 1")
        try {
            val clazz = Class.forName("com.yecon.metazone.YeconMetazone")
            val setMethod = clazz.getMethod("SetUpdateMode", Int::class.javaPrimitiveType)
            val ret = setMethod.invoke(null, 1) as? Int ?: -1
            log("SystemUpgradeManager: YeconMetazone.SetUpdateMode(1) returned $ret")

            val getMethod = clazz.getMethod("GetUpdateMode")
            val mode = getMethod.invoke(null) as? Int ?: -1
            log("SystemUpgradeManager: YeconMetazone.GetUpdateMode() returned $mode")

            if (mode != 1) {
                return UpgradeTriggerResult.Failure("METAZONE 写入校验失败(当前mode=$mode, setRet=$ret)")
            }
        } catch (e: Throwable) {
            log("SystemUpgradeManager: YeconMetazone reflection exception: ${e.message}")
            return UpgradeTriggerResult.Failure("调用底层 METAZONE 异常: ${e.javaClass.simpleName}: ${e.message}")
        }

        log("SystemUpgradeManager: update mode verified. Attempting system reboot...")
        var rebootDispatched = false

        // 1. Broadcast carocean internal reboot action
        try {
            context.sendBroadcast(Intent("action.carocean.reboot"))
            log("SystemUpgradeManager: sent action.carocean.reboot broadcast")
        } catch (e: Throwable) {
            log("SystemUpgradeManager: action.carocean.reboot broadcast error: ${e.message}")
        }

        // 2. PowerManager standard reboot
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.reboot("")
            log("SystemUpgradeManager: pm.reboot() invoked")
            rebootDispatched = true
        } catch (e: Throwable) {
            log("SystemUpgradeManager: pm.reboot() failed: ${e.message}")
        }

        // 3. ServiceManager IPowerManager reflection
        if (!rebootDispatched) {
            try {
                val smClass = Class.forName("android.os.ServiceManager")
                val getServiceMethod = smClass.getMethod("getService", String::class.java)
                val binder = getServiceMethod.invoke(null, "power") as? IBinder
                if (binder != null) {
                    val stubClass = Class.forName("android.os.IPowerManager\$Stub")
                    val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
                    val ipm = asInterfaceMethod.invoke(null, binder)
                    ipm?.javaClass?.getMethod("reboot", String::class.java)?.invoke(ipm, "")
                    log("SystemUpgradeManager: IPowerManager reflection invoked")
                    rebootDispatched = true
                }
            } catch (e: Throwable) {
                log("SystemUpgradeManager: IPowerManager reflection failed: ${e.message}")
            }
        }

        // 4. Shell / su reboot
        if (!rebootDispatched) {
            try {
                val proc = Runtime.getRuntime().exec("reboot")
                proc.waitFor()
                log("SystemUpgradeManager: shell reboot exited")
                rebootDispatched = true
            } catch (e: Throwable) {
                log("SystemUpgradeManager: shell reboot failed: ${e.message}")
            }
        }
        if (!rebootDispatched) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                proc.waitFor()
                log("SystemUpgradeManager: su reboot exited")
                rebootDispatched = true
            } catch (e: Throwable) {
                log("SystemUpgradeManager: su reboot failed: ${e.message}")
            }
        }

        return if (rebootDispatched) {
            UpgradeTriggerResult.Rebooting
        } else {
            UpgradeTriggerResult.ManualRebootRequired(
                "底层 METAZONE 升级标志已成功写入 (UpdateMode=1)！\n\n" +
                "应用受系统安全权限限制，无法直接执行关机重启。\n\n" +
                "【下一步关键操作】：\n" +
                "请用牙签/卡针戳一下面板【RST 复位小孔】（或车辆熄火断电再重新通电）。\n" +
                "车机开机检测到升级标志，将直接进入 RSD 写屏刷机！"
            )
        }
    }
}
