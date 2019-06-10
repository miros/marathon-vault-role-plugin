package io.funbox.marathon.plugin.vault.helpers

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.api.Logical
import io.funbox.marathon.plugin.vault.VaultApi
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonObject

class VaultTestContext(vaultURL: String) {

    companion object {
        const val ROOT_TOKEN = "test-root-token"

        private const val PLUGIN_POLICY = "plugin-policy"
        private const val PLUGIN_ROLE = "test-role"
        const val PLUGIN_ROLE_ID = "test-role-id"
        const val PLUGIN_SECRET_ID = "test-secret-id"

        const val TEST_APP_NAME = "test-app"
        private const val TEST_APP_ROLE = "mesos-$TEST_APP_NAME"
    }

    private val vaultClient = createVaultClient(vaultURL)
    private val vaultAPI = VaultApi(vaultURL, ROOT_TOKEN)

    private fun createVaultClient(url: String): Logical {
        val config = VaultConfig()
            .address(url)
            .token(ROOT_TOKEN)
            .engineVersion(1)

        return Vault(config.build()).logical()
    }

    fun init() {
        initPluginRoles()
        createTestAppRole()

        mountSecretsEngine()
    }

    fun mountSecretsEngine() {
        vaultAPI.mountSecretsEngine(
            "secrets_v1", JsonObject(
                mapOf(
                    "type" to JsonLiteral("kv"),
                    "version" to JsonLiteral(1)
                )
            )
        )
    }

    fun initPluginRoles() {
        vaultClient.write("sys/auth/approle", mapOf("type" to "approle"))
        createPluginRole()
    }

    private fun createPluginRole() {

        createPluginPolicy()

        vaultClient.write(
            "auth/approle/role/$PLUGIN_ROLE",
            mapOf("policies" to "plugin-policy")
        )

        vaultClient.write(
            "auth/approle/role/$PLUGIN_ROLE/role-id",
            mapOf("role_id" to PLUGIN_ROLE_ID)
        )

        vaultClient.write(
            "auth/approle/role/$PLUGIN_ROLE/custom-secret-id",
            mapOf("secret_id" to PLUGIN_SECRET_ID)
        )
    }

    private fun createPluginPolicy() {
        vaultClient.write(
            "sys/policy/$PLUGIN_POLICY", mapOf(
                "policy" to javaClass.getResource("/test-policy.hcl").readText()
            )
        )
    }

    fun createTestAppRole(roleName: String = TEST_APP_ROLE) {
        vaultClient.write(
            "auth/approle/role/$roleName",
            mapOf("policies" to PLUGIN_POLICY)
        )
    }

    fun writeSecret(path: String, secrets: Map<String, String>) {
        vaultClient.write(path, secrets)
    }

}