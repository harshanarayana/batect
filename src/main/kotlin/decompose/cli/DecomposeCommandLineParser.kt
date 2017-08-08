package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import decompose.cli.commands.ListTasksCommandDefinition
import decompose.cli.commands.RunTaskCommandDefinition

class DecomposeCommandLineParser(kodein: Kodein) : CommandLineParser(kodein) {
    val configurationFileName: String by ValueOptionWithDefault("config-file", "The configuration file to use.", "decompose.yml", 'f')

    init {
        addCommandDefinition(RunTaskCommandDefinition())
        addCommandDefinition(ListTasksCommandDefinition())
    }

    override fun createBindings(): Kodein.Module {
        return Kodein.Module {
            bind<String>(CommonOptions.ConfigurationFileName) with instance(configurationFileName)
        }
    }
}
