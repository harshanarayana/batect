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

package batect.config.io

import batect.config.BuildImage
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PortMapping
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.VolumeMount
import batect.os.Command
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.data_driven.data
import org.jetbrains.spek.data_driven.on

object ConfigurationFileSpec : Spek({
    describe("a configuration file") {
        describe("converting to a configuration model object") {
            on("converting an empty configuration file") {
                val configFile = ConfigurationFile("the_project_name")
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assertThat(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with no containers") {
                    assertThat(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a task") {
                val runConfiguration = TaskRunConfigurationFromFile("some_container", "some_command", mapOf("SOME_VAR" to "some value"), setOf(PortMapping(123, 456)))
                val task = TaskFromFile(runConfiguration, "Some description", setOf("dependency-1"), setOf("other-task"))
                val taskName = "the_task_name"
                val configFile = ConfigurationFile("the_project_name", mapOf(taskName to task))
                val pathResolver = mock<PathResolver>()
                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with the task") {
                    assertThat(resultingConfig.tasks, equalTo(TaskMap(
                        Task(
                            taskName,
                            TaskRunConfiguration(runConfiguration.container, Command.parse(runConfiguration.command), runConfiguration.additionalEnvironmentVariables, runConfiguration.additionalPortMappings),
                            "Some description",
                            task.dependsOnContainers,
                            task.prerequisiteTasks
                        )
                    )))
                }

                it("returns a configuration object with no containers") {
                    assertThat(resultingConfig.containers, isEmpty)
                }
            }

            on("converting a configuration file with a container") {
                val originalBuildDirectory = "build_dir"
                val resolvedBuildDirectory = "/the_resolved_build_dir"
                val originalVolumeMountPath = "local_volume_dir"
                val resolvedVolumeMountPath = "/the_resolved_local_volume_dir"
                val volumeMountTargetPath = "/remote"

                val container = ContainerFromFile(
                    buildDirectory = originalBuildDirectory,
                    command = "the-command",
                    environment = mapOf("ENV_VAR" to "/here"),
                    workingDirectory = "working_dir",
                    volumeMounts = setOf(VolumeMount(originalVolumeMountPath, volumeMountTargetPath, "some-options")),
                    portMappings = setOf(PortMapping(1234, 5678)),
                    dependencies = setOf("some-dependency"))

                val containerName = "the_container_name"
                val configFile = ConfigurationFile("the_project_name", containers = mapOf(containerName to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn PathResolutionResult.Resolved(resolvedBuildDirectory, PathType.Directory)
                    on { resolve(originalVolumeMountPath) } doReturn PathResolutionResult.Resolved(resolvedVolumeMountPath, PathType.Directory)
                }

                val resultingConfig = configFile.toConfiguration(pathResolver)

                it("returns a configuration object with the project name") {
                    assertThat(resultingConfig.projectName, equalTo("the_project_name"))
                }

                it("returns a configuration object with no tasks") {
                    assertThat(resultingConfig.tasks, isEmpty)
                }

                it("returns a configuration object with the container") {
                    assertThat(resultingConfig.containers, equalTo(ContainerMap(
                        Container(
                            containerName,
                            BuildImage(resolvedBuildDirectory),
                            Command.parse(container.command),
                            container.environment,
                            container.workingDirectory,
                            setOf(VolumeMount(resolvedVolumeMountPath, volumeMountTargetPath, "some-options")),
                            container.portMappings,
                            container.dependencies)
                    )))
                }
            }

            on("converting a configuration file with a container that has a build directory that %s",
                data("does not exist", PathResolutionResult.Resolved("/some_resolved_path", PathType.DoesNotExist) as PathResolutionResult, "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' does not exist."),
                data("is a file", PathResolutionResult.Resolved("/some_resolved_path", PathType.File) as PathResolutionResult, "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' is not a directory."),
                data("is neither a file or directory", PathResolutionResult.Resolved("/some_resolved_path", PathType.Other) as PathResolutionResult, "Build directory 'build_dir' (resolved to '/some_resolved_path') for container 'the_container_name' is not a directory."),
                data("is an invalid path", PathResolutionResult.InvalidPath as PathResolutionResult, "Build directory 'build_dir' for container 'the_container_name' is not a valid path.")
            ) { _, resolution, expectedMessage ->
                val originalBuildDirectory = "build_dir"
                val container = ContainerFromFile(originalBuildDirectory)
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalBuildDirectory) } doReturn resolution
                }

                it("fails with an appropriate error message") {
                    assertThat({ configFile.toConfiguration(pathResolver) }, throws(withMessage(expectedMessage)))
                }
            }

            on("converting a configuration file with a container that has a volume mount with a local path that can be resolved") {
                val originalVolumeMountPath = "local_volume_path"
                val container = ContainerFromFile(imageName = "some-image", volumeMounts = setOf(VolumeMount(originalVolumeMountPath, "/container_path", "some-options")))
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalVolumeMountPath) } doReturn PathResolutionResult.Resolved("/some_resolved_path", PathType.File)
                }

                val resultingConfig = configFile.toConfiguration(pathResolver)
                val resultingContainer = resultingConfig.containers.getValue("the_container_name")

                it("returns a configuration object with the volume mount path resolved") {
                    assertThat(resultingContainer.volumeMounts, equalTo(setOf(VolumeMount("/some_resolved_path", "/container_path", "some-options"))))
                }
            }

            on("converting a configuration file with a container that has a volume mount that has a local path that is an invalid path") {
                val originalVolumeMountPath = "local_volume_path"
                val container = ContainerFromFile(imageName = "some-image", volumeMounts = setOf(VolumeMount(originalVolumeMountPath, "/container_path", "some-options")))
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                val pathResolver = mock<PathResolver> {
                    on { resolve(originalVolumeMountPath) } doReturn PathResolutionResult.InvalidPath
                }

                it("fails with an appropriate error message") {
                    assertThat({ configFile.toConfiguration(pathResolver) },
                        throws(withMessage("Local path 'local_volume_path' for volume mount in container 'the_container_name' is not a valid path.")))
                }
            }

            on("converting a configuration file with a container with an invalid command") {
                val container = ContainerFromFile(imageName = "some-image", command = "'")
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container))

                it("fails with an appropriate error message") {
                    assertThat({ configFile.toConfiguration(mock()) },
                        throws(withMessage("Command for container 'the_container_name' is invalid: Command `'` is invalid: it contains an unbalanced single quote")))
                }
            }

            on("converting a configuration file with a task run configuration with an invalid command") {
                val container = ContainerFromFile(imageName = "some-image")
                val runConfig = TaskRunConfigurationFromFile("the_container_name", command = "'")
                val task = TaskFromFile(runConfig)
                val configFile = ConfigurationFile("the_project_name", containers = mapOf("the_container_name" to container), tasks = mapOf("the_task_name" to task))

                it("fails with an appropriate error message") {
                    assertThat({ configFile.toConfiguration(mock()) },
                        throws(withMessage("Command for task 'the_task_name' is invalid: Command `'` is invalid: it contains an unbalanced single quote")))
                }
            }
        }
    }
})
