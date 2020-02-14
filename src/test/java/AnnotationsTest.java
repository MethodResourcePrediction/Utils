import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.annotations.TypeAnnotation;
import com.ibm.wala.util.config.AnalysisScopeReader;

import de.rherzog.master.thesis.utils.InstrumenterComparator;
import de.rherzog.master.thesis.utils.LocalVariableRange;
import de.rherzog.master.thesis.utils.LocalVariableRangeReader;

public class AnnotationsTest {
	final String inputJar = "../EvaluationPrograms.jar";
	final String signature = "LSleep;.test([Ljava/lang/String;)I";

//	final String inputJar = "/DATA/WALA/com.ibm.wala.core/com.ibm.wala.core.testdata_1.0.0.jar";
//	final String signature = "Lannotations/TypeAnnotatedClass1;.foo(ILjava/lang/Object;)Ljava/lang/Integer;";

	final String outputJar = "test.jar";

	public void annotation() throws IOException, ClassHierarchyException, InvalidClassFileException {
		// create an analysis scope representing the appJar as a J2SE application
		File regressionExclusionsFile = new File("Java60RegressionExclusions.txt");
		AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(inputJar, regressionExclusionsFile);

		// build a class hierarchy, call graph, and system dependence graph
		ClassHierarchy cha = ClassHierarchyFactory.make(scope);

		InstrumenterComparator comparator = InstrumenterComparator.of(signature);
		ShrikeCTMethod method = (ShrikeCTMethod) cha.resolveMethod(comparator.getMethodReference());

		System.out.println(method);

		System.out.println("Annotations ...");
//		method.getDeclaringClass().getAnnotations().forEach(System.out::println);

		Collection<TypeAnnotation> annotations = method.getTypeAnnotationsAtCode(false);

		LocalVariableRangeReader localVariableRangeReader = new LocalVariableRangeReader(annotations);
		for (LocalVariableRange localVariableRange : localVariableRangeReader.getLocalVariableRanges()) {
			System.out.println(localVariableRange);
		}
	}
}
