package de.uniks.vs.methodresourceprediction.utils.test;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class UtilitiesTest {

  @Test
  public void testGetCTMethodbySignature() throws ClassHierarchyException, IOException {
    final String PREFIX = "org.gradle.wrapper.";

    List<String> methodSignatures =
        Utilities.getMethodSignatures(
            "Java60RegressionExclusions.txt", "gradle/wrapper/gradle-wrapper.jar");
    Objects.requireNonNull(methodSignatures);
    Assert.assertTrue(methodSignatures.size() > 0);

    for (String methodSignature : methodSignatures) {
      if (methodSignature.startsWith(PREFIX)) {
        System.out.println(methodSignature);
      }
    }
  }
}
