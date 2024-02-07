/* Java-GI - Java language bindings for GObject-Introspection-based libraries
 * Copyright (C) 2022-2024 the Java-GI developers
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

package io.github.jwharm.javagi.generators;

import com.squareup.javapoet.*;
import io.github.jwharm.javagi.configuration.ClassNames;
import io.github.jwharm.javagi.gir.Class;
import io.github.jwharm.javagi.gir.Record;
import io.github.jwharm.javagi.gir.*;
import io.github.jwharm.javagi.util.Conversions;
import io.github.jwharm.javagi.util.PartialStatement;

import javax.lang.model.element.Modifier;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import static io.github.jwharm.javagi.util.Conversions.*;

class TypedValueGenerator {

    protected final TypedValue v;
    protected final Array array;
    protected final Type type;
    protected final RegisteredType target;

    TypedValueGenerator(TypedValue v) {
        this.v = v;
        this.array = v.anyType() instanceof Array a ? a : null;
        this.type = v.anyType() instanceof Type t ? t : null;
        this.target = type != null ? type.get() : null;
    }

    /**
     * We cannot null-check primitive values.
     * @return true if this parameter is not a primitive value
     */
    boolean checkNull() {
        if (v instanceof InstanceParameter) return false;

        if (v instanceof Parameter p &&
                (p.notNull()
                    || p.varargs()
                    || p.isErrorParameter()
                    || p.isUserDataParameter()
                    || p.isDestroyNotifyParameter()
                    || p.isArrayLengthParameter()))
            return false;

        return ! (type != null
                    && !type.isPointer()
                    && (type.isPrimitive()
                        || (target instanceof Alias a && a.type().isPrimitive())
                        || target instanceof FlaggedType));
    }

    TypeName getType() {
        if (type != null && type.isActuallyAnArray())
            return ArrayTypeName.of(getType(type));

        if (v instanceof Field f && f.callback() != null)
            return f.parent().typeName().nestedClass(
                    toJavaSimpleType(f.name() + "_callback", f.namespace()));

        return getType(v.anyType());
    }

    private TypeName getType(AnyType anyType) {
        if (v instanceof Parameter p && p.isOutParameter())
            return ParameterizedTypeName.get(
                    ClassNames.OUT,
                    anyType.typeName().box()
            );

        if (type != null
                && type.isPointer()
                && (type.isPrimitive() || target instanceof FlaggedType))
            return TypeName.get(MemorySegment.class);

        return anyType.typeName();
    }

    String getName() {
        return "...".equals(v.name())
                ? "varargs"
                : Conversions.toJavaIdentifier(v.name());
    }

    PartialStatement marshalJavaToNative(String identifier) {
        if (type != null)
            return marshalJavaToNative(type, identifier);

        if (array != null && array.anyType() instanceof Array)
            return PartialStatement.of(
                    "$memorySegment:T.NULL /* unsupported */",
                    "memorySegment", MemorySegment.class);

        if (array != null && array.anyType() instanceof Type)
            return marshalJavaArrayToNative(array, identifier);

        if (v instanceof Field f && f.callback() != null)
            return PartialStatement.of(identifier + ".toCallback(_arena)");

        return PartialStatement.of(
                "$memorySegment:T.NULL /* unsupported */",
                "memorySegment", MemorySegment.class);
    }

    private PartialStatement marshalJavaToNative(Type type, String identifier) {
        if (type.isActuallyAnArray())
            return PartialStatement.of(
                    "$interop:T.allocateNativeArray(" + identifier + ", false, _arena)",
                    "interop", ClassNames.INTEROP);

        if (type.javaType().equals("java.lang.String"))
            return PartialStatement.of(
                    "$interop:T.allocateNativeString(" + identifier + ", _arena)",
                    "interop", ClassNames.INTEROP);

        if (! (v instanceof Parameter p && p.isOutParameter()))
            if (type.cType() != null && type.cType().endsWith("**"))
                return PartialStatement.of(identifier);

        if (type.javaType().equals("java.lang.foreign.MemorySegment"))
            return PartialStatement.of(identifier);

        if (type.isPointer() && (type.isPrimitive() || target instanceof FlaggedType))
            return PartialStatement.of(identifier);

        if (type.isBoolean())
            return PartialStatement.of(identifier + " ? 1 : 0");

        if (target != null)
            return PartialStatement.of(
                    target.getInteropString(identifier, type.isPointer(), Scope.ofTypedValue(v))
            );

        return PartialStatement.of(identifier);
    }

    private PartialStatement marshalJavaArrayToNative(Array array, String identifier) {
        // When ownership is transferred, we must not free the allocated memory -> use global scope
        String allocator = (v instanceof Parameter p && p.transferOwnership() == TransferOwnership.FULL)
                ? "$arena:T.global()" : "_arena";

        Type type = (Type) array.anyType();
        RegisteredType target = type.get();

        boolean isBitfield = target instanceof Bitfield;
        boolean isEnumeration = target instanceof Enumeration;
        boolean isPrimitiveAlias = target instanceof Alias a && a.type().isPrimitive();

        String targetTypeTag =
                isEnumeration ? "enumeration" :
                isBitfield ? "bitfield" :
                type.toTypeTag();

        String primitiveClassName = isPrimitiveAlias
                ? Conversions.primitiveClassName(((Alias) target).type().javaType())
                : "";

        if (isBitfield || isEnumeration || isPrimitiveAlias) {
            return PartialStatement.of(
                    "$interop:T.allocateNativeArray($" + targetTypeTag + ":T.get" + primitiveClassName + "Values(" + identifier + "), " + array.zeroTerminated() + ", " + allocator + ")",
                    "arena", Arena.class,
                    "interop", ClassNames.INTEROP,
                    targetTypeTag, isEnumeration ? ClassNames.ENUMERATION : isBitfield ? ClassNames.BITFIELD : type.typeName());
        }

        if (target instanceof Record && (!type.isPointer()))
            return PartialStatement.of(
                    "$interop:T.allocateNativeArray(" + identifier + ", $" + targetTypeTag + ":T.getMemoryLayout(), " + array.zeroTerminated() + ", " + allocator + ")",
                    "arena", Arena.class,
                    targetTypeTag, target.typeName(),
                    "interop", ClassNames.INTEROP);

        return PartialStatement.of(
                "$interop:T.allocateNativeArray(" + identifier + ", " + array.zeroTerminated() + ", " + allocator + ")",
                "arena", Arena.class,
                "interop", ClassNames.INTEROP);
    }

    PartialStatement marshalNativeToJava(String identifier, boolean upcall) {
        if (type != null && type.cType() != null && type.cType().equals("gfloat**"))
            return PartialStatement.of("null /* unsupported */");

        if (type != null) {
            if (type.isActuallyAnArray())
                return marshalNativeToJavaArray(type, null, identifier);

            if (type.isPointer()
                    && (type.isPrimitive()
                        || target instanceof Bitfield
                        || target instanceof Enumeration))
                return PartialStatement.of(identifier);

            return marshalNativeToJava(type, identifier, upcall);
        }

        if (array != null && array.anyType() instanceof Array)
            return PartialStatement.of("null /* unsupported */");

        if (array != null && array.anyType() instanceof Type arrayType)
            return marshalNativeToJavaArray(
                    arrayType,
                    array.sizeExpression(upcall),
                    identifier
            );

        return PartialStatement.of("null /* unsupported */");
    }

    PartialStatement marshalNativeToJava(Type type, String identifier, boolean upcall) {
        String free = switch(v) {
            case Parameter    p when  p.transferOwnership() == TransferOwnership.FULL -> "true";
            case ReturnValue rv when rv.transferOwnership() == TransferOwnership.FULL -> "true";
            default -> "false";
        };

        String targetTypeTag = target == null ? null : type.toTypeTag();

        boolean isTypeClass = target instanceof Record && "TypeClass".equals(target.name());

        if ("java.lang.String".equals(type.javaType()))
            return PartialStatement.of(
                    "$interop:T.getStringFrom(" + identifier + ", " + free + ")",
                    "interop", ClassNames.INTEROP);

        if (target instanceof Enumeration)
            return PartialStatement.of(
                    "$" + targetTypeTag + ":T.of(" + identifier + ")",
                    targetTypeTag, target.typeName());

        if ((target instanceof Record && (!isTypeClass))
                || target instanceof Union
                || (target instanceof Alias a && (a.type().get() instanceof Record)))
            return PartialStatement.of(
                    "$memorySegment:T.NULL.equals(" + identifier + ") ? null : new $" + targetTypeTag + ":T(" + identifier + ")",
                    "memorySegment", MemorySegment.class,
                    targetTypeTag, target.typeName());

        if (target instanceof Bitfield || target instanceof Alias a && a.type().isPrimitive())
            return PartialStatement.of(
                    "new $" + targetTypeTag + ":T(" + identifier + ")",
                    targetTypeTag, target.typeName());

        if (target instanceof Callback)
            return PartialStatement.of("null /* Unsupported parameter type */");

        boolean hasGType = target instanceof Class
                || target instanceof Interface
                || (target instanceof Alias a
                    && (a.type().get() instanceof Class ||
                        a.type().get() instanceof Interface));

        String cacheFunction =
                hasGType ? "getForType" :
                isTypeClass ? "getForTypeClass" :
                "get";

        String cache = upcall ? "false" : "true";

        if (target instanceof Class
                || target instanceof Interface
                || target instanceof Alias
                || isTypeClass)
            return PartialStatement.of(
                    "($" + targetTypeTag + ":T) $instanceCache:T." + cacheFunction + "(" + identifier + ", " + target.constructorName() + ", " + cache + ")",
                    targetTypeTag, target.typeName(),
                    "instanceCache", ClassNames.INSTANCE_CACHE);

        if (type.isBoolean())
            return PartialStatement.of(identifier + " != 0");

        return PartialStatement.of(identifier);
    }

    private PartialStatement marshalNativeToJavaArray(Type type, String size, String identifier) {
        String free = switch(v) {
            case Parameter    p when  p.transferOwnership() == TransferOwnership.FULL -> "true";
            case ReturnValue rv when rv.transferOwnership() == TransferOwnership.FULL -> "true";
            default -> "false";
        };

        RegisteredType target = type.get();
        String targetTypeTag = target != null ? type.toTypeTag() : null;

        String primitive = type.isPrimitive() ? Conversions.primitiveClassName(type.javaType()) : null;

        // GArray stores the length in the len field
        if (size == null
                && array != null
                && array.name() != null
                && List.of("GLib.Array", "GLib.PtrArray", "GLib.ByteArray").contains(array.name())) {
            size = "new $arrayType:T(" + identifier + ").readLen()";
        }

        // Null-terminated array
        if (size == null) {
            if ("java.lang.String".equals(type.javaType()))
                return PartialStatement.of(
                        "$interop:T.getStringArrayFrom(" + identifier + ", " + free + ")",
                        "interop", ClassNames.INTEROP);

            if ("java.lang.foreign.MemorySegment".equals(type.javaType()))
                return PartialStatement.of(
                        "$interop:T.getAddressArrayFrom(" + identifier + ", " + free + ")",
                        "interop", ClassNames.INTEROP);

            if (target instanceof FlaggedType)
                return PartialStatement.of(
                        "$interop:T.getArrayFromIntPointer(" + identifier + ", (int) $" + targetTypeTag + ":T.class, $" + targetTypeTag + ":T::of)",
                        "interop", ClassNames.INTEROP,
                        targetTypeTag, target.typeName());

            if (target instanceof Alias a && a.type().isPrimitive())
                return PartialStatement.of(
                        "$" + targetTypeTag + ":T.fromNativeArray(" + identifier + ", " + free + ")",
                        targetTypeTag, target.typeName());

            if (type.isPrimitive())
                return PartialStatement.of(
                        "$interop:T.get" + primitive + "ArrayFrom(" + identifier + ", _arena, " + free + ")",
                        "interop", ClassNames.INTEROP);

            if (target instanceof Record && (! type.isPointer()) &&
                    (! (array != null && "GLib.PtrArray".equals(array.name()))))
                return PartialStatement.of(
                        "$interop:T.getStructArrayFrom(" + identifier + ", $" + targetTypeTag + ":T.class, " + target.constructorName() + ", $" + targetTypeTag + ":T.getMemoryLayout())",
                        "interop", ClassNames.INTEROP,
                        targetTypeTag, target.typeName());

            if (target == null)
                throw new IllegalStateException("Target is null for type " + type);

            return PartialStatement.of(
                    "$interop:T.getProxyArrayFrom(" + identifier + ", $" + targetTypeTag + ":T.class, " + target.constructorName() + ")",
                    "interop", ClassNames.INTEROP,
                    targetTypeTag, target.typeName());
        }

        // Array with known size
        if ("java.lang.String".equals(type.javaType()))
            return PartialStatement.of(
                    "$interop:L.getStringArrayFrom(" + identifier + ", " + size + ", " + free + ")",
                    "interop", ClassNames.INTEROP,
                    "arrayType", array == null ? null : toJavaQualifiedType(array.name(), array.namespace()));

        if ("java.lang.foreign.MemorySegment".equals(type.javaType()))
            return PartialStatement.of(
                    "$interop:T.getAddressArrayFrom(" + identifier + ", " + size + ", " + free + ")",
                    "interop", ClassNames.INTEROP,
                    "arrayType", array == null ? null : toJavaQualifiedType(array.name(), array.namespace()));

        if (target instanceof FlaggedType)
            return PartialStatement.of(
                    "$interop:T.getArrayFromIntPointer(" + identifier + ", (int) " + size + ", $" + targetTypeTag + ":T.class, $" + targetTypeTag + ":T::of)",
                    "interop", ClassNames.INTEROP,
                    "arrayType", array == null ? null : toJavaQualifiedType(array.name(), array.namespace()),
                    targetTypeTag, target.typeName());

        if (target instanceof Alias a && a.type().isPrimitive())
            return PartialStatement.of(
                    "$targetType:T.fromNativeArray(" + identifier + ", " + size + ", " + free + ")",
                    "targetType", target.typeName(),
                    "arrayType", array == null ? null : toJavaQualifiedType(array.name(), array.namespace()));

        if (type.isPrimitive() && array != null && array.anyType() instanceof Type arrayType)
            return PartialStatement.of(
                    "$interop:T.get" + primitive + "ArrayFrom(" + identifier + ", " + size + ", _arena, " + free + ")",
                    "interop", ClassNames.INTEROP,
                    "arrayType", toJavaQualifiedType(array.name(), array.namespace()));

        if (target instanceof Record && (! type.isPointer()) &&
                (! (array != null && "GLib.PtrArray".equals(array.name()))))
            return PartialStatement.of(
                    "$interop:T.getStructArrayFrom(" + identifier + ", (int) " + size + ", $" + targetTypeTag + ":T.class, " + target.constructorName() + ", $" + targetTypeTag + ":T.getMemoryLayout())",
                    "interop", ClassNames.INTEROP,
                    "arrayType", array == null ? null : toJavaQualifiedType(array.name(), array.namespace()),
                    targetTypeTag, target.typeName());

        if (target == null)
            throw new IllegalStateException("Target is null for type " + type);

        return PartialStatement.of(
                "$interop:T.getProxyArrayFrom(" + identifier + ", (int) " + size + ", $" + targetTypeTag + ":T.class, " + target.constructorName() + ")",
                "interop", ClassNames.INTEROP,
                "arrayType", array == null ? null : toJavaQualifiedType(array.name(), array.namespace()),
                targetTypeTag, target.typeName());
    }

    PartialStatement getGTypeDeclaration() {
        if (array != null) {
            if (array.anyType() instanceof Type arrayType && "utf8".equals(arrayType.name()))
                return PartialStatement.of("$types:T.STRV", "types", ClassNames.TYPES);

            // Other array types are not supported yet, but could be added here
            return PartialStatement.of("$types:T.BOXED", "types", ClassNames.TYPES);
        }
        if (type == null)
            return PartialStatement.of("$types:T.BOXED", "types", ClassNames.TYPES);

        if (type.isPrimitive()) {
            return PartialStatement.of("$types:T." + switch(type.cType()) {
                case "gboolean" -> "BOOLEAN";
                case "gchar", "gint8" -> "CHAR";
                case "guchar", "guint8" -> "UCHAR";
                case "gint", "gint32" -> "INT";
                case "guint", "guint32", "gunichar" -> "UINT";
                case "glong" -> "LONG";
                case "gulong" -> "ULONG";
                case "gint64" -> "INT64";
                case "guint64" -> "UINT64";
                case "gpointer", "gconstpointer", "gssize", "gsize", "goffset", "gintptr", "guintptr" -> "POINTER";
                case "gdouble" -> "DOUBLE";
                case "gfloat" -> "FLOAT";
                case "none" -> "NONE";
                case "utf8", "filename" -> "STRING";
                default -> throw new IllegalStateException("Unexpected type: " + type.cType());
            }, "types", ClassNames.TYPES);
        }
        if (type.javaType().equals("java.lang.String"))
            return PartialStatement.of("$types:T.STRING", "types", ClassNames.TYPES);

        if (type.javaType().equals("java.lang.foreign.MemorySegment"))
            return PartialStatement.of("$types:T.POINTER", "types", ClassNames.TYPES);

        if (type.javaType().equals("org.gnome.gobject.GObject"))
            return PartialStatement.of("$types:T.OBJECT", "types", ClassNames.TYPES);

        RegisteredType rt = (target instanceof Alias a && (!a.type().isPrimitive()))
                ? a.type().get()
                : target;

        if (rt != null) {
            if (rt instanceof Class cls && cls.isInstanceOf("GObject", "ParamSpec"))
                return PartialStatement.of("$types:T.PARAM", "types", ClassNames.TYPES);

            if (rt.javaType().equals("org.gnome.glib.Variant"))
                return PartialStatement.of("$types:T.VARIANT", "types", ClassNames.TYPES);

            if (rt.getTypeFunc() != null) {
                String typeTag = type.toTypeTag();
                return PartialStatement.of("$" + typeTag + ":T.getType()", typeTag, type.typeName());
            }
        }

        if (type.javaType().equals("org.gnome.glib.Type"))
            return PartialStatement.of("$gobjects:T.gtypeGetType()", "gobjects",
                    ClassName.get("org.gnome.gobject", "GObjects"));

        return PartialStatement.of("$types:T.BOXED", "types", ClassNames.TYPES);
    }

    PartialStatement getValueSetter(PartialStatement gTypeDeclaration, String payloadIdentifier) {
        // First, check for fundamental classes with their own GValue setters
        if (type != null) {
            RegisteredType rt = target instanceof Alias a
                    ? a.type().get()
                    : target;

            if (rt instanceof Class cls && cls.setValueFunc() != null) {
                var setter = cls.namespace().functions().stream()
                        .filter(f -> f.attrs().cIdentifier().equals(cls.setValueFunc()))
                        .findAny();

                if (setter.isPresent()) {
                    Function function = setter.get();
                    String setValueFunc = Conversions.toJavaIdentifier(function.name());
                    String globalClassTag = rt.namespace().globalClassName();
                    return PartialStatement.of("$" + globalClassTag + ":T." + setValueFunc + "(_value, " + payloadIdentifier + ")",
                            globalClassTag,
                            toJavaQualifiedType(rt.namespace().globalClassName(),
                                    rt.namespace()));
                }
            }
        }

        // Other, known types
        String setValue = switch (gTypeDeclaration.format()) {
            case "$types:T.BOOLEAN" -> "setBoolean";
            case "$types:T.CHAR" -> "setSchar";
            case "$types:T.DOUBLE" -> "setDouble";
            case "$types:T.FLOAT" -> "setFloat";
            case "$types:T.INT" -> "setInt";
            case "$types:T.UINT" -> "setUint";
            case "$types:T.LONG" -> "setLong";
            case "$types:T.ULONG" -> "setUlong";
            case "$types:T.INT64" -> "setInt64";
            case "$types:T.UINT64" -> "setUint64";
            case "$types:T.STRING" -> "setString";
            case "$types:T.POINTER" -> "setPointer";
            case "$types:T.PARAM" -> "setParam";
            case "$types:T.VARIANT" -> "setVariant";
            case "$types:T.BOXED", "Types.STRV" -> "setBoxed";
            case "$gobjects:T.gtypeGetType()" -> "setGtype";
            default -> type == null ? "UNKNOWN"
                    : target instanceof Enumeration ? "setEnum"
                    : target instanceof Bitfield ? "setFlags"
                    : target instanceof Record ? "setBoxed"
                    : "setObject";
        };
        return switch(setValue) {
            case "setEnum", "setFlags" -> PartialStatement.of("_value" + "." + setValue + "(" + payloadIdentifier + ".getValue())");
            case "setBoxed" -> PartialStatement.of("_value" + "." + setValue + "(")
                        .add(marshalJavaToNative(payloadIdentifier))
                        .add(")");
            case "setObject" -> PartialStatement.of("_value" + "." + setValue + "(($gobject:T) " + payloadIdentifier + ")",
                    "gobject", ClassName.get("org.gnome.gobject", "GObject"));
            default -> PartialStatement.of("_value" + "." + setValue + "(" + payloadIdentifier + ")");
        };
    }

    FieldSpec generateConstantDeclaration() {
        final String value = ((Constant) v).value();
        try {
            TypeName typeName = target != null
                    ? target.typeName()
                    : type.typeName();

            var builder = FieldSpec.builder(
                    typeName,
                    toJavaConstant(v.name()),
                    Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL
            );

            if (v.infoElements().doc() != null)
                builder.addJavadoc(new DocGenerator(v.infoElements().doc()).generate());

            if (target instanceof Alias a && a.type().isPrimitive())
                builder.initializer("new $T($L)",
                        a.typeName(),
                        Conversions.literal(a.type().typeName(), value));
            else if (target instanceof FlaggedType)
                builder.initializer("new $T($L)",
                        target.typeName(),
                        Conversions.literal(TypeName.INT, value));
            else
                builder.initializer(
                        Conversions.literal(type.typeName(), value)
                                .replace("$", "$$"));

            return builder.build();

        } catch (NumberFormatException nfe) {
            // Do not write anything
            System.out.printf("Skipping <constant name=\"%s\" value=\"%s\">: Value not allowed%n",
                    v.name(),
                    value);
            return null;
        }
    }
}
