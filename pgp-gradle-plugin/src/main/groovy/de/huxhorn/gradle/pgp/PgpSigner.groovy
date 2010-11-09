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

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPUtil

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.FileInputStream
import java.security.Security

enum SignatureOutput {
	Armored,
	Binary,
	Both
}

/*
@Grapes([
@Grab(group='org.bouncycastle', module='bcpg-jdk15', version='1.45'),
@Grab(group='ch.qos.logback', module='logback-classic', version='0.9.24'),
@Grab(group='org.slf4j', module='slf4j-api', version='1.6.1')
])
*/
class PgpSigner {
	private final Logger logger = LoggerFactory.getLogger(PgpSigner.class)
	private static final String PROVIDER = 'BC'
	
	private static final int BUFFER_SIZE = 1024
	private static final long MASK = 0xFFFFFFFFL
	
	private static final String ARMORED_SIGNATURE_SUFFIX='.asc'
	private static final String BINARY_SIGNATURE_SUFFIX='.sig'

	
	static {
		// this needs to be added once
		Security.addProvider(new BouncyCastleProvider())
		final Logger logger=LoggerFactory.getLogger(PgpSigner)
		if(logger.isDebugEnabled()) logger.debug('Added BouncyCastleProvider.')
	}
	
	/**
	 * The id of the key used to sign, e.g. '740A1840'
	 */
	String keyId
	String password = System.getProperty('pgp.password')
	
	/**
	 * The output generated by signing a file. Either Armored, Binary or Both.
	 * Defaults to SignatureOutput.Armored.
	 */
	SignatureOutput output=SignatureOutput.Armored
	
	private Map<String, PGPSecretKey> secretKeys
	
	public void setKeyId(String keyId) {
		if(keyId) {
			keyId = keyId.toLowerCase()
		}
		this.keyId=keyId
	}
	
	public void setSecretKeyRingFile(File secretKeyRingFile) {
		FileInputStream fis=new FileInputStream(secretKeyRingFile)
		setSecretKeyRing(fis)
		fis.close()
	}
	
	public void setSecretKeyRing(InputStream secretKeyRingStream) {
		PGPObjectFactory pgpFact = new PGPObjectFactory(PGPUtil.getDecoderStream(secretKeyRingStream))

		secretKeys=new HashMap<String, PGPSecretKey>()

		for(;;) {
			def obj = pgpFact.nextObject()
			if(!obj) {
				break
			}
				
			if(!(obj instanceof PGPSecretKeyRing)) {
				throw new IllegalArgumentException("${obj.class.name} found, expected PGPSecretKeyRing instead." )
			}

			PGPSecretKeyRing pgpSecretKeyRing = (PGPSecretKeyRing) obj
			for(secretKey in pgpSecretKeyRing.secretKeys) {
				String key = Long.toString(secretKey.getKeyID() & MASK, 16)
	
				secretKeys[key] = secretKey
			}
		}
		if(logger.isDebugEnabled()) logger.debug('secretKeys: {}', secretKeys)
	}
	
	private File writeSignature(byte[] signature, File f, boolean armored) {
		File signatureFile
		OutputStream out
		if(armored) {
			signatureFile = new File(f.absolutePath + ARMORED_SIGNATURE_SUFFIX)
			ArmoredOutputStream armoredOutput = new ArmoredOutputStream(new FileOutputStream(signatureFile))
			armoredOutput.setHeader('Comment', 'Created by Gradle. Is this comment a good idea?')
			out = armoredOutput
		} else {
			signatureFile = new File(f.absolutePath + BINARY_SIGNATURE_SUFFIX)
			out = new FileOutputStream(signatureFile)
		}
		
		try {
			out.write(signature)
			out.flush()
			if(logger.isInfoEnabled()) logger.info('Wrote signature to file \'{}\'.', signatureFile.absolutePath)
			return signatureFile;
		}
		finally {
			try {
				out.close()
			}
			catch(any) {}
		}
	}
	
	List<File> sign(File f) {
		List<File> result=new ArrayList<File>();
		String fname=f.name.toLowerCase()
		if(fname.endsWith(ARMORED_SIGNATURE_SUFFIX) || fname.endsWith(BINARY_SIGNATURE_SUFFIX)) {
			if(logger.isDebugEnabled()) logger.debug('Ignoring {} because it is already a signature.', f.absolutePath)
			return result
		}
		
		String absolutePath=f.absolutePath
		
		FileInputStream input=new FileInputStream(f)
		ByteArrayOutputStream out=new ByteArrayOutputStream()
		byte[] signature
		try {
			sign(input, out)
			signature = out.toByteArray()
			if(logger.isInfoEnabled()) logger.info('Created signature for file \'{}\'.', absolutePath)
		}
		catch(Exception ex) {
			if(logger.isWarnEnabled()) logger.warn('Exception while creating signature!', ex)
			throw ex;
		}
		finally {
			try {
				input.close()
			}
			catch(IOException ex) {
				// ignore
			}
			try {
				out.close()
			}
			catch(IOException ex) {
				// ignore
			}
		}
		if(output == SignatureOutput.Armored || output == SignatureOutput.Both) {
			File sigFile = writeSignature(signature, f, true)
			if(sigFile) {
				result.add(sigFile)
			}
		}
		if(output == SignatureOutput.Binary || output == SignatureOutput.Both) {
			File sigFile = writeSignature(signature, f, false)
			if(sigFile) {
				result.add(sigFile)
			}
		}
		return result
	}
	
	def sign(InputStream input, OutputStream output) {
		if(!secretKeys) {
			throw new IllegalStateException('Missing secret keys! Have you set secretKeyRing File?')
		}
		
		PGPSecretKey pgpSec
		if(keyId) {
			pgpSec = secretKeys[keyId]
			if(!pgpSec) {
				throw new IllegalStateException("The key with id '${keyId}' was not found in the secret keyring. (${secretKeys.keySet()})")
			}
		} else {
			// use master key by default?
			throw new IllegalStateException('Missing keyId!')
		}

		if(!password) {
			// Java 1.6 required!!
			Console console = System.console()
			if(console) {
				System.out.flush()
				System.out.print('Enter PGP-Password: ')
				System.out.flush()
				char[] pwChars = console.readPassword()
				if(pwChars) {
					password=new String(pwChars)
				}
			}
		}
		if(!password) {
			throw new IllegalStateException('Missing password!')
		}
		
		def signOut = new BCPGOutputStream(output)

		PGPPrivateKey pgpPrivKey = pgpSec.extractPrivateKey(password.toCharArray(), PROVIDER)
		PGPSignatureGenerator sGen = new PGPSignatureGenerator(pgpSec.publicKey.algorithm, PGPUtil.SHA1, PROVIDER)
		sGen.initSign(PGPSignature.BINARY_DOCUMENT, pgpPrivKey)
		
		byte[] buf = new byte[BUFFER_SIZE]

		for(;;)
		{
			int len = input.read(buf)
			if(len < 0) {
				break
			}
			if(len > 0) {
				sGen.update(buf, 0, len)
			}
		}

		sGen.generate().encode(signOut)
		signOut.flush()
		output.close()
	}
}
