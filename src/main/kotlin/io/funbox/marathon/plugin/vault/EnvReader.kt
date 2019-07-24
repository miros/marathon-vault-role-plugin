package io.funbox.marathon.plugin.vault

import org.slf4j.LoggerFactory
import java.nio.file.Paths

class EnvReader(private val conf: PluginConf) {

    class NoVaultRoleError(message: String) : Exception(message)

    private val logger = LoggerFactory.getLogger(javaClass)

    data class Envs(
        val defaultSecrets: Map<String, String>,
        val customSecrets: Map<String, String>,
        val appRole: String
    ) {
        val allNames = defaultSecrets.keys + customSecrets.keys
        val allSecrets = defaultSecrets + customSecrets
    }

    private var _rootVault: VaultClient? = null
    private val rootVault: VaultClient
        @Synchronized
        get() {
            _rootVault =
                _rootVault?.refresh() ?: let { VaultClient.login(conf.vaultOptions) }

            return _rootVault!!
        }

    fun envsFor(appID: String, customSecrets: Map<String, String>): Envs {
        val appRole = roleFor(appID) ?: throw NoVaultRoleError("no role in vault for appID:$appID")
        val defaultSecretsPath = defaultSecretsPath(appID)

        logger.info("VaultEnvPlugin default secrets path:$defaultSecretsPath")

        return rootVault.loginAs(appRole) { vault ->
            val defaultEnvs = envsForDefaultSecrets(vault, defaultSecretsPath)
            val customEnvs = envsForCustomSecrets(vault, customSecrets)

            Envs(defaultEnvs, customEnvs, appRole)
        }
    }

    private fun roleFor(appID: String): String? {
        var parts = appID.trimStart('/').split('/')

        while (parts.isNotEmpty()) {
            val role = roleNameForMarathonID(parts.joinToString("/"))

            logger.info("VaultEnvPlugin trying role:$role")

            if (rootVault.roleExists(role)) {
                return role
            }

            parts = parts.dropLast(1)
        }

        return null
    }

    private fun envsForDefaultSecrets(vault: VaultClient, path: String): Map<String, String> {
        val children = vault.listChildren(path)
        logger.info("VaultEnvPlugin listing children for path:$path children:${children.joinToString(",")}")

        return children
            .filterNot(::isDirectory)
            .fold(emptyMap()) { secrets, resource ->
                val subpath = "$path/$resource"

                logger.info("VaultEnvPlugin reading secrets from path:$subpath")

                val subSecrets = vault
                    .readSecrets(subpath)
                    .mapKeys { formatEnvName(resource, it.key) }

                secrets + subSecrets
            }
    }

    private fun envsForCustomSecrets(vault: VaultClient, customSecrets: Map<String, String>): Map<String, String> {
        return customSecrets.mapValues { (_, sel) -> tryReadSecret(vault, sel) }
    }

    private fun tryReadSecret(vault: VaultClient, selector: String): String {
        val (path, name) = selector.split("@", limit = 2)

        val secrets = try {
            vault.readSecrets(path)
        } catch (exc: VaultApi.Error) {
            logger.warn("error reading secret selector:$selector", exc)
            emptyMap<String, String>()
        }

        return secrets[name] ?: ""
    }

    private fun isDirectory(path: String) = path.endsWith("/")

    private fun defaultSecretsPath(marathonID: String) =
        Paths.get(conf.defaultSecretsPath, marathonID.trimStart('/')).toString()

    private fun roleNameForMarathonID(marathonID: String): String {
        val prefix = if (conf.rolePrefix.isBlank()) {
            ""
        } else {
            conf.rolePrefix + "-"
        }

        return prefix + marathonID.trimStart('/').replace("/", "-")
    }

    private fun formatEnvName(prefix: String, name: String): String {
        return "${prefix}_$name".replace("-", "_").toUpperCase()
    }

}
