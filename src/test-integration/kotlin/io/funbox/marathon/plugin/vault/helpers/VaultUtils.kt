package io.funbox.marathon.plugin.vault.helpers

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.api.Logical

object VaultUtils {

    const val ROOT_TOKEN = "test-root-token"

    private const val PLUGIN_POLICY = "plugin-policy"
    private const val PLUGIN_ROLE = "test-role"
    const val PLUGIN_ROLE_ID = "test-role-id"
    const val PLUGIN_SECRET_ID = "test-secret-id"

    const val TEST_APP_NAME = "test-app"
    private const val TEST_APP_ROLE = "mesos-$TEST_APP_NAME"

    fun vaultClient(url: String): Logical {
        val config = VaultConfig()
            .address(url)
            .token(ROOT_TOKEN)
            .engineVersion(1)

        return Vault(config.build()).logical()
    }

    fun createTestRoles(vault: Logical) {
        vault.write("sys/auth/approle", mapOf("type" to "approle"))

        createPluginRole(vault)
        createTestAppRole(vault)
    }

    private fun createPluginRole(vault: Logical) {

        createPluginPolicy(vault)

        vault.write(
            "auth/approle/role/$PLUGIN_ROLE",
            mapOf("policies" to "plugin-policy")
        )

        vault.write(
            "auth/approle/role/$PLUGIN_ROLE/role-id",
            mapOf("role_id" to PLUGIN_ROLE_ID)
        )

        vault.write(
            "auth/approle/role/$PLUGIN_ROLE/custom-secret-id",
            mapOf("secret_id" to PLUGIN_SECRET_ID)
        )
    }

    private fun createPluginPolicy(vault: Logical) {
        vault.write(
            "sys/policy/$PLUGIN_POLICY", mapOf(
                "policy" to javaClass.getResource("/test-policy.hcl").readText()
            )
        )
    }

    private fun createTestAppRole(vault: Logical) {
        vault.write(
            "auth/approle/role/$TEST_APP_ROLE",
            mapOf("policies" to PLUGIN_POLICY)
        )
    }

}