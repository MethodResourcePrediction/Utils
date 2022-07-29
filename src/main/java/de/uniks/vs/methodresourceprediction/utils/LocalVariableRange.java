package de.uniks.vs.methodresourceprediction.utils;

import java.util.Map;

import com.ibm.wala.shrike.shrikeCT.AnnotationsReader.ElementValue;
import com.ibm.wala.shrike.shrikeCT.TypeAnnotationsReader.TargetType;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.types.annotations.TypeAnnotation;
import com.ibm.wala.types.annotations.TypeAnnotation.LocalVarTarget;
import com.ibm.wala.util.collections.Pair;

import de.uniks.vs.methodresourceprediction.annotations.Range;

public class LocalVariableRange {
	private TypeAnnotation typeAnnotation;
	private TargetType targetType;
	private Annotation annotation;

	private Map<String, ElementValue> arguments;
	private Double min, max;
	private Integer varIndex;
	private LocalVarTarget localVarTarget;
	private String varName;

	public LocalVariableRange(TypeAnnotation typeAnnotation) {
		this.typeAnnotation = typeAnnotation;
		this.targetType = typeAnnotation.getTargetType();
		this.annotation = typeAnnotation.getAnnotation();

		// Check if annotation is located at a LOCAL_VARIABLE
		if (!TargetType.LOCAL_VARIABLE.equals(targetType)) {
			throw new UnsupportedOperationException(
					"type annotation target type must be " + TargetType.LOCAL_VARIABLE + " not " + targetType);
		}
		this.localVarTarget = (LocalVarTarget) typeAnnotation.getTypeAnnotationTarget();

		// Check if annotation has the correct class type
		String rangeTypeText = Utilities.classToType(Range.class);
		String annotationTypeNameText = annotation.getType().getName().toString() + ";";
		if (!rangeTypeText.equals(annotationTypeNameText)) {
			throw new UnsupportedOperationException(
					"annotation type name must be " + rangeTypeText + " not " + annotationTypeNameText);
		}
		this.arguments = annotation.getNamedArguments();
	}

	public int getVarIndex() {
		if (varIndex != null) {
			return varIndex;
		}
		varIndex = localVarTarget.getIndex();
		return varIndex;
	}

	public String getVarName() {
		if (varName != null) {
			return varName;
		}
		varName = localVarTarget.getName();
		return varName;
	}

	public double getMin() {
		if (min != null) {
			return min;
		}
		ElementValue elementValue = arguments.get("min");
		String valueString = elementValue.toString();
		min = Double.parseDouble(valueString);
		return min;
	}

	public double getMax() {
		if (max != null) {
			return max;
		}
		ElementValue elementValue = arguments.get("max");
		String valueString = elementValue.toString();
		max = Double.parseDouble(valueString);
		return max;
	}
	
	public Pair<Double, Double> getRange() {
		return Pair.make(getMin(), getMax());
	}

	public TypeAnnotation getTypeAnnotation() {
		return typeAnnotation;
	}

	public TargetType getTargetType() {
		return targetType;
	}

	public Annotation getAnnotation() {
		return annotation;
	}

	@Override
	public String toString() {
		return "LocalVariableRange [varIndex=" + getVarIndex() + ", name=" + getVarName() + ", min=" + getMin()
				+ ", max=" + getMax() + "]";
	}

	public static LocalVariableRange of(TypeAnnotation typeAnnotation) {
		return new LocalVariableRange(typeAnnotation);
	}
}
