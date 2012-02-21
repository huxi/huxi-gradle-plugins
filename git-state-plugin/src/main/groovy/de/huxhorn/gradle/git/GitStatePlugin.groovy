/*
 * git-state-plugin
 * Copyright (C) 2012 Joern Huxhorn
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
 * Copyright 2012 Joern Huxhorn
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

import org.gradle.api.*
import org.gradle.api.tasks.TaskAction; 
import org.gradle.api.logging.*

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.api.Git;

import groovy.transform.Canonical
import groovy.transform.ToString

class GitStatePlugin implements Plugin<Project> {
	def logger = Logging.getLogger(this.class)
	
	def void apply(Project project) {
		project.extensions.git = new GitStatePluginExtension()
		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			Repository repository = builder.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build();
			logger.debug('Created git repository.')
			def headRef = repository.getRef("HEAD")
			project.setProperty('gitHeadHash', headRef.objectId.name)
			repository.close()
			logger.debug('Closed git repository.')
		} catch (IllegalArgumentException ex) {
			// ignore, there is no git repository
		}
			
		// add task
		project.task('checkGitState', type: GitStateTask)
	}
}


class GitStateTask extends DefaultTask {
    @TaskAction
    def checkState() {
    	if(!project.git.requireClean) {
    		return
    	}
    	
		boolean dirty=false
		StringBuilder message = new StringBuilder()
		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			Repository repository = builder.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build();
			logger.debug('Created git repository.')
			
			Git git = Git.wrap(repository)
			def status = git.status().call()
			if(status) {
				if(status.added) {
					message.append('Added:\n')
					for(String current : status.added) {
						message.append("\t${current}\n")
					}
					dirty = true
				}
				if(status.changed) {
					if(message.length()) {
						message.append('\n')
					}
					message.append('Changed:\n')
					for(String current : status.changed) {
						message.append("\t${current}\n")
					}
					dirty = true
				}
				if(status.conflicting) {
					if(message.length()) {
						message.append('\n')
					}
					message.append('Conflicting:\n')
					for(String current : status.conflicting) {
						message.append("\t${current}\n")
					}
					dirty = true
				}
				if(status.missing) {
					if(message.length()) {
						message.append('\n')
					}
					message.append('Missing:\n')
					for(String current : status.missing) {
						message.append("\t${current}\n")
					}
					dirty = true
				}
				if(status.modified) {
					if(message.length()) {
						message.append('\n')
					}
					message.append('Modified:\n')
					for(String current : status.modified) {
						message.append("\t${current}\n")
					}
					dirty = true
				}
				if(status.removed) {
					if(message.length()) {
						message.append('\n')
					}
					message.append('Removed:\n')
					for(String current : status.removed) {
						message.append("\t${current}\n")
					}
					dirty = true
				}
				if(status.untracked) {
					if(!project.git.ignoreUntracked) {
						if(message.length()) {
							message.append('\n')
						}
						message.append('Untracked:\n')
						for(String current : status.untracked) {
							message.append("\t${current}\n")
						}
						dirty = true
					}
				}
			}
			repository.close()
			logger.debug('Closed git repository.')
		} catch (IllegalArgumentException ex) {
			// ignore, there is no git repository
		}
		
		if(dirty) {
			logger.lifecycle(message.toString())
			throw new IllegalStateException('Git repository is dirty!')
		} else {
			logger.info('Git repository is clean.')
		}
    }
}

@Canonical
@ToString(includeNames=true)
class GitStatePluginExtension {
	boolean requireClean = false
	boolean ignoreUntracked = false
	
	def git(Closure closure) {
        closure.delegate = this
        closure() 
    }

}

