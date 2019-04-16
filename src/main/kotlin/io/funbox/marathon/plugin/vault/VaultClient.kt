package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.api.Auth
import java.time.Instant

class VaultClient private constructor(
    val options: Options,
    private val vault: Vault,
    private val validUntil: Instant
) {

    // TODO add ssl related configs
    data class Options(
        val url: String,
        val timeout: Int,
        val roleID: String,
        val secretID: String
    )

    companion object {

        const val VALIDITY_THRESHOLD_SEC = 60L

        val NOW = { Instant.now() }

        fun login(options: Options, clock: () -> Instant = NOW): VaultClient {
            val authResp = createVault(options).auth()
                .loginByAppRole(options.roleID, options.secretID)

            return VaultClient(
                options,
                loginWithToken(options, authResp.authClientToken),
                clock().plusSeconds(authResp.authLeaseDuration)
            )
        }

        private fun loginWithToken(options: Options, token: String): Vault {
            return createVault(options) { it.token(token) }
        }

        private fun createVault(options: Options, block: (VaultConfig) -> Unit = {}): Vault {
            val config = VaultConfig()
                .address(options.url)
                .openTimeout(options.timeout)
                .readTimeout(options.timeout)
                .engineVersion(1)

            block(config)

            return Vault(config.build())
        }

    }

    // TODO test this
    fun refresh(clock: () -> Instant = NOW): VaultClient {
        if (isFresh(clock)) {
            return this
        }

        return login(options)
    }

    // TODO use v2 secrets api

    fun readSecrets(path: String): Map<String, String> {
        // TODO make smart concatenation
        return vault.logical().read("secret/$path").data
    }

    fun listChildren(path: String): List<String> {
        return vault.logical().list("secret/$path")
    }

    fun loginAs(roleName: String): VaultClient {
        val roleID = getAppRoleID(roleName)
        val secretID = generateSecretID(roleName)
        return login(options.copy(roleID = roleID, secretID = secretID))
    }

    private fun isFresh(clock: () -> Instant): Boolean {
        return validUntil.minusSeconds(VALIDITY_THRESHOLD_SEC).isBefore(clock())
    }

    private fun getAppRoleID(roleName: String): String {
        return vault.logical().read("auth/approle/role/$roleName/role-id")
            .data.getValue("role_id")
    }

    private fun generateSecretID(roleName: String): String {
        return vault.logical().write("auth/approle/role/$roleName/secret-id", emptyMap())
            .data.getValue("secret_id")
    }

    fun logout(roleName: String, secretID: String) {
        vault.logical().write(
            "auth/approle/role/$roleName/secret-id/destroy",
            mapOf("secret_id" to secretID)
        )
    }

}