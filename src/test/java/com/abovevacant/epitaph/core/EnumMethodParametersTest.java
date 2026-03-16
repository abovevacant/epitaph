package com.abovevacant.epitaph.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class EnumMethodParametersTest {

  // Guards against the old D8/R8 crash triggered by enum constructor MethodParameters with null
  // names.
  @Test
  void enumConstructorsExposeNonNullParameterNames() throws IOException {
    List<Class<?>> enumClasses =
        Arrays.asList(Architecture.class, MemoryError.Tool.class, MemoryError.Type.class);

    List<String> failures = new ArrayList<>();
    for (Class<?> enumClass : enumClasses) {
      failures.addAll(validateEnumConstructorParameters(enumClass));
    }

    assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
  }

  private static List<String> validateEnumConstructorParameters(Class<?> enumClass)
      throws IOException {
    assertTrue(enumClass.isEnum(), () -> enumClass.getName() + " must be an enum");

    String resourceName = "/" + enumClass.getName().replace('.', '/') + ".class";
    InputStream classStream = enumClass.getResourceAsStream(resourceName);
    assertNotNull(classStream, () -> "Missing class resource: " + resourceName);

    try (InputStream in = classStream) {
      List<String> failures = new ArrayList<>();
      new ClassReader(in)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                  if (!"<init>".equals(name)) {
                    return null;
                  }

                  Type[] argumentTypes = Type.getArgumentTypes(descriptor);
                  int[] methodParameterCount = new int[] {0};

                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitParameter(String name, int access) {
                      if (name == null) {
                        failures.add(
                            enumClass.getName()
                                + " constructor "
                                + descriptor
                                + " has a null MethodParameters name at index "
                                + methodParameterCount[0]);
                      }
                      methodParameterCount[0]++;
                    }

                    @Override
                    public void visitEnd() {
                      if (methodParameterCount[0] != argumentTypes.length) {
                        failures.add(
                            enumClass.getName()
                                + " constructor "
                                + descriptor
                                + " exposes "
                                + methodParameterCount[0]
                                + " MethodParameters entries for "
                                + argumentTypes.length
                                + " parameters");
                      }
                    }
                  };
                }
              },
              ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
      return failures;
    }
  }
}
