package io.funbox.marathon.plugin.vault

import io.funbox.marathon.plugin.vault.helpers.VaultTestContext
import org.assertj.core.api.Assertions.assertThat
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.vault.VaultContainer
import java.time.Duration
import java.time.Instant
import kotlin.test.Test


@Testcontainers
class VaultClientTest {

    companion object {
        const val VAULT_PORT = 8200
    }

    class KVaultContainer : VaultContainer<KVaultContainer>("vault:0.9.6")

    @Container
    private val vaultContainer = KVaultContainer()
        .withVaultToken(VaultTestContext.ROOT_TOKEN)
        .withVaultPort(VAULT_PORT)
        .waitingFor(
            Wait.forHttp("/v1/sys/active")
                .forStatusCode(400)
                .withStartupTimeout(Duration.ofSeconds(5))
        )

    private val vaultURL by lazy {
        "http://${vaultContainer.containerIpAddress}:${vaultContainer.getMappedPort(VAULT_PORT)}"
    }

    private val vaultContext by lazy {
        VaultTestContext(vaultURL)
    }

    @Test
    fun `refreshes its secret id when stale`() {
        vaultContext.initPluginRoles()

        val vaultClient = VaultClient.login(
            VaultClient.Options(
                url = vaultURL,
                timeout = 1,
                roleID = VaultTestContext.PLUGIN_ROLE_ID,
                secretID = VaultTestContext.PLUGIN_SECRET_ID
            )
        )

        val future = { Instant.now().plus(Duration.ofDays(365)) }

        assertThat(vaultClient.isFresh()).isTrue()
        assertThat(vaultClient.isFresh(future)).isFalse()

        val freshClient = vaultClient.refresh(future)
        assertThat(freshClient).isNotEqualTo(vaultClient)
        assertThat(freshClient.isFresh()).isTrue()
    }

}
