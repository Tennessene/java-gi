package io.github.jwharm.javagi.patches;

import io.github.jwharm.javagi.gir.GirElement;
import io.github.jwharm.javagi.gir.Method;
import io.github.jwharm.javagi.gir.Type;
import io.github.jwharm.javagi.util.Patch;

/**
 * Patch the gir data of spice-gtk
 */
public class SpicePatch implements Patch {

    @Override
    public GirElement patch(GirElement element, String namespace) {

        if (!"SpiceClientGLib".equals(namespace))
            return element;

        /*
         * "Audio::getPlaybackVolumeInfoFinish" has a "volume" parameter of
         * type "guint16**". The code generator tries to turn it into a java
         * short[] but that doesn't work. Change it to an opaque pointer.
         */
        if (element instanceof Type t
                && "guint16**".equals(t.cType()))
            return t.withAttribute("c:type", "gpointer");

        /*
         * The "Channel::openFd" method clashes with the class handler of the
         * "open-fd" signal. Rename to "openSocket".
         */
        if (element instanceof Method m
                && "spice_channel_open_fd".equals(m.callableAttrs().cIdentifier()))
            return m.withAttribute("name", "open_socket");

        return element;
    }
}
