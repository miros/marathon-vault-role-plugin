package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import java.time.Instant

class VaultClient private constructor(
    private val options: Options,
    private val vault: Vault,
    token: String,
    private val validUntil: Instant
) {

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

            val leaseDuration = if (authResp.authLeaseDuration > 0) {
                authResp.authLeaseDuration
            } else {
                Long.MAX_VALUE
            }

            return VaultClient(
                options,
                loginWithToken(options, authResp.authClientToken),
                authResp.authClientToken,
                clock().plusSeconds(leaseDuration)
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

    private val vaultAPI = VaultApi(options.url, token, options.timeout.toLong())

    fun refresh(clock: () -> Instant = NOW): VaultClient {
        if (isFresh(clock)) {
            return this
        }

        return login(options)
    }

    fun isFresh(clock: () -> Instant = NOW): Boolean {
        return validUntil.minusSeconds(VALIDITY_THRESHOLD_SEC).isAfter(clock())
    }

    fun readSecrets(path: String): Map<String, String> {
        return vault.logical().read(preparePath(path)).data
    }

    fun listChildren(path: String): List<String> {
        return vaultAPI.listChildren(path)
    }

    fun roleExists(roleName: String): Boolean {
        return try {
            vault.logical().read("auth/approle/role/$roleName")
            true
        } catch (exc: VaultException) {
            if (exc.httpStatusCode != 404) {
                throw(exc)
            }
            false
        }
    }

    private fun preparePath(path: String) = path.trim('/')

    fun <T> loginAs(roleName: String, block: (VaultClient) -> T): T {
        val roleID = getAppRoleID(roleName)
        val secretID = generateSecretID(roleName)
        val newClient = login(options.copy(roleID = roleID, secretID = secretID))

        return block(newClient).also { newClient.logout(roleName, secretID) }
    }

    private fun getAppRoleID(roleName: String): String {
        return vault.logical().read("auth/approle/role/$roleName/role-id")
            .data.getValue("role_id")
    }

    private fun generateSecretID(roleName: String): String {
        return vault.logical().write("auth/approle/role/$roleName/secret-id", emptyMap())
            .data.getValue("secret_id")
    }

    private fun logout(roleName: String, secretID: String) {
        vault.logical().write(
            "auth/approle/role/$roleName/secret-id/destroy",
            mapOf("secret_id" to secretID)
        )
    }


}