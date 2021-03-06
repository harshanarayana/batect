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

package batect.os

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.Properties

object SystemInfoSpec : Spek({
    describe("a system info provider") {
        val processRunner = mock<ProcessRunner> {
            on { runAndCaptureOutput(listOf("id", "-u")) } doReturn ProcessOutput(0, "123\n")
            on { runAndCaptureOutput(listOf("id", "-un")) } doReturn ProcessOutput(0, "awesome-user\n")
            on { runAndCaptureOutput(listOf("id", "-g")) } doReturn ProcessOutput(0, "777\n")
            on { runAndCaptureOutput(listOf("id", "-gn")) } doReturn ProcessOutput(0, "awesome-group\n")
        }

        val systemProperties = Properties()
        systemProperties.setProperty("java.vm.vendor", "Awesome JVMs, Inc.")
        systemProperties.setProperty("java.vm.name", "Best JVM Ever")
        systemProperties.setProperty("java.version", "1.2.3")
        systemProperties.setProperty("os.name", "Best OS Ever")
        systemProperties.setProperty("os.arch", "x86")
        systemProperties.setProperty("os.version", "4.5.6")
        systemProperties.setProperty("user.home", "/some/home/dir")

        val systemInfo = SystemInfo(processRunner, systemProperties)

        on("getting the JVM version") {
            val jvmVersion = systemInfo.jvmVersion

            it("returns a formatted string containing the details of the JVM") {
                assertThat(jvmVersion, equalTo("Awesome JVMs, Inc. Best JVM Ever 1.2.3"))
            }
        }

        on("getting the OS version") {
            val osVersion = systemInfo.osVersion

            it("returns a formatted string containing the details of the OS") {
                assertThat(osVersion, equalTo("Best OS Ever 4.5.6 (x86)"))
            }
        }

        on("getting the home directory") {
            val homeDir = systemInfo.homeDirectory

            it("returns the user's home directory") {
                assertThat(homeDir, equalTo("/some/home/dir"))
            }
        }

        on("getting the current user ID") {
            val userID = systemInfo.userId

            it("returns the ID given by the `id -u` command") {
                assertThat(userID, equalTo(123))
            }
        }

        on("getting the current user name") {
            val userName = systemInfo.userName

            it("returns the ID given by the `id -un` command") {
                assertThat(userName, equalTo("awesome-user"))
            }
        }

        on("getting the current group ID") {
            val groupID = systemInfo.groupId

            it("returns the ID given by the `id -g` command") {
                assertThat(groupID, equalTo(777))
            }
        }

        on("getting the current group name") {
            val groupName = systemInfo.groupName

            it("returns the ID given by the `id -gn` command") {
                assertThat(groupName, equalTo("awesome-group"))
            }
        }
    }
})
