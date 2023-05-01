package io.github.jwharm.javagi.model;

import java.util.Set;

public class Repository extends GirElement {

    public final Module module;
    public Namespace namespace = null;
    public Package package_ = null;
    public boolean generate;
    public Set<String> natives;

    public Repository(Module module) {
        super(null);
        this.module = module;
    }

    public boolean isApi() {
        return namespace.isApi();
    }
}
