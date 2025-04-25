package net.xzos.upgradeall.server.update

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.xzos.upgradeall.core.manager.AppManager

class UpdateWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    private val updateNotification = UpdateNotification()

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val notificationId = UpdateNotification.UPDATE_SERVER_RUNNING_NOTIFICATION_ID
            val notification = updateNotification.startUpdateNotification(notificationId)
            setForeground(createForegroundInfo(notificationId, notification))
            doUpdateWork(updateNotification)
            finishNotify(updateNotification)
            Result.success()
        }
    }

    companion object {
        private fun createForegroundInfo(
            notificationId: Int, notification: Notification
        ): ForegroundInfo {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(notificationId, notification)
            }
        }

        suspend fun doUpdateWork(updateNotification: UpdateNotification) {
            // 假设这里添加版本类型判断逻辑
            val versionType = getVersionType() // 自定义函数，用于获取版本类型
            when (versionType) {
                "pre" -> updateForPreVersion(updateNotification)
                "beta" -> updateForBetaVersion(updateNotification)
                else -> AppManager.renewApp(
                    updateNotification.renewStatusFun,
                    updateNotification.recheckStatusFun
                )
            }
        }

        private suspend fun updateForPreVersion(updateNotification: UpdateNotification) {
            // 处理 pre 版本更新的逻辑
            AppManager.renewApp(
                updateNotification.renewStatusFun,
                updateNotification.recheckStatusFun
            )
            // 可以添加额外的 pre 版本更新逻辑
        }

        private suspend fun updateForBetaVersion(updateNotification: UpdateNotification) {
            // 处理 beta 版本更新的逻辑
            AppManager.renewApp(
                updateNotification.renewStatusFun,
                updateNotification.recheckStatusFun
            )
            // 可以添加额外的 beta 版本更新逻辑
        }

        private fun getVersionType(): String {
            // 实现获取版本类型的逻辑，这里只是示例
            // 获取最新应用信息
            val latestApp = AppManager.getAppList().firstOrNull()
            if (latestApp != null) {
                val latestVersion = DataGetter.getLatestVersion(latestApp)
                if (latestVersion != null) {
                    // 这里可以根据最新版本信息分析版本类型
                    // 示例：简单根据版本号是否包含特定字符串判断版本类型
                    val versionType = if (latestVersion.versionNumber.contains("beta")) {
                        "Beta 版本"
                    } else {
                        "正式版本"
                    }
                    // 可以在这里处理版本类型，例如记录日志或更新通知
                    println("最新版本类型: $versionType")
                }
            }
            return "stable" // 根据实际情况返回 "pre", "beta" 或其他值
        }

        fun finishNotify(updateNotification: UpdateNotification) {
            updateNotification.updateDone()
            updateNotification.cancelNotification(
                UpdateNotification.UPDATE_SERVER_RUNNING_NOTIFICATION_ID
            )
        }
    }
}