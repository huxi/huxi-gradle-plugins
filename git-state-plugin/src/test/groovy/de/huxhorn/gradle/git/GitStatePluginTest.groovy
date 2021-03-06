/*
 * git-state-plugin
 * Copyright (C) 2018 Joern Huxhorn
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright 2018 Joern Huxhorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.huxhorn.gradle.git 

import org.junit.Test
import org.zeroturnaround.zip.ZipUtil

import static org.junit.Assert.*
import org.gradle.api.*
import org.gradle.testfixtures.ProjectBuilder

class GitStatePluginTest {
	@Test
	void checkGitHeadHash() {
		Project project = ProjectBuilder.builder().build()
		unzipResourceIntoProject('/clean.zip', project)

		project.apply plugin: 'git-state'
		println "project.gitHeadHash: ${project.gitHeadHash}"
		assertEquals('d8435ab78d483666e2bb32565b8ed6d2adfe0346', project.gitHeadHash)
	}

	@Test
	void checkGitExtensionDefaults() {
		Project project = ProjectBuilder.builder().build()
		unzipResourceIntoProject('/clean.zip', project)

		project.apply plugin: 'git-state'
		println "project.git: ${project.git}"
		assertNotNull(project.git)
		assertFalse(project.git.requireClean)
		assertFalse(project.git.ignoreUntracked)
	}
	
	@Test
	void checkTaskAdded() {
		Project project = ProjectBuilder.builder().build()
		unzipResourceIntoProject('/clean.zip', project)

		project.apply plugin: 'git-state'
		assertTrue(project.tasks.checkGitState instanceof GitStateTask)
	}

	@Test
	void checkTaskExecuteDeactivated() {
		Project project = ProjectBuilder.builder().build()
		unzipResourceIntoProject('/clean.zip', project)

		project.apply plugin: 'git-state'
		assertTrue(project.tasks.checkGitState instanceof GitStateTask)
		project.tasks.checkGitState.checkState()
	}

	@Test
	void checkTaskExecuteActivated() {
		Project project = ProjectBuilder.builder().build()
		unzipResourceIntoProject('/clean.zip', project)

		project.apply plugin: 'git-state'
		assertTrue(project.tasks.checkGitState instanceof GitStateTask)
		project.git.requireClean = true
		project.git.ignoreUntracked = true
		project.tasks.checkGitState.checkState()
		println 'State was clean.'
	}

	@Test
	void checkTaskExecuteActivatedDirty() {
		Project project = ProjectBuilder.builder().build()
		unzipResourceIntoProject('/dirty.zip', project)

		project.apply plugin: 'git-state'
		assertTrue(project.tasks.checkGitState instanceof GitStateTask)
		project.git.requireClean = true
		project.git.ignoreUntracked = true
		try {
			project.tasks.checkGitState.checkState()
			fail('This should throw an exception!')
		} catch (IllegalStateException ex) {
			assertEquals('Git repository is dirty!', ex.message)
		}
	}

	private static void unzipResourceIntoProject(String resource, Project project) {
		def resourceStream = GitStatePluginTest.class.getResourceAsStream(resource)
		ZipUtil.unpack(resourceStream, project.projectDir)
	}
}

