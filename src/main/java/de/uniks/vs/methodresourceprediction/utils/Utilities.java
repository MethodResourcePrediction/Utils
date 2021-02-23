package de.uniks.vs.methodresourceprediction.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.UnexpectedException;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.types.Selector;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrikeBT.MethodEditor.Output;
import com.ibm.wala.shrikeBT.MethodEditor.Patch;
import com.ibm.wala.shrikeBT.shrikeCT.CTCompiler;
import com.ibm.wala.shrikeBT.shrikeCT.CTDecoder;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.generics.BaseType;
import com.ibm.wala.types.generics.TypeSignature;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.strings.StringStuff;

import javax.management.monitor.Monitor;

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
		} else if (baseType.getType().equals(TypeReference.Short)) {
			typeReference = TypeReference.JavaLangShort;
		} else if (baseType.getType().equals(TypeReference.Int)) {
			typeReference = TypeReference.JavaLangInteger;
		} else if (baseType.getType().equals(TypeReference.Long)) {
			typeReference = TypeReference.JavaLangLong;
		} else if (baseType.getType().equals(TypeReference.Float)) {
			typeReference = TypeReference.JavaLangFloat;
		} else if (baseType.getType().equals(TypeReference.Double)) {
			typeReference = TypeReference.JavaLangDouble;
		} else {
			throw new IllegalArgumentException("Cannot convert base type [" + type + "]");
		}
		String clazz = typeReference.getName().toString() + ";";
		String methodSignature = "(" + type.toString() + ")" + clazz;

		return InvokeInstruction.make(methodSignature, clazz, "valueOf", Dispatch.STATIC);
	}

	public static boolean isNumericBaseType(String type) {
		if (Constants.TYPE_boolean.equals(type)) {
			return true;
		}
		if (Constants.TYPE_byte.equals(type)) {
			return true;
		}
		if (Constants.TYPE_char.equals(type)) {
			return true;
		}
		if (Constants.TYPE_short.equals(type)) {
			return true;
		}
		if (Constants.TYPE_int.equals(type)) {
			return true;
		}
		if (Constants.TYPE_long.equals(type)) {
			return true;
		}
		if (Constants.TYPE_float.equals(type)) {
			return true;
		}
		if (Constants.TYPE_double.equals(type)) {
			return true;
		}
		return false;
	}

	/**
	 * Get the maximum variable index which is in use by a method. The next one is
	 * intended to be free for use.
	 *
	 * @param methodData
	 * @return max variable index for method (in all instructions)
	 */
	public static int getMaxLocalVarIndex(MethodData methodData) {
		return getMaxLocalVarIndex(methodData.getInstructions());
	}

	/**
	 * Get the maximum variable index which is in use by a method. The next one is
	 * intended to be free for use.
	 *
	 * @param instructions
	 * @return max variable index for method (in all instructions)
	 */
	public static int getMaxLocalVarIndex(IInstruction[] instructions) {
		int maxIndex = 0;

		// Search for the biggest variable index
		for (IInstruction instruction : instructions) {
			if (instruction instanceof StoreInstruction) {
				StoreInstruction instruction2 = (StoreInstruction) instruction;
				if (instruction2.getVarIndex() > maxIndex) {
					maxIndex = instruction2.getVarIndex();

					// If the instruction has a 2-sized element type, reserve one more var index
					String type = instruction2.getType();
					if (type.contentEquals(Constants.TYPE_long) || type.contentEquals(Constants.TYPE_double)) {
						maxIndex += 1;
					}
				}
			} else if (instruction instanceof LoadInstruction) {
				LoadInstruction instruction2 = (LoadInstruction) instruction;
				if (instruction2.getVarIndex() > maxIndex) {
					maxIndex = instruction2.getVarIndex();

					// If the instruction has a 2-sized element type, reserve one more var index
					String type = instruction2.getType();
					if (type.contentEquals(Constants.TYPE_long) || type.contentEquals(Constants.TYPE_double)) {
						maxIndex += 1;
					}
				}
			}
		}
		return maxIndex;
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

	public static boolean isNumeric(Object value) {
		if (value == null) {
			return false;
		}
		try {
			if (value instanceof Integer) {
				return true;
			} else if (value instanceof Long) {
				return true;
			} else if (value instanceof Double) {
				return true;
			} else if (value instanceof Boolean) {
				return true;
			} else if (value instanceof Short) {
				return true;
			} else if (value instanceof Byte) {
				return true;
			} else if (value instanceof Float) {
				return true;
			} else if (value instanceof Character) {
				return true;
			}
		} catch (NullPointerException e) {
		}
		return false;
	}

	public static Double convertNumericObjectToDouble(Object value) {
		if (!isNumeric(value)) {
			return null;
		}

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
		} else if (value instanceof Character) {
			d = Double.valueOf((Character) value).doubleValue();
		} else {
			// In other cases the value should be primitive
			d = (double) value;
		}
		return d;
	}

	public static boolean isRecursiveInvokeInstruction(MethodData methodData, IInvokeInstruction instruction) {
		if (methodData.getClassType().compareTo(instruction.getClassType()) != 0) {
			return false;
		}
		if (methodData.getName().compareTo(instruction.getMethodName()) != 0) {
			return false;
		}
		if (methodData.getSignature().compareTo(instruction.getMethodSignature()) != 0) {
			return false;
		}
		return true;
	}

	public static boolean isConstructorInvokeInstruction(NewInstruction newInstruction,
			IInvokeInstruction invokeInstruction) {
		// TODO Are there more possible checks like package or modules?
		if (!newInstruction.getType().contentEquals(invokeInstruction.getClassType())) {
			return false;
		}
		return invokeInstruction.getMethodName().contentEquals("<init>");
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

	public static Patch getStoreCurrentTimeDurationPatch(int timeStartVarIndex, int timeDurationVarIndex) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Java-Code
//				long timeDuration = System.currentTimeMillis() - timeMillis;

				// Bytecode
//			    INVOKESTATIC java/lang/System.currentTimeMillis()J
//			    LLOAD 1 [timeStartVarIndex]
//			    LSUB
//				LSTORE 2 [timeDurationVarIndex]

				w.emit(Util.makeInvoke(System.class, "currentTimeMillis", new Class[] {}));
				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, timeStartVarIndex));
				w.emit(BinaryOpInstruction.make(CTCompiler.TYPE_long,
						com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.SUB));
				w.emit(StoreInstruction.make(CTCompiler.TYPE_long, timeDurationVarIndex));
			}
		};
	}

	public static Patch getSubstractionPatch(int opA, int opB, int result) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Bytecode
//				LLOAD 3 [opA]
//			    LLOAD 1 [opB]
//			    LSUB
//			    LSTORE 5 [result]

				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, opB));
				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, opA));
				w.emit(BinaryOpInstruction.make(CTCompiler.TYPE_long, Operator.SUB));
				w.emit(StoreInstruction.make(CTCompiler.TYPE_long, result));
			}
		};
	}

	public static Patch getAdditionPatch(int opA, int opB, int result) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Bytecode
//				LLOAD 3 [opA]
//			    LLOAD 1 [opB]
//			    LADD
//			    LSTORE 5 [result]

				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, opB));
				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, opA));
				w.emit(BinaryOpInstruction.make(CTCompiler.TYPE_long, Operator.ADD));
				w.emit(StoreInstruction.make(CTCompiler.TYPE_long, result));
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

	public static IInstruction rewriteVarIndex(Map<Integer, Set<Integer>> varIndexesToRenumber,
			IInstruction instruction, int instructionIndex) {
		IInstruction newInstruction = instruction;
		for (Entry<Integer, Set<Integer>> entry : varIndexesToRenumber.entrySet()) {
			if (entry.getValue().contains(instructionIndex)) {
				if (instruction instanceof ILoadInstruction) {
					ILoadInstruction loadInstruction = (ILoadInstruction) instruction;
					newInstruction = LoadInstruction.make(loadInstruction.getType(), entry.getKey());
				} else if (instruction instanceof IStoreInstruction) {
					IStoreInstruction storeInstruction = (IStoreInstruction) instruction;
					newInstruction = StoreInstruction.make(storeInstruction.getType(), entry.getKey());
				}
				break;
			}
		}
		return newInstruction;
	}

	public static void dotWriteToFile(String path, String dotPrint) throws IOException, InterruptedException {
		dotWriteToFile(path, dotPrint, "png");
	}

	public static void dotWriteToFile(String path, String dotPrint, String format)
			throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder("dot", "-T" + format, "-o" + path);
		Process process = builder.start();
		OutputStream outputStream = process.getOutputStream();

		IOUtils.write(dotPrint, outputStream, Charset.defaultCharset());
		outputStream.close();

		process.waitFor();
	}

	public static void dotShow(Path dir, String fileName, String dotPrint) throws IOException, InterruptedException {
		final String format = "png";

		final Path filePath = Path.of(dir.toString(), fileName);
		String path = null;
		if (!Files.exists(filePath)) {
			path = Files.createFile(filePath).toFile().getPath();
		} else {
			path = filePath.toString();
		}

		dotWriteToFile(path, dotPrint, format);

		ProcessBuilder builder = new ProcessBuilder("xdg-open", path);
		builder.start().waitFor();
	}

	public static void dotShow(Path dir, String dotPrint) throws IOException, InterruptedException {
		final String format = "png";
		final String fileName = Files.createTempFile(dir, "dot-", "." + format).toFile().getName();

		dotShow(dir, fileName, dotPrint);
	}

	public static void dotShow(String dotPrint) throws IOException, InterruptedException {
		Path dir = Files.createTempDirectory("dot-");
		dotShow(dir, dotPrint);
	}

	public static int getPoppedElementCount(IInstruction iInstruction) {
		if (iInstruction instanceof PopInstruction) {
			// Regardless of pop size, the number of elements popped is always 1
			return 1;
		}
		return iInstruction.getPoppedCount();
	}

	public static int getPoppedElementCountForDupInstruction(DupInstruction dupInstruction, boolean singleSized) {
		if (singleSized) {
			return dupInstruction.getSize();
		} else {
			return 1;
		}
	}

	public static byte getPoppedSize(IInstruction iInstruction) {
		if (iInstruction instanceof ILoadInstruction) {
//			ILoadInstruction instruction = (ILoadInstruction) iInstruction;
			// A load instruction does never pop anything
			return 0;
		}
		if (iInstruction instanceof IStoreInstruction) {
			IStoreInstruction instruction = (IStoreInstruction) iInstruction;
			return getWordSizeByType(instruction.getType());
		}
		if (iInstruction instanceof IInvokeInstruction) {
			IInvokeInstruction instruction = (IInvokeInstruction) iInstruction;
			String methodSignature = instruction.getMethodSignature();
			TypeName[] parameterNames = StringStuff.parseForParameterNames(methodSignature);
			byte poppedSize = (byte) (instruction.getInvocationCode().hasImplicitThis() ? 1 : 0);
			if (parameterNames != null) {
				for (TypeName parameterName : parameterNames) {
					poppedSize += getWordSizeByType(parameterName.getClassName().toString());
				}
			}
			return poppedSize;
		}
		if (iInstruction instanceof IConditionalBranchInstruction) {
			IConditionalBranchInstruction instruction = (IConditionalBranchInstruction) iInstruction;
//			return (byte) (2 * getWordSizeByType(instruction.getType()));
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IBinaryOpInstruction) {
			IBinaryOpInstruction instruction = (IBinaryOpInstruction) iInstruction;
			return (byte) (2 * getWordSizeByType(instruction.getType()));
		}
		if (iInstruction instanceof ConstantInstruction) {
			// A constant instruction does never pop anything
			return 0;
		}
		if (iInstruction instanceof IArrayLoadInstruction) {
			IArrayLoadInstruction instruction = (IArrayLoadInstruction) iInstruction;
			// TODO Usually consumes 2 elements but what about a primitive array?
//			return getWordSizeByType(instruction.getType());
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof PopInstruction) {
			PopInstruction instruction = (PopInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof DupInstruction) {
			DupInstruction instruction = (DupInstruction) iInstruction;
			return (byte) instruction.getSize();
		}
		if (iInstruction instanceof IGetInstruction) {
			IGetInstruction instruction = (IGetInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof ReturnInstruction) {
			ReturnInstruction instruction = (ReturnInstruction) iInstruction;
			return (byte) getWordSizeByType(instruction.getType());
		}
		if (iInstruction instanceof IConversionInstruction) {
			IConversionInstruction instruction = (IConversionInstruction) iInstruction;
			return getWordSizeByType(instruction.getFromType());
		}
		if (iInstruction instanceof NewInstruction) {
			NewInstruction instruction = (NewInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IArrayStoreInstruction) {
			IArrayStoreInstruction instruction = (IArrayStoreInstruction) iInstruction;
			// TODO Usually consumes 2 elements but what about a primitive array?
//			return getWordSizeByType(instruction.getType());
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof GotoInstruction) {
//			GotoInstruction instruction = (GotoInstruction) iInstruction;
			// A goto instruction does never pop anything
			return 0;
		}
		if (iInstruction instanceof ArrayLengthInstruction) {
			ArrayLengthInstruction instruction = (ArrayLengthInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof PutInstruction) {
			PutInstruction instruction = (PutInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IComparisonInstruction) {
			ComparisonInstruction instruction = (ComparisonInstruction) iInstruction;
			return (byte) (2 * getWordSizeByType(instruction.getType()));
		}
		if (iInstruction instanceof ThrowInstruction) {
			ThrowInstruction instruction = (ThrowInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof MonitorInstruction) {
			MonitorInstruction instruction = (MonitorInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof CheckCastInstruction) {
			CheckCastInstruction instruction = (CheckCastInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof InstanceofInstruction) {
			InstanceofInstruction instruction = (InstanceofInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof SwitchInstruction) {
			SwitchInstruction instruction = (SwitchInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof UnaryOpInstruction) {
			UnaryOpInstruction instruction = (UnaryOpInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IShiftInstruction) {
			IShiftInstruction instruction = (IShiftInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		throw new UnsupportedOperationException("Unhandled instruction type " + iInstruction.getClass().getName());
	}

	public static int getPoppedSizeForDupInstruction(DupInstruction dupInstruction, boolean singleSized) {
		if (singleSized) {
			return dupInstruction.getSize();
		} else {
			return 2 * dupInstruction.getSize();
		}
	}

	public static int getPushedElementCount(IInstruction iInstruction) {
		if (iInstruction instanceof DupInstruction) {
			return 2 * ((DupInstruction) iInstruction).getSize();
		}
		return iInstruction.getPushedWordSize() >= 1 ? 1 : 0;
	}

	public static byte getPushedSize(IInstruction iInstruction) {
		if (iInstruction instanceof DupInstruction) {
			DupInstruction instruction = (DupInstruction) iInstruction;
			return (byte) (2 * instruction.getSize());
		}
		if (iInstruction instanceof IInvokeInstruction) {
			IInvokeInstruction instruction = (IInvokeInstruction) iInstruction;
			TypeName returnTypeName = StringStuff.parseForReturnTypeName(instruction.getMethodSignature());
			if (returnTypeName.equals(TypeName.findOrCreate(Constants.TYPE_void))) {
				return 0;
			}
			return Util.getWordSize(returnTypeName.toString());
		}
		return iInstruction.getPushedWordSize();
	}

	public static byte getWordSizeByType(String type) {
		switch (type) {
		case CTCompiler.TYPE_void:
			return 0;
		case CTCompiler.TYPE_double:
		case CTCompiler.TYPE_long:
			return 2;
		default:
			return 1;
		}
	}

	public static Pair<Integer, IStoreInstruction> findInstructionByVarIndex(IInstruction[] instructions,
			int varIndex) {
		for (int i = 0; i < instructions.length; i++) {
			IInstruction instruction2 = instructions[i];
			if (instruction2 instanceof IStoreInstruction) {
				IStoreInstruction storeInstruction = (IStoreInstruction) instruction2;
				if (storeInstruction.getVarIndex() == varIndex) {
					return Pair.make(i, storeInstruction);
				}
			}
		}
		return null;
	}

	public static ShrikeCTMethod getShrikeCTMethod(String regressionExclusions, String inputJar, String methodSignature)
			throws IOException, ClassHierarchyException {
		InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);
		ShrikeCTMethod method = (ShrikeCTMethod) getClassHierarchy(regressionExclusions, inputJar)
				.resolveMethod(comparator.getMethodReference());
		return method;
	}

	public static List<IMethod> getMethods(String regressionExclusions, String inputJar)
			throws IOException, ClassHierarchyException {
		List<IMethod> iMethodList = new ArrayList<>();
		for (IClass iClass : getClassHierarchy(regressionExclusions, inputJar)) {
			iMethodList.addAll(iClass.getAllMethods());
		}
		return iMethodList;
	}

	public static List<String> getMethodSignatures(String regressionExclusions, String inputJar)
			throws IOException, ClassHierarchyException {
		List<String> signatures = new ArrayList<>();
		getMethods(regressionExclusions, inputJar).forEach(method ->
				signatures.add(Selector.make(method.getSignature()).toString())
		);
		return signatures;
	}

	public static ClassHierarchy getClassHierarchy(String regressionExclusions, String inputJar)
			throws IOException, ClassHierarchyException {
		// create an analysis scope representing the appJar as a J2SE application
		File regressionExclusionsFile = new File(regressionExclusions);
		AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(inputJar, regressionExclusionsFile);

		// build a class hierarchy, call graph, and system dependence graph
		ClassHierarchy cha = ClassHierarchyFactory.make(scope);
		return cha;
	}

	public static TypeSignature getInstructionType(IInstruction iInstruction) {
		String pushedType = iInstruction.getPushedType(null);
		if (pushedType != null) {
			return TypeSignature.make(pushedType);
		}
		if (iInstruction instanceof ILoadInstruction) {
			ILoadInstruction instruction = (ILoadInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof IStoreInstruction) {
			IStoreInstruction instruction = (IStoreInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof IInvokeInstruction) {
			IInvokeInstruction instruction = (IInvokeInstruction) iInstruction;
			String methodSignature = instruction.getMethodSignature();
			TypeName returnTypeName = StringStuff.parseForReturnTypeName(methodSignature);
			return TypeSignature.make(returnTypeName.toString());
		}
		if (iInstruction instanceof IConditionalBranchInstruction) {
			IConditionalBranchInstruction instruction = (IConditionalBranchInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof IBinaryOpInstruction) {
			IBinaryOpInstruction instruction = (IBinaryOpInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof ConstantInstruction) {
			ConstantInstruction instruction = (ConstantInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof IArrayLoadInstruction) {
			IArrayLoadInstruction instruction = (IArrayLoadInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof PopInstruction) {
//			PopInstruction instruction = (PopInstruction) iInstruction;
			return null;
		}
		if (iInstruction instanceof DupInstruction) {
//			DupInstruction instruction = (DupInstruction) iInstruction;
			return null;
		}
		if (iInstruction instanceof IGetInstruction) {
			IGetInstruction instruction = (IGetInstruction) iInstruction;
			return TypeSignature.make(instruction.getFieldType());
		}
		if (iInstruction instanceof ReturnInstruction) {
			ReturnInstruction instruction = (ReturnInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof IConversionInstruction) {
			IConversionInstruction instruction = (IConversionInstruction) iInstruction;
			return TypeSignature.make(instruction.getToType());
		}
		if (iInstruction instanceof NewInstruction) {
			NewInstruction instruction = (NewInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof IArrayStoreInstruction) {
			IArrayStoreInstruction instruction = (IArrayStoreInstruction) iInstruction;
			return TypeSignature.make(instruction.getType());
		}
		if (iInstruction instanceof GotoInstruction) {
//			GotoInstruction instruction = (GotoInstruction) iInstruction;
			return null;
		}
		if (iInstruction instanceof ArrayLengthInstruction) {
//			ArrayLengthInstruction instruction = (ArrayLengthInstruction) iInstruction;
			return TypeSignature.make(Constants.TYPE_int);
		}
		return null;
	}

	public static boolean booleanValue(double d) {
		return d == 0.0d;
	}

	public static void extractFilesFromZip(String zipFilePath, String outputPath, String... zipFiles)
			throws IOException {
		byte[] buffer = new byte[1024];

		ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
		ZipEntry entry = null;
		while ((entry = zis.getNextEntry()) != null) {
			for (String zipFile : zipFiles) {
				if (zipFile.equals(entry.getName())) {
					File targetFile = new File(FilenameUtils.concat(outputPath, zipFile));
					FileOutputStream fos = new FileOutputStream(targetFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
			}
		}
		zis.closeEntry();
		zis.close();
	}

	public static String typeToConstantFieldSource(String type) throws UnexpectedException {
		String constantFieldSource = "Constants.TYPE_";
		switch (type) {
		case Constants.TYPE_boolean:
			constantFieldSource += "boolean";
			break;
		case Constants.TYPE_byte:
			constantFieldSource += "byte";
			break;
		case Constants.TYPE_char:
			constantFieldSource += "char";
			break;
		case Constants.TYPE_Class:
			constantFieldSource += "Class";
			break;
		case Constants.TYPE_double:
			constantFieldSource += "double";
			break;
		case Constants.TYPE_Error:
			constantFieldSource += "Error";
			break;
		case Constants.TYPE_Exception:
			constantFieldSource += "Exception";
			break;
		case Constants.TYPE_float:
			constantFieldSource += "float";
			break;
		case Constants.TYPE_int:
			constantFieldSource += "int";
			break;
		case Constants.TYPE_long:
			constantFieldSource += "long";
			break;
		case Constants.TYPE_MethodHandle:
			constantFieldSource += "MethodHandle";
			break;
		case Constants.TYPE_MethodType:
			constantFieldSource += "MethodType";
			break;
		case Constants.TYPE_null:
			constantFieldSource += "null";
			break;
		case Constants.TYPE_Object:
			constantFieldSource += "Object";
			break;
		case Constants.TYPE_RuntimeException:
			constantFieldSource += "RuntimeException";
			break;
		case Constants.TYPE_short:
			constantFieldSource += "short";
			break;
		case Constants.TYPE_String:
			constantFieldSource += "String";
			break;
		case Constants.TYPE_Throwable:
			constantFieldSource += "Throwable";
			break;
		case Constants.TYPE_unknown:
			constantFieldSource += "unknown";
			break;
		case Constants.TYPE_void:
			constantFieldSource += "void";
			break;
		default:
			return "\"" + type + "\"";
//			throw new UnexpectedException("type " + type + " not found in WALA Constants");
		}
		return constantFieldSource;
	}

	public static Map<Integer, Set<String>> getInstructionExceptions(IInstruction[] instructions, ExceptionHandler[][] exceptionHandlers) {
		// Precompute exception handlers
		Map<Integer, Set<String>> instructionExceptions = new HashMap<>();
		for (int index = 0; index < instructions.length; index++) {
			// Handle exceptions
			ExceptionHandler[] instructionExceptionHandlers = exceptionHandlers[index];
			for (ExceptionHandler instructionExceptionHandler : instructionExceptionHandlers) {
				int handler = instructionExceptionHandler.getHandler();
				String catchClass = instructionExceptionHandler.getCatchClass();
				if (Objects.isNull(catchClass)) {
					// TODO Meaningful?
					catchClass = "";
				}

				if (!instructionExceptions.containsKey(handler)) {
					instructionExceptions.put(handler, Set.of(catchClass));
				} else {
					Set<String> exceptionClasses = new LinkedHashSet<>(instructionExceptions.get(handler));
					exceptionClasses.add(catchClass);
					instructionExceptions.replace(handler, exceptionClasses);
				}
			}
		}
		return instructionExceptions;
	}
}
