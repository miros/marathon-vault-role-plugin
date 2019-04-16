package io.funbox.marathon.plugin.vault

import mesosphere.marathon.plugin.ApplicationSpec
import mesosphere.marathon.plugin.PodSpec
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.task.RunSpecTaskProcessor
import org.apache.mesos.Protos
import org.apache.mesos.Protos.Environment.Variable
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject

class VaultEnvPlugin : RunSpecTaskProcessor, PluginConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    private lateinit var envReader: EnvReader

    override fun initialize(marathonInfo: scala.collection.immutable.Map<String, Any>?, configuration: JsObject?) {
        envReader = EnvReader(PluginConf.fromJson(Json(configuration)))
    }

    override fun taskInfo(appSpec: ApplicationSpec, builder: Protos.TaskInfo.Builder) {
        val envs = envReader.envsFor(appSpec.id().toString())
        setEnvs(envs, builder)
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
