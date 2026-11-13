package com.skodamusic.app.system

import java.io.File

object SystemDiagnosticManager {

    data class RootInspectionResult(
        val isSuFilePresent: Boolean,
        val suFilePath: String?,
        val suPermissions: String,
        val canExecuteSu: Boolean,
        val isRootUid: Boolean,
        val rootIdOutput: String,
        val canRemountSystemRw: Boolean,
        val remountMessage: String?,
        val speechClientState: String,
        val speechAdaptState: String
    ) {
        val isRootFunctional: Boolean
            get() = isRootUid
    }

    fun inspectRoot(): RootInspectionResult {
        val suCandidates = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su")
        var foundSuFile: File? = null
        for (path in suCandidates) {
            val file = File(path)
            if (file.exists()) {
                foundSuFile = file
                break
            }
        }

        val suPerms = foundSuFile?.let {
            buildString {
                append(if (it.canRead()) "r" else "-")
                append(if (it.canWrite()) "w" else "-")
                append(if (it.canExecute()) "x" else "-")
            }
        } ?: "none"

        val execResult = testSuExecution()
        val remountResult = if (execResult.first) testRemountRw() else Pair(false, "未获取 root，跳过读写测试")

        val clientState = checkApkState("/system/app/SpeechClient.apk")
        val adaptState = checkApkState("/system/app/SpeechAdapt.apk")

        return RootInspectionResult(
            isSuFilePresent = foundSuFile != null,
            suFilePath = foundSuFile?.absolutePath,
            suPermissions = suPerms,
            canExecuteSu = execResult.first,
            isRootUid = execResult.first,
            rootIdOutput = execResult.second,
            canRemountSystemRw = remountResult.first,
            remountMessage = remountResult.second,
            speechClientState = clientState,
            speechAdaptState = adaptState
        )
    }

    private fun testSuExecution(): Pair<Boolean, String> {
        return try {
            val res = RootShellHelper.runCommand("id", 8000L)
            val output = res.stdout.ifBlank { res.stderr }
            val isRoot = res.isSuccess && (output.contains("uid=0") || output.contains("root"))
            Pair(isRoot, output.ifBlank { "执行退出码: ${res.exitCode}" })
        } catch (e: Throwable) {
            Pair(false, "执行异常: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun testRemountRw(): Pair<Boolean, String> {
        return try {
            val res = RootShellHelper.runCommand("mount -o remount,rw /system && mount", 10000L)
            val hasSystemRw = res.stdout.lines().any { line ->
                line.contains("/system") && line.contains("rw")
            }
            RootShellHelper.runCommand("mount -o remount,ro /system", 5000L)
            if (hasSystemRw) {
                Pair(true, "支持读写重挂载 (remount rw/ro 校验成功)")
            } else {
                Pair(false, "未检测到 /system rw 挂载标志 (退出码 ${res.exitCode}: ${res.stderr})")
            }
        } catch (e: Throwable) {
            Pair(false, "挂载测试异常: ${e.message}")
        }
    }

    private fun checkApkState(basePath: String): String {
        val original = File(basePath)
        val backup = File("$basePath.bak")
        return when {
            backup.exists() -> "已重命名停用 (.bak)"
            original.exists() -> "原厂启用中 (.apk)"
            else -> "未检测到文件"
        }
    }

    fun buildInspectionSummary(result: RootInspectionResult): String {
        val sb = StringBuilder()
        sb.append("【Root 核心权限】：\n")
        if (result.isRootUid) {
            sb.append("• 权限状态: ✓ 拥有最高管理权限 (uid=0)\n")
            sb.append("• su 路径: ${result.suFilePath ?: "未知"} (${result.suPermissions})\n")
            sb.append("• id 输出: ${result.rootIdOutput}\n")
        } else {
            sb.append("• 权限状态: ✗ 未获取 Root 权限\n")
            sb.append("• su 文件存在: ${if (result.isSuFilePresent) "是 (${result.suFilePath})" else "否"}\n")
            sb.append("• 执行输出: ${result.rootIdOutput}\n")
        }

        sb.append("\n【/system 分区读写】：\n")
        if (result.canRemountSystemRw) {
            sb.append("• 读写重挂载: ✓ 支持 (${result.remountMessage})\n")
        } else {
            sb.append("• 读写重挂载: ✗ 失败 (${result.remountMessage})\n")
        }

        sb.append("\n【原厂语音接管检测】：\n")
        sb.append("• 讯飞客户端: ${result.speechClientState}\n")
        sb.append("• 语音适配层: ${result.speechAdaptState}\n")
        if (result.speechClientState.contains(".bak") && result.speechAdaptState.contains(".bak")) {
            sb.append("• 评估结果: ✓ 原厂讯飞已彻底停用，方向盘语音键已成功释放给第三方！\n")
        } else {
            sb.append("• 评估结果: ⚠️ 原厂讯飞仍在运行，可能会拦截方向盘按键\n")
        }
        return sb.toString()
    }
}
