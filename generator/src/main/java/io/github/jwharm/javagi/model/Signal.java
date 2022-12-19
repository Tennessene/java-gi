package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Conversions;

import java.io.IOException;
import java.io.Writer;

public class Signal extends Method {

    public final String when;
    public boolean detailed;
    
    private String signalName, className, qualifiedName, callbackName;
    private boolean returnsBool;

    public Signal(GirElement parent, String name, String when, String detailed, String deprecated, String throws_) {
        super(parent, name, null, deprecated, throws_);
        this.when = when;
        this.detailed = "1".equals(detailed);
    }

    public void generate(Writer writer, boolean isDefault) throws IOException {
        signalName = Conversions.toSimpleJavaType(name, getNamespace());
        className = ((RegisteredType) parent).javaName;
        qualifiedName = className + "." + signalName;
        callbackName = "signal" + className + signalName;
        returnsBool = returnValue != null && returnValue.type.simpleJavaType.equals("boolean");

        generateFunctionalInterface(writer);
        generateSignalDeclaration(writer, isDefault);
    }
    
    // Generate the functional interface
    private void generateFunctionalInterface(Writer writer) throws IOException {
        writer.write("    \n");
        writer.write("    @FunctionalInterface\n");
        writer.write("    public interface " + signalName + " {\n");
        writer.write("        " + (returnsBool ? "boolean" : "void") + " signalReceived("
                + className + " source" + Conversions.toLowerCaseJavaName(className));

        if (parameters != null) {
            writer.write(", ");
            parameters.generateJavaParameters(writer, false);
        }
        writer.write(");\n");

        writer.write("    }\n");
    }

    // Generate the static callback method, that will run the handler method.
    public void generateStaticCallback(Writer writer, boolean isDefault) throws IOException {
        String implClassName = className;
        String source = "source" + Conversions.toLowerCaseJavaName(className);
        if (isDefault) {
            implClassName = className + "." + className + "Impl";
        }

        writer.write("        \n");
        writer.write("        public static " + (returnsBool ? "boolean " : "void ") + callbackName + "(MemoryAddress " + source);

        if (parameters != null) {
            for (Parameter p : parameters.parameterList) {
                writer.write (", " + Conversions.toPanamaJavaType(p.type));
                writer.write(" " + Conversions.toLowerCaseJavaName(p.name));
            }
        }
        writer.write(", MemoryAddress DATA) {\n");

        if (! isSafeToBind()) {
            writer.write("        // Operation not supported yet\n");
            if (returnsBool) writer.write("    return false;\n");
            writer.write("    }\n");
            return;
        }
        
        writer.write("            int HASH = DATA.get(Interop.valueLayout.C_INT, 0);\n");
        writer.write("            var HANDLER = (" + qualifiedName + ") Interop.signalRegistry.get(HASH);\n");
        writer.write("            " + (returnsBool ? "return " : "") + "HANDLER.signalReceived(new " + implClassName + "(" + source + ", Ownership.NONE)");

        if (parameters != null) {
            for (Parameter p : parameters.parameterList) {
                writer.write(", ");
                p.generateReverseInterop(writer, p.name, true);
            }
        }
        writer.write(");\n");

        writer.write("        }\n");
    }

    // Generate the method that connects the signal to the handler (defined by the functional interface above)
    private void generateSignalDeclaration(Writer writer, boolean isDefault) throws IOException {
        writer.write("    \n");
        if (doc != null) {
            doc.generate(writer, 1);
        }
        writer.write("    public " + (isDefault ? "default " : "") + "Signal<" + qualifiedName + "> on" + signalName + "(");
        
        // For detailed signals like GObject.notify::..., generate a String parameter to specify the detailed signal
        if (detailed) {
            writer.write("@Nullable String detail, ");
        }
        
        writer.write(qualifiedName + " handler) {\n");
        
        if (! isSafeToBind()) {
            writer.write("        throw new UnsupportedOperationException(\"Operation not supported yet\");\n");
            writer.write("    }\n");
            return;
        }
        
        writer.write("        try {\n");
        writer.write("            var RESULT = (long) Interop.g_signal_connect_data.invokeExact(\n");
        writer.write("                handle(),\n");
        writer.write("                Interop.allocateNativeString(\"" + name + "\"");
        if (detailed) {
            writer.write(" + ((detail == null || detail.isBlank()) ? \"\" : (\"::\" + detail))");
        }
        writer.write("),\n");
        writer.write("                (Addressable) Linker.nativeLinker().upcallStub(\n");
        writer.write("                    MethodHandles.lookup().findStatic(" + className + ".Callbacks.class, \"" + callbackName + "\",\n");
        writer.write("                        MethodType.methodType(");
        if (returnsBool) {
            writer.write("boolean.class, MemoryAddress.class");
        } else {
            writer.write("void.class, MemoryAddress.class");
        }
        if (parameters != null) {
            for (Parameter p : parameters.parameterList) {
                writer.write(", " + Conversions.toPanamaJavaType(p.type) + ".class");
            }
        }
        writer.write(", MemoryAddress.class)),\n");
        writer.write("                    FunctionDescriptor.");
        if (returnsBool) {
            writer.write("of(Interop.valueLayout.C_BOOLEAN, Interop.valueLayout.ADDRESS");
        } else {
            writer.write("ofVoid(Interop.valueLayout.ADDRESS");
        }
        if (parameters != null) {
            for (Parameter p : parameters.parameterList) {
                writer.write(", " + Conversions.toPanamaMemoryLayout(p.type));
            }
        }
        writer.write(", Interop.valueLayout.ADDRESS),\n");
        writer.write("                    Interop.getScope()),\n");
        writer.write("                Interop.registerCallback(handler),\n");
        writer.write("                (Addressable) MemoryAddress.NULL, 0);\n");
        writer.write("            return new Signal<" + qualifiedName + ">(handle(), RESULT);\n");
        writer.write("        } catch (Throwable ERR) {\n");
        writer.write("            throw new AssertionError(\"Unexpected exception occured: \", ERR);\n");
        writer.write("        }\n");
        writer.write("    }\n");
    }
}
