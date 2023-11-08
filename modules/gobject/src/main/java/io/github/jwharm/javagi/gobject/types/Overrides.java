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

package io.github.jwharm.javagi.gobject.types;

import io.github.jwharm.javagi.base.Proxy;
import io.github.jwharm.javagi.interop.Interop;
import io.github.jwharm.javagi.interop.InteropException;
import org.gnome.glib.GLib;
import org.gnome.glib.LogLevelFlags;
import org.gnome.glib.Type;
import org.gnome.gobject.GObject;
import org.gnome.gobject.GObjects;
import org.gnome.gobject.TypeInterface;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.github.jwharm.javagi.Constants.LOG_DOMAIN;

/**
 * Helper class to register method overrides in a new GType
 */
public class Overrides {

    static {
        // Ensure that the "gobject-2.0" library has been loaded.
        // This is required for the downcall handles.
        GObjects.javagi$ensureInitialized();
    }

    /**
     * The method handle for g_type_interface_peek is used by all virtual
     * method bindings in interfaces to retrieve the interface typestruct.
     */
    private static final MethodHandle g_type_interface_peek = Interop.downcallHandle(
            "g_type_interface_peek",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
            false
    );

    private static final MethodHandle g_type_class_peek_parent = Interop.downcallHandle(
            "g_type_class_peek_parent",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            false
    );

    /**
     * Find declared methods that override methods defined in a GObject type class,
     * and return a class initializer lambda that will register the method overrides
     * in the class virtual function table.
     * @param cls the class that possibly declares method overrides
     * @return a lambda to run during class initialization that will register the virtual functions
     * @param <T> the class must extend {@link GObject}
     * @param <TC> the returned lambda expects a {@link GObject.ObjectClass} parameter
     */
    public static <T extends GObject, TC extends GObject.ObjectClass> Consumer<TC> overrideClassMethods(Class<T> cls) {
        Class<?> typeStruct = Types.getTypeClass(cls);
        if (typeStruct == null) {
            return null;
        }
        Class<?> parentClass = cls.getSuperclass();

        // Find all overridden methods
        List<Method> methods = new ArrayList<>();
        for (Method method : cls.getDeclaredMethods()) {
            try {
                Method virtual = parentClass.getMethod(method.getName(), method.getParameterTypes());
                if (! Proxy.class.isAssignableFrom(virtual.getDeclaringClass())) {
                    continue;
                }
            } catch (NoSuchMethodException e) {
                continue;
            }
            String name = method.getName();
            name = "override" + name.substring(0, 1).toUpperCase() + name.substring(1);
            try {
                typeStruct.getMethod(name, Arena.class, Method.class);
            } catch (NoSuchMethodException e) {
                continue;
            }
            methods.add(method);
        }
        if (methods.isEmpty()) {
            return null;
        }

        // Register the overridden methods in the typeclass
        return (gclass) -> {
            for (Method method : methods) {
                String name = method.getName();
                name = "override" + name.substring(0, 1).toUpperCase() + name.substring(1);
                try {
                    Method overrider = gclass.getClass().getMethod(name, Arena.class, Method.class);
                    overrider.invoke(gclass, Arena.global(), method);
                } catch (InvocationTargetException ite) {
                    System.err.printf("Cannot override method %s in class %s: %s\n",
                            method.getName(), cls.getName(), ite.getTargetException().toString());
                } catch (Exception e) {
                    GLib.log(LOG_DOMAIN, LogLevelFlags.LEVEL_CRITICAL,
                            "Cannot override method %s in class %s: %s\n",
                            method.getName(), cls.getName(), e.toString());
                }
            }
        };
    }

    /**
     * Find declared methods that implement methods defined in the provided GObject interface,
     * and return an interface initializer lambda that will register the method overrides
     * in the interface virtual function table.
     * @param cls the class that possibly declares method overrides
     * @param iface the interface from which methods are implemented
     * @return a lambda to run during interface initialization that will register the virtual functions
     * @param <T> the class must extend {@link GObject}
     * @param <TI> the returned lambda expects a {@link TypeInterface} parameter
     */
    static <T extends GObject, TI extends TypeInterface> Consumer<TI> overrideInterfaceMethods(Class<T> cls, Class<?> iface) {
        // Find all overridden methods
        Class<TI> typeStruct = Types.getTypeInterface(iface);
        if (typeStruct == null) {
            GLib.log(LOG_DOMAIN, LogLevelFlags.LEVEL_CRITICAL,
                    "Cannot find TypeInterface class for interface %s\n", iface);
            return null;
        }
        var constructor = Types.getAddressConstructor(typeStruct);
        if (constructor == null) {
            GLib.log(LOG_DOMAIN, LogLevelFlags.LEVEL_CRITICAL,
                    "Cannot find constructor in TypeInterface %s\n", typeStruct);
            return null;
        }

        // Find all overridden methods
        List<Method> methods = new ArrayList<>();
        for (Method method : cls.getDeclaredMethods()) {
            try {
                iface.getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                continue;
            }
            String name = method.getName();
            name = "override" + name.substring(0, 1).toUpperCase() + name.substring(1);
            try {
                typeStruct.getMethod(name, Arena.class, Method.class);
            } catch (NoSuchMethodException e) {
                continue;
            }
            methods.add(method);
        }
        if (methods.isEmpty()) {
            return null;
        }

        // Register the overridden methods in the typeinterface
        return (giface) -> {
            for (Method method : methods) {
                String name = method.getName();
                name = "override" + name.substring(0, 1).toUpperCase() + name.substring(1);
                try {
                    TI ifaceInstance = constructor.apply(giface.handle()); // upcast to the actual type
                    Method overrider = typeStruct.getMethod(name, Arena.class, Method.class);
                    overrider.invoke(ifaceInstance, Arena.global(), method);
                } catch (Exception e) {
                    GLib.log(LOG_DOMAIN, LogLevelFlags.LEVEL_CRITICAL,
                            "Cannot override method %s from interface %s in class %s: %s\n",
                            method.getName(), iface.getName(), cls.getName(), e.toString());
                }
            }
        };
    }

    /**
     * Returns a function pointer to the specified virtual method. The pointer is retrieved from the TypeClass.
     *
     * @param address     the memory address of the object instance
     * @param classLayout the memory layout of the object's TypeClass
     * @param name        the name of the virtual method (as defined in the TypeClass)
     * @return a function pointer to the requested virtual method
     */
    public static MemorySegment lookupVirtualMethod(MemorySegment address, MemoryLayout classLayout, String name) {
        return address
                .get(ValueLayout.ADDRESS.withTargetLayout(classLayout), 0)
                .get(ValueLayout.ADDRESS, classLayout.byteOffset(MemoryLayout.PathElement.groupElement(name)));
    }

    /**
     * Returns a function pointer to the specified virtual method. The pointer is retrieved from the TypeClass of the
     * parent class of the instance.
     *
     * @param address     the memory address of the object instance
     * @param classLayout the memory layout of the object's TypeClass
     * @param name        the name of the virtual method (as defined in the TypeClass)
     * @return a function pointer to the requested virtual method
     */
    public static MemorySegment lookupVirtualMethodParent(MemorySegment address, MemoryLayout classLayout, String name) {
        try {
            // Get the TypeClass
            var myClass = address.get(ValueLayout.ADDRESS, 0);

            // Get the parent TypeClass
            var parentClass = (MemorySegment) g_type_class_peek_parent.invoke(myClass);

            // Return a pointer to the requested virtual method address in the dispatch table
            return parentClass.reinterpret(classLayout.byteSize())
                    .get(ValueLayout.ADDRESS, classLayout.byteOffset(MemoryLayout.PathElement.groupElement(name)));
        } catch (Throwable t) {
            throw new InteropException(t);
        }
    }

    /**
     * Returns a function pointer to the specified virtual method. The pointer is retrieved from the TypeInterface with
     * the specified GType.
     *
     * @param address     the memory address of the object instance
     * @param classLayout the memory layout of the object's TypeClass
     * @param name        the name of the virtual method (as defined in the TypeInterface)
     * @param ifaceType   the GType of the interface that declares the virtual method
     * @return a function pointer to the requested virtual method
     */
    public static MemorySegment lookupVirtualMethod(MemorySegment address, MemoryLayout classLayout, String name, Type ifaceType) {
        try {
            // Get the TypeClass
            MemorySegment myClass = address.get(ValueLayout.ADDRESS, 0);

            // Get the TypeInterface implemented by the TypeClass
            MemorySegment iface = (MemorySegment) g_type_interface_peek.invokeExact(myClass, ifaceType.getValue().longValue());

            // Return a pointer to the requested virtual method address in the dispatch table
            return iface.reinterpret(classLayout.byteSize())
                    .get(ValueLayout.ADDRESS, classLayout.byteOffset(MemoryLayout.PathElement.groupElement(name)));
        } catch (Throwable t) {
            throw new InteropException(t);
        }
    }

    /**
     * Returns a function pointer to the specified virtual method. The pointer is retrieved from the TypeInterface with
     * the specified GType, implemented by the parent class of the instance.
     *
     * @param address           the memory address of the object instance
     * @param parentClassLayout the memory layout of the parent object's TypeClass
     * @param name              the name of the virtual method (as defined in the TypeInterface)
     * @param ifaceType         the GType of the interface that declares the virtual method
     * @return a function pointer to the requested virtual method
     */
    public static MemorySegment lookupVirtualMethodParent(MemorySegment address, MemoryLayout parentClassLayout, String name, Type ifaceType) {
        try {
            // Get the TypeClass
            var myClass = address.get(ValueLayout.ADDRESS, 0);

            // Get the parent TypeClass
            var parentClass = (MemorySegment) g_type_class_peek_parent.invoke(myClass);

            // Get the TypeInterface implemented by the parent TypeClass
            var parentIface = (MemorySegment) g_type_interface_peek.invokeExact(parentClass, ifaceType.getValue().longValue());

            // Return a pointer to the requested virtual method address in the dispatch table
            return parentIface.reinterpret(parentClassLayout.byteSize())
                    .get(ValueLayout.ADDRESS, parentClassLayout.byteOffset(MemoryLayout.PathElement.groupElement(name)));
        } catch (Throwable t) {
            throw new InteropException(t);
        }
    }
}
