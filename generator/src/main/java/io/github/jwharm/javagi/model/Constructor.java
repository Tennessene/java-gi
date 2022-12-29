package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Conversions;

import java.io.IOException;
import java.io.Writer;

public class Constructor extends Method {

    public Constructor(GirElement parent, String name, String cIdentifier, String deprecated, String throws_) {
        super(parent, name, cIdentifier, deprecated, throws_, null, null);
    }

    public void generate(Writer writer, boolean isInterface) throws IOException {
        String privateMethodName = generateConstructorHelper(writer);

        writer.write("    \n");
        if (doc != null) {
            doc.generate(writer, 1);
        }
        
        if ("1".equals(deprecated)) {
            writer.write("    @Deprecated\n");
        }
        
        writer.write("    public ");
        writer.write(((RegisteredType) parent).javaName);
        if (parameters != null) {
            writer.write("(");
            parameters.generateJavaParameters(writer, false);
            writer.write(")");
        } else {
            writer.write("()");
        }
        if (throws_ != null) {
            writer.write(" throws GErrorException");
        }
        writer.write(" {\n");
        
        if (! isSafeToBind()) {
            writer.write("        this(null, null); // avoid compiler error\n");
            writer.write("        throw new UnsupportedOperationException(\"Operation not supported yet\");\n");
            writer.write("    }\n");
            return;
        }
        
        writer.write("        super(" + privateMethodName);
        if (parameters != null) {
            writer.write("(");
            parameters.generateJavaParameterNames(writer);
            writer.write(")");
        } else {
            writer.write("()");
        }
        writer.write(", " + returnValue.transferOwnership() + ");\n");
        writer.write("    }\n");
    }

    public void generateNamed(Writer writer, boolean isInterface) throws IOException {
        RegisteredType clazz = (RegisteredType) parent;

        String privateMethodName = generateConstructorHelper(writer);

        writer.write("    \n");
        if (doc != null) {
            doc.generate(writer, 1);
        }
        
        if ("1".equals(deprecated)) {
            writer.write("    @Deprecated\n");
        }
        
        writer.write("    public static " + clazz.javaName + " " + Conversions.toLowerCaseJavaName(name));
        if (parameters != null) {
            writer.write("(");
            parameters.generateJavaParameters(writer, false);
            writer.write(")");
        } else {
            writer.write("()");
        }
        if (throws_ != null) {
            writer.write(" throws GErrorException");
        }
        writer.write(" {\n");
        
        if (! isSafeToBind()) {
            writer.write("        throw new UnsupportedOperationException(\"Operation not supported yet\");\n");
            writer.write("    }\n");
            return;
        }
        
        writer.write("        return new " + clazz.javaName + "(" + privateMethodName);
        if (parameters != null) {
            writer.write("(");
            parameters.generateJavaParameterNames(writer);
            writer.write(")");
        } else {
            writer.write("()");
        }
        writer.write(", " + returnValue.transferOwnership() + ");\n");
        writer.write("    }\n");
    }

    // Because constructors sometimes throw exceptions, we need to allocate a GError segment before
    // calling "super(..., GERROR)", which is not allowed - the super() call must
    // be the first statement in the constructor. Therefore, we always generate a private method that
    // prepares a GError memorysegment if necessary, calls the C API and throws the GErrorException
    // (if necessary). The "real" constructor just calls super(private_method());
    private String generateConstructorHelper(Writer writer) throws IOException {

        // Method name
        String methodName = "construct" + Conversions.toCamelCase(name, true);
        writer.write("    \n");
        writer.write("    private static Addressable " + methodName);

        // Parameters
        if (parameters != null) {
            writer.write("(");
            parameters.generateJavaParameters(writer, false);
            writer.write(")");
        } else {
            writer.write("()");
        }

        // Exceptions
        if (throws_ != null) {
            writer.write(" throws GErrorException");
        }
        writer.write(" {\n");
        
        // Currently unsupported constructor method: throw an exception
        if (! isSafeToBind()) {
            writer.write("        throw new UnsupportedOperationException(\"Operation not supported yet\");\n");
            writer.write("    }\n");
            return methodName;
        }
        
        // Generate preprocessing statements for all parameters
        if (parameters != null) {
            parameters.generatePreprocessing(writer, 2);
        }

        // Allocate GError pointer
        if (throws_ != null) {
            writer.write("        MemorySegment GERROR = Interop.getAllocator().allocate(Interop.valueLayout.ADDRESS);\n");
        }
        
        // Generate the return type
        writer.write("        Addressable RESULT;\n");
        
        // The method call is wrapped in a try-catch block
        writer.write("        try {\n");

        // Invoke to the method handle
        writer.write("            RESULT = (MemoryAddress) DowncallHandles." + cIdentifier + ".invokeExact");
        
        // Marshall the parameters to the native types
        if (parameters != null) {
            writer.write("(");
            parameters.marshalJavaToNative(writer, throws_);
            writer.write(")");
        } else {
            writer.write("()");
        }
        writer.write(";\n");
        
        // If something goes wrong in the invokeExact() call
        writer.write("        } catch (Throwable ERR) {\n");
        writer.write("            throw new AssertionError(\"Unexpected exception occured: \", ERR);\n");
        writer.write("        }\n");
        
        // Throw GErrorException
        if (throws_ != null) {
            writer.write("        if (GErrorException.isErrorSet(GERROR)) {\n");
            writer.write("            throw new GErrorException(GERROR);\n");
            writer.write("        }\n");
        }
        
        // Generate post-processing actions for parameters
        if (parameters != null) {
            parameters.generatePostprocessing(writer, 2);
        }
        
        writer.write("        return RESULT;\n");
        writer.write("    }\n");
        return methodName;
    }
}
