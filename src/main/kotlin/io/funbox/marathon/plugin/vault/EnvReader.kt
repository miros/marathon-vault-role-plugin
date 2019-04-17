package io.funbox.marathon.plugin.vault

import org.slf4j.LoggerFactory
import java.nio.file.Paths

class EnvReader(private val conf: PluginConf) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // TODO add support for marathon secrets extension

    data class Results(val envs: Map<String, String>, val appRole: String) {
        val envNames = envs.keys
    }

    private var _rootVault: VaultClient? = null
    private val rootVault: VaultClient
        get() {
            _rootVault =
                _rootVault?.refresh() ?: let { VaultClient.login(conf.vaultOptions) }

            return _rootVault!!
        }

    fun envsFor(appID: String): Results {
        val appRole = roleFor(appID) ?: throw RuntimeException("no role in vault for appID:$appID")
        val defaultSecretsPath = defaultSecretsPath(appID)

        val envs = rootVault.loginAs(appRole) { vault ->
            envsFrom(vault, defaultSecretsPath)
        }

        return Results(envs, appRole)
    }

    private fun roleFor(appID: String): String? {
        var parts = appID.trimStart('/').split('/')

        while (parts.isNotEmpty()) {
            val role = roleNameForMarathonID(parts.joinToString("/"))

            if (rootVault.roleExists(role)) {
                return role
            }

            parts = parts.dropLast(1)
        }

        return null
    }

    private fun envsFrom(vault: VaultClient, path: String): Map<String, String> {
        return vault
            .listChildren(path)
            .filterNot(::isDirectory)
            .fold(emptyMap()) { secrets, resource ->
                val subSecrets = vault
                    .readSecrets("$path/$resource")
                    .mapKeys { formatEnvName(resource, it.key) }

                secrets + subSecrets
            }
    }

    private fun isDirectory(path: String) = path.endsWith("/")

    private fun defaultSecretsPath(marathonID: String) =
        Paths.get(conf.defaultSecretsPath, marathonID.trimStart('/')).toString()

    private fun roleNameForMarathonID(marathonID: String) =
        conf.rolePrefix + "-" + marathonID.trimStart('/').replace("/", "-")

    private fun formatEnvName(prefix: String, name: String): String {
        return "${prefix}_$name".replace("-", "_").toUpperCase()
    }

}
