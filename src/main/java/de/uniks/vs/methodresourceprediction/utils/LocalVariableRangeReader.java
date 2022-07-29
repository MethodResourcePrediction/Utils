package de.uniks.vs.methodresourceprediction.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.annotations.TypeAnnotation;
import com.ibm.wala.util.collections.Pair;

public class LocalVariableRangeReader {
	private List<TypeAnnotation> typeAnnotations;
	private List<LocalVariableRange> localVariableRanges;

	public LocalVariableRangeReader(ShrikeCTMethod method) throws InvalidClassFileException {
		this(method.getTypeAnnotationsAtCode(false));
	}

	public LocalVariableRangeReader(TypeAnnotation annotation) {
		this(Arrays.asList(annotation));
	}

	public LocalVariableRangeReader(Collection<TypeAnnotation> annotations) {
		this(new ArrayList<>(annotations));
	}

	public LocalVariableRangeReader(List<TypeAnnotation> annotations) {
		this.typeAnnotations = annotations;

		getLocalVariableRanges();
	}

	public Pair<Double, Double> getRange(int varIndex) {
		Pair<Double, Double> range = null;
		for (LocalVariableRange localVariableRange : getLocalVariableRanges()) {
			if (localVariableRange.getVarIndex() == varIndex) {
				if (range != null) {
					throw new UnsupportedOperationException(
							"Duplicate annotation range found for varIndex " + varIndex);
				}
				range = localVariableRange.getRange();
			}
		}
		return range;
	}

	public List<LocalVariableRange> getLocalVariableRanges() {
		if (localVariableRanges != null) {
			return localVariableRanges;
		}

		localVariableRanges = new ArrayList<>(typeAnnotations.size());
		for (TypeAnnotation typeAnnotation : typeAnnotations) {
			try {
				LocalVariableRange localVariableRange = LocalVariableRange.of(typeAnnotation);
				localVariableRanges.add(localVariableRange);
			} catch (UnsupportedOperationException e) {
				// Not a Range annotation found
				continue;
			}
		}
		return localVariableRanges;
	}

}
