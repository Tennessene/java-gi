/* Java-GI - Java language bindings for GObject-Introspection-based libraries
 * Copyright (C) 2022-2023 Jan-Willem Harmannij
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package io.github.jwharm.javagi.generator;

import io.github.jwharm.javagi.model.Class;
import io.github.jwharm.javagi.model.Module;
import io.github.jwharm.javagi.model.Package;
import io.github.jwharm.javagi.model.Record;
import io.github.jwharm.javagi.model.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The GirParser class is a simple SAX parser that creates a tree of "GirElement" objects 
 * that (roughly) corresponds to the XML elements in the GIR file.
 * Most of the work is done in the startElement() callback. The endElement() callback is 
 * mostly used to set the docstrings into the currently open Doc element.
 */
public class GirParser extends DefaultHandler {

    private final Path sourceDir;
    private final SAXParser parser;
    private final Module module;
    private boolean generate = true;

    private StringBuilder chars;
    private GirElement current;
    private String skip;

    /**
     * Reset the parser
     */
    @Override
    public void startDocument() {
        chars = new StringBuilder();
        current = null;
        skip = null;
    }

    /**
     * Not used
     */
    @Override
    public void endDocument() {
    }

    /**
     * Create a GirElement for this XML element and its attributes, and add it to
     * the tree.
     */
    @Override
    public void startElement(String uri, String lName, String qName, Attributes attr) {
        if (skip != null) {
            return;
        }

        switch (qName) {
            case "alias" -> {
                Alias newAlias = new Alias(current, attr.getValue("name"), 
                        attr.getValue("c:type"), attr.getValue("glib:get-type"), attr.getValue("version"));
                current.aliasList.add(newAlias);
                current = newAlias;
            }
            case "array" -> {
                Array newArray = new Array(current, attr.getValue("name"), attr.getValue("c:type"),
                        attr.getValue("length"), attr.getValue("zero-terminated"), attr.getValue("fixed-size"));
                current.array = newArray;
                current = newArray;
            }
            case "attribute" -> {
                Attribute newAttribute = new Attribute(current, attr.getValue("name"),
                        attr.getValue("type"));
                current.attributeList.add(newAttribute);
                current = newAttribute;
            }
            case "bitfield" -> {
                Bitfield newBitfield = new Bitfield(current, attr.getValue("name"), 
                        attr.getValue("c:type"), attr.getValue("glib:get-type"), attr.getValue("version"));
                current.bitfieldList.add(newBitfield);
                current = newBitfield;

            }
            case "callback" -> {
                Callback newCallback = new Callback(current, attr.getValue("name"), 
                        attr.getValue("c:type"), attr.getValue("throws"), attr.getValue("version"));
                if (current instanceof Namespace ns) {
                    ns.callbackList.add(newCallback);
                } else if (current instanceof Field f) {
                    f.callback = newCallback;
                }
                current = newCallback;
            }
            case "class" -> {
                io.github.jwharm.javagi.model.Class newClass = new Class(current, attr.getValue("name"),
                        attr.getValue("parent"), attr.getValue("c:type"), attr.getValue("glib:type-name"), 
                        attr.getValue("glib:get-type"), attr.getValue("glib:type-struct"), 
                        attr.getValue("glib:get-value-func"), attr.getValue("glib:set-value-func"), 
                        attr.getValue("version"), attr.getValue("abstract"), attr.getValue("final"));
                current.classList.add(newClass);
                current = newClass;
            }
            case "constant" -> {
                Constant newConstant = new Constant(current, attr.getValue("name"),
                        attr.getValue("value"), attr.getValue("c:type"));
                current.constantList.add(newConstant);
                current = newConstant;
            }
            case "constructor" -> {
                Constructor newConstructor = new Constructor(current, attr.getValue("name"),
                        attr.getValue("c:identifier"), attr.getValue("deprecated"),
                        attr.getValue("throws"));
                current.constructorList.add(newConstructor);
                current = newConstructor;
            }
            case "docsection" -> {
                Docsection newDocsection = new Docsection(current, attr.getValue("name"));
                current.docsectionList.add(newDocsection);
                current = newDocsection;
            }
            case "doc" -> {
                Doc newDoc = new Doc(current, attr.getValue("xml:space"));
                current.doc = newDoc;
                current = newDoc;
            }
            case "doc-deprecated" -> {
                DocDeprecated newDocDeprecated = new DocDeprecated(current, attr.getValue("xml:space"));
                current.docDeprecated = newDocDeprecated;
                current = newDocDeprecated;
            }
            case "doc-version" -> {
                DocVersion newDocVersion = new DocVersion(current, attr.getValue("xml:space"));
                current.docVersion = newDocVersion;
                current = newDocVersion;
            }
            case "enumeration" -> {
                Enumeration newEnumeration = new Enumeration(current, attr.getValue("name"),
                        attr.getValue("c:type"), attr.getValue("glib:get-type"), attr.getValue("version"));
                current.enumerationList.add(newEnumeration);
                current = newEnumeration;
            }
            case "field" -> {
                Field newField = new Field(current, attr.getValue("name"), attr.getValue("readable"),
                        attr.getValue("private"));
                current.fieldList.add(newField);
                current = newField;
            }
            case "function" -> {
                Function newFunction = new Function(current, attr.getValue("name"),
                        attr.getValue("c:identifier"), attr.getValue("deprecated"),
                        attr.getValue("throws"), attr.getValue("moved-to"));
                if (current instanceof Namespace ns) {
                    ns.functionList.add(newFunction);
                } else if (current instanceof RegisteredType c) {
                    c.functionList.add(newFunction);
                }
                current = newFunction;
            }
            case "function-macro" -> {
                FunctionMacro newFunctionMacro = new FunctionMacro(current, attr.getValue("name"),
                        attr.getValue("c:identifier"), attr.getValue("introspectable"),
                        attr.getValue("deprecated"), attr.getValue("throws"));
                current.functionMacroList.add(newFunctionMacro);
                current = newFunctionMacro;
            }
            case "implements" -> {
                Implements newImplements = new Implements(current, attr.getValue("name"));
                current.implementsList.add(newImplements);
                current = newImplements;
            }
            case "include" -> {
                Include newInclude = new Include(current, attr.getValue("name"), attr.getValue("version"));
                current.includeList.add(newInclude);
                current = newInclude;
                // Recursively parse included gir files
                parseInclude(newInclude);
            }
            case "instance-parameter" -> {
                InstanceParameter newInstanceParameter = new InstanceParameter(current, attr.getValue("name"),
                        attr.getValue("transfer-ownership"), attr.getValue("nullable"),
                        attr.getValue("allow-none"));
                ((Parameters) current).parameterList.add(newInstanceParameter);
                current = newInstanceParameter;
            }
            case "interface" -> {
                Interface newInterface = new Interface(current, attr.getValue("name"),
                        attr.getValue("c:type"), attr.getValue("glib:type-name"), 
                        attr.getValue("glib:get-type"), attr.getValue("glib:type-struct"),
                        attr.getValue("version"));
                current.interfaceList.add(newInterface);
                current = newInterface;
            }
            case "member" -> {
                Member newMember = new Member(current, attr.getValue("name"),
                        attr.getValue("c:identifier"), attr.getValue("value"));
                current.memberList.add(newMember);
                current = newMember;
            }
            case "method" -> {
                if (attr.getValue("shadowed-by") != null) {
                    // Do not generate shadowed methods
                    skip = qName;
                } else {
                    Method newMethod = new Method(current, attr.getValue("name"),
                            attr.getValue("c:identifier"), attr.getValue("deprecated"),
                            attr.getValue("throws"), attr.getValue("shadowed-by"),
                            attr.getValue("shadows"), attr.getValue("moved-to"));
                    current.methodList.add(newMethod);
                    current = newMethod;
                }
            }
            case "namespace" -> {
                Namespace newNamespace = new Namespace(current, attr.getValue("name"), attr.getValue("version"),
                        attr.getValue("shared-library"), attr.getValue("c:identifier-prefixes"),
                        attr.getValue("c:symbol-prefixes"));
                ((Repository) current).namespace = newNamespace;
                current = newNamespace;
            }
            case "package" -> {
                io.github.jwharm.javagi.model.Package newPackage = new Package(current, attr.getValue("name"));
                ((Repository) current).package_ = newPackage;
                current = newPackage;
            }
            case "parameter" -> {
                Parameter newParameter = new Parameter(current, attr.getValue("name"),
                        attr.getValue("transfer-ownership"), attr.getValue("nullable"),
                        attr.getValue("allow-none"), attr.getValue("optional"),
                        attr.getValue("direction"), attr.getValue("closure"),
                        attr.getValue("caller-allocates"));
                ((Parameters) current).parameterList.add(newParameter);
                current = newParameter;
            }
            case "parameters" -> {
                Parameters newParameters = new Parameters(current);
                ((CallableType) current).setParameters(newParameters);
                current = newParameters;
            }
            case "prerequisite" -> {
                Prerequisite newPrerequisite = new Prerequisite(current, attr.getValue("name"));
                ((Interface) current).prerequisite = newPrerequisite;
                current = newPrerequisite;
            }
            case "property" -> {
                Property newProperty = new Property(current, attr.getValue("name"),
                        attr.getValue("transfer-ownership"), attr.getValue("getter"));
                current.propertyList.add(newProperty);
                current = newProperty;
            }
            case "record" -> {
                Record newRecord = new Record(current, attr.getValue("name"),
                        attr.getValue("c:type"), attr.getValue("glib:get-type"), attr.getValue("version"),
                        attr.getValue("disguised"), attr.getValue("glib:is-gtype-struct-for"),
                        attr.getValue("foreign"));
                current.recordList.add(newRecord);
                current = newRecord;
            }
            case "repository" -> {
                current = new Repository(module, this.generate);
            }
            case "return-value" -> {
                ReturnValue newReturnValue = new ReturnValue(current, attr.getValue("transfer-ownership"),
                        attr.getValue("nullable"));
                ((CallableType) current).setReturnValue(newReturnValue);
                current = newReturnValue;
            }
            case "glib:signal" -> {
                Signal newSignal = new Signal(current, attr.getValue("name"), attr.getValue("when"),
                        attr.getValue("detailed"), attr.getValue("deprecated"), attr.getValue("throws"));
                current.signalList.add(newSignal);
                current = newSignal;
            }
            case "type" -> {
                Type newType = new Type(current, attr.getValue("name"), attr.getValue("c:type"));
                current.type = newType;
                current = newType;
            }
            case "union" -> {
                Union newUnion = new Union(current, attr.getValue("name"), attr.getValue("glib:get-type"),
                        attr.getValue("c:type"), attr.getValue("version"));
                current.unionList.add(newUnion);
                current = newUnion;
            }
            case "varargs" -> {
                ((Parameter) current).varargs = true;
                skip = qName;
            }
            case "virtual-method" -> {
                VirtualMethod newVirtualMethod = new VirtualMethod(current, attr.getValue("name"),
                        attr.getValue("deprecated"), attr.getValue("throws"));
                current.virtualMethodList.add(newVirtualMethod);
                current = newVirtualMethod;
            }
            case "glib:boxed", "c:include", "source-position" -> {
                // Ignored
                skip = qName;
            }
            default -> {
                System.out.println("WARNING: Unhandled gir element: " + qName);
                skip = qName;
            }
        }
    }

    /**
     * The characters callback is not guaranteed to receive the complete text inside
     * the currently open XML element in one call. So we append the chars to a 
     * StringBuilder, and save the complete text in the endElement() callback.
     */
    @Override
    public void characters(char[] ch, int start, int length) {
        chars.append(ch, start, length);
    }

    /**
     * Save the text (docstring) into the currently open Doc instance.
     */
    @Override
    public void endElement(String uri, String localName, String qName) {
        if (!(chars.isEmpty() || chars.toString().isBlank())) {
            String characters = chars.toString();
            chars.setLength(0);
            if (current instanceof Doc doc) {
                doc.contents = characters;
            }
        }
        if (qName.equals(skip)) {
            skip = null;
        } else if ((skip == null) && (! (current instanceof Repository))) {
            current = current.parent;
        }
    }

    /**
     * Recursively parse an included GIR file
     * @param include the include-element from the current GIR file
     */
    private void parseInclude(Include include) {
        // Check if this one has already been parsed
        if (module.repositories.containsKey(include.name)) {
            return;
        }

        try {
            // Recursively parse included gir files
            GirParser parser = new GirParser(sourceDir, module);
            parser.generate = false;
            Repository r = parser.parse(include.name + "-" + include.version + ".gir");

            // Add the repository to the module
            module.repositories.put(r.namespace.name, r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Setup a new XML parser to process a GIR file
     * @param sourceDir folder that contains the GIR files
     * @param module module to add repositories into
     * @throws ParserConfigurationException Indicates a serious SAX configuration error. Should not happen.
     * @throws SAXException Generic SAX error. Should not happen here.
     */
    public GirParser(Path sourceDir, Module module) throws ParserConfigurationException, SAXException {
        parser = SAXParserFactory.newInstance().newSAXParser();
        this.sourceDir = sourceDir;
        this.module = module;
    }

    /**
     * Parse the provided GIR file and create a tree of GirElement instances that
     * represents the GI repository.
     *
     * @param girFile Filename of the GIR file
     * @return The GI repository tree of GirElement instances
     * @throws IOException  If an error is encountered while reading the GIR file
     * @throws SAXException If an error is encountered while parsing the XML in the GIR file
     */
    public Repository parse(String girFile) throws IOException, SAXException {
        Path source = sourceDir.resolve(girFile);
        if (!Files.exists(source)) {
            throw new IOException("Specified GIR file does not exist: " + source);
        }
        try (InputStream is = Files.newInputStream(source)) {
            parser.parse(is, this);
        }
        return (Repository) current;
    }
}