package decompose.model.steps

import decompose.docker.ContainerCreationFailedException
import decompose.docker.ContainerDoesNotExistException
import decompose.docker.ContainerHealthCheckException
import decompose.docker.ContainerRemovalFailedException
import decompose.docker.ContainerStartFailedException
import decompose.docker.ContainerStopFailedException
import decompose.docker.DockerClient
import decompose.docker.HealthStatus
import decompose.docker.ImageBuildFailedException
import decompose.docker.NetworkCreationFailedException
import decompose.docker.NetworkDeletionFailedException
import decompose.model.events.ContainerBecameHealthyEvent
import decompose.model.events.ContainerCreatedEvent
import decompose.model.events.ContainerCreationFailedEvent
import decompose.model.events.ContainerDidNotBecomeHealthyEvent
import decompose.model.events.ContainerRemovalFailedEvent
import decompose.model.events.ContainerRemovedEvent
import decompose.model.events.ContainerStartFailedEvent
import decompose.model.events.ContainerStartedEvent
import decompose.model.events.ContainerStopFailedEvent
import decompose.model.events.ContainerStoppedEvent
import decompose.model.events.ImageBuildFailedEvent
import decompose.model.events.ImageBuiltEvent
import decompose.model.events.RunningContainerExitedEvent
import decompose.model.events.TaskEventSink
import decompose.model.events.TaskNetworkCreatedEvent
import decompose.model.events.TaskNetworkCreationFailedEvent
import decompose.model.events.TaskNetworkDeletedEvent
import decompose.model.events.TaskNetworkDeletionFailedEvent
import decompose.model.events.TaskStartedEvent

class TaskStepRunner(private val dockerClient: DockerClient) {
    fun run(step: TaskStep, eventSink: TaskEventSink) {
        when (step) {
            is BeginTaskStep -> eventSink.postEvent(TaskStartedEvent)
            is BuildImageStep -> handleBuildImageStep(step, eventSink)
            is CreateTaskNetworkStep -> handleCreateTaskNetworkStep(eventSink)
            is CreateContainerStep -> handleCreateContainerStep(step, eventSink)
            is RunContainerStep -> handleRunContainerStep(step, eventSink)
            is StartContainerStep -> handleStartContainerStep(step, eventSink)
            is WaitForContainerToBecomeHealthyStep -> handleWaitForContainerToBecomeHealthyStep(step, eventSink)
            is StopContainerStep -> handleStopContainerStep(step, eventSink)
            is CleanUpContainerStep -> handleCleanUpContainerStep(step, eventSink)
            is RemoveContainerStep -> handleRemoveContainerStep(step, eventSink)
            is DeleteTaskNetworkStep -> handleDeleteTaskNetworkStep(step, eventSink)
            is DisplayTaskFailureStep -> ignore()
            is FinishTaskStep -> ignore()
        }
    }

    private fun handleBuildImageStep(step: BuildImageStep, eventSink: TaskEventSink) {
        try {
            val image = dockerClient.build(step.projectName, step.container)
            eventSink.postEvent(ImageBuiltEvent(step.container, image))
        } catch (e: ImageBuildFailedException) {
            eventSink.postEvent(ImageBuildFailedEvent(step.container, e.message ?: ""))
        }
    }

    private fun handleCreateTaskNetworkStep(eventSink: TaskEventSink) {
        try {
            val network = dockerClient.createNewBridgeNetwork()
            eventSink.postEvent(TaskNetworkCreatedEvent(network))
        } catch (e: NetworkCreationFailedException) {
            eventSink.postEvent(TaskNetworkCreationFailedEvent(e.outputFromDocker))
        }
    }

    private fun handleCreateContainerStep(step: CreateContainerStep, eventSink: TaskEventSink) {
        try {
            val dockerContainer = dockerClient.create(step.container, step.command, step.image, step.network)
            eventSink.postEvent(ContainerCreatedEvent(step.container, dockerContainer))
        } catch (e: ContainerCreationFailedException) {
            eventSink.postEvent(ContainerCreationFailedEvent(step.container, e.message ?: ""))
        }
    }

    private fun handleRunContainerStep(step: RunContainerStep, eventSink: TaskEventSink) {
        val result = dockerClient.run(step.dockerContainer)
        eventSink.postEvent(RunningContainerExitedEvent(step.container, result.exitCode))
    }

    private fun handleStartContainerStep(step: StartContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.start(step.dockerContainer)
            eventSink.postEvent(ContainerStartedEvent(step.container))
        } catch (e: ContainerStartFailedException) {
            eventSink.postEvent(ContainerStartFailedEvent(step.container, e.outputFromDocker))
        }
    }

    private fun handleWaitForContainerToBecomeHealthyStep(step: WaitForContainerToBecomeHealthyStep, eventSink: TaskEventSink) {
        try {
            val result = dockerClient.waitForHealthStatus(step.dockerContainer)

            val event = when (result) {
                HealthStatus.NoHealthCheck -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameHealthy -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameUnhealthy -> ContainerDidNotBecomeHealthyEvent(step.container, "The configured health check did not report the container as healthy within the timeout period.")
                HealthStatus.Exited -> ContainerDidNotBecomeHealthyEvent(step.container, "The container exited before becoming healthy.")
            }

            eventSink.postEvent(event)
        } catch (e: ContainerHealthCheckException) {
            eventSink.postEvent(ContainerDidNotBecomeHealthyEvent(step.container, "Waiting for the container's health status failed: ${e.message}"))
        }
    }

    private fun handleStopContainerStep(step: StopContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.stop(step.dockerContainer)
            eventSink.postEvent(ContainerStoppedEvent(step.container))
        } catch (e: ContainerStopFailedException) {
            eventSink.postEvent(ContainerStopFailedEvent(step.container, e.outputFromDocker))
        }
    }

    private fun handleCleanUpContainerStep(step: CleanUpContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.forciblyRemove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        } catch (_: ContainerDoesNotExistException) {
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        }
    }

    private fun handleRemoveContainerStep(step: RemoveContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.remove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        } catch (_: ContainerDoesNotExistException) {
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        }
    }

    private fun handleDeleteTaskNetworkStep(step: DeleteTaskNetworkStep, eventSink: TaskEventSink) {
        try {
            dockerClient.deleteNetwork(step.network)
            eventSink.postEvent(TaskNetworkDeletedEvent)
        } catch (e: NetworkDeletionFailedException) {
            eventSink.postEvent(TaskNetworkDeletionFailedEvent(e.outputFromDocker))
        }
    }

    private fun ignore() {
        // Do nothing.
    }
}