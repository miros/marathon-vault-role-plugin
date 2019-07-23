package io.funbox.marathon.plugin.vault.helpers

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

    private val vaultAPI = VaultApi(vaultURL, ROOT_TOKEN)

    fun init() {
        initPluginRoles()
        createTestAppRole()

        mountSecretsEngine()
    }

    fun mountSecretsEngine() {
        vaultAPI.mountSecretsEngine("secrets_v1", "kv", 1)
    }

    fun initPluginRoles() {
        callVault("sys/auth/approle", mapOf("type" to "approle"))
        createPluginRole()
    }

    private fun createPluginRole() {

        createPluginPolicy()

        callVault(
            "auth/approle/role/$PLUGIN_ROLE",
            mapOf("policies" to "plugin-policy")
        )

        callVault(
            "auth/approle/role/$PLUGIN_ROLE/role-id",
            mapOf("role_id" to PLUGIN_ROLE_ID)
        )

        callVault(
            "auth/approle/role/$PLUGIN_ROLE/custom-secret-id",
            mapOf("secret_id" to PLUGIN_SECRET_ID)
        )
    }

    private fun createPluginPolicy() {
        callVault(
            "sys/policy/$PLUGIN_POLICY", mapOf(
                "policy" to javaClass.getResource("/test-policy.hcl").readText()
            )
        )
    }

    fun createTestAppRole(roleName: String = TEST_APP_ROLE) {
        callVault(
            "auth/approle/role/$roleName",
            mapOf("policies" to PLUGIN_POLICY)
        )
    }

    fun writeSecret(path: String, secrets: Map<String, String>) {
        callVault(path, secrets)
    }

    private fun callVault(path: String, params: Map<String, String>) {
        vaultAPI.callVault<Nothing>(
            "POST", path,
            requestParams = JsonObject(
                params.mapValues { JsonLiteral(it.value) }
            )
        )
    }

}