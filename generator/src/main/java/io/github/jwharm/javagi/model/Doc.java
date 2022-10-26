package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.GtkDoc;

import java.io.IOException;
import java.io.Writer;

public class Doc extends GirElement {
    public final String space;
    public String contents;

    public Doc(GirElement parent, String space) {
        super(parent);
        this.space = space;
    }

    public void generate(Writer writer, int indent) throws IOException {
        if (contents == null || contents.length() == 0) {
            return;
        }
        writer.write(" ".repeat(indent * 4) + "/**\n");
        
        // Convert GtkDoc to javadoc
        String javadoc = GtkDoc.getInstance().convert(this);
        
        // Write docstring
        writeDoc(writer, indent, javadoc, null);
        
        // Version
        if (parent instanceof RegisteredType rt) {
        	if (rt.version != null) {
        		writeDoc(writer, indent, rt.version, "@version");
        	}
        }
        
        if (parent instanceof CallableType ct
        		&& (! (parent instanceof Callback || parent instanceof Signal))) {
        	
        	// Param
        	Parameters parameters = ct.getParameters();
        	if (parameters != null) {
        		for (Parameter p : parameters.parameterList) {
                    if (p.isInstanceParameter()) {
                        continue;
                    }
                    if (parameters.hasCallbackParameter() && (p.isUserDataParameter() || p.isDestroyNotify())) {
                        continue;
                    }
                    // Varargs not yet supported
                    if (p.varargs) {
                    	continue;
                    }
                	if (p.doc != null) {
                		String pJavadoc = GtkDoc.getInstance().convert(p.doc);
                		writeDoc(writer, indent, pJavadoc, "@param " + p.name);
                	}
        		}
        	}
        	
        	// Return (except for constructors)
        	if (! (parent instanceof Constructor c && c.name.equals("new"))) {
            	ReturnValue rv = ct.getReturnValue();
            	if (rv != null && rv.doc != null) {
            		String rvJavadoc = GtkDoc.getInstance().convert(rv.doc);
            		writeDoc(writer, indent, rvJavadoc, "@return");
            	}
        	}
            
            // Exception
            if (parent instanceof Method m) {
            	if ("1".equals(m.throws_)) {
            		writeDoc(writer, indent, "GErrorException {@see GLib.GError}", "@throws");
            	}
            }
        }
        
        // Deprecated
        if (parent instanceof Method m && "1".equals(m.deprecated)) {
        	if (parent.docDeprecated != null) {
        		String deprecatedJavadoc = GtkDoc.getInstance().convert(parent.docDeprecated);
        		writeDoc(writer, indent, deprecatedJavadoc, "@deprecated");
        	}
        }
        
        writer.write(" ".repeat(indent * 4) + " */\n");
    }
    
    private void writeDoc(Writer writer, int indent, String javadoc, String tag) throws IOException {
        
    	// Write the lines (starting each line with " * ")
    	int count = 0;
        for (String line : javadoc.trim().lines().toList()) {
            String escapedLine = line.replace("\\", "\\\\");
            
            // Write tag (optional)
        	if (count == 0 && tag != null) {
        		escapedLine = tag + " " + escapedLine;
        	}
            writer.write(" ".repeat(indent * 4) + " * " + escapedLine + "\n");
            count++;
        }
    }
}
