package de.uniks.vs.methodresourceprediction.utils.test;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.NewInstruction;
import com.ibm.wala.shrikeBT.Util;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class UtilitiesTest {

  @Test
  public void testGetCTMethodbySignature() throws ClassHierarchyException, IOException {
    final String PREFIX = "org.gradle.wrapper.";

    List<String> methodSignatures =
        Utilities.getMethodSignatures(
            "Java60RegressionExclusions.txt", "gradle/wrapper/gradle-wrapper.jar");
    Objects.requireNonNull(methodSignatures);
    assertTrue(methodSignatures.size() > 0);

    for (String methodSignature : methodSignatures) {
      if (methodSignature.startsWith(PREFIX)) {
        System.out.println(methodSignature);
      }
    }
  }

  @Test
  public void testInitializerInstruction() throws ClassNotFoundException {
    NewInstruction newInstruction = NewInstruction.make("Ljava/lang/Object;", 0);
    LoadInstruction loadInstruction = LoadInstruction.make("Ljava/lang/Object;", 0);

    InvokeInstruction invokeInstruction = Util.makeInvoke(OutputStream.class, "<init>");
    assertTrue(Utilities.isInitializerInstruction(loadInstruction, invokeInstruction));
    assertTrue(Utilities.isInitializerInstruction(newInstruction, invokeInstruction));
  }
}
