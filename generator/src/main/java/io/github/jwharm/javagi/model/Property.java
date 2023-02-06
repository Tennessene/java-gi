package io.github.jwharm.javagi.model;

import java.io.IOException;

import io.github.jwharm.javagi.generator.Conversions;
import io.github.jwharm.javagi.generator.SourceWriter;

public class Property extends Variable {

    public final String propertyName, transferOwnership, getter;

    public Property(GirElement parent, String name, String transferOwnership, String getter) {
        super(parent);
        this.propertyName = name;
        this.name = Conversions.toLowerCaseJavaName(name);
        this.transferOwnership = transferOwnership;
        this.getter = getter;
    }
    
    /**
     * Generate a setter method for use in a GObjectBuilder
     * @param writer The writer to the class file
     * @throws IOException Thrown when an exception occurs during writing
     */
    public void generate(SourceWriter writer) throws IOException {
        writer.write("\n");
        if (doc != null) {
            doc.generate(writer, false);
        }
        writer.write((parent instanceof Interface) ? "default " : "public ");
        writer.write("S set" + Conversions.toCamelCase(name, true) + "(");
        writeTypeAndName(writer, false);
        writer.write(") {\n");
        writer.write("    addBuilderProperty(\"" + propertyName + "\", org.gnome.gobject.Value.create(" + name + "));\n");
        writer.write("    return (S) this;\n");
        writer.write("}\n");
    }
}
