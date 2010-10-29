/*
 * pgp-gradle-plugin
 * Copyright (C) 2010 Joern Huxhorn
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
 * Copyright 2007-2010 Joern Huxhorn
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

package de.huxhorn.gradle.pgp 

import org.gradle.api.*
import org.gradle.api.logging.*
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

class PgpPlugin implements Plugin<Project> {
	def logger = Logging.getLogger(this.class)
	
	/**
	 * Static so password is only requested once if keyId isn't changed in
	 * sub-projects.
	 */
	private static PgpSigner signer=new PgpSigner();
	
	def void apply(Project project) {
        project.convention.plugins.pgp = new PgpPluginConvention()
		project.getByName('uploadArchives').doFirst {
			
			if(!project.convention.plugins.pgp.secretKeyRingFile)
			{
				throw new InvalidUserDataException("Missing secretKeyRingFile! Specify using 'pgp { secretKeyRingFile = file }'.")
			}
			if(!project.convention.plugins.pgp.keyId)
			{
				throw new InvalidUserDataException("Missing keyId! Specify using 'pgp { secretKeyRingFile = 'cafebabe' }'.")
			}

			boolean resetPassword = false
			signer.setSecretKeyRingFile( project.convention.plugins.pgp.secretKeyRingFile )

			if(project.convention.plugins.pgp.keyId != signer.keyId) {
				signer.keyId = project.convention.plugins.pgp.keyId
				resetPassword = true
				// reset signer password if keyId changes
				// this enables signing with different keys in submodules
			}
			
			if(resetPassword) {
				signer.password = project.convention.plugins.pgp.password
			}

			def defConf=project.getConfigurations().getByName('default')
			
			List<File> allFiles=new ArrayList<File>()
			for(File file in defConf.allArtifactFiles.files) {
				if(logger.isInfoEnabled())
					logger.info("Creating signature(s) for file '${file.absolutePath}'...")
				List<File> files = signer.sign(file)
				if(files)
				{
					if(logger.isDebugEnabled())
						logger.debug("Created signature files '{}'...", files)
					for(File f in files)
					{
						def extension=f.name
						extension = extension.substring(extension.lastIndexOf('.')+1)
						DefaultPublishArtifact artifact = new DefaultPublishArtifact(file.name, extension, extension, null, new Date(), f, this) 
						defConf.addArtifact(artifact)
						if(logger.isDebugEnabled())
							logger.debug("Added artifact: {}", artifact)
					}
					allFiles.addAll(files);
				}
			}
			if(logger.isInfoEnabled())
				logger.info("Created signature files '{}'...", allFiles)
			
			for(File file in allFiles)
			{
				// doesn't work ;(
				//project.artifacts.'default' file
			}
		}
	}
}

class PgpPluginConvention {
	File secretKeyRingFile;
	String keyId;
	String password = System.getProperty('pgp.password')
	
	def pgp(Closure closure) {
        closure.delegate = this
        closure() 
    }

}
