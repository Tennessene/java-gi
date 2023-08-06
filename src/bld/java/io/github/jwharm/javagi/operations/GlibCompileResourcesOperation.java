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

package io.github.jwharm.javagi.operations;

import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GlibCompileResourcesOperation extends AbstractOperation<GlibCompileResourcesOperation> {

    File workDirectory;

    public GlibCompileResourcesOperation workDirectory(File directory) {
        this.workDirectory = directory;
        return this;
    }

    public File workDirectory() {
        return workDirectory;
    }

    private List<String> getCommand() throws FileNotFoundException {
        List<String> command = new ArrayList<>();
        command.add("glib-compile-resources");
        File[] files = workDirectory().listFiles((dir, name) -> name.endsWith(".gresource.xml"));
        if (files == null) {
            throw new FileNotFoundException("No .gresource.xml files found");
        }
        Arrays.stream(files).map(File::getAbsolutePath).forEach(command::add);
        return command;
    }

    @Override
    public void execute() throws Exception {
        int exitCode = new ProcessBuilder()
            .inheritIO()
            .directory(workDirectory())
            .command(getCommand())
            .start()
            .waitFor();

        if (exitCode != 0) {
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        if (! silent()) {
            System.out.println("GResource compilation completed successfully.");
        }
    }
}
