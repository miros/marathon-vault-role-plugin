package io.funbox.marathon.plugin.vault

import com.google.gson.Gson
import io.funbox.marathon.plugin.vault.helpers.VaultTestContext
import mesosphere.dcos.client.model.Secret
import mesosphere.marathon.client.Marathon
import mesosphere.marathon.client.MarathonClient
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.SecretSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.awaitility.Duration.FIVE_SECONDS
import org.awaitility.Duration.TEN_SECONDS
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.net.SocketException
import java.time.Duration
import kotlin.test.Test


@Testcontainers
class VaultEnvPluginTest {

    companion object {
        const val VAULT_PORT = 8200
        const val MARATHON_PORT = 8080
        const val MESOS_MASTER_PORT = 5050
        const val MESOS_SLAVE_PORT = 5051
        const val TEST_APP_PORT = 8000
    }

    // java recursive generics fail to compile in kotlin without this type hinting
    class KDockerComposeContainer(file: File) : DockerComposeContainer<KDockerComposeContainer>(file)

    private val dockerComposeFile = File("src/test-integration/resources/docker-compose.yml")

    @Container
    private val docker = KDockerComposeContainer(dockerComposeFile)
        .withEnv(
            mapOf(
                "MESOS_VERSION" to System.getProperty("MESOS_VERSION"),
                "MARATHON_VERSION" to System.getProperty("MARATHON_VERSION"),
                "VAULT_VERSION" to System.getProperty("VAULT_VERSION")
            )
        )
        .withLocalCompose(true)
        .withTailChildContainers(true)
        .withExposedService(
            "vault", VAULT_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )
        .withExposedService(
            "mesos-master", MESOS_MASTER_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )
        .withExposedService(
            "mesos-slave", MESOS_SLAVE_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )
        .withExposedService(
            "marathon", MARATHON_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )


    @Test
    fun `sets env variables for marathon application`() {
        val vaultContext = VaultTestContext(getServiceURL("vault", VAULT_PORT))
        vaultContext.init()

        vaultContext.writeSecret(
            "secret/mesos/${VaultTestContext.TEST_APP_NAME}/passwords",
            mapOf("some-secret-key" to "some-secret-value")
        )

        vaultContext.writeSecret(
            "secret/mesos-custom/password",
            mapOf("some-secret-key" to "custom-secret-value")
        )

        marathonClient().createApp(App().apply {
            id = VaultTestContext.TEST_APP_NAME
            cmd = "python3 /server.py $TEST_APP_PORT"
            env = mapOf("CUSTOM_SECRET" to mapOf("secret" to "secret-ref"))
            secrets =
                mapOf("secret-ref" to SecretSource().apply { source = "secret/mesos-custom/password@some-secret-key" })
        })

        val testAppURL = getServiceURL("mesos-slave", TEST_APP_PORT)
        val response = await()
            .ignoreExceptions()
            .timeout(TEN_SECONDS)
            .untilCallTo { tryURL(testAppURL) }
            .matches { it!!.isNotEmpty() }
        val envs = parseTestAppResponse(response!!)

        assertThat(envs).containsEntry("PASSWORDS_SOME_SECRET_KEY", "some-secret-value")
        assertThat(envs).containsEntry("CUSTOM_SECRET", "custom-secret-value")

    }

    @Test
    fun `works when no vault roles for app are configured`() {
        val vaultContext = VaultTestContext(getServiceURL("vault", VAULT_PORT))
        vaultContext.initPluginRoles()

        marathonClient().createApp(App().apply {
            id = VaultTestContext.TEST_APP_NAME
            cmd = "python3 /server.py $TEST_APP_PORT"
        })

        val testAppURL = getServiceURL("mesos-slave", TEST_APP_PORT)

        await()
            .ignoreExceptions()
            .timeout(TEN_SECONDS)
            .untilCallTo { tryURL(testAppURL) }
            .matches { it!!.isNotEmpty() }
    }

    private fun tryURL(url: String): String? {
        return try {
            val request = Request.Builder().get().url(url).build()
            OkHttpClient().newCall(request).execute().body()?.string()
        } catch (exc: SocketException) {
            null
        }
    }

    private fun parseTestAppResponse(response: String): Map<String, String> {
        return Gson().fromJson(response, mutableMapOf<String, String>().javaClass)
    }

    private fun marathonClient(): Marathon {
        return MarathonClient.getInstance(getServiceURL("marathon", MARATHON_PORT))
    }

    private fun getServiceURL(name: String, port: Int): String {
        val dockerClient = DockerClientFactory.instance().client()
        val container =
            dockerClient.listContainersCmd().withLabelFilter(mapOf("com.docker.compose.service" to name)).exec().first()

        val publicPort = container.ports.find { it.privatePort == port }!!.publicPort

        return "http://localhost:$publicPort"
    }


}
