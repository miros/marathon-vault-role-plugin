package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.VaultException
import org.slf4j.LoggerFactory

class EnvReader(private val conf: PluginConf) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // TODO add support for marathon secrets extension
    // TODO concatenate paths smartly

    private var _rootVault: VaultClient? = null
    private val rootVault: VaultClient
        get() {
            _rootVault =
                _rootVault?.refresh() ?: let { VaultClient.login(conf.vaultOptions) }

            return _rootVault!!
        }

    fun envsFor(appID: String): Map<String, String> {
        try {
            val appRole = roleNameForMarathonID(appID)
            val defaultSecretsPath = defaultSecretsPath(appID)

            // TODO make block api
            val vault = rootVault.loginAs(appRole)
            val envs = envsFrom(vault, defaultSecretsPath)
            rootVault.logout(appRole, vault.options.secretID)

            logger.info(
                "appID:$appID appRole:$appRole defaultSecretsPath:$defaultSecretsPath envs:[${envs.keys.joinToString(",")}]"
            )

            return envs
            // TODO move catch to caller
        } catch (exc: VaultException) {
            logger.error("VaultEnvPlugin error injecting vault secrets for appID:$appID", exc)
            return emptyMap()
        }
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

    private fun defaultSecretsPath(marathonID: String) = conf.defaultSecretsPath + "/" + marathonID.trimStart('/')

    private fun roleNameForMarathonID(marathonID: String) =
        conf.rolePrefix + "-" + marathonID.trimStart('/').replace("/", "-")

    private fun formatEnvName(prefix: String, name: String): String {
        return listOf(prefix, name).joinToString("_").replace("-", "_").toUpperCase()
    }

}
