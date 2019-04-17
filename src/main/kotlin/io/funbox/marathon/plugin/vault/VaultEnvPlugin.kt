package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.VaultException
import mesosphere.marathon.plugin.ApplicationSpec
import mesosphere.marathon.plugin.EnvVarSecretRef
import mesosphere.marathon.plugin.PodSpec
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.task.RunSpecTaskProcessor
import org.apache.mesos.Protos
import org.apache.mesos.Protos.Environment.Variable
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject
import scala.collection.JavaConverters

class VaultEnvPlugin : RunSpecTaskProcessor, PluginConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    private lateinit var envReader: EnvReader

    override fun initialize(marathonInfo: scala.collection.immutable.Map<String, Any>?, configuration: JsObject?) {
        envReader = EnvReader(PluginConf.fromJson(Json(configuration)))
    }

    override fun taskInfo(appSpec: ApplicationSpec, builder: Protos.TaskInfo.Builder) {
        try {
            val result = envReader.envsFor(appSpec.id().toString(), customSecrets(appSpec))

            logger.info(
                "injecting vault secrets for appID:{} appRole:{} secrets:{}",
                result.appRole,
                appSpec.id(),
                result.allNames.joinToString(",")
            )

            setEnvs(result.allSecrets, builder)

        } catch (exc: VaultException) {
            logger.warn("error injecting vault secrets for appID:${appSpec.id()}", exc)
        }
    }

    private fun customSecrets(appSpec: ApplicationSpec): Map<String, String> {
        val definitions = JavaConverters.asJavaCollection(appSpec.env()).toList()

        return definitions.mapNotNull { tuple ->
            val envName = tuple._1
            val value = tuple._2

            if (value is EnvVarSecretRef) {
                val secretRef = appSpec.secrets()[value.secret()]

                secretRef.takeIf { it.isDefined }?.let {
                    envName to it.get().source()
                }
            } else {
                null
            }
        }.toMap()
    }

    private fun setEnvs(envs: Map<String, String>, builder: Protos.TaskInfo.Builder) {
        val envBuilder = builder.command.environment.toBuilder()

        envs.forEach { (key, value) ->
            envBuilder.addVariables(
                Variable.newBuilder().setName(key).setValue(value)
            )
        }

        val commandBuilder = builder.command.toBuilder()
        commandBuilder.setEnvironment(envBuilder)

        builder.setCommand(commandBuilder)
    }

    override fun taskGroup(
        podSpec: PodSpec?,
        executor: Protos.ExecutorInfo.Builder?,
        taskGroup: Protos.TaskGroupInfo.Builder?
    ) {
        // not implemented
    }

}
