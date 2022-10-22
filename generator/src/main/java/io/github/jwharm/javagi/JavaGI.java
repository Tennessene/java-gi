package io.github.jwharm.javagi;

import io.github.jwharm.javagi.generator.*;
import io.github.jwharm.javagi.model.Repository;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.nio.file.Path;
import java.util.*;

public class JavaGI {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("ERROR: No input file provided.");
            return;
        }
        String inputFile = args[0];
        
        if (args.length == 1) {
            System.err.println("ERROR: No output directory provided.");
            return;
        }
        String outputDir = args[1];

        run(inputFile, outputDir);
    }

    public static void run(String inputFile, String outputDir) throws Exception {
        List<Source> toGenerate = new ArrayList<>();
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        parser.parse(inputFile, new DefaultHandler() {
            @Override
            public void startElement(String uri, String lName, String qName, Attributes attr) {
                if ("repository".equals(qName)) {
                    toGenerate.add(new Source(attr.getValue("path"), attr.getValue("package"), Set.of(), Path.of(outputDir), PatchSet.EMPTY));
                }
            }
        });
        generate(toGenerate.toArray(Source[]::new));
    }

    public static void generate(Source... sources) throws Exception {
        GirParser parser = new GirParser();
        BindingsGenerator generator = new BindingsGenerator();

        Map<String, Repository> repositories = new LinkedHashMap<>();
        Map<String, Parsed> parsed = new HashMap<>();

        for (Source source : sources) {
            System.out.println("PARSE " + source.path());
            Repository r = parser.parse(source.path(), source.pkg());
            repositories.put(r.namespace.name, r);
            parsed.put(r.namespace.name, new Parsed(r, source.natives, source.outputDir, source.patches));
        }

        System.out.println("LINK " + parsed.size() + " REPOSITORIES");
        CrossReference.link(repositories);
        Conversions.cIdentifierLookupTable = CrossReference.createIdLookupTable(repositories);
        Conversions.cTypeLookupTable = CrossReference.createCTypeLookupTable(repositories);

        System.out.println("APPLY PATCHES");
        for (Parsed p : parsed.values()) {
            p.patches.patch(p.repository);
        }

        for (Parsed p : parsed.values()) {
            Path basePath = p.outputDir.resolve(p.repository.namespace.pathName);
            System.out.println("GENERATE " + p.repository.namespace.name + " to " + basePath);
            generator.generate(p.repository, p.natives, basePath);
        }
    }

    public record Source(String path, String pkg, Set<String> natives, Path outputDir, PatchSet patches) {}
    private record Parsed(Repository repository, Set<String> natives, Path outputDir, PatchSet patches) {}
}
