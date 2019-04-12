package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import java.time.Instant

class VaultClient private constructor(
    private val options: Options,
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

        fun login(options: Options): VaultClient {
            val authResp = createVault(options).auth()
                .loginByAppRole(options.roleID, options.secretID)

            return VaultClient(
                options,
                loginWithToken(options, authResp.authClientToken),
                Instant.now().plusSeconds(authResp.authLeaseDuration)
            )
        }

        private fun loginWithToken(options: Options, token: String): Vault {
            return createVault(options) {
                it.token(token)
            }
        }

        private fun createVault(options: Options, block: (VaultConfig) -> Unit = {}): Vault {
            val config = VaultConfig()
                .address(options.url)
                .openTimeout(options.timeout)
                .readTimeout(options.timeout)

            block(config)

            return Vault(config.build())
        }

    }

    fun refresh(): VaultClient {
        if (isFresh()) {
            return this
        }

        return login(options)
    }

    private fun isFresh() = validUntil.minusSeconds(VALIDITY_THRESHOLD_SEC).isBefore(Instant.now())

    fun readSecrets(path: String) : Map<String, String> {
        return vault.logical().read(path).data
    }

    fun listChildren(path: String) : List<String> {
        return vault.logical().list(path)
    }

    fun loginAs(roleName: String): VaultClient {
        val roleID = getAppRoleID(roleName)
        val secretID = generateSecretID(roleName)
        return login(options.copy(roleID = roleID, secretID = secretID))
    }

    private fun getAppRoleID(roleName: String): String {
        return vault.logical().write("/auth/approle/role/$roleName/role-id", emptyMap())
            .data.getValue("role_id")
    }

    private fun generateSecretID(roleName: String): String {
        return vault.logical().write("auth/approle/role/$roleName/secret-id", emptyMap())
            .data.getValue("secret_id")
    }

}