package com.starscape.rapidupload.common.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot<ID extends Serializable> extends Entity<ID> {
    
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();
    
    protected AggregateRoot() {
        super();
    }
    
    protected AggregateRoot(ID id) {
        super(id);
    }
    
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
    
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
    
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}

