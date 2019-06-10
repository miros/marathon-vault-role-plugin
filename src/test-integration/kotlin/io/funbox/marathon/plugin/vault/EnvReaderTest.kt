package io.funbox.marathon.plugin.vault

import io.funbox.marathon.plugin.vault.helpers.VaultTestContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.vault.VaultContainer
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class EnvReaderTest {

    companion object {
        const val VAULT_PORT = 8200
    }

    class KVaultContainer : VaultContainer<KVaultContainer>("vault:${System.getProperty("VAULT_VERSION")}")

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

    private val conf by lazy {
        PluginConf(
            vaultOptions = VaultClient.Options(
                url = vaultURL,
                timeout = 1,
                roleID = VaultTestContext.PLUGIN_ROLE_ID,
                secretID = VaultTestContext.PLUGIN_SECRET_ID
            ),
            rolePrefix = "mesos",
            defaultSecretsPath = "/secrets_v1/mesos"
        )
    }

    private val vaultContext by lazy {
        VaultTestContext(vaultURL)
    }


    @Test
    fun `returns env variables for application`() {
        vaultContext.init()

        vaultContext.writeSecret(
            "secrets_v1/mesos/${VaultTestContext.TEST_APP_NAME}/passwords",
            mapOf(
                "some-secret-key" to "some-secret-value",
                "other-secret-key" to "other-secret-value"
            )
        )

        vaultContext.writeSecret(
            "secrets_v1/mesos/${VaultTestContext.TEST_APP_NAME}/other-passwords",
            mapOf("some-secret-key" to "some-secret-value")
        )

        val result = EnvReader(conf).envsFor(VaultTestContext.TEST_APP_NAME, emptyMap())

        assertThat(result.allSecrets).containsAllEntriesOf(
            mapOf(
                "PASSWORDS_SOME_SECRET_KEY" to "some-secret-value",
                "PASSWORDS_OTHER_SECRET_KEY" to "other-secret-value",
                "OTHER_PASSWORDS_SOME_SECRET_KEY" to "some-secret-value"
            )
        )
    }

    @Test
    fun `uses top level app role if specific role for app does not exist`() {
        vaultContext.initPluginRoles()
        vaultContext.createTestAppRole("mesos-some-namespace")
        vaultContext.mountSecretsEngine()

        vaultContext.writeSecret(
            "secrets_v1/mesos/some-namespace/test-app/passwords",
            mapOf(
                "some-secret-key" to "some-secret-value"
            )
        )

        val result = EnvReader(conf).envsFor("/some-namespace/test-app", emptyMap())

        assertThat(result.allSecrets).containsEntry("PASSWORDS_SOME_SECRET_KEY", "some-secret-value")
        assertThat(result.appRole).isEqualTo("mesos-some-namespace")
    }

    @Test
    fun `returns custom secrets`() {
        vaultContext.init()

        vaultContext.writeSecret(
            "secrets_v1/mesos-custom/password",
            mapOf(
                "some-secret-key" to "some-secret-value"
            )
        )

        val result = EnvReader(conf).envsFor(
            "test-app", mapOf(
                "SECRET_KEY" to "secrets_v1/mesos-custom/password@some-secret-key"
            )
        )

        assertThat(result.allSecrets).containsEntry("SECRET_KEY", "some-secret-value")
    }

    @Test
    fun `ignores missing default secrets`() {
        vaultContext.init()
        val result = EnvReader(conf).envsFor("test-app", mapOf())
        assertThat(result.allSecrets).isEmpty()
    }

    @Test
    fun `sets missing custom secrets to blank string`() {
        vaultContext.init()

        val result = EnvReader(conf).envsFor(
            "test-app", mapOf(
                "SECRET_KEY" to "secrets_v1/mesos-custom/password@some-secret-key"
            )
        )

        assertThat(result.customSecrets).containsEntry("SECRET_KEY", "")
    }

}
