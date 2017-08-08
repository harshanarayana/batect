package decompose.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import decompose.PrintStreamType
import decompose.cli.Command
import decompose.cli.CommandDefinition
import decompose.cli.CommonOptions
import decompose.config.Task
import decompose.config.io.ConfigurationLoader
import java.io.PrintStream

class ListTasksCommandDefinition : CommandDefinition("tasks", "List all tasks defined in the configuration file.") {
    override fun createCommand(kodein: Kodein): Command = ListTasksCommand(
            kodein.instance(CommonOptions.ConfigurationFileName),
            kodein.instance(),
            kodein.instance(PrintStreamType.Error))
}

data class ListTasksCommand(val configFile: String, val configLoader: ConfigurationLoader, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)

        config.tasks.map { t: Task -> t.name }
                .sorted()
                .forEach {
                    outputStream.println(it)
                }

        return 0
    }
}
