package io.funbox.marathon.plugin.vault

data class PluginConf(
    val vaultOptions: VaultClient.Options,
    val defaultSecretsPath: String
) {
    companion object {

        fun fromJson(json: Json): PluginConf {
            return PluginConf(
                vaultOptions = VaultClient.Options(
                    url = json.getStr("vault_url"),
                    timeout = json.getInt("vault_timeout"),
                    roleID = json.getStr("role_id"),
                    secretID = json.getStr("secret_id")
                ),
                defaultSecretsPath = json.getStr("default_secrets_path")
            )
        }

    }
}