package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Conversions;

import java.io.IOException;
import java.io.Writer;

public class Signal extends Method implements Closure {

    public final String when;
    private final String signalName;
    private final String qualifiedName;
    public boolean detailed;
    

    public Signal(GirElement parent, String name, String when, String detailed, String deprecated, String throws_) {
        super(parent, name, null, deprecated, throws_, null, null);
        this.when = when;
        this.detailed = "1".equals(detailed);

        String className = ((RegisteredType) parent).javaName;
        signalName = Conversions.toSimpleJavaType(name, getNamespace());
        qualifiedName = className + "." + signalName;

        // Add source parameter
        if (parameters == null) {
            parameters = new Parameters(this);
        }
        String paramName = "source" + className;
        Parameter p = new Parameter(parameters, paramName, "none", "0", "0", "0", null);
        p.type = new Type(p, className, ((RegisteredType) parent).cType);
        p.signalSource = true;
        parameters.parameterList.add(0, p);
    }

    public void generate(Writer writer, boolean isDefault) throws IOException {
        writer.write("    \n");
        generateFunctionalInterface(writer, signalName, 1);
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
        writer.write("                handle(), Interop.allocateNativeString(\"" + name + "\"");
        if (detailed) {
            writer.write(" + ((detail == null || detail.isBlank()) ? \"\" : (\"::\" + detail))");
        }
        writer.write("), (Addressable) handler.toCallback(), (Addressable) MemoryAddress.NULL, (Addressable) MemoryAddress.NULL, 0);\n");
        writer.write("            return new Signal<>(handle(), RESULT);\n");
        writer.write("        } catch (Throwable ERR) {\n");
        writer.write("            throw new AssertionError(\"Unexpected exception occured: \", ERR);\n");
        writer.write("        }\n");
        writer.write("    }\n");
    }
}
