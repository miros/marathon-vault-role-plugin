package io.funbox.marathon.plugin.vault

data class PluginConf(
    val vaultOptions: VaultClient.Options,
    val rolePrefix: String,
    val defaultSecretsPath: String
) {
    companion object {

        fun fromJson(json: Json): PluginConf {
            return PluginConf(
                vaultOptions = VaultClient.Options(
                    url = json.getStr("vault_url"),
                    timeout = json.getInt("vault_timeout"),
                    roleID = json.getStr("plugin_role_id"),
                    secretID = json.getStr("plugin_secret_id"),
                    ssl = json["ssl"]?.let {
                        VaultClient.SSLOptions(
                            verify = it.tryStr("verify") == "true",
                            trustStoreFile = it.tryStr("trust_store_file"),
                            keyStoreFile = it.tryStr("key_store_file"),
                            keyStorePassword = it.tryStr("key_store_password"),
                            pemFile = it.tryStr("pem_file"),
                            pemUTF8 = it.tryStr("pem_utf8"),
                            clientPemFile = it.tryStr("client_pem_file"),
                            clientKeyPemFile = it.tryStr("client_key_file")
                        )
                    }
                ),
                rolePrefix = json.getStr("role_prefix"),
                defaultSecretsPath = json.getStr("default_secrets_path")
            )
        }

    }
}