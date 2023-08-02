package io.github.jwharm.javagi.patches;

import io.github.jwharm.javagi.generator.Patch;
import io.github.jwharm.javagi.model.Field;
import io.github.jwharm.javagi.model.Repository;
import io.github.jwharm.javagi.model.Type;

public class GObjectPatch implements Patch {

    @Override
    public void patch(Repository repo) {
        // Remove va_list marshaller. va_list parameters are unsupported
        removeType(repo, "VaClosureMarshal");
        removeType(repo, "SignalCVaMarshaller");
        removeFunction(repo, "signal_set_va_marshaller");

        // Override with different return type
        renameMethod(repo, "TypeModule", "use", "use_type_module");

        // Make GWeakRef a generic class
        makeGeneric(repo, "WeakRef");

        // Change GInitiallyUnownedClass struct to refer to GObjectClass. Both structs
        // are identical, so this has no practical consequences, besides
        // convincing the bindings generator that GObject.InitiallyUnownedClass
        // is not a fundamental type class, but extends GObject.ObjectClass.
        var iuc = repo.namespace.registeredTypeMap.get("InitiallyUnownedClass");
        if (iuc != null) {
            iuc.fieldList.clear();
            var parent_class = new Field(iuc, "parent_class", null, null);
            parent_class.type = new Type(parent_class, "GObject.ObjectClass", "GObjectClass");
            parent_class.type.girElementType = "Record";
            parent_class.type.girElementInstance = repo.namespace.registeredTypeMap.get("ObjectClass");
            parent_class.type.init("GObject.ObjectClass");
            iuc.fieldList.add(parent_class);
        }

        // Add a static factory method for GObject
        inject(repo, "Object", """
            
            /**
             * Creates a new GObject instance of the provided GType.
             * @param objectType the GType of the new GObject
             * @return the newly created GObject instance
             */
            public static <T extends GObject> T newInstance(org.gnome.glib.Type objectType) {
                var _result = constructNew(objectType, null);
                T _object = (T) InstanceCache.getForType(_result, org.gnome.gobject.GObject::new, true);
                if (_object != null) {
                    _object.ref();
                }
                return _object;
            }
            
            /**
             * Creates a new GObject instance of the provided GType and with the provided property values.
             * @param objectType the GType of the new GObject
             * @param propertyNamesAndValues pairs of property names and values (Strings and Objects)
             * @return the newly created GObject instance
             */
            public static <T extends GObject> T newInstance(org.gnome.glib.Type objectType, Object... propertyNamesAndValues) {
                return io.github.jwharm.javagi.types.Properties.newGObjectWithProperties(objectType, propertyNamesAndValues);
            }
            
            /**
             * Gets a property of an object.
             * @param propertyName the name of the property to get
             * @return the property value
             */
            public Object getProperty(String propertyName) {
                return io.github.jwharm.javagi.types.Properties.getProperty(this, propertyName);
            }
            
            /**
             * Sets a property of an object.
             * @param propertyName the name of the property to set
             * @param value the new property value
             */
            public void setProperty(String propertyName, Object value) {
                io.github.jwharm.javagi.types.Properties.setProperty(this, propertyName, value);
            }
        """);
    }
}
