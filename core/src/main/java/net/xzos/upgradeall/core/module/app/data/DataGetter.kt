package net.xzos.upgradeall.core.module.app.data

import net.xzos.upgradeall.core.manager.AppManager
import net.xzos.upgradeall.core.module.Hub
import net.xzos.upgradeall.core.module.app.App
import net.xzos.upgradeall.core.module.app.version.AssetWrapper
import net.xzos.upgradeall.core.module.app.version.Version
import net.xzos.upgradeall.core.module.app.version.VersionWrapper
import net.xzos.upgradeall.core.utils.coroutines.ValueMutexMap
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

internal object DataGetter {

    private val lockMap = ValueMutexMap()
    private val httpClient = HttpClient()

    // 新增方法：从 GitHub 获取最新版本
    suspend fun getLatestGitHubVersion(repoOwner: String, repoName: String): String? {
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            if (response.status == HttpStatusCode.OK) {
                val json = response.body<Map<String, Any>>()
                return json["tag_name"] as? String
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getLatestVersion(app: App, hub: Hub): Version? {
        var appList: Collection<App> = emptySet()
        if (!app.needCompleteVersion) {
            appList = lockMap.runWithLock(hub) {
                getLatestUpdate(hub, setOf(app))
            }
        }
        if (!appList.contains(app)) getVersionList(app, hub)
        return app.versionMap.getVersionList().firstOrNull()
    }

    fun getLatestVersion(app: App): Version? {
        app.hubEnableList.forEach { hub ->
            var appList: Collection<App> = emptySet()
            if (!app.needCompleteVersion) {
                appList = lockMap.runWithLock(hub) {
                    getLatestUpdate(hub, setOf(app))
                }
            }
            if (!appList.contains(app)) getVersionList(app, hub)
        }
        return app.versionMap.getVersionList().firstOrNull()
    }

    fun getLatestUpdate(
        hub: Hub, appList: Collection<App> = AppManager.getAppList(hub)
    ): Set<App> {
        val appLatestReleaseMap = hub.getAppLatestRelease(*appList.toTypedArray())
            ?: return emptySet()
        appLatestReleaseMap.forEach {
            val (app, releaseGson) = it
            if (releaseGson != null)
                app.versionMap.addSingleRelease(
                    VersionWrapper(
                        hub, releaseGson,
                        releaseGson.assetGsonList.mapIndexed { assetIndex, assetGson ->
                            AssetWrapper(hub, listOf(0, assetIndex), assetGson)
                        })
                )
        }
        return appLatestReleaseMap.keys
    }

    fun getVersionList(app: App): List<Version> {
        app.hubEnableList.forEach {
            getVersionList(app, it)
        }
        return app.versionMap.getVersionList()
    }

    private fun getVersionList(app: App, hub: Hub): Boolean {
        return lockMap.runWithLock(Pair(app, hub)) {
            getVersionList0(app, hub)
        }
    }

    private fun getVersionList0(app: App, hub: Hub): Boolean {
        return hub.getAppReleaseList(app)?.mapIndexed { index, releaseGson ->
            VersionWrapper(
                hub, releaseGson,
                releaseGson.assetGsonList.mapIndexed { assetIndex, assetGson ->
                    AssetWrapper(hub, listOf(index, assetIndex), assetGson)
                })
        }.also {
            if (it != null)
                app.versionMap.addReleaseList(it)
            else app.versionMap.setError(hub)
        } != null
    }
}