package io.funbox.marathon.plugin.vault

import io.funbox.marathon.plugin.vault.vault_client.VaultClient

data class PluginConf(
    val vaultOptions: VaultClient.Options,
    val rolePrefix: String,
    val defaultSecretsPath: String,
    val appNameLabel: String?
) {
    companion object {

        fun fromJson(json: Json): PluginConf {
            return PluginConf(
                vaultOptions = VaultClient.Options(
                    url = json.getStr("vault_url"),
                    timeout = json.getInt("vault_timeout"),
                    roleID = json.getStr("plugin_role_id"),
                    secretID = json.getStr("plugin_secret_id")
                ),
                rolePrefix = json.getStr("role_prefix"),
                defaultSecretsPath = json.getStr("default_secrets_path"),
                appNameLabel = json.tryStr("app_name_label")
            )
        }

    }
}