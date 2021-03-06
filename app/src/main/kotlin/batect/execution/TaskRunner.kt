/*
   Copyright 2017-2018 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.execution

import batect.config.Configuration
import batect.config.Task
import batect.execution.model.events.RunningContainerExitedEvent
import batect.logging.Logger
import batect.ui.EventLogger
import batect.ui.EventLoggerProvider

data class TaskRunner(
    private val eventLoggerProvider: EventLoggerProvider,
    private val graphProvider: ContainerDependencyGraphProvider,
    private val stateMachineProvider: TaskStateMachineProvider,
    private val executionManagerProvider: ParallelExecutionManagerProvider,
    private val logger: Logger
) {
    fun run(config: Configuration, task: Task, runOptions: RunOptions): Int {
        logger.info {
            message("Preparing task.")
            data("task", task.name)
        }

        val graph = graphProvider.createGraph(config, task)
        val eventLogger = eventLoggerProvider.getEventLogger(graph, runOptions)
        eventLogger.onTaskStarting(task.name)

        val stateMachine = stateMachineProvider.createStateMachine(graph, runOptions)
        val executionManager = executionManagerProvider.createParallelExecutionManager(eventLogger, stateMachine, runOptions)

        logger.info {
            message("Preparation complete, starting task.")
            data("task", task.name)
        }

        executionManager.run()

        logger.info {
            message("Task execution completed.")
            data("task", task.name)
        }

        if (stateMachine.taskHasFailed) {
            return onTaskFailed(eventLogger, task, stateMachine)
        }

        return findTaskContainerExitCode(stateMachine, task)
    }

    private fun onTaskFailed(eventLogger: EventLogger, task: Task, stateMachine: TaskStateMachine): Int {
        eventLogger.onTaskFailed(task.name, stateMachine.manualCleanupInstructions)

        logger.warn {
            message("Task execution failed.")
            data("task", task.name)
        }

        return -1
    }

    private fun findTaskContainerExitCode(stateMachine: TaskStateMachine, task: Task): Int {
        val containerExitedEvent = stateMachine.getAllEvents()
                .filterIsInstance<RunningContainerExitedEvent>()
                .singleOrNull()

        if (containerExitedEvent == null) {
            throw IllegalStateException("The task neither failed nor succeeded.")
        }

        logger.info {
            message("Task execution completed normally.")
            data("task", task.name)
            data("exitCode", containerExitedEvent.exitCode)
        }

        return containerExitedEvent.exitCode
    }
}
