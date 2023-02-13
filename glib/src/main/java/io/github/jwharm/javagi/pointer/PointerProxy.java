package io.github.jwharm.javagi.pointer;

import io.github.jwharm.javagi.base.Proxy;
import io.github.jwharm.javagi.interop.Interop;

import java.lang.foreign.*;
import java.lang.reflect.Array;
import java.util.List;
import java.util.function.Function;

/**
 * This type of Pointer object points to a GObject-derived object 
 * in native memory.
 * @param <T> The type of the object
 */
public class PointerProxy<T extends Proxy> extends Pointer<T> {

    private final Function<Addressable, ? extends T> make;

    /**
     * Create a pointer to an existing memory address.
     * @param address the memory address
     * @param make a function to create an instance
     */
    public PointerProxy(MemoryAddress address, Function<Addressable, ? extends T> make) {
        super(address);
        this.make = make;
    }

    /**
     * Use this method to set the value that the pointer points to.
     * @param value the new value that is pointed to
     */
    public void set(T value) {
        address.set(Interop.valueLayout.ADDRESS, 0, value.handle());
    }

    /**
     * Use this method to retrieve the value of the pointer.
     * @return the value of the pointer
     */
    public T get() {
        return get(0);
    }

    /**
     * Treat the pointer as an array, and return the given element.
     * <p>
     * <strong>Warning: There is no bounds checking.</strong>
     * @param index the array index
     * @return the value stored at the given index
     */
    public T get(int index) {
        // Get the memory address of the native object.
        Addressable ref = address.get(
                Interop.valueLayout.ADDRESS,
                Interop.valueLayout.ADDRESS.byteSize() * index
        );
        // Call the constructor of the proxy object and return the created instance.
        // TODO: What about ownership of the object?
        return make.apply(ref);
    }

    /**
     * Read an array of values from the pointer
     * @param length length of the array
     * @param clazz type of the array elements
     * @return the array
     */
    public T[] toArrayOfStructs(int length, Class<T> clazz, MemoryLayout layout) {
        // clazz is of type Class<T>, so the cast to T is safe
        @SuppressWarnings("unchecked") T[] array = (T[]) Array.newInstance(clazz, length);
        var segment = MemorySegment.ofAddress(address, length * layout.byteSize(), MemorySession.openImplicit());
        List<MemorySegment> elements = segment.elements(layout).toList();
        for (int i = 0; i < length; i++) {
            array[i] = make.apply(elements.get(i).address());
        }
        return array;
    }
}
