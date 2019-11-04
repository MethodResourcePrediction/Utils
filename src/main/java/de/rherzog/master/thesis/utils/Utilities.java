package de.rherzog.master.thesis.utils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.StoreInstruction;
import com.ibm.wala.shrikeBT.Util;
import com.ibm.wala.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrikeBT.MethodEditor.Output;
import com.ibm.wala.shrikeBT.MethodEditor.Patch;
import com.ibm.wala.shrikeBT.shrikeCT.CTCompiler;
import com.ibm.wala.shrikeBT.shrikeCT.CTDecoder;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.generics.BaseType;
import com.ibm.wala.types.generics.TypeSignature;
import com.ibm.wala.util.strings.StringStuff;

public class Utilities {
	/**
	 * Converts a {@code Class} to a Type string. For example: "String" =>
	 * "Ljava/lang/String;"
	 * 
	 * @param clazz
	 * @return type equivalent to the class
	 */
	public static String classToType(Class<?> clazz) {
		String clazzName = clazz.getName().replaceAll("\\.", "/");
		return CTDecoder.convertClassToType(clazzName);
	}

	/**
	 * Instructs a conversion from primitive type to {@code Object}. Example: "I" =>
	 * "Ljava/lang/Integer".
	 * 
	 * @param w    - Method writer
	 * @param type - Type of stacks top-most element
	 */
	public static void convertIfNecessary(Output w, TypeSignature type) {
		// Convert to Object if necessary
		InvokeInstruction conversionInstruction = fromBaseTypeToObject(type);
		if (conversionInstruction != null) {
			w.emit(conversionInstruction);
		}
	}

	/**
	 * Return an {@code InvokeInstruction} for a given base type (int, long, float,
	 * ...) which converts the base type into an {@code Object}. If the given
	 * {@code type} is not a base type, the return value is {@code null}
	 * 
	 * @see com.ibm.wala.types.TypeReference
	 * 
	 * @param type TypeSignature
	 * @return InvokeInstruction or null if the type is not a base type
	 */
	public static InvokeInstruction fromBaseTypeToObject(TypeSignature type) {
		if (!type.isBaseType()) {
			return null;
		}
		BaseType baseType = (BaseType) type;
		TypeReference typeReference = null;

		if (baseType.getType().equals(TypeReference.Boolean)) {
			typeReference = TypeReference.JavaLangBoolean;
		} else if (baseType.getType().equals(TypeReference.Byte)) {
			typeReference = TypeReference.JavaLangByte;
		} else if (baseType.getType().equals(TypeReference.Char)) {
			typeReference = TypeReference.JavaLangCharacter;
		} else if (baseType.getType().equals(TypeReference.Double)) {
			typeReference = TypeReference.JavaLangDouble;
		} else if (baseType.getType().equals(TypeReference.Float)) {
			typeReference = TypeReference.JavaLangFloat;
		} else if (baseType.getType().equals(TypeReference.Int)) {
			typeReference = TypeReference.JavaLangInteger;
		} else if (baseType.getType().equals(TypeReference.Long)) {
			typeReference = TypeReference.JavaLangLong;
		} else if (baseType.getType().equals(TypeReference.Short)) {
			typeReference = TypeReference.JavaLangShort;
		} else {
			throw new IllegalArgumentException("Cannot convert base type [" + type + "]");
		}
		String clazz = typeReference.getName().toString() + ";";
		String methodSignature = "(" + type.toString() + ")" + clazz;

		return InvokeInstruction.make(methodSignature, clazz, "valueOf", Dispatch.STATIC);
	}

	/**
	 * Get the maximum variable index which is in use by a method. The next one is
	 * intended to be free for use.
	 * 
	 * @param methodData
	 * @param instructions
	 * @return max variable index for method (in all instructions)
	 */
	public static int getMaxLocalVarIndex(MethodData methodData, IInstruction[] instructions) {
		int maxIndex = 0;

		// Search for the biggest variable index
		for (IInstruction instruction : instructions) {
			if (instruction instanceof StoreInstruction) {
				StoreInstruction instruction2 = (StoreInstruction) instruction;
				if (instruction2.getVarIndex() > maxIndex) {
					maxIndex = instruction2.getVarIndex();
				}
			} else if (instruction instanceof LoadInstruction) {
				LoadInstruction instruction2 = (LoadInstruction) instruction;
				if (instruction2.getVarIndex() > maxIndex) {
					maxIndex = instruction2.getVarIndex();
				}
			}
		}
		maxIndex += 1; // For non-static methods => 0 is [this]

		// Increment by the number of parameters as well
		final TypeName[] parameterNames = StringStuff.parseForParameterNames(methodData.getSignature());
		if (parameterNames != null) {
			maxIndex += parameterNames.length;
		}
		return maxIndex + 10;
	}

	public static void correctJarManifests(final String jarFileIn, final String jarFileOut)
			throws IOException, DecoderException {
		correctJarManifests(jarFileIn, jarFileOut, null);
	}

	/**
	 * Recreate correct META-INF/ files for a given *.jar
	 * <p>
	 * This method recalculates all specified SHA1-Digest's inside MANIFEST.MF and
	 * deletes all other files (for example signature files)
	 * </p>
	 * 
	 * @param jarFileIn  the jar file to read
	 * @param jarFileOut the corrected jar file to write
	 * @throws IOException      if a file cannot be read/written
	 * @throws DecoderException if the file digest cannot be converted to HEX bytes
	 */
	public static void correctJarManifests(final String jarFileIn, final String jarFileOut, final String mainClass)
			throws IOException, DecoderException {
		final String charsetName = "UTF-8";
		final Charset charset = Charset.forName(charsetName);

		// Open jar file for reading (zip)
		FileInputStream fileInputStream = new FileInputStream(jarFileIn);
		ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

		// Open jar file for writing (zip)
		FileOutputStream fileOutStream = new FileOutputStream(jarFileOut);
		ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutStream);

		// Iterate all entries (files) in the zip
		ZipEntry zipInputEntry = zipInputStream.getNextEntry();
		while (zipInputEntry != null) {
			String filePath = zipInputEntry.getName();

			// Special handling for "META-INF/" directory.
			// External signatures will be removed and the file hash values are post
			// calculated
			if (filePath.startsWith("META-INF/")) {
				// Ignore all other files except META-INF/MANIFEST.MF
				if (!filePath.equals("META-INF/MANIFEST.MF")) {
					// Ignore all other files under META-INF/*
				} else {
					// Read the MANIFEST.MF into a byte array stream
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					IOUtils.copy(zipInputStream, baos);
					baos.close();

					// Convert the manifest bytes to a String (which we can parse)
					String manifestContent = IOUtils.toString(baos.toByteArray(), charsetName);

					// Create the new Manifest structure from our converted String
					InputStream manifestIS = IOUtils.toInputStream(manifestContent, charset);
					Manifest manifest = new Manifest(manifestIS);
					manifestIS.close();

					if (mainClass != null && !mainClass.isEmpty()) {
						manifest.getMainAttributes().putValue("Main-Class", mainClass);
					}

					// Iterate all manifest entries to find each of them which contains a class hash
					for (Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
						// The key is "Name: ..." or "SHA1-Digest: ..." for example
						String manifestKey = entry.getKey();

						// Get the corresponding attributes of an entry (the value of the name)
						Attributes attributes = entry.getValue();

						// Find the "SHA1-Digest" property and get the value (the hash)
						String sha1digest = attributes.getValue("SHA1-Digest");

						if (sha1digest != null) {
							// If a hash is found => calculate the new digest (base64 representation of the
							// SHA1 hash) of the (possibly) altered class
							String base64sha1Hash = jarFileBase64SHA1HashDigest(jarFileIn, manifestKey);

							if (!sha1digest.equals(base64sha1Hash)) {
								// Print out if hashes do not match
								System.out.println(manifestKey + ": " + sha1digest + " => " + base64sha1Hash);
							}
							// Set the new digest (
							attributes.putValue("SHA1-Digest", base64sha1Hash);
						}
					}
					// Write the manifest contents into a temporary variable which we can read later
					ByteArrayOutputStream mfBaos = new ByteArrayOutputStream();
					manifest.write(mfBaos);

					// Create a new ZipEntry for the MANIFEST.MF and place the pointer on that
					ZipEntry zipOutputEntry = new ZipEntry(zipInputEntry.getName());
					zipOutputStream.putNextEntry(zipOutputEntry);

					// Copy the String representation of the String into the ZipEntry
					IOUtils.copyLarge(IOUtils.toInputStream(mfBaos.toString(), charset), zipOutputStream, 0,
							mfBaos.size());

					// Close everything what was opened
					mfBaos.close();
					zipOutputStream.closeEntry();
				}
			} else {
				// Simply copy all other entries
				zipOutputStream.putNextEntry(zipInputEntry);
				IOUtils.copyLarge(zipInputStream, zipOutputStream, 0, zipInputEntry.getCompressedSize());
				zipOutputStream.closeEntry();
			}

			zipInputEntry = zipInputStream.getNextEntry();
		}
		// Close the read file handles
		zipOutputStream.close();
		fileOutStream.close();

		// Close the write file handles
		zipInputStream.close();
		fileInputStream.close();
	}

	/**
	 * Reads out a file from a given jar-Path and calculates its HEX-Base64 digest
	 * representation
	 * 
	 * @param jarFilePath the jar file where the file can be found
	 * @param fileName    the name of file from which the digest is requested
	 * @return HEX-Base64 digest of "name"
	 * @throws IOException      if an IO error occurs reading jarFilePath
	 * @throws DecoderException if the file digest cannot be converted to HEX bytes
	 */
	public static String jarFileBase64SHA1HashDigest(String jarFilePath, String fileName)
			throws IOException, DecoderException {
		// Open jar file as a ZipInputStream
		FileInputStream fileInputStream = new FileInputStream(jarFilePath);
		ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

		String hexB64 = null;

		// Iterate all zip entries as long as we found the requested file
		ZipEntry zipInputEntry = zipInputStream.getNextEntry();
		while (zipInputEntry != null) {
			if (zipInputEntry.getName().equals(fileName)) {
				// Found file in jar. Read it
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				IOUtils.copy(zipInputStream, baos);

				// https://stackoverflow.com/a/7372374
				// Calculate the SHA1 hash as HEX and decode it binary
				byte[] decodedHex = Hex.decodeHex(DigestUtils.sha1Hex(baos.toByteArray()));
				// Encode the binary SHA1 digest into Base64
				byte[] encodedHexB64 = Base64.encodeBase64(decodedHex);

				// Create our return String
				hexB64 = new String(encodedHexB64);
				// Done
				break;
			}
			zipInputEntry = zipInputStream.getNextEntry();
		}
		zipInputStream.close();
		fileInputStream.close();
		return hexB64;
	}

	public static Double convertNumericObjectToDouble(Object value) {
		// Convert every numeric datatype to double
		Double d = null;
		if (value instanceof Integer) {
			d = ((Integer) value).doubleValue();
		} else if (value instanceof Long) {
			d = ((Long) value).doubleValue();
		} else if (value instanceof Double) {
			d = ((Double) value).doubleValue();
		} else if (value instanceof Boolean) {
			d = ((Boolean) value) ? 1d : 0d;
		} else if (value instanceof Short) {
			d = ((Short) value).doubleValue();
		} else if (value instanceof Byte) {
			d = ((Byte) value).doubleValue();
		} else if (value instanceof Float) {
			d = ((Float) value).doubleValue();
		} else {
			// In other cases the value should be primitive
			d = (double) value;
		}
		return d;
	}

	public static Patch getStoreTimePatch(int timeVarIndex) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Java-Code
//				long timeMillis = System.currentTimeMillis();

				// Bytecode
//			    INVOKESTATIC java/lang/System.currentTimeMillis()J
//			    LSTORE 1 [timeMillisVarIndex]

				w.emit(Util.makeInvoke(System.class, "currentTimeMillis", new Class[] {}));
				w.emit(StoreInstruction.make(CTCompiler.TYPE_long, timeVarIndex));
			}
		};
	}

	public static Patch getStoreDurationPatch(int timeStartVarIndex, int timeEndVarIndex, int timeDurationVarIndex) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Java-Code (pseudo)
//				public void getDuration(long start, long end) {
//					long duration = end - start;
//				}

				// Bytecode
//				LLOAD 3 [timeEndVarIndex]
//			    LLOAD 1 [timeStartVarIndex]
//			    LSUB
//			    LSTORE 5 [timeDurationVarIndex]

				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, timeEndVarIndex));
				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, timeStartVarIndex));
				w.emit(BinaryOpInstruction.make(CTCompiler.TYPE_long, Operator.SUB));
				w.emit(StoreInstruction.make(CTCompiler.TYPE_long, timeDurationVarIndex));
			}
		};
	}

	public static Patch getEmptyPatch() {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
			}
		};
	}
}
