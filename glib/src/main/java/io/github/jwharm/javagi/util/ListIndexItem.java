package io.github.jwharm.javagi.util;

import io.github.jwharm.javagi.interop.Interop;
import io.github.jwharm.javagi.base.Marshal;
import org.gtk.glib.Type;
import org.gtk.gobject.GObject;
import org.gtk.gobject.GObjects;
import org.gtk.gobject.ObjectClass;
import org.gtk.gobject.TypeFlags;

import java.lang.foreign.*;

/**
 * Represents an item in the {@link ListIndexModel}
 */
public class ListIndexItem extends GObject {

    /**
     * Construct a ListIndexItem for the provided memory address.
     * @param address the memory address of the instance in native memory
     */
    protected ListIndexItem(Addressable address) {
        super(address);
    }

    /**
     * Marshaller from a memory address to a ListIndexItem instance.
     */
    public static final Marshal<Addressable, ListIndexItem> fromAddress =
            (input, scope) -> input.equals(MemoryAddress.NULL) ? null : new ListIndexItem(input);

    /**
     * Get the {@link MemoryLayout} of the instance struct
     * @return the memory layout
     */
    public static MemoryLayout getMemoryLayout() {
        return MemoryLayout.structLayout(
                GObject.getMemoryLayout().withName("parent_instance"),
                Interop.valueLayout.C_INT.withName("int_value")
        ).withName("ListIndexModel");
    }

    private static Type type;

    /**
     * Get the gtype of {@link ListIndexItem}, or register it as a new gtype
     * if it was not registered yet.
     * @return the {@link Type} that has been registered for {@link ListIndexItem}
     */
    public static Type getType() {
        if (type == null) {
            // Register the new gtype
            type = GObjects.typeRegisterStaticSimple(
                    GObject.getType(),
                    "ListIndexItem",
                    (short) ObjectClass.getMemoryLayout().byteSize(),
                    (gclass, data) -> {},
                    (short) getMemoryLayout().byteSize(),
                    (inst, gclass) -> {},
                    TypeFlags.NONE
            );
        }
        Interop.register(type, fromAddress);
        return type;
    }

    /**
     * Set the int_value field in the instance struct
     * @param value the new value to set
     */
    public void setIntValue(int value) {
        getMemoryLayout()
                .varHandle(MemoryLayout.PathElement.groupElement("int_value"))
                .set(MemorySegment.ofAddress((MemoryAddress) handle(), getMemoryLayout().byteSize(), MemorySession.openImplicit()), value);
    }

    /**
     * Get the int_value field from the instance struct
     * @return the requested value
     */
    public int getIntValue() {
        return (int) getMemoryLayout()
                .varHandle(MemoryLayout.PathElement.groupElement("int_value"))
                .get(MemorySegment.ofAddress((MemoryAddress) handle(), getMemoryLayout().byteSize(), MemorySession.openImplicit()));
    }

    /**
     * Create a {@link ListIndexItem} (registering it as a new gtype) and set the int_value field
     * to the provided value.
     * @param value the initial value for the int_value field
     */
    public ListIndexItem(int value) {
        super(getType(), null);
        setIntValue(value);
    }
}