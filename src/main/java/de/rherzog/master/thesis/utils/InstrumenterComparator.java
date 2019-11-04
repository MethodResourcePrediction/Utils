package de.rherzog.master.thesis.utils;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.Util;
import com.ibm.wala.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.StringStuff;

public class InstrumenterComparator {
	private MethodReference methodReference;
	private TypeName classTypeName;

	public static InstrumenterComparator of(String methodSignature) {
		if (methodSignature.startsWith("L")) {
			methodSignature = methodSignature.substring(1);
		}
		methodSignature = methodSignature.replaceFirst(";", "");
		return new InstrumenterComparator(StringStuff.makeMethodReference(methodSignature));
	}

	public InstrumenterComparator(MethodReference methodReference) {
		this.methodReference = methodReference;
		this.classTypeName = methodReference.getDeclaringClass().getName();
	}

	public boolean equals(MethodData md) {
		String mrClassName = classTypeName.toString();

		String mdClassType = md.getClassType().toString().replaceFirst(";", "");
//		if (mdClassType.startsWith("L")) {
//			mdClassType = mdClassType.substring(1);
//		}

		if (!mdClassType.equals(mrClassName)) {
			return false;
		}
		if (!methodReference.getName().toString().equals(md.getName())) {
			return false;
		}
		if (!md.getSignature().equals(methodReference.getSelector().getDescriptor().toString())) {
			return false;
		}
		return true;
	}

	public boolean equals(IMethod method) {
		return method.getReference().equals(methodReference);
//		// Lspec/benchmarks/crypto/signverify/Main;.harnessMain()V
//		String className = method.getDeclaringClass().getReference().getName().toString();
//
//		StringBuilder builder = new StringBuilder();
//		MethodReference reference = method.getReference();
//		int numberOfParameters = reference.getNumberOfParameters();
//		for (int i = 0; i < numberOfParameters; i++) {
//			TypeReference parameterTypeReference = reference.getParameterType(i);
//			String parameterType = parameterTypeReference.getName().toString();
//			builder.append(parameterType);
//			if (parameterType.contains("L") || parameterType.contains("[L")) {
//				builder.append(";");
//			}
//		}
//		String parameterTypes = builder.toString();
//		String returnType = reference.getReturnType().getName().toString();
//
//		StringBuilder methodSignatureBuilder = new StringBuilder();
//
//		methodSignatureBuilder.append(className.substring(1).replaceAll("/", "."));
//		methodSignatureBuilder.append("." + reference.getName().toString());
//		methodSignatureBuilder.append("(" + parameterTypes + ")");
//		methodSignatureBuilder.append(returnType);
//
//		String methodSignature = methodSignatureBuilder.toString();
//		return methodSignature.equals(this.methodSignature);
	}

	public boolean equals(ClassInstrumenter classInstrumenter) {
		String className = null;
		try {
			className = classInstrumenter.getReader().getName();
		} catch (InvalidClassFileException e) {
			return false;
		}
		String classType = Util.makeType(className);
		String referenceClassName = methodReference.getDeclaringClass().getName().toString();
		String referenceClassType = Util.makeType(referenceClassName.substring(1));
		return classType.equals(referenceClassType);
	}

	public MethodReference getMethodReference() {
		return methodReference;
	}

	public TypeName getClassTypeName() {
		return classTypeName;
	}
}
