package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Conversions;

import java.io.IOException;
import java.io.Writer;

public class Class extends RegisteredType {
    
    public String typeStruct;

    public Class(GirElement parent, String name, String parentClass, String cType, String typeStruct, String version) {
        super(parent, name, parentClass, cType, version);
        this.typeStruct = typeStruct;
    }

    public void generate(Writer writer) throws IOException {
        generatePackageDeclaration(writer);
        generateImportStatements(writer);
        generateJavadoc(writer);

        writer.write("public class " + javaName);
        writer.write(" extends ");
        if (name.equals("Object")) {
            writer.write("io.github.jwharm.javagi.ProxyBase");
        } else if (parentClass == null) {
            writer.write("org.gtk.gobject.Object");
        } else {
            writer.write(parentClass);
        }
        for (int i = 0; i < implementsList.size(); i++) {
            if (i == 0) {
                writer.write(" implements " + Conversions.toQualifiedJavaType(implementsList.get(i).name, getNamespace().packageName));
            } else {
                writer.write(", " + Conversions.toQualifiedJavaType(implementsList.get(i).name, getNamespace().packageName));
            }
        }
        writer.write(" {\n");

        generateEnsureInitialized(writer);
        generateCType(writer);
        generateMemoryLayout(writer);
        for (Field f : fieldList) {
            f.generate(writer);
        }

        generateMemoryAddressConstructor(writer);
        generateCastFromGObject(writer);
        generateConstructors(writer);
        
        for (Method m : methodList) {
            m.generate(writer, false, false);
        }

        for (Function function : functionList) {
            function.generate(writer, false, true);
        }

        for (Signal s : signalList) {
            s.generate(writer, false);
        }

        generateDowncallHandles(writer);
        generateSignalCallbacks(writer);
        
        writer.write("}\n");
    }
}
