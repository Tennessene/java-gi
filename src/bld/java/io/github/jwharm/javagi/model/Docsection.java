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

package io.github.jwharm.javagi.model;

import io.github.jwharm.javagi.generator.Conversions;
import io.github.jwharm.javagi.generator.SourceWriter;

import java.io.IOException;

public class Docsection extends GirElement {

    public Docsection(GirElement parent, String name) {
        super(parent);
        this.name = name;
    }

    public void generate(SourceWriter writer) throws IOException {
        if (doc != null) {
            writer.write(" * \n");
            writer.write(" * <h2>");
            boolean first = true;
            for (var word : name.split("\\_")) {
                if (! first) {
                    writer.write(" ");
                }
                writer.write(Conversions.toCamelCase(word, true));
                first = false;
            }
            writer.write("</h2>\n");
            writer.write(" * \n");
            doc.generate(writer, false, false);
        }
    }
}
